package codes.rafael.asmjdkbridge;

import org.objectweb.asm.*;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class JdkClassWriter extends ClassVisitor {

    private final ClassFile classFile;

    private ClassDesc thisClass;
    private Consumer<ClassBuilder> classConsumer = _ -> { };
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
        thisClass = ClassDesc.ofInternalName(name);
        classConsumer = classConsumer.andThen(classBuilder -> {
            classBuilder.withVersion(version & 0xFF, (version >> 8) & 0xFF);
            classBuilder.withFlags(access & ~Opcodes.ACC_DEPRECATED);
            if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                classBuilder.with(DeprecatedAttribute.of());
            }
            if (signature != null) {
                classBuilder.with(SignatureAttribute.of(classBuilder.constantPool().utf8Entry(signature)));
            }
            classBuilder.withSuperclass(ClassDesc.ofInternalName(superName));
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
        classConsumer = classConsumer.andThen(classBuilder -> {
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
        classConsumer = classConsumer.andThen(classBuilder -> classBuilder.with(NestHostAttribute.of(ClassDesc.ofInternalName(nestHost))));
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
                classConsumer = classConsumer.andThen(classBuilder -> classBuilder.withField(name, ClassDesc.ofDescriptor(descriptor), fieldBuilder -> {
                    fieldBuilder.withFlags(access & ~Opcodes.ACC_DEPRECATED);
                    if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                        classBuilder.with(DeprecatedAttribute.of());
                    }
                    if (signature != null) {
                        fieldBuilder.with(SignatureAttribute.of(classBuilder.constantPool().utf8Entry(signature)));
                    }
                }));
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9) {

            private Consumer<CodeBuilder> codeConsumer;

            private final Map<Label, java.lang.classfile.Label> labels = new HashMap<>();

            private Label currentLocation;

            @Override
            public void visitCode() {
                codeConsumer  = _ -> { };
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
                this.codeConsumer = this.codeConsumer.andThen(codeConsumer);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.BIPUSH -> codeBuilder -> codeBuilder.bipush(operand);
                    case Opcodes.SIPUSH -> codeBuilder -> codeBuilder.sipush(operand);
                    case Opcodes.NEWARRAY -> codeBuilder -> codeBuilder.newarray(TypeKind.fromNewArrayCode(operand));
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                this.codeConsumer = this.codeConsumer.andThen(codeConsumer);
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
                    case Opcodes.RET -> throw new IllegalStateException("Unsupported opcode: " + opcode);
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                this.codeConsumer = this.codeConsumer.andThen(codeConsumer);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                this.codeConsumer = this.codeConsumer.andThen(codeBuilder -> codeBuilder.fieldInstruction(
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
                this.codeConsumer = this.codeConsumer.andThen(codeBuilder -> codeBuilder.invokeInstruction(
                        switch (opcode) {
                            case Opcodes.INVOKEVIRTUAL -> Opcode.INVOKEVIRTUAL;
                            case Opcodes.INVOKEINTERFACE -> Opcode.INVOKEINTERFACE;
                            case Opcodes.INVOKESPECIAL -> Opcode.INVOKESPECIAL;
                            case Opcodes.INVOKESTATIC -> Opcode.INVOKESTATIC;
                            default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                        },
                        ClassDesc.ofInternalName(owner),
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
                this.codeConsumer = this.codeConsumer.andThen(codeBuilder -> codeBuilder.invokeDynamicInstruction(DynamicCallSiteDesc.of(
                        MethodHandleDesc.of(
                                DirectMethodHandleDesc.Kind.valueOf(bootstrapMethodHandle.getTag()),
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
                    case Opcodes.JSR -> throw new IllegalStateException("Unsupported opcode: " + opcode);
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                this.codeConsumer = this.codeConsumer.andThen(codeConsumer);
            }

            @Override
            public void visitLdcInsn(Object value) {
                ConstantDesc constant = toConstantDesc(value);
                codeConsumer = codeConsumer.andThen(codeBuilder -> codeBuilder.ldc(constant));
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                codeConsumer = codeConsumer.andThen(codeBuilder -> codeBuilder.iinc(varIndex, increment));
            }

            @Override
            public void visitLabel(Label label) {
                currentLocation = label;
                codeConsumer = codeConsumer.andThen(codeBuilder -> codeBuilder.labelBinding(labels.computeIfAbsent(label, _ -> codeBuilder.newLabel())));
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                codeConsumer = codeConsumer.andThen(codeBuilder -> {
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
                codeConsumer = codeConsumer.andThen(codeBuilder -> {
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
                Consumer<CodeBuilder> codeConsumer = switch (opcode) {
                    case Opcodes.NEW -> codeBuilder -> codeBuilder.new_(ClassDesc.ofInternalName(type));
                    case Opcodes.ANEWARRAY -> codeBuilder -> codeBuilder.anewarray(ClassDesc.ofInternalName(type));
                    case Opcodes.CHECKCAST -> codeBuilder -> codeBuilder.checkcast(ClassDesc.ofInternalName(type));
                    case Opcodes.INSTANCEOF -> codeBuilder -> codeBuilder.instanceof_(ClassDesc.ofInternalName(type));
                    default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
                };
                this.codeConsumer = this.codeConsumer.andThen(codeConsumer);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                codeConsumer = codeConsumer.andThen(codeBuilder -> codeBuilder.multianewarray(ClassDesc.ofDescriptor(descriptor), numDimensions));
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                if (currentLocation != start) {
                    throw new IllegalStateException("JDK class writer requires to visit line numbers at current location");
                }
                codeConsumer = codeConsumer.andThen(codeBuilder -> codeBuilder.lineNumber(line));
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                codeConsumer = codeConsumer.andThen(codeBuilder -> {
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
                                Signature.parseFrom(descriptor),
                                labels.computeIfAbsent(start, _ -> codeBuilder.newLabel()),
                                labels.computeIfAbsent(end, _ -> codeBuilder.newLabel()));
                    }
                });
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                codeConsumer = codeConsumer.andThen(codeBuilder -> codeBuilder.with(ExceptionCatch.of(labels.computeIfAbsent(start, _ -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(end, _ -> codeBuilder.newLabel()),
                        labels.computeIfAbsent(handler, _ -> codeBuilder.newLabel()),
                        type == null ? Optional.empty() : null))); // TODO: exception type

            }

            @Override
            public void visitEnd() {
                classConsumer = classConsumer.andThen(classBuilder -> classBuilder.withMethod(name, MethodTypeDesc.ofDescriptor(descriptor), access & ~Opcodes.ACC_DEPRECATED, methodBuilder -> {
                    if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                        classBuilder.with(DeprecatedAttribute.of());
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
                    if (codeConsumer != null) {
                        methodBuilder.withCode(codeConsumer);
                    }
                }));
            }
        };
    }

    @Override
    public void visitEnd() {
        bytes = classFile.build(thisClass, classConsumer);
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
                    DirectMethodHandleDesc.Kind.valueOf(value.getTag()),
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
                            DirectMethodHandleDesc.Kind.valueOf(value.getBootstrapMethod().getTag()),
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

    public byte[] toBytes() {
        if (bytes == null) {
            throw new IllegalStateException();
        }
        return bytes;
    }
}
