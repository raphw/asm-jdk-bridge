package codes.rafael.asmjdkbridge.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Annotations.A(0)
@Annotations.B(0)
public class TypeAnnotationsWithoutPath {

    @A(1)
    @B(1)
    Object f;

    @A(2)
    @B(2)
    Object m(@A(3) @B(3) Object p) throws @A(4) @B(4) RuntimeException {
        return p;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface A {
        int value();
    }

    @Target(ElementType.TYPE_USE)
    @interface B {
        int value();
    }
}
