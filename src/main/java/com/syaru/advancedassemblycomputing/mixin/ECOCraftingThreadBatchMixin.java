package com.syaru.advancedassemblycomputing.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchThread;
import com.syaru.advancedassemblycomputing.execution.AACThreadSidecarFailure;
import com.syaru.advancedassemblycomputing.execution.PreparedCraftingTableWork;
import com.syaru.advancedassemblycomputing.execution.VerifiedCraftingTableRecipe;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * InsaneAE式の「一度実組立してN倍」と、NeoECO式の実Thread進捗を結ぶ。
 *
 * <p>longとBigIntegerで別のクラフト処理を持たず、同じRequest・同じ実assemble・
 * 同じThreadを使う。違いは完成量をAE2へ流すか、親BigInteger台帳へ返すかだけ。</p>
 */
@Mixin(value = ECOCraftingThread.class, remap = false)
public abstract class ECOCraftingThreadBatchMixin
        implements AACCraftingTableBatchThread {
    /** 破損NBTで一Threadへ過大なAEKey配列を確保しない固定上限。 */
    private static final int MAXIMUM_EXACT_OUTPUT_KEYS = 65_536;
    private static final int SIDECAR_SCHEMA = 1;
    private static final String NBT_SIDECAR =
            "aacCraftingTableBatch";
    private static final String NBT_STATE = "state";
    private static final String NBT_QUARANTINE_RAW = "rawSidecar";
    private static final String NBT_FAILURE_CATEGORY = "failureCategory";
    private static final String NBT_FAILURE_SUMMARY = "failureSummary";
    private static final int MAXIMUM_FAILURE_SUMMARY_LENGTH = 256;

    private enum AacThreadState {
        NONE,
        RUNNING,
        OUTPUT_READY,
        QUARANTINED
    }

    @Shadow
    @Final
    private ECOCraftingWorkerBlockEntity worker;

    @Shadow
    @Final
    private TransientCraftingContainer craftingInv;

    @Shadow
    private boolean isBusy;

    @Unique
    private UUID aac$batchTransactionId;

    @Unique
    private UUID aac$ownerTransactionId;

    @Unique
    private String aac$payloadDigest = "";

    @Unique
    private CraftingTableBatchMode aac$batchMode;

    @Unique
    private Map<AEKey, BigInteger> aac$exactOutputs =
            Map.of();

    @Unique
    private AacThreadState aac$state = AacThreadState.NONE;

    @Unique
    private Tag aac$quarantinedRawSidecar;

    @Unique
    private AACThreadSidecarFailure aac$quarantineFailure =
            AACThreadSidecarFailure.INTERNAL_VALIDATION_ERROR;

    @Unique
    private String aac$quarantineSummary = "";

    @Unique
    private UUID aac$quarantineTransactionId;

    @Unique
    private UUID aac$quarantineOwnerTransactionId;

    @Override
    public boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller) {
        if (aac$isQuarantined()
                || aac$state != AacThreadState.NONE
                || isBusy
                || !(request.pattern()
                        instanceof IMolecularAssemblerSupportedPattern pattern)
                || worker.getLevel() == null) {
            return false;
        }

        VerifiedCraftingTableRecipe.Proof proof =
                VerifiedCraftingTableRecipe.assembleOnce(
                                pattern,
                                request.inputsPerExecution(),
                                request.outputsPerExecution(),
                                request.remainingPerExecution(),
                                craftingInv,
                                worker.getLevel())
                        .orElse(null);
        // 実assembleとPattern宣言が一致しないレシピは数量展開せず、通常経路へ返す。
        if (proof == null) {
            return false;
        }
        // 一回分入力×係数がACOの所有権移転量と一致する場合だけ物理仕事を開始する。
        if (!proof.exactInputTotals(
                        request.executions())
                .equals(
                        request.aggregateInputTotals())) {
            craftingInv.clearContent();
            return false;
        }
        Map<AEKey, BigInteger> actualOutputs =
                proof.exactOutputTotals(
                        request.executions());
        // 実assemble出力×係数が親会計の期待値と一致しなければ受理しない。
        if (!actualOutputs.equals(
                request.aggregateExpectedOutputs())) {
            craftingInv.clearContent();
            return false;
        }
        /*
         * long変換を含むThread用データを、冷却材消費とイベント発火より前に完成させる。
         * ここで失敗した要求は設備へ一切の副作用を残さない。
         */
        PreparedCraftingTableWork preparedWork =
                prepareVerifiedWork(
                        request,
                        proof);
        // ここまでが副作用のないPrepare段階。以降は一度だけ狭くcommitする。
        aac$batchTransactionId =
                request.transactionId();
        aac$ownerTransactionId =
                request.ownerTransactionId();
        aac$payloadDigest =
                request.payloadDigest();
        aac$batchMode =
                request.mode();
        aac$exactOutputs =
                actualOutputs;
        aac$state = AacThreadState.RUNNING;
        boolean workStarted = false;
        boolean coolantCommitted = false;
        try {
            // Sidecarを先に用意し、NeoECO Threadと同じ保存単位で復元できるようにする。
            startVerifiedWork(
                    preparedWork,
                    request.craftingJobId());
            workStarted = true;
            /*
             * N回分ではなく、一つの物理Thread分だけ冷却材をcommitする。
             * falseは消費されていない拒否、例外は消費状態不明として隔離する。
             */
            try {
                if (!((ECOCraftingThreadBatchAccessor) (Object) this)
                        .aac$invokeConsumeCraftingCoolant(
                                controller,
                                1)) {
                    aac$rollbackPhysicalWork();
                    craftingInv.clearContent();
                    aac$clearSidecar();
                    return false;
                }
                coolantCommitted = true;
            } catch (RuntimeException | LinkageError coolantFailure) {
                aac$quarantineAfterCommit(
                        coolantFailure);
                craftingInv.clearContent();
                return true;
            }
            /*
             * Eventは永続会計ではない。listenerの例外で完成済みThreadを巻き戻さず、
             * 物理仕事を正本として記録だけ残す。
             */
            try {
                proof.fireCraftingEvent(
                        worker.getLevel(),
                        pattern,
                        craftingInv);
            } catch (RuntimeException | LinkageError eventFailure) {
                com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing.LOGGER
                        .warn(
                                "AAC crafting event listener failed after physical commit; preserving Thread ownership",
                                eventFailure);
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (workStarted && !coolantCommitted) {
                aac$rollbackPhysicalWork();
                craftingInv.clearContent();
                aac$clearSidecar();
                return false;
            }
            if (coolantCommitted) {
                // 冷却材とThreadのどちらかが確定した後の不確定例外は再実行禁止。
                aac$quarantineAfterCommit(
                        failure);
                craftingInv.clearContent();
                return true;
            }
            aac$clearSidecar();
            craftingInv.clearContent();
            return false;
        }
    }

    @Unique
    private void aac$rollbackPhysicalWork() {
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        int occupiedSlots =
                Math.max(
                        1,
                        self.getOccupiedThreadSlots());
        ((ECOCraftingThreadBatchAccessor) (Object) this)
                .aac$invokeClearWork();
        worker.onThreadStop(
                occupiedSlots);
        worker.setChanged();
    }

    @Unique
    private PreparedCraftingTableWork prepareVerifiedWork(
            CraftingTableBatchRequest request,
            VerifiedCraftingTableRecipe.Proof proof) {
        List<GenericStack> outputs;
        List<GenericStack> inputs;
        List<GenericStack> remaining;
        if (request.mode()
                == CraftingTableBatchMode.AE2_JOB) {
            /*
             * AE2正本仕事は実在する合算スタックをThreadへ保持し、NeoECO本来の
             * CPU/ME搬出と取消回収を利用する。
             */
            if (!request.countsFitSignedLong()) {
                throw new IllegalArgumentException(
                        "AE2 batch totals exceed signed long");
            }
            long executions =
                    request.executions()
                            .longValueExact();
            outputs =
                    proof.scaledOutputs(executions);
            inputs =
                    toLongStacks(
                            request.aggregateSlotInputs());
            remaining =
                    proof.scaledRemaining(executions);
        } else {
            /*
             * BigInteger正本仕事はThreadへ一回分の代表スタックだけを置く。
             * 正確な全量はSidecarとACO親台帳が所有し、通常ME搬出へ混ぜない。
             */
            outputs =
                    proof.outputsPerExecution();
            inputs =
                    proof.representativeInputs();
            remaining =
                    proof.remainingPerExecution();
        }
        return new PreparedCraftingTableWork(
                outputs,
                inputs,
                remaining);
    }

    @Unique
    private void startVerifiedWork(
            PreparedCraftingTableWork prepared,
            UUID craftingJobId) {
        ((ECOCraftingThreadBatchAccessor) (Object) this)
                .aac$invokeStartWork(
                        prepared.outputs(),
                        prepared.inputs(),
                        prepared.remaining(),
                        craftingJobId,
                        1);
    }

    @Override
    public boolean aac$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        return aac$isManagedCraftingTableBatch()
                && transactionId.equals(
                        aac$batchTransactionId)
                && payloadDigest.equals(
                        aac$payloadDigest);
    }

    @Override
    public boolean aac$isManagedCraftingTableBatch() {
        return !aac$isQuarantined()
                && isBusy
                && aac$batchTransactionId != null
                && aac$ownerTransactionId != null
                && aac$batchMode != null
                && !aac$payloadDigest.isBlank()
                && !aac$exactOutputs.isEmpty();
    }

    @Override
    public boolean aac$isQuarantined() {
        return aac$state == AacThreadState.QUARANTINED;
    }

    @Override
    public Optional<CraftingTableBatchSnapshot>
            aac$quarantinedCraftingTableBatchSnapshot(
                    UUID transactionId) {
        if (!aac$isQuarantined()
                || aac$quarantineTransactionId == null
                || !aac$quarantineTransactionId.equals(transactionId)) {
            return Optional.empty();
        }
        return Optional.of(
                new CraftingTableBatchSnapshot(
                        aac$quarantineTransactionId,
                        "",
                        CraftingTableBatchSnapshot.State.QUARANTINED,
                        0,
                        ECOCraftingThread.MAX_PROGRESS,
                        Map.of(),
                        "AAC Thread sidecar quarantined: "
                                + aac$quarantineFailure.name()
                                + ": "
                                + aac$quarantineSummary));
    }

    @Override
    public String aac$quarantineDiagnostic() {
        return aac$quarantineFailure.name()
                + ": "
                + aac$quarantineSummary
                + "; transactionId="
                + String.valueOf(aac$quarantineTransactionId)
                + "; ownerTransactionId="
                + String.valueOf(aac$quarantineOwnerTransactionId);
    }

    @Override
    public CraftingTableBatchMode aac$craftingTableBatchMode() {
        return aac$batchMode;
    }

    @Override
    public Optional<CraftingTableBatchSnapshot>
            aac$craftingTableBatchSnapshot(
                    UUID transactionId,
                    String payloadDigest) {
        Optional<CraftingTableBatchSnapshot> quarantined =
                aac$quarantinedCraftingTableBatchSnapshot(
                        transactionId);
        if (quarantined.isPresent()) {
            return quarantined;
        }
        if (!aac$ownsCraftingTableBatch(
                transactionId,
                payloadDigest)) {
            return Optional.empty();
        }
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        boolean ready =
                self.isOutputReady();
        return Optional.of(
                new CraftingTableBatchSnapshot(
                        transactionId,
                        payloadDigest,
                        ready
                                ? CraftingTableBatchSnapshot.State
                                        .OUTPUT_READY
                                : CraftingTableBatchSnapshot.State
                                        .RUNNING,
                        Math.min(
                                ECOCraftingThread.MAX_PROGRESS,
                                Math.max(
                                        0,
                                        self.getProgress())),
                        ECOCraftingThread.MAX_PROGRESS,
                        ready
                                ? aac$exactOutputs
                                : Map.of(),
                        ready
                                ? "NeoECO worker output is ready"
                                : "NeoECO worker is crafting"));
    }

    @Override
    public boolean aac$acknowledgeCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        if (aac$isQuarantined()
                || aac$batchMode
                        != CraftingTableBatchMode.BIG_INTEGER_JOB
                || !self.isOutputReady()
                || !aac$ownsCraftingTableBatch(
                        transactionId,
                        payloadDigest)) {
            return false;
        }
        KeyCounter representativeOutputs =
                new KeyCounter();
        // NeoECOの完了処理へ「代表出力を受理済み」と渡し、MEへは挿入しない。
        for (Object2LongMap.Entry<AEKey> output :
                self.collectOutputItems()) {
            representativeOutputs.add(
                    output.getKey(),
                    output.getLongValue());
        }
        self.applyOutputFlush(
                representativeOutputs);
        return true;
    }

    @Override
    public boolean aac$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        if (aac$isQuarantined()
                || aac$batchMode
                        != CraftingTableBatchMode.BIG_INTEGER_JOB
                || !aac$ownsCraftingTableBatch(
                        transactionId,
                        payloadDigest)) {
            return false;
        }
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        int occupiedSlots =
                Math.max(
                        1,
                        self.getOccupiedThreadSlots());
        /*
         * 代表一回分は実在庫ではないため、通常回収へ渡さずThread占有だけを解放する。
         * 実境界入力の返却はACO親Transactionが行う。
         */
        ((ECOCraftingThreadBatchAccessor) (Object) this)
                .aac$invokeClearWork();
        worker.onThreadStop(occupiedSlots);
        worker.setChanged();
        return true;
    }

    @Inject(
            method = "recoverOrphanedWorkToNetwork",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$keepBigIntegerOrphanOutOfNetwork(
            Set<UUID> activeJobIds,
            MEStorage storage,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        /*
         * BigInteger Threadのスタックは表示・進捗用の一回分であり、
         * 通常回収へ流すと実在庫を複製する。
         */
        if (aac$isBigIntegerBatch()
                || aac$isQuarantined()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
            method = {
                "recoverInputsToNetwork",
                "recoverUnfinishedInputsToNetwork"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void aac$keepBigIntegerInputsOutOfNetwork(
            MEStorage storage,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        // 実入力を所有するACO親Receiptが取消または再開を確定するまで代表入力を返さない。
        if (aac$isBigIntegerBatch()
                || aac$isQuarantined()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
            method = "collectOutputItems",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$hideQuarantinedOutputs(
            CallbackInfoReturnable<KeyCounter> callbackInfo) {
        // 隔離中の代表出力は、ME搬出・Receipt作成のどちらにも公開しない。
        if (aac$isQuarantined()) {
            callbackInfo.setReturnValue(new KeyCounter());
        }
    }

    @Inject(
            method = "applyOutputFlush",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$ignoreQuarantinedOutputFlush(
            KeyCounter acceptedOutputs,
            CallbackInfo callbackInfo) {
        if (aac$isQuarantined()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "dropRecoverablesAndClear",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$doNotDropBigIntegerRepresentativeStacks(
            List<ItemStack> drops,
            CallbackInfo callbackInfo) {
        // 構造破壊時もBigInteger代表スタックを実アイテムとしてドロップさせない。
        if (!aac$isBigIntegerBatch()
                && !aac$isQuarantined()) {
            return;
        }
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        int occupiedSlots =
                Math.max(
                        1,
                        self.getOccupiedThreadSlots());
        ((ECOCraftingThreadBatchAccessor) (Object) this)
                .aac$invokeClearWork();
        worker.onThreadStop(occupiedSlots);
        worker.setChanged();
        callbackInfo.cancel();
    }

    @Inject(method = "serializeNBT", at = @At("RETURN"))
    private void aac$saveBatchSidecar(
            CallbackInfoReturnable<CompoundTag> callbackInfo) {
        if (aac$isQuarantined()) {
            callbackInfo.getReturnValue()
                    .put(
                            NBT_SIDECAR,
                            aac$writeQuarantinedSidecar());
            return;
        }
        if (!aac$isManagedCraftingTableBatch()) {
            return;
        }
        CompoundTag sidecar =
                new CompoundTag();
        sidecar.putInt(
                "schema",
                SIDECAR_SCHEMA);
        sidecar.putString(
                NBT_STATE,
                ((ECOCraftingThread) (Object) this)
                                .isOutputReady()
                        ? AacThreadState.OUTPUT_READY.name()
                        : AacThreadState.RUNNING.name());
        sidecar.putUUID(
                "transactionId",
                aac$batchTransactionId);
        sidecar.putUUID(
                "ownerTransactionId",
                aac$ownerTransactionId);
        sidecar.putString(
                "payloadDigest",
                aac$payloadDigest);
        sidecar.putString(
                "mode",
                aac$batchMode.name());
        sidecar.put(
                "exactOutputs",
                writeExactOutputs(
                        aac$exactOutputs));
        callbackInfo.getReturnValue()
                .put(
                        NBT_SIDECAR,
                        sidecar);
    }

    @Inject(method = "deserializeNBT", at = @At("TAIL"))
    private void aac$loadBatchSidecar(
            CompoundTag data,
            CallbackInfo callbackInfo) {
        aac$resetSidecarForLoad();
        // 通常NeoECO ThreadにはAAC Sidecarがないため、そのまま終了する。
        if (!data.contains(NBT_SIDECAR)) {
            return;
        }
        try {
            Tag raw = data.get(NBT_SIDECAR);
            if (!(raw instanceof CompoundTag sidecar)) {
                throw new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_STATE,
                        "sidecar is not a compound tag");
            }
            if (AacThreadState.QUARANTINED.name()
                    .equals(sidecar.getString(NBT_STATE))) {
                aac$loadPersistedQuarantine(sidecar);
                return;
            }
            aac$loadValidatedSidecar(sidecar);
        } catch (RuntimeException | LinkageError failure) {
            Tag raw = data.get(NBT_SIDECAR);
            aac$quarantine(
                    raw == null ? new CompoundTag() : raw,
                    failure);
        }
    }

    @Inject(method = "clearWork", at = @At("TAIL"))
    private void aac$clearBatchSidecar(
            CallbackInfo callbackInfo) {
        aac$clearSidecar();
    }

    @Unique
    private boolean aac$isBigIntegerBatch() {
        return aac$isManagedCraftingTableBatch()
                && aac$batchMode
                        == CraftingTableBatchMode.BIG_INTEGER_JOB;
    }

    @Unique
    private static List<GenericStack> toLongStacks(
            List<ExactStack> slotInputs) {
        List<GenericStack> result =
                new ArrayList<>(slotInputs.size());
        /*
         * 同じAEKeyが九slotにLong.MAX_VALUEずつあっても合算しない。
         * NeoECO Threadへslot由来の九要素として渡し、各要素だけをexact変換する。
         */
        for (ExactStack input :
                slotInputs) {
            result.add(
                    new GenericStack(
                            input.key(),
                            input.amount()
                                    .longValueExact()));
        }
        return List.copyOf(result);
    }

    @Unique
    private static ListTag writeExactOutputs(
            Map<AEKey, BigInteger> outputs) {
        ListTag result =
                new ListTag();
        // 一キー一要素で保存し、再起動後の部分会計を一意にする。
        for (Map.Entry<AEKey, BigInteger> entry :
                outputs.entrySet()) {
            CompoundTag stack =
                    new CompoundTag();
            stack.put(
                    "key",
                    entry.getKey()
                            .toTagGeneric());
            PreparedVectorBatchCodec
                    .putNonNegative(
                            stack,
                            "amount",
                            entry.getValue());
            result.add(stack);
        }
        return result;
    }

    @Unique
    private static Map<AEKey, BigInteger> readExactOutputs(
            Tag raw) {
        if (!(raw instanceof ListTag list)) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_OUTPUTS,
                    "invalid AAC exact output sidecar");
        }
        if ((!list.isEmpty()
                && list.getElementType()
                        != Tag.TAG_COMPOUND)
                || list.size()
                        > MAXIMUM_EXACT_OUTPUT_KEYS) {
            throw new InvalidSidecarException(
                    list.size() > MAXIMUM_EXACT_OUTPUT_KEYS
                            ? AACThreadSidecarFailure.OVERSIZED_PAYLOAD
                            : AACThreadSidecarFailure.INVALID_OUTPUTS,
                    "invalid AAC exact output sidecar");
        }
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // 保存順を維持したまま、同じキーの二重要素を拒否する。
        for (int index = 0;
                index < list.size();
                index++) {
            CompoundTag entry =
                    list.getCompound(index);
            AEKey key;
            try {
                key =
                        AEKey.fromTagGeneric(
                                entry.getCompound(
                                        "key"));
            } catch (RuntimeException invalidKey) {
                throw new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_AE_KEY,
                        "invalid AAC exact output key",
                        invalidKey);
            }
            BigInteger amount;
            try {
                amount =
                        PreparedVectorBatchCodec
                                .readNonNegative(
                                        entry,
                                        "amount");
            } catch (RuntimeException invalidAmount) {
                throw new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_OUTPUTS,
                        "invalid AAC exact output amount",
                        invalidAmount);
            }
            // 不正キー、非正数、API上限超過、重複キーは再開不能として拒否する。
            if (key == null) {
                throw new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_AE_KEY,
                        "AAC exact output key is null");
            }
            if (amount.signum() <= 0
                    || amount.bitLength()
                            > CraftingTableBatchRequest
                                    .MAXIMUM_COUNT_BITS) {
                throw new InvalidSidecarException(
                        amount.bitLength()
                                        > CraftingTableBatchRequest
                                                .MAXIMUM_COUNT_BITS
                                ? AACThreadSidecarFailure.OVERSIZED_PAYLOAD
                                : AACThreadSidecarFailure.INVALID_OUTPUTS,
                        "invalid AAC exact output amount");
            }
            if (result.putIfAbsent(
                            key,
                            amount)
                    != null) {
                throw new InvalidSidecarException(
                        AACThreadSidecarFailure.DUPLICATE_KEY,
                        "duplicate AAC exact output key");
            }
        }
        return Map.copyOf(result);
    }

    @Unique
    private void aac$clearSidecar() {
        if (aac$isQuarantined()) {
            return;
        }
        aac$resetSidecarForLoad();
    }

    @Unique
    private void aac$resetSidecarForLoad() {
        aac$batchTransactionId = null;
        aac$ownerTransactionId = null;
        aac$payloadDigest = "";
        aac$batchMode = null;
        aac$exactOutputs = Map.of();
        aac$state = AacThreadState.NONE;
        aac$quarantinedRawSidecar = null;
        aac$quarantineFailure =
                AACThreadSidecarFailure.INTERNAL_VALIDATION_ERROR;
        aac$quarantineSummary = "";
        aac$quarantineTransactionId = null;
        aac$quarantineOwnerTransactionId = null;
    }

    @Unique
    private void aac$loadValidatedSidecar(
            CompoundTag sidecar) {
        if (sidecar.getInt("schema") != SIDECAR_SCHEMA) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.UNKNOWN_SCHEMA,
                    "unknown AAC crafting-table batch sidecar schema");
        }
        String storedState =
                sidecar.getString(NBT_STATE);
        if (!storedState.isEmpty()
                && !AacThreadState.RUNNING.name()
                        .equals(storedState)
                && !AacThreadState.OUTPUT_READY.name()
                        .equals(storedState)
                && !AacThreadState.NONE.name()
                        .equals(storedState)) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_STATE,
                    "invalid AAC crafting-table batch state");
        }
        if (!sidecar.hasUUID("transactionId")
                || !sidecar.hasUUID("ownerTransactionId")) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.MISSING_IDENTIFIER,
                    "AAC crafting-table batch sidecar is missing an owner UUID");
        }
        UUID transactionId =
                sidecar.getUUID("transactionId");
        UUID ownerTransactionId =
                sidecar.getUUID("ownerTransactionId");
        String payloadDigest =
                sidecar.getString("payloadDigest");
        if (payloadDigest.isBlank()
                || payloadDigest.length() > MAXIMUM_FAILURE_SUMMARY_LENGTH) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_DIGEST,
                    "AAC crafting-table batch sidecar has an invalid payload digest");
        }
        CraftingTableBatchMode batchMode;
        try {
            batchMode =
                    CraftingTableBatchMode.valueOf(
                            sidecar.getString("mode"));
        } catch (IllegalArgumentException invalidMode) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_MODE,
                    "invalid AAC crafting-table batch mode",
                    invalidMode);
        }
        Map<AEKey, BigInteger> exactOutputs =
                readExactOutputs(
                        sidecar.get("exactOutputs"));
        if (exactOutputs.isEmpty()) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_OUTPUTS,
                    "AAC crafting-table batch sidecar has no exact outputs");
        }
        // 全項目の検証が終わってから初めて所有権を公開する。
        aac$batchTransactionId = transactionId;
        aac$ownerTransactionId = ownerTransactionId;
        aac$payloadDigest = payloadDigest;
        aac$batchMode = batchMode;
        aac$exactOutputs = exactOutputs;
        aac$state =
                AacThreadState.OUTPUT_READY.name()
                        .equals(storedState)
                ? AacThreadState.OUTPUT_READY
                : AacThreadState.RUNNING;
    }

    @Unique
    private void aac$loadPersistedQuarantine(
            CompoundTag sidecar) {
        Tag raw = sidecar.get(NBT_QUARANTINE_RAW);
        aac$quarantinedRawSidecar =
                raw == null ? sidecar.copy() : raw.copy();
        aac$quarantineFailure =
                aac$readFailureCategory(
                        sidecar.getString(
                                NBT_FAILURE_CATEGORY));
        aac$quarantineSummary =
                aac$boundedSummary(
                        sidecar.getString(
                                NBT_FAILURE_SUMMARY));
        aac$quarantineTransactionId =
                aac$optionalUuid(
                        sidecar,
                        "transactionId");
        aac$quarantineOwnerTransactionId =
                aac$optionalUuid(
                        sidecar,
                        "ownerTransactionId");
        aac$state = AacThreadState.QUARANTINED;
    }

    @Unique
    private void aac$quarantine(
            Tag raw,
            Throwable failure) {
        aac$quarantinedRawSidecar =
                raw.copy();
        aac$quarantineFailure =
                failure instanceof InvalidSidecarException invalid
                        ? invalid.category
                        : AACThreadSidecarFailure.INTERNAL_VALIDATION_ERROR;
        aac$quarantineSummary =
                aac$boundedSummary(
                        failure.getMessage());
        if (raw instanceof CompoundTag compound) {
            aac$quarantineTransactionId =
                    aac$optionalUuid(
                            compound,
                            "transactionId");
            aac$quarantineOwnerTransactionId =
                    aac$optionalUuid(
                            compound,
                            "ownerTransactionId");
        } else {
            aac$quarantineTransactionId = null;
            aac$quarantineOwnerTransactionId = null;
        }
        // 不確定な識別子・出力・代表stackは通常の所有状態へ一切昇格させない。
        aac$batchTransactionId = null;
        aac$ownerTransactionId = null;
        aac$payloadDigest = "";
        aac$batchMode = null;
        aac$exactOutputs = Map.of();
        aac$state = AacThreadState.QUARANTINED;
    }

    @Unique
    private void aac$quarantineAfterCommit(
            Throwable failure) {
        // 既に識別・出力が検証済みでも、commit後の不確定例外は再実行させない。
        aac$quarantine(
                aac$writeManagedSidecar(),
                failure);
    }

    @Unique
    private CompoundTag aac$writeManagedSidecar() {
        CompoundTag sidecar =
                new CompoundTag();
        sidecar.putInt(
                "schema",
                SIDECAR_SCHEMA);
        sidecar.putString(
                NBT_STATE,
                AacThreadState.RUNNING.name());
        if (aac$batchTransactionId != null) {
            sidecar.putUUID(
                    "transactionId",
                    aac$batchTransactionId);
        }
        if (aac$ownerTransactionId != null) {
            sidecar.putUUID(
                    "ownerTransactionId",
                    aac$ownerTransactionId);
        }
        sidecar.putString(
                "payloadDigest",
                aac$payloadDigest);
        if (aac$batchMode != null) {
            sidecar.putString(
                    "mode",
                    aac$batchMode.name());
        }
        sidecar.put(
                "exactOutputs",
                writeExactOutputs(
                        aac$exactOutputs));
        return sidecar;
    }

    @Unique
    private CompoundTag aac$writeQuarantinedSidecar() {
        CompoundTag sidecar =
                new CompoundTag();
        sidecar.putInt(
                "schema",
                SIDECAR_SCHEMA);
        sidecar.putString(
                NBT_STATE,
                AacThreadState.QUARANTINED.name());
        sidecar.putString(
                NBT_FAILURE_CATEGORY,
                aac$quarantineFailure.name());
        sidecar.putString(
                NBT_FAILURE_SUMMARY,
                aac$quarantineSummary);
        if (aac$quarantineTransactionId != null) {
            sidecar.putUUID(
                    "transactionId",
                    aac$quarantineTransactionId);
        }
        if (aac$quarantineOwnerTransactionId != null) {
            sidecar.putUUID(
                    "ownerTransactionId",
                    aac$quarantineOwnerTransactionId);
        }
        if (aac$quarantinedRawSidecar != null) {
            sidecar.put(
                    NBT_QUARANTINE_RAW,
                    aac$quarantinedRawSidecar.copy());
        }
        return sidecar;
    }

    @Unique
    private static UUID aac$optionalUuid(
            CompoundTag tag,
            String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    @Unique
    private static AACThreadSidecarFailure aac$readFailureCategory(
            String value) {
        try {
            return AACThreadSidecarFailure.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            return AACThreadSidecarFailure.PERSISTED_QUARANTINE;
        }
    }

    @Unique
    private static String aac$boundedSummary(
            String summary) {
        String normalized =
                summary == null || summary.isBlank()
                        ? "sidecar validation failed"
                        : summary;
        return normalized.length() <= MAXIMUM_FAILURE_SUMMARY_LENGTH
                ? normalized
                : normalized.substring(
                        0,
                        MAXIMUM_FAILURE_SUMMARY_LENGTH);
    }

    private static final class InvalidSidecarException
            extends IllegalArgumentException {
        private final AACThreadSidecarFailure category;

        private InvalidSidecarException(
                AACThreadSidecarFailure category,
                String message) {
            super(message);
            this.category = category;
        }

        private InvalidSidecarException(
                AACThreadSidecarFailure category,
                String message,
                Throwable cause) {
            super(message, cause);
            this.category = category;
        }
    }
}
