package codes.rafael.asmjdkbridge;

import jdk.classfile.OpenBuilder;
import jdk.classfile.instruction.NopInstruction;
import org.objectweb.asm.*;

class JdkMethodWriter extends MethodVisitor {

    private final OpenBuilder.OpenMethodBuilder openMethodBuilder;

    private OpenBuilder.OpenCodeBuilder openCodeBuilder;

    JdkMethodWriter(OpenBuilder.OpenMethodBuilder openMethodBuilder) {
        super(Opcodes.ASM9);
        this.openMethodBuilder = openMethodBuilder;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // TODO: not really considered in ASM today
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }


    @Override
    public void visitCode() {
        openCodeBuilder = openMethodBuilder.withCode();
    }

    @Override
    public void visitInsn(int opcode) {
        /*
         * NOP, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
         * LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1, IALOAD, LALOAD,
         * FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, IASTORE, LASTORE, FASTORE,
         * DASTORE, AASTORE, BASTORE, CASTORE, SASTORE, POP, POP2, DUP, DUP_X1, DUP_X2, DUP2,
         * DUP2_X1, DUP2_X2, SWAP, IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL,
         * FMUL, DMUL, IDIV, LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG,
         * ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR, I2L, I2F, I2D,
         * L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S, LCMP, FCMPL, FCMPG, DCMPL,
         * DCMPG, IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ARRAYLENGTH, ATHROW, MONITORENTER, or MONITOREXIT.*/
        openCodeBuilder.accept(openCodeBuilder -> openCodeBuilder.with(switch (opcode) { // TODO: complete
            case Opcodes.NOP -> NopInstruction.of();
            case Opcodes.ACONST_NULL,
                    Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                    Opcodes.LCONST_0, Opcodes.LCONST_1,
                    Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
                    Opcodes.DCONST_0, Opcodes.DCONST_1 -> null; // TODO: different PR -> ConstantInstruction.ofIntrinsic(Opcode.ofBytecode(opcode));
            default -> throw new UnsupportedOperationException("Unknown opcode: " + opcode);
        }));
    }
}
