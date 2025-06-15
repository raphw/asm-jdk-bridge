package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

/**
 * A reader for class files that uses the JDK class file API. The created class reader is immutable.
 */
public class JdkClassReader {

    /**
     * Creates a new class reader.
     *
     * @param classFile           The class file to represent.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     */
    public JdkClassReader(byte[] classFile, Attribute... attributePrototypes) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new class reader.
     *
     * @param inputStream         An input stream of the class file to represent.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the stream cannot be read.
     */
    public JdkClassReader(InputStream inputStream, Attribute... attributePrototypes) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new class reader.
     *
     * @param className           The name of the class to represent. The class must be resolvable from the system loader.
     * @param attributePrototypes Prototypes of ASM attributes to map if discovered.
     * @throws IOException If the class file cannot be read.
     */
    public JdkClassReader(String className, Attribute... attributePrototypes) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the access flags of this class as stored in the class file.
     *
     * @return The access flags of this class as stored in the class file.
     */
    public int getAccess() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the internal name of this class.
     *
     * @return The internal name of this class.
     */
    public String getClassName() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the internal super class name of this class or {@code null} for {@link Object}.
     *
     * @return The internal super class name of this class or {@code null} for {@link Object}.
     */
    public String getSuperName() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the internal interface names of this class. Maybe {@code null}.
     *
     * @return The internal interface names of this class.
     */
    public String[] getInterfaces() {
        throw new UnsupportedOperationException();
    }

    /**
     * Accepts a class visitor for the represented class file.
     *
     * @param classVisitor The class visitor to delegate calls to.
     * @param flags        The ASM flags to consider when visiting the class file.
     */
    public void accept(ClassVisitor classVisitor, int flags) {
        throw new UnsupportedOperationException();
    }
}
