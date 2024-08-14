package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.classfile.ClassReader;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class JdkClassWriter extends ClassVisitor {

    private final int flags;
    private final ConstantPoolBuilder constantPoolBuilder;
    private final Function<Attribute, byte[]> extractor;

    private final List<ClassDesc> nestMembers = new ArrayList<>();
    private final List<InnerClassInfo> innerClasses = new ArrayList<>();
    private final List<ClassDesc> permittedSubclasses = new ArrayList<>();
    private final List<RecordComponentInfo> recordComponents = new ArrayList<>();
    private final List<RawAttribute> attributes = new ArrayList<>();
    private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
    private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();

    private ClassDesc thisClass;
    private boolean isRecord;
    private List<Consumer<ClassBuilder>> classConsumers = new ArrayList<>(List.of(classBuilder -> {
        for (RawAttribute attribute : attributes) {
            classBuilder.with(attribute);
        }
        if (!visibleAnnotations.isEmpty()) {
            classBuilder.with(RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations));
        }
        if (!invisibleAnnotations.isEmpty()) {
            classBuilder.with(RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations));
        }
        if (!visibleTypeAnnotations.isEmpty()) {
            classBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(visibleTypeAnnotations));
        }
        if (!invisibleTypeAnnotations.isEmpty()) {
            classBuilder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(invisibleTypeAnnotations));
        }
        if (!nestMembers.isEmpty()) {
            classBuilder.with(NestMembersAttribute.ofSymbols(nestMembers));
        }
        if (!innerClasses.isEmpty()) {
            classBuilder.with(InnerClassesAttribute.of(innerClasses));
        }
        if (!permittedSubclasses.isEmpty()) {
            classBuilder.with(PermittedSubclassesAttribute.ofSymbols(permittedSubclasses));
        }
        if (isRecord || !recordComponents.isEmpty()) {
            classBuilder.with(RecordAttribute.of(recordComponents));
        }
    }));

    private byte[] bytes;

    public JdkClassWriter(int flags) {
        this(flags, attribute -> {
            throw new UnsupportedOperationException("Unknown attribute: " + attribute);
        });
    }

    public JdkClassWriter(int flags, ConstantPoolBuilder constantPoolBuilder) {
        this(flags, constantPoolBuilder, attribute -> {
            throw new UnsupportedOperationException("Unknown attribute: " + attribute);
        });
    }

    public JdkClassWriter(int flags, Function<Attribute, byte[]> extractor) {
        this(flags, null, extractor);
    }

    public JdkClassWriter(int flags, ConstantPoolBuilder constantPoolBuilder, Function<Attribute, byte[]> extractor) {
        super(Opcodes.ASM9);
        this.flags = flags;
        this.constantPoolBuilder = constantPoolBuilder;
        this.extractor = extractor;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        thisClass = ClassDesc.ofInternalName(name);
        isRecord = (access & Opcodes.ACC_RECORD) != 0;
        classConsumers.add(classBuilder -> {
            classBuilder.withVersion(version & 0xFFFF, version >>> 16);
            classBuilder.withFlags(access & ~(Opcodes.ACC_DEPRECATED | Opcodes.ACC_RECORD));
            if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                classBuilder.with(DeprecatedAttribute.of());
            }
            if (signature != null) {
                classBuilder.with(SignatureAttribute.of(classBuilder.constantPool().utf8Entry(signature)));
            }
            if (superName != null) {
                classBuilder.withSuperclass(ClassDesc.ofInternalName(superName));
            }
            if (interfaces != null) {
                ClassDesc[] entries = new ClassDesc[interfaces.length];
                for (int index = 0; index < interfaces.length; index++) {
                    entries[index] = ClassDesc.ofInternalName(interfaces[index]);
                }
                classBuilder.withInterfaceSymbols(entries);
            }
        });
    }

    @Override
    public void visitSource(String source, String debug) {
        classConsumers.add(classBuilder -> {
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
        return new ModuleVisitor(Opcodes.ASM9) {

            private String mainClass;
            private List<PackageDesc> packages = new ArrayList<>();

            private List<Consumer<ModuleAttribute.ModuleAttributeBuilder>> moduleAttributeConsumers = new ArrayList<>(List.of(moduleAttributeBuilder -> {
                moduleAttributeBuilder.moduleFlags(access & ~Opcodes.ACC_DEPRECATED);
                if (version != null) {
                    moduleAttributeBuilder.moduleVersion(version);
                }
            }));

            @Override
            public void visitMainClass(String mainClass) {
                this.mainClass = mainClass;
            }

            @Override
            public void visitPackage(String packaze) {
                packages.add(PackageDesc.ofInternalName(packaze));
            }

            @Override
            public void visitRequire(String module, int access, String version) {
                moduleAttributeConsumers.add(moduleAttributeBuilder -> moduleAttributeBuilder.requires(
                        ModuleDesc.of(module),
                        access,
                        version));
            }

            @Override
            public void visitExport(String packaze, int access, String... modules) {
                moduleAttributeConsumers.add(moduleAttributeBuilder -> {
                    ModuleDesc[] descriptions = new ModuleDesc[modules.length];
                    for (int index = 0; index < modules.length; index++) {
                        descriptions[index] = ModuleDesc.of(modules[index]);
                    }
                    moduleAttributeBuilder.exports(PackageDesc.ofInternalName(packaze),
                            access,
                            descriptions);
                });
            }

            @Override
            public void visitOpen(String packaze, int access, String... modules) {
                moduleAttributeConsumers.add(moduleAttributeBuilder -> {
                    ModuleDesc[] descriptions = new ModuleDesc[modules.length];
                    for (int index = 0; index < modules.length; index++) {
                        descriptions[index] = ModuleDesc.of(modules[index]);
                    }
                    moduleAttributeBuilder.opens(PackageDesc.ofInternalName(packaze),
                            access,
                            descriptions);
                });
            }

            @Override
            public void visitUse(String service) {
                moduleAttributeConsumers.add(moduleAttributeBuilder ->
                        moduleAttributeBuilder.uses(ClassDesc.ofInternalName(service)));
            }

            @Override
            public void visitProvide(String service, String... providers) {
                moduleAttributeConsumers.add(moduleAttributeBuilder -> {
                    ClassDesc[] descriptions = new ClassDesc[providers.length];
                    for (int index = 0; index < providers.length; index++) {
                        descriptions[index] = ClassDesc.of(providers[index]);
                    }
                    moduleAttributeBuilder.provides(ClassDesc.ofInternalName(service), descriptions);
                });
            }

            @Override
            public void visitEnd() {
                classConsumers.add(classBuilder -> {
                    classBuilder.with(ModuleAttribute.of(
                            ModuleDesc.of(name),
                            moduleAttributeBuilder -> moduleAttributeConsumers.forEach(moduleAttributeConsumer -> moduleAttributeConsumer.accept(moduleAttributeBuilder))));
                    if (mainClass != null) {
                        classBuilder.with(ModuleMainClassAttribute.of(ClassDesc.ofInternalName(mainClass)));
                    }
                    if (!packages.isEmpty()) {
                        classBuilder.with(ModulePackagesAttribute.ofNames(packages));
                    }
                });
            }
        };
    }

    @Override
    public void visitNestHost(String nestHost) {
        classConsumers.add(classBuilder -> classBuilder.with(NestHostAttribute.of(ClassDesc.ofInternalName(nestHost))));
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        classConsumers.add(classBuilder -> classBuilder.with(EnclosingMethodAttribute.of(
                ClassDesc.ofInternalName(owner),
                Optional.ofNullable(name),
                Optional.ofNullable(descriptor).map(MethodTypeDesc::ofDescriptor))));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return WritingAnnotationVisitor.of(
                descriptor,
                (visible ? visibleAnnotations : invisibleAnnotations)::add);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        attributes.add(new RawAttribute(attribute.type, extractor.apply(attribute)));
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return WritingAnnotationVisitor.ofTypeAnnotation(
                descriptor,
                typeRef,
                typePath,
                (visible ? visibleTypeAnnotations: invisibleTypeAnnotations)::add);
    }

    @Override
    public void visitNestMember(String nestMember) {
        nestMembers.add(ClassDesc.ofInternalName(nestMember));
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        permittedSubclasses.add(ClassDesc.ofInternalName(permittedSubclass));
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        innerClasses.add(InnerClassInfo.of(ClassDesc.ofInternalName(name),
                Optional.ofNullable(outerName).map(ClassDesc::ofInternalName),
                Optional.ofNullable(innerName),
                access));
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        return new RecordComponentVisitor(Opcodes.ASM9) {

            private final List<RawAttribute> attributes = new ArrayList<>();
            private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
            private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();

            @Override
            public void visitAttribute(Attribute attribute) {
                attributes.add(new RawAttribute(attribute.type, extractor.apply(attribute)));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return WritingAnnotationVisitor.of(
                        descriptor,
                        (visible ? visibleAnnotations : invisibleAnnotations)::add);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                return WritingAnnotationVisitor.ofTypeAnnotation(
                        descriptor,
                        typeRef,
                        typePath,
                        (visible ? visibleTypeAnnotations: invisibleTypeAnnotations)::add);
            }

            @Override
            public void visitEnd() {
                List<java.lang.classfile.Attribute<?>> attributes = new ArrayList<>(this.attributes);
                if (!visibleAnnotations.isEmpty()) {
                    attributes.add(RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations));
                }
                if (!invisibleAnnotations.isEmpty()) {
                    attributes.add(RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations));
                }
                if (!visibleTypeAnnotations.isEmpty()) {
                    attributes.add(RuntimeVisibleTypeAnnotationsAttribute.of(visibleTypeAnnotations));
                }
                if (!invisibleTypeAnnotations.isEmpty()) {
                    attributes.add(RuntimeInvisibleTypeAnnotationsAttribute.of(invisibleTypeAnnotations));
                }
                recordComponents.add(RecordComponentInfo.of(name, ClassDesc.ofDescriptor(descriptor), attributes));
            }
        };
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return new FieldVisitor(Opcodes.ASM9) {

            private final List<RawAttribute> attributes = new ArrayList<>();
            private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
            private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();

            @Override
            public void visitAttribute(Attribute attribute) {
                attributes.add(new RawAttribute(attribute.type, extractor.apply(attribute)));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return WritingAnnotationVisitor.of(
                        descriptor,
                        (visible ? visibleAnnotations : invisibleAnnotations)::add);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                return WritingAnnotationVisitor.ofTypeAnnotation(
                        descriptor,
                        typeRef,
                        typePath,
                        (visible ? visibleTypeAnnotations: invisibleTypeAnnotations)::add);
            }

            @Override
            public void visitEnd() {
                classConsumers.add(classBuilder -> classBuilder.withField(name, ClassDesc.ofDescriptor(descriptor), fieldBuilder -> {
                    fieldBuilder.withFlags(access & ~Opcodes.ACC_DEPRECATED);
                    if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                        fieldBuilder.with(DeprecatedAttribute.of());
                    }
                    if (signature != null) {
                        fieldBuilder.with(SignatureAttribute.of(classBuilder.constantPool().utf8Entry(signature)));
                    }
                    for (RawAttribute attribute : attributes) {
                        fieldBuilder.with(attribute);
                    }
                    if (!visibleAnnotations.isEmpty()) {
                        fieldBuilder.with(RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations));
                    }
                    if (!invisibleAnnotations.isEmpty()) {
                        fieldBuilder.with(RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations));
                    }
                    if (!visibleTypeAnnotations.isEmpty()) {
                        fieldBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(visibleTypeAnnotations));
                    }
                    if (!invisibleTypeAnnotations.isEmpty()) {
                        fieldBuilder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(invisibleTypeAnnotations));
                    }
                    if (value != null) {
                        fieldBuilder.with(ConstantValueAttribute.of(toConstantDesc(value)));
                    }
                }));
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9) {

            private List<Consumer<CodeBuilder>> codeConsumers;

            private final List<RawAttribute> attributes = new ArrayList<>();
            private AnnotationValue defaultValue;
            private int catchCount = -1;
            private Label currentLocation;
            private final List<StackMapFrameInfo.VerificationTypeInfo> locals = new ArrayList<>();
            private final List<MethodParameterInfo> methodParameters = new ArrayList<>();
            private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
            private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();
            private final Map<Integer, List<Annotation>> visibleParameterAnnotations = new HashMap<>(), invisibleParameterAnnotations = new HashMap<>();
            private int visibleParameterAnnotationsCount, invisibleParameterAnnotationsCount;

            private List<StackMapFrameInfo> stackMapFrames;
            private Map<Label, java.lang.classfile.Label> labels;

            @Override
            public void visitCode() {
                codeConsumers = new ArrayList<>();
                codeConsumers.add(_ -> {
                    stackMapFrames = new ArrayList<>();
                    labels = new HashMap<>();
                });
                if ((flags & ClassWriter.COMPUTE_FRAMES) == 0) {
                    if ((access & Opcodes.ACC_STATIC) == 0) {
                        locals.add(name.equals("<init>")
                            ? StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_UNINITIALIZED_THIS
                            : StackMapFrameInfo.ObjectVerificationTypeInfo.of(thisClass));
                    }
                    Type type = Type.getMethodType(descriptor);
                    for (Type argumentType : type.getArgumentTypes()) {
                        locals.add(switch (argumentType.getSort()) {
                            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_INTEGER;
                            case Type.LONG -> StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_LONG;
                            case Type.FLOAT -> StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_FLOAT;
                            case Type.DOUBLE -> StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_DOUBLE;
                            default -> StackMapFrameInfo.ObjectVerificationTypeInfo.of(ClassDesc.ofDescriptor(argumentType.getDescriptor()));
                        });
                    }
                }
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                attributes.add(new RawAttribute(attribute.type, extractor.apply(attribute)));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return WritingAnnotationVisitor.of(
                        descriptor,
                        (visible ? visibleAnnotations : invisibleAnnotations)::add);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                return WritingAnnotationVisitor.ofTypeAnnotation(
                        descriptor,
                        typeRef,
                        typePath,
                        (visible ? visibleTypeAnnotations: invisibleTypeAnnotations)::add);
            }

            @Override
            public void visitParameter(String name, int access) {
                methodParameters.add(MethodParameterInfo.ofParameter(Optional.ofNullable(name), access));
            }

            @Override
            public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                if (visible) {
                    visibleParameterAnnotationsCount = parameterCount;
                } else {
                    invisibleParameterAnnotationsCount = parameterCount;
                }
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                return WritingAnnotationVisitor.of(
                        descriptor,
                        (visible ? visibleParameterAnnotations : invisibleParameterAnnotations).computeIfAbsent(parameter, _ -> new ArrayList<>())::add);
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                int catchCount = this.catchCount;
                return WritingAnnotationVisitor.ofExceptionTypeAnnotation(
                        descriptor,
                        typeRef,
                        typePath,
                        function -> codeConsumers.add(codeBuilder -> {
                            TypeAnnotation annotation = function.apply(catchCount);
                            codeBuilder.with(visible
                                    ? RuntimeVisibleTypeAnnotationsAttribute.of(annotation)
                                    : RuntimeInvisibleTypeAnnotationsAttribute.of(annotation));

                        }));
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                return WritingAnnotationVisitor.ofLabeledTypeAnnotation(
                        descriptor,
                        typeRef,
                        typePath,
                        function -> codeConsumers.add(codeBuilder -> {
                            TypeAnnotation annotation = function.apply(codeBuilder.newBoundLabel());
                            codeBuilder.with(visible
                                    ? RuntimeVisibleTypeAnnotationsAttribute.of(annotation)
                                    : RuntimeInvisibleTypeAnnotationsAttribute.of(annotation));

                        }));
            }

            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                return WritingAnnotationVisitor.ofValue(value -> defaultValue = value);
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] indices, String descriptor, boolean visible) {
                return WritingAnnotationVisitor.ofTargetedTypeAnnotation(
                        descriptor,
                        typeRef,
                        typePath,
                        function -> codeConsumers.add(codeBuilder -> {
                            List<TypeAnnotation.LocalVarTargetInfo> targets = new ArrayList<>();
                            for (int index = 0; index < start.length; index++) {
                                targets.add(TypeAnnotation.LocalVarTargetInfo.of(
                                        labels.computeIfAbsent(start[index], _ -> codeBuilder.newLabel()),
                                        labels.computeIfAbsent(end[index], _ -> codeBuilder.newLabel()),
                                        indices[index]));
                            }
                            TypeAnnotation annotation = function.apply(targets);
                            codeBuilder.with(visible
                                    ? RuntimeVisibleTypeAnnotationsAttribute.of(annotation)
                                    : RuntimeInvisibleTypeAnnotationsAttribute.of(annotation));

                        }));
            }

            @Override
            public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                if ((flags & ClassWriter.COMPUTE_FRAMES) != 0) {
                    return;
                }
                codeConsumers.add(codeBuilder -> {
                    List<StackMapFrameInfo.VerificationTypeInfo> stacks = new ArrayList<>(numStack);
                    for (int index = 0; index < numStack; index++) {
                        stacks.add(toVerificationTypeInfo(stack[index], label -> labels.computeIfAbsent(label, _ -> codeBuilder.newLabel())));
                    }
                    switch (type) {
                        case Opcodes.F_SAME:
                            break;
                        case Opcodes.F_SAME1:
                            break;
                        case Opcodes.F_APPEND:
                            for (int index = 0; index < numLocal; index++) {
                                locals.add(toVerificationTypeInfo(local[index], label -> labels.computeIfAbsent(label, _ -> codeBuilder.newLabel())));
                            }
                            break;
                        case Opcodes.F_CHOP:
                            locals.subList(locals.size() - numLocal, locals.size()).clear();
                            break;
                        case Opcodes.F_FULL:
                        case Opcodes.F_NEW:
                            locals.clear();
                            for (int index = 0; index < numLocal; index++) {
                                locals.add(toVerificationTypeInfo(local[index], label -> labels.computeIfAbsent(label, _ -> codeBuilder.newLabel())));
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported type: " + type);
                    }
                    stackMapFrames.add(StackMapFrameInfo.of(codeBuilder.newBoundLabel(), new ArrayList<>(locals), stacks));
                });
            }

            private static StackMapFrameInfo.VerificationTypeInfo toVerificationTypeInfo(Object value, Function<Label, java.lang.classfile.Label> labels) {
                if (value == Opcodes.TOP) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_TOP;
                } else if (value == Opcodes.INTEGER) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_INTEGER;
                } else if (value == Opcodes.LONG) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_LONG;
                } else if (value == Opcodes.FLOAT) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_FLOAT;
                } else if (value == Opcodes.DOUBLE) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_DOUBLE;
                } else if (value == Opcodes.NULL) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_NULL;
                } else if (value == Opcodes.UNINITIALIZED_THIS) {
                    return StackMapFrameInfo.SimpleVerificationTypeInfo.ITEM_UNINITIALIZED_THIS;
                } else if (value instanceof Label label) {
                    return StackMapFrameInfo.UninitializedVerificationTypeInfo.of(labels.apply(label));
                } else if (value instanceof String name) {
                    return StackMapFrameInfo.ObjectVerificationTypeInfo.of(name.startsWith("[")
                        ? ClassDesc.ofDescriptor(name)
                        : ClassDesc.ofInternalName(name));
                } else {
                    throw new IllegalArgumentException("Unsupported type: " + value);
                }
            }

            @Override
            public void visitInsn(int opcode) {
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.NOP -> CodeBuilder::nop;
                    case Opcodes.ACONST_NULL -> CodeBuilder::aconst_null;
                    case Opcodes.ICONST_M1 -> CodeBuilder::iconst_m1;
                    case Opcodes.ICONST_0 -> CodeBuilder::iconst_0;
                    case Opcodes.ICONST_1 -> CodeBuilder::iconst_1;
                    case Opcodes.ICONST_2 -> CodeBuilder::iconst_2;
                    case Opcodes.ICONST_3 -> CodeBuilder::iconst_3;
                    case Opcodes.ICONST_4 -> CodeBuilder::iconst_4;
                    case Opcodes.ICONST_5 -> CodeBuilder::iconst_5;
                    case Opcodes.LCONST_0 -> CodeBuilder::lconst_0;
                    case Opcodes.LCONST_1 -> CodeBuilder::lconst_1;
                    case Opcodes.FCONST_0 -> CodeBuilder::fconst_0;
                    case Opcodes.FCONST_1 -> CodeBuilder::fconst_1;
                    case Opcodes.DCONST_0 -> CodeBuilder::dconst_0;
                    case Opcodes.DCONST_1 -> CodeBuilder::dconst_1;
                    case Opcodes.IALOAD -> CodeBuilder::iaload;
                    case Opcodes.LALOAD -> CodeBuilder::laload;
                    case Opcodes.FALOAD -> CodeBuilder::faload;
                    case Opcodes.DALOAD -> CodeBuilder::daload;
                    case Opcodes.AALOAD -> CodeBuilder::aaload;
                    case Opcodes.BALOAD -> CodeBuilder::baload;
                    case Opcodes.CALOAD -> CodeBuilder::caload;
                    case Opcodes.SALOAD -> CodeBuilder::saload;
                    case Opcodes.IASTORE -> CodeBuilder::iastore;
                    case Opcodes.LASTORE -> CodeBuilder::lastore;
                    case Opcodes.FASTORE -> CodeBuilder::fastore;
                    case Opcodes.DASTORE -> CodeBuilder::dastore;
                    case Opcodes.AASTORE -> CodeBuilder::aastore;
                    case Opcodes.BASTORE -> CodeBuilder::bastore;
                    case Opcodes.CASTORE -> CodeBuilder::castore;
                    case Opcodes.SASTORE -> CodeBuilder::sastore;
                    case Opcodes.POP -> CodeBuilder::pop;
                    case Opcodes.POP2 -> CodeBuilder::pop2;
                    case Opcodes.DUP -> CodeBuilder::dup;
                    case Opcodes.DUP_X1 -> CodeBuilder::dup_x1;
                    case Opcodes.DUP_X2 -> CodeBuilder::dup_x2;
                    case Opcodes.DUP2 -> CodeBuilder::dup2;
                    case Opcodes.DUP2_X1 -> CodeBuilder::dup2_x1;
                    case Opcodes.DUP2_X2 -> CodeBuilder::dup2_x2;
                    case Opcodes.SWAP -> CodeBuilder::swap;
                    case Opcodes.IADD -> CodeBuilder::iadd;
                    case Opcodes.LADD -> CodeBuilder::ladd;
                    case Opcodes.FADD -> CodeBuilder::fadd;
                    case Opcodes.DADD -> CodeBuilder::dadd;
                    case Opcodes.ISUB -> CodeBuilder::isub;
                    case Opcodes.LSUB -> CodeBuilder::lsub;
                    case Opcodes.FSUB -> CodeBuilder::fsub;
                    case Opcodes.DSUB -> CodeBuilder::dsub;
                    case Opcodes.IMUL -> CodeBuilder::imul;
                    case Opcodes.LMUL -> CodeBuilder::lmul;
                    case Opcodes.FMUL -> CodeBuilder::fmul;
                    case Opcodes.DMUL -> CodeBuilder::dmul;
                    case Opcodes.IDIV -> CodeBuilder::idiv;
                    case Opcodes.LDIV -> CodeBuilder::ldiv;
                    case Opcodes.FDIV -> CodeBuilder::fdiv;
                    case Opcodes.DDIV -> CodeBuilder::ddiv;
                    case Opcodes.IREM -> CodeBuilder::irem;
                    case Opcodes.LREM -> CodeBuilder::lrem;
                    case Opcodes.FREM -> CodeBuilder::frem;
                    case Opcodes.DREM -> CodeBuilder::drem;
                    case Opcodes.INEG -> CodeBuilder::ineg;
                    case Opcodes.LNEG -> CodeBuilder::lneg;
                    case Opcodes.FNEG -> CodeBuilder::fneg;
                    case Opcodes.DNEG -> CodeBuilder::dneg;
                    case Opcodes.ISHL -> CodeBuilder::ishl;
                    case Opcodes.LSHL -> CodeBuilder::lshl;
                    case Opcodes.ISHR -> CodeBuilder::ishr;
                    case Opcodes.LSHR -> CodeBuilder::lshr;
                    case Opcodes.IUSHR -> CodeBuilder::iushr;
                    case Opcodes.LUSHR -> CodeBuilder::lushr;
                    case Opcodes.IAND -> CodeBuilder::iand;
                    case Opcodes.LAND -> CodeBuilder::land;
                    case Opcodes.IOR -> CodeBuilder::ior;
                    case Opcodes.LOR -> CodeBuilder::lor;
                    case Opcodes.IXOR -> CodeBuilder::ixor;
                    case Opcodes.LXOR -> CodeBuilder::lxor;
                    case Opcodes.I2L -> CodeBuilder::i2l;
                    case Opcodes.I2F -> CodeBuilder::i2f;
                    case Opcodes.I2D -> CodeBuilder::i2d;
                    case Opcodes.L2I -> CodeBuilder::l2i;
                    case Opcodes.L2F -> CodeBuilder::l2f;
                    case Opcodes.L2D -> CodeBuilder::l2d;
                    case Opcodes.F2I -> CodeBuilder::f2i;
                    case Opcodes.F2L -> CodeBuilder::f2l;
                    case Opcodes.F2D -> CodeBuilder::f2d;
                    case Opcodes.D2I -> CodeBuilder::d2i;
                    case Opcodes.D2L -> CodeBuilder::d2l;
                    case Opcodes.D2F -> CodeBuilder::d2f;
                    case Opcodes.I2B -> CodeBuilder::i2b;
                    case Opcodes.I2C -> CodeBuilder::i2c;
                    case Opcodes.I2S -> CodeBuilder::i2s;
                    case Opcodes.LCMP -> CodeBuilder::lcmp;
                    case Opcodes.FCMPL -> CodeBuilder::fcmpl;
                    case Opcodes.FCMPG -> CodeBuilder::fcmpg;
                    case Opcodes.DCMPL -> CodeBuilder::dcmpl;
                    case Opcodes.DCMPG -> CodeBuilder::dcmpg;
                    case Opcodes.IRETURN -> CodeBuilder::ireturn;
                    case Opcodes.LRETURN -> CodeBuilder::lreturn;
                    case Opcodes.FRETURN -> CodeBuilder::freturn;
                    case Opcodes.DRETURN -> CodeBuilder::dreturn;
                    case Opcodes.ARETURN -> CodeBuilder::areturn;
                    case Opcodes.RETURN -> CodeBuilder::return_;
                    case Opcodes.ARRAYLENGTH -> CodeBuilder::arraylength;
                    case Opcodes.ATHROW -> CodeBuilder::athrow;
                    case Opcodes.MONITORENTER -> CodeBuilder::monitorenter;
                    case Opcodes.MONITOREXIT -> CodeBuilder::monitorexit;
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                codeConsumers.add(codeConsumer);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.BIPUSH -> codeBuilder -> codeBuilder.bipush(operand);
                    case Opcodes.SIPUSH -> codeBuilder -> codeBuilder.sipush(operand);
                    case Opcodes.NEWARRAY -> codeBuilder -> codeBuilder.newarray(TypeKind.fromNewArrayCode(operand));
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                codeConsumers.add(codeConsumer);
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.ILOAD -> codeBuilder -> codeBuilder.iload(varIndex);
                    case Opcodes.LLOAD -> codeBuilder -> codeBuilder.lload(varIndex);
                    case Opcodes.FLOAD -> codeBuilder -> codeBuilder.fload(varIndex);
                    case Opcodes.DLOAD -> codeBuilder -> codeBuilder.dload(varIndex);
                    case Opcodes.ALOAD -> codeBuilder -> codeBuilder.aload(varIndex);
                    case Opcodes.ISTORE -> codeBuilder -> codeBuilder.istore(varIndex);
                    case Opcodes.LSTORE -> codeBuilder -> codeBuilder.lstore(varIndex);
                    case Opcodes.FSTORE -> codeBuilder -> codeBuilder.fstore(varIndex);
                    case Opcodes.DSTORE -> codeBuilder -> codeBuilder.dstore(varIndex);
                    case Opcodes.ASTORE -> codeBuilder -> codeBuilder.astore(varIndex);
                    case Opcodes.RET -> codeBuilder -> codeBuilder.with(DiscontinuedInstruction.RetInstruction.of(varIndex));
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                codeConsumers.add(codeConsumer);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                codeConsumers.add(codeBuilder -> codeBuilder.fieldInstruction(
                        switch (opcode) {
                            case Opcodes.GETFIELD -> Opcode.GETFIELD;
                            case Opcodes.PUTFIELD -> Opcode.PUTFIELD;
                            case Opcodes.GETSTATIC -> Opcode.GETSTATIC;
                            case Opcodes.PUTSTATIC -> Opcode.PUTSTATIC;
                            default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                        },
                        ClassDesc.ofInternalName(owner),
                        name,
                        ClassDesc.ofDescriptor(descriptor)));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                codeConsumers.add(codeBuilder -> codeBuilder.invokeInstruction(
                        switch (opcode) {
                            case Opcodes.INVOKEVIRTUAL -> Opcode.INVOKEVIRTUAL;
                            case Opcodes.INVOKEINTERFACE -> Opcode.INVOKEINTERFACE;
                            case Opcodes.INVOKESPECIAL -> Opcode.INVOKESPECIAL;
                            case Opcodes.INVOKESTATIC -> Opcode.INVOKESTATIC;
                            default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                        },
                        owner.startsWith("[")
                            ? ClassDesc.ofDescriptor(owner)
                            : ClassDesc.ofInternalName(owner),
                        name,
                        MethodTypeDesc.ofDescriptor(descriptor),
                        isInterface));
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                ConstantDesc[] constants = new ConstantDesc[bootstrapMethodArguments.length];
                for (int index = 0; index < bootstrapMethodArguments.length; index++) {
                    constants[index] = toConstantDesc(bootstrapMethodArguments[index]);
                }
                codeConsumers.add(codeBuilder -> codeBuilder.invokeDynamicInstruction(DynamicCallSiteDesc.of(
                        MethodHandleDesc.of(
                                DirectMethodHandleDesc.Kind.valueOf(bootstrapMethodHandle.getTag(), bootstrapMethodHandle.isInterface()),
                                ClassDesc.ofInternalName(bootstrapMethodHandle.getOwner()),
                                    bootstrapMethodHandle.getName(),
                                    bootstrapMethodHandle.getDesc()),
                        name,
                        MethodTypeDesc.ofDescriptor(descriptor),
                        constants)));
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.IFEQ -> codeBuilder -> codeBuilder.ifeq(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFNE -> codeBuilder -> codeBuilder.ifne(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFLT -> codeBuilder -> codeBuilder.iflt(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFGE -> codeBuilder -> codeBuilder.ifge(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFGT -> codeBuilder -> codeBuilder.ifgt(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFLE -> codeBuilder -> codeBuilder.ifle(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ICMPEQ -> codeBuilder -> codeBuilder.if_icmpeq(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ICMPNE -> codeBuilder -> codeBuilder.if_icmpne(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ICMPLT -> codeBuilder -> codeBuilder.if_icmplt(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ICMPGE -> codeBuilder -> codeBuilder.if_icmpge(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ICMPGT -> codeBuilder -> codeBuilder.if_icmpgt(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ICMPLE -> codeBuilder -> codeBuilder.if_icmple(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ACMPEQ -> codeBuilder -> codeBuilder.if_acmpeq(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IF_ACMPNE -> codeBuilder -> codeBuilder.if_acmpne(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.GOTO -> codeBuilder -> codeBuilder.goto_(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFNULL -> codeBuilder -> codeBuilder.if_null(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.IFNONNULL -> codeBuilder -> codeBuilder.if_nonnull(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel()));
                    case Opcodes.JSR -> codeBuilder -> codeBuilder.with(DiscontinuedInstruction.JsrInstruction.of(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel())));
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                codeConsumers.add(codeConsumer);
            }

            @Override
            public void visitLdcInsn(Object value) {
                ConstantDesc constant = toConstantDesc(value);
                codeConsumers.add(codeBuilder -> codeBuilder.ldc(constant));
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                codeConsumers.add(codeBuilder -> codeBuilder.iinc(varIndex, increment));
            }

            @Override
            public void visitLabel(Label label) {
                currentLocation = label;
                codeConsumers.add(codeBuilder -> codeBuilder.labelBinding(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel())));
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                codeConsumers.add(codeBuilder -> {
                    SwitchCase[] switchCases = new SwitchCase[labels.length];
                    for (int index = 0; index < labels.length; index++) {
                        switchCases[index] = SwitchCase.of(min + index, this.labels.computeIfAbsent(labels[index], _ -> codeBuilder.newLabel()));
                    }
                    codeBuilder.tableSwitchInstruction(
                            min,
                            max,
                            this.labels.computeIfAbsent(dflt, _ -> codeBuilder.newLabel()),
                            List.of(switchCases));
                });
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                codeConsumers.add(codeBuilder -> {
                    SwitchCase[] switchCases = new SwitchCase[labels.length];
                    for (int index = 0; index < labels.length; index++) {
                        switchCases[index] = SwitchCase.of(keys[index], this.labels.computeIfAbsent(labels[index], _ -> codeBuilder.newLabel()));
                    }
                    codeBuilder.lookupSwitchInstruction(
                            this.labels.computeIfAbsent(dflt, _ -> codeBuilder.newLabel()),
                            List.of(switchCases));
                });
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                ClassDesc description = type.startsWith("[")
                        ? ClassDesc.ofDescriptor(type)
                        : ClassDesc.ofInternalName(type);
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.NEW -> codeBuilder -> codeBuilder.new_(description);
                    case Opcodes.ANEWARRAY -> codeBuilder -> codeBuilder.anewarray(description);
                    case Opcodes.CHECKCAST -> codeBuilder -> codeBuilder.checkcast(description);
                    case Opcodes.INSTANCEOF -> codeBuilder -> codeBuilder.instanceof_(description);
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                codeConsumers.add(codeConsumer);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                codeConsumers.add(codeBuilder -> codeBuilder.multianewarray(ClassDesc.ofDescriptor(descriptor), numDimensions));
            }

            @Override
            public void visitLineNumber(int line, Label start) {
//                if (currentLocation != start) {
//                    throw new IllegalStateException("JDK class writer requires to visit line numbers at current location");
//                }
                codeConsumers.add(codeBuilder -> codeBuilder.lineNumber(line));
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                codeConsumers.add(codeBuilder -> {
                    if (descriptor != null) {
                        codeBuilder.localVariable(
                                index,
                                name,
                                ClassDesc.ofDescriptor(descriptor),
                                labels.computeIfAbsent(start, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(end, _ -> codeBuilder.newLabel()));
                    }
                    if (signature != null) {
                        codeBuilder.localVariableType(
                                index,
                                name,
                                Signature.parseFrom(signature),
                                labels.computeIfAbsent(start, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(end, _ -> codeBuilder.newLabel()));
                    }
                });
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                catchCount += 1;
                codeConsumers.add(codeBuilder -> {
                    if (type == null) {
                        codeBuilder.exceptionCatchAll(labels.computeIfAbsent(start, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(end, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(handler, _ -> codeBuilder.newLabel()));
                    } else {
                        codeBuilder.exceptionCatch(labels.computeIfAbsent(start, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(end, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(handler, _ -> codeBuilder.newLabel()),
                                ClassDesc.ofInternalName(type));
                    }
                });

            }

            @Override
            public void visitEnd() {
                classConsumers.add(classBuilder -> classBuilder.withMethod(name, MethodTypeDesc.ofDescriptor(descriptor), access & ~Opcodes.ACC_DEPRECATED, methodBuilder -> {
                    if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                        methodBuilder.with(DeprecatedAttribute.of());
                    }
                    if (signature != null) {
                        methodBuilder.with(SignatureAttribute.of(classBuilder.constantPool().utf8Entry(signature)));
                    }
                    if (exceptions != null) {
                        ClassDesc[] entries = new ClassDesc[exceptions.length];
                        for (int index = 0; index < exceptions.length; index++) {
                            entries[index] = ClassDesc.ofInternalName(exceptions[index]);
                        }
                        methodBuilder.with(ExceptionsAttribute.ofSymbols(entries));
                    }
                    for (RawAttribute attribute : attributes) {
                        methodBuilder.with(attribute);
                    }
                    if (defaultValue != null) {
                        methodBuilder.with(AnnotationDefaultAttribute.of(defaultValue));
                    }
                    if (!visibleAnnotations.isEmpty()) {
                        methodBuilder.with(RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations));
                    }
                    if (!invisibleAnnotations.isEmpty()) {
                        methodBuilder.with(RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations));
                    }
                    if (!visibleTypeAnnotations.isEmpty()) {
                        methodBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(visibleTypeAnnotations));
                    }
                    if (!invisibleTypeAnnotations.isEmpty()) {
                        methodBuilder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(invisibleTypeAnnotations));
                    }
                    if (!methodParameters.isEmpty()) {
                        methodBuilder.with(MethodParametersAttribute.of(methodParameters));
                    }
                    if (!visibleParameterAnnotations.isEmpty()) {
                        List<List<Annotation>> annotations = new ArrayList<>();
                        for (int index = 0; index < Math.max(visibleParameterAnnotationsCount, visibleParameterAnnotations.size()); index++) {
                            annotations.add(visibleParameterAnnotations.getOrDefault(index, List.of()));
                        }
                        methodBuilder.with(RuntimeVisibleParameterAnnotationsAttribute.of(annotations));
                    }
                    if (!invisibleParameterAnnotations.isEmpty()) {
                        List<List<Annotation>> annotations = new ArrayList<>();
                        for (int index = 0; index < Math.max(invisibleParameterAnnotationsCount, invisibleParameterAnnotations.size()); index++) {
                            annotations.add(invisibleParameterAnnotations.getOrDefault(index, List.of()));
                        }
                        methodBuilder.with(RuntimeInvisibleParameterAnnotationsAttribute.of(annotations));
                    }
                    if (codeConsumers != null) {
                        methodBuilder.withCode(codeBuilder -> {
                            codeConsumers.forEach(codeConsumer -> codeConsumer.accept(codeBuilder));
                            if (!stackMapFrames.isEmpty()) {
                                codeBuilder.with(StackMapTableAttribute.of(stackMapFrames));
                            }
                        });
                    }
                }));
            }
        };
    }

    @Override
    public void visitEnd() {
        ClassFile classFile = (flags & ClassWriter.COMPUTE_FRAMES) == 0
                ? ClassFile.of(ClassFile.DeadCodeOption.KEEP_DEAD_CODE, ClassFile.StackMapsOption.DROP_STACK_MAPS)
                : ClassFile.of(ClassFile.DeadCodeOption.PATCH_DEAD_CODE, ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED);
        bytes = constantPoolBuilder == null
                ? classFile.build(thisClass, classBuilder -> classConsumers.forEach(classConsumer -> classConsumer.accept(classBuilder)))
                : classFile.build(constantPoolBuilder.classEntry(thisClass), constantPoolBuilder, classBuilder -> classConsumers.forEach(classConsumer -> classConsumer.accept(classBuilder)));
    }

    private ConstantDesc toConstantDesc(Object asm) {
        if (asm instanceof Integer value) {
            return value;
        } else if (asm instanceof Long value) {
            return value;
        } else if (asm instanceof Float value) {
            return value;
        } else if (asm instanceof Double value) {
            return value;
        } else if (asm instanceof String value) {
            return value;
        } else if (asm instanceof Type value) {
            return switch (value.getSort()) {
                case Type.OBJECT, Type.ARRAY -> ClassDesc.ofDescriptor(value.getDescriptor());
                case Type.METHOD -> MethodTypeDesc.ofDescriptor(value.getDescriptor());
                default -> throw new IllegalArgumentException("Unexpected type sort: " + value.getSort());
            };
        } else if (asm instanceof Handle value) {
            return MethodHandleDesc.of(
                    DirectMethodHandleDesc.Kind.valueOf(value.getTag(), value.isInterface()),
                    ClassDesc.ofInternalName(value.getOwner()),
                    value.getName(),
                    value.getDesc());
        } else if (asm instanceof ConstantDynamic value) {
            ConstantDesc[] constants = new ConstantDesc[value.getBootstrapMethodArgumentCount()];
            for (int index = 0; index < value.getBootstrapMethodArgumentCount(); index++) {
                constants[index] = toConstantDesc(value.getBootstrapMethodArgument(index));
            }
            return DynamicConstantDesc.ofNamed(
                    MethodHandleDesc.of(
                            DirectMethodHandleDesc.Kind.valueOf(value.getBootstrapMethod().getTag(), value.getBootstrapMethod().isInterface()),
                            ClassDesc.ofInternalName(value.getBootstrapMethod().getOwner()),
                            value.getBootstrapMethod().getName(),
                            value.getBootstrapMethod().getDesc()),
                    value.getName(),
                    ClassDesc.ofDescriptor(value.getDescriptor()),
                    constants);
        } else {
            throw new IllegalArgumentException("Unexpected constant: " + asm);
        }
    }

    public byte[] toByteArray() {
        if (bytes == null) {
            throw new IllegalStateException();
        }
        return bytes;
    }

    private static class WritingAnnotationVisitor extends AnnotationVisitor {

        private final BiConsumer<String, AnnotationValue> consumer;
        private final Runnable onEnd;

        private static AnnotationVisitor of(String descriptor, Consumer<Annotation> consumer) {
            List<AnnotationElement> elements = new ArrayList<>();
            return new WritingAnnotationVisitor(
                    (name, value) -> elements.add(AnnotationElement.of(name, value)),
                    () -> consumer.accept(Annotation.of(ClassDesc.ofDescriptor(descriptor), elements)));
        }

        private static AnnotationVisitor ofValue(Consumer<AnnotationValue> consumer) {
            return new WritingAnnotationVisitor(
                    (_, value) -> consumer.accept(value),
                    () -> { });
        }

        private static AnnotationVisitor ofTypeAnnotation(String descriptor, int typeRef, TypePath typePath, Consumer<TypeAnnotation> consumer) {
            return ofUnresolvedTypeAnnotation(descriptor, typeRef, typePath, function -> consumer.accept(function.apply(reference -> switch (reference.getSort()) {
                case TypeReference.CLASS_TYPE_PARAMETER -> TypeAnnotation.TargetInfo.ofClassTypeParameter(reference.getTypeParameterIndex());
                case TypeReference.METHOD_TYPE_PARAMETER -> TypeAnnotation.TargetInfo.ofMethodTypeParameter(reference.getTypeParameterIndex());
                case TypeReference.CLASS_EXTENDS -> TypeAnnotation.TargetInfo.ofClassExtends(reference.getSuperTypeIndex());
                case TypeReference.CLASS_TYPE_PARAMETER_BOUND -> TypeAnnotation.TargetInfo.ofClassTypeParameterBound(reference.getTypeParameterIndex(), reference.getTypeParameterBoundIndex());
                case TypeReference.METHOD_TYPE_PARAMETER_BOUND -> TypeAnnotation.TargetInfo.ofMethodTypeParameterBound(reference.getTypeParameterIndex(), reference.getTypeParameterBoundIndex());
                case TypeReference.FIELD -> TypeAnnotation.TargetInfo.ofField();
                case TypeReference.METHOD_RETURN -> TypeAnnotation.TargetInfo.ofMethodReturn();
                case TypeReference.METHOD_RECEIVER -> TypeAnnotation.TargetInfo.ofMethodReceiver();
                case TypeReference.METHOD_FORMAL_PARAMETER -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(reference.getFormalParameterIndex());
                case TypeReference.THROWS -> TypeAnnotation.TargetInfo.ofThrows(reference.getExceptionIndex());
                default -> throw new IllegalArgumentException("Unexpected reference sort: " + reference.getSort());
            })));
        }

        private static AnnotationVisitor ofExceptionTypeAnnotation(String descriptor, int typeRef, TypePath typePath, Consumer<Function<Integer, TypeAnnotation>> consumer) {
            return ofUnresolvedTypeAnnotation(descriptor, typeRef, typePath, function -> consumer.accept(index -> function.apply(reference -> switch (reference.getSort()) {
                case TypeReference.EXCEPTION_PARAMETER -> TypeAnnotation.TargetInfo.ofExceptionParameter(index);
                default -> throw new IllegalArgumentException("Unexpected reference sort: " + reference.getSort());
            })));
        }

        private static AnnotationVisitor ofLabeledTypeAnnotation(String descriptor, int typeRef, TypePath typePath, Consumer<Function<java.lang.classfile.Label, TypeAnnotation>> consumer) {
            return ofUnresolvedTypeAnnotation(descriptor, typeRef, typePath, function -> consumer.accept(label -> function.apply(reference -> switch (reference.getSort()) {
                case TypeReference.INSTANCEOF -> TypeAnnotation.TargetInfo.ofInstanceofExpr(label);
                case TypeReference.NEW -> TypeAnnotation.TargetInfo.ofNewExpr(label);
                case TypeReference.CONSTRUCTOR_REFERENCE -> TypeAnnotation.TargetInfo.ofConstructorReference(label);
                case TypeReference.METHOD_REFERENCE -> TypeAnnotation.TargetInfo.ofMethodReference(label);
                case TypeReference.CAST -> TypeAnnotation.TargetInfo.ofCastExpr(label, reference.getTypeArgumentIndex());
                case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofConstructorInvocationTypeArgument(label, reference.getTypeArgumentIndex());
                case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofMethodInvocationTypeArgument(label, reference.getTypeArgumentIndex());
                case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofConstructorReferenceTypeArgument(label, reference.getTypeArgumentIndex());
                case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT -> TypeAnnotation.TargetInfo.ofMethodReferenceTypeArgument(label, reference.getTypeArgumentIndex());
                default -> throw new IllegalArgumentException("Unexpected reference sort: " + reference.getSort());
            })));
        }

        private static AnnotationVisitor ofTargetedTypeAnnotation(String descriptor, int typeRef, TypePath typePath, Consumer<Function<List<TypeAnnotation.LocalVarTargetInfo>, TypeAnnotation>> consumer) {
            return ofUnresolvedTypeAnnotation(descriptor, typeRef, typePath, function -> consumer.accept(targets -> function.apply(reference -> switch (reference.getSort()) {
                case TypeReference.LOCAL_VARIABLE -> TypeAnnotation.TargetInfo.ofLocalVariable(targets);
                case TypeReference.RESOURCE_VARIABLE -> TypeAnnotation.TargetInfo.ofResourceVariable(targets);
                default -> throw new IllegalArgumentException("Unexpected reference sort: " + reference.getSort());
            })));
        }

        private static AnnotationVisitor ofUnresolvedTypeAnnotation(String descriptor, int typeRef, TypePath typePath, Consumer<Function<Function<TypeReference, TypeAnnotation.TargetInfo>, TypeAnnotation>> consumer) {
            List<AnnotationElement> elements = new ArrayList<>();
            List<TypeAnnotation.TypePathComponent> components;
            if (typePath == null) {
                components = List.of();
            } else {
                components = new ArrayList<>(typePath.getLength());
                for (int index = 0; index < typePath.getLength(); index++) {
                    components.add(switch (typePath.getStep(index)) {
                        case TypePath.ARRAY_ELEMENT -> TypeAnnotation.TypePathComponent.ARRAY;
                        case TypePath.INNER_TYPE -> TypeAnnotation.TypePathComponent.INNER_TYPE;
                        case TypePath.WILDCARD_BOUND -> TypeAnnotation.TypePathComponent.WILDCARD;
                        case TypePath.TYPE_ARGUMENT -> TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, typePath.getStepArgument(index));
                        default -> throw new IllegalArgumentException("Unkniwn type path type: " + typePath.getStep(index));
                    });
                }
            }
            TypeReference reference = new TypeReference(typeRef);
            return new WritingAnnotationVisitor(
                    (name, value) -> elements.add(AnnotationElement.of(name, value)),
                    () -> consumer.accept(targeting -> TypeAnnotation.of(
                            targeting.apply(reference),
                            components,
                            ClassDesc.ofDescriptor(descriptor),
                            elements)));
        }

        private WritingAnnotationVisitor(BiConsumer<String, AnnotationValue> consumer, Runnable onEnd) {
            super(Opcodes.ASM9);
            this.consumer = consumer;
            this.onEnd = onEnd;
        }

        @Override
        public void visit(String name, Object asm) {
            AnnotationValue annotation;
            if (asm instanceof Boolean value) {
                annotation = AnnotationValue.ofBoolean(value);
            } else if (asm instanceof Byte value) {
                annotation = AnnotationValue.ofByte(value);
            } else if (asm instanceof Short value) {
                annotation = AnnotationValue.ofShort(value);
            } else if (asm instanceof Character value) {
                annotation = AnnotationValue.ofChar(value);
            } else if (asm instanceof Integer value) {
                annotation = AnnotationValue.ofInt(value);
            } else if (asm instanceof Long value) {
                annotation = AnnotationValue.ofLong(value);
            } else if (asm instanceof Float value) {
                annotation = AnnotationValue.ofFloat(value);
            } else if (asm instanceof Double value) {
                annotation = AnnotationValue.ofDouble(value);
            } else if (asm instanceof String value) {
                annotation = AnnotationValue.ofString(value);
            } else if (asm instanceof boolean[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofBoolean(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof byte[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofByte(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof short[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofShort(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof char[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofChar(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof int[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofInt(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof long[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofLong(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof float[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofFloat(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof double[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofDouble(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof String[] array) {
                AnnotationValue[] values = new AnnotationValue[array.length];
                for (int index = 0; index < array.length; index++) {
                    values[index] = AnnotationValue.ofString(array[index]);
                }
                annotation = AnnotationValue.ofArray(values);
            } else if (asm instanceof Type type) {
                annotation = AnnotationValue.ofClass(ClassDesc.ofDescriptor(type.getDescriptor()));
            } else {
                throw new IllegalArgumentException("Unknown annotation value: " + asm);
            }
            consumer.accept(name, annotation);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            consumer.accept(name, AnnotationValue.ofEnum(
                    ClassDesc.ofDescriptor(descriptor),
                    value));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return WritingAnnotationVisitor.of(descriptor, annotation -> consumer.accept(
                    name,
                    AnnotationValue.ofAnnotation(annotation)));
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            List<AnnotationValue> values = new ArrayList<>();
            return new WritingAnnotationVisitor(
                    (_, value) -> values.add(value),
                    () -> consumer.accept(name, AnnotationValue.ofArray(values)));
        }

        @Override
        public void visitEnd() {
            onEnd.run();
        }
    }

    private static class RawAttribute extends CustomAttribute<RawAttribute> {

        private final byte[] bytes;

        private RawAttribute(String name, byte[] bytes) {
            super(new AttributeMapper<>() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public RawAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeAttribute(BufWriter buf, RawAttribute attr) {
                    buf.writeIndex(buf.constantPool().utf8Entry(name));
                    buf.writeInt(attr.bytes.length);
                    buf.writeBytes(attr.bytes);
                }

                @Override
                public AttributeStability stability() {
                    return AttributeStability.UNKNOWN;
                }
            });
            this.bytes = bytes;
        }
    }
}
