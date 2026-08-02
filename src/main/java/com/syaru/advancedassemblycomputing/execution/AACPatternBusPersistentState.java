package com.syaru.advancedassemblycomputing.execution;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Pattern BusへMixinしたBatch台帳を、AE2のBlock Entity保存処理から読み書きする境界。
 */
public interface AACPatternBusPersistentState {
    void aac$savePatternBusBatchState(
            CompoundTag data,
            HolderLookup.Provider registries);

    void aac$loadPatternBusBatchState(
            CompoundTag data,
            HolderLookup.Provider registries);
}
