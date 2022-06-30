package codes.rafael.asmjdkbridge;

import jdk.classfile.Label;
import jdk.classfile.*;
import jdk.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import jdk.classfile.attribute.StackMapTableAttribute;
import jdk.classfile.attribute.UnknownAttribute;
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

public class JdkClassReader {

    private final ClassModel classModel;

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
        classModel.findAttribute(Attributes.RECORD).stream()
                .flatMap(record -> record.components().stream())
                .forEach(recordComponent -> {
                    RecordComponentVisitor recordComponentVisitor = classVisitor.visitRecordComponent(recordComponent.name().stringValue(),
                            recordComponent.descriptor().stringValue(),
                            recordComponent.descriptorSymbol().descriptorString());
                    if (recordComponentVisitor != null) {
                        acceptAnnotations(recordComponent, recordComponentVisitor::visitAnnotation, recordComponentVisitor::visitTypeAnnotation);
                        acceptAttributes(recordComponent, recordComponentVisitor::visitAttribute);
                        recordComponentVisitor.visitEnd();
                    }
                });
        classModel.findAttribute(Attributes.INNER_CLASSES).stream()
                .flatMap(innerClasses -> innerClasses.classes().stream())
                .forEach(innerClass -> classVisitor.visitInnerClass(innerClass.innerClass().asInternalName(),
                        innerClass.outerClass().map(ClassEntry::asInternalName).orElse(null),
                        innerClass.innerName().map(Utf8Entry::stringValue).orElse(null),
                        innerClass.flagsMask()));
        for (FieldModel fieldModel : classModel.fields()) {
            FieldVisitor fieldVisitor = classVisitor.visitField(fieldModel.flags().flagsMask() | (fieldModel.findAttribute(Attributes.DEPRECATED).isPresent() ? Opcodes.ACC_DEPRECATED : 0),
                    fieldModel.fieldName().stringValue(),
                    fieldModel.descriptorSymbol().descriptorString(),
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
                    methodModel.descriptorSymbol().descriptorString(),
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
                methodModel.attributes().stream()
                        .filter(attribute -> attribute instanceof UnknownAttribute)
                        .map(UnknownAttribute.class::cast)
                        .forEach(unknownAttribute -> methodVisitor.visitAttribute(new ByteArrayAttribute(unknownAttribute.attributeName(), unknownAttribute.contents())));
                methodModel.code().ifPresent(code -> {
                    Map<Integer, StackMapTableAttribute.StackMapFrame> frames = new HashMap<>(methodModel.findAttribute(Attributes.STACK_MAP_TABLE)
                            .map(stackMapTable -> stackMapTable.entries().stream().collect(Collectors.toMap(StackMapTableAttribute.StackMapFrame::absoluteOffset, Function.identity())))
                            .orElse(Collections.emptyMap()));
                    Map<MergedLocalVariableKey, MergedLocalVariableValue> localVariables = new LinkedHashMap<>();
                    methodVisitor.visitCode();
                    int offset = 0;
                    org.objectweb.asm.Label currentPositionLabel = null;
                    for (CodeElement element : code) {
                        StackMapTableAttribute.StackMapFrame frame = frames.remove(offset);
                        if (frame != null) {
                            if (expandFrames) {
                                methodVisitor.visitFrame(Opcodes.F_NEW,
                                        frame.absoluteOffset(),
                                        frame.absoluteOffset() < 1
                                                ? null
                                                : frame.effectiveLocals().stream().map(StackMapTableAttribute.VerificationTypeInfo::type).toArray(),
                                        frame.effectiveStack().size(),
                                        frame.effectiveStack().isEmpty()
                                                ? null
                                                : frame.effectiveStack().stream().map(StackMapTableAttribute.VerificationTypeInfo::type).toArray());
                            } else {
                                methodVisitor.visitFrame(frame.frameType(),
                                        frame.offsetDelta(),
                                        frame.offsetDelta() < 1
                                                ? null
                                                : frame.declaredLocals().stream().map(StackMapTableAttribute.VerificationTypeInfo::type).toArray(),
                                        frame.declaredStack().size(),
                                        frame.declaredStack().isEmpty()
                                                ? null
                                                : frame.declaredStack().stream().map(StackMapTableAttribute.VerificationTypeInfo::type).toArray());
                            }
                        }
                        offset += element.sizeInBytes();
                        switch (element) {
                            case MonitorInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case TypeCheckInstruction instruction ->
                                    methodVisitor.visitTypeInsn(element.opcode().bytecode(), instruction.type().asInternalName());
                            case LoadInstruction instruction -> {
                                if (instruction.slot() < 4) {
                                    methodVisitor.visitVarInsn(switch (instruction.typeKind()) {
                                        case BooleanType, ByteType, CharType, ShortType, IntType -> Opcodes.ILOAD;
                                        case LongType -> Opcodes.LLOAD;
                                        case FloatType -> Opcodes.FLOAD;
                                        case DoubleType -> Opcodes.DLOAD;
                                        case ReferenceType -> Opcodes.ALOAD;
                                        default ->
                                                throw new IllegalStateException("Unexpected type: " + instruction.typeKind());
                                    }, instruction.slot());
                                } else {
                                    methodVisitor.visitVarInsn(element.opcode().bytecode(), instruction.slot());
                                }
                            }
                            case OperatorInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case ReturnInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case InvokeInstruction instruction ->
                                    methodVisitor.visitMethodInsn(element.opcode().bytecode(),
                                            instruction.owner().asInternalName(),
                                            instruction.name().stringValue(),
                                            instruction.type().stringValue(),
                                            instruction.isInterface());
                            case IncrementInstruction instruction ->
                                    methodVisitor.visitIincInsn(instruction.slot(), instruction.constant());
                            case FieldInstruction instruction ->
                                    methodVisitor.visitFieldInsn(element.opcode().bytecode(),
                                            instruction.owner().asInternalName(),
                                            instruction.name().stringValue(),
                                            instruction.type().stringValue());
                            case InvokeDynamicInstruction instruction ->
                                    methodVisitor.visitInvokeDynamicInsn(instruction.name().stringValue(),
                                            instruction.type().stringValue(),
                                            (Handle) toAsmConstant(instruction.bootstrapMethod()),
                                            instruction.bootstrapArgs().stream().map(JdkClassReader::toAsmConstant).toArray());
                            case BranchInstruction instruction ->
                                    methodVisitor.visitJumpInsn(element.opcode().bytecode(), labels.computeIfAbsent(instruction.target(), label -> new org.objectweb.asm.Label()));
                            case StoreInstruction instruction -> {
                                if (instruction.slot() < 4) {
                                    methodVisitor.visitVarInsn(switch (instruction.typeKind()) {
                                        case BooleanType, ByteType, CharType, ShortType, IntType -> Opcodes.ISTORE;
                                        case LongType -> Opcodes.LSTORE;
                                        case FloatType -> Opcodes.FSTORE;
                                        case DoubleType -> Opcodes.DSTORE;
                                        case ReferenceType -> Opcodes.ASTORE;
                                        default ->
                                                throw new IllegalStateException("Unexpected type: " + instruction.typeKind());
                                    }, instruction.slot());
                                } else {
                                    methodVisitor.visitVarInsn(element.opcode().bytecode(), instruction.slot());
                                }
                            }
                            case NewReferenceArrayInstruction instruction ->
                                    methodVisitor.visitTypeInsn(element.opcode().bytecode(), instruction.componentType().asInternalName());
                            case LookupSwitchInstruction instruction ->
                                    methodVisitor.visitLookupSwitchInsn(labels.computeIfAbsent(instruction.defaultTarget(), label -> new org.objectweb.asm.Label()),
                                            instruction.cases().stream().mapToInt(SwitchCase::caseValue).toArray(),
                                            instruction.cases().stream().map(value -> labels.computeIfAbsent(value.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                            case TableSwitchInstruction instruction ->
                                    methodVisitor.visitTableSwitchInsn(instruction.lowValue(),
                                            instruction.highValue(),
                                            labels.computeIfAbsent(instruction.defaultTarget(), label -> new org.objectweb.asm.Label()),
                                            instruction.cases().stream().map(value -> labels.computeIfAbsent(value.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                            case ArrayStoreInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case ArrayLoadInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case ConstantInstruction instruction ->
                                    methodVisitor.visitLdcInsn(toAsmConstant(instruction.constantValue()));
                            case StackInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case NopInstruction instruction -> methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case ThrowInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case NewObjectInstruction instruction ->
                                    methodVisitor.visitTypeInsn(element.opcode().bytecode(), instruction.className().asInternalName());
                            case ConvertInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case NewMultiArrayInstruction instruction ->
                                    methodVisitor.visitMultiANewArrayInsn(instruction.arrayType().asInternalName(), instruction.dimensions());
                            case NewPrimitiveArrayInstruction instruction ->
                                    methodVisitor.visitInsn(instruction.opcode().bytecode());
                            case LocalVariableType instruction -> localVariables.compute(new MergedLocalVariableKey(
                                    labels.computeIfAbsent(instruction.startScope(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(instruction.endScope(), label -> new org.objectweb.asm.Label()),
                                    instruction.name().stringValue(),
                                    instruction.slot()
                            ), (key, value) -> new MergedLocalVariableValue(value == null ? null : value.descriptor, instruction.signature().stringValue()));
                            case ExceptionCatch instruction ->
                                    methodVisitor.visitTryCatchBlock(labels.computeIfAbsent(instruction.tryStart(), label -> new org.objectweb.asm.Label()),
                                            labels.computeIfAbsent(instruction.tryEnd(), label -> new org.objectweb.asm.Label()),
                                            labels.computeIfAbsent(instruction.handler(), label -> new org.objectweb.asm.Label()),
                                            instruction.catchType().map(ClassEntry::asInternalName).orElse(null));
                            case LocalVariable instruction -> localVariables.compute(new MergedLocalVariableKey(
                                    labels.computeIfAbsent(instruction.startScope(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(instruction.endScope(), label -> new org.objectweb.asm.Label()),
                                    instruction.name().stringValue(),
                                    instruction.slot()
                            ), (key, value) -> new MergedLocalVariableValue(instruction.typeSymbol().descriptorString(), value == null ? null : value.signature));
                            case LineNumber instruction -> {
                                if (currentPositionLabel == null) {
                                    currentPositionLabel = new org.objectweb.asm.Label();
                                    methodVisitor.visitLabel(currentPositionLabel);
                                }
                                methodVisitor.visitLineNumber(instruction.line(), currentPositionLabel);
                            }
                            case LabelTarget instruction -> {
                                currentPositionLabel = labels.computeIfAbsent(instruction.label(), label -> new org.objectweb.asm.Label());
                                methodVisitor.visitLabel(currentPositionLabel);
                            }
                            case CharacterRange ignored -> {
                                // TODO: How to write this back to a byte array?
                            }
                            default -> throw new UnsupportedOperationException("Failed to ");
                        }
                        if (element instanceof Instruction) {
                            currentPositionLabel = null;
                        }
                    }
                    localVariables.forEach((key, value) -> methodVisitor.visitLocalVariable(key.name(),
                            value.descriptor(),
                            value.signature(),
                            key.start(),
                            key.end(),
                            key.slot()));
                    // TODO: visitInsAnnotation, visitTryCatchAnnotation, visitLocalVariableAnnotation
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
                .forEach(annotation -> acceptAnnotation(annotationVisitorSource.visitAnnotation(annotation.className().stringValue(), true), annotation.elements()));
        element.findAttribute(Attributes.RUNTIME_INVISIBLE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> acceptAnnotation(annotationVisitorSource.visitAnnotation(annotation.className().stringValue(), false), annotation.elements()));
        element.findAttribute(Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> acceptAnnotation(typeAnnotationVisitorSource.visitTypeAnnotation(annotation.targetInfo().targetType().targetTypeValue(),
                        toTypePath(annotation.targetPath()),
                        annotation.className().stringValue(),
                        true), annotation.elements()));
        element.findAttribute(Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS).stream()
                .flatMap(annotations -> annotations.annotations().stream())
                .forEach(annotation -> acceptAnnotation(typeAnnotationVisitorSource.visitTypeAnnotation(annotation.targetInfo().targetType().targetTypeValue(),
                        toTypePath(annotation.targetPath()),
                        annotation.className().stringValue(),
                        false), annotation.elements()));
    }

    private static void acceptParameterAnnotations(MethodModel methodModel, MethodVisitor methodVisitor, boolean visible) {
        int count = methodModel.descriptorSymbol().parameterCount();
        Optional<List<List<Annotation>>> target = visible
                ? methodModel.findAttribute(Attributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS).map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations)
                : methodModel.findAttribute(Attributes.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS).map(RuntimeInvisibleParameterAnnotationsAttribute::parameterAnnotations);
        target.ifPresent(annotations -> {
            if (annotations.size() != count) {
                methodVisitor.visitAnnotableParameterCount(count, visible);
            }
            for (int index = 0; index < annotations.size(); index++) {
                for (Annotation annotation : annotations.get(index)) {
                    acceptAnnotation(methodVisitor.visitParameterAnnotation(index, annotation.className().stringValue(), visible), annotation.elements());
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

    private static void acceptAnnotation(AnnotationVisitor annotationVisitor, List<AnnotationElement> elements) {
        if (annotationVisitor != null) {
            elements.forEach(element -> appendAnnotationValue(annotationVisitor, element.name().stringValue(), element.value()));
            annotationVisitor.visitEnd();
        }
    }

    private static void appendAnnotationValue(AnnotationVisitor annotationVisitor, String name, AnnotationValue annotationValue) {
        switch (annotationValue) {
            case AnnotationValue.OfConstant value ->
                    annotationVisitor.visit(name, toAsmConstant(value.constantValue()));
            case AnnotationValue.OfClass value ->
                    annotationVisitor.visit(name, Type.getType(value.className().stringValue()));
            case AnnotationValue.OfAnnotation value ->
                    acceptAnnotation(annotationVisitor.visitAnnotation(name, value.annotation().className().stringValue()), value.annotation().elements());
            case AnnotationValue.OfEnum value ->
                    annotationVisitor.visitEnum(name, value.className().stringValue(), value.constantName().stringValue());
            case AnnotationValue.OfArray value -> {
                AnnotationVisitor nested = annotationVisitor.visitArray(name);
                if (nested != null) {
                    value.values().forEach(entry -> appendAnnotationValue(nested, null, entry));
                    nested.visitEnd();
                }
            }
            default -> throw new UnsupportedOperationException();
        }
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
                    value.owner().descriptorString(),
                    value.methodName(),
                    value.lookupDescriptor(),
                    value.isOwnerInterface());
            case MethodHandleDesc value ->
                    throw new UnsupportedOperationException("Cannot map non-direct method handle to ASM constant: " + value);
            case DynamicConstantDesc<?> value -> new ConstantDynamic(value.constantName(),
                    value.constantType().descriptorString(),
                    (Handle) toAsmConstant(value.bootstrapMethod()),
                    value.bootstrapArgsList().stream().map(JdkClassReader::toAsmConstant).toArray());
            default -> throw new UnsupportedOperationException("Unknown constant: " + constant);
        };
    }

    private static TypePath toTypePath(List<TypeAnnotation.TypePathComponent> components) {
        return TypePath.fromString(components.stream().map(component -> switch (component.typePathKind()) {
            case ARRAY -> "[";
            case INNER_TYPE -> ".";
            case WILDCARD -> "*";
            case TYPE_ARGUMENT -> component.typeArgumentIndex() + ";";
        }).collect(Collectors.joining()));
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
