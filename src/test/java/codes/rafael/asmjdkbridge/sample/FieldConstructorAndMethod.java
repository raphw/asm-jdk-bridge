package codes.rafael.asmjdkbridge.sample;

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
