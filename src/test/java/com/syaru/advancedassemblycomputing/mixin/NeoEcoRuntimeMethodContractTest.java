package com.syaru.advancedassemblycomputing.mixin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Contract test against the exact Neo ECO 20.3.0 JAR used by the build. */
class NeoEcoRuntimeMethodContractTest {
    private static final String NEO_ECO_JAR = "neoecoae-20.3.0.jar";
    private static final String THREAD_CLASS =
            "cn.dancingsnow.neoecoae.api.me.ECOCraftingThread";
    private static final String WORKER_CLASS =
            "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity";
    private static final String PATTERN_BUS_CLASS =
            "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity";
    private static final String CLUSTER_CLASS =
            "cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator";

    @Test
    void productionThreadContractHasExactPersistenceAndRecoveryDescriptors() throws Exception {
        ClassContract contract = readContract(THREAD_CLASS);
        assertMethods(contract,
                "startWork(Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/UUID;I)V",
                "clearWork()V",
                "serializeNBT()Lnet/minecraft/nbt/CompoundTag;",
                "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V",
                "recoverOrphanedWorkToNetwork(Ljava/util/Set;Lappeng/api/storage/MEStorage;)Z",
                "recoverInputsToNetwork(Lappeng/api/storage/MEStorage;)Z",
                "recoverUnfinishedInputsToNetwork(Lappeng/api/storage/MEStorage;)Z",
                "dropRecoverablesAndClear(Ljava/util/List;)V");
    }

    @Test
    void productionWorkerAndPatternBusExposeRequiredTargets() throws Exception {
        ClassContract worker = readContract(WORKER_CLASS);
        assertFields(worker, "craftingThreadsLjava/util/List;", "nextFreeThreadIndexI");
        assertMethods(worker,
                "getAvailableThreadSlots()I",
                "getThreadSnapshots()Ljava/util/List;",
                "m_183515_(Lnet/minecraft/nbt/CompoundTag;)V",
                "loadTag(Lnet/minecraft/nbt/CompoundTag;)V");

        ClassContract patternBus = readContract(PATTERN_BUS_CLASS);
        assertMethods(patternBus,
                "getAvailablePatterns()Ljava/util/List;",
                "getCraftingController()Lcn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity;",
                "notifyPersistence()V",
                "m_183515_(Lnet/minecraft/nbt/CompoundTag;)V",
                "loadTag(Lnet/minecraft/nbt/CompoundTag;)V");
    }

    @Test
    void productionClusterHasTheExpectedStructurePredicateTarget() throws Exception {
        assertMethods(
                readContract(CLUSTER_CLASS),
                "verifyInternalStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Z");
    }

    private static ClassContract readContract(String className) throws Exception {
        Path modsDir = Path.of(System.getProperty("aacLocalModsDir", ""));
        Path jarPath = modsDir.resolve(NEO_ECO_JAR);
        assertTrue(Files.isRegularFile(jarPath), "Neo ECO contract JAR is missing: " + jarPath);
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(className.replace('.', '/') + ".class");
            assertNotNull(entry, "Neo ECO class is missing: " + className);
            try (InputStream input = jar.getInputStream(entry)) {
                ClassContract contract = new ClassContract();
                new ClassReader(input).accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public FieldVisitor visitField(
                                    int access, String name, String descriptor,
                                    String signature, Object value) {
                                contract.fields.add(name + descriptor);
                                return null;
                            }

                            @Override
                            public MethodVisitor visitMethod(
                                    int access, String name, String descriptor,
                                    String signature, String[] exceptions) {
                                contract.methods.add(name + descriptor);
                                return null;
                            }
                        },
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return contract;
            }
        }
    }

    private static void assertMethods(ClassContract contract, String... expected) {
        for (String method : expected) {
            assertTrue(contract.methods.contains(method), "missing Neo ECO method: " + method);
        }
    }

    private static void assertFields(ClassContract contract, String... expected) {
        for (String field : expected) {
            assertTrue(contract.fields.contains(field), "missing Neo ECO field: " + field);
        }
    }

    private static final class ClassContract {
        private final Set<String> methods = new HashSet<>();
        private final Set<String> fields = new HashSet<>();
    }
}
