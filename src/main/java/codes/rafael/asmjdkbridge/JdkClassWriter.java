package codes.rafael.asmjdkbridge;

import jdk.classfile.*;
import jdk.classfile.attribute.*;
import jdk.classfile.jdktypes.ModuleDesc;
import org.objectweb.asm.*;
import org.objectweb.asm.Attribute;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

public class JdkClassWriter extends ClassVisitor {

    private OpenBuilder.OpenClassBuilder openClassBuilder;

    public JdkClassWriter() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        openClassBuilder = Classfile.buildOpen(ClassDesc.ofInternalName(name));
        openClassBuilder.accept(classBuilder -> {
            classBuilder
                    .withVersion(version & 0xFF, version >> 24)
                    .withFlags(access & ~(Opcodes.ACC_DEPRECATED | Opcodes.ACC_SYNTHETIC));
            if (superName != null) {
                classBuilder.withSuperclass(ClassDesc.ofInternalName(superName));
            }
            if (interfaces != null && interfaces.length > 0) {
                classBuilder.withInterfaceSymbols(Stream.of(interfaces).map(ClassDesc::ofInternalName).toArray(ClassDesc[]::new));
            }
            if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                classBuilder.with(DeprecatedAttribute.of());
            }
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                classBuilder.with(SyntheticAttribute.of());
            }
            if (signature != null) {
                classBuilder.with(SignatureAttribute.of(ClassSignature.parseFrom(signature)));
            }
        });
    }

    @Override
    public void visitSource(String source, String debug) {
        openClassBuilder.accept(classBuilder -> {
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
        ModuleAttribute.OpenModuleAttributeBuilder openModuleAttributeBuilder = ModuleAttribute.of(ModuleDesc.of(name));
        openModuleAttributeBuilder.accept(moduleAttributeBuilder -> {
            moduleAttributeBuilder.moduleFlags(access);
            if (version != null) {
                moduleAttributeBuilder.moduleVersion(version);
            }
        });
        return new JdkModuleWriter(openClassBuilder, openModuleAttributeBuilder);
    }

    @Override
    public void visitNestHost(String nestHost) {
        openClassBuilder.accept(classBuilder -> classBuilder.with(NestHostAttribute.of(ClassDesc.of(nestHost))));
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        openClassBuilder.accept(classBuilder -> classBuilder.with(EnclosingMethodAttribute.of(
                ClassDesc.ofInternalName(owner),
                Optional.ofNullable(name),
                Optional.of(descriptor).map(MethodTypeDesc::ofDescriptor)))); // TODO: avoid optional in parameters
    }

    @Override
    public void visitNestMember(String nestMember) {
        // TODO: collect and trigger once guaranteed complete
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        // TODO: collect and trigger once guaranteed complete
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // TODO: collect and trigger once guaranteed complete
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        // TODO: collect and trigger once guaranteed complete
        return null;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // TODO: not really considered by ASM
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        OpenBuilder.OpenFieldBuilder openFieldBuilder = openClassBuilder.withField(name, ClassDesc.ofInternalName(descriptor));
        openFieldBuilder.accept(fieldBuilder -> {
            fieldBuilder.withFlags(access & ~(Opcodes.ACC_DEPRECATED | Opcodes.ACC_SYNTHETIC));
            if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                fieldBuilder.with(DeprecatedAttribute.of());
            }
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                fieldBuilder.with(SyntheticAttribute.of());
            }
            if (signature != null) {
                fieldBuilder.with(SignatureAttribute.of(Signature.parseFrom(signature)));
            }
            // TODO: default object
        });
        return new JdkFieldWriter(openFieldBuilder);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // TODO: why flags already here? (inconsistent)
        OpenBuilder.OpenMethodBuilder openMethodBuilder = openClassBuilder.withMethod(name,
                MethodTypeDesc.ofDescriptor(descriptor),
                access & ~(Opcodes.ACC_DEPRECATED | Opcodes.ACC_SYNTHETIC));
        openMethodBuilder.accept(methodBuilder -> {
            methodBuilder.withFlags(access & ~(Opcodes.ACC_DEPRECATED | Opcodes.ACC_SYNTHETIC));
            if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                methodBuilder.with(DeprecatedAttribute.of());
            }
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                methodBuilder.with(SyntheticAttribute.of());
            }
            if (signature != null) {
                methodBuilder.with(SignatureAttribute.of(Signature.parseFrom(signature)));
            }
            if (exceptions != null) {
                methodBuilder.with(ExceptionsAttribute.ofSymbols(Stream.of(exceptions).map(ClassDesc::ofInternalName).toArray(ClassDesc[]::new)));
            }
        });
        return new JdkMethodWriter(openMethodBuilder);
    }

    @Override
    public void visitEnd() {
        openClassBuilder.close();
    }

    public byte[] toByteArray() {
        return openClassBuilder.toClassfile();
    }
}
