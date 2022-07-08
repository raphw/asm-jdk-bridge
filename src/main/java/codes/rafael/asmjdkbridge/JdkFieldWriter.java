package codes.rafael.asmjdkbridge;

import jdk.classfile.Annotation;
import jdk.classfile.OpenBuilder;
import jdk.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;

class JdkFieldWriter extends FieldVisitor {

    private final OpenBuilder.OpenFieldBuilder openFieldBuilder;

    private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();

    JdkFieldWriter(OpenBuilder.OpenFieldBuilder openFieldBuilder) {
        super(Opcodes.ASM9);
        this.openFieldBuilder = openFieldBuilder;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // TODO: not really considered in ASM today
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
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }

    private void completeAttributes() {
        if (!visibleAnnotations.isEmpty()) {
            openFieldBuilder.accept(fieldBuilder -> RuntimeVisibleAnnotationsAttribute.of(visibleAnnotations));
            visibleAnnotations.clear();
        }
        if (!invisibleAnnotations.isEmpty()) {
            openFieldBuilder.accept(fieldBuilder -> RuntimeInvisibleAnnotationsAttribute.of(invisibleAnnotations));
            invisibleAnnotations.clear();
        }
    }

    @Override
    public void visitEnd() {
        completeAttributes();
        openFieldBuilder.close();
    }
}
