package codes.rafael.asmjdkbridge;

import org.junit.Test;

import java.io.InputStream;

public class ProbingTest {

    @Test
    public void can_probe_supported_version() throws Exception {
        byte[] classFile;
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getName().substring(Sample.class.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        ProbingClassReader classReader = new ProbingClassReader(classFile);
        ProbingClassReader.ClassWriterContainer<?> classWriter = classReader.toClassWriter(0);
        classReader.accept(classWriter.getClassVisitor(), 0);
    }

    public static class Sample { }
}
