package codes.rafael.asmjdkbridge;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * A class writer that automatically resolves a suitable writer, either based on ASM (if ASM officially
 * supports a class file version) or a JDK Class File API based implementation.
 */
public class ProbingClassWriter extends ClassVisitor {

    private final int flags;

    /**
     * Creates a class writer.
     *
     * @param flags The ASM flags to consider.
     */
    public ProbingClassWriter(int flags) {
        super(Opcodes.ASM9);
        this.flags = flags;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        cv = ProbingResolver.ofVersion(flags, version);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * Returns the generated class file.
     *
     * @return The class file as a byte array.
     */
    public byte[] toByteArray() {
        if (cv instanceof JdkClassWriter) {
            return ((JdkClassWriter) cv).toByteArray();
        } else if (cv instanceof ClassWriter) {
            return ((ClassWriter) cv).toByteArray();
        } else if (cv instanceof ProbingClassWriter) {
            return ((ProbingClassWriter) cv).toByteArray();
        } else if (cv == null) {
            throw new IllegalStateException("No version discovered");
        } else {
            throw new IllegalStateException("Unexpected type: " + cv.getClass().getTypeName());
        }
    }
}
