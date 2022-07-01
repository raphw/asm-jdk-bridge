package codes.rafael.asmjdkbridge.sample;

public class ArrayInstructions {

    Object[] aa(Object[] x) {
        x[0] = x[1];
        return x;
    }

    Object[] aa() {
        Object[] x = new Object[100];
        x[0] = x[1];
        return x;
    }

    Object[] aaa() {
        Object[][] x = new Object[100][100];
        x[0][0] = x[1][1];
        return x;
    }
}
