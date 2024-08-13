package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JdkClassWriterTest {

    @SuppressWarnings("deprecation")
    @Parameterized.Parameters(name = "{0} (expandFrames={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Trivial.class, 0},
                {LoadStoreAndReturn.class, 0},
                {FieldConstructorAndMethod.class, 0},
                {Operations.class, 0},
                {DeprecatedClass.class, 0},
                {SyntheticConstructor.Inner.class, 0},
                {ArrayInstructions.class, 0},
                {Invokedynamic.class, 0},
                {BranchesAndStackMapFrames.class, 0},
                {BranchesAndStackMapFrames.class, ClassReader.EXPAND_FRAMES},
                {Switches.class, 0},
                {TryThrowCatch.class, 0},
                {RecordComponents.class, 0},
                {NoRecordComponents.class, 0},
                {Annotations.class, 0},
                {TypeAnnotationsWithoutPath.class, 0},
                {TypeAnnotationsWithPath.class, 0},
                {TypeAnnotationsInCode.class, 0},
                {CustomAttribute.make(), 0},
                {String.class, ClassReader.SKIP_FRAMES},
                {Integer.class, ClassReader.SKIP_FRAMES},
                {System.class, ClassReader.SKIP_FRAMES}
        });
    }

    private final Class<?> target;

    private final int flags;

    public JdkClassWriterTest(Class<?> target, int flags) {
        this.target = target;
        this.flags = flags;
    }

    @Test
    public void parsed_class_files_are_equal() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        new ClassReader(classFile).accept(toVisitor(asm), flags);
        JdkClassWriter writer = new JdkClassWriter(attribute -> {
            if (attribute instanceof TestAttribute testAttribute) {
                return testAttribute.content;
            } else {
                throw new AssertionError("Unknown attribute: " + attribute.type);
            }
        });
        new ClassReader(classFile).accept(writer, new Attribute[]{ new TestAttribute() }, flags);
        new ClassReader(writer.toByteArray()).accept(toVisitor(jdk), flags);
        assertEquals(asm.toString(), jdk.toString());
    }

    private static ClassVisitor toVisitor(StringWriter writer) {
        return new ClassVisitor(Opcodes.ASM9, new TraceClassVisitor(new PrintWriter(writer))) {
        };
    }

    public static class TestAttribute extends Attribute {

        private byte[] content;

        protected TestAttribute() {
            super("CustomAttribute");
        }

        @Override
        @SuppressWarnings("deprecation")
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            TestAttribute attribute = new TestAttribute();
            attribute.content = new byte[length];
            System.arraycopy(classReader.b, offset, attribute.content, 0, length);
            return attribute;
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector vector = new ByteVector(content.length);
            vector.putByteArray(content, 0, content.length);
            return vector;
        }
    }
}
