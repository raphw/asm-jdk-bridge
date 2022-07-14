package codes.rafael.asmjdkbridge.sample;

import org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class ConstantDynamic {

    public static Class<?> make() {
        Method method;
        try {
            method = ConstantBootstraps.class.getMethod("nullConstant", MethodHandles.Lookup.class, String.class, Class.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        String generated = ConstantDynamic.class.getPackageName() + ".CustomConstant";
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V19,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                generated.replace('.', '/'),
                null,
                Type.getInternalName(Object.class),
                null);
        MethodVisitor methodVisitor = classWriter.visitMethod(0, "m", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitLdcInsn(new org.objectweb.asm.ConstantDynamic("_",
                Type.getDescriptor(Object.class),
                new Handle(Opcodes.H_INVOKESTATIC,
                        Type.getInternalName(ConstantBootstraps.class),
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        false)));
        methodVisitor.visitInsn(Opcodes.POP);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(1, 1);
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
