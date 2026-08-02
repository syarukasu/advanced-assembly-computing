package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableTerminalReceiptLedger;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchThread;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchWorker;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
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

    @Override
    public boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller) {
        if (!(controller
                        instanceof VectorCraftingControllerBlockEntity vectorController)
                || !vectorController.isFormed()) {
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
                // 実レシピ検証と冷却材検査を通った最初のThreadだけが所有権を得る。
                if (batchThread
                        .aac$acceptCraftingTableBatch(
                                request,
                                controller)) {
                    aac$threadsByTransaction.put(
                            request.transactionId(),
                            batchThread);
                    nextFreeThreadIndex =
                            (index + 1)
                                    % Math.max(
                                            1,
                                            craftingThreads.size());
                    return true;
                }
            }
        }

        /*
         * 新規Threadも一つの物理仕事だけを占有する。
         * executionsはThread数へ変換しない。
         */
        if (craftingThreads.size()
                >= controller.getThreadCountPerWorker()) {
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
        if (!((AACCraftingTableBatchThread) (Object) thread)
                .aac$acceptCraftingTableBatch(
                        request,
                        controller)) {
            return false;
        }
        craftingThreads.add(
                thread);
        aac$threadsByTransaction.put(
                request.transactionId(),
                (AACCraftingTableBatchThread) (Object) thread);
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
            aac$threadsByTransaction.remove(
                    transactionId);
            ((ECOCraftingWorkerBlockEntity) (Object) this)
                    .setChanged();
            return true;
        }
        return false;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void aac$saveTerminalReceipts(
            CompoundTag data,
            HolderLookup.Provider registries,
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
            HolderLookup.Provider registries,
            CallbackInfo callbackInfo) {
        // Thread一覧は親MODが復元するため、実行時索引だけを空にして遅延再構築する。
        aac$threadsByTransaction.clear();
        aac$terminalReceipts.load(
                data.getCompound(
                        AAC_TERMINAL_RECEIPTS_NBT));
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
        /*
         * 再起動直後または親MODがThread一覧を再構築した直後だけ一巡する。
         * 見つけたThreadは以後Transaction IDから定数時間で取得する。
         */
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
        return Optional.empty();
    }
}
