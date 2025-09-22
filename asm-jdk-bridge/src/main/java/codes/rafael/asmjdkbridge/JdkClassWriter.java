package codes.rafael.asmjdkbridge;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * A class visitor that creates a class file which is based upon the JDK Class File API.
 */
public class JdkClassWriter extends ClassVisitor {

    /**
     * Creates a class writer.
     *
     * @param flags The ASM flags to consider.
     */
    public JdkClassWriter(int flags) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a class writer.
     *
     * @param classReader A class reader of which to retain the constant pool, if possible.
     * @param flags       The ASM flags to consider.
     */
    public JdkClassWriter(JdkClassReader classReader, int flags) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a class writer.
     *
     * @param flags         The ASM flags to consider.
     * @param getSuperClass A resolver for the supplied internal class name's internal super class name. If
     *                      a class is an interface, {@code null} should be returned. As a method to allow
     *                      pre-Java 8 code to call this constructor via reflection.
     * @param target        The target to invoke the reflective method on.
     */
    public JdkClassWriter(int flags, Method getSuperClass, Object target) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a class writer.
     *
     * @param classReader   A class reader of which to retain the constant pool, if possible.
     * @param flags         The ASM flags to consider.
     * @param getSuperClass A resolver for the supplied internal class name's internal super class name. If
     *                      a class is an interface, {@code null} should be returned. As a method to allow
     *                      pre-Java 8 code to call this constructor via reflection.
     * @param target        The target to invoke the reflective method on.
     */
    public JdkClassWriter(JdkClassReader classReader, int flags, Method getSuperClass, Object target) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a class writer.
     *
     * @param flags         The ASM flags to consider.
     * @param getSuperClass A resolver for the supplied internal class name's internal super class name. If
     *                      a class is an interface, {@code null} should be returned.
     */
    public JdkClassWriter(int flags, Function<String, String> getSuperClass) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a class writer.
     *
     * @param classReader   A class reader of which to retain the constant pool, if possible.
     * @param flags         The ASM flags to consider.
     * @param getSuperClass A resolver for the supplied internal class name's internal super class name. If
     *                      a class is an interface, {@code null} should be returned.
     */
    public JdkClassWriter(JdkClassReader classReader, int flags, Function<String, String> getSuperClass) {
        super(Opcodes.ASM9);
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the generated class file.
     *
     * @return The class file as a byte array.
     */
    public byte[] toByteArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the super class of the class that is provided by name. The default implementation
     * resolves the super class from this instance's class' {@link ClassLoader}, unless
     * {@link #getClassLoader()} is overridden.
     * <p>
     * This is used for generating stack map frames.
     *
     * @param name The name of the class for which to resolve the super class.
     * @return The name of the resolved super class.
     */
    protected String getSuperClass(String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the class loader to use for resolving the super class of a discovered class.
     *
     * @return The class loader to use for resolving a super class's name.
     */
    protected ClassLoader getClassLoader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void visitSource(String source, String debug) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitNestHost(String nestHost) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitNestMember(String nestMember) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitEnd() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param classWriterFlags {@link ClassWriter} flags.
     * @return An instance of {@code ClassFile} configured according to the given flags.
     */
    protected Object getClassFile(int classWriterFlags) {
        throw new UnsupportedOperationException();
    }
}
