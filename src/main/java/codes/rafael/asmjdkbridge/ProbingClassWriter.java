package codes.rafael.asmjdkbridge;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class ProbingClassWriter extends ClassVisitor {

    private final int flags;

    public ProbingClassWriter(int flags) {
        super(Opcodes.ASM9);
        this.flags = flags;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        cv = ProbingResolver.ofVersion(flags, version);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public byte[] toByteArray() {
        return switch (cv) {
            case JdkClassWriter classWriter -> classWriter.toByteArray();
            case ClassWriter classWriter -> classWriter.toByteArray();
            case null -> throw new IllegalStateException("No version discovered");
            default -> throw new IllegalStateException("Unexpected type: " + cv.getClass().getTypeName());
        };
    }
}
