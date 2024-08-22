package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.ModuleHashInfo;
import java.lang.classfile.attribute.ModuleHashesAttribute;

class AsmModuleHashesAttribute extends Attribute {

    final ModuleHashesAttribute attribute;

    AsmModuleHashesAttribute(ModuleHashesAttribute attribute) {
        super(Attributes.NAME_MODULE_HASHES);
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
        ByteVector bytes = new ByteVector();
        bytes.putShort(classWriter.newUTF8(attribute.algorithm().stringValue()));
        bytes.putShort(attribute.hashes().size());
        for (ModuleHashInfo info : attribute.hashes()) {
            bytes.putShort(classWriter.newModule(info.moduleName().name().stringValue()));
            bytes.putShort(info.hash().length);
            bytes.putByteArray(info.hash(), 0, info.hash().length);
        }
        return bytes;
    }

    static class CustomModuleHashesAttribute extends CustomAttribute<CustomModuleHashesAttribute> {

        private final ModuleHashesAttribute attribute;

        CustomModuleHashesAttribute(ModuleHashesAttribute attribute) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return attribute.attributeName();
                }

                @Override
                public CustomModuleHashesAttribute readAttribute(AttributedElement attributedElement, java.lang.classfile.ClassReader classReader, int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter bufWriter, CustomModuleHashesAttribute attribute) {
                    Attributes.moduleHashes().writeAttribute(bufWriter, attribute.attribute);
                }

                @Override
                public AttributeStability stability() {
                    return Attributes.moduleHashes().stability();
                }
            });
            this.attribute = attribute;
        }
    }
}
