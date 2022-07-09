package codes.rafael.asmjdkbridge;

import jdk.classfile.Annotation;
import jdk.classfile.Signature;
import jdk.classfile.TypeAnnotation;
import jdk.classfile.attribute.*;
import org.objectweb.asm.*;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class JdkRecordComponentExtractor extends RecordComponentVisitor {

    private final String name, descriptor, signature;
    private final Consumer<RecordComponentInfo> handler;

    private final List<Annotation> visibleAnnotations = new ArrayList<>(), invisibleAnnotations = new ArrayList<>();
    private final List<TypeAnnotation> visibleTypeAnnotations = new ArrayList<>(), invisibleTypeAnnotations = new ArrayList<>();

    JdkRecordComponentExtractor(String name, String descriptor, String signature, Consumer<RecordComponentInfo> handler) {
        super(Opcodes.ASM9);
        this.name = name;
        this.descriptor = descriptor;
        this.handler = handler;
        this.signature = signature;
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

    @Override
    public void visitEnd() {
        List<jdk.classfile.Attribute<?>> attributes = new ArrayList<>();
        if (signature != null) {
            attributes.add(SignatureAttribute.of(Signature.parseFrom(signature)));
        }
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
        handler.accept(RecordComponentInfo.of(name, ClassDesc.ofDescriptor(descriptor), attributes));
    }
}
