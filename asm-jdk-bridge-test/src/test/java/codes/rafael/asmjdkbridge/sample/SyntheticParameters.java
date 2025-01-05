package codes.rafael.asmjdkbridge.sample;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public enum SyntheticParameters {

    INSTANCE(null);

    SyntheticParameters(@SampleAnnotation Void ignored) { }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface SampleAnnotation { }

    public class InnerClass {

        public InnerClass(@SampleAnnotation Void unused) { }
    }
}
