# asm-jdk-bridge

A first approach to trial the JDK API for generation and reading of class files by adapting the ASM API. This should serve as a first prof of concept by plugging the reader/writer into existing ASM-based code without much change of code. This also serves as an adapter concept for Byte Buddy where ASM is used vastly.

In order to use the adapter, simply replace an instance of ASM's `ClassReader` or `ClassWriter` with `JdkClassReader` or `JdkClassWriter`. The latter use the Class File API internally, but expose equal APIs to ASM. If the availability of the Class File API is unclear, `ProbingClassReader` and `ProbingClassWriter` can be used, which will discover the underlying JVM and delegate to ASM or the Class File API, depending on capability.
