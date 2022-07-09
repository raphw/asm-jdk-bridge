package codes.rafael.asmjdkbridge;

import jdk.classfile.Annotation;
import jdk.classfile.OpenBuilder;
import jdk.classfile.TypeAnnotation;
import jdk.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;

class JdkFieldWriter extends FieldVisitor {

    private final OpenBuilder.OpenFieldBuilder openFieldBuilder;

    private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
    private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();

    JdkFieldWriter(OpenBuilder.OpenFieldBuilder openFieldBuilder) {
        super(Opcodes.ASM9);
        this.openFieldBuilder = openFieldBuilder;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return JdkAnnotationExtractor.ofAnnotation(descriptor, annotation -> {
            if (visible) {
                visibleAnnotations.add(annotation);
            } else {
                invisibleAnnotations.add(annotation);
            }
        });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return JdkAnnotationExtractor.ofTypeAnnotation(typeRef, typePath, descriptor, typeAnnotation -> {
            if (visible) {
                visibleTypeAnnotations.add(typeAnnotation);
            } else {
                invisibleTypeAnnotations.add(typeAnnotation);
            }
        });
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // TODO: not really considered in ASM as things are
    }

    private void completeAttributes() {
        if (!visibleAnnotations.isEmpty()) {
            openFieldBuilder.accept(fieldBuilder -> fieldBuilder.with(RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations)));
            visibleAnnotations.clear();
        }
        if (!invisibleAnnotations.isEmpty()) {
            openFieldBuilder.accept(fieldBuilder -> fieldBuilder.with(RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations)));
            invisibleAnnotations.clear();
        }
        if (!visibleTypeAnnotations.isEmpty()) {
            openFieldBuilder.accept(fieldBuilder -> fieldBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(visibleTypeAnnotations)));
            visibleTypeAnnotations.clear();
        }
        if (!invisibleTypeAnnotations.isEmpty()) {
            openFieldBuilder.accept(fieldBuilder -> fieldBuilder.with(RuntimeInvisibleTypeAnnotationsAttribute.of(invisibleTypeAnnotations)));
            invisibleTypeAnnotations.clear();
        }
    }

    @Override
    public void visitEnd() {
        completeAttributes();
        openFieldBuilder.close();
    }
}
