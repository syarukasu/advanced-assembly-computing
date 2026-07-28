package com.syaru.advancedassemblycomputing.blockentity;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.registry.AACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class VectorCraftingWorkerBlockEntity extends ECOCraftingWorkerBlockEntity {
    public VectorCraftingWorkerBlockEntity(BlockPos pos, BlockState state) {
        super(AACBlockEntities.VECTOR_CRAFTING_WORKER.get(), pos, state);
    }
}
