package com.syaru.advancedassemblycomputing.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

class NeoEcoRuntimeMethodContractTest {
    private static final String THREAD_CLASS =
            "cn.dancingsnow.neoecoae.api.me.ECOCraftingThread";
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
    void productionThreadExposesEveryDirectMixinTarget() throws Exception {
        ClassContract contract = readContract(THREAD_CLASS);
        assertFields(contract, "isBusyZ", "outputsReadyZ");
        assertMethods(
                contract,
                "startBatchWork(Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/UUID;I)V",
                "consumeCraftingCoolant(Lcn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity;I)Z",
                "tick(III)Lappeng/api/networking/ticking/TickRateModulation;",
                "clearWork()V",
                "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
                "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
                "ejectOutputsSafely()Lappeng/api/networking/ticking/TickRateModulation;",
                "collectOutputItems()Lappeng/api/stacks/KeyCounter;",
                "recoverInputsToNetwork(Lappeng/api/storage/MEStorage;)Z",
                "dropRecoverablesAndClear(Ljava/util/List;)V");
    }

    @Test
    void productionWorkerAndPatternBusExposeEveryDirectMixinTarget() throws Exception {
        ClassContract worker = readContract(WORKER_CLASS);
        assertFields(worker, "craftingThreadsLjava/util/List;", "nextFreeThreadIndexI");
        assertMethods(
                worker,
                "getAvailableThreadSlots()I",
                "getThreadSnapshots()Ljava/util/List;",
                "wakeTickingDevice()V",
                "saveAdditional" + PERSISTENCE_DESCRIPTOR,
                "loadTag" + PERSISTENCE_DESCRIPTOR);

        ClassContract patternBus = readContract(PATTERN_BUS_CLASS);
        assertMethods(
                patternBus,
                "getAvailablePatterns()Ljava/util/List;",
                "getCraftingController()Lcn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity;",
                "notifyPersistence()V");
    }

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
        Set<String> methods = readContract(className).methods;

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

    private static ClassContract readContract(String className) throws Exception {
        String resourceName = className.replace('.', '/') + ".class";
        InputStream classBytes =
                NeoEcoRuntimeMethodContractTest.class
                        .getClassLoader()
                        .getResourceAsStream(resourceName);
        assertNotNull(classBytes, "NeoECO production class is missing: " + className);
        try (InputStream input = classBytes) {
            ClassContract contract = new ClassContract();
            new ClassReader(input)
                    .accept(
                            new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public FieldVisitor visitField(
                                        int access,
                                        String name,
                                        String descriptor,
                                        String signature,
                                        Object value) {
                                    contract.fields.add(name + descriptor);
                                    return null;
                                }

                                @Override
                                public MethodVisitor visitMethod(
                                        int access,
                                        String name,
                                        String descriptor,
                                        String signature,
                                        String[] exceptions) {
                                    contract.methods.add(name + descriptor);
                                    return null;
                                }
                            },
                            ClassReader.SKIP_CODE
                                    | ClassReader.SKIP_DEBUG
                                    | ClassReader.SKIP_FRAMES);
            return contract;
        }
    }

    private static void assertMethods(ClassContract contract, String... expected) {
        // Mixinが直接参照する全method descriptorを一件ずつ確認する。
        for (String method : expected) {
            assertTrue(contract.methods.contains(method), "missing NeoECO method: " + method);
        }
    }

    private static void assertFields(ClassContract contract, String... expected) {
        // Shadow対象の名前と型が両方一致することを確認する。
        for (String field : expected) {
            assertTrue(contract.fields.contains(field), "missing NeoECO field: " + field);
        }
    }

    private static final class ClassContract {
        private final Set<String> methods = new HashSet<>();
        private final Set<String> fields = new HashSet<>();
    }
}
