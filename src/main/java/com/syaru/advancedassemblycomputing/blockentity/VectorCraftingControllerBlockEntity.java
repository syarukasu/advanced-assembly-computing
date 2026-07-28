package com.syaru.advancedassemblycomputing.blockentity;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.multiblock.AACMultiBlocks;
import com.syaru.advancedassemblycomputing.registry.AACBlockEntities;
import com.syaru.advancedassemblycomputing.tier.AACTier;
import com.syaru.advancedassemblycomputing.util.VectorBatchMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class VectorCraftingControllerBlockEntity extends ECOCraftingSystemBlockEntity {
    // Neo ECO 20.3.0の最上位L9が公開するオーバークロック段数。
    private static final int NEO_ECO_MAX_OVERCLOCK_LEVEL = 9;
    public VectorCraftingControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AACBlockEntities.VECTOR_CRAFTING_CONTROLLER.get(), pos, state, AACTier.VECTOR);
    }

    public int getVectorCapacity() {
        // Config OFFでは同じ構造をNeo ECO L9相当の計算へ完全に戻す。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getMaxInFlightCrafts();
        }
        return VectorBatchMath.capacity(
                super.getStructureBuildLength(),
                AACConfig.physicalThreadsPerWorker());
    }

    @Override
    public int getMaxInFlightCrafts() {
        return getVectorCapacity();
    }

    @Override
    public int getCurrentBatchSlots() {
        // Config OFFではNeo ECOが管理する通常の空きslot数を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getCurrentBatchSlots();
        }
        return VectorBatchMath.available(getVectorCapacity(), super.getRunningThreadCount());
    }

    @Override
    public int getThreadCount() {
        // Config OFFではNeo ECOの並列コア由来thread数を表示する。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getThreadCount();
        }
        return getVectorCapacity();
    }

    @Override
    public int getThreadCountPerWorker() {
        // Config OFFではNeo ECOの標準worker容量へ戻す。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getThreadCountPerWorker();
        }
        return AACConfig.physicalThreadsPerWorker();
    }

    @Override
    public int getAvailableThreads() {
        // Config OFFではNeo ECOの標準空きthread計算を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getAvailableThreads();
        }
        return VectorBatchMath.available(
                getVectorCapacity(),
                super.getRunningThreadCount());
    }

    @Override
    public int getOverflowThreads() {
        // Config OFFではNeo ECOが算出したworker不足分をそのまま表示する。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getOverflowThreads();
        }
        return 0;
    }

    @Override
    public boolean isOverclocked() {
        return AACConfig.vectorCraftingEnabled()
                || super.isOverclocked();
    }

    @Override
    public boolean isActiveCooling() {
        return !AACConfig.vectorCraftingEnabled()
                && super.isActiveCooling();
    }

    @Override
    public void toggleOverclocked() {
        // Vector実行中は性能条件を固定し、UI操作で会計上限が途中変更されることを防ぐ。
        if (!AACConfig.vectorCraftingEnabled()) {
            super.toggleOverclocked();
        }
    }

    @Override
    public void toggleActiveCooling() {
        // 1億slot分の冷却材をintバッファへ要求すると飽和するため、Vector経路では冷却を使わない。
        if (!AACConfig.vectorCraftingEnabled()) {
            super.toggleActiveCooling();
        }
    }

    @Override
    public int getEffectiveOverclockTimes() {
        // Config OFFでは冷却状態を含むNeo ECOの有効段数を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getEffectiveOverclockTimes();
        }
        return NEO_ECO_MAX_OVERCLOCK_LEVEL;
    }

    @Override
    public int getOverlockTimes() {
        // Config OFFではNeo ECOが構造から計算した段数を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getOverlockTimes();
        }
        return NEO_ECO_MAX_OVERCLOCK_LEVEL;
    }

    @Override
    public int getProgressPerTick() {
        // Config OFFではNeo ECOの通常進捗速度を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getProgressPerTick();
        }
        return AACConfig.progressPerTick();
    }

    @Override
    public int getTheoreticalCraftTicks() {
        // Config OFFではNeo ECOの通常理論tick数を表示する。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getTheoreticalCraftTicks();
        }
        int progress = Math.max(1, getProgressPerTick());
        return (ECOCraftingThread.MAX_PROGRESS + progress - 1) / progress;
    }

    @Override
    public int getCraftingPowerMultiplier() {
        // Config OFFではNeo ECOの通常電力倍率を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getCraftingPowerMultiplier();
        }
        return AACConfig.powerMultiplier();
    }

    @Override
    public long getCurrentEnergyPerTick() {
        // Config OFFではNeo ECOの通常消費電力表示を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getCurrentEnergyPerTick();
        }
        return (long) super.getRunningThreadCount() * getProgressPerTick() * getCraftingPowerMultiplier();
    }

    @Override
    public double getEnergyMultiplier() {
        return getCraftingPowerMultiplier();
    }

    @Override
    public double getTimeMultiplier() {
        // Config OFFではNeo ECOの構造依存時間倍率を使う。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getTimeMultiplier();
        }
        // Neo ECOの標準工程は10tickなので、1tick実行は0.1倍時間として表示する。
        return getTheoreticalCraftTicks() / 10.0D;
    }

    @Override
    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        return AACConfig.vectorCraftingEnabled()
                || super.tryConsumeCoolant(
                        amount,
                        requiredOverclock);
    }

    @Override
    public int getCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        // Config OFFではNeo ECOの冷却材残量による実行上限を守る。
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getCraftingCoolantCraftLimit(coolantPerCraft, requiredOverclock, requestedCrafts);
        }
        return Math.max(0, requestedCrafts);
    }

    @Override
    public MultiBlockDefinition getBuildDefinition() {
        return AACMultiBlocks.craftingSystem();
    }
}
