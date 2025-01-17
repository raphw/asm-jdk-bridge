package codes.rafael.asmjdkbridge;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A class visitor that creates a class file which is based upon the JDK Class File API.
 */
public class JdkClassWriter extends ClassVisitor {

    /**
     * Creates a class writer.
     *
     * @param flags The ASM flags to consider.
     */
    public JdkClassWriter(int flags) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a class writer.
     *
     * @param classReader A class reader of which to retain the constant pool, if possible.
     * @param flags       The ASM flags to consider.
     */
    public JdkClassWriter(JdkClassReader classReader, int flags) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the generated class file.
     *
     * @return The class file as a byte array.
     */
    public byte[] toByteArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the super class of the class that is provided by name. The default implementation
     * resolves the super class from this instance's class' {@link ClassLoader}, unless
     * {@link #getClassLoader()} is overridden.
     *
     * This is used for generating stack map frames.
     *
     * @param name The name of the class for which to resolve the super class.
     * @return The name of the resolved super class.
     */
    protected String getSuperClass(String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the class loader to use for resolving the super class of a discovered class.
     *
     * @return The class loader to use for resolving a super class's name.
     */
    protected ClassLoader getClassLoader() {
        throw new UnsupportedOperationException();
    }
}
