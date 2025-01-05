package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.io.InputStream;

public class JdkClassReader {

    public JdkClassReader(byte[] classFile, Attribute... attributePrototypes) {
        throw new UnsupportedOperationException();
    }

    public JdkClassReader(InputStream inputStream, Attribute... attributePrototypes) throws IOException {
        throw new UnsupportedOperationException();
    }

    public JdkClassReader(String className, Attribute... attributePrototypes) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void accept(ClassVisitor classVisitor, int flags) {
        throw new UnsupportedOperationException();
    }
}
