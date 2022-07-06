package codes.rafael.asmjdkbridge;

import jdk.classfile.OpenBuilder;
import jdk.classfile.attribute.*;
import jdk.classfile.jdktypes.ModuleDesc;
import jdk.classfile.jdktypes.PackageDesc;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class JdkModuleWriter extends ModuleVisitor {

    private final OpenBuilder.OpenClassBuilder openClassBuilder;
    private final ModuleAttribute.OpenModuleAttributeBuilder openModuleAttributeBuilder;

    private List<PackageDesc> packageDescs;

    JdkModuleWriter(OpenBuilder.OpenClassBuilder openClassBuilder, ModuleAttribute.OpenModuleAttributeBuilder openModuleAttributeBuilder) {
        super(Opcodes.ASM9);
        this.openClassBuilder = openClassBuilder;
        this.openModuleAttributeBuilder = openModuleAttributeBuilder;
    }

    @Override
    public void visitMainClass(String mainClass) {
        openClassBuilder.accept(classBuilder -> classBuilder.with(ModuleMainClassAttribute.of(ClassDesc.of(mainClass))));
    }

    @Override
    public void visitPackage(String name) {
        if (packageDescs == null) {
            packageDescs = new ArrayList<>();
        }
        packageDescs.add(PackageDesc.of(name));
    }

    @Override
    public void visitRequire(String module, int access, String version) {
        openModuleAttributeBuilder.accept(moduleAttributeBuilder -> moduleAttributeBuilder.requires(ModuleRequireInfo.of(
                ModuleDesc.of(module),
                access,
                version)));
    }

    @Override
    public void visitExport(String packageName, int access, String... modules) {
        /*openModuleAttributeBuilder.accept(moduleAttributeBuilder -> moduleAttributeBuilder.exports(ModuleExportInfo.of(PackageDesc.of(packageName),
                access,
                modules == null ? new ModuleDesc[0] : Stream.of(modules).map(ModuleDesc::of).toArray(ModuleDesc[]::new))));*/ // TODO: different PR
    }

    @Override
    public void visitOpen(String packageName, int access, String... modules) {
        /*openModuleAttributeBuilder.accept(moduleAttributeBuilder -> moduleAttributeBuilder.opens(ModuleOpenInfo.of(PackageDesc.of(packageName),
                access,
                modules == null ? new ModuleDesc[0] : Stream.of(modules).map(ModuleDesc::of).toArray(ModuleDesc[]::new))));*/ // TODO: different PR
    }

    @Override
    public void visitUse(String service) {
        openModuleAttributeBuilder.accept(moduleAttributeBuilder -> moduleAttributeBuilder.uses(ClassDesc.of(service)));
    }

    @Override
    public void visitProvide(String service, String... providers) {
        openModuleAttributeBuilder.accept(moduleAttributeBuilder -> moduleAttributeBuilder.provides(ClassDesc.of(service), Stream.of(providers)
                .map(ClassDesc::of)
                .toArray(ClassDesc[]::new)));
    }

    @Override
    public void visitEnd() {
        openClassBuilder.accept(classBuilder -> {
            classBuilder.with(openModuleAttributeBuilder.build());
            if (packageDescs != null) {
                classBuilder.with(ModulePackagesAttribute.ofNames(packageDescs));
            }
        });
    }
}
