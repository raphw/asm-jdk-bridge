package codes.rafael.asmjdkbridge.sample;

import java.util.function.Function;

public class Invokedynamic {

    Function<String, String> i() {
        return value -> value;
    }
}
