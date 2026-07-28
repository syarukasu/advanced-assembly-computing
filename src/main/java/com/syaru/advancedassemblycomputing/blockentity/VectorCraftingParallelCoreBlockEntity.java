package com.syaru.advancedassemblycomputing.blockentity;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import com.syaru.advancedassemblycomputing.registry.AACBlockEntities;
import com.syaru.advancedassemblycomputing.tier.AACTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class VectorCraftingParallelCoreBlockEntity extends ECOCraftingParallelCoreBlockEntity {
    public VectorCraftingParallelCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AACBlockEntities.VECTOR_CRAFTING_PARALLEL_CORE.get(), pos, state, AACTier.VECTOR);
    }
}
