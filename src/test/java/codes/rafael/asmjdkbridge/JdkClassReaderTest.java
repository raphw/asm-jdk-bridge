package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.TrivialType;
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
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JdkClassReaderTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Stream.of(
                TrivialType.class
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
        new ClassReader(classFile).accept(new TraceClassVisitor(new PrintWriter(asm)), 0);
        new JdkClassReader(Classfile.parse(classFile)).accept(new TraceClassVisitor(new PrintWriter(jdk)));
        assertEquals(asm.toString(), jdk.toString());
    }
}
