package codes.rafael.asmjdkbridge.sample;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class TypeAnnotationsInCode {

    void i() {
        Object o = new @A(0) @B(0) ArrayList<>();
    }

    void t() {
        Object o1 = new ArrayList<@A(0) @B(0) Object>(); // TODO: should be CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT
        Object o2 = List.<@A(1) @B(1) Object>of();
    }

    void c() {
        try {
            throw new RuntimeException();
        } catch (@A(1) @B(1) RuntimeException e) {
        }
    }

    void ca(Object o) {
        String v = (@A(1) @B(1) String) o;
    }

    void in(Object o) {
        boolean b = o instanceof @A(1) @B(1) String;
    }

    void cr() {
        Supplier<Object> s1 = @A(1) @B(1) Object::new;
        Supplier<List<Object>> s2 = ArrayList<@A(2) @B(2) Object>::new; // TODO: should be CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT
    }

    <T> void mr() {
        Sample s = null;
        Supplier<Object> s1 = s::p; // TODO: Should allow for METHOD_REFERENCE
        Supplier<T> s2 = s::<@A(2) @B(2) T>g;
    }

    void v() {
        @A(2) @B(2) Object v = new Object();
    }

    void r() throws Exception {
        try (@A(3) @B(3) AutoCloseable a = () -> {}) {
            new Object();
        }
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

    private interface Sample {

        Object p();
        <T> T g();
    }
}
