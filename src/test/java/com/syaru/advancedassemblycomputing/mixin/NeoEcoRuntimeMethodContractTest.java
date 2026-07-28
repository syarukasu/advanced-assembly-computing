package com.syaru.advancedassemblycomputing.mixin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

class NeoEcoRuntimeMethodContractTest {
    private static final String WORKER_CLASS =
            "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity";
    private static final String PATTERN_BUS_CLASS =
            "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity";
    /*
     * NeoECO 20.3.0の配布JARでは、BlockEntity#saveAdditionalがこのSRG名になる。
     * AACのremap=false Mixinも同じ名前を指定しなければならない。
     */
    private static final String SAVE_ADDITIONAL_SRG = "m_183515_";
    /** CompoundTagを一つ受け取って値を返さない、NeoECO保存・読込メソッドの記述子。 */
    private static final String PERSISTENCE_DESCRIPTOR =
            "(Lnet/minecraft/nbt/CompoundTag;)V";

    @Test
    void productionNeoEcoWorkerExposesPersistenceTargets() throws Exception {
        assertPersistenceTargets(WORKER_CLASS);
    }

    @Test
    void productionNeoEcoPatternBusExposesPersistenceTargets() throws Exception {
        assertPersistenceTargets(PATTERN_BUS_CLASS);
    }

    private static void assertPersistenceTargets(String className) throws Exception {
        String resourceName =
                className.replace('.', '/')
                        + ".class";
        InputStream classBytes =
                NeoEcoRuntimeMethodContractTest.class
                        .getClassLoader()
                        .getResourceAsStream(resourceName);
        assertNotNull(
                classBytes,
                "NeoECO production class is missing: " + className);

        Set<String> methods =
                new HashSet<>();
        try (InputStream input = classBytes) {
            new ClassReader(input)
                    .accept(
                            new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(
                                        int access,
                                        String name,
                                        String descriptor,
                                        String signature,
                                        String[] exceptions) {
                                    methods.add(name + descriptor);
                                    return null;
                                }
                            },
                            ClassReader.SKIP_CODE
                                    | ClassReader.SKIP_DEBUG
                                    | ClassReader.SKIP_FRAMES);
        }

        assertTrue(
                methods.contains(
                        SAVE_ADDITIONAL_SRG
                                + PERSISTENCE_DESCRIPTOR),
                className + " does not expose the production save target");
        assertTrue(
                methods.contains(
                        "loadTag"
                                + PERSISTENCE_DESCRIPTOR),
                className + " does not expose the production load target");
    }
}
