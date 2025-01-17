package codes.rafael.asmjdkbridge.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.AbstractList;
import java.util.List;

public abstract class TypeAnnotationsWithPath<
        @TypeAnnotationsWithoutPath.A(-2) @TypeAnnotationsWithoutPath.B(-2) T extends List<@TypeAnnotationsWithPath.A(-1) @TypeAnnotationsWithPath.B(-1) Object>>
        extends AbstractList<@TypeAnnotationsWithPath.A(0) @TypeAnnotationsWithPath.B(0) Object>
        implements List<@TypeAnnotationsWithPath.A(1) @TypeAnnotationsWithPath.B(1) Object> {

    List<@A(2) @B(2) Object> f1;
    @A(3) @B(3) Object[] f2;

    List<@A(4) @B(4) Object> m(List<@A(5) @B(5) Object> p) {
        return p;
    }

    @A(5) @B(5) Object[] a(@A(6) @B(6) Object[] p) {
        return p;
    }

    TypeAnnotationsWithPath<@A(7) @B(7) T>.Inner a(TypeAnnotationsWithPath<@A(8) @B(8) T>.Inner p) {
        return p;
    }

    class Inner { }

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
