package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableTerminalReceiptLedger;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchThread;
import com.syaru.advancedassemblycomputing.execution.AACPerformanceMetrics;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchWorker;
import com.syaru.advancedassemblycomputing.execution.AACRevisionTracker;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 一つの論理Batchを、注文数量ぶんのThreadを作らず空き実Thread一本へ割り当てる。
 */
@Mixin(value = ECOCraftingWorkerBlockEntity.class, remap = false)
public abstract class ECOCraftingWorkerBatchMixin
        implements AACCraftingTableBatchWorker {
    private static final String AAC_TERMINAL_RECEIPTS_NBT =
            "aacCraftingTableTerminalReceipts";

    @Shadow
    @Final
    private List<ECOCraftingThread> craftingThreads;

    @Shadow
    private int nextFreeThreadIndex;

    @Unique
    private final AACCraftingTableTerminalReceiptLedger
            aac$terminalReceipts =
                    new AACCraftingTableTerminalReceiptLedger();

    /**
     * AAC TransactionからNeoECO Threadを引く実行時索引。
     *
     * <p>NBTへ重複保存せず、再起動後は最初の照会時にThread sidecarから遅延再構築する。</p>
     */
    @Unique
    private final Map<UUID, AACCraftingTableBatchThread>
            aac$threadsByTransaction =
                    new HashMap<>();

    @Unique
    private final Map<UUID, AACCraftingTableBatchThread>
            aac$quarantinedByTransaction =
                    new HashMap<>();

    @Unique
    private final Set<UUID> aac$knownMissingTransactions =
            new HashSet<>();

    @Unique
    private final Set<UUID> aac$knownMissingQuarantines =
            new HashSet<>();

    @Unique
    private final AACRevisionTracker aac$revisions =
            new AACRevisionTracker();

    @Override
    public boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller) {
        if (!(controller
                        instanceof VectorCraftingControllerBlockEntity vectorController)
                || !vectorController.isFormed()) {
            return false;
        }
        // 完了済みTransactionの再送は、物理Threadを再実行せず受理済みとして返す。
        if (aac$terminalReceipts.contains(
                request.transactionId(),
                request.payloadDigest())) {
            return true;
        }
        // 受理前に完了Receipt枠を予約し、後段で容量不足にならないようにする。
        if (!aac$terminalReceipts.reserve(
                request.transactionId(),
                request.payloadDigest())) {
            return false;
        }

        int threadCount =
                craftingThreads.size();
        // 既存の空きThreadをラウンドロビン順で探し、一仕事だけを割り当てる。
        if (threadCount > 0) {
            int start =
                    Math.floorMod(
                            nextFreeThreadIndex,
                            threadCount);
            for (int offset = 0;
                    offset < threadCount;
                    offset++) {
                int index =
                        (start + offset)
                                % threadCount;
                ECOCraftingThread thread =
                        craftingThreads.get(index);
                // 使用中またはAAC契約がないThreadは候補から外す。
                if (!thread.isFree()
                        || !(thread
                                instanceof AACCraftingTableBatchThread batchThread)) {
                    continue;
                }
                // 隔離ThreadはNeoECO上でfreeに見えても、管理者確認なしに再利用しない。
                if (batchThread.aac$isQuarantined()) {
                    continue;
                }
                // 実レシピ検証と冷却材検査を通った最初のThreadだけが所有権を得る。
                try {
                    if (batchThread
                            .aac$acceptCraftingTableBatch(
                                    request,
                                    controller)) {
                        aac$threadsByTransaction.put(
                                request.transactionId(),
                                batchThread);
                        aac$knownMissingTransactions.remove(
                                request.transactionId());
                        aac$revisions.ownershipChanged();
                        aac$wakeForBatchChange();
                        nextFreeThreadIndex =
                                (index + 1)
                                        % Math.max(
                                                1,
                                                craftingThreads.size());
                        return true;
                    }
                } catch (RuntimeException | LinkageError failure) {
                    aac$terminalReceipts.releaseReservation(
                            request.transactionId(),
                            request.payloadDigest());
                    throw failure;
                }
                /*
                 * 既存Threadで検証に失敗した場合は、次のThreadへ推測で同じ予約を
                 * 移せる。最終的に受理できなければ下の共通releaseへ進む。
                 */
                if (batchThread.aac$isQuarantined()) {
                    continue;
                }
            }
        }

        /*
         * 新規Threadも一つの物理仕事だけを占有する。
         * executionsはThread数へ変換しない。
         */
        if (craftingThreads.size()
                >= controller.getThreadCountPerWorker()) {
            aac$terminalReceipts.releaseReservation(
                    request.transactionId(),
                    request.payloadDigest());
            return false;
        }
        ECOCraftingWorkerBlockEntity self =
                (ECOCraftingWorkerBlockEntity) (Object) this;
        ECOCraftingThread thread =
                new ECOCraftingThread(self);
        /*
         * 実レシピ、冷却材、数量式を新Thread自身で先に検証する。
         * 拒否されたThreadを一覧へ追加すると、失敗要求だけで物理Thread上限を埋めてしまう。
         */
        try {
            if (!((AACCraftingTableBatchThread) (Object) thread)
                    .aac$acceptCraftingTableBatch(
                            request,
                            controller)) {
                aac$terminalReceipts.releaseReservation(
                        request.transactionId(),
                        request.payloadDigest());
                return false;
            }
        } catch (RuntimeException | LinkageError failure) {
            aac$terminalReceipts.releaseReservation(
                    request.transactionId(),
                    request.payloadDigest());
            throw failure;
        }
        craftingThreads.add(
                thread);
        aac$threadsByTransaction.put(
                request.transactionId(),
                (AACCraftingTableBatchThread) (Object) thread);
        aac$knownMissingTransactions.remove(
                request.transactionId());
        aac$revisions.ownershipChanged();
        aac$wakeForBatchChange();
        nextFreeThreadIndex =
                craftingThreads.size()
                        % Math.max(
                                1,
                                controller.getThreadCountPerWorker());
        self.setChanged();
        return true;
    }

    @Override
    public boolean aac$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        // Thread解放済みでも、親Jobがforgetするまでは終端Receiptが所有権を保持する。
        if (aac$terminalReceipts.contains(
                transactionId,
                payloadDigest)) {
            return true;
        }
        return aac$findThread(
                        transactionId,
                        payloadDigest)
                .isPresent();
    }

    @Override
    public void aac$wakeForBatchChange() {
        ((ECOCraftingWorkerBatchAccessor) (Object) this)
                .aac$invokeWakeTickingDevice();
        AACPerformanceMetrics.wakeup();
    }

    @Override
    public Set<UUID> aac$knownTransactionIds() {
        Set<UUID> result = new HashSet<>(
                aac$threadsByTransaction.keySet());
        result.addAll(aac$terminalReceipts.transactionIds());
        return Set.copyOf(result);
    }

    @Override
    public Optional<CraftingTableBatchSnapshot>
            aac$craftingTableBatchSnapshot(
                    UUID transactionId,
                    String payloadDigest) {
        Optional<AACCraftingTableBatchThread> active =
                aac$findThread(
                        transactionId,
                        payloadDigest);
        // 生きたThreadがある間は、その小さな進捗Snapshotを正本として返す。
        if (active.isPresent()) {
            return active.orElseThrow()
                    .aac$craftingTableBatchSnapshot(
                            transactionId,
                            payloadDigest);
        }
        // 実Thread解放後は、同じWorker NBTに残した完了Receiptを返す。
        Optional<CraftingTableBatchSnapshot> quarantined =
                aac$findQuarantinedThread(
                        transactionId)
                        .flatMap(thread ->
                                thread
                                        .aac$quarantinedCraftingTableBatchSnapshot(
                                                transactionId));
        if (quarantined.isPresent()) {
            return quarantined;
        }
        return aac$terminalReceipts.snapshot(
                transactionId,
                payloadDigest);
    }

    @Override
    public boolean aac$acknowledgeCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        /*
         * 親会計済みのBigInteger仕事を所有するThreadだけを解放する。
         * Receiptを同じWorker NBTへ先に登録し、Thread状態との保存順を原子的にする。
         */
        Optional<AACCraftingTableBatchThread> active =
                aac$findThread(
                        transactionId,
                        payloadDigest);
        // 生きたThreadが見つかった場合だけ、出力Receiptを保存して解放する。
        if (active.isPresent()) {
            AACCraftingTableBatchThread batchThread =
                    active.orElseThrow();
            Optional<CraftingTableBatchSnapshot> snapshot =
                    batchThread
                            .aac$craftingTableBatchSnapshot(
                                    transactionId,
                                    payloadDigest);
            // 実出力が完成していないThreadを終端Receiptへ昇格しない。
            if (snapshot.isEmpty()
                    || snapshot.orElseThrow()
                                    .state()
                            != CraftingTableBatchSnapshot.State
                                    .OUTPUT_READY) {
                return false;
            }
            CraftingTableBatchSnapshot completed =
                    snapshot.orElseThrow();
            // 台帳上限またはPayload不一致ならThreadを解放せず、次tickの再試行を待つ。
            if (!aac$terminalReceipts.record(
                    transactionId,
                    batchThread.aac$ownerTransactionId()
                            .orElse(null),
                    payloadDigest,
                    completed.exactOutputs())) {
                return false;
            }
            // 終端Receiptを作成できた後だけ、代表スタックを消してThreadを解放する。
            if (batchThread
                    .aac$acknowledgeCraftingTableBatch(
                            transactionId,
                            payloadDigest)) {
                aac$threadsByTransaction.remove(
                        transactionId);
                aac$knownMissingTransactions.remove(transactionId);
                aac$revisions.receiptChanged();
                aac$revisions.ownershipChanged();
                aac$wakeForBatchChange();
                ((ECOCraftingWorkerBlockEntity) (Object) this)
                        .setChanged();
                return true;
            }
            return false;
        }
        // 再起動後にThreadが既に解放済みなら、残存Receiptを冪等な成功として扱う。
        return aac$terminalReceipts.contains(
                transactionId,
                payloadDigest);
    }

    @Override
    public boolean aac$forgetCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        // 生きたThreadを所有したまま完了証明だけを削除してはならない。
        if (aac$findThread(
                        transactionId,
                        payloadDigest)
                .isPresent()) {
            return false;
        }
        boolean forgotten =
                aac$terminalReceipts.forget(
                        transactionId,
                        payloadDigest);
        // 削除または既削除を保存し、親Jobの再送を冪等に終える。
        if (forgotten) {
            aac$knownMissingTransactions.remove(transactionId);
            aac$revisions.receiptChanged();
            aac$wakeForBatchChange();
            ((ECOCraftingWorkerBlockEntity) (Object) this)
                    .setChanged();
        }
        return forgotten;
    }

    @Override
    public boolean aac$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        // 完了Receiptがある仕事は既に実出力を持つため、入力へ巻き戻さない。
        if (aac$terminalReceipts.contains(
                transactionId,
                payloadDigest)) {
            return false;
        }
        Optional<AACCraftingTableBatchThread> active =
                aac$findThread(
                        transactionId,
                        payloadDigest);
        // 未完了BigInteger仕事を所有するThreadだけを代表スタック返却なしで解放する。
        if (active.isPresent()
                && active.orElseThrow()
                        .aac$cancelCraftingTableBatch(
                                transactionId,
                                payloadDigest)) {
            aac$terminalReceipts.releaseReservation(
                    transactionId,
                    payloadDigest);
            aac$threadsByTransaction.remove(
                    transactionId);
            aac$knownMissingTransactions.remove(transactionId);
            aac$revisions.receiptChanged();
            aac$revisions.ownershipChanged();
            aac$wakeForBatchChange();
            ((ECOCraftingWorkerBlockEntity) (Object) this)
                    .setChanged();
            return true;
        }
        // Thread保存前に停止した場合は、孤立した予約だけを安全に解放する。
        if (aac$terminalReceipts.isReserved(
                transactionId,
                payloadDigest)) {
            boolean released =
                    aac$terminalReceipts.releaseReservation(
                            transactionId,
                            payloadDigest);
            if (released) {
                ((ECOCraftingWorkerBlockEntity) (Object) this)
                        .setChanged();
            }
            return released;
        }
        return false;
    }

    /*
     * このMixinはNeoECOクラス全体をremapしないため、Minecraft由来の
     * saveAdditionalだけはNeoECO 20.4.0配布JAR上のSRG名を明示する。
     */
    @Inject(method = "m_183515_", at = @At("TAIL"))
    private void aac$saveTerminalReceipts(
            CompoundTag data,
            CallbackInfo callbackInfo) {
        // 空台帳は通常NeoECO WorkerのNBTを増やさない。
        if (!aac$terminalReceipts.isEmpty()) {
            data.put(
                    AAC_TERMINAL_RECEIPTS_NBT,
                    aac$terminalReceipts.save());
        }
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void aac$loadTerminalReceipts(
            CompoundTag data,
            CallbackInfo callbackInfo) {
        // Thread一覧は親MODが復元するため、ここで一度だけ実行時索引を再構築する。
        aac$threadsByTransaction.clear();
        aac$quarantinedByTransaction.clear();
        aac$knownMissingTransactions.clear();
        aac$knownMissingQuarantines.clear();
        aac$terminalReceipts.load(
                data.getCompound(
                        AAC_TERMINAL_RECEIPTS_NBT));
        for (int index = 0;
                index < craftingThreads.size();
                index++) {
            ECOCraftingThread thread =
                    craftingThreads.get(index);
            if (thread instanceof AACCraftingTableBatchThread batchThread) {
                batchThread.aac$ownerTransactionId()
                        .ifPresent(id -> aac$threadsByTransaction.put(id, batchThread));
                batchThread.aac$quarantineTransactionId()
                        .ifPresent(id -> aac$quarantinedByTransaction.put(id, batchThread));
            }
            if (thread instanceof AACCraftingTableBatchThread batchThread
                    && batchThread.aac$isQuarantined()) {
                AdvancedAssemblyComputing.LOGGER.warn(
                        "AAC quarantined NeoECO Thread: workerPos={}, threadIndex={}, {}",
                        ((ECOCraftingWorkerBlockEntity) (Object) this)
                                .getBlockPos(),
                        index,
                        batchThread.aac$quarantineDiagnostic());
            }
        }
        AACPerformanceMetrics.fullIndexRebuild();
    }

    @Unique
    private Optional<AACCraftingTableBatchThread> aac$findThread(
            UUID transactionId,
            String payloadDigest) {
        AACCraftingTableBatchThread cached =
                aac$threadsByTransaction.get(
                        transactionId);
        // 索引先が現在も同じPayloadを所有する場合は、Thread総走査を省略する。
        if (cached != null
                && cached.aac$ownsCraftingTableBatch(
                        transactionId,
                        payloadDigest)) {
            return Optional.of(
                    cached);
        }
        // 解放済みまたは別Payloadの古い索引を、次の再構築前に除去する。
        if (cached != null) {
            aac$threadsByTransaction.remove(
                    transactionId);
        }
        if (aac$knownMissingTransactions.contains(transactionId)) {
            AACPerformanceMetrics.pollAvoided();
            return Optional.empty();
        }
        /*
         * 再起動直後または親MODがThread一覧を再構築した直後だけ一巡する。
         * 見つけたThreadは以後Transaction IDから定数時間で取得する。
         */
        AACPerformanceMetrics.threadScan();
        for (ECOCraftingThread thread :
                craftingThreads) {
            // AAC管理Threadかつ同一Payloadを所有する一件だけを索引へ登録する。
            if (thread
                            instanceof AACCraftingTableBatchThread batchThread
                    && batchThread
                            .aac$ownsCraftingTableBatch(
                                    transactionId,
                                    payloadDigest)) {
                aac$threadsByTransaction.put(
                        transactionId,
                        batchThread);
                return Optional.of(
                        batchThread);
            }
        }
        aac$knownMissingTransactions.add(transactionId);
        return Optional.empty();
    }

    @Unique
    private Optional<AACCraftingTableBatchThread>
            aac$findQuarantinedThread(UUID transactionId) {
        AACCraftingTableBatchThread cached =
                aac$quarantinedByTransaction.get(transactionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (aac$knownMissingQuarantines.contains(transactionId)) {
            return Optional.empty();
        }
        AACPerformanceMetrics.threadScan();
        for (ECOCraftingThread thread : craftingThreads) {
            if (thread instanceof AACCraftingTableBatchThread batchThread
                    && batchThread.aac$isQuarantined()
                    && batchThread
                            .aac$quarantinedCraftingTableBatchSnapshot(
                                    transactionId)
                            .isPresent()) {
                aac$quarantinedByTransaction.put(transactionId, batchThread);
                return Optional.of(batchThread);
            }
        }
        aac$knownMissingQuarantines.add(transactionId);
        return Optional.empty();
    }
}
