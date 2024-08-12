package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class JdkClassWriter extends ClassVisitor {

    private final ClassFile classFile;

    private ClassDesc thisClass;
    private Consumer<ClassBuilder> consumer = _ -> { };
    private byte[] bytes;

    public JdkClassWriter() {
        this(ClassFile.of(ClassFile.DeadCodeOption.KEEP_DEAD_CODE));
    }

    public JdkClassWriter(ClassFile classFile) {
        super(Opcodes.ASM9);
        this.classFile = classFile;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        thisClass = ClassDesc.of(name.replace('/', '.'));
        consumer = consumer.andThen(classBuilder -> {
            classBuilder.withVersion(version & 0xFF, (version >> 8) & 0xFF);
            classBuilder.withFlags(access);
            if (signature != null) {
                classBuilder.with(SignatureAttribute.of(Signature.parseFrom(signature)));
            }
            classBuilder.withSuperclass(ClassDesc.of(superName.replace('/', '.')));
            if (interfaces != null) {
                ClassDesc[] entries = new ClassDesc[interfaces.length];
                for (int index = 0; index < interfaces.length; index++) {
                    entries[index] = ClassDesc.of(interfaces[index].replace('/', '.'));
                }
                classBuilder.withInterfaceSymbols(entries);
            }
        });
    }

    @Override
    public void visitSource(String source, String debug) {
        consumer = consumer.andThen(classBuilder -> {
            if (source != null) {
                classBuilder.with(SourceFileAttribute.of(source));
            }
            if (debug != null) {
                classBuilder.with(SourceDebugExtensionAttribute.of(debug.getBytes(StandardCharsets.UTF_8)));
            }
        });
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        return super.visitModule(name, access, version);
    }

    @Override
    public void visitNestHost(String nestHost) {
        consumer = consumer.andThen(classBuilder -> classBuilder.with(NestHostAttribute.of(ClassDesc.of(nestHost.replace('/', '.')))));
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        super.visitOuterClass(owner, name, descriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        super.visitAttribute(attribute);
    }

    @Override
    public void visitNestMember(String nestMember) {
        super.visitNestMember(nestMember);
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        super.visitPermittedSubclass(permittedSubclass);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        return super.visitRecordComponent(name, descriptor, signature);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return new FieldVisitor(Opcodes.ASM9) {

            @Override
            public void visitEnd() {
                consumer = consumer.andThen(classBuilder -> classBuilder.withField(name, ClassDesc.ofDescriptor(descriptor), fieldBuilder -> {
                    fieldBuilder.withFlags(access);
                    if (signature != null) {
                        fieldBuilder.with(SignatureAttribute.of(Signature.parseFrom(signature)));
                    }
                }));
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9) {



            @Override
            public void visitEnd() {
                consumer = consumer.andThen(classBuilder -> classBuilder.withMethod(name, MethodTypeDesc.ofDescriptor(descriptor), access, methodBuilder -> {
                    if (signature != null) {
                        methodBuilder.with(SignatureAttribute.of(Signature.parseFrom(signature)));
                    }
                    if (exceptions != null) {
                        ClassDesc[] entries = new ClassDesc[exceptions.length];
                        for (int index = 0; index < exceptions.length; index++) {
                            entries[index] = ClassDesc.of(exceptions[index].replace('/', '.'));
                        }
                        methodBuilder.with(ExceptionsAttribute.ofSymbols(entries));
                    }
                }));
            }
        };
    }

    @Override
    public void visitEnd() {
        bytes = classFile.build(thisClass, consumer);
    }

    public byte[] toBytes() {
        if (bytes == null) {
            throw new IllegalStateException();
        }
        return bytes;
    }
}
