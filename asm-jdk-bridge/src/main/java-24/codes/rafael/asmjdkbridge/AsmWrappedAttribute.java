package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CharacterRangeTableAttribute;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.CompilationIDAttribute;
import java.lang.classfile.attribute.ModuleHashInfo;
import java.lang.classfile.attribute.ModuleHashesAttribute;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.attribute.SourceIDAttribute;
import java.lang.classfile.attribute.UnknownAttribute;
import java.lang.classfile.instruction.CharacterRange;
import java.util.List;

abstract class AsmWrappedAttribute<A extends java.lang.classfile.Attribute<?>> extends Attribute {

    final A attribute;

    protected AsmWrappedAttribute(A attribute) {
        super(attribute.attributeName().stringValue());
        this.attribute = attribute;
    }

    static <T> T unwrap(Attribute attribute, Class<T> type) {
        return type.cast(attribute instanceof AsmWrappedAttribute<?> wrappedAttribute
                ? type.cast(wrappedAttribute.attribute)
                : type.cast(AsmAttribute.of(attribute)));
    }

    @Override
    protected final Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected abstract ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals);

    @Override
    public final boolean isUnknown() {
        return false;
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

    static class AsmCharacterRangeTableAttribute extends AsmWrappedAttribute<CharacterRangeTableAttribute> { // Can never be retained as instructions might change BCIs.

        AsmCharacterRangeTableAttribute(CharacterRangeTableAttribute attribute) {
            super(attribute);
        }

        // Must be recalculated as label BCIs might change when code instructions are altered. Should be delayed to option to resolve by righter
        static AsmWrappedAttribute<CharacterRangeTableAttribute> of(List<CharacterRange> characterRanges, CodeAttribute codeAttribute) {
            return new AsmCharacterRangeTableAttribute(CharacterRangeTableAttribute.of(characterRanges.stream().map(characterRange -> CharacterRangeInfo.of(
                    codeAttribute.labelToBci(characterRange.startScope()),
                    codeAttribute.labelToBci(characterRange.endScope()),
                    characterRange.characterRangeStart(),
                    characterRange.characterRangeEnd(),
                    characterRange.flags())).toList()));
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector byteVector = new ByteVector(2 + 14 * attribute.characterRangeTable().size());
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

    static class AsmUnknownAttribute extends AsmWrappedAttribute<UnknownAttribute> {

        private final boolean code;

        AsmUnknownAttribute(UnknownAttribute attribute, boolean code) {
            super(attribute);
            this.code = code;
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector vector = new ByteVector(attribute.contents().length);
            vector.putByteArray(attribute.contents(), 0, attribute.contents().length);
            return vector;
        }

        @Override
        public boolean isCodeAttribute() {
            return code;
        }
    }
}
