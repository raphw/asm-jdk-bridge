package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.ModuleResolutionAttribute;

class AsmModuleResolutionAttribute extends Attribute {

    final ModuleResolutionAttribute attribute;

    AsmModuleResolutionAttribute(ModuleResolutionAttribute attribute) {
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
        ByteVector byteVector = new ByteVector(2);
        byteVector.putShort(attribute.resolutionFlags());
        return byteVector;
    }

    static class CustomModuleResolutionAttribute extends CustomAttribute<CustomModuleResolutionAttribute> {

        private final ModuleResolutionAttribute attribute;

        CustomModuleResolutionAttribute(ModuleResolutionAttribute attribute) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return attribute.attributeName();
                }

                @Override
                public CustomModuleResolutionAttribute readAttribute(AttributedElement attributedElement, java.lang.classfile.ClassReader classReader, int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter bufWriter, CustomModuleResolutionAttribute attribute) {
                    Attributes.moduleResolution().writeAttribute(bufWriter, attribute.attribute);
                }

                @Override
                public AttributeStability stability() {
                    return Attributes.moduleResolution().stability();
                }
            });
            this.attribute = attribute;
        }
    }
}
