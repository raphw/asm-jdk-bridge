package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Label;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.lang.reflect.AccessFlag;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("ALL")
public class JdkClassReader {

    private static final Attribute[] NO_ATTRIBUTES = new Attribute[0];

    private static final int
            SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247,
            SAME_EXTENDED = 251;

    private final ClassModel classModel;

    public JdkClassReader(byte[] classFile) {
        this(ClassFile.of().parse(classFile));
    }

    public JdkClassReader(InputStream inputStream) throws IOException {
        this(inputStream.readAllBytes());
    }

    public JdkClassReader(String className) throws IOException {
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class")) {
            classModel = ClassFile.of().parse(inputStream.readAllBytes());
        }
    }

    public JdkClassReader(ClassModel classModel) {
        this.classModel = classModel;
    }

    public ClassModel getClassModel() {
        return classModel;
    }

    public void accept(ClassVisitor classVisitor, int flags) {
        accept(classVisitor, NO_ATTRIBUTES, flags);
    }

    public void accept(ClassVisitor classVisitor, Attribute[] attributePrototypes, int flags) {
        Map<String, AsmAttribute> prototypes = Stream.of(attributePrototypes).collect(Collectors.toMap(
                attribute -> attribute.type,
                attribute -> new AsmAttribute(attribute)
        ));
        Map<Label, org.objectweb.asm.Label> labels = new HashMap<>();
        classVisitor.visit(classModel.minorVersion() << 16 | classModel.majorVersion(),
                classModel.flags().flagsMask()
                        | (classModel.findAttribute(Attributes.deprecated()).isPresent() ? Opcodes.ACC_DEPRECATED : 0)
                        | (classModel.findAttribute(Attributes.record()).isPresent() ? Opcodes.ACC_RECORD : 0),
                classModel.thisClass().asInternalName(),
                classModel.findAttribute(Attributes.signature()).map(signature -> signature.signature().stringValue()).orElse(null),
                classModel.superclass().map(ClassEntry::asInternalName).orElse(null),
                classModel.interfaces().stream().map(ClassEntry::asInternalName).toArray(String[]::new));
        if ((flags & ClassReader.SKIP_DEBUG) == 0) {
            String sourceFile = classModel.findAttribute(Attributes.sourceFile())
                    .map(attribute -> attribute.sourceFile().stringValue())
                    .orElse(null);
            String debug = classModel.findAttribute(Attributes.sourceDebugExtension())
                    .map(attribute -> new String(attribute.contents(), StandardCharsets.UTF_8))
                    .orElse(null);
            if (sourceFile != null || debug != null) {
                classVisitor.visitSource(sourceFile, debug);
            }
        }
        classModel.findAttribute(Attributes.module()).ifPresent(module -> {
            ModuleVisitor moduleVisitor = classVisitor.visitModule(module.moduleName().name().stringValue(),
                    module.moduleFlagsMask(),
                    module.moduleVersion().map(Utf8Entry::stringValue).orElse(null));
            if (moduleVisitor instanceof JdkClassWriter.WritingModuleVisitor writingModuleVisitor && writingModuleVisitor.has(classModel)) {
                classModel.findAttribute(Attributes.moduleMainClass()).ifPresent(writingModuleVisitor::add);
                classModel.findAttribute(Attributes.modulePackages()).ifPresent(writingModuleVisitor::add);
                writingModuleVisitor.add(module);
            } else if (moduleVisitor != null) {
                classModel.findAttribute(Attributes.moduleMainClass())
                        .map(moduleMainClass -> moduleMainClass.mainClass().asInternalName())
                        .ifPresent(moduleVisitor::visitMainClass);
                classModel.findAttribute(Attributes.modulePackages()).stream()
                        .flatMap(modulePackagesAttribute -> modulePackagesAttribute.packages().stream())
                        .map(packageName -> packageName.name().stringValue())
                        .forEach(moduleVisitor::visitPackage);
                module.requires().forEach(moduleRequire -> moduleVisitor.visitRequire(moduleRequire.requires().name().stringValue(),
                        moduleRequire.requiresFlagsMask(),
                        moduleRequire.requiresVersion().map(Utf8Entry::stringValue).orElse(null)));
                module.exports().forEach(moduleExport -> moduleVisitor.visitExport(moduleExport.exportedPackage().name().stringValue(),
                        moduleExport.exportsFlagsMask(),
                        moduleExport.exportsTo().isEmpty() ? null : moduleExport.exportsTo().stream().map(to -> to.name().stringValue()).toArray(String[]::new)));
                module.opens().forEach(moduleOpen -> moduleVisitor.visitOpen(moduleOpen.openedPackage().name().stringValue(),
                        moduleOpen.opensFlagsMask(),
                        moduleOpen.opensTo().isEmpty() ? null : moduleOpen.opensTo().stream().map(to -> to.name().stringValue()).toArray(String[]::new)));
                module.uses().forEach(moduleUses -> moduleVisitor.visitUse(moduleUses.asInternalName()));
                module.provides().forEach(provideInfo -> moduleVisitor.visitProvide(
                        provideInfo.provides().asInternalName(),
                        provideInfo.providesWith().stream().map(ClassEntry::asInternalName).toArray(String[]::new)));
                moduleVisitor.visitEnd();
            }
        });
        classModel.findAttribute(Attributes.nestHost()).ifPresent(nestHost -> classVisitor.visitNestHost(nestHost.nestHost().asInternalName()));
        classModel.findAttribute(Attributes.enclosingMethod()).ifPresent(enclosingMethod -> classVisitor.visitOuterClass(enclosingMethod.enclosingClass().asInternalName(),
                enclosingMethod.enclosingMethod().map(value -> value.name().stringValue()).orElse(null),
                enclosingMethod.enclosingMethod().map(value -> value.type().stringValue()).orElse(null)));
        acceptAnnotations(classModel, classVisitor::visitAnnotation, classVisitor::visitTypeAnnotation);
        classModel.findAttribute(Attributes.sourceId()).ifPresent(sourceIDAttribute -> classVisitor.visitAttribute(new AsmSourceIdAttribute(sourceIDAttribute)));
        classModel.findAttribute(Attributes.compilationId()).ifPresent(sourceIDAttribute -> classVisitor.visitAttribute(new AsmCompilationIdAttribute(sourceIDAttribute)));
        classModel.findAttribute(Attributes.moduleResolution()).ifPresent(moduleResolutionAttribute -> classVisitor.visitAttribute(new AsmModuleResolutionAttribute(moduleResolutionAttribute)));
        classModel.findAttribute(Attributes.moduleHashes()).ifPresent(moduleResolutionAttribute -> classVisitor.visitAttribute(new AsmModuleHashesAttribute(moduleResolutionAttribute)));
        acceptAttributes(classModel, prototypes, classVisitor::visitAttribute);
        classModel.findAttribute(Attributes.nestMembers()).stream()
                .flatMap(nestMembers -> nestMembers.nestMembers().stream())
                .forEach(nestMember -> classVisitor.visitNestMember(nestMember.asInternalName()));
        classModel.findAttribute(Attributes.permittedSubclasses()).stream()
                .flatMap(permittedSubclasses -> permittedSubclasses.permittedSubclasses().stream())
                .forEach(nestMember -> classVisitor.visitPermittedSubclass(nestMember.asInternalName()));
        classModel.findAttribute(Attributes.innerClasses()).stream()
                .flatMap(innerClasses -> innerClasses.classes().stream())
                .forEach(innerClass -> classVisitor.visitInnerClass(innerClass.innerClass().asInternalName(),
                        innerClass.outerClass().map(ClassEntry::asInternalName).orElse(null),
                        innerClass.innerName().map(Utf8Entry::stringValue).orElse(null),
                        innerClass.flagsMask()));
        classModel.findAttribute(Attributes.record()).stream()
                .flatMap(record -> record.components().stream())
                .forEach(recordComponent -> {
                    RecordComponentVisitor recordComponentVisitor = classVisitor.visitRecordComponent(recordComponent.name().stringValue(),
                            recordComponent.descriptor().stringValue(),
                            recordComponent.findAttribute(Attributes.signature()).map(signature -> signature.signature().stringValue()).orElse(null));
                    if (recordComponentVisitor != null) {
                        acceptAnnotations(recordComponent, recordComponentVisitor::visitAnnotation, recordComponentVisitor::visitTypeAnnotation);
                        acceptAttributes(recordComponent, prototypes, recordComponentVisitor::visitAttribute);
                        recordComponentVisitor.visitEnd();
                    }
                });
        for (FieldModel fieldModel : classModel.fields()) {
            FieldVisitor fieldVisitor = classVisitor.visitField(fieldModel.flags().flagsMask() | (fieldModel.findAttribute(Attributes.deprecated()).isPresent() ? Opcodes.ACC_DEPRECATED : 0),
                    fieldModel.fieldName().stringValue(),
                    fieldModel.fieldType().stringValue(),
                    fieldModel.findAttribute(Attributes.signature()).map(signature -> signature.signature().stringValue()).orElse(null),
                    fieldModel.findAttribute(Attributes.constantValue()).map(constantValue -> toAsmConstant(constantValue.constant().constantValue())).orElse(null));
            if (fieldVisitor instanceof JdkClassWriter.WritingFieldVisitor writingFieldVisitor && writingFieldVisitor.has(classModel)) {
                writingFieldVisitor.add(fieldModel);
            } else if (fieldVisitor != null) {
                acceptAnnotations(fieldModel, fieldVisitor::visitAnnotation, fieldVisitor::visitTypeAnnotation);
                acceptAttributes(fieldModel, prototypes, fieldVisitor::visitAttribute);
                fieldVisitor.visitEnd();
            }
        }
        for (MethodModel methodModel : classModel.methods()) {
            MethodVisitor methodVisitor = classVisitor.visitMethod(methodModel.flags().flagsMask() | (methodModel.findAttribute(Attributes.deprecated()).isPresent() ? Opcodes.ACC_DEPRECATED : 0),
                    methodModel.methodName().stringValue(),
                    methodModel.methodType().stringValue(),
                    methodModel.findAttribute(Attributes.signature()).map(signature -> signature.signature().stringValue()).orElse(null),
                    methodModel.findAttribute(Attributes.exceptions()).map(exceptions -> exceptions.exceptions().stream().map(ClassEntry::asInternalName).toArray(String[]::new)).orElse(null));
            if (methodVisitor instanceof JdkClassWriter.WritingMethodVisitor writingMethodVisitor && writingMethodVisitor.has(classModel)) {
                writingMethodVisitor.add(methodModel);
            } else if (methodVisitor != null) {
                if ((flags & ClassReader.SKIP_DEBUG) == 0) {
                    methodModel.findAttribute(Attributes.methodParameters()).stream()
                            .flatMap(methodParameters -> methodParameters.parameters().stream())
                            .forEach(methodParameter -> methodVisitor.visitParameter(methodParameter.name().map(Utf8Entry::stringValue).orElse(null), methodParameter.flagsMask()));
                }
                methodModel.findAttribute(Attributes.annotationDefault()).ifPresent(annotationDefault -> {
                    AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotationDefault();
                    if (annotationVisitor != null) {
                        appendAnnotationValue(annotationVisitor, null, annotationDefault.defaultValue());
                        annotationVisitor.visitEnd();
                    }
                });
                acceptAnnotations(methodModel, methodVisitor::visitAnnotation, methodVisitor::visitTypeAnnotation);
                acceptParameterAnnotations(methodModel, methodVisitor, true);
                acceptParameterAnnotations(methodModel, methodVisitor, false);
                acceptAttributes(methodModel, prototypes, methodVisitor::visitAttribute);
                methodModel.code().filter(_ -> (flags & ClassReader.SKIP_CODE) == 0).map(code -> (CodeAttribute) code).ifPresent(code -> {
                    code.findAttribute(Attributes.characterRangeTable()).ifPresent(characterRangeTable -> methodVisitor.visitAttribute(new AsmCharacterRangeTableAttribute(characterRangeTable)));
                    int localVariablesSize = Type.getMethodType(methodModel.methodType().stringValue()).getArgumentTypes().length + (methodModel.flags().has(AccessFlag.STATIC) ? 0 : 1);
                    Map<Label, StackMapFrameInfo> frames = (flags & ClassReader.SKIP_FRAMES) == 0 ? code.findAttribute(Attributes.stackMapTable())
                            .map(stackMapTable -> stackMapTable.entries().stream().collect(Collectors.toMap(StackMapFrameInfo::target, Function.identity())))
                            .orElse(Collections.emptyMap()) : Map.of();
                    Map<MergedLocalVariableKey, MergedLocalVariableValue> localVariables = new LinkedHashMap<>();
                    Map<org.objectweb.asm.Label, List<Map.Entry<TypeAnnotation, Boolean>>> offsetTypeAnnotations = new HashMap<>();
                    List<Map.Entry<TypeAnnotation, Boolean>> localVariableAnnotations = new ArrayList<>();
                    methodVisitor.visitCode();
                    org.objectweb.asm.Label currentPositionLabel = null;
                    PushbackIterator<CodeElement> it = new PushbackIterator<>(code.iterator());
                    while (it.hasNext()) {
                        CodeElement element = it.next();
                        switch (element) {
                            case MonitorInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case TypeCheckInstruction value -> methodVisitor.visitTypeInsn(value.opcode().bytecode(), value.type().asInternalName());
                            case LoadInstruction value -> methodVisitor.visitVarInsn(switch (value.typeKind()) {
                                case BooleanType, ByteType, CharType, ShortType, IntType -> Opcodes.ILOAD;
                                case LongType -> Opcodes.LLOAD;
                                case FloatType -> Opcodes.FLOAD;
                                case DoubleType -> Opcodes.DLOAD;
                                case ReferenceType -> Opcodes.ALOAD;
                                default -> throw new IllegalStateException("Unexpected type: " + value.typeKind());
                            }, value.slot());
                            case OperatorInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case ReturnInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case InvokeInstruction value -> methodVisitor.visitMethodInsn(value.opcode().bytecode(),
                                    value.owner().asInternalName(),
                                    value.name().stringValue(),
                                    value.type().stringValue(),
                                    value.isInterface());
                            case IncrementInstruction value -> methodVisitor.visitIincInsn(value.slot(), value.constant());
                            case FieldInstruction value -> methodVisitor.visitFieldInsn(value.opcode().bytecode(),
                                    value.owner().asInternalName(),
                                    value.name().stringValue(),
                                    value.type().stringValue());
                            case InvokeDynamicInstruction value -> methodVisitor.visitInvokeDynamicInsn(value.name().stringValue(),
                                    value.type().stringValue(),
                                    (Handle) toAsmConstant(value.bootstrapMethod()),
                                    value.bootstrapArgs().stream().map(JdkClassReader::toAsmConstant).toArray());
                            case BranchInstruction value -> methodVisitor.visitJumpInsn(
                                    value.opcode() == Opcode.GOTO_W ? Opcodes.GOTO : value.opcode().bytecode(),
                                    labels.computeIfAbsent(value.target(), _ -> new org.objectweb.asm.Label()));
                            case StoreInstruction value -> methodVisitor.visitVarInsn(switch (value.typeKind()) {
                                case BooleanType, ByteType, CharType, ShortType, IntType -> Opcodes.ISTORE;
                                case LongType -> Opcodes.LSTORE;
                                case FloatType -> Opcodes.FSTORE;
                                case DoubleType -> Opcodes.DSTORE;
                                case ReferenceType -> Opcodes.ASTORE;
                                default -> throw new IllegalStateException("Unexpected type: " + value.typeKind());
                            }, value.slot());
                            case NewReferenceArrayInstruction value -> methodVisitor.visitTypeInsn(value.opcode().bytecode(), value.componentType().asInternalName());
                            case LookupSwitchInstruction value -> methodVisitor.visitLookupSwitchInsn(labels.computeIfAbsent(value.defaultTarget(), _ -> new org.objectweb.asm.Label()),
                                    value.cases().stream().mapToInt(SwitchCase::caseValue).toArray(),
                                    value.cases().stream().map(aCase -> labels.computeIfAbsent(aCase.target(), _ -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                            case TableSwitchInstruction value -> methodVisitor.visitTableSwitchInsn(value.lowValue(),
                                    value.highValue(),
                                    labels.computeIfAbsent(value.defaultTarget(), _ -> new org.objectweb.asm.Label()),
                                    value.cases().stream().map(aCase -> labels.computeIfAbsent(aCase.target(), _ -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                            case ArrayStoreInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case ArrayLoadInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case ConstantInstruction value -> {
                                switch (value.opcode()) {
                                    case LDC, LDC_W, LDC2_W -> methodVisitor.visitLdcInsn(toAsmConstant(value.constantValue()));
                                    case BIPUSH, SIPUSH -> methodVisitor.visitIntInsn(value.opcode().bytecode(), (Integer) value.constantValue());
                                    default -> methodVisitor.visitInsn(value.opcode().bytecode());
                                }
                            }
                            case StackInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case NopInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case ThrowInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case NewObjectInstruction value -> methodVisitor.visitTypeInsn(value.opcode().bytecode(), value.className().asInternalName());
                            case ConvertInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case NewMultiArrayInstruction value -> methodVisitor.visitMultiANewArrayInsn(value.arrayType().asInternalName(), value.dimensions());
                            case NewPrimitiveArrayInstruction value -> methodVisitor.visitIntInsn(value.opcode().bytecode(), value.typeKind().newarrayCode());
                            case LocalVariableType value -> localVariables.compute(new MergedLocalVariableKey(
                                    labels.computeIfAbsent(value.startScope(), _ -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.endScope(), _ -> new org.objectweb.asm.Label()),
                                    value.name().stringValue(),
                                    value.slot()
                            ), (_, values) -> new MergedLocalVariableValue(values == null ? null : values.descriptor, value.signature().stringValue()));
                            case ExceptionCatch value -> methodVisitor.visitTryCatchBlock(labels.computeIfAbsent(value.tryStart(), _ -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.tryEnd(), _ -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.handler(), _ -> new org.objectweb.asm.Label()),
                                    value.catchType().map(ClassEntry::asInternalName).orElse(null));
                            case LocalVariable value -> localVariables.compute(new MergedLocalVariableKey(
                                    labels.computeIfAbsent(value.startScope(), _ -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.endScope(), _ -> new org.objectweb.asm.Label()),
                                    value.name().stringValue(),
                                    value.slot()
                            ), (_, values) -> new MergedLocalVariableValue(value.typeSymbol().descriptorString(), values == null ? null : values.signature));
                            case LineNumber value -> {
                                if ((flags & ClassReader.SKIP_DEBUG) == 0) {
                                    if (currentPositionLabel == null) {
                                        currentPositionLabel = new org.objectweb.asm.Label();
                                        methodVisitor.visitLabel(currentPositionLabel);
                                    }
                                    methodVisitor.visitLineNumber(value.line(), currentPositionLabel);
                                }
                            }
                            case LabelTarget value -> {
                                currentPositionLabel = labels.computeIfAbsent(value.label(), _ -> new org.objectweb.asm.Label());
                                methodVisitor.visitLabel(currentPositionLabel);
                                StackMapFrameInfo frame = frames.get(value.label());
                                if (frame != null) {
                                    if (it.hasNext()) { // Assure same ordering of ASM and JDK class reader with respect to line numbers and frames.
                                        CodeElement next = it.next();
                                        if (next instanceof LineNumber line) {
                                            methodVisitor.visitLineNumber(line.line(), currentPositionLabel);
                                        } else {
                                            it.push(next);
                                        }
                                    }
                                    if ((flags & ClassReader.EXPAND_FRAMES) != 0) {
                                        methodVisitor.visitFrame(Opcodes.F_NEW,
                                                frame.locals().size(),
                                                frame.locals().isEmpty() ? null : frame.locals().stream()
                                                        .map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, labels))
                                                        .toArray(),
                                                frame.stack().size(),
                                                frame.stack().isEmpty() ? null : frame.stack().stream()
                                                        .map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, labels))
                                                        .toArray());
                                    } else if (frame.frameType() < 64) {
                                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                                    } else if (frame.frameType() < 128) {
                                        methodVisitor.visitFrame(Opcodes.F_SAME1,
                                                0, null,
                                                1, new Object[]{toAsmFrameValue(frame.stack().get(0), labels)});
                                    } else if (frame.frameType() < SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                                        throw new IllegalArgumentException("Invalid stackmap frame type: " + frame.frameType());
                                    } else if (frame.frameType() == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                                        methodVisitor.visitFrame(Opcodes.F_SAME1,
                                                0, null,
                                                1, new Object[]{toAsmFrameValue(frame.stack().get(0), labels)});
                                    } else if (frame.frameType() < SAME_EXTENDED) {
                                        methodVisitor.visitFrame(Opcodes.F_CHOP,
                                                localVariablesSize - frame.locals().size(), null,
                                                0, null);
                                        localVariablesSize = frame.locals().size();
                                    } else if (frame.frameType() == SAME_EXTENDED) {
                                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                                    } else if (frame.frameType() < SAME_EXTENDED + 4) {
                                        int appended = frame.locals().size() - localVariablesSize;
                                        methodVisitor.visitFrame(Opcodes.F_APPEND,
                                                appended, frame.locals().stream().skip(localVariablesSize).map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, labels)).toArray(),
                                                0, null);
                                        localVariablesSize = frame.locals().size();
                                    } else {
                                        methodVisitor.visitFrame(Opcodes.F_FULL,
                                                frame.locals().size(), frame.locals().stream().map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, labels)).toArray(),
                                                frame.stack().size(), frame.stack().stream().map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, labels)).toArray());
                                        localVariablesSize = frame.locals().size();
                                    }
                                }
                            }
                            case CharacterRange ignored -> { /* ASM's contract requires to process this attribute before visiting instructions */ }
                            case RuntimeVisibleTypeAnnotationsAttribute value -> appendCodeAnnotations(value.annotations(), true, methodVisitor, labels, localVariableAnnotations, offsetTypeAnnotations);
                            case RuntimeInvisibleTypeAnnotationsAttribute value -> appendCodeAnnotations(value.annotations(), false, methodVisitor, labels, localVariableAnnotations, offsetTypeAnnotations);
                            case DiscontinuedInstruction.JsrInstruction value -> methodVisitor.visitJumpInsn(value.opcode().bytecode(), labels.computeIfAbsent(value.target(), _ -> new org.objectweb.asm.Label()));
                            case DiscontinuedInstruction.RetInstruction value -> methodVisitor.visitVarInsn(value.opcode().bytecode(), value.slot());
                            default -> throw new UnsupportedOperationException("Unknown value: " + element);
                        }
                        if (element instanceof Instruction) {
                            offsetTypeAnnotations.getOrDefault(currentPositionLabel, Collections.emptyList()).forEach(entry -> appendAnnotationValues(methodVisitor.visitInsnAnnotation(
                                    TypeReference.newTypeReference(entry.getKey().targetInfo().targetType().targetTypeValue()).getValue(),
                                    toTypePath(entry.getKey().targetPath()),
                                    entry.getKey().annotation().className().stringValue(),
                                    entry.getValue()), entry.getKey().annotation().elements()));
                            currentPositionLabel = null;
                        }
                    }
                    if ((flags & ClassReader.SKIP_DEBUG) == 0) {
                        localVariables.forEach((key, value) -> methodVisitor.visitLocalVariable(key.name(),
                                value.descriptor(),
                                value.signature(),
                                key.start(),
                                key.end(),
                                key.slot()));
                    }
                    localVariableAnnotations.forEach(entry -> {
                        TypeAnnotation.LocalVarTarget target = (TypeAnnotation.LocalVarTarget) entry.getKey().targetInfo();
                        appendAnnotationValues(methodVisitor.visitLocalVariableAnnotation(
                                TypeReference.newTypeReference(entry.getKey().targetInfo().targetType().targetTypeValue()).getValue(),
                                toTypePath(entry.getKey().targetPath()),
                                target.table().stream().map(localVarTargetInfo -> labels.get(localVarTargetInfo.startLabel())).toArray(org.objectweb.asm.Label[]::new),
                                target.table().stream().map(localVarTargetInfo -> labels.get(localVarTargetInfo.endLabel())).toArray(org.objectweb.asm.Label[]::new),
                                target.table().stream().mapToInt(TypeAnnotation.LocalVarTargetInfo::index).toArray(),
                                entry.getKey().annotation().className().stringValue(),
                                entry.getValue()), entry.getKey().annotation().elements());
                    });
                    methodVisitor.visitMaxs(code.maxStack(), code.maxLocals());
                });
                methodVisitor.visitEnd();
            }
        }
        classVisitor.visitEnd();
    }

    private void acceptAnnotations(AttributedElement element, AnnotationVisitorSource annotationVisitorSource, TypeAnnotationVisitorSource typeAnnotationVisitorSource) {
        element.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(annotationVisitorSource.visitAnnotation(annotation.className().stringValue(), true), annotation.elements()));
        element.findAttribute(Attributes.runtimeInvisibleAnnotations()).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(annotationVisitorSource.visitAnnotation(annotation.className().stringValue(), false), annotation.elements()));
        element.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(typeAnnotationVisitorSource.visitTypeAnnotation(toTypeReference(annotation.targetInfo()).getValue(),
                        toTypePath(annotation.targetPath()),
                        annotation.annotation().className().stringValue(),
                        true), annotation.annotation().elements()));
        element.findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(typeAnnotationVisitorSource.visitTypeAnnotation(toTypeReference(annotation.targetInfo()).getValue(),
                        toTypePath(annotation.targetPath()),
                        annotation.annotation().className().stringValue(),
                        false), annotation.annotation().elements()));
    }

    private void acceptParameterAnnotations(MethodModel methodModel, MethodVisitor methodVisitor, boolean visible) {
        int count = methodModel.findAttribute(Attributes.methodParameters())
                .map(parameters -> (int) parameters.parameters().stream().filter(parameter -> !parameter.has(AccessFlag.SYNTHETIC) && !parameter.has(AccessFlag.MANDATED)).count())
                .orElseGet(() -> methodModel.methodTypeSymbol().parameterCount());
        Optional<List<List<Annotation>>> target = visible
                ? methodModel.findAttribute(Attributes.runtimeVisibleParameterAnnotations()).map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations)
                : methodModel.findAttribute(Attributes.runtimeInvisibleParameterAnnotations()).map(RuntimeInvisibleParameterAnnotationsAttribute::parameterAnnotations);
        target.ifPresent(annotations -> {
            methodVisitor.visitAnnotableParameterCount(count, visible);
            for (int index = 0; index < annotations.size(); index++) {
                for (Annotation annotation : annotations.get(index)) {
                    appendAnnotationValues(methodVisitor.visitParameterAnnotation(index, annotation.className().stringValue(), visible), annotation.elements());
                }
            }
        });
    }

    private void acceptAttributes(AttributedElement element, Map<String, AsmAttribute> prototypes, Consumer<Attribute> consumer) {
        element.attributes().stream()
                .filter(attribute -> attribute instanceof java.lang.classfile.attribute.UnknownAttribute)
                .forEach(attribute -> {
                    AsmAttribute prototype = prototypes.get(attribute.attributeName());
                    consumer.accept(prototype == null
                            ? new AsmUnknownAttribute(((UnknownAttribute) attribute))
                            : element.findAttribute(prototype.attributeMapper()).orElseThrow().attribute);
                });
    }

    private void appendAnnotationValues(AnnotationVisitor annotationVisitor, List<AnnotationElement> elements) {
        if (annotationVisitor != null) {
            elements.forEach(element -> appendAnnotationValue(annotationVisitor, element.name().stringValue(), element.value()));
            annotationVisitor.visitEnd();
        }
    }

    private void appendAnnotationValue(AnnotationVisitor annotationVisitor, String name, AnnotationValue annotationValue) {
        if (annotationVisitor instanceof JdkClassWriter.WritingAnnotationVisitor writingAnnotationVisitor && writingAnnotationVisitor.has(classModel)) {
            writingAnnotationVisitor.add(name, annotationValue);
            return;
        }
        switch (annotationValue) {
            case AnnotationValue.OfConstant.OfBoolean value -> annotationVisitor.visit(name, value.booleanValue());
            case AnnotationValue.OfConstant.OfByte value -> annotationVisitor.visit(name, value.byteValue());
            case AnnotationValue.OfConstant.OfShort value -> annotationVisitor.visit(name, value.shortValue());
            case AnnotationValue.OfConstant.OfChar value -> annotationVisitor.visit(name, value.charValue());
            case AnnotationValue.OfConstant.OfInt value -> annotationVisitor.visit(name, value.intValue());
            case AnnotationValue.OfConstant.OfLong value -> annotationVisitor.visit(name, value.longValue());
            case AnnotationValue.OfConstant.OfFloat value -> annotationVisitor.visit(name, value.floatValue());
            case AnnotationValue.OfConstant.OfDouble value -> annotationVisitor.visit(name, value.doubleValue());
            case AnnotationValue.OfConstant.OfString value -> annotationVisitor.visit(name, toAsmConstant(value.stringValue()));
            case AnnotationValue.OfClass value -> annotationVisitor.visit(name, Type.getType(value.className().stringValue()));
            case AnnotationValue.OfAnnotation value -> appendAnnotationValues(annotationVisitor.visitAnnotation(name, value.annotation().className().stringValue()), value.annotation().elements());
            case AnnotationValue.OfEnum value -> annotationVisitor.visitEnum(name, value.className().stringValue(), value.constantName().stringValue());
            case AnnotationValue.OfArray value -> {
                Set<Character> tags = value.values().stream().map(AnnotationValue::tag).collect(Collectors.toSet());
                if (tags.size() == 1) { // Handle arrays of primitive types as direct values.
                    switch (tags.iterator().next()) {
                        case 'Z': {
                            boolean[] array = new boolean[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfBoolean) value.values().get(index)).booleanValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'B': {
                            byte[] array = new byte[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfByte) value.values().get(index)).byteValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'S': {
                            short[] array = new short[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfShort) value.values().get(index)).shortValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'C': {
                            char[] array = new char[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfChar) value.values().get(index)).charValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'I': {
                            int[] array = new int[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfInt) value.values().get(index)).intValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'L': {
                            long[] array = new long[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfLong) value.values().get(index)).longValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'F': {
                            float[] array = new float[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfFloat) value.values().get(index)).floatValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                        case 'D': {
                            double[] array = new double[value.values().size()];
                            for (int index = 0; index < value.values().size(); index++) {
                                array[index] = ((AnnotationValue.OfConstant.OfDouble) value.values().get(index)).doubleValue();
                            }
                            annotationVisitor.visit(name, array);
                            return;
                        }
                    }
                }
                AnnotationVisitor nested = annotationVisitor.visitArray(name);
                if (nested != null) {
                    value.values().forEach(entry -> appendAnnotationValue(nested, null, entry));
                    nested.visitEnd();
                }
            }
            default -> throw new UnsupportedOperationException("Unknown annotation value: " + annotationValue);
        }
    }

    private void appendCodeAnnotations(List<TypeAnnotation> typeAnnotations,
                                              boolean visible,
                                              MethodVisitor methodVisitor,
                                              Map<Label, org.objectweb.asm.Label> labels,
                                              List<Map.Entry<TypeAnnotation, Boolean>> localVariableAnnotations,
                                              Map<org.objectweb.asm.Label, List<Map.Entry<TypeAnnotation, Boolean>>> offsetTypeAnnotations) {
        typeAnnotations.forEach(typeAnnotation -> {
            switch (typeAnnotation.targetInfo()) {
                case TypeAnnotation.LocalVarTarget ignored -> localVariableAnnotations.add(Map.entry(typeAnnotation, visible));
                case TypeAnnotation.OffsetTarget value -> offsetTypeAnnotations.merge(labels.computeIfAbsent(value.target(), _ -> new org.objectweb.asm.Label()),
                        Collections.singletonList(Map.entry(typeAnnotation, visible)),
                        (left, right) -> Stream.of(left.stream(), right.stream()).flatMap(Function.identity()).collect(Collectors.toList()));
                case TypeAnnotation.CatchTarget value -> appendAnnotationValues(methodVisitor.visitTryCatchAnnotation(
                        TypeReference.newTypeReference(value.targetType().targetTypeValue()).getValue(),
                        toTypePath(typeAnnotation.targetPath()),
                        typeAnnotation.annotation().className().stringValue(),
                        visible), typeAnnotation.annotation().elements());
                default -> throw new UnsupportedOperationException("Unexpected target: " + typeAnnotation.targetInfo());
            }
        });
    }

    static Object toAsmConstant(ConstantDesc constant) {
        return switch (constant) {
            case String value -> value;
            case Integer value -> value;
            case Long value -> value;
            case Float value -> value;
            case Double value -> value;
            case ClassDesc value -> Type.getType(value.descriptorString());
            case MethodTypeDesc value -> Type.getMethodType(value.descriptorString());
            case DirectMethodHandleDesc value -> new Handle(value.refKind(),
                    toInternalName(value.owner()),
                    value.methodName(),
                    value.lookupDescriptor(),
                    value.isOwnerInterface());
            case MethodHandleDesc value -> throw new UnsupportedOperationException("Cannot map non-direct method handle to ASM constant: " + value);
            case DynamicConstantDesc<?> value -> new ConstantDynamic(value.constantName(),
                    value.constantType().descriptorString(),
                    (Handle) toAsmConstant(value.bootstrapMethod()),
                    value.bootstrapArgsList().stream().map(JdkClassReader::toAsmConstant).toArray());
        };
    }

    private static String toInternalName(ClassDesc constant) {
        if (!constant.isClassOrInterface()) {
            throw new IllegalArgumentException("Not a class or interface: " + constant);
        }
        String descriptor = constant.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1);
    }

    private static TypeReference toTypeReference(TypeAnnotation.TargetInfo targetInfo) {
        return switch (targetInfo) {
            case TypeAnnotation.SupertypeTarget value -> TypeReference.newSuperTypeReference(value.supertypeIndex());
            case TypeAnnotation.TypeParameterTarget value -> TypeReference.newTypeParameterReference(value.targetType().targetTypeValue(), value.typeParameterIndex());
            case TypeAnnotation.TypeParameterBoundTarget value -> TypeReference.newTypeParameterBoundReference(value.targetType().targetTypeValue(), value.typeParameterIndex(), value.boundIndex());
            case TypeAnnotation.ThrowsTarget value -> TypeReference.newExceptionReference(value.throwsTargetIndex());
            case TypeAnnotation.FormalParameterTarget value -> TypeReference.newFormalParameterReference(value.formalParameterIndex());
            case TypeAnnotation.EmptyTarget value -> TypeReference.newTypeReference(value.targetType().targetTypeValue());
            default -> throw new UnsupportedOperationException("Unexpected target: " + targetInfo);
        };
    }

    private static TypePath toTypePath(List<TypeAnnotation.TypePathComponent> components) {
        if (components.isEmpty()) {
            return null;
        }
        return TypePath.fromString(components.stream().map(component -> switch (component.typePathKind()) {
            case ARRAY -> "[";
            case INNER_TYPE -> ".";
            case WILDCARD -> "*";
            case TYPE_ARGUMENT -> component.typeArgumentIndex() + ";";
        }).collect(Collectors.joining()));
    }

    private static Object toAsmFrameValue(StackMapFrameInfo.VerificationTypeInfo verificationTypeInfo, Map<Label, org.objectweb.asm.Label> labels) {
        return switch (verificationTypeInfo) {
            case StackMapFrameInfo.SimpleVerificationTypeInfo value -> value.tag();
            case StackMapFrameInfo.ObjectVerificationTypeInfo value -> value.className().asInternalName();
            case StackMapFrameInfo.UninitializedVerificationTypeInfo value -> labels.computeIfAbsent(value.newTarget(), ignored -> new org.objectweb.asm.Label());
            default -> throw new UnsupportedOperationException("Unknown verification type info: " + verificationTypeInfo);
        };
    }

    @FunctionalInterface
    private interface AnnotationVisitorSource {
        AnnotationVisitor visitAnnotation(String descriptor, boolean visible);
    }

    @FunctionalInterface
    private interface TypeAnnotationVisitorSource {
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible);
    }

    private record MergedLocalVariableKey(
            org.objectweb.asm.Label start,
            org.objectweb.asm.Label end,
            String name,
            int slot
    ) {
    }

    private record MergedLocalVariableValue(
            String descriptor,
            String signature
    ) {
    }

    private static class PushbackIterator<T> implements Iterator<T> {

        private final Iterator<T> it;
        private T value;

        private PushbackIterator(Iterator<T> it) {
            this.it = it;
        }

        private void push(T value) {
            if (this.value != null) {
                throw new IllegalStateException();
            }
            this.value = value;
        }

        @Override
        public boolean hasNext() {
            return value != null || it.hasNext();
        }

        @Override
        public T next() {
            if (value == null) {
                return it.next();
            } else {
                T next = value;
                value = null;
                return next;
            }
        }
    }
}
