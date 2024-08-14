package codes.rafael.asmjdkbridge.sample;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public enum SyntheticParameters {

    INSTANCE(null);

    SyntheticParameters(@SampleAnnotation Void ignored) {
        /* empty */
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface SampleAnnotation {
        /* empty */
    }

    public class InnerClass {

        public InnerClass(@SampleAnnotation Void unused) {
            /* empty */
        }
    }
}
