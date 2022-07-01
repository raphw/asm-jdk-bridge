package codes.rafael.asmjdkbridge.sample;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Annotations.A(0)
@Annotations.B(0)
public class Annotations {

    @A(1)
    @B(1)
    Object o;

    @A(2)
    @B(2)
    Object a(@A(3) @B(3) Object o) {
        return o;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface A {
        int value();
    }

    @interface B {
        int value();
    }
}
