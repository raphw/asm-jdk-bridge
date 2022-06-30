package codes.rafael.asmjdkbridge;

import jdk.classfile.Label;
import jdk.classfile.*;
import jdk.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import jdk.classfile.attribute.StackMapTableAttribute;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.Utf8Entry;
import jdk.classfile.instruction.*;
import org.objectweb.asm.*;

import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JdkClassReader {

    private final ClassModel classModel;

    public JdkClassReader(ClassModel classModel) {
        this.classModel = classModel;
    }

    public void accept(ClassVisitor classVisitor) {
        Map<Label, org.objectweb.asm.Label> labels = new HashMap<>();
        classVisitor.visit(classModel.minorVersion() << 16 | classModel.majorVersion(),
                classModel.flags().flagsMask()
                        | (classModel.findAttribute(Attributes.DEPRECATED).isPresent() ? Opcodes.ACC_DEPRECATED : 0)
                        | (classModel.findAttribute(Attributes.RECORD).isPresent() ? Opcodes.ACC_RECORD : 0),
                classModel.thisClass().asInternalName(),
                null,
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
        // TODO: visitAttribute
        classModel.findAttribute(Attributes.NEST_MEMBERS).stream()
                .flatMap(nestMembers -> nestMembers.nestMembers().stream())
                .forEach(nestMember -> classVisitor.visitNestMember(nestMember.asInternalName()));
        classModel.findAttribute(Attributes.PERMITTED_SUBCLASSES).stream()
                .flatMap(permittedSubclasses -> permittedSubclasses.permittedSubclasses().stream())
                .forEach(nestMember -> classVisitor.visitPermittedSubclass(nestMember.asInternalName()));
        classModel.findAttribute(Attributes.RECORD).stream()
                .flatMap(record -> record.components().stream())
                .forEach(recordComponent -> classVisitor.visitRecordComponent(recordComponent.name().stringValue(),
                    recordComponent.descriptor().stringValue(),
                    recordComponent.descriptorSymbol().descriptorString()));
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
                // TODO: visitAttribute
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
                // TODO: visitAttribute
                methodModel.code().ifPresent(code -> {
                    Map<Integer, StackMapTableAttribute.StackMapFrame> frames = new HashMap<>(methodModel.findAttribute(Attributes.STACK_MAP_TABLE)
                            .map(stackMapTable -> stackMapTable.entries().stream().collect(Collectors.toMap(StackMapTableAttribute.StackMapFrame::absoluteOffset, Function.identity())))
                            .orElse(Collections.emptyMap()));
                    methodVisitor.visitCode();
                    int offset = 0;
                    for (CodeElement element : code) {
                        StackMapTableAttribute.StackMapFrame frame = frames.remove(offset);
                        if (frame != null) {
                            methodVisitor.visitFrame(frame.frameType(),
                                    frame.declaredLocals().size(),
                                    frame.declaredLocals().stream().map(StackMapTableAttribute.VerificationTypeInfo::type).toArray(),
                                    frame.declaredStack().size(),
                                    frame.declaredStack().stream().map(StackMapTableAttribute.VerificationTypeInfo::type).toArray());
                        }
                        offset += element.sizeInBytes();
                        if (element instanceof MonitorInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof TypeCheckInstruction instruction) {
                            methodVisitor.visitTypeInsn(element.opcode().bytecode(), instruction.type().asInternalName());
                        } else if (element instanceof LoadInstruction instruction) {
                            methodVisitor.visitVarInsn(element.opcode().bytecode(), instruction.slot());
                        } else if (element instanceof OperatorInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof ReturnInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof InvokeInstruction instruction) {
                            methodVisitor.visitMethodInsn(element.opcode().bytecode(),
                                    instruction.owner().asInternalName(),
                                    instruction.name().stringValue(),
                                    instruction.type().stringValue(),
                                    instruction.isInterface());
                        } else if (element instanceof IncrementInstruction instruction) {
                            methodVisitor.visitIincInsn(instruction.slot(), instruction.constant());
                        } else if (element instanceof FieldInstruction instruction) {
                            methodVisitor.visitFieldInsn(element.opcode().bytecode(),
                                    instruction.owner().asInternalName(),
                                    instruction.name().stringValue(),
                                    instruction.type().stringValue());
                        } else if (element instanceof InvokeDynamicInstruction instruction) {
                            methodVisitor.visitInvokeDynamicInsn(instruction.name().stringValue(),
                                    instruction.type().stringValue(),
                                    (Handle) toAsmConstant(instruction.bootstrapMethod()),
                                    instruction.bootstrapArgs().stream().map(JdkClassReader::toAsmConstant).toArray());
                        } else if (element instanceof BranchInstruction instruction) {
                            methodVisitor.visitJumpInsn(element.opcode().bytecode(), labels.computeIfAbsent(instruction.target(), label -> new org.objectweb.asm.Label()));
                        } else if (element instanceof StoreInstruction instruction) {
                            methodVisitor.visitVarInsn(element.opcode().bytecode(), instruction.slot());
                        } else if (element instanceof NewReferenceArrayInstruction instruction) {
                            methodVisitor.visitTypeInsn(element.opcode().bytecode(), instruction.componentType().asInternalName());
                        } else if (element instanceof LookupSwitchInstruction instruction) {
                            methodVisitor.visitLookupSwitchInsn(labels.computeIfAbsent(instruction.defaultTarget(), label -> new org.objectweb.asm.Label()),
                                    instruction.cases().stream().mapToInt(SwitchCase::caseValue).toArray(),
                                    instruction.cases().stream().map(value -> labels.computeIfAbsent(value.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                        } else if (element instanceof TableSwitchInstruction instruction) {
                            methodVisitor.visitTableSwitchInsn(instruction.lowValue(),
                                    instruction.highValue(),
                                    labels.computeIfAbsent(instruction.defaultTarget(), label -> new org.objectweb.asm.Label()),
                                    instruction.cases().stream().map(value -> labels.computeIfAbsent(value.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                        } else if (element instanceof ArrayStoreInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof ArrayLoadInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof ConstantInstruction instruction) {
                            methodVisitor.visitLdcInsn(toAsmConstant(instruction.constantValue()));
                        } else if (element instanceof StackInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof NopInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof ThrowInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof NewObjectInstruction instruction) {
                            methodVisitor.visitTypeInsn(element.opcode().bytecode(), instruction.className().asInternalName());
                        } else if (element instanceof ConvertInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof NewMultiArrayInstruction instruction) {
                            methodVisitor.visitMultiANewArrayInsn(instruction.arrayType().asInternalName(), instruction.dimensions());
                        } else if (element instanceof NewPrimitiveArrayInstruction) {
                            methodVisitor.visitInsn(element.opcode().bytecode());
                        } else if (element instanceof LocalVariableType instruction) { // TODO: merge with LocalVariable?
                            methodVisitor.visitLocalVariable(instruction.name().stringValue(),
                                    null,
                                    instruction.signatureSymbol().signatureString(),
                                    labels.computeIfAbsent(instruction.startScope(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(instruction.endScope(), label -> new org.objectweb.asm.Label()),
                                    instruction.slot());
                        } else if (element instanceof ExceptionCatch instruction) {
                            methodVisitor.visitTryCatchBlock(labels.computeIfAbsent(instruction.tryStart(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(instruction.tryEnd(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(instruction.handler(), label -> new org.objectweb.asm.Label()),
                                    instruction.catchType().map(ClassEntry::asInternalName).orElse(null));
                        } else if (element instanceof LocalVariable instruction) { // TODO: merge with LocalVariableType?
                            methodVisitor.visitLocalVariable(instruction.name().stringValue(),
                                    instruction.type().stringValue(),
                                    null,
                                    labels.computeIfAbsent(instruction.startScope(), label -> new org.objectweb.asm.Label()),
                                    labels.computeIfAbsent(instruction.endScope(), label -> new org.objectweb.asm.Label()),
                                    instruction.slot());
                        } else if (element instanceof LineNumber instruction) {
                            org.objectweb.asm.Label label = new org.objectweb.asm.Label();
                            methodVisitor.visitLabel(label);
                            methodVisitor.visitLineNumber(instruction.line(), label);
                        } else if (element instanceof LabelTarget instruction) {
                            methodVisitor.visitLabel(labels.computeIfAbsent(instruction.label(), label -> new org.objectweb.asm.Label()));
                        } else { // TODO: allow forwarding or throw exception (also: CharacterRange)
                        }
                    }
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

    private static void acceptAnnotation(AnnotationVisitor annotationVisitor, List<AnnotationElement> elements) {
        if (annotationVisitor != null) {
            elements.forEach(element -> appendAnnotationValue(annotationVisitor, element.name().stringValue(), element.value()));
            annotationVisitor.visitEnd();
        }
    }

    private static void appendAnnotationValue(AnnotationVisitor annotationVisitor, String name, AnnotationValue annotationValue) {
        if (annotationValue instanceof AnnotationValue.OfConstant value) {
            annotationVisitor.visit(name, toAsmConstant(value.constantValue()));
        } else if (annotationValue instanceof AnnotationValue.OfClass value) {
            annotationVisitor.visit(name, Type.getType(value.className().stringValue()));
        } else if (annotationValue instanceof AnnotationValue.OfAnnotation value) {
            acceptAnnotation(annotationVisitor.visitAnnotation(name, value.annotation().className().stringValue()), value.annotation().elements());
        } else if (annotationValue instanceof AnnotationValue.OfEnum value) {
            annotationVisitor.visitEnum(name, value.className().stringValue(), value.constantName().stringValue());
        } else if (annotationValue instanceof AnnotationValue.OfArray value) {
            AnnotationVisitor nested = annotationVisitor.visitArray(name);
            if (nested != null) {
                value.values().forEach(entry -> appendAnnotationValue(nested, null, entry));
                nested.visitEnd();
            }
        } else {
            throw new UnsupportedOperationException("Cannot map annotation value of type: " + annotationValue.tag());
        }
    }

    private static Object toAsmConstant(ConstantDesc constant) {
        if (constant instanceof DynamicConstantDesc<?> value) {
            return new ConstantDynamic(value.constantName(),
                    value.constantType().descriptorString(),
                    (Handle) toAsmConstant(value.bootstrapMethod()),
                    value.bootstrapArgsList().stream().map(JdkClassReader::toAsmConstant).toArray());
        } else if (constant instanceof DirectMethodHandleDesc value) {
            return new Handle(value.refKind(),
                    value.owner().descriptorString(),
                    value.methodName(),
                    value.lookupDescriptor(),
                    value.isOwnerInterface());
        } else if (constant instanceof MethodHandleDesc) {
            throw new UnsupportedOperationException("Cannot map non-direct method handle to ASM constant");
        } else if (constant instanceof MethodTypeDesc value) {
            return Type.getMethodType(value.descriptorString());
        } else if (constant instanceof ClassDesc value) {
            return Type.getType(value.descriptorString());
        } else {
            return constant;
        }
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
}
