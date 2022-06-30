package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import jdk.classfile.Classfile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JdkClassReaderTest {

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Stream.of(
                Trivial.class,
                LoadStoreAndReturn.class,
                FieldAndMethod.class,
                Operations.class,
                Invokedynamic.class
        ).map(type -> new Object[]{type}).collect(Collectors.toList());
    }

    private final Class<?> target;

    public JdkClassReaderTest(Class<?> target) {
        this.target = target;
    }

    @Test
    public void parsed_class_files_are_equal() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getSimpleName() + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        nonValidatingClassReader(classFile).accept(new TraceClassVisitor(new PrintWriter(asm)), 0);
        new JdkClassReader(Classfile.parse(classFile)).accept(new TraceClassVisitor(new PrintWriter(jdk)));
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
}
