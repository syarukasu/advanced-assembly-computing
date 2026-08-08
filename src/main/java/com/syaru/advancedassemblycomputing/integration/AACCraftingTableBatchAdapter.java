package com.syaru.advancedassemblycomputing.integration;

import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.util.LongBatchStackMath;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchCpuAccountingMode;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchEnergyAccountingMode;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchOwnershipProof;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchPayloadFingerprint;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.advancedassemblycomputing.execution.AACNativePatternBatchSupport;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * ACOのCPU会計と、AAC/NeoECOの実作業台Workerを結ぶNative Adapter。
 *
 * <p>論理N回を一つの物理Worker仕事として所有するため、CPU予算へは一操作だけ返す。
 * Task残量・入力・期待出力はACOの永続TransactionでN回ぶん正確に会計する。</p>
 */
public final class AACCraftingTableBatchAdapter
        implements TransactionalPatternBatchAdapter {
    public static final AACCraftingTableBatchAdapter INSTANCE =
            new AACCraftingTableBatchAdapter();
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(
                    AdvancedAssemblyComputing.MOD_ID,
                    "native_crafting_table_batch");

    private AACCraftingTableBatchAdapter() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        // 外部機械向けAdapterより先に、形成済みAAC自身のPatternを選ぶ。
        return 100_000;
    }

    @Override
    public BatchCpuAccountingMode cpuAccountingMode() {
        return BatchCpuAccountingMode.SINGLE_PHYSICAL_OPERATION;
    }

    @Override
    public BatchEnergyAccountingMode energyAccountingMode() {
        /*
         * NeoECO Workerが実Threadの進捗とAE電力を消費する。
         * ACO CPU側では論理N回ぶんを重ねて課金しない。
         */
        return BatchEnergyAccountingMode.TARGET_PHYSICAL_OPERATION;
    }

    @Override
    public boolean supports(PatternBatchContext context) {
        if (!AACConfig.nativeCraftingTableBatchEnabled()
                || !(context.pattern()
                        instanceof IMolecularAssemblerSupportedPattern)
                || !context.providerOwnedTarget()
                || context.craftingJobId() == null
                || !(context.provider()
                        instanceof ECOCraftingPatternBusBlockEntity patternBus)
                || !(context.target()
                        instanceof ECOCraftingPatternBusBlockEntity)
                || !(context.target()
                        instanceof CraftingTableBatchTarget)
                || !(context.target()
                        instanceof NativeBatchReceiptStore)) {
            return false;
        }
        return patternBus.getCraftingController()
                        instanceof VectorCraftingControllerBlockEntity controller
                && controller.isFormed()
                && controller.getMaxInFlightCrafts() > 0
                && patternBus.getAvailablePatterns().contains(
                        context.pattern());
    }

    @Override
    public long limitExecutions(
            PatternBatchContext context,
            long offeredExecutions) {
        long configured = Math.min(
                offeredExecutions,
                AACConfig.maximumCraftingTableBatchExecutions());
        return LongBatchStackMath.safeExecutionLimit(
                context.copyInputsPerExecution(),
                LongBatchStackMath.fromCounter(
                        context.copyOutputsPerExecution()),
                LongBatchStackMath.fromCounter(
                        context.copyRemainingOutputsPerExecution()),
                configured);
    }

    @Override
    public PreparedPatternBatch prepare(
            PatternBatchContext context,
            PatternBatchBudget budget,
            UUID transactionId) {
        if (!supports(context)) {
            throw new IllegalStateException(
                    "AAC crafting-table batch target is no longer available");
        }
        long executions = limitExecutions(
                context,
                budget.maximumExecutions());
        if (executions <= 0L || !budget.hasTimeRemaining()) {
            throw new IllegalStateException(
                    "AAC crafting-table batch has no safe execution capacity");
        }

        var scaledInputs = AACNativePatternBatchSupport.scaleInputs(
                context,
                executions);
        List<GenericStack> aggregateInputs =
                AACNativePatternBatchSupport.flatten(scaledInputs);
        List<GenericStack> expectedOutputs =
                AACNativePatternBatchSupport.scaleAllExpectedOutputs(
                        context,
                        executions);
        if (!LongBatchStackMath.totalsFitLong(expectedOutputs)) {
            throw new IllegalStateException(
                    "AAC worker output counters would exceed signed long");
        }

        CompoundTag adapterData = new CompoundTag();
        adapterData.putInt("schema", 1);
        adapterData.putLong(
                "providerPos",
                context.target().getBlockPos().asLong());
        adapterData.putUUID(
                "craftingJobId",
                context.craftingJobId());
        return new PreparedPatternBatch(
                transactionId,
                executions,
                aggregateInputs,
                expectedOutputs,
                adapterData);
    }

    @Override
    public PatternBatchCommit commit(
            PatternBatchContext context,
            PreparedPatternBatch prepared) {
        Target target = requireTarget(context);
        String patternFingerprint =
                AACNativePatternBatchSupport.fingerprint(context);
        String payloadDigest =
                BatchPayloadFingerprint.of(prepared);
        NativeBatchReceipt existing =
                target.receipts().aco$getNativeBatchReceipt(
                        prepared.transactionId());
        if (existing != null) {
            validateExisting(
                    existing,
                    prepared,
                    patternFingerprint,
                    payloadDigest);
            if (existing.state()
                    == NativeBatchReceipt.State.ACCEPTED) {
                return acceptedCommit(
                        context,
                        prepared,
                        payloadDigest);
            }
            if (existing.state()
                    == NativeBatchReceipt.State.REJECTED) {
                return rejectedCommit();
            }
        } else {
            NativeBatchReceipt pending = new NativeBatchReceipt(
                    prepared.transactionId(),
                    NativeBatchReceipt.State.PENDING,
                    prepared.offeredExecutions(),
                    patternFingerprint,
                    payloadDigest,
                    context.level().getGameTime());
            if (!target.receipts()
                    .aco$prepareNativeBatchReceipt(pending)) {
                return rejectedCommit();
            }
        }

        CraftingTableBatchRequest request =
                createRequest(
                        context,
                        prepared,
                        payloadDigest);
        boolean accepted =
                target.patternBus().aco$acceptCraftingTableBatch(
                        request);
        target.receipts().aco$finishNativeBatchReceipt(
                prepared.transactionId(),
                accepted
                        ? NativeBatchReceipt.State.ACCEPTED
                        : NativeBatchReceipt.State.REJECTED,
                context.level().getGameTime());
        return accepted
                ? acceptedCommit(
                        context,
                        prepared,
                        payloadDigest)
                : rejectedCommit();
    }

    @Override
    public void rollback(
            PatternBatchContext context,
            PreparedPatternBatch prepared) {
        Target target = requireTarget(context);
        NativeBatchReceipt receipt =
                target.receipts().aco$getNativeBatchReceipt(
                        prepared.transactionId());
        if (receipt == null
                || receipt.state()
                        == NativeBatchReceipt.State.REJECTED) {
            return;
        }
        if (receipt.state()
                == NativeBatchReceipt.State.ACCEPTED) {
            throw new IllegalStateException(
                    "accepted AAC worker ownership cannot be rolled back");
        }
        target.receipts().aco$finishNativeBatchReceipt(
                prepared.transactionId(),
                NativeBatchReceipt.State.REJECTED,
                context.level().getGameTime());
    }

    @Override
    public BatchRecoveryResult reconcileTarget(
            ServerLevel level,
            BatchTransactionRecord record) {
        if (!level.isLoaded(record.targetPos())) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.RETRY,
                    0L,
                    "AAC Pattern Bus chunk is not loaded");
        }
        if (!(level.getBlockEntity(record.targetPos())
                        instanceof CraftingTableBatchTarget patternBus)
                || !(level.getBlockEntity(record.targetPos())
                        instanceof NativeBatchReceiptStore receipts)) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "AAC Pattern Bus or its receipt ledger is missing");
        }
        if (!receipts.aco$isNativeBatchReceiptLedgerHealthy()) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "AAC Pattern Bus receipt ledger is malformed");
        }

        NativeBatchReceipt receipt =
                receipts.aco$getNativeBatchReceipt(record.id());
        if (receipt == null) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                    0L,
                    "AAC Pattern Bus has no matching receipt");
        }
        String payloadDigest =
                AACNativePatternBatchSupport.payloadDigest(
                        record.offeredExecutions(),
                        record.extractedInputs(),
                        record.expectedOutputs());
        if (receipt.executions()
                        != record.offeredExecutions()
                || !receipt.patternFingerprint()
                        .equals(record.patternFingerprint())
                || !receipt.payloadDigest()
                        .equals(payloadDigest)) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.QUARANTINE,
                    0L,
                    "AAC target receipt does not match the journal payload");
        }
        if (receipt.state()
                == NativeBatchReceipt.State.ACCEPTED) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.ACCEPTED,
                    receipt.executions(),
                    "AAC worker owns the exact crafting-table batch");
        }
        if (receipt.state()
                == NativeBatchReceipt.State.REJECTED) {
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                    0L,
                    "AAC worker rejected the batch");
        }

        /*
         * Pattern BusのACCEPTED保存直前に停止した場合でも、Thread側の
         * transaction sidecarが一致すれば所有権を証明してReceiptを完了する。
         */
        if (patternBus.aco$ownsCraftingTableBatch(
                record.id(),
                payloadDigest)) {
            receipts.aco$finishNativeBatchReceipt(
                    record.id(),
                    NativeBatchReceipt.State.ACCEPTED,
                    level.getGameTime());
            return new BatchRecoveryResult(
                    BatchRecoveryResult.TargetState.ACCEPTED,
                    receipt.executions(),
                    "AAC worker sidecar recovered pending ownership");
        }
        return new BatchRecoveryResult(
                BatchRecoveryResult.TargetState.NOT_ACCEPTED,
                0L,
                "AAC pending receipt has no matching worker ownership");
    }

    @Override
    public void forgetResolvedTarget(
            PatternBatchContext context,
            UUID transactionId) {
        Target target = requireTarget(context);
        target.receipts().aco$removeTerminalNativeBatchReceipt(
                transactionId);
    }

    @Override
    public void forgetResolvedTarget(
            ServerLevel level,
            BatchTransactionRecord record) {
        if (level.getBlockEntity(record.targetPos())
                instanceof NativeBatchReceiptStore receipts) {
            receipts.aco$removeTerminalNativeBatchReceipt(
                    record.id());
        }
    }

    private static CraftingTableBatchRequest createRequest(
            PatternBatchContext context,
            PreparedPatternBatch prepared,
            String payloadDigest) {
        List<GenericStack> outputsPerExecution =
                LongBatchStackMath.fromCounter(
                        context.copyOutputsPerExecution());
        List<GenericStack> remainingPerExecution =
                LongBatchStackMath.fromCounter(
                        context.copyRemainingOutputsPerExecution());
        BigInteger executions =
                BigInteger.valueOf(
                        prepared.offeredExecutions());
        return new CraftingTableBatchRequest(
                prepared.transactionId(),
                prepared.transactionId(),
                context.craftingJobId(),
                payloadDigest,
                0,
                CraftingTableBatchMode.AE2_JOB,
                context.pattern(),
                executions,
                context.copyInputsPerExecution(),
                exactSlotInputs(
                        context.copyInputsPerExecution(),
                        executions),
                outputsPerExecution,
                remainingPerExecution,
                exactTotals(
                        prepared.expectedOutputs()));
    }

    private static PatternBatchCommit acceptedCommit(
            PatternBatchContext context,
            PreparedPatternBatch prepared,
            String payloadDigest) {
        CompoundTag data = prepared.adapterData();
        data.putString("payloadDigest", payloadDigest);
        BatchOwnershipProof proof = new BatchOwnershipProof(
                prepared.transactionId(),
                prepared.offeredExecutions(),
                payloadDigest,
                "AAC Pattern Bus "
                        + context.target().getBlockPos());
        return new PatternBatchCommit(
                prepared.offeredExecutions(),
                "AAC worker accepted one physical crafting-table batch",
                data,
                proof);
    }

    private static PatternBatchCommit rejectedCommit() {
        return new PatternBatchCommit(
                0L,
                "AAC worker rejected the crafting-table batch",
                new CompoundTag());
    }

    private static void validateExisting(
            NativeBatchReceipt receipt,
            PreparedPatternBatch prepared,
            String patternFingerprint,
            String payloadDigest) {
        if (receipt.executions()
                        != prepared.offeredExecutions()
                || !receipt.patternFingerprint()
                        .equals(patternFingerprint)
                || !receipt.payloadDigest()
                        .equals(payloadDigest)) {
            throw new IllegalStateException(
                    "AAC transaction id was reused with different batch data");
        }
    }

    private static Target requireTarget(
            PatternBatchContext context) {
        if (!(context.target()
                        instanceof CraftingTableBatchTarget patternBus)
                || !(context.target()
                        instanceof NativeBatchReceiptStore receipts)) {
            throw new IllegalStateException(
                    "AAC Pattern Bus batch mixins are missing");
        }
        return new Target(patternBus, receipts);
    }

    private record Target(
            CraftingTableBatchTarget patternBus,
            NativeBatchReceiptStore receipts) {
    }

    private static List<ExactStack> exactSlotInputs(
            appeng.api.stacks.KeyCounter[] inputs,
            BigInteger executions) {
        List<ExactStack> result =
                new ArrayList<>();
        // slot境界を維持し、同一素材九枠も九要素のままWorkerへ渡す。
        for (appeng.api.stacks.KeyCounter slot :
                inputs) {
            // 一つの確定slotに含まれる各キーへ同じ実行係数を掛ける。
            for (var entry : slot) {
                result.add(
                        new ExactStack(
                                entry.getKey(),
                                BigInteger.valueOf(
                                                entry.getLongValue())
                                        .multiply(
                                                executions)));
            }
        }
        return List.copyOf(result);
    }

    private static Map<appeng.api.stacks.AEKey, BigInteger>
            exactTotals(
                    List<GenericStack> stacks) {
        Map<appeng.api.stacks.AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // Prepared Batchの主出力と返却物をキー別BigInteger合計へ変換する。
        for (GenericStack stack : stacks) {
            result.merge(
                    stack.what(),
                    BigInteger.valueOf(
                            stack.amount()),
                    BigInteger::add);
        }
        return Map.copyOf(result);
    }
}
