package codes.rafael.asmjdkbridge.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;

public class TypeAnnotationsInCode {

    void i() {
        Object o = new @A(0) @B(0) Object();
    }

    void t() {
        Object o = new ArrayList<@A(0) @B(0) Object>();
    }

    void c() {
        try {
            throw new RuntimeException();
        } catch (@A(1) @B(1) RuntimeException e) {
        }
    }

    void v() {
        @A(2) @B(2) Object v = new Object();
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
