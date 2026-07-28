package com.syaru.advancedassemblycomputing.mixin;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingParallelCoreBlockEntity;
import com.syaru.advancedassemblycomputing.registry.AACBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NECraftingClusterCalculator.class, remap = false)
public abstract class NECraftingClusterCalculatorMixin {
    @Unique
    private boolean aac$validatingVectorStructure;

    @Inject(
            method = "verifyInternalStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"))
    private void aac$detectVectorController(
            ServerLevel level,
            BlockPos min,
            BlockPos max,
            boolean mirrored,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        aac$validatingVectorStructure = false;

        // 検証対象の小さな外接領域からAAC Controllerだけを探し、通常Neo ECO構造と判定を分離する。
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            // AAC Controllerが一つでもあれば、この検証だけ上位構成部品を要求する。
            if (level.getBlockEntity(pos) instanceof VectorCraftingControllerBlockEntity) {
                aac$validatingVectorStructure = true;
                return;
            }
        }
    }

    @Inject(
            method = "verifyInternalStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("RETURN"))
    private void aac$clearVectorValidationState(
            ServerLevel level,
            BlockPos min,
            BlockPos max,
            boolean mirrored,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        aac$validatingVectorStructure = false;
    }

    @Redirect(
            method = "verifyInternalStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcn/dancingsnow/neoecoae/multiblock/calculator/NECraftingClusterCalculator;matchingStateFacing(Lcom/tterrag/registrate/util/entry/BlockEntry;Lnet/minecraft/core/Direction;)Ljava/util/function/BiPredicate;",
                    ordinal = 0))
    private BiPredicate<BlockState, BlockPos> aac$selectWorkerPredicate(
            BlockEntry<? extends Block> originalWorker,
            Direction expectedFacing) {
        // AAC構造では、元Workerを混ぜて上位性能だけ得る構成を拒否する。
        if (aac$validatingVectorStructure) {
            return (state, pos) -> state.is(AACBlocks.VECTOR_CRAFTING_WORKER.get())
                    && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == expectedFacing;
        }
        return (state, pos) -> state.is(originalWorker.get())
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == expectedFacing;
    }

    @Inject(
            method = "matchingParallelCore(Lnet/minecraft/world/level/Level;Lcn/dancingsnow/neoecoae/api/IECOTier;Lnet/minecraft/core/Direction;)Ljava/util/function/BiPredicate;",
            at = @At("HEAD"),
            cancellable = true)
    private void aac$selectParallelCorePredicate(
            Level level,
            IECOTier tier,
            Direction expectedFacing,
            CallbackInfoReturnable<BiPredicate<BlockState, BlockPos>> callbackInfo) {
        // 通常Neo ECO構造のTier互換判定は一切変更しない。
        if (!aac$validatingVectorStructure) {
            return;
        }
        callbackInfo.setReturnValue((state, pos) -> state.is(AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get())
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == expectedFacing
                && level.getBlockEntity(pos) instanceof VectorCraftingParallelCoreBlockEntity);
    }
}
