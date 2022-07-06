package codes.rafael.asmjdkbridge;

import jdk.classfile.Annotation;
import jdk.classfile.AnnotationElement;
import jdk.classfile.AnnotationValue;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

class JdkAnnotationExtractor extends AnnotationVisitor {

    private final String descriptor;
    private final Consumer<Annotation> handler;

    private final List<AnnotationElement> elements = new ArrayList<>();

    JdkAnnotationExtractor(String descriptor, Consumer<Annotation> handler) {
        super(Opcodes.ASM9);
        this.descriptor = descriptor;
        this.handler = handler;
    }

    @Override
    public void visit(String name, Object value) {
        elements.add(switch (value) {
            case Boolean b -> AnnotationElement.ofBoolean(name, b);
            case Byte b -> AnnotationElement.ofByte(name, b);
            case Short s -> AnnotationElement.ofShort(name, s);
            case Character c -> AnnotationElement.ofChar(name, c);
            case Integer i -> AnnotationElement.ofInt(name, i);
            case Long l -> AnnotationElement.ofLong(name, l);
            case Float f -> AnnotationElement.ofFloat(name, f);
            case Double d -> AnnotationElement.ofDouble(name, d);
            case String s -> AnnotationElement.ofString(name, s);
            case boolean[] b -> AnnotationElement.ofArray(name, IntStream.range(0, b.length)
                    .mapToObj(index -> AnnotationValue.ofBoolean(b[index]))
                    .toArray(AnnotationValue[]::new));
            case byte[] b -> AnnotationElement.ofArray(name, IntStream.range(0, b.length)
                    .mapToObj(index -> AnnotationValue.ofByte(b[index]))
                    .toArray(AnnotationValue[]::new));
            case short[] s -> AnnotationElement.ofArray(name, IntStream.range(0, s.length)
                    .mapToObj(index -> AnnotationValue.ofShort(s[index]))
                    .toArray(AnnotationValue[]::new));
            case char[] c -> AnnotationElement.ofArray(name, IntStream.range(0, c.length)
                    .mapToObj(index -> AnnotationValue.ofChar(c[index]))
                    .toArray(AnnotationValue[]::new));
            case int[] i -> AnnotationElement.ofArray(name, Arrays.stream(i)
                    .mapToObj(AnnotationValue::ofInt)
                    .toArray(AnnotationValue[]::new));
            case long[] l -> AnnotationElement.ofArray(name, Arrays.stream(l)
                    .mapToObj(AnnotationValue::ofLong)
                    .toArray(AnnotationValue[]::new));
            case float[] f -> AnnotationElement.ofArray(name, IntStream.range(0, f.length)
                    .mapToObj(index -> AnnotationValue.ofFloat(f[index]))
                    .toArray(AnnotationValue[]::new));
            case double[] d -> AnnotationElement.ofArray(name, Arrays.stream(d)
                    .mapToObj(AnnotationValue::ofDouble)
                    .toArray(AnnotationValue[]::new));
            case String[] s -> AnnotationElement.ofArray(name, Arrays.stream(s)
                    .map(AnnotationValue::ofString)
                    .toArray(AnnotationValue[]::new));
            default -> throw new UnsupportedOperationException("Unknown annotation constant: " + value);
        });
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        elements.add(AnnotationElement.of(name, AnnotationValue.ofEnum(ClassDesc.ofDescriptor(descriptor), value)));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        return new ArrayCollector(values -> elements.add(AnnotationElement.ofArray(name, values.toArray(AnnotationValue[]::new))));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        return new JdkAnnotationExtractor(descriptor, annotation -> elements.add(AnnotationElement.ofAnnotation(name, annotation)));
    }

    @Override
    public void visitEnd() {
        handler.accept(Annotation.of(ClassDesc.ofDescriptor(descriptor), elements));
    }

    private static class ArrayCollector extends AnnotationVisitor {

        private final Consumer<List<AnnotationValue>> handler;

        private final List<AnnotationValue> values = new ArrayList<>();

        private ArrayCollector(Consumer<List<AnnotationValue>> handler) {
            super(Opcodes.ASM9);
            this.handler = handler;
        }

        @Override
        public void visit(String name, Object value) {
            values.add(switch (value) {
                case Boolean b -> AnnotationValue.ofBoolean(b);
                case Byte b -> AnnotationValue.ofByte(b);
                case Short s -> AnnotationValue.ofShort(s);
                case Character c -> AnnotationValue.ofChar(c);
                case Integer i -> AnnotationValue.ofInt(i);
                case Long l -> AnnotationValue.ofLong(l);
                case Float f -> AnnotationValue.ofFloat(f);
                case Double d -> AnnotationValue.ofDouble(d);
                case String s -> AnnotationValue.ofString(s);
                default -> throw new UnsupportedOperationException("Unknown annotation array value: " + value);
            });
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            throw new UnsupportedOperationException("Cannot declare array within array");
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            values.add(AnnotationValue.ofEnum(ClassDesc.ofDescriptor(descriptor), value));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return new JdkAnnotationExtractor(descriptor, annotation -> values.add(AnnotationValue.ofAnnotation(annotation)));
        }

        @Override
        public void visitEnd() {
            handler.accept(values);
        }
    }
}
