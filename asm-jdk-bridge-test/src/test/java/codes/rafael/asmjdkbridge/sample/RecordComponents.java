package codes.rafael.asmjdkbridge.sample;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public record RecordComponents(
        @A @B Object v1,
        Object v2
) {

    @Retention(RetentionPolicy.RUNTIME)
    @interface A { }

    @Retention(RetentionPolicy.RUNTIME)
    @interface B { }
}
