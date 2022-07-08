package codes.rafael.asmjdkbridge;

import jdk.classfile.Signature;
import jdk.classfile.attribute.RecordComponentInfo;
import jdk.classfile.attribute.SignatureAttribute;
import org.objectweb.asm.*;

import java.lang.constant.ClassDesc;
import java.util.Collections;
import java.util.function.Consumer;

class JdkRecordComponentExtractor extends RecordComponentVisitor {

    private final String name, descriptor, signature;
    private final Consumer<RecordComponentInfo> handler;

    JdkRecordComponentExtractor(String name, String descriptor, String signature, Consumer<RecordComponentInfo> handler) {
        super(Opcodes.ASM9);
        this.name = name;
        this.descriptor = descriptor;
        this.handler = handler;
        this.signature = signature;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // TODO: collect
        return new JdkAnnotationExtractor(descriptor, annotation -> {
        });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        // TODO: collect
        return new JdkAnnotationExtractor(descriptor, annotation -> {
        });
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // TODO: Not really yet considered in ASM
    }

    @Override
    public void visitEnd() {
        // TODO: Annotations;
        handler.accept(RecordComponentInfo.of(name, ClassDesc.ofDescriptor(descriptor), signature == null
                ? Collections.emptyList()
                : Collections.singletonList(SignatureAttribute.of(Signature.parseFrom(signature)))));
    }
}
