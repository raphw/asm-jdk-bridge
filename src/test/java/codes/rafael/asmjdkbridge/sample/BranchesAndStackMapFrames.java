package codes.rafael.asmjdkbridge.sample;

public class BranchesAndStackMapFrames {

    int s(int v) {
        if (v == 0) {
            return 1;
        } else {
            return 3;
        }
    }

    int a(int v) {
        int x = 0;
        if (v == 0) {
            return 1;
        } else {
            return 3;
        }
    }

    int f(int v) {
        int x1 = 0, x2 = 1, x3 = 3, x4 = 4;
        if (v == 0) {
            return 1;
        } else {
            return 3;
        }
    }

    int c(int v1, int v2) {
        if (v1 == 0) {
            int x1 = 0, x2 = 1, x3 = 3;
            if (v2 == 1) {
                return 1;
            } else {
                return 2;
            }
        }
        return 3;
    }

    int aac(int v1, int v2) {
        int x1 = 0;
        if (v1 == 0) {
            int x2 = 0;
            if (v2 == 0) {
                return 1;
            } else {
                return 2;
            }
        } else {
            return 3;
        }
    }
}
