package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

import java.lang.classfile.attribute.*;

abstract class AsmWrappedAttribute<A extends java.lang.classfile.Attribute<?>> extends Attribute {

    final A attribute;

    AsmWrappedAttribute(A attribute) {
        super(attribute.attributeName());
        this.attribute = attribute;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    static class AsmCharacterRangeTableAttribute extends AsmWrappedAttribute<CharacterRangeTableAttribute> {

        AsmCharacterRangeTableAttribute(CharacterRangeTableAttribute attribute) {
            super(attribute);
        }

        @Override
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector byteVector = new ByteVector(2 + 8 * attribute.characterRangeTable().size());
            byteVector.putShort(attribute.characterRangeTable().size());
            for (CharacterRangeInfo characterRangeInfo : attribute.characterRangeTable()) {
                byteVector.putShort(characterRangeInfo.startPc());
                byteVector.putShort(characterRangeInfo.endPc());
                byteVector.putInt(characterRangeInfo.characterRangeStart());
                byteVector.putInt(characterRangeInfo.characterRangeEnd());
                byteVector.putShort(characterRangeInfo.flags());
            }
            return byteVector;
        }

        @Override
        public boolean isCodeAttribute() {
            return true;
        }
    }

    static class AsmCompilationIdAttribute extends AsmWrappedAttribute<CompilationIDAttribute> {

        AsmCompilationIdAttribute(CompilationIDAttribute attribute) {
            super(attribute);
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            int index = classWriter.newUTF8(attribute.compilationId().stringValue());
            ByteVector byteVector = new ByteVector(2);
            byteVector.putShort(index);
            return byteVector;
        }
    }

    static class AsmModuleHashesAttribute extends AsmWrappedAttribute<ModuleHashesAttribute> {

        AsmModuleHashesAttribute(ModuleHashesAttribute attribute) {
            super(attribute);
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
    }

    static class AsmModuleResolutionAttribute extends AsmWrappedAttribute<ModuleResolutionAttribute> {

        AsmModuleResolutionAttribute(ModuleResolutionAttribute attribute) {
            super(attribute);
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector byteVector = new ByteVector(2);
            byteVector.putShort(attribute.resolutionFlags());
            return byteVector;
        }
    }

    static class AsmSourceIdAttribute extends AsmWrappedAttribute<SourceIDAttribute> {

        AsmSourceIdAttribute(SourceIDAttribute attribute) {
            super(attribute);
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            int index = classWriter.newUTF8(attribute.sourceId().stringValue());
            ByteVector byteVector = new ByteVector(2);
            byteVector.putShort(index);
            return byteVector;
        }
    }

    static class AsmUnknownAttribute extends AsmWrappedAttribute<UnknownAttribute> {

        AsmUnknownAttribute(UnknownAttribute attribute) {
            super(attribute);
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector vector = new ByteVector(attribute.contents().length);
            vector.putByteArray(attribute.contents(), 0, attribute.contents().length);
            return vector;
        }
    }
}
