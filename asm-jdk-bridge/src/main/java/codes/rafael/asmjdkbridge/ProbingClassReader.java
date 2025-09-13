package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Function;

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
        resolver = ProbingResolver.ofClassFile(null, classFile, attributePrototypes);
    }

    /**
     * Creates a new class reader.
     *
     * @param inputStream         An input stream of the class file to represent.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the stream cannot be read.
     */
    public ProbingClassReader(InputStream inputStream, Attribute... attributePrototypes) throws IOException {
        this(readAllBytes(inputStream), null, attributePrototypes);
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
            classFile = readAllBytes(inputStream);
        }
        resolver = ProbingResolver.ofClassFile(null, classFile, attributePrototypes);
    }

    /**
     * Creates a new class reader.
     *
     * @param classFile           The class file to represent.
     * @param getSuperClass       A resolver to use for finding super classes when computing stack map frames.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     */
    public ProbingClassReader(byte[] classFile,
                              Function<String, String> getSuperClass,
                              Attribute... attributePrototypes) {
        resolver = ProbingResolver.ofClassFile(getSuperClass, classFile, attributePrototypes);
    }

    /**
     * Creates a new class reader.
     *
     * @param inputStream         An input stream of the class file to represent.
     * @param getSuperClass       A resolver to use for finding super classes when computing stack map frames.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the stream cannot be read.
     */
    public ProbingClassReader(InputStream inputStream,
                              Function<String, String> getSuperClass,
                              Attribute... attributePrototypes) throws IOException {
        this(readAllBytes(inputStream), getSuperClass, attributePrototypes);
    }

    /**
     * Creates a new class reader.
     *
     * @param className           The name of the class to represent. The class must be resolvable from the system loader.
     * @param getSuperClass       A resolver to use for finding super classes when computing stack map frames.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the class file cannot be read.
     */
    public ProbingClassReader(String className,
                              Function<String, String> getSuperClass,
                              Attribute... attributePrototypes) throws IOException {
        byte[] classFile;
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class")) {
            classFile = readAllBytes(inputStream);
        }
        resolver = ProbingResolver.ofClassFile(getSuperClass, classFile, attributePrototypes);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 8];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    /**
     * Returns the access flags of this class as stored in the class file.
     *
     * @return The access flags of this class as stored in the class file.
     */
    public int getAccess() {
        return resolver.getAccess();
    }

    /**
     * Returns the internal name of this class.
     *
     * @return The internal name of this class.
     */
    public String getClassName() {
        return resolver.getClassName();
    }

    /**
     * Returns the internal super class name of this class or {@code null} for {@link Object}.
     *
     * @return The internal super class name of this class or {@code null} for {@link Object}.
     */
    public String getSuperName() {
        return resolver.getSuperName();
    }

    /**
     * Returns the internal interface names of this class. Maybe {@code null}.
     *
     * @return The internal interface names of this class.
     */
    public String[] getInterfaces() {
        return resolver.getInterfaces();
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

            OfAsm(ClassReader classReader, int flags, Function<String, String> getSuperClass) {
                super(new ClassWriter(classReader, flags) {
                    @Override
                    protected String getCommonSuperClass(String left, String right) {
                        if (getSuperClass == null) {
                            return super.getCommonSuperClass(left, right);
                        } else {
                            String resolved = doGetCommonSuperClass(left, right, getSuperClass);
                            if (resolved == null) {
                                resolved = doGetCommonSuperClass(right, left, getSuperClass);
                            }
                            if (resolved == null) {
                                return "java/lang/Object";
                            } else {
                                return resolved;
                            }
                        }
                    }
                });
            }

            private static String doGetCommonSuperClass(String constant,
                                                        String dynamic,
                                                        Function<String, String> getSuperClass) {
                while (!Objects.equals(constant, dynamic) && dynamic != null) {
                    dynamic = getSuperClass.apply(dynamic);
                }
                return dynamic;
            }

            @Override
            public byte[] toByteArray() {
                return delegate.toByteArray();
            }
        }

        static class OfJdk extends ClassWriterContainer<JdkClassWriter> {

            OfJdk(JdkClassReader classReader, int flags, Function<String, String> getSuperClass) {
                super(new JdkClassWriter(classReader, flags, getSuperClass));
            }

            @Override
            public byte[] toByteArray() {
                return delegate.toByteArray();
            }
        }
    }
}
