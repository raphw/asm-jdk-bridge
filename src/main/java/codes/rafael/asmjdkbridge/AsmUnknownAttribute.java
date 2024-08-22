package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.BufWriter;
import java.lang.classfile.CustomAttribute;
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