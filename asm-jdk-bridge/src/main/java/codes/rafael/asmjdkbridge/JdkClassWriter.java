package codes.rafael.asmjdkbridge;

import org.objectweb.asm.ClassVisitor;

public class JdkClassWriter extends ClassVisitor {

    public JdkClassWriter(int flags) {
        super(flags);
        throw new UnsupportedOperationException();
    }

    public JdkClassWriter(JdkClassReader classReader, int flags) {
        super(flags);
        throw new UnsupportedOperationException();
    }

    public byte[] toByteArray() {
        throw new UnsupportedOperationException();
    }
}
