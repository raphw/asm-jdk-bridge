package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;

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
     * Accepts a class visitor for the represented class file.
     *
     * @param classVisitor The class visitor to delegate calls to.
     * @param flags        The ASM flags to consider when visiting the class file.
     */
    public void accept(ClassVisitor classVisitor, int flags) {
        throw new UnsupportedOperationException();
    }
}
