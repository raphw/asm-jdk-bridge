package codes.rafael.asmjdkbridge;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.*;

import java.lang.classfile.ClassReader;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.constant.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.IntFunction;

class AsmAttribute extends CustomAttribute<AsmAttribute> {

    private static final MethodHandle READ_ATTRIBUTE, WRITE_ATTRIBUTE, GET_BYTES;

    static {
        try {
            Method method = Attribute.class.getDeclaredMethod("read",
                    org.objectweb.asm.ClassReader.class,
                    int.class,
                    int.class,
                    char[].class,
                    int.class,
                    org.objectweb.asm.Label[].class);
            method.setAccessible(true);
            READ_ATTRIBUTE = MethodHandles.lookup().unreflect(method);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Method method = Attribute.class.getDeclaredMethod("write",
                    ClassWriter.class,
                    byte[].class,
                    int.class,
                    int.class,
                    int.class);
            method.setAccessible(true);
            WRITE_ATTRIBUTE = MethodHandles.lookup().unreflect(method);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Field field = ByteVector.class.getDeclaredField("data");
            field.setAccessible(true);
            GET_BYTES = MethodHandles.lookup().unreflectGetter(field);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    final Attribute attribute;

    AsmAttribute(Attribute attribute) {
        super(new AttributeMapper<>() {
            @Override
            public String name() {
                return attribute.type;
            }

            @Override
            public AsmAttribute readAttribute(AttributedElement attributedElement, ClassReader classReader, int offset) {
                try {
                    return new AsmAttribute((Attribute) READ_ATTRIBUTE.invoke(attribute,
                            new DelegatingClassReader(classReader),
                            offset,
                            -1,
                            null,
                            -1,
                            null,
                            null));
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }

            @Override
            public void writeAttribute(BufWriter bufWriter, AsmAttribute asmAttribute) {
                bufWriter.writeIndex(bufWriter.constantPool().utf8Entry(asmAttribute.attribute.type));
                byte[] bytes;
                int length;
                try {
                    ByteVector vector = (ByteVector) WRITE_ATTRIBUTE.invoke(asmAttribute.attribute,
                            new DelegatingClassWriter(bufWriter),
                            null,
                            -1,
                            -1,
                            -1);
                    bytes = (byte[]) GET_BYTES.invoke(vector);
                    length = vector.size();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
                bufWriter.writeBytes(bytes, 0, length);
            }

            @Override
            public AttributeStability stability() {
                return AttributeStability.UNKNOWN;
            }
        });
        this.attribute = attribute;
    }

    private static class DelegatingClassReader extends org.objectweb.asm.ClassReader {

        private static final byte[] FAKE = new byte[10];

        private final java.lang.classfile.ClassReader delegate;

        private DelegatingClassReader(java.lang.classfile.ClassReader delegate) {
            super(FAKE);
            this.delegate = delegate;
        }

        @Override
        public int getAccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getClassName() {
            return delegate.thisClassEntry().asInternalName();
        }

        @Override
        public String getSuperName() {
            return delegate.superclassEntry().map(ClassEntry::asInternalName).orElse(null);
        }

        @Override
        public String[] getInterfaces() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(ClassVisitor classVisitor, int parsingOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(ClassVisitor classVisitor, Attribute[] attributePrototypes, int parsingOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getItemCount() {
            return delegate.size();
        }

        @Override
        public int getItem(int constantPoolEntryIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMaxStringLength() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readByte(int offset) {
            return delegate.readU1(offset);
        }

        @Override
        public int readUnsignedShort(int offset) {
            return delegate.readU2(offset);
        }

        @Override
        public short readShort(int offset) {
            return (short) delegate.readS2(offset);
        }

        @Override
        public int readInt(int offset) {
            return delegate.readInt(offset);
        }

        @Override
        public long readLong(int offset) {
            return delegate.readLong(offset);
        }

        @Override
        public String readUTF8(int offset, char[] charBuffer) {
            return delegate.readEntry(delegate.readU2(offset), Utf8Entry.class).stringValue();
        }

        @Override
        public String readClass(int offset, char[] charBuffer) {
            return delegate.readEntry(delegate.readU2(offset), ClassEntry.class).name().stringValue();
        }

        @Override
        public String readModule(int offset, char[] charBuffer) {
            return delegate.readEntry(delegate.readU2(offset), ModuleEntry.class).name().stringValue();
        }

        @Override
        public String readPackage(int offset, char[] charBuffer) {
            return delegate.readEntry(delegate.readU2(offset), PackageEntry.class).name().stringValue();
        }

        @Override
        public Object readConst(int constantPoolEntryIndex, char[] charBuffer) {
            return switch (delegate.readEntry(constantPoolEntryIndex)) {
                case IntegerEntry entry -> entry.intValue();
                case LongEntry entry -> entry.longValue();
                case FloatEntry entry -> entry.floatValue();
                case DoubleEntry entry -> entry.doubleValue();
                case Utf8Entry entry -> entry.stringValue();
                case ClassEntry entry -> Type.getType("L" + entry.asInternalName() + ";");
                case MethodHandleEntry entry -> JdkClassReader.toAsmConstant(entry.asSymbol());
                case ConstantDynamicEntry entry -> JdkClassReader.toAsmConstant(entry.asSymbol());
                default -> throw new IllegalStateException();
            };
        }
    }

    private static class DelegatingClassWriter extends ClassWriter {

        private final BufWriter delegate;

        private DelegatingClassWriter(BufWriter delegate) {
            super(0);
            this.delegate = delegate;
        }

        @Override
        public boolean hasFlags(int flags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toByteArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int newConst(Object value) {
            return (switch (value) {
                case Boolean constant -> delegate.constantPool().intEntry(constant ? 1 : 0).index();
                case Byte constant -> delegate.constantPool().intEntry(constant).index();
                case Short constant -> delegate.constantPool().intEntry(constant).index();
                case Character constant -> delegate.constantPool().intEntry(constant).index();
                case Integer constant -> delegate.constantPool().intEntry(constant).index();
                case Long constant -> delegate.constantPool().longEntry(constant).index();
                case Float constant -> delegate.constantPool().floatEntry(constant).index();
                case Double constant -> delegate.constantPool().doubleEntry(constant).index();
                case String constant -> delegate.constantPool().stringEntry(constant).index();
                case Type constant -> (switch (constant.getSort()) {
                    case Type.OBJECT ->
                            delegate.constantPool().classEntry(ClassDesc.ofInternalName(constant.getInternalName()));
                    case Type.METHOD ->
                            delegate.constantPool().methodTypeEntry(MethodTypeDesc.ofDescriptor(constant.getDescriptor()));
                    default -> delegate.constantPool().classEntry(ClassDesc.ofDescriptor(constant.getDescriptor()));
                }).index();
                case Handle constant -> newHandle(constant.getTag(),
                        constant.getOwner(),
                        constant.getName(),
                        constant.getDesc(),
                        constant.isInterface());
                case ConstantDynamic constant -> newConstantDynamic(constant.getName(),
                        constant.getDescriptor(),
                        constant.getBootstrapMethod(),
                        constant.getBootstrapMethodArgumentCount(),
                        constant::getBootstrapMethodArgument);
                default -> throw new IllegalArgumentException();
            });
        }

        @Override
        public int newUTF8(String value) {
            return delegate.constantPool().utf8Entry(value).index();
        }

        @Override
        public int newClass(String value) {
            return delegate.constantPool().classEntry(ClassDesc.ofInternalName(value)).index();
        }

        @Override
        public int newMethodType(String methodDescriptor) {
            return delegate.constantPool().methodTypeEntry(MethodTypeDesc.ofDescriptor(methodDescriptor)).index();
        }

        @Override
        public int newModule(String moduleName) {
            return delegate.constantPool().moduleEntry(ModuleDesc.of(moduleName)).index();
        }

        @Override
        public int newPackage(String packageName) {
            return delegate.constantPool().packageEntry(PackageDesc.ofInternalName(packageName)).index();
        }

        @Override
        public int newHandle(int tag, String owner, String name, String descriptor, boolean isInterface) {
            return delegate.constantPool().methodHandleEntry(MethodHandleDesc.of(
                    DirectMethodHandleDesc.Kind.valueOf(tag, isInterface),
                    ClassDesc.ofInternalName(owner),
                    name,
                    descriptor)).index();
        }

        @Override
        public int newConstantDynamic(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            return newConstantDynamic(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments.length, index -> bootstrapMethodArguments[index]);
        }

        private int newConstantDynamic(String name, String descriptor, Handle bootstrapMethodHandle, int length, IntFunction<Object> resolver) {
            ConstantDesc[] constants = new ConstantDesc[length];
            for (int index = 0; index < length; index++) {
                constants[index] = JdkClassWriter.toConstantDesc(resolver.apply(index));
            }
            return delegate.constantPool().constantDynamicEntry(DynamicConstantDesc.ofNamed(
                    MethodHandleDesc.of(
                            DirectMethodHandleDesc.Kind.valueOf(bootstrapMethodHandle.getTag(), bootstrapMethodHandle.isInterface()),
                            ClassDesc.ofInternalName(bootstrapMethodHandle.getOwner()),
                            bootstrapMethodHandle.getName(),
                            bootstrapMethodHandle.getDesc()),
                    name,
                    ClassDesc.ofDescriptor(descriptor),
                    constants)).index();
        }

        @Override
        public int newInvokeDynamic(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            ConstantDesc[] constants = new ConstantDesc[bootstrapMethodArguments.length];
            for (int index = 0; index < bootstrapMethodArguments.length; index++) {
                constants[index] = JdkClassWriter.toConstantDesc(bootstrapMethodArguments[index]);
            }
            return delegate.constantPool().invokeDynamicEntry(DynamicCallSiteDesc.of(
                    MethodHandleDesc.of(
                            DirectMethodHandleDesc.Kind.valueOf(bootstrapMethodHandle.getTag(), bootstrapMethodHandle.isInterface()),
                            ClassDesc.ofInternalName(bootstrapMethodHandle.getOwner()),
                            bootstrapMethodHandle.getName(),
                            bootstrapMethodHandle.getDesc()),
                    name,
                    MethodTypeDesc.ofDescriptor(descriptor),
                    constants)).index();
        }

        @Override
        public int newField(String owner, String name, String descriptor) {
            return delegate.constantPool().fieldRefEntry(
                    ClassDesc.ofInternalName(owner),
                    name,
                    ClassDesc.ofDescriptor(descriptor)).index();
        }

        @Override
        public int newMethod(String owner, String name, String descriptor, boolean isInterface) {
            if (isInterface) {
                return delegate.constantPool().interfaceMethodRefEntry(
                        ClassDesc.ofInternalName(owner),
                        name,
                        MethodTypeDesc.ofDescriptor(descriptor)).index();
            } else {
                return delegate.constantPool().methodRefEntry(
                        ClassDesc.ofInternalName(owner),
                        name,
                        MethodTypeDesc.ofDescriptor(descriptor)).index();
            }
        }

        @Override
        public int newNameType(String name, String descriptor) {
            return delegate.constantPool().nameAndTypeEntry(
                    name,
                    ClassDesc.ofDescriptor(descriptor)).index();
        }
    }
}
