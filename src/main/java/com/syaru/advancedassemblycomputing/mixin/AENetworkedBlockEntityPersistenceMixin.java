package com.syaru.advancedassemblycomputing.mixin;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.syaru.advancedassemblycomputing.execution.AACPatternBusPersistentState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1のPattern Busが継承するAE2保存経路へ、AAC固有台帳だけを追加する。
 */
@Mixin(value = AENetworkedBlockEntity.class, remap = false)
public abstract class AENetworkedBlockEntityPersistenceMixin {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void aac$savePatternBusBatchState(
            CompoundTag data,
            HolderLookup.Provider registries,
            CallbackInfo callbackInfo) {
        // AACの台帳を持たない通常AE2 Block Entityには一切処理を加えない。
        if (!((Object) this instanceof AACPatternBusPersistentState persistentState)) {
            return;
        }
        persistentState.aac$savePatternBusBatchState(data, registries);
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void aac$loadPatternBusBatchState(
            CompoundTag data,
            HolderLookup.Provider registries,
            CallbackInfo callbackInfo) {
        // AACの台帳を持たない通常AE2 Block Entityには一切処理を加えない。
        if (!((Object) this instanceof AACPatternBusPersistentState persistentState)) {
            return;
        }
        persistentState.aac$loadPatternBusBatchState(data, registries);
    }
}
