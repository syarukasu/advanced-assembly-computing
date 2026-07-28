package com.syaru.advancedassemblycomputing.mixin;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** NeoECOの実Thread開始・終了・冷却材処理だけをAACへ公開する。 */
@Mixin(value = ECOCraftingThread.class, remap = false)
public interface ECOCraftingThreadBatchAccessor {
    @Invoker("startWork")
    void aac$invokeStartWork(
            List<GenericStack> outputs,
            List<GenericStack> inputs,
            List<GenericStack> remaining,
            UUID craftingJobId,
            int occupiedThreadSlots);

    /** BigInteger代表仕事をMEへ返却せず、Thread占有だけ解放する。 */
    @Invoker("clearWork")
    void aac$invokeClearWork();

    /** 一つの物理仕事としてNeoECO本来の冷却材条件を検査・消費する。 */
    @Invoker("consumeCraftingCoolant")
    boolean aac$invokeConsumeCraftingCoolant(
            ECOCraftingSystemBlockEntity controller,
            int physicalCraftCount);
}
