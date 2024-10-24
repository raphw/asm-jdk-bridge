package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;

public class ProbingClassReader {

    private final Resolver resolver;

    public ProbingClassReader(byte[] classFile, Attribute... attributePrototypes) {
        resolver = Resolver.of(classFile, attributePrototypes);
    }

    public ProbingClassReader(InputStream inputStream, Attribute... attributePrototypes) throws IOException {
        this(inputStream.readAllBytes(), attributePrototypes);
    }

    public ProbingClassReader(String className, Attribute... attributePrototypes) throws IOException {
        byte[] classFile;
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        resolver = Resolver.of(classFile, attributePrototypes);
    }

    public void accept(ClassVisitor classVisitor, int flags) {
        resolver.accept(classVisitor, flags);
    }

    public ClassWriterContainer<?> toClassWriter(int flags) {
        return resolver.toClassWriter(flags);
    }

    static abstract class Resolver {

        private static final int supported;

        static {
            int version = Opcodes.V24;
            try {
                int candidate = 25;
                while (!Thread.interrupted()) {
                    version = (Integer) Opcodes.class.getField("V" + candidate).get(null);
                }
            } catch (Throwable ignored) { }
            supported = version & 0xFFFF;
        }

        static Resolver of(byte[] classFile, Attribute[] attributePrototypes) {
            int majorVersion = classFile[6] << 8 | classFile[7];
            if (majorVersion > supported) {
                return new OfJdk(classFile, attributePrototypes);
            } else {
                return new OfAsm(classFile, attributePrototypes);
            }
        }

        abstract void accept(ClassVisitor classVisitor, int flags);

        abstract ClassWriterContainer<?> toClassWriter(int flags);

        static class OfAsm extends Resolver {

            private final ClassReader classReader;
            private final Attribute[] attributePrototypes;

            OfAsm(byte[] classFile, Attribute[] attributePrototypes) {
                classReader = new ClassReader(classFile);
                this.attributePrototypes = attributePrototypes;
            }

            @Override
            void accept(ClassVisitor classVisitor, int flags) {
                classReader.accept(classVisitor, attributePrototypes, flags);
            }

            @Override
            ClassWriterContainer<?> toClassWriter(int flags) {
                return new ClassWriterContainer.OfAsm(classReader, flags);
            }
        }

        static class OfJdk extends Resolver {

            private final JdkClassReader classReader;

            OfJdk(byte[] classFile, Attribute[] attributePrototypes) {
                classReader = new JdkClassReader(classFile, attributePrototypes);
            }

            @Override
            void accept(ClassVisitor classVisitor, int flags) {
                classReader.accept(classVisitor, flags);
            }

            @Override
            ClassWriterContainer<?> toClassWriter(int flags) {
                return new ClassWriterContainer.OfJdk(classReader, flags);
            }
        }
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
