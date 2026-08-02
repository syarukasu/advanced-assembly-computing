package com.syaru.advancedassemblycomputing.blockentity;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.registry.AACBlockEntities;
import com.syaru.advancedassemblycomputing.tier.AACTier;
import com.syaru.advancedassemblycomputing.util.VectorBatchMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** NeoECOAE 21.1.1のFast Pathを保ったまま、AAC設備の実行枠だけを拡張するController。 */
public final class VectorCraftingControllerBlockEntity extends ECOCraftingSystemBlockEntity {
    // NeoECOAEの最大オーバークロック段数。1.21.1本体の計算上限と同じ値に固定する。
    private static final int NEO_ECO_MAX_OVERCLOCK_LEVEL = 9;

    public VectorCraftingControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AACBlockEntities.VECTOR_CRAFTING_CONTROLLER.get(), pos, state, AACTier.VECTOR);
    }

    public int getVectorCapacity() {
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getThreadCount();
        }
        return VectorBatchMath.capacity(
                getWorkerCount(),
                AACConfig.physicalThreadsPerWorker());
    }

    @Override
    public int getThreadCount() {
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getThreadCount();
        }
        return getVectorCapacity();
    }

    @Override
    public int getThreadCountPerWorker() {
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getThreadCountPerWorker();
        }
        return AACConfig.physicalThreadsPerWorker();
    }

    @Override
    public boolean isOverclocked() {
        return AACConfig.vectorCraftingEnabled() || super.isOverclocked();
    }

    @Override
    public boolean isActiveCooling() {
        return !AACConfig.vectorCraftingEnabled() && super.isActiveCooling();
    }

    @Override
    public int getEffectiveOverclockTimes() {
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getEffectiveOverclockTimes();
        }
        return NEO_ECO_MAX_OVERCLOCK_LEVEL;
    }

    @Override
    public int getOverlockTimes() {
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getOverlockTimes();
        }
        return NEO_ECO_MAX_OVERCLOCK_LEVEL;
    }

    @Override
    public boolean tryConsumeCoolant(int amount, int requiredOverclock) {
        if (AACConfig.vectorCraftingEnabled()) {
            return true;
        }
        return super.tryConsumeCoolant(amount, requiredOverclock);
    }

    @Override
    public int getCraftingCoolantCraftLimit(
            int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!AACConfig.vectorCraftingEnabled()) {
            return super.getCraftingCoolantCraftLimit(
                    coolantPerCraft,
                    requiredOverclock,
                    requestedCrafts);
        }
        return Math.max(0, requestedCrafts);
    }
}
