package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CompilationIDAttribute;

class AsmCompilationIdAttribute extends Attribute {

    final CompilationIDAttribute attribute;

    AsmCompilationIdAttribute(CompilationIDAttribute attribute) {
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
        int index = classWriter.newUTF8(attribute.compilationId().stringValue());
        ByteVector byteVector = new ByteVector(2);
        byteVector.putShort(index);
        return byteVector;
    }

    static class CustomCompilationIdAttribute extends CustomAttribute<CustomCompilationIdAttribute> {

        private final CompilationIDAttribute attribute;

        CustomCompilationIdAttribute(CompilationIDAttribute attribute) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return attribute.attributeName();
                }

                @Override
                public CustomCompilationIdAttribute readAttribute(AttributedElement attributedElement, java.lang.classfile.ClassReader classReader, int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter bufWriter, CustomCompilationIdAttribute attribute) {
                    Attributes.compilationId().writeAttribute(bufWriter, attribute.attribute);
                }

                @Override
                public AttributeStability stability() {
                    return Attributes.compilationId().stability();
                }
            });
            this.attribute = attribute;
        }
    }
}
