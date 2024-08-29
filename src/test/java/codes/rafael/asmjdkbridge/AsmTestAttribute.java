package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

public class AsmTestAttribute extends Attribute {

    private final byte[] bytes;

    public AsmTestAttribute() {
        super("CustomAttribute");
        bytes = null;
    }

    public AsmTestAttribute(byte[] bytes) {
        super("CustomAttribute");
        this.bytes = bytes;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        AsmTestAttribute attribute = new AsmTestAttribute(new byte[length]);
        System.arraycopy(classReader.b, offset, attribute.bytes, 0, length);
        return attribute;
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
        ByteVector vector = new ByteVector(bytes.length);
        vector.putByteArray(bytes, 0, bytes.length);
        return vector;
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    public static class AsmCodeTestAttribute extends Attribute {

        private final byte[] bytes;

        public AsmCodeTestAttribute() {
            super("CustomCodeAttribute");
            bytes = null;
        }

        public AsmCodeTestAttribute(byte[] bytes) {
            super("CustomCodeAttribute");
            this.bytes = bytes;
        }

        @Override
        public boolean isCodeAttribute() {
            return true;
        }

        @Override
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            AsmCodeTestAttribute attribute = new AsmCodeTestAttribute(new byte[length]);
            System.arraycopy(classReader.b, offset, attribute.bytes, 0, length);
            return attribute;
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector vector = new ByteVector(bytes.length);
            vector.putByteArray(bytes, 0, bytes.length);
            return vector;
        }
    }
}