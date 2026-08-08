package com.syaru.advancedassemblycomputing.integration;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchThread;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchWorker;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableTerminalReceiptLedger;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import net.minecraftforge.fml.ModList;

/** ACO公開API、Neo ECO対象版、必須Mixinの版検査とAdapter登録を一元管理する。 */
public final class AACIntegrationBootstrap {
    /** Provider所有Target、単一物理操作、実Worker電力会計を含むACO V2契約版。 */
    private static final int SUPPORTED_PATTERN_BATCH_API = 4;

    private AACIntegrationBootstrap() {
    }

    public static void initialize() {
        requireApiVersion(
                "Transactional Pattern Batch",
                SUPPORTED_PATTERN_BATCH_API,
                PatternBatchV2Api.API_VERSION);
        requirePublicAcoContract();
        requireAppliedMixinContract();

        PatternBatchV2Api.registerAdapter(AACCraftingTableBatchAdapter.INSTANCE);
        TransactionalPatternBatchAdapter registered =
                PatternBatchV2Api.adapter(AACCraftingTableBatchAdapter.ID)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "ACO did not retain AAC adapter "
                                                + AACCraftingTableBatchAdapter.ID));
        requireRegisteredAdapterIdentity(
                AACCraftingTableBatchAdapter.INSTANCE,
                registered);

        AdvancedAssemblyComputing.LOGGER.info(
                "AAC ACO integration initialized: version={}, adapter={}, nativeCraftingTableBatch={}, maximumExecutionsPerWave={}, patternBatchApi={}, exactCountBits={}, receiptProtocol={}, snapshotProtocol={}, wakeupRevision={}, receiptSchema={}",
                loadedVersion(),
                AACCraftingTableBatchAdapter.ID,
                AACConfig.nativeCraftingTableBatchEnabled(),
                AACConfig.maximumCraftingTableBatchExecutions(),
                PatternBatchV2Api.API_VERSION,
                CraftingTableBatchRequest.MAXIMUM_COUNT_BITS,
                NativeBatchReceipt.State.values().length,
                CraftingTableBatchSnapshot.class.getName(),
                "optional-1.20.1",
                AACCraftingTableTerminalReceiptLedger.schemaVersion());
    }

    private static void requirePublicAcoContract() {
        requirePublicMethod(
                CraftingTableBatchTarget.class,
                "aco$acceptCraftingTableBatch",
                "aco$craftingTableBatchSnapshot",
                "aco$acknowledgeCraftingTableBatch",
                "aco$forgetCraftingTableBatch",
                "aco$cancelCraftingTableBatch");
        requirePublicMethod(
                NativeBatchReceiptStore.class,
                "aco$isNativeBatchReceiptLedgerHealthy",
                "aco$getNativeBatchReceipt",
                "aco$prepareNativeBatchReceipt",
                "aco$finishNativeBatchReceipt",
                "aco$removeTerminalNativeBatchReceipt");
        if (CraftingTableBatchRequest.MAXIMUM_COUNT_BITS < Long.SIZE
                || NativeBatchReceipt.State.values().length != 3) {
            throw new IllegalStateException(
                    "ACO public exact-count or receipt contract is incompatible");
        }
    }

    private static void requireAppliedMixinContract() {
        if (!AACCraftingTableBatchThread.class.isAssignableFrom(ECOCraftingThread.class)
                || !AACCraftingTableBatchWorker.class.isAssignableFrom(
                        ECOCraftingWorkerBlockEntity.class)
                || !ProviderOwnedPatternBatchTarget.class.isAssignableFrom(
                        ECOCraftingPatternBusBlockEntity.class)
                || !CraftingTableBatchTarget.class.isAssignableFrom(
                        ECOCraftingPatternBusBlockEntity.class)
                || !NativeBatchReceiptStore.class.isAssignableFrom(
                        ECOCraftingPatternBusBlockEntity.class)) {
            throw new IllegalStateException(
                    "AAC correctness Mixin contract was not applied to Neo ECO");
        }
    }

    private static void requirePublicMethod(Class<?> type, String... names) {
        for (String name : names) {
            boolean found = false;
            for (var method : type.getMethods()) {
                if (method.getName().equals(name)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException(
                        "ACO public contract is missing " + type.getName() + "#" + name);
            }
        }
    }

    static void requireRegisteredAdapterIdentity(Object expected, Object actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "ACO registered a different adapter instance for "
                            + AACCraftingTableBatchAdapter.ID);
        }
    }

    private static String loadedVersion() {
        return ModList.get()
                .getModContainerById(AdvancedAssemblyComputing.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static void requireApiVersion(String apiName, int expected, int actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "AAC requires ACO " + apiName + " API " + expected + " but found " + actual);
        }
    }
}
