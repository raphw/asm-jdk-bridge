package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import jdk.classfile.Classfile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class JdkClassReaderTest {

    @SuppressWarnings("deprecation")
    @Parameterized.Parameters(name = "{0} (expandFrames={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Trivial.class, false, false, true},
                {LoadStoreAndReturn.class, false, false, true},
                {FieldConstructorAndMethod.class, false, false, true},
                {Operations.class, false, false, true},
                {DeprecatedClass.class, false, false, true},
                {SyntheticConstructor.Inner.class, false, false, true},
                {ArrayInstructions.class, false, false, true},
                {Invokedynamic.class, false, false, true},
                {BranchesAndStackMapFrames.class, false, false, true},
                {BranchesAndStackMapFrames.class, true, false, true},
                {Switches.class, false, false, true},
                {TryThrowCatch.class, false, false, false}, // TODO: glitches because of auto-compute of stack map frames
                {Annotations.class, false, false, true},
                {TypeAnnotationsWithoutPath.class, false, false, true},
                {TypeAnnotationsWithPath.class, false, false, true},
                {TypeAnnotationsInCode.class, false, true, false}, // TODO: type annotation label must be placed before, not after instruction
                {RecordComponents.class, false, false, true},
                {NoRecordComponents.class, false, false, true},
                {JsrRet.make(), false, false, true}, // TODO: How to handle old class files (e.g. JDBC)?
                {CustomAttribute.make(), false, false, false}, // TODO: How to handle unknown attributes on write in ASM?
                {FrameWithMissingType.make(), false, false, false} // TODO: Frame generation yields invalid frame*/
        });
    }

    private final Class<?> target;

    private final boolean expandFrames;

    private final boolean clearUnusedLabels;

    private final boolean consistentWrite;

    public JdkClassReaderTest(Class<?> target, boolean expandFrames, boolean clearUnusedLabels, boolean consistentWrite) {
        this.target = target;
        this.expandFrames = expandFrames;
        this.clearUnusedLabels = clearUnusedLabels;
        this.consistentWrite = consistentWrite;
    }

    @Test
    public void equal_reader_output() throws IOException {
        assumeFalse(target.getName().equals(JsrRet.class.getPackageName() + ".JmpRetGen"));
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        nonValidatingClassReader(classFile).accept(toVisitor(asm, clearUnusedLabels), expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        new JdkClassReader(Classfile.parse(classFile)).accept(toVisitor(jdk, clearUnusedLabels), expandFrames);
        assertEquals(asm.toString(), jdk.toString());
        System.out.println(asm.toString());
    }

    //@Test
    public void equal_writer_output() throws IOException {
        assumeFalse(target.getName().equals(JsrRet.class.getPackageName() + ".JmpRetGen"));
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        ClassReader classReader = nonValidatingClassReader(classFile);
        ClassWriter asmWriter = new ClassWriter(0);
        JdkClassWriter jdkWriter = new JdkClassWriter();
        classReader.accept(asmWriter, expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        classReader.accept(jdkWriter, expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        nonValidatingClassReader(asmWriter.toByteArray()).accept(toVisitor(asm, false), expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        nonValidatingClassReader(jdkWriter.toByteArray()).accept(toVisitor(jdk, false), expandFrames ? ClassReader.EXPAND_FRAMES : 0);
        assumeTrue(consistentWrite);
        assertEquals(asm.toString(), jdk.toString());
    }

    private static ClassReader nonValidatingClassReader(byte[] classFile) {
        try {
            Constructor<ClassReader> constructor = ClassReader.class.getDeclaredConstructor(byte[].class, int.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(classFile, 0, false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static ClassVisitor toVisitor(StringWriter writer, boolean clearUnusedLabels) {
        return new ClassVisitor(Opcodes.ASM9, new TraceClassVisitor(new PrintWriter(writer))) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!clearUnusedLabels) {
                    return methodVisitor;
                }
                return new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions) {
                    @Override
                    public void visitEnd() {
                        super.visitEnd();
                        Map<Label, LabelNode> defined = new HashMap<>();
                        Set<Label> used = new HashSet<>();
                        for (AbstractInsnNode node : instructions) {
                            switch (node) {
                                case LabelNode labelNode -> defined.put(labelNode.getLabel(), labelNode);
                                case TableSwitchInsnNode tableSwitchInsnNode -> {
                                    used.add(tableSwitchInsnNode.dflt.getLabel());
                                    tableSwitchInsnNode.labels.stream().map(LabelNode::getLabel).forEach(used::add);
                                }
                                case LineNumberNode lineNumberNode -> used.add(lineNumberNode.start.getLabel());
                                case FrameNode frameNode -> {
                                    if (frameNode.local != null) {
                                        frameNode.local.stream()
                                                .filter(o -> o instanceof Label)
                                                .map(Label.class::cast)
                                                .forEach(used::add);
                                    }
                                    if (frameNode.stack != null) {
                                        frameNode.stack.stream()
                                                .filter(o -> o instanceof Label)
                                                .map(Label.class::cast)
                                                .forEach(used::add);
                                    }
                                }
                                case JumpInsnNode jumpInsnNode -> used.add(jumpInsnNode.label.getLabel());
                                case LookupSwitchInsnNode lookupSwitchInsnNode -> {
                                    used.add(lookupSwitchInsnNode.dflt.getLabel());
                                    lookupSwitchInsnNode.labels.stream().map(LabelNode::getLabel).forEach(used::add);
                                }
                                default -> { }
                            }
                        }
                        defined.entrySet().stream()
                                .filter(entry -> !used.contains(entry.getKey()))
                                .map(Map.Entry::getValue)
                                .forEach(instructions::remove);
                        accept(methodVisitor);
                    }
                };
            }
        };
    }
}
