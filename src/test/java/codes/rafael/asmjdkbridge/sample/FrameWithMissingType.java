package codes.rafael.asmjdkbridge.sample;

import org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FrameWithMissingType {

    public static Class<?> make() {
        Map<String, byte[]> classFiles = new HashMap<>();
        {
            ClassWriter classWriter = new ClassWriter(0);
            addDefaultConstructor(classWriter, Type.getInternalName(Object.class));
            classWriter.visit(Opcodes.V19,
                    Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
                    Type.getInternalName(FrameWithMissingType.class) + "$Base",
                    null,
                    Type.getInternalName(Object.class),
                    null);
            classWriter.visitMethod(Opcodes.ACC_ABSTRACT, "m", "()V", null, null).visitEnd();
            classWriter.visitEnd();
            classFiles.put(FrameWithMissingType.class.getName() + "$Base", classWriter.toByteArray());
        }
        {
            ClassWriter classWriter = new ClassWriter(0);
            addDefaultConstructor(classWriter, Type.getInternalName(FrameWithMissingType.class) + "$Base");
            classWriter.visit(Opcodes.V19,
                    Opcodes.ACC_SUPER,
                    Type.getInternalName(FrameWithMissingType.class) + "$Sub",
                    null,
                    Type.getInternalName(FrameWithMissingType.class) + "$Base",
                    null);
            MethodVisitor methodVisitor = classWriter.visitMethod(0, "m", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 1);
            classWriter.visitEnd();
            classFiles.put(FrameWithMissingType.class.getName() + "$Sub", classWriter.toByteArray());
        }
        {
            ClassWriter classWriter = new ClassWriter(0);
            addDefaultConstructor(classWriter, Type.getInternalName(Object.class));
            classWriter.visit(Opcodes.V19,
                    Opcodes.ACC_SUPER,
                    Type.getInternalName(FrameWithMissingType.class) + "$Exec",
                    null,
                    Type.getInternalName(Object.class),
                    null);
            MethodVisitor methodVisitor = classWriter.visitMethod(0, "m", "(I)V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
            Label branch = new Label(), end = new Label();
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, branch);
            methodVisitor.visitTypeInsn(Opcodes.NEW, Type.getInternalName(FrameWithMissingType.class) + "$Sub");
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    Type.getInternalName(FrameWithMissingType.class) + "$Sub",
                    "<init>",
                    "()V",
                    false);
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
            methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
            methodVisitor.visitLabel(branch);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            methodVisitor.visitTypeInsn(Opcodes.NEW, Type.getInternalName(FrameWithMissingType.class) + "$Missing");
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    Type.getInternalName(FrameWithMissingType.class) + "$Missing",
                    "<init>",
                    "()V",
                    false);
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
            methodVisitor.visitLabel(end);
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Type.getInternalName(FrameWithMissingType.class) + "$Base"}, 0, null);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(FrameWithMissingType.class) + "$Base", "m", "()V", false);
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(2, 3);
            methodVisitor.visitEnd();
            classWriter.visitEnd();
            classFiles.put(FrameWithMissingType.class.getName() + "$Exec", classWriter.toByteArray());
        }

        try {
            return new ClassLoader(null) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.startsWith(FrameWithMissingType.class.getName() + "$")) {
                        byte[] bytes = classFiles.get(name);
                        if (bytes == null) {
                            return super.findClass(name);
                        }
                        return defineClass(name, bytes, 0, bytes.length);
                    } else {
                        return super.findClass(name);
                    }
                }

                @Override
                public InputStream getResourceAsStream(String name) {
                    if (name.endsWith(".class") && name.startsWith(FrameWithMissingType.class.getName().replace('.', '/') + "$")) {
                        byte[] bytes = classFiles.get(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                        if (bytes == null) {
                            return super.getResourceAsStream(name);
                        }
                        return new ByteArrayInputStream(bytes);
                    } else {
                        return super.getResourceAsStream(name);
                    }
                }
            }.loadClass(FrameWithMissingType.class.getName() + "$Exec");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void addDefaultConstructor(ClassVisitor classVisitor, String superType) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superType, "<init>", "()V", false);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(1, 1);
    }
}
