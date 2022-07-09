package codes.rafael.asmjdkbridge;

import jdk.classfile.Annotation;
import jdk.classfile.AnnotationElement;
import jdk.classfile.AnnotationValue;
import jdk.classfile.TypeAnnotation;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.IntStream;

class JdkAnnotationExtractor<T> extends AnnotationVisitor {

    private final Consumer<List<T>> handler;

    private final List<T> elements = new ArrayList<>();

    private final BiFunction<String, AnnotationValue, T> resolver;

    private JdkAnnotationExtractor(Consumer<List<T>> handler, BiFunction<String, AnnotationValue, T> resolver) {
        super(Opcodes.ASM9);
        this.handler = handler;
        this.resolver = resolver;
    }

    static AnnotationVisitor ofAnnotationElements(Consumer<List<AnnotationElement>> handler) {
        return new JdkAnnotationExtractor<>(handler, AnnotationElement::of);
    }

    static AnnotationVisitor ofAnnotationValues(Consumer<List<AnnotationValue>> handler) {
        return new JdkAnnotationExtractor<>(handler, (name, value) -> value);
    }

    private static List<TypeAnnotation.TypePathComponent> toTypePathComponents(TypePath typePath) {
        if (typePath == null) {
            return List.of();
        }
        return IntStream.range(0, typePath.getLength()).mapToObj(index -> switch (typePath.getStep(index)) {
            case TypePath.ARRAY_ELEMENT -> TypeAnnotation.TypePathComponent.ARRAY;
            case TypePath.INNER_TYPE -> TypeAnnotation.TypePathComponent.INNER_TYPE;
            case TypePath.WILDCARD_BOUND -> TypeAnnotation.TypePathComponent.WILDCARD;
            case TypePath.TYPE_ARGUMENT -> TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT.tag(), typePath.getStepArgument(index));
            default -> throw new UnsupportedOperationException("Unknown type path type: " + typePath.getStep(index));
        }).toList();
    }

    static AnnotationVisitor ofAnnotation(String descriptor, Consumer<Annotation> handler) {
        return ofAnnotationElements(elements -> handler.accept(Annotation.of(ClassDesc.ofDescriptor(descriptor), elements)));
    }

    static AnnotationVisitor ofTypeAnnotation(TypePath typePath, BiConsumer<List<TypeAnnotation.TypePathComponent>, List<AnnotationElement>> handler) {
        return ofAnnotationElements(elements -> handler.accept(toTypePathComponents(typePath), elements));
    }

    static AnnotationVisitor ofTypeAnnotation(int typeRef, TypePath typePath, String descriptor, Consumer<TypeAnnotation> handler) {
        return ofTypeAnnotation(typePath, (components, elements) -> {
            TypeReference typeReference = new TypeReference(typeRef);
            handler.accept(TypeAnnotation.of(
                    switch (typeReference.getSort()) {
                        case TypeReference.CLASS_TYPE_PARAMETER -> TypeAnnotation.TargetInfo.ofClassTypeParameter(typeReference.getTypeParameterIndex());
                        case TypeReference.METHOD_TYPE_PARAMETER -> TypeAnnotation.TargetInfo.ofMethodTypeParameter(typeReference.getTypeParameterIndex());
                        case TypeReference.CLASS_EXTENDS -> TypeAnnotation.TargetInfo.ofClassExtends(typeReference.getExceptionIndex());
                        case TypeReference.CLASS_TYPE_PARAMETER_BOUND -> TypeAnnotation.TargetInfo.ofClassTypeParameterBound(typeReference.getTypeParameterIndex(), typeReference.getTypeParameterBoundIndex());
                        case TypeReference.METHOD_TYPE_PARAMETER_BOUND -> TypeAnnotation.TargetInfo.ofMethodTypeParameterBound(typeReference.getTypeParameterIndex(), typeReference.getTypeParameterBoundIndex());
                        case TypeReference.FIELD -> TypeAnnotation.TargetInfo.ofField();
                        case TypeReference.METHOD_RETURN -> TypeAnnotation.TargetInfo.ofMethodReturn();
                        case TypeReference.METHOD_RECEIVER -> TypeAnnotation.TargetInfo.ofMethodReceiver();
                        case TypeReference.METHOD_FORMAL_PARAMETER -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(typeReference.getFormalParameterIndex());
                        case TypeReference.THROWS -> TypeAnnotation.TargetInfo.ofThrows(typeReference.getTryCatchBlockIndex());
                        case TypeReference.EXCEPTION_PARAMETER -> TypeAnnotation.TargetInfo.ofExceptionParameter(typeReference.getExceptionIndex());
                        default -> throw new UnsupportedOperationException("Unknown type reference: " + typeReference.getSort());
                    },
                    components,
                    ClassDesc.ofDescriptor(descriptor),
                    elements
            ));
        });
    }

    @Override
    public void visit(String name, Object value) {
        elements.add(resolver.apply(name, switch (value) {
            case Boolean b -> AnnotationValue.ofBoolean(b);
            case Byte b -> AnnotationValue.ofByte(b);
            case Short s -> AnnotationValue.ofShort(s);
            case Character c -> AnnotationValue.ofChar(c);
            case Integer i -> AnnotationValue.ofInt(i);
            case Long l -> AnnotationValue.ofLong(l);
            case Float f -> AnnotationValue.ofFloat(f);
            case Double d -> AnnotationValue.ofDouble(d);
            case String s -> AnnotationValue.ofString(s);
            case boolean[] b -> AnnotationValue.ofArray(IntStream.range(0, b.length)
                    .mapToObj(index -> AnnotationValue.ofBoolean(b[index]))
                    .toArray(AnnotationValue[]::new));
            case byte[] b -> AnnotationValue.ofArray(IntStream.range(0, b.length)
                    .mapToObj(index -> AnnotationValue.ofByte(b[index]))
                    .toArray(AnnotationValue[]::new));
            case short[] s -> AnnotationValue.ofArray(IntStream.range(0, s.length)
                    .mapToObj(index -> AnnotationValue.ofShort(s[index]))
                    .toArray(AnnotationValue[]::new));
            case char[] c -> AnnotationValue.ofArray(IntStream.range(0, c.length)
                    .mapToObj(index -> AnnotationValue.ofChar(c[index]))
                    .toArray(AnnotationValue[]::new));
            case int[] i -> AnnotationValue.ofArray(Arrays.stream(i)
                    .mapToObj(AnnotationValue::ofInt)
                    .toArray(AnnotationValue[]::new));
            case long[] l -> AnnotationValue.ofArray(Arrays.stream(l)
                    .mapToObj(AnnotationValue::ofLong)
                    .toArray(AnnotationValue[]::new));
            case float[] f -> AnnotationValue.ofArray(IntStream.range(0, f.length)
                    .mapToObj(index -> AnnotationValue.ofFloat(f[index]))
                    .toArray(AnnotationValue[]::new));
            case double[] d -> AnnotationValue.ofArray(Arrays.stream(d)
                    .mapToObj(AnnotationValue::ofDouble)
                    .toArray(AnnotationValue[]::new));
            case String[] s -> AnnotationValue.ofArray(Arrays.stream(s)
                    .map(AnnotationValue::ofString)
                    .toArray(AnnotationValue[]::new));
            default -> throw new UnsupportedOperationException("Unknown annotation constant: " + value);
        }));
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        elements.add(resolver.apply(name, AnnotationValue.ofEnum(ClassDesc.ofDescriptor(descriptor), value)));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        return ofAnnotationValues(values -> elements.add(resolver.apply(name, AnnotationValue.ofArray(values.toArray(AnnotationValue[]::new)))));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        return ofAnnotationElements(elements -> this.elements.add(resolver.apply(name, AnnotationValue.ofAnnotation(Annotation.of(ClassDesc.ofDescriptor(descriptor), elements)))));
    }

    @Override
    public void visitEnd() {
        handler.accept(elements);
    }
}
