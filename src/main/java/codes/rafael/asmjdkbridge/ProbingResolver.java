package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

abstract class ProbingResolver {

    private static final int SUPPORTED;

    static {
        int version = Opcodes.V24;
        try {
            int candidate = 25;
            while (!Thread.interrupted()) {
                version = (Integer) Opcodes.class.getField("V" + candidate).get(null);
            }
        } catch (Throwable ignored) {
        }
        SUPPORTED = version & 0xFFFF;
    }

    static ClassVisitor ofVersion(int flags, int version) {
        if (version > SUPPORTED) {
            return new JdkClassWriter(flags);
        } else {
            return new ClassWriter(flags);
        }
    }

    static ProbingResolver ofClassFile(byte[] classFile, Attribute[] attributePrototypes) {
        int majorVersion = classFile[6] << 8 | classFile[7];
        if (majorVersion > SUPPORTED) {
            return new OfJdk(classFile, attributePrototypes);
        } else {
            return new OfAsm(classFile, attributePrototypes);
        }
    }

    abstract void accept(ClassVisitor classVisitor, int flags);

    abstract ProbingClassReader.ClassWriterContainer<?> toClassWriter(int flags);

    static class OfAsm extends ProbingResolver {

        private final ClassReader classReader;
        private final Attribute[] attributePrototypes;

        OfAsm(byte[] classFile, Attribute[] attributePrototypes) {
            classReader = new ClassReader(classFile);
            this.attributePrototypes = attributePrototypes;
        }

        @Override
        void accept(ClassVisitor classVisitor, int flags) {
            classReader.accept(classVisitor, attributePrototypes, flags);
        }

        @Override
        ProbingClassReader.ClassWriterContainer<?> toClassWriter(int flags) {
            return new ProbingClassReader.ClassWriterContainer.OfAsm(classReader, flags);
        }
    }

    static class OfJdk extends ProbingResolver {

        private final JdkClassReader classReader;

        OfJdk(byte[] classFile, Attribute[] attributePrototypes) {
            classReader = new JdkClassReader(classFile, attributePrototypes);
        }

        @Override
        void accept(ClassVisitor classVisitor, int flags) {
            classReader.accept(classVisitor, flags);
        }

        @Override
        ProbingClassReader.ClassWriterContainer<?> toClassWriter(int flags) {
            return new ProbingClassReader.ClassWriterContainer.OfJdk(classReader, flags);
        }
    }
}
