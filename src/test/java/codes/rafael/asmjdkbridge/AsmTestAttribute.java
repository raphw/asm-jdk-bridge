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
    @SuppressWarnings("deprecation")
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
}