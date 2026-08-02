package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.multiblock.AACMultiBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoECOの自動建築とローカルプレビューへ、AAC専用の構成定義を返す。
 */
@Mixin(value = ECOCraftingSystemBlockEntity.class, remap = false)
public abstract class ECOCraftingSystemBuildDefinitionMixin {
    @Inject(
            method = "getBuildDefinition()Lcn/dancingsnow/neoecoae/multiblock/definition/MultiBlockDefinition;",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$useVectorBuildDefinition(
            CallbackInfoReturnable<MultiBlockDefinition> callbackInfo) {
        // 通常のNeoECO Controllerには親MODのTier別定義をそのまま使わせる。
        if (!((Object) this instanceof VectorCraftingControllerBlockEntity)) {
            return;
        }
        callbackInfo.setReturnValue(AACMultiBlocks.craftingSystem());
    }
}
