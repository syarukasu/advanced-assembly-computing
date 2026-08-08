package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Neo ECO's existing lifecycle wakeup without replacing its scheduler. */
@Mixin(value = ECOCraftingWorkerBlockEntity.class, remap = false)
public interface ECOCraftingWorkerBatchAccessor {
    @Invoker("wakeTickingDevice")
    void aac$invokeWakeTickingDevice();
}
