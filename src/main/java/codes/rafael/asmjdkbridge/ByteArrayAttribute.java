package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

public class ByteArrayAttribute extends Attribute {

    public final byte[] bytes;

    public ByteArrayAttribute(String type, byte[] bytes) {
        super(type);
        this.bytes = bytes;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
        ByteVector vector = new ByteVector(bytes.length);
        vector.putByteArray(bytes, 0, bytes.length);
        return vector;
    }
}
