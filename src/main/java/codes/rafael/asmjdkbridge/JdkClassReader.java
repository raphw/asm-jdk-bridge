package codes.rafael.asmjdkbridge;

import jdk.classfile.Label;
import jdk.classfile.*;
import jdk.classfile.attribute.*;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.Utf8Entry;
import jdk.classfile.instruction.*;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.*;

import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JdkClassReader {

    private final ClassModel classModel; // TODO: Would be more desirable if a ClassWriter could be handled similarly, prototype with sharing.

    public JdkClassReader(ClassModel classModel) {
        this.classModel = classModel;
    }

    public void accept(ClassVisitor classVisitor) {
        accept(classVisitor, false);
    }

    public void accept(ClassVisitor classVisitor, boolean expandFrames) {
        Map<Label, org.objectweb.asm.Label> labels = new HashMap<>();
        classVisitor.visit(classModel.minorVersion() << 16 | classModel.majorVersion(),
                classModel.flags().flagsMask()
                        | (classModel.findAttribute(Attributes.DEPRECATED).isPresent() ? Opcodes.ACC_DEPRECATED : 0)
                        | (classModel.findAttribute(Attributes.RECORD).isPresent() ? Opcodes.ACC_RECORD : 0),
                classModel.thisClass().asInternalName(),
                classModel.findAttribute(Attributes.SIGNATURE).map(signature -> signature.signature().stringValue()).orElse(null),
                classModel.superclass().map(ClassEntry::asInternalName).orElse(null),
                classModel.interfaces().stream().map(ClassEntry::asInternalName).toArray(String[]::new));
        String sourceFile = classModel.findAttribute(Attributes.SOURCE_FILE)
                .map(attribute -> attribute.sourceFile().stringValue())
                .orElse(null);
        String debug = classModel.findAttribute(Attributes.SOURCE_DEBUG_EXTENSION)
                .map(attribute -> new String(attribute.contents(), StandardCharsets.UTF_8))
                .orElse(null);
        if (sourceFile != null || debug != null) {
            classVisitor.visitSource(sourceFile, debug);
        }
        classModel.findAttribute(Attributes.MODULE).ifPresent(module -> {
            ModuleVisitor moduleVisitor = classVisitor.visitModule(module.moduleName().name().stringValue(),
                    module.moduleFlagsMask(),
                    module.moduleVersion().map(Utf8Entry::stringValue).orElse(null));
            if (moduleVisitor != null) {
                classModel.findAttribute(Attributes.MODULE_MAIN_CLASS)
                        .map(moduleMainClass -> moduleMainClass.mainClass().asInternalName())
                        .ifPresent(moduleVisitor::visitMainClass);
                classModel.findAttribute(Attributes.MODULE_PACKAGES).stream()
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
        classModel.findAttribute(Attributes.NEST_HOST).ifPresent(nestHost -> classVisitor.visitNestHost(nestHost.nestHost().asInternalName()));
        classModel.findAttribute(Attributes.ENCLOSING_METHOD).ifPresent(enclosingMethod -> classVisitor.visitOuterClass(enclosingMethod.enclosingClass().asInternalName(),
                enclosingMethod.enclosingMethod().map(value -> value.name().stringValue()).orElse(null),
                enclosingMethod.enclosingMethod().map(value -> value.type().stringValue()).orElse(null)));
        acceptAnnotations(classModel, classVisitor::visitAnnotation, classVisitor::visitTypeAnnotation);
        acceptAttributes(classModel, classVisitor::visitAttribute);
        classModel.findAttribute(Attributes.NEST_MEMBERS).stream()
                .flatMap(nestMembers -> nestMembers.nestMembers().stream())
                .forEach(nestMember -> classVisitor.visitNestMember(nestMember.asInternalName()));
        classModel.findAttribute(Attributes.PERMITTED_SUBCLASSES).stream()
                .flatMap(permittedSubclasses -> permittedSubclasses.permittedSubclasses().stream())
                .forEach(nestMember -> classVisitor.visitPermittedSubclass(nestMember.asInternalName()));
        classModel.findAttribute(Attributes.INNER_CLASSES).stream()
                .flatMap(innerClasses -> innerClasses.classes().stream())
                .forEach(innerClass -> classVisitor.visitInnerClass(innerClass.innerClass().asInternalName(),
                        innerClass.outerClass().map(ClassEntry::asInternalName).orElse(null),
                        innerClass.innerName().map(Utf8Entry::stringValue).orElse(null),
                        innerClass.flagsMask()));
        classModel.findAttribute(Attributes.RECORD).stream()
                .flatMap(record -> record.components().stream())
                .forEach(recordComponent -> {
                    RecordComponentVisitor recordComponentVisitor = classVisitor.visitRecordComponent(recordComponent.name().stringValue(),
                            recordComponent.descriptor().stringValue(),
                            recordComponent.findAttribute(Attributes.SIGNATURE).map(signature -> signature.signature().stringValue()).orElse(null));
                    if (recordComponentVisitor != null) {
                        acceptAnnotations(recordComponent, recordComponentVisitor::visitAnnotation, recordComponentVisitor::visitTypeAnnotation);
                        acceptAttributes(recordComponent, recordComponentVisitor::visitAttribute);
                        recordComponentVisitor.visitEnd();
                    }
                });
        for (FieldModel fieldModel : classModel.fields()) {
            FieldVisitor fieldVisitor = classVisitor.visitField(fieldModel.flags().flagsMask() | (fieldModel.findAttribute(Attributes.DEPRECATED).isPresent() ? Opcodes.ACC_DEPRECATED : 0),
                    fieldModel.fieldName().stringValue(),
                    fieldModel.fieldType().stringValue(),
                    fieldModel.findAttribute(Attributes.SIGNATURE).map(signature -> signature.signature().stringValue()).orElse(null),
                    fieldModel.findAttribute(Attributes.CONSTANT_VALUE).map(constantValue -> toAsmConstant(constantValue.constant().constantValue())).orElse(null));
            if (fieldVisitor != null) {
                acceptAnnotations(fieldModel, fieldVisitor::visitAnnotation, fieldVisitor::visitTypeAnnotation);
                acceptAttributes(fieldModel, fieldVisitor::visitAttribute);
                fieldVisitor.visitEnd();
            }
        }
        for (MethodModel methodModel : classModel.methods()) {
            MethodVisitor methodVisitor = classVisitor.visitMethod(methodModel.flags().flagsMask() | (methodModel.findAttribute(Attributes.DEPRECATED).isPresent() ? Opcodes.ACC_DEPRECATED : 0),
                    methodModel.methodName().stringValue(),
                    methodModel.methodType().stringValue(),
                    methodModel.findAttribute(Attributes.SIGNATURE).map(signature -> signature.signature().stringValue()).orElse(null),
                    methodModel.findAttribute(Attributes.EXCEPTIONS).map(exceptions -> exceptions.exceptions().stream().map(ClassEntry::asInternalName).toArray(String[]::new)).orElse(null));
            if (methodVisitor != null) {
                methodModel.findAttribute(Attributes.METHOD_PARAMETERS).stream()
                        .flatMap(methodParameters -> methodParameters.parameters().stream())
                        .forEach(methodParameter -> methodVisitor.visitParameter(methodParameter.name().map(Utf8Entry::stringValue).orElse(null), methodParameter.flagsMask()));
                methodModel.findAttribute(Attributes.ANNOTATION_DEFAULT).ifPresent(annotationDefault -> {
                    AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotationDefault();
                    if (annotationVisitor != null) {
                        appendAnnotationValue(annotationVisitor, null, annotationDefault.defaultValue());
                        annotationVisitor.visitEnd();
                    }
                });
                acceptAnnotations(methodModel, methodVisitor::visitAnnotation, methodVisitor::visitTypeAnnotation);
                acceptParameterAnnotations(methodModel, methodVisitor, true);
                acceptParameterAnnotations(methodModel, methodVisitor, false);
                acceptAttributes(methodModel, methodVisitor::visitAttribute);
                methodModel.code().ifPresent(code -> {
                    // TODO: Stack map frames should use labels rather then offsets in the API?
                    Map<Integer, StackMapTableAttribute.StackMapFrame> frames = code.findAttribute(Attributes.STACK_MAP_TABLE)
                            .map(stackMapTable -> stackMapTable.entries().stream().collect(Collectors.toMap(StackMapTableAttribute.StackMapFrame::absoluteOffset, Function.identity())))
                            .orElse(Collections.emptyMap());
                    Set<Integer> offsets = frames.values().stream()
                            .flatMap(stackMapFrame -> Stream.concat(stackMapFrame.effectiveStack().stream(), stackMapFrame.effectiveLocals().stream()))
                            .filter(verificationTypeInfo -> verificationTypeInfo instanceof StackMapTableAttribute.UninitializedVerificationTypeInfo)
                            .map(StackMapTableAttribute.UninitializedVerificationTypeInfo.class::cast)
                            .map(StackMapTableAttribute.UninitializedVerificationTypeInfo::offset)
                            .collect(Collectors.toSet());
                    Map<Integer, org.objectweb.asm.Label> offsetLabels = new HashMap<>();
                    Map<MergedLocalVariableKey, MergedLocalVariableValue> localVariables = new LinkedHashMap<>();
                    Map<org.objectweb.asm.Label, List<Map.Entry<TypeAnnotation, Boolean>>> offsetTypeAnnotations = new HashMap<>();
                    List<Map.Entry<TypeAnnotation, Boolean>> localVariableAnnotations = new ArrayList<>();
                    methodVisitor.visitCode();
                    int offset = 0;
                    org.objectweb.asm.Label currentPositionLabel = null;
                    for (CodeElement element : code) {
                        if (element instanceof Instruction) {
                            if (offsets.contains(offset)) {
                                org.objectweb.asm.Label label = offsetLabels.get(offset);
                                if (label != null) {
                                    methodVisitor.visitLabel(label);
                                } else if (currentPositionLabel != null) {
                                    offsetLabels.put(offset, currentPositionLabel);
                                } else {
                                    currentPositionLabel = new org.objectweb.asm.Label();
                                    methodVisitor.visitLabel(currentPositionLabel);
                                    offsetLabels.put(offset, currentPositionLabel);
                                }
                            }
                            StackMapTableAttribute.StackMapFrame frame = frames.get(offset);
                            if (frame != null) {
                                if (expandFrames) {
                                    methodVisitor.visitFrame(Opcodes.F_NEW,
                                            frame.effectiveLocals().size(),
                                            frame.effectiveLocals().isEmpty() ? null : frame.effectiveLocals().stream()
                                                    .map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, offsetLabels))
                                                    .toArray(),
                                            frame.effectiveStack().size(),
                                            frame.effectiveStack().isEmpty() ? null : frame.effectiveStack().stream()
                                                    .map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, offsetLabels))
                                                    .toArray());
                                } else {
                                    switch (frame) {
                                        case StackMapTableAttribute.StackMapFrame.Same ignored -> methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                                        case StackMapTableAttribute.StackMapFrame.Same1 same1 -> methodVisitor.visitFrame(Opcodes.F_SAME1,
                                                0, null,
                                                1, new Object[] {toAsmFrameValue(same1.declaredStack(), offsetLabels)});
                                        case StackMapTableAttribute.StackMapFrame.Append append -> methodVisitor.visitFrame(Opcodes.F_APPEND,
                                                append.declaredLocals().size(), append.declaredLocals().stream().map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, offsetLabels)).toArray(),
                                                0, null);
                                        case StackMapTableAttribute.StackMapFrame.Chop chop -> methodVisitor.visitFrame(Opcodes.F_CHOP,
                                                chop.choppedLocals().size(), null,
                                                0, null);
                                        case StackMapTableAttribute.StackMapFrame.Full full -> methodVisitor.visitFrame(Opcodes.F_FULL,
                                                full.declaredLocals().size(), full.declaredLocals().stream().map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, offsetLabels)).toArray(),
                                                full.declaredStack().size(), full.declaredStack().stream().map(verificationTypeInfo -> toAsmFrameValue(verificationTypeInfo, offsetLabels)).toArray());
                                        default -> throw new UnsupportedOperationException("Unknown frame type: " + frame);
                                    }
                                }
                            }
                        }
                        switch (element) { // TODO: Add toString methods to all "impl"?
                            case MonitorInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case TypeCheckInstruction value -> methodVisitor.visitTypeInsn(element.opcode().bytecode(), value.type().asInternalName());
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
                            case InvokeInstruction value -> methodVisitor.visitMethodInsn(element.opcode().bytecode(),
                                    value.owner().asInternalName(),
                                    value.name().stringValue(),
                                    value.type().stringValue(),
                                    value.isInterface());
                            case IncrementInstruction value -> methodVisitor.visitIincInsn(value.slot(), value.constant());
                            case FieldInstruction value -> methodVisitor.visitFieldInsn(element.opcode().bytecode(),
                                    value.owner().asInternalName(),
                                    value.name().stringValue(),
                                    value.type().stringValue());
                            case InvokeDynamicInstruction value -> methodVisitor.visitInvokeDynamicInsn(value.name().stringValue(),
                                    value.type().stringValue(),
                                    (Handle) toAsmConstant(value.bootstrapMethod()),
                                    value.bootstrapArgs().stream().map(JdkClassReader::toAsmConstant).toArray());
                            case BranchInstruction value -> methodVisitor.visitJumpInsn(
                                    value.opcode() == Opcode.GOTO_W ? Opcodes.GOTO : value.opcode().bytecode(),
                                    labels.computeIfAbsent(value.target(), label -> new org.objectweb.asm.Label()));
                            case StoreInstruction value -> methodVisitor.visitVarInsn(switch (value.typeKind()) {
                                case BooleanType, ByteType, CharType, ShortType, IntType -> Opcodes.ISTORE;
                                case LongType -> Opcodes.LSTORE;
                                case FloatType -> Opcodes.FSTORE;
                                case DoubleType -> Opcodes.DSTORE;
                                case ReferenceType -> Opcodes.ASTORE;
                                default -> throw new IllegalStateException("Unexpected type: " + value.typeKind());
                            }, value.slot());
                            case NewReferenceArrayInstruction value -> methodVisitor.visitTypeInsn(element.opcode().bytecode(), value.componentType().asInternalName());
                            case LookupSwitchInstruction value -> methodVisitor.visitLookupSwitchInsn(labels.computeIfAbsent(value.defaultTarget(), label -> new org.objectweb.asm.Label()),
                                    value.cases().stream().mapToInt(SwitchCase::caseValue).toArray(),
                                    value.cases().stream().map(aCase -> labels.computeIfAbsent(aCase.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                            case TableSwitchInstruction value -> methodVisitor.visitTableSwitchInsn(value.lowValue(),
                                    value.highValue(),
                                    labels.computeIfAbsent(value.defaultTarget(), label -> new org.objectweb.asm.Label()),
                                    value.cases().stream().map(aCase -> labels.computeIfAbsent(aCase.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
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
                            case NewObjectInstruction value -> methodVisitor.visitTypeInsn(element.opcode().bytecode(), value.className().asInternalName());
                            case ConvertInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case NewMultiArrayInstruction value -> methodVisitor.visitMultiANewArrayInsn(value.arrayType().asInternalName(), value.dimensions());
                            case NewPrimitiveArrayInstruction value -> methodVisitor.visitInsn(value.opcode().bytecode());
                            case LocalVariableType value -> localVariables.compute(new MergedLocalVariableKey(
                                    labels.computeIfAbsent(value.startScope(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.endScope(), label -> new org.objectweb.asm.Label()),
                                    value.name().stringValue(),
                                    value.slot()
                            ), (key, values) -> new MergedLocalVariableValue(values == null ? null : values.descriptor, value.signature().stringValue()));
                            case ExceptionCatch value -> methodVisitor.visitTryCatchBlock(labels.computeIfAbsent(value.tryStart(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.tryEnd(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.handler(), label -> new org.objectweb.asm.Label()),
                                    value.catchType().map(ClassEntry::asInternalName).orElse(null));
                            case LocalVariable value -> localVariables.compute(new MergedLocalVariableKey(
                                    labels.computeIfAbsent(value.startScope(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(value.endScope(), label -> new org.objectweb.asm.Label()),
                                    value.name().stringValue(),
                                    value.slot()
                            ), (key, values) -> new MergedLocalVariableValue(value.typeSymbol().descriptorString(), values == null ? null : values.signature));
                            case LineNumber value -> {
                                if (currentPositionLabel == null) {
                                    currentPositionLabel = new org.objectweb.asm.Label();
                                    methodVisitor.visitLabel(currentPositionLabel);
                                }
                                methodVisitor.visitLineNumber(value.line(), currentPositionLabel);
                            }
                            case LabelTarget value -> {
                                currentPositionLabel = labels.computeIfAbsent(value.label(), label -> new org.objectweb.asm.Label());
                                methodVisitor.visitLabel(currentPositionLabel);
                            }
                            case CharacterRange ignored -> {
                                // TODO: Is there an easier way to deconstruct to byte array then by knowing spec?
                                // Note: This would allow for better forward compatibility if unknown attributes should just be dumped to binary.
                            }
                            case RuntimeVisibleTypeAnnotationsAttribute value -> appendCodeAnnotations(value.annotations(), true, methodVisitor, labels, localVariableAnnotations, offsetTypeAnnotations);
                            case RuntimeInvisibleTypeAnnotationsAttribute value -> appendCodeAnnotations(value.annotations(), false, methodVisitor, labels, localVariableAnnotations, offsetTypeAnnotations);
                            default -> throw new UnsupportedOperationException("Unknown value: " + element);
                        }
                        if (element instanceof Instruction) {
                            offsetTypeAnnotations.getOrDefault(currentPositionLabel, Collections.emptyList()).forEach(entry -> appendAnnotationValues(methodVisitor.visitInsnAnnotation(
                                    TypeReference.newTypeReference(entry.getKey().targetInfo().targetType().targetTypeValue()).getValue(),
                                    toTypePath(entry.getKey().targetPath()),
                                    entry.getKey().className().stringValue(),
                                    entry.getValue()), entry.getKey().elements()));
                            currentPositionLabel = null;
                        }
                        offset += element.sizeInBytes();
                    }
                    localVariables.forEach((key, value) -> methodVisitor.visitLocalVariable(key.name(),
                            value.descriptor(),
                            value.signature(),
                            key.start(),
                            key.end(),
                            key.slot()));
                    localVariableAnnotations.forEach(entry -> {
                        TypeAnnotation.LocalVarTarget target = (TypeAnnotation.LocalVarTarget) entry.getKey().targetInfo();
                        appendAnnotationValues(methodVisitor.visitLocalVariableAnnotation(
                                TypeReference.newTypeReference(entry.getKey().targetInfo().targetType().targetTypeValue()).getValue(),
                                toTypePath(entry.getKey().targetPath()),
                                target.table().stream().map(localVarTargetInfo -> labels.get(localVarTargetInfo.startLabel())).toArray(org.objectweb.asm.Label[]::new),
                                target.table().stream().map(localVarTargetInfo -> labels.get(localVarTargetInfo.endLabel())).toArray(org.objectweb.asm.Label[]::new),
                                target.table().stream().mapToInt(TypeAnnotation.LocalVarTargetInfo::index).toArray(),
                                entry.getKey().className().stringValue(),
                                entry.getValue()), entry.getKey().elements());
                    });
                    methodVisitor.visitMaxs(code.maxStack(), code.maxLocals());
                });
                methodVisitor.visitEnd();
            }
        }
        classVisitor.visitEnd();
    }

    private static void acceptAnnotations(AttributedElement element, AnnotationVisitorSource annotationVisitorSource, TypeAnnotationVisitorSource typeAnnotationVisitorSource) {
        element.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(annotationVisitorSource.visitAnnotation(annotation.className().stringValue(), true), annotation.elements()));
        element.findAttribute(Attributes.RUNTIME_INVISIBLE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(annotationVisitorSource.visitAnnotation(annotation.className().stringValue(), false), annotation.elements()));
        element.findAttribute(Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(typeAnnotationVisitorSource.visitTypeAnnotation(toTypeReference(annotation.targetInfo()).getValue(),
                        toTypePath(annotation.targetPath()),
                        annotation.className().stringValue(),
                        true), annotation.elements()));
        element.findAttribute(Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> appendAnnotationValues(typeAnnotationVisitorSource.visitTypeAnnotation(toTypeReference(annotation.targetInfo()).getValue(),
                        toTypePath(annotation.targetPath()),
                        annotation.className().stringValue(),
                        false), annotation.elements()));
    }

    private static void acceptParameterAnnotations(MethodModel methodModel, MethodVisitor methodVisitor, boolean visible) {
        int count = methodModel.methodTypeSymbol().parameterCount();
        Optional<List<List<Annotation>>> target = visible
                ? methodModel.findAttribute(Attributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS).map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations)
                : methodModel.findAttribute(Attributes.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS).map(RuntimeInvisibleParameterAnnotationsAttribute::parameterAnnotations);
        target.ifPresent(annotations -> {
            methodVisitor.visitAnnotableParameterCount(count, visible);
            for (int index = 0; index < annotations.size(); index++) {
                for (Annotation annotation : annotations.get(index)) {
                    appendAnnotationValues(methodVisitor.visitParameterAnnotation(index, annotation.className().stringValue(), visible), annotation.elements());
                }
            }
        });
    }

    private static void acceptAttributes(AttributedElement element, Consumer<Attribute> consumer) {
        element.attributes().stream()
                .filter(attribute -> attribute instanceof UnknownAttribute)
                .map(UnknownAttribute.class::cast)
                .forEach(unknownAttribute -> consumer.accept(new ByteArrayAttribute(unknownAttribute.attributeName(), unknownAttribute.contents())));
    }

    private static void appendAnnotationValues(AnnotationVisitor annotationVisitor, List<AnnotationElement> elements) {
        if (annotationVisitor != null) {
            elements.forEach(element -> appendAnnotationValue(annotationVisitor, element.name().stringValue(), element.value()));
            annotationVisitor.visitEnd();
        }
    }

    private static void appendAnnotationValue(AnnotationVisitor annotationVisitor, String name, AnnotationValue annotationValue) {
        switch (annotationValue) {
            case AnnotationValue.OfConstant value -> annotationVisitor.visit(name, toAsmConstant(value.constantValue()));
            case AnnotationValue.OfClass value -> annotationVisitor.visit(name, Type.getType(value.className().stringValue()));
            case AnnotationValue.OfAnnotation value -> appendAnnotationValues(annotationVisitor.visitAnnotation(name, value.annotation().className().stringValue()), value.annotation().elements());
            case AnnotationValue.OfEnum value -> annotationVisitor.visitEnum(name, value.className().stringValue(), value.constantName().stringValue());
            case AnnotationValue.OfArray value -> {
                AnnotationVisitor nested = annotationVisitor.visitArray(name);
                if (nested != null) {
                    value.values().forEach(entry -> appendAnnotationValue(nested, null, entry));
                    nested.visitEnd();
                }
            }
            default -> throw new UnsupportedOperationException("Unknown annotation value: " + annotationValue);
        }
    }

    private static void appendCodeAnnotations(List<TypeAnnotation> typeAnnotations,
                                              boolean visible,
                                              MethodVisitor methodVisitor,
                                              Map<Label, org.objectweb.asm.Label> labels,
                                              List<Map.Entry<TypeAnnotation, Boolean>> localVariableAnnotations,
                                              Map<org.objectweb.asm.Label, List<Map.Entry<TypeAnnotation, Boolean>>> offsetTypeAnnotations) {
        typeAnnotations.forEach(typeAnnotation -> {
            switch (typeAnnotation.targetInfo()) {
                case TypeAnnotation.LocalVarTarget ignored -> localVariableAnnotations.add(Map.entry(typeAnnotation, visible));
                case TypeAnnotation.OffsetTarget value -> offsetTypeAnnotations.merge(labels.computeIfAbsent(value.target(), label -> new org.objectweb.asm.Label()),
                        Collections.singletonList(Map.entry(typeAnnotation, visible)),
                        (left, right) -> Stream.of(left.stream(), right.stream()).flatMap(Function.identity()).collect(Collectors.toList()));
                case TypeAnnotation.CatchTarget value -> appendAnnotationValues(methodVisitor.visitTryCatchAnnotation( // TODO: verify annotation index?
                        TypeReference.newTypeReference(value.targetType().targetTypeValue()).getValue(),
                        toTypePath(typeAnnotation.targetPath()),
                        typeAnnotation.className().stringValue(),
                        visible), typeAnnotation.elements());
                default -> throw new UnsupportedOperationException("Unexpected target: " + typeAnnotation.targetInfo());
            }
        });
    }

    private static Object toAsmConstant(ConstantDesc constant) {
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
            default -> throw new UnsupportedOperationException("Unknown constant: " + constant);
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

    private static Object toAsmFrameValue(StackMapTableAttribute.VerificationTypeInfo verificationTypeInfo, Map<Integer, org.objectweb.asm.Label> labels) {
        return switch (verificationTypeInfo) {
            case StackMapTableAttribute.SimpleVerificationTypeInfo value -> value.type().tag();
            case StackMapTableAttribute.ObjectVerificationTypeInfo value -> value.className().asInternalName();
            case StackMapTableAttribute.UninitializedVerificationTypeInfo value -> labels.computeIfAbsent(value.offset(), ignored -> new org.objectweb.asm.Label());
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
}
