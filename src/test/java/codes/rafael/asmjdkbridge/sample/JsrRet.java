package codes.rafael.asmjdkbridge.sample;

import org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class JsrRet {

    public static Class<?> make() {
        String generated = JsrRet.class.getPackageName() + ".JmpRetGen";
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_3,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                generated.replace('.', '/'),
                null,
                Type.getInternalName(Object.class),
                null);
        MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "m",
                Type.getMethodDescriptor(Type.VOID_TYPE),
                null,
                null);
        methodVisitor.visitCode();
        Label label = new Label();
        methodVisitor.visitJumpInsn(Opcodes.JSR, label);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitLabel(label);
        methodVisitor.visitVarInsn(Opcodes.ASTORE, 0);
        methodVisitor.visitVarInsn(Opcodes.RET, 0);
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
}
