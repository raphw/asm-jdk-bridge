package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CharacterRangeTableAttribute;

class AsmCharacterRangeTableAttribute extends Attribute {

    final CharacterRangeTableAttribute attribute;

    AsmCharacterRangeTableAttribute(CharacterRangeTableAttribute attribute) {
        super(Attributes.NAME_CHARACTER_RANGE_TABLE);
        this.attribute = attribute;
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

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    protected Label[] getLabels() {
        return null;
    }

    static class CustomCharacterRangeTableAttribute extends CustomAttribute<CustomCharacterRangeTableAttribute> {

        private final CharacterRangeTableAttribute attribute;

        CustomCharacterRangeTableAttribute(CharacterRangeTableAttribute attribute) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return Attributes.NAME_CHARACTER_RANGE_TABLE;
                }

                @Override
                public CustomCharacterRangeTableAttribute readAttribute(AttributedElement attributedElement, java.lang.classfile.ClassReader classReader, int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter bufWriter, CustomCharacterRangeTableAttribute attribute) {
                    Attributes.characterRangeTable().writeAttribute(bufWriter, attribute.attribute);
                }

                @Override
                public AttributeStability stability() {
                    return Attributes.characterRangeTable().stability();
                }
            });
            this.attribute = attribute;
        }
    }
}
