package codes.rafael.asmjdkbridge.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class FrameWithMissingType {

    public static Class<?> make() {
        try {
            return new ClassLoader(null) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.startsWith(FrameWithMissingType.class.getName()) && !name.equals(Missing.class.getName())) {
                        byte[] bytes;
                        try (InputStream inputStream = FrameWithMissingType.class
                                .getClassLoader()
                                .getResourceAsStream(name.replace('.', '/') + ".class")) {
                            bytes = inputStream.readAllBytes();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        return defineClass(name, bytes, 0, bytes.length);
                    } else {
                        return super.findClass(name);
                    }
                }

                @Override
                public InputStream getResourceAsStream(String name) {
                    if (name.endsWith(".class")
                            && name.startsWith(FrameWithMissingType.class.getName().replace('.', '/'))
                            && !name.equals(Missing.class.getName().replace('.', '/') + ".class")) {
                        return FrameWithMissingType.class
                                .getClassLoader()
                                .getResourceAsStream(name);
                    } else {
                        return super.getResourceAsStream(name);
                    }
                }
            }.loadClass(FrameWithMissingType.class.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public void m(boolean toggle) {
        Iface i;
        if (toggle) {
            i = new Present();
        } else {
            i = new Missing();
        }
        i.m();
    }

    public interface Iface {
        void m();
    }

    public static class Present implements Iface {
        @Override
        public void m() { }
    }

    public static class Missing implements Iface {
        @Override
        public void m() { }
    }
}
