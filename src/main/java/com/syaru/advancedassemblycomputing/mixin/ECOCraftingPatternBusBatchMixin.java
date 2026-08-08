package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchWorker;
import com.syaru.advancedassemblycomputing.execution.AACPatternBusPersistentState;
import com.syaru.advancedassemblycomputing.execution.AACRevisionIndex;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.batch.NativeBatchReceiptLedger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * NeoECO Pattern Busを、ACOの単一一括作業台Targetへ拡張する。
 */
@Mixin(value = ECOCraftingPatternBusBlockEntity.class, remap = false)
public abstract class ECOCraftingPatternBusBatchMixin
        implements ProviderOwnedPatternBatchTarget,
                CraftingTableBatchTarget,
                NativeBatchReceiptStore,
                AACPatternBusPersistentState {
    private static final String AAC_RECEIPTS_NBT =
            "aacCraftingTableBatchReceipts";

    @Shadow
    private int nextWorkerIndex;

    @Unique
    private final NativeBatchReceiptLedger aac$batchReceipts =
            new NativeBatchReceiptLedger();

    /** Transactionから実Workerへ引く、NBTへ重複保存しない遅延索引。 */
    @Unique
    private final AACRevisionIndex<ECOCraftingWorkerBlockEntity>
            aac$workersByTransaction =
                    new AACRevisionIndex<>();

    @Override
    public BlockEntity aco$getProviderOwnedBatchTarget() {
        return (BlockEntity) (Object) this;
    }

    @Override
    public boolean aco$acceptCraftingTableBatch(
            CraftingTableBatchRequest request) {
        ECOCraftingPatternBusBlockEntity self =
                (ECOCraftingPatternBusBlockEntity) (Object) this;
        /*
         * 停止直後の再送では、同じTransactionを既に所有するThreadまたは終端Receiptを
         * 先に見つけ、新しいWorkerへ二重投入しない。
         */
        if (aco$ownsCraftingTableBatch(
                request.transactionId(),
                request.payloadDigest())) {
            return true;
        }
        boolean ownsPattern =
                self.getAvailablePatterns()
                        .contains(
                                request.pattern());
        if (!(self.getCraftingController()
                        instanceof VectorCraftingControllerBlockEntity controller)
                || !controller.isFormed()
                || self.getCluster() == null
                || (AACConfig.requireExactPatternOwnership()
                        && !ownsPattern)) {
            return false;
        }

        List<ECOCraftingWorkerBlockEntity> workers =
                self.getCluster()
                        .getWorkers();
        if (workers.isEmpty()) {
            return false;
        }
        int start =
                Math.floorMod(
                        nextWorkerIndex,
                        workers.size());
        // Pattern Bus配下のWorkerをラウンドロビン順で一巡する。
        for (int offset = 0;
                offset < workers.size();
                offset++) {
            int index =
                    (start + offset)
                            % workers.size();
            ECOCraftingWorkerBlockEntity worker =
                    workers.get(index);
            // AACの一括Thread境界を持たないWorkerは候補から外す。
            if (!(worker
                    instanceof AACCraftingTableBatchWorker batchWorker)) {
                continue;
            }
            // 最初に受理したWorkerへ所有権を固定し、次回探索位置を一つ進める。
            if (batchWorker
                    .aac$acceptCraftingTableBatch(
                            request,
                            controller)) {
                aac$workersByTransaction.put(
                        request.transactionId(),
                        request.payloadDigest(),
                        worker);
                nextWorkerIndex =
                        (index + 1)
                                % Math.max(
                                        1,
                                        workers.size());
                self.saveChanges();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean aco$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        return aac$findWorker(
                        transactionId,
                        payloadDigest)
                .isPresent();
    }

    @Override
    public Optional<CraftingTableBatchSnapshot>
            aco$craftingTableBatchSnapshot(
                    UUID transactionId,
                    String payloadDigest) {
        return aac$findWorker(
                        transactionId,
                        payloadDigest)
                .flatMap(worker ->
                        ((AACCraftingTableBatchWorker) worker)
                                .aac$craftingTableBatchSnapshot(
                                        transactionId,
                                        payloadDigest));
    }

    @Override
    public boolean aco$acknowledgeCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        ECOCraftingPatternBusBlockEntity self =
                (ECOCraftingPatternBusBlockEntity) (Object) this;
        Optional<ECOCraftingWorkerBlockEntity> active =
                aac$findWorker(
                        transactionId,
                        payloadDigest);
        // 親会計済みReceiptを所有するWorkerだけを解放する。
        if (active.isPresent()
                && ((AACCraftingTableBatchWorker)
                                active.orElseThrow())
                        .aac$acknowledgeCraftingTableBatch(
                                transactionId,
                                payloadDigest)) {
            self.saveChanges();
            return true;
        }
        return false;
    }

    @Override
    public boolean aco$forgetCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        ECOCraftingPatternBusBlockEntity self =
                (ECOCraftingPatternBusBlockEntity) (Object) this;
        // 構造未形成中はWorker台帳を推測で破棄せず、再形成を待つ。
        if (self.getCluster() == null) {
            return false;
        }
        Optional<ECOCraftingWorkerBlockEntity> active =
                aac$findWorker(
                        transactionId,
                        payloadDigest);
        // 同じ終端Receiptを所有するWorkerだけへ明示削除を送る。
        if (active.isPresent()) {
            boolean forgotten =
                    ((AACCraftingTableBatchWorker)
                                    active.orElseThrow())
                            .aac$forgetCraftingTableBatch(
                                    transactionId,
                                    payloadDigest);
            // 削除成功時だけ索引を破棄し、Pattern Bus側も永続化を要求する。
            if (forgotten) {
                aac$workersByTransaction.remove(
                        transactionId);
                self.saveChanges();
            }
            return forgotten;
        }
        // 既に削除済みなら、親Jobの再送を冪等な成功として扱う。
        return true;
    }

    @Override
    public boolean aco$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        ECOCraftingPatternBusBlockEntity self =
                (ECOCraftingPatternBusBlockEntity) (Object) this;
        Optional<ECOCraftingWorkerBlockEntity> active =
                aac$findWorker(
                        transactionId,
                        payloadDigest);
        // 取消対象Receiptを所有するWorkerだけを解放する。
        if (active.isPresent()
                && ((AACCraftingTableBatchWorker)
                                active.orElseThrow())
                        .aac$cancelCraftingTableBatch(
                                transactionId,
                                payloadDigest)) {
            aac$workersByTransaction.remove(
                    transactionId);
            self.saveChanges();
            return true;
        }
        return false;
    }

    @Override
    public boolean aco$isNativeBatchReceiptLedgerHealthy() {
        return aac$batchReceipts.isHealthy();
    }

    @Override
    public NativeBatchReceipt aco$getNativeBatchReceipt(
            UUID transactionId) {
        return aac$batchReceipts.get(
                transactionId);
    }

    @Override
    public boolean aco$prepareNativeBatchReceipt(
            NativeBatchReceipt receipt) {
        boolean prepared =
                aac$batchReceipts.prepare(
                        receipt);
        // 新しいReceiptを保存した時だけBlock Entityをdirtyにする。
        if (prepared) {
            ((ECOCraftingPatternBusBlockEntity) (Object) this)
                    .saveChanges();
        }
        return prepared;
    }

    @Override
    public void aco$finishNativeBatchReceipt(
            UUID transactionId,
            NativeBatchReceipt.State state,
            long updatedTick) {
        aac$batchReceipts.finish(
                transactionId,
                state,
                updatedTick);
        ((ECOCraftingPatternBusBlockEntity) (Object) this)
                .saveChanges();
    }

    @Override
    public boolean aco$removeTerminalNativeBatchReceipt(
            UUID transactionId) {
        boolean removed =
                aac$batchReceipts.removeTerminal(
                        transactionId);
        // 終端Receiptを実際に削除した時だけ保存を要求する。
        if (removed) {
            ((ECOCraftingPatternBusBlockEntity) (Object) this)
                    .saveChanges();
        }
        return removed;
    }

    @Override
    public void aac$savePatternBusBatchState(
            CompoundTag data,
            HolderLookup.Provider registries) {
        // 空台帳はNBTへ書かず、通常Pattern Busの保存量を増やさない。
        if (!aac$batchReceipts.isEmpty()) {
            data.put(
                    AAC_RECEIPTS_NBT,
                    aac$batchReceipts.save());
        }
    }

    @Override
    public void aac$loadPatternBusBatchState(
            CompoundTag data,
            HolderLookup.Provider registries) {
        // Worker一覧は親MODが復元するため、索引は再起動後に一度だけ再構築する。
        aac$workersByTransaction.requestRebuild();
        aac$batchReceipts.load(
                data.getCompound(
                        AAC_RECEIPTS_NBT));
    }

    @Unique
    private Optional<ECOCraftingWorkerBlockEntity> aac$findWorker(
            UUID transactionId,
            String payloadDigest) {
        ECOCraftingPatternBusBlockEntity self =
                (ECOCraftingPatternBusBlockEntity) (Object) this;
        Optional<ECOCraftingWorkerBlockEntity> indexed =
                aac$workersByTransaction.lookup(
                        transactionId,
                        payloadDigest,
                        worker -> !worker.isRemoved()
                                && worker.getLevel() != null
                                && worker.getLevel().getBlockEntity(
                                        worker.getBlockPos()) == worker
                                && worker
                                        instanceof AACCraftingTableBatchWorker batchWorker
                                && batchWorker.aac$ownsCraftingTableBatch(
                                        transactionId,
                                        payloadDigest));
        if (indexed.isPresent()) {
            return indexed;
        }
        if (!aac$workersByTransaction.rebuildRequired()) {
            return Optional.empty();
        }
        // 未形成中は別設備へ所有権を推測せず、再形成後の照会を待つ。
        if (self.getCluster() == null) {
            return Optional.empty();
        }
        /*
         * 再起動直後または構造変更直後だけ、現在ClusterのWorkerを一巡する。
         * 一致したWorkerは以後Transaction IDから直接取得する。
         */
        aac$workersByTransaction.rebuild(
                self.getCluster().getWorkers(),
                worker -> worker
                                instanceof AACCraftingTableBatchWorker batchWorker
                        && batchWorker.aac$ownsCraftingTableBatch(
                                transactionId,
                                payloadDigest)
                                ? Optional.of(
                                        new AACRevisionIndex.IndexedTarget<>(
                                                transactionId,
                                                payloadDigest,
                                                worker))
                                : Optional.empty());
        return aac$workersByTransaction.lookup(
                transactionId,
                payloadDigest,
                worker -> worker
                                instanceof AACCraftingTableBatchWorker batchWorker
                        && batchWorker.aac$ownsCraftingTableBatch(
                                transactionId,
                                payloadDigest));
    }
}
