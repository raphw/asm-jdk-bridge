package codes.rafael.asmjdkbridge;

import jdk.classfile.OpenBuilder;
import org.objectweb.asm.*;

class JdkFieldWriter extends FieldVisitor {

    private final OpenBuilder.OpenFieldBuilder openFieldBuilder;

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
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        // TODO: collect and trigger once guaranteed complete
        return new JdkAnnotationExtractor(descriptor, annotation -> {});
    }

    @Override
    public void visitEnd() {
        openFieldBuilder.close();
    }
}
