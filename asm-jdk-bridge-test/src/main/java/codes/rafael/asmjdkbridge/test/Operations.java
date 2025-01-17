package codes.rafael.asmjdkbridge.test;

public class Operations {

    void o(int a) {
        int i = 1 + a;
        int ii = 100 + a;
        int i2 = 1000 + a;
        int i3 = 100000 + a;
        long l = 1L + a;
        float f = 1f + a;
        double d = 1d + a;
    }

    String c(Object o) {
        String s = (String) o;
        return s;
    }
}
