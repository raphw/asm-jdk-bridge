package codes.rafael.asmjdkbridge.sample;

public class FieldAndMethod {

    String v;

    String f() {
        return v;
    }

    String m() {
        return f();
    }
}
