package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ACOのACK/取消通知がNeoECOの待機Tickを直接起こすための最小Invoker。 */
@Mixin(value = ECOCraftingWorkerBlockEntity.class, remap = false)
public interface ECOCraftingWorkerBatchAccessor {
    @Invoker("wakeTickingDevice")
    void aac$invokeWakeTickingDevice();
}
