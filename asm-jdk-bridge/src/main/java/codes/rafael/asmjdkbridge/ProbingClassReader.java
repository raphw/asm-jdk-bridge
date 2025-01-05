package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;

public class ProbingClassReader {

    private final ProbingResolver resolver;

    public ProbingClassReader(byte[] classFile, Attribute... attributePrototypes) {
        resolver = ProbingResolver.ofClassFile(classFile, attributePrototypes);
    }

    public ProbingClassReader(InputStream inputStream, Attribute... attributePrototypes) throws IOException {
        this(inputStream.readAllBytes(), attributePrototypes);
    }

    public ProbingClassReader(String className, Attribute... attributePrototypes) throws IOException {
        byte[] classFile;
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        resolver = ProbingResolver.ofClassFile(classFile, attributePrototypes);
    }

    public void accept(ClassVisitor classVisitor, int flags) {
        resolver.accept(classVisitor, flags);
    }

    public ClassWriterContainer<?> toClassWriter(int flags) {
        return resolver.toClassWriter(flags);
    }

    public abstract static class ClassWriterContainer<T extends ClassVisitor> {

        final T delegate;

        ClassWriterContainer(T delegate) {
            this.delegate = delegate;
        }

        public ClassVisitor getClassVisitor() {
            return delegate;
        }

        public abstract byte[] toByteArray();

        static class OfAsm extends ClassWriterContainer<ClassWriter> {

            OfAsm(ClassReader classReader, int flags) {
                super(new ClassWriter(classReader, flags));
            }

            @Override
            public byte[] toByteArray() {
                return delegate.toByteArray();
            }
        }

        static class OfJdk extends ClassWriterContainer<JdkClassWriter> {

            OfJdk(JdkClassReader classReader, int flags) {
                super(new JdkClassWriter(classReader, flags));
            }

            @Override
            public byte[] toByteArray() {
                return delegate.toByteArray();
            }
        }
    }
}
