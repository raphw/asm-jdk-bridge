package codes.rafael.asmjdkbridge.test;

import codes.rafael.asmjdkbridge.JdkClassReader;
import codes.rafael.asmjdkbridge.sample.NoRecordComponents;
import codes.rafael.asmjdkbridge.sample.RecordComponents;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Attribute;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JdkClassReaderTest {

    @SuppressWarnings("deprecation")
    @Parameterized.Parameters(name = "{0} (reader={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Object.class, 0},
                {Runnable.class, 0},
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
                {BranchesAndStackMapFrames.class, ClassReader.SKIP_FRAMES},
                {BranchesAndStackMapFrames.class, ClassReader.SKIP_CODE},
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

    public JdkClassReaderTest(Class<?> target, int flags) {
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
        toClassReader(classFile).accept(toVisitor(asm), new Attribute[]{ new AsmTestAttribute(), new AsmTestAttribute.AsmCodeTestAttribute() }, flags);
        new JdkClassReader(classFile, new AsmTestAttribute(), new AsmTestAttribute.AsmCodeTestAttribute()).accept(toVisitor(jdk), flags);
        assertEquals(asm.toString(), jdk.toString());
    }

    @Test
    public void properties_are_equal() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        ClassReader asm = toClassReader(classFile);
        JdkClassReader jdk = new JdkClassReader(classFile);
        assertEquals(asm.getAccess(), jdk.getAccess());
        assertEquals(asm.getClassName(), jdk.getClassName());
        assertEquals(asm.getSuperName(), jdk.getSuperName());
        assertArrayEquals(asm.getInterfaces(), jdk.getInterfaces());
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
