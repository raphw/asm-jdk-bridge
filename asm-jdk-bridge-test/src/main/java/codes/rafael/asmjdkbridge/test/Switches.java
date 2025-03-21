package codes.rafael.asmjdkbridge.test;

public class Switches {

    int t(int i) {
        return switch (i) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> 4;
        };
    }

    int t2(int i) {
        return switch (i) {
            case 0 -> 1;
            case 1 -> 2;
            case 3 -> 4;
            default -> 5;
        };
    }

    int s(int i) {
        return switch (i) {
            case 0 -> 1;
            case 10 -> 2;
            case 20 -> 2;
            default -> 3;
        };
    }
}
