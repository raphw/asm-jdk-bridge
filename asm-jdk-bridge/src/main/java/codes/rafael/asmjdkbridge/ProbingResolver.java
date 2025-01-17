package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.function.Function;

abstract class ProbingResolver {

    private static final String OBJECT = "java/lang/Object";

    private static final int SUPPORTED;

    static {
        int version = Opcodes.V24;
        try {
            int candidate = 25;
            while (!Thread.interrupted()) {
                version = (Integer) Opcodes.class.getField("V" + candidate++).get(null);
            }
        } catch (Throwable ignored) {
        }
        SUPPORTED = version & 0xFFFF;
    }

    static ClassVisitor ofVersion(int flags, int version, Function<String, String> getSuperClass) {
        if (version > SUPPORTED) {
            return new JdkClassWriter(flags, getSuperClass);
        } else {
            return new ClassWriter(flags) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    if (getSuperClass == null) {
                        return super.getCommonSuperClass(type1, type2);
                    }
                    if (type1.equals(type2)) {
                        return type1;
                    }
                    String class1 = getSuperClass.apply(type1), class2 = getSuperClass.apply(type2);
                    if (class1 == null || class2 == null) {
                        return OBJECT;
                    } else {
                        while (!class1.equals(OBJECT)) {
                            if (class1.equals(type2)) {
                                return type2;
                            }
                            class1 = getSuperClass.apply(class1);
                        }
                        while (!class2.equals(OBJECT)) {
                            if (class2.equals(type1)) {
                                return type1;
                            }
                            class2 = getSuperClass.apply(class2);
                        }
                        return OBJECT;
                    }
                }
            };
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
