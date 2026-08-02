package com.syaru.advancedassemblycomputing.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.execution.AACCraftingTableBatchThread;
import com.syaru.advancedassemblycomputing.execution.PreparedCraftingTableWork;
import com.syaru.advancedassemblycomputing.execution.VerifiedCraftingTableRecipe;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec;
import com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import appeng.api.networking.ticking.TickRateModulation;
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

    @Shadow
    @Final
    private ECOCraftingWorkerBlockEntity worker;

    @Shadow
    @Final
    private TransientCraftingContainer craftingInv;

    @Shadow
    private boolean isBusy;

    @Shadow
    private boolean outputsReady;

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

    @Override
    public boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller) {
        if (isBusy
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
        /*
         * N回分の冷却材を消費すると注文量依存コストへ戻る。
         * 物理Thread一仕事としてNeoECO本来の一回分だけを検査・消費する。
         */
        if (!((ECOCraftingThreadBatchAccessor) (Object) this)
                .aac$invokeConsumeCraftingCoolant(
                        controller,
                        1)) {
            craftingInv.clearContent();
            return false;
        }
        /*
         * InsaneAEと同じく、注文数量ではなく実際に行った一回のassembleだけを通知する。
         * 係数展開した回数ぶんイベントを発火すると、TPS負荷と副作用が数量依存へ戻る。
         */
        proof.fireCraftingEvent(
                worker.getLevel(),
                pattern,
                craftingInv);

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
        try {
            startVerifiedWork(
                    preparedWork,
                    request.craftingJobId());
            return true;
        } catch (RuntimeException failure) {
            aac$clearSidecar();
            craftingInv.clearContent();
            throw failure;
        }
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
                .aac$invokeStartBatchWork(
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
        return isBusy
                && aac$batchTransactionId != null
                && aac$ownerTransactionId != null
                && aac$batchMode != null
                && !aac$payloadDigest.isBlank()
                && !aac$exactOutputs.isEmpty();
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
        if (!aac$ownsCraftingTableBatch(
                transactionId,
                payloadDigest)) {
            return Optional.empty();
        }
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        boolean ready = outputsReady;
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
        if (aac$batchMode
                        != CraftingTableBatchMode.BIG_INTEGER_JOB
                || !outputsReady
                || !aac$ownsCraftingTableBatch(
                        transactionId,
                        payloadDigest)) {
            return false;
        }
        ECOCraftingThread self =
                (ECOCraftingThread) (Object) this;
        int occupiedSlots = Math.max(1, self.getOccupiedThreadSlots());
        // ACOが正確なBigInteger出力を会計済みなので、代表出力をMEへ挿入せず解放する。
        ((ECOCraftingThreadBatchAccessor) (Object) this)
                .aac$invokeClearWork();
        worker.onThreadStop(occupiedSlots);
        worker.setChanged();
        return true;
    }

    @Override
    public boolean aac$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest) {
        if (aac$batchMode
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
            method = "recoverInputsToNetwork",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$keepBigIntegerInputsOutOfNetwork(
            MEStorage storage,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        // 実入力を所有するACO親Receiptが取消または再開を確定するまで代表入力を返さない。
        if (aac$isBigIntegerBatch()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
            method = "ejectOutputsSafely",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$holdBigIntegerOutputForAccounting(
            CallbackInfoReturnable<TickRateModulation> callbackInfo) {
        if (!aac$isBigIntegerBatch()) {
            return;
        }
        // BigInteger出力はACOが正本を会計するまでThread内へ保持する。
        callbackInfo.setReturnValue(TickRateModulation.URGENT);
    }

    @Inject(
            method = "dropRecoverablesAndClear",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$doNotDropBigIntegerRepresentativeStacks(
            List<ItemStack> drops,
            CallbackInfo callbackInfo) {
        // 構造破壊時もBigInteger代表スタックを実アイテムとしてドロップさせない。
        if (!aac$isBigIntegerBatch()) {
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
            HolderLookup.Provider registries,
            CallbackInfoReturnable<CompoundTag> callbackInfo) {
        if (!aac$isManagedCraftingTableBatch()) {
            return;
        }
        CompoundTag sidecar =
                new CompoundTag();
        sidecar.putInt(
                "schema",
                SIDECAR_SCHEMA);
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
            HolderLookup.Provider registries,
            CompoundTag data,
            CallbackInfo callbackInfo) {
        aac$clearSidecar();
        // 通常NeoECO ThreadにはAAC Sidecarがないため、そのまま終了する。
        if (!data.contains(
                NBT_SIDECAR,
                Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag sidecar =
                data.getCompound(
                        NBT_SIDECAR);
        // 未知schemaまたは識別子欠落は所有権を推測せずロードを拒否する。
        if (sidecar.getInt("schema")
                        != SIDECAR_SCHEMA
                || !sidecar.hasUUID(
                        "transactionId")
                || !sidecar.hasUUID(
                        "ownerTransactionId")) {
            throw new IllegalArgumentException(
                    "invalid AAC crafting-table batch sidecar");
        }
        aac$batchTransactionId =
                sidecar.getUUID(
                        "transactionId");
        aac$ownerTransactionId =
                sidecar.getUUID(
                        "ownerTransactionId");
        aac$payloadDigest =
                sidecar.getString(
                        "payloadDigest");
        try {
            aac$batchMode =
                    CraftingTableBatchMode.valueOf(
                            sidecar.getString(
                                    "mode"));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalArgumentException(
                    "invalid AAC crafting-table batch mode",
                    invalidMode);
        }
        aac$exactOutputs =
                readExactOutputs(
                        sidecar.get(
                                "exactOutputs"));
        // 空識別子または出力なしのSidecarは再開可能な物理仕事ではない。
        if (aac$payloadDigest.isBlank()
                || aac$exactOutputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "incomplete AAC crafting-table batch sidecar");
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
                            .toTagGeneric(ACORegistryAccess.require()));
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
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty()
                        && list.getElementType()
                                != Tag.TAG_COMPOUND)
                || list.size()
                        > MAXIMUM_EXACT_OUTPUT_KEYS) {
            throw new IllegalArgumentException(
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
            AEKey key =
                    AEKey.fromTagGeneric(
                            ACORegistryAccess.require(),
                            entry.getCompound(
                                    "key"));
            BigInteger amount =
                    PreparedVectorBatchCodec
                            .readNonNegative(
                                    entry,
                                    "amount");
            // 不正キー、非正数、API上限超過、重複キーは再開不能として拒否する。
            if (key == null
                    || amount.signum() <= 0
                    || amount.bitLength()
                            > CraftingTableBatchRequest
                                    .MAXIMUM_COUNT_BITS
                    || result.putIfAbsent(
                                    key,
                                    amount)
                            != null) {
                throw new IllegalArgumentException(
                        "duplicate or invalid AAC exact output");
            }
        }
        return Map.copyOf(result);
    }

    @Unique
    private void aac$clearSidecar() {
        aac$batchTransactionId = null;
        aac$ownerTransactionId = null;
        aac$payloadDigest = "";
        aac$batchMode = null;
        aac$exactOutputs = Map.of();
    }
}
