package com.syaru.advancedassemblycomputing.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final String AE_NETWORKED_BLOCK_ENTITY_CLASS =
            "appeng.blockentity.grid.AENetworkedBlockEntity";
    private static final String CLUSTER_CALCULATOR_CLASS =
            "cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator";
    /** 1.21.1のNBT保存・読込はRegistry Providerも受け取る。 */
    private static final String PERSISTENCE_DESCRIPTOR =
            "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V";

    @Test
    void productionNeoEcoWorkerExposesPersistenceTargets() throws Exception {
        assertPersistenceTargets(WORKER_CLASS);
    }

    @Test
    void productionNeoEcoPatternBusExposesPersistenceTargets() throws Exception {
        assertNotNull(
                Class.forName(PATTERN_BUS_CLASS),
                "NeoECO production Pattern Bus is missing");
        assertPersistenceTargets(AE_NETWORKED_BLOCK_ENTITY_CLASS);
    }

    @Test
    void productionNeoEcoWorkerPredicateRemainsInVerifyStructure() throws Exception {
        String resourceName = CLUSTER_CALCULATOR_CLASS.replace('.', '/') + ".class";
        InputStream classBytes =
                NeoEcoRuntimeMethodContractTest.class
                        .getClassLoader()
                        .getResourceAsStream(resourceName);
        assertNotNull(classBytes, "NeoECO cluster calculator is missing");

        int[] matchingStateFacingCalls = {0};
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
                                    // Worker判定を行う実メソッドだけを検査する。
                                    if (!name.equals("verifyStructure")) {
                                        return null;
                                    }
                                    return new MethodVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visitMethodInsn(
                                                int opcode,
                                                String owner,
                                                String invokedName,
                                                String invokedDescriptor,
                                                boolean isInterface) {
                                            if (owner.equals(CLUSTER_CALCULATOR_CLASS.replace('.', '/'))
                                                    && invokedName.equals("matchingStateFacing")) {
                                                matchingStateFacingCalls[0]++;
                                            }
                                        }
                                    };
                                }
                            },
                            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        // Worker、Vent、Pattern Bus、残りのFacing部品の4呼出しを前提にし、
        // ordinal=0のWorker判定が上流更新でずれる変更を検出する。
        assertEquals(4, matchingStateFacingCalls[0]);
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
                        "saveAdditional"
                                + PERSISTENCE_DESCRIPTOR),
                className + " does not expose the production save target");
        assertTrue(
                methods.contains(
                        "loadTag"
                                + PERSISTENCE_DESCRIPTOR),
                className + " does not expose the production load target");
    }
}
