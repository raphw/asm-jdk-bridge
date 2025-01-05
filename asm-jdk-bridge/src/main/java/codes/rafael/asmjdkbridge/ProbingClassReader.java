package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;

/**
 * A class reader that automatically resolves a suitable reader, either based on ASM (if ASM officially
 * supports a class file version) or a JDK Class File API based implementation.
 */
public class ProbingClassReader {

    private final ProbingResolver resolver;

    /**
     * Creates a new class reader.
     *
     * @param classFile           The class file to represent.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     */
    public ProbingClassReader(byte[] classFile, Attribute... attributePrototypes) {
        resolver = ProbingResolver.ofClassFile(classFile, attributePrototypes);
    }

    /**
     * Creates a new class reader.
     *
     * @param inputStream         An input stream of the class file to represent.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the stream cannot be read.
     */
    public ProbingClassReader(InputStream inputStream, Attribute... attributePrototypes) throws IOException {
        this(inputStream.readAllBytes(), attributePrototypes);
    }

    /**
     * Creates a new class reader.
     *
     * @param className           The name of the class to represent. The class must be resolvable from the system loader.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the class file cannot be read.
     */
    public ProbingClassReader(String className, Attribute... attributePrototypes) throws IOException {
        byte[] classFile;
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        resolver = ProbingResolver.ofClassFile(classFile, attributePrototypes);
    }

    /**
     * Accepts a class visitor for the represented class file.
     *
     * @param classVisitor The class visitor to delegate calls to.
     * @param flags        The ASM flags to consider when visiting the class file.
     */
    public void accept(ClassVisitor classVisitor, int flags) {
        resolver.accept(classVisitor, flags);
    }

    /**
     * Resolves the underlying class reader to an implementation-equivalent class writer. Using this approach,
     * an attempt is made to retain the constant pool if a class is supposed to be transformed.
     *
     * @param flags The ASM flags to consider when creating the class writer.
     * @return A suitable container for a class writer.
     */
    public ClassWriterContainer<?> toClassWriter(int flags) {
        return resolver.toClassWriter(flags);
    }

    /**
     * A container for a class writer.
     *
     * @param <T> The type of the class visitor that represents the class writer.
     */
    public abstract static class ClassWriterContainer<T extends ClassVisitor> {

        final T delegate;

        ClassWriterContainer(T delegate) {
            this.delegate = delegate;
        }

        /**
         * Returns the underlying ASM class visitor.
         *
         * @return The underlying ASM class visitor.
         */
        public ClassVisitor getClassVisitor() {
            return delegate;
        }

        /**
         * Returns the byte array that is created by this class writer.
         *
         * @return A byte array that represents the generated class file.
         */
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
