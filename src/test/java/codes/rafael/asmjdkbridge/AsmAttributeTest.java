package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.CustomAttributeExtractable;
import org.junit.Test;
import org.objectweb.asm.*;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class AsmAttributeTest {

    @Test
    public void can_read_attribute_properties() throws Exception {
        Class<?> target = CustomAttributeExtractable.make();
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringBuilder asm = new StringBuilder(), jdk = new StringBuilder();
        new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM9) {
        }, new Attribute[] {new StringCollectingAttribute(asm)}, 0);
        new JdkClassReader(classFile, new StringCollectingAttribute(jdk)).accept(new ClassVisitor(Opcodes.ASM9) {
        }, 0);
        assertEquals(asm.toString(), jdk.toString());
    }

    static class StringCollectingAttribute extends Attribute {

        private final StringBuilder sb;

        StringCollectingAttribute(StringBuilder sb) {
            super("CustomAttribute");
            this.sb = sb;
        }

        @Override
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            sb.append("----").append("\n");
            sb.append(offset).append("\n");
            sb.append(length).append("\n");
            sb.append(codeAttributeOffset).append("\n");
            sb.append(labels == null).append("\n");
            sb.append(classReader.getClassName()).append("\n");
            sb.append(classReader.getSuperName()).append("\n");
            sb.append(classReader.readByte(10)).append("\n");
            sb.append(classReader.readShort(10)).append("\n");
            sb.append(classReader.readUnsignedShort(10)).append("\n");
            sb.append(classReader.readInt(10)).append("\n");
            sb.append(classReader.readLong(10)).append("\n");
            sb.append(classReader.getItemCount()).append("\n");
            sb.append(classReader.readConst(2, charBuffer)).append("\n");
            sb.append(classReader.readClass(140, charBuffer)).append("\n");
            sb.append(classReader.readUTF8(177, charBuffer)).append("\n");
            return super.read(classReader, offset, length, charBuffer, codeAttributeOffset, labels);
        }
    }
}
