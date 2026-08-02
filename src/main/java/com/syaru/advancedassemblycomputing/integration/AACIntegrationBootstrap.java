package com.syaru.advancedassemblycomputing.integration;

import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableTerminalReceiptLedger;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.neoforged.fml.ModList;

/** ACO公開APIの版検査とAAC Adapter登録を一元管理する。 */
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
        PatternBatchV2Api.registerAdapter(
                AACCraftingTableBatchAdapter.INSTANCE);
        TransactionalPatternBatchAdapter registered =
                PatternBatchV2Api.adapter(
                                AACCraftingTableBatchAdapter.ID)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ACO did not retain AAC adapter "
                                                        + AACCraftingTableBatchAdapter.ID));
        requireRegisteredAdapterIdentity(
                AACCraftingTableBatchAdapter.INSTANCE,
                registered);
        /*
         * Adapter登録だけ成功しても、ACO側Master Switchが無効なら
         * long係数は使われずNeoECO標準の物理Thread経路へ戻る。
         */
        if (AACConfig.nativeCraftingTableBatchEnabled()
                && !ACOConfig.enableTransactionalBatchingV2()) {
            AdvancedAssemblyComputing.LOGGER.error(
                    "AAC native crafting-table batching is enabled, but ACO transaction V2 is disabled. "
                            + "The long execution coefficient is inactive; enable "
                            + "experimentalCraftingEngine.enableTransactionalBatchingV2 in "
                            + "ae2_crafting_optimizer-common.toml.");
        }
        AdvancedAssemblyComputing.LOGGER.info(
                "AAC ACO integration initialized: version={}, adapter={}, nativeCraftingTableBatch={}, maximumExecutionsPerWave={}, patternBatchApi={}, receiptSchema={}",
                loadedVersion(),
                AACCraftingTableBatchAdapter.ID,
                AACConfig.nativeCraftingTableBatchEnabled(),
                AACConfig.maximumCraftingTableBatchExecutions(),
                PatternBatchV2Api.API_VERSION,
                AACCraftingTableTerminalReceiptLedger.schemaVersion());
    }

    static void requireRegisteredAdapterIdentity(
            Object expected,
            Object actual) {
        // 同じIDの別実体へ置換されている場合、Receipt所有者を推測せず起動時に停止する。
        if (actual != expected) {
            throw new IllegalStateException(
                    "ACO registered a different adapter instance for "
                            + AACCraftingTableBatchAdapter.ID);
        }
    }

    private static String loadedVersion() {
        return ModList.get()
                .getModContainerById(
                        AdvancedAssemblyComputing.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static void requireApiVersion(
            String apiName,
            int expected,
            int actual) {
        // 契約版不一致では装飾設備として起動を続けず、誤会計前に停止する。
        if (actual != expected) {
            throw new IllegalStateException(
                    "AAC requires ACO "
                            + apiName
                            + " API "
                            + expected
                            + " but found "
                            + actual);
        }
    }
}
