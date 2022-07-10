package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import jdk.classfile.Classfile;
import org.junit.Assert;
import org.junit.Assume;
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
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class JdkClassReaderTest {

    @SuppressWarnings("deprecation")
    @Parameterized.Parameters(name = "{0} (expandFrames={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Trivial.class, false, true},
                {LoadStoreAndReturn.class, false, true},
                {FieldConstructorAndMethod.class, false, true},
                {Operations.class, false, true},
                {DeprecatedClass.class, false, true},
                {SyntheticConstructor.Inner.class, false, true},
                {ArrayInstructions.class, false, true},
                {Invokedynamic.class, false, true},
                {BranchesAndStackMapFrames.class, false, true},
                {BranchesAndStackMapFrames.class, true, true},
                {Switches.class, false, true},
                {TryThrowCatch.class, false, false}, // TODO: glitches because of auto-compute of stack map frames
                {Annotations.class, false, true},
                {TypeAnnotationsWithoutPath.class, false, true},
                {TypeAnnotationsWithPath.class, false, true},
                {TypeAnnotationsInCode.class, false, false}, // TODO: why type annotations after DUP and not NEW?
                {RecordComponents.class, false, true},
                {NoRecordComponents.class, false, true},
                // {JsrRet.make(), false, true}, // TODO: How to handle old class files (e.g. JDBC)?
                {CustomAttribute.make(), false, false} // TODO: How to handle unknown attributes on write in ASM?
        });
    }

    private final Class<?> target;

    private final boolean expandFrames;

    private final boolean consistentWrite;

    public JdkClassReaderTest(Class<?> target, boolean expandFrames, boolean consistentWrite) {
        this.target = target;
        this.expandFrames = expandFrames;
        this.consistentWrite = consistentWrite;
    }

    @Test
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
        assumeTrue(consistentWrite);
        assertEquals(asm.toString(), jdk.toString());
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
