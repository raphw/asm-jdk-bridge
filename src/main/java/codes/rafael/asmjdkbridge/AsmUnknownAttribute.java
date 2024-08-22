package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;

import java.lang.classfile.*;
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

    static class CustomUnknownAttribute extends CustomAttribute<CustomUnknownAttribute> {

        private final UnknownAttribute attribute;

        CustomUnknownAttribute(UnknownAttribute attribute) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return attribute.attributeName();
                }

                @Override
                public CustomUnknownAttribute readAttribute(AttributedElement attributedElement, java.lang.classfile.ClassReader classReader, int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter bufWriter, CustomUnknownAttribute attribute) {
                    bufWriter.writeIndex(bufWriter.constantPool().utf8Entry(attribute.attributeName()));
                    bufWriter.writeInt(attribute.attribute.contents().length);
                    bufWriter.writeBytes(attribute.attribute.contents());
                }

                @Override
                public AttributeStability stability() {
                    return AttributeStability.UNKNOWN;
                }
            });
            this.attribute = attribute;
        }
    }
}