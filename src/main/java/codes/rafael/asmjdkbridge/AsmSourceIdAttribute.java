package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SourceIDAttribute;

class AsmSourceIdAttribute extends Attribute {

    final SourceIDAttribute attribute;

    AsmSourceIdAttribute(SourceIDAttribute attribute) {
        super(Attributes.NAME_SOURCE_ID);
        this.attribute = attribute;
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
        int index = classWriter.newUTF8(attribute.sourceId().stringValue());
        ByteVector byteVector = new ByteVector(2);
        byteVector.putShort(index);
        return byteVector;
    }

    static class CustomSourceIdAttribute extends CustomAttribute<CustomSourceIdAttribute> {

        private final SourceIDAttribute attribute;

        CustomSourceIdAttribute(SourceIDAttribute attribute) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return attribute.attributeName();
                }

                @Override
                public CustomSourceIdAttribute readAttribute(AttributedElement attributedElement, java.lang.classfile.ClassReader classReader, int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter bufWriter, CustomSourceIdAttribute attribute) {
                    Attributes.sourceId().writeAttribute(bufWriter, attribute.attribute);
                }

                @Override
                public AttributeStability stability() {
                    return Attributes.sourceId().stability();
                }
            });
            this.attribute = attribute;
        }
    }
}
