package codes.rafael.asmjdkbridge.sample;

import org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class CustomAttribute {

    public static Class<?> make() {
        String generated = CustomAttribute.class.getPackageName() + ".CustomAttributeGen";
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V19,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                generated.replace('.', '/'),
                null,
                Type.getInternalName(Object.class),
                null);
        classWriter.visitAttribute(new ByteArrayAttribute("CustomAttribute", new byte[]{1}));
        FieldVisitor fieldVisitor = classWriter.visitField(Opcodes.ACC_PUBLIC, "f", Type.getDescriptor(Object.class), null, null);
        fieldVisitor.visitAttribute(new ByteArrayAttribute("CustomAttribute", new byte[]{2}));
        classWriter.visitEnd();
        MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "f", Type.getMethodType(Type.VOID_TYPE).getDescriptor(), null, null);
        methodVisitor.visitAttribute(new ByteArrayAttribute("CustomAttribute", new byte[]{3}));
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        byte[] classFile = classWriter.toByteArray();
        try {
            return new ClassLoader(null) {

                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.equals(generated)) {
                        return defineClass(name, classFile, 0, classFile.length);
                    }
                    return super.findClass(name);
                }

                @Override
                public InputStream getResourceAsStream(String name) {
                    if (name.equals(generated.replace('.', '/') + ".class")) {
                        return new ByteArrayInputStream(classFile);
                    }
                    return super.getResourceAsStream(name);
                }
            }.loadClass(generated);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class ByteArrayAttribute extends Attribute {

        private final byte[] bytes;

        private ByteArrayAttribute(String type, byte[] bytes) {
            super(type);
            this.bytes = bytes;
        }

        @Override
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector vector = new ByteVector(bytes.length);
            vector.putByteArray(bytes, 0, bytes.length);
            return vector;
        }
    }
}
