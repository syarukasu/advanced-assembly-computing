package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchWorker;
import com.syaru.advancedassemblycomputing.execution.AACPerformanceMetrics;
import com.syaru.advancedassemblycomputing.execution.AACRevisionTracker;
import com.syaru.advancedassemblycomputing.execution.AACNativeBatchReceiptLedger;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoECO Pattern Busを、ACOの単一一括作業台Targetへ拡張する。
 */
@Mixin(value = ECOCraftingPatternBusBlockEntity.class, remap = false)
public abstract class ECOCraftingPatternBusBatchMixin
        implements ProviderOwnedPatternBatchTarget,
                CraftingTableBatchTarget,
                NativeBatchReceiptStore {
    private static final String AAC_RECEIPTS_NBT =
            "aacCraftingTableBatchReceipts";

    @Shadow
    private int nextWorkerIndex;

    @Unique
    private final AACNativeBatchReceiptLedger aac$batchReceipts =
            new AACNativeBatchReceiptLedger();

    /** Transactionから実Workerへ引く、NBTへ重複保存しない遅延索引。 */
    @Unique
    private final Map<UUID, ECOCraftingWorkerBlockEntity>
            aac$workersByTransaction =
                    new HashMap<>();

    @Unique
    private final Set<UUID> aac$knownMissingTransactions =
            new HashSet<>();

    @Unique
    private boolean aac$workerIndexReady;

    @Unique
    private final AACRevisionTracker aac$revisions =
            new AACRevisionTracker();

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
                        worker);
                aac$knownMissingTransactions.remove(
                        request.transactionId());
                aac$revisions.ownershipChanged();
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
            ((AACCraftingTableBatchWorker) active.orElseThrow())
                    .aac$wakeForBatchChange();
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
                aac$knownMissingTransactions.remove(transactionId);
                aac$revisions.receiptChanged();
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
            aac$knownMissingTransactions.remove(transactionId);
            aac$revisions.ownershipChanged();
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

    /*
     * このMixinはNeoECOクラス全体をremapしないため、Minecraft由来の
     * saveAdditionalだけはNeoECO 20.3.0配布JAR上のSRG名を明示する。
     */
    @Inject(method = "m_183515_", at = @At("TAIL"))
    private void aac$saveBatchReceipts(
            CompoundTag data,
            CallbackInfo callbackInfo) {
        // 空台帳はNBTへ書かず、通常Pattern Busの保存量を増やさない。
        if (!aac$batchReceipts.isEmpty()) {
            data.put(
                    AAC_RECEIPTS_NBT,
                    aac$batchReceipts.save());
        }
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void aac$loadBatchReceipts(
            CompoundTag data,
            CallbackInfo callbackInfo) {
        // Worker一覧は親MODが復元するため、索引は次の照会で一度だけ再構築する。
        aac$workersByTransaction.clear();
        aac$knownMissingTransactions.clear();
        aac$workerIndexReady = false;
        aac$revisions.capacityChanged();
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
        ECOCraftingWorkerBlockEntity cached =
                aac$workersByTransaction.get(
                        transactionId);
        /*
         * キャッシュしたWorkerが同じLevel・BlockPosの実Block Entityであり、
         * 同じPayloadを所有する場合だけ定数時間の索引を使う。
         */
        if (cached != null
                && !cached.isRemoved()
                && cached.getLevel() != null
                && cached.getLevel()
                                .getBlockEntity(
                                        cached.getBlockPos())
                        == cached
                && cached
                        instanceof AACCraftingTableBatchWorker batchWorker
                && batchWorker
                        .aac$ownsCraftingTableBatch(
                                transactionId,
                                payloadDigest)) {
            return Optional.of(
                    cached);
        }
        // 構造変更で無効になった索引は、現在Clusterを調べる前に除去する。
        if (cached != null) {
            aac$workersByTransaction.remove(
                    transactionId);
        }
        if (aac$knownMissingTransactions.contains(transactionId)) {
            AACPerformanceMetrics.pollAvoided();
            return Optional.empty();
        }
        // 未形成中は別設備へ所有権を推測せず、再形成後の照会を待つ。
        if (self.getCluster() == null) {
            return Optional.empty();
        }
        if (!aac$workerIndexReady) {
            aac$rebuildWorkerIndex(self);
        }
        cached = aac$workersByTransaction.get(transactionId);
        if (cached != null
                && !cached.isRemoved()
                && cached.getLevel() != null
                && cached.getLevel().getBlockEntity(cached.getBlockPos()) == cached
                && cached instanceof AACCraftingTableBatchWorker batchWorker
                && batchWorker.aac$ownsCraftingTableBatch(
                        transactionId,
                        payloadDigest)) {
            return Optional.of(cached);
        }
        /*
         * 再起動直後または構造変更直後だけ、現在ClusterのWorkerを一巡する。
         * 一致したWorkerは以後Transaction IDから直接取得する。
         */
        AACPerformanceMetrics.threadScan();
        for (ECOCraftingWorkerBlockEntity worker :
                self.getCluster()
                        .getWorkers()) {
            // AAC契約があり、同じReceiptを所有する一件だけを索引へ登録する。
            if (worker
                            instanceof AACCraftingTableBatchWorker batchWorker
                    && batchWorker
                            .aac$ownsCraftingTableBatch(
                                    transactionId,
                                    payloadDigest)) {
                aac$workersByTransaction.put(
                        transactionId,
                        worker);
                return Optional.of(
                        worker);
            }
        }
        aac$knownMissingTransactions.add(transactionId);
        return Optional.empty();
    }

    @Unique
    private void aac$rebuildWorkerIndex(
            ECOCraftingPatternBusBlockEntity self) {
        aac$workersByTransaction.clear();
        aac$knownMissingTransactions.clear();
        for (ECOCraftingWorkerBlockEntity worker :
                self.getCluster().getWorkers()) {
            if (worker instanceof AACCraftingTableBatchWorker batchWorker) {
                for (UUID transactionId : batchWorker.aac$knownTransactionIds()) {
                    aac$workersByTransaction.put(transactionId, worker);
                }
            }
        }
        aac$workerIndexReady = true;
        aac$revisions.capacityChanged();
        AACPerformanceMetrics.fullIndexRebuild();
    }
}
