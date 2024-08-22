package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JdkClassReplicationTest {

    @SuppressWarnings("deprecation")
    @Parameterized.Parameters(name = "{0} (reader={1})")
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
                {CustomAttributeExtractable.make(), 0},
                {SyntheticParameters.class, 0},
                {SyntheticParameters.InnerClass.class, 0},
                {String.class, 0},
                {Integer.class, 0},
                {Math.class, 0}
        });
    }

    private final Class<?> target;

    private final int flags;

    public JdkClassReplicationTest(Class<?> target, int flags) {
        this.target = target;
        this.flags = flags;
    }

    @Test
    public void parsed_class_files_are_equal() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringWriter original = new StringWriter(), replicated = new StringWriter();
        JdkClassWriter classWriter = new JdkClassWriter(0);
        new JdkClassReader(classFile).accept(classWriter, 0);
        toClassReader(classFile).accept(toVisitor(original), flags);
        toClassReader(classWriter.toByteArray()).accept(toVisitor(replicated), flags);
        assertEquals(original.toString(), replicated.toString());
    }

    private static ClassVisitor toVisitor(StringWriter writer) {
        return new TraceClassVisitor(new PrintWriter(writer));
    }

    private static ClassReader toClassReader(byte[] bytes) {
        try {
            Constructor<ClassReader> constructor = ClassReader.class.getDeclaredConstructor(byte[].class, int.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(bytes, 0, false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
