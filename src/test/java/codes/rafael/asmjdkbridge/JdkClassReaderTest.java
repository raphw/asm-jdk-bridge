package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import jdk.classfile.Classfile;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
    @Parameterized.Parameters(name = "{0} (expandFrames={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Trivial.class, false},
                {LoadStoreAndReturn.class, false},
                {FieldConstructorAndMethod.class, false},
                {Operations.class, false},
                {DeprecatedClass.class, false},
                {SyntheticConstructor.Inner.class, false},
                {ArrayInstructions.class, false},
                {Invokedynamic.class, false},
                {BranchesAndStackMapFrames.class, false},
                {BranchesAndStackMapFrames.class, true},
                {Switches.class, false},
                {TryThrowCatch.class, false},
                {RecordComponents.class, false},
                {NoRecordComponents.class, false},
                {Annotations.class, false},
                {TypeAnnotationsWithoutPath.class, false},
                {TypeAnnotationsWithPath.class, false},
                {TypeAnnotationsInCode.class, false},
                {CustomAttribute.make(), false}
        });
    }

    private final Class<?> target;

    private final boolean expandFrames;

    public JdkClassReaderTest(Class<?> target, boolean expandFrames) {
        this.target = target;
        this.expandFrames = expandFrames;
    }

    //@Test
    public void equal_reader_output() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        nonValidatingClassReader(classFile).accept(toVisitor(asm), expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        new JdkClassReader(Classfile.parse(classFile)).accept(toVisitor(jdk), expandFrames);
        assertEquals(asm.toString(), jdk.toString());
    }

    @Test
    public void equal_writer_output() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        ClassReader classReader = nonValidatingClassReader(classFile);
        ClassWriter asmWriter = new ClassWriter(0);
        JdkClassWriter jdkWriter = new JdkClassWriter();
        classReader.accept(asmWriter, expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        classReader.accept(jdkWriter, expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        nonValidatingClassReader(asmWriter.toByteArray()).accept(toVisitor(asm), expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        nonValidatingClassReader(jdkWriter.toByteArray()).accept(toVisitor(jdk), expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        assertEquals(asm.toString(), jdk.toString());
        //System.out.println(asm.toString());
        //System.out.println(jdk.toString());
    }

    private static ClassReader nonValidatingClassReader(byte[] classFile) {
        try {
            Constructor<ClassReader> constructor = ClassReader.class.getDeclaredConstructor(byte[].class, int.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(classFile, 0, false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static ClassVisitor toVisitor(StringWriter writer) {
        return new ClassVisitor(Opcodes.ASM9, new TraceClassVisitor(new PrintWriter(writer))) {
        };
    }
}
