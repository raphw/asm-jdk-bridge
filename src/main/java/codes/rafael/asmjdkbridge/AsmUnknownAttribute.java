package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

import java.lang.classfile.attribute.UnknownAttribute;

class AsmUnknownAttribute extends Attribute {

    final UnknownAttribute attribute;

    AsmUnknownAttribute(UnknownAttribute attribute) {
        super(attribute.attributeName());
        this.attribute = attribute;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
        ByteVector vector = new ByteVector(attribute.contents().length);
        vector.putByteArray(attribute.contents(), 0, attribute.contents().length);
        return vector;
    }
}