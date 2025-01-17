package codes.rafael.asmjdkbridge;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.function.Function;

/**
 * A class writer that automatically resolves a suitable writer, either based on ASM (if ASM officially
 * supports a class file version) or a JDK Class File API based implementation.
 */
public class ProbingClassWriter extends ClassVisitor {

    private final int flags;
    private final Function<String, String> getSuperClass;

    /**
     * Creates a class writer.
     *
     * @param flags The ASM flags to consider.
     */
    public ProbingClassWriter(int flags) {
        super(Opcodes.ASM9);
        this.flags = flags;
        getSuperClass = null;
    }

    /**
     * Creates a class writer.
     *
     * @param flags         The ASM flags to consider.
     * @param getSuperClass A resolver for the supplied internal class name's internal super class name. If
     *                      a class is an interface, {@code null} should be returned.
     */
    public ProbingClassWriter(int flags, Function<String, String> getSuperClass) {
        super(Opcodes.ASM9);
        this.flags = flags;
        this.getSuperClass = getSuperClass;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        cv = ProbingResolver.ofVersion(flags, version, getSuperClass);
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
