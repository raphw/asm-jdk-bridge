package codes.rafael.asmjdkbridge.sample;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Annotations.A(0)
@Annotations.B(0)
public class TypeAnnotations {

    @A(1)
    @B(1)
    Object o;

    @A(2)
    @B(2)
    Object a(@A(3) @B(3) Object o) {
        return o;
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
