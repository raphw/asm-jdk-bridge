package codes.rafael.asmjdkbridge.sample;

import codes.rafael.asmjdkbridge.AsmTestAttribute;
import codes.rafael.asmjdkbridge.JdkClassWriterTest;
import org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class CustomAttributeExtractable {

    public static Class<?> make() {
        String generated = CustomAttributeExtractable.class.getPackageName() + ".CustomAttributeGen";
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V19,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                generated.replace('.', '/'),
                null,
                Type.getInternalName(Object.class),
                null);
        classWriter.visitAttribute(new AsmTestAttribute(new byte[]{1}));
        FieldVisitor fieldVisitor = classWriter.visitField(Opcodes.ACC_PUBLIC, "f", "Ljava/lang/Object;", null, null);
        fieldVisitor.visitAttribute(new AsmTestAttribute(new byte[]{2}));
        classWriter.visitEnd();
        MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "f", "()V", null, null);
        methodVisitor.visitAttribute(new AsmTestAttribute(new byte[]{3}));
        methodVisitor.visitAttribute(new AsmTestAttribute.AsmCodeTestAttribute(new byte[]{4}));
        methodVisitor.visitCode();
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
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
}
