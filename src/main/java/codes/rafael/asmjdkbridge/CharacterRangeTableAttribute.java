package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

import java.lang.classfile.Attributes;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.util.List;

public class CharacterRangeTableAttribute extends Attribute {

    private final List<CharacterRangeInfo> ranges;

    public CharacterRangeTableAttribute(List<CharacterRangeInfo> ranges) {
        super(Attributes.NAME_CHARACTER_RANGE_TABLE);
        this.ranges = ranges;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
        ByteVector byteVector = new ByteVector(2 + 8 * ranges.size());
        byteVector.putShort(ranges.size());
        for (CharacterRangeInfo info : ranges) {
            byteVector.putShort(info.startPc());
            byteVector.putShort(info.endPc());
            byteVector.putInt(info.characterRangeStart());
            byteVector.putInt(info.characterRangeEnd());
            byteVector.putShort(info.flags());
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
}
