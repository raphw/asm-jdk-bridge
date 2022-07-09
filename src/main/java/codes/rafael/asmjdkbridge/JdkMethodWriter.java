package codes.rafael.asmjdkbridge;

import jdk.classfile.*;
import jdk.classfile.attribute.*;
import jdk.classfile.instruction.*;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.constant.*;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class JdkMethodWriter extends MethodVisitor {

    private static final Opcode[] OPCODES;

    static {
        OPCODES = new Opcode[Opcodes.IFNONNULL + 1];
        for (Opcode opcode : Opcode.values()) {
            if (opcode.bytecode() <= Opcodes.IFNONNULL) {
                OPCODES[opcode.bytecode()] = opcode;
            }
        }
    }

    private final OpenBuilder.OpenMethodBuilder openMethodBuilder;

    private final List<MethodParameterInfo> methodParameterInfos = new ArrayList<>();
    private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
    private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();
    private final List<List<Annotation>> visibleParameterAnnotations = new ArrayList<>(), invisibleParameterAnnotations = new ArrayList<>();

    private OpenBuilder.OpenCodeBuilder openCodeBuilder;
    private Map<Label, jdk.classfile.Label> labels;
    private Label current;

    JdkMethodWriter(String descriptor, OpenBuilder.OpenMethodBuilder openMethodBuilder) {
        super(Opcodes.ASM9);
        this.openMethodBuilder = openMethodBuilder;
        IntStream.range(0, Type.getMethodType(descriptor).getArgumentTypes().length).forEach(index -> {
            visibleParameterAnnotations.add(new ArrayList<>());
            invisibleParameterAnnotations.add(new ArrayList<>());
        });
    }

    @Override
    public void visitParameter(String name, int access) {
        methodParameterInfos.add(MethodParameterInfo.ofParameter(Optional.ofNullable(name), access));
    }

    private void completeParameterInfo() {
        if (!methodParameterInfos.isEmpty()) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(MethodParametersAttribute.of(methodParameterInfos)));
            methodParameterInfos.clear();
        }
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        completeParameterInfo();
        return JdkAnnotationExtractor.ofAnnotationValues(values -> {
            if (values.size() != 1) {
                throw new IllegalStateException("Expected exactly one default value: " + values.size());
            }
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(AnnotationDefaultAttribute.of(values.get(0))));
        });
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        completeParameterInfo();
        return JdkAnnotationExtractor.ofAnnotation(descriptor, annotation -> {
            if (visible) {
                visibleAnnotations.add(annotation);
            } else {
                invisibleAnnotations.add(annotation);
            }
        });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        completeParameterInfo();
        return JdkAnnotationExtractor.ofTypeAnnotation(typeRef, typePath, descriptor, typeAnnotation -> {
            if (visible) {
                visibleTypeAnnotations.add(typeAnnotation);
            } else {
                invisibleTypeAnnotations.add(typeAnnotation);
            }
        });
    }

    @Override
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
        List<List<Annotation>> annotations = visible ? visibleParameterAnnotations : invisibleParameterAnnotations;
        if (parameterCount < annotations.size()) {
            annotations.subList(parameterCount, annotations.size()).clear();
        }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        return JdkAnnotationExtractor.ofAnnotation(descriptor, annotation -> {
            if (visible) {
                visibleParameterAnnotations.get(parameter).add(annotation);
            } else {
                invisibleParameterAnnotations.get(parameter).add(annotation);
            }
        });
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        completeParameterInfo();
        // TODO: not really considered in ASM as things are
    }

    private void completeAttributes() {
        if (!visibleAnnotations.isEmpty()) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations)));
            visibleAnnotations.clear();
        }
        if (!invisibleAnnotations.isEmpty()) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations)));
            invisibleAnnotations.clear();
        }
        if (!visibleTypeAnnotations.isEmpty()) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(visibleTypeAnnotations)));
            visibleTypeAnnotations.clear();
        }
        if (!invisibleTypeAnnotations.isEmpty()) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(invisibleTypeAnnotations)));
            invisibleTypeAnnotations.clear();
        }
        if (visibleParameterAnnotations.stream().anyMatch(annotations -> !annotations.isEmpty())) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(RuntimeVisibleParameterAnnotationsAttribute.of(visibleParameterAnnotations)));
            visibleParameterAnnotations.clear();
        }
        if (invisibleParameterAnnotations.stream().anyMatch(annotations -> !annotations.isEmpty())) {
            openMethodBuilder.accept(methodBuilder -> methodBuilder.with(RuntimeInvisibleParameterAnnotationsAttribute.of(invisibleParameterAnnotations)));
            invisibleParameterAnnotations.clear();
        }
    }

    @Override
    public void visitCode() {
        completeParameterInfo();
        completeAttributes();
        openCodeBuilder = openMethodBuilder.withCode();
        labels = new HashMap<>();
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        openCodeBuilder.accept(codeBuilder -> {
            if (type == null) {
                codeBuilder.exceptionCatchAll(
                        labels.computeIfAbsent(start, ignored -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(end, ignored -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(handler, ignored -> codeBuilder.newLabel())
                );
            } else {
                codeBuilder.exceptionCatch(
                        labels.computeIfAbsent(start, ignored -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(end, ignored -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(handler, ignored -> codeBuilder.newLabel()),
                        ClassDesc.ofInternalName(type)
                );
            }
        });
    }

    @Override
    public void visitInsn(int opcode) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.with(switch (opcode) {
            case Opcodes.NOP -> NopInstruction.of();
            case Opcodes.ACONST_NULL,
                    Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                    Opcodes.LCONST_0, Opcodes.LCONST_1,
                    Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
                    Opcodes.DCONST_0, Opcodes.DCONST_1 -> ConstantInstruction.ofIntrinsic(OPCODES[opcode]);
            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> ArrayLoadInstruction.of(OPCODES[opcode]);
            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> ArrayStoreInstruction.of(OPCODES[opcode]);
            case Opcodes.POP, Opcodes.POP2, Opcodes.DUP, Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.DUP2, Opcodes.DUP2_X1, Opcodes.DUP2_X2, Opcodes.SWAP -> StackInstruction.of(OPCODES[opcode]);
            case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD,
                    Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB,
                    Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL,
                    Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV,
                    Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM,
                    Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG,
                    Opcodes.ISHL, Opcodes.LSHL, Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR,
                    Opcodes.IAND, Opcodes.LAND, Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR,
                    Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG,
                    Opcodes.ARRAYLENGTH -> OperatorInstruction.of(OPCODES[opcode]);
            case Opcodes.I2L, Opcodes.I2F, Opcodes.I2D,
                    Opcodes.L2I, Opcodes.L2F, Opcodes.L2D,
                    Opcodes.F2I, Opcodes.F2L, Opcodes.F2D,
                    Opcodes.D2I, Opcodes.D2L, Opcodes.D2F,
                    Opcodes.I2B, Opcodes.I2C, Opcodes.I2S -> ConvertInstruction.of(OPCODES[opcode]);
            case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.RETURN -> ReturnInstruction.of(OPCODES[opcode]);
            case Opcodes.ATHROW -> ThrowInstruction.of();
            case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> MonitorInstruction.of(OPCODES[opcode]);
            default -> throw new UnsupportedOperationException("Unexpected opcode: " + opcode);
        }));
        current = null;
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.with(switch (opcode) {
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ConstantInstruction.ofArgument(OPCODES[opcode], operand);
            case Opcodes.NEWARRAY -> NewPrimitiveArrayInstruction.of(switch (operand) {
                case Opcodes.T_BOOLEAN -> TypeKind.BooleanType;
                case Opcodes.T_BYTE -> TypeKind.ByteType;
                case Opcodes.T_SHORT -> TypeKind.ShortType;
                case Opcodes.T_CHAR -> TypeKind.CharType;
                case Opcodes.T_INT -> TypeKind.IntType;
                case Opcodes.T_LONG -> TypeKind.LongType;
                case Opcodes.T_FLOAT -> TypeKind.FloatType;
                case Opcodes.T_DOUBLE -> TypeKind.DoubleType;
                default -> throw new UnsupportedOperationException("Unexpected primitive array type: " + operand);
            });
            default -> throw new UnsupportedOperationException("Unexpected opcode: " + opcode);
        }));
        current = null;
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.incrementInstruction(varIndex, increment));
        current = null;
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) { // TODO: No RET, how to parse old class files like JDBC?
        openCodeBuilder.accept(codeBuilder -> codeBuilder.with(switch (opcode) {
            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> LoadInstruction.of(OPCODES[opcode], varIndex);
            case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> StoreInstruction.of(OPCODES[opcode], varIndex);
            default -> throw new UnsupportedOperationException("Unexpected opcode: " + opcode);
        }));
        current = null;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        openCodeBuilder.accept(codeBuilder -> {
            switch (opcode) {
                case Opcodes.NEW -> codeBuilder.new_(ClassDesc.ofInternalName(type));
                case Opcodes.ANEWARRAY -> codeBuilder.anewarray(ClassDesc.ofInternalName(type));
                case Opcodes.CHECKCAST -> codeBuilder.checkcast(ClassDesc.ofInternalName(type));
                case Opcodes.INSTANCEOF -> codeBuilder.instanceof_(ClassDesc.ofInternalName(type));
                default -> throw new UnsupportedOperationException("Unexpected opcode: " + opcode);
            }
        });
        current = null;
    }

    @Override
    public void visitLdcInsn(Object value) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.constantInstruction(JdkClassWriter.toJdkConstant(value)));
        current = null;
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.multianewarray(ClassDesc.ofDescriptor(descriptor), numDimensions));
        current = null;
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.branchInstruction(OPCODES[opcode], labels.computeIfAbsent(label, ignored -> codeBuilder.newLabel())));
        current = null;
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.tableswitch(
                min,
                max,
                this.labels.computeIfAbsent(dflt, ignored -> codeBuilder.newLabel()),
                IntStream.rangeClosed(min, max).mapToObj(index -> SwitchCase.of(index, this.labels.computeIfAbsent(labels[index], ignored -> codeBuilder.newLabel()))).toList()));
        current = null;
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.lookupswitch(
                this.labels.computeIfAbsent(dflt, ignored -> codeBuilder.newLabel()),
                IntStream.range(0, keys.length).mapToObj(index -> SwitchCase.of(keys[index], this.labels.computeIfAbsent(labels[index], ignored -> codeBuilder.newLabel()))).toList()));
        current = null;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.fieldInstruction(OPCODES[opcode],
                ClassDesc.ofInternalName(owner),
                name,
                ClassDesc.ofDescriptor(descriptor)));
        current = null;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        openCodeBuilder.accept(codeBuilder -> {
            switch (opcode) {
                case Opcodes.INVOKEVIRTUAL -> codeBuilder.invokevirtual(ClassDesc.ofInternalName(owner), name, MethodTypeDesc.ofDescriptor(descriptor), isInterface);
                case Opcodes.INVOKEINTERFACE -> codeBuilder.invokeinterface(ClassDesc.ofInternalName(owner), name, MethodTypeDesc.ofDescriptor(descriptor));
                case Opcodes.INVOKESTATIC -> codeBuilder.invokestatic(ClassDesc.ofInternalName(owner), name, MethodTypeDesc.ofDescriptor(descriptor), isInterface);
                case Opcodes.INVOKESPECIAL -> codeBuilder.invokespecial(ClassDesc.ofInternalName(owner), name, MethodTypeDesc.ofDescriptor(descriptor), isInterface);
                default -> throw new UnsupportedOperationException("Unexpected opcode: " + opcode);
            }
        });
        current = null;
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.invokeDynamicInstruction(DynamicCallSiteDesc.of((DirectMethodHandleDesc) JdkClassWriter.toJdkConstant(bootstrapMethodHandle),
                name,
                MethodTypeDesc.ofDescriptor(descriptor),
                Stream.of(bootstrapMethodArguments).map(JdkClassWriter::toJdkConstant).toArray(ConstantDesc[]::new))));
        current = null;
    }

    @Override
    public void visitLabel(Label label) {
        openCodeBuilder.accept(codeBuilder -> codeBuilder.labelBinding(labels.computeIfAbsent(label, ignored -> codeBuilder.newLabel())));
        current = label;
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        // TODO: No factory
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        // TODO: Should there be a better way for writing this afterwards?
        if (start != current) {
            throw new UnsupportedOperationException("JDK Writer does not support delayed writing of line number");
        }
        openCodeBuilder.accept(codeBuilder -> codeBuilder.lineNumber(line));
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        openCodeBuilder.accept(codeBuilder -> {
            codeBuilder.localVariable(index,
                    name,
                    ClassDesc.ofDescriptor(descriptor),
                    labels.computeIfAbsent(start, ignored -> codeBuilder.newLabel()),
                    labels.computeIfAbsent(end, ignored -> codeBuilder.newLabel()));
            if (signature != null) {
                codeBuilder.localVariableType(index,
                        name,
                        Signature.parseFrom(signature),
                        labels.computeIfAbsent(start, ignored -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(end, ignored -> codeBuilder.newLabel()));
            }
        });
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return JdkAnnotationExtractor.ofTypeAnnotation(typePath, (components, elements) -> {
            TypeReference typeReference = new TypeReference(typeRef);
            openCodeBuilder.accept(codeBuilder -> {
                jdk.classfile.Label label = current == null ? codeBuilder.newBoundLabel() : labels.get(current);
                TypeAnnotation typeAnnotation = TypeAnnotation.of(switch (typeReference.getSort()) {
                    case TypeReference.NEW -> TypeAnnotation.TargetInfo.ofNewExpr(label);
                    case TypeReference.CONSTRUCTOR_REFERENCE -> TypeAnnotation.TargetInfo.ofConstructorReference(label);
                    case TypeReference.METHOD_REFERENCE -> TypeAnnotation.TargetInfo.ofMethodReference(label);
                    case TypeReference.CAST -> TypeAnnotation.TargetInfo.ofCastExpr(label, typeReference.getTypeArgumentIndex());
                    case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofConstructorInvocationTypeArgument(label, typeReference.getTypeArgumentIndex());
                    case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofMethodInvocationTypeArgument(label, typeReference.getTypeArgumentIndex());
                    case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofConstructorReferenceTypeArgument(label, typeReference.getTypeArgumentIndex());
                    case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofMethodReferenceTypeArgument(label, typeReference.getTypeArgumentIndex());
                    case TypeReference.INSTANCEOF -> TypeAnnotation.TargetInfo.ofInstanceofExpr(label);
                    default -> throw new UnsupportedOperationException("Unexpected type reference: " + typeReference.getSort());
                }, components, ClassDesc.ofDescriptor(descriptor), elements);
                codeBuilder.with(visible ? RuntimeVisibleTypeAnnotationsAttribute.of(typeAnnotation) : RuntimeInvisibleTypeAnnotationsAttribute.of(typeAnnotation));
            });
        });
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return JdkAnnotationExtractor.ofTypeAnnotation(typeRef, typePath, descriptor, typeAnnotation -> openCodeBuilder.accept(codeBuilder -> {
            if (visible) {
                codeBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(typeAnnotation));
            } else {
                codeBuilder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(typeAnnotation));
            }
        }));
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] indices, String descriptor, boolean visible) {
        return JdkAnnotationExtractor.ofTypeAnnotation(typePath, (components, elements) -> {
            TypeReference typeReference = new TypeReference(typeRef);
            if (typeReference.getSort() != TypeReference.LOCAL_VARIABLE) {
                throw new UnsupportedOperationException("Unexpected type reference: " + typeReference.getSort());
            }
            openCodeBuilder.accept(codeBuilder -> {
                TypeAnnotation typeAnnotation = TypeAnnotation.of(TypeAnnotation.TargetInfo.ofLocalVariable(IntStream.range(0, indices.length).mapToObj(index -> TypeAnnotation.LocalVarTargetInfo.of(
                        labels.computeIfAbsent(start[index], ignored -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(end[index], ignored -> codeBuilder.newLabel()),
                        indices[index]
                )).toList()), components, ClassDesc.ofDescriptor(descriptor), elements);
                codeBuilder.with(visible ? RuntimeVisibleTypeAnnotationsAttribute.of(typeAnnotation) : RuntimeInvisibleTypeAnnotationsAttribute.of(typeAnnotation));
            });
        });
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) { // TODO: maximum values?
        openCodeBuilder.close();
    }

    @Override
    public void visitEnd() {
        completeParameterInfo();
        completeAttributes();
        openMethodBuilder.close();
    }
}
