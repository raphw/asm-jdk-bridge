package codes.rafael.asmjdkbridge.test;

public class FieldConstructorAndMethod {

    String v;

    String f() {
        return v;
    }

    FieldConstructorAndMethod c() {
        return new FieldConstructorAndMethod();
    }

    String m() {
        return f();
    }
}
