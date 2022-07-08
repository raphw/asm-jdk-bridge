package codes.rafael.asmjdkbridge;

import jdk.classfile.*;
import jdk.classfile.attribute.*;
import jdk.classfile.jdktypes.ModuleDesc;
import org.objectweb.asm.*;
import org.objectweb.asm.Attribute;

import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JdkClassWriter extends ClassVisitor {

    private OpenBuilder.OpenClassBuilder openClassBuilder;

    private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
    private final List<ClassDesc> permittedSubclasses = new ArrayList<>(), nestMembers = new ArrayList<>();
    private boolean isRecord;
    private final List<RecordComponentInfo> recordComponentInfos = new ArrayList<>();
    private final List<InnerClassInfo> innerClassInfos = new ArrayList<>();

    public JdkClassWriter() {
        super(Opcodes.ASM9);
    }

    static ConstantDesc toJdkConstant(Object constant) {
        return switch (constant) {
            case Integer i -> i;
            case Long l -> l;
            case Float f -> f;
            case Double d -> d;
            case String s -> s;
            case Type t -> t.getSort() == Type.METHOD
                    ? MethodTypeDesc.ofDescriptor(t.getDescriptor())
                    : ClassDesc.ofDescriptor(t.getDescriptor());
            case Handle h -> MethodHandleDesc.of(DirectMethodHandleDesc.Kind.valueOf(h.getTag(), h.isInterface()),
                    ClassDesc.ofInternalName(h.getOwner()),
                    h.getName(),
                    h.getDesc());
            case ConstantDynamic c -> DynamicConstantDesc.ofCanonical((DirectMethodHandleDesc) toJdkConstant(c.getBootstrapMethod()),
                    c.getName(),
                    ClassDesc.ofDescriptor(c.getDescriptor()),
                    IntStream.range(0, c.getBootstrapMethodArgumentCount())
                            .mapToObj(i -> toJdkConstant(c.getBootstrapMethodArgument(i)))
                            .toArray(ConstantDesc[]::new));
            default -> throw new IllegalArgumentException("Unknown ASM constant: " + constant);
        };
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        openClassBuilder = Classfile.buildOpen(ClassDesc.ofInternalName(name));
        openClassBuilder.accept(classBuilder -> {
            classBuilder
                    .withVersion(version & 0xFF, version >> 24)
                    .withFlags(access & ~(Opcodes.ACC_DEPRECATED | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_RECORD));
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
        isRecord = (access & Opcodes.ACC_RECORD) != 0;
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
        openClassBuilder.accept(classBuilder -> classBuilder.with(NestHostAttribute.of(ClassDesc.ofInternalName(nestHost))));
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        openClassBuilder.accept(classBuilder -> classBuilder.with(EnclosingMethodAttribute.of(
                ClassDesc.ofInternalName(owner),
                Optional.ofNullable(name),
                Optional.of(descriptor).map(MethodTypeDesc::ofDescriptor))));
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // TODO: not really considered by ASM
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return new JdkAnnotationExtractor(descriptor, annotation -> {
            if (visible) {
                visibleAnnotations.add(annotation);
            } else {
                invisibleAnnotations.add(annotation);
            }
        });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {
        });
    }

    private void completeAttributes() {
        if (!visibleAnnotations.isEmpty()) {
            openClassBuilder.accept(classBuilder -> RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations));
            visibleAnnotations.clear();
        }
        if (!invisibleAnnotations.isEmpty()) {
            openClassBuilder.accept(classBuilder -> RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations));
            invisibleAnnotations.clear();
        }
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        completeAttributes();
        permittedSubclasses.add(ClassDesc.ofInternalName(permittedSubclass));
    }

    @Override
    public void visitNestMember(String nestMember) {
        completeAttributes();
        nestMembers.add(ClassDesc.ofInternalName(nestMember));
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        completeAttributes();
        return new JdkRecordComponentExtractor(name, descriptor, signature, recordComponentInfos::add);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        completeAttributes();
        innerClassInfos.add(InnerClassInfo.of(ClassDesc.ofInternalName(name),
                Optional.ofNullable(outerName).map(ClassDesc::ofInternalName),
                Optional.ofNullable(innerName),
                access));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        completeAttributes();
        OpenBuilder.OpenFieldBuilder openFieldBuilder = openClassBuilder.withField(name, ClassDesc.ofDescriptor(descriptor));
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
            if (value != null) {
                fieldBuilder.with(ConstantValueAttribute.of(toJdkConstant(value)));
            }
        });
        return new JdkFieldWriter(openFieldBuilder);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        completeAttributes();
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
                methodBuilder.with(SignatureAttribute.of(MethodSignature.parseFrom(signature)));
            }
            if (exceptions != null) {
                methodBuilder.with(ExceptionsAttribute.ofSymbols(Stream.of(exceptions).map(ClassDesc::ofInternalName).toArray(ClassDesc[]::new)));
            }
        });
        return new JdkMethodWriter(descriptor, openMethodBuilder);
    }

    private void completeMain() {
        if (!permittedSubclasses.isEmpty()) {
            openClassBuilder.accept(classBuilder -> classBuilder.with(PermittedSubclassesAttribute.ofSymbols(permittedSubclasses)));
            permittedSubclasses.clear();
        }
        if (!nestMembers.isEmpty()) {
            openClassBuilder.accept(classBuilder -> classBuilder.with(NestMembersAttribute.ofSymbols(nestMembers)));
            nestMembers.clear();
        }
        if (isRecord || !recordComponentInfos.isEmpty()) {
            openClassBuilder.accept(classBuilder -> classBuilder.with(RecordAttribute.of(recordComponentInfos)));
            isRecord = false;
            recordComponentInfos.clear();
        }
        if (!innerClassInfos.isEmpty()) {
            openClassBuilder.accept(classBuilder -> classBuilder.with(InnerClassesAttribute.of(innerClassInfos)));
            innerClassInfos.clear();
        }
    }

    @Override
    public void visitEnd() {
        completeAttributes();
        completeMain();
        openClassBuilder.close();
    }

    public byte[] toByteArray() {
        return openClassBuilder.toClassfile();
    }
}
