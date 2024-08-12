package codes.rafael.asmjdkbridge.sample;

public class TryThrowCatch {

    int t() {
        try {
            throw new RuntimeException();
        } catch (Exception e) {
            return 2;
        } finally {
            x();
        }
    }

    int t2() {
        Object o = new Object();
        try {
            throw new RuntimeException();
        } catch (Exception e) {
            return 2;
        } finally {
            x();
        }
    }

    int t3() {
        Object o = new Object();
        try {
            throw new RuntimeException();
        } finally {
            x();
        }
    }

    void x() { }
}
