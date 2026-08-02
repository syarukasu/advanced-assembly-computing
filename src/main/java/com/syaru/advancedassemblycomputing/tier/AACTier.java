package com.syaru.advancedassemblycomputing.tier;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Neo ECOの構造互換性はL9を維持し、実行性能だけをAACのController側で拡張するTier。
 */
public enum AACTier implements IECOTier {
    VECTOR;

    @Override
    public int getTier() {
        // Neo ECOのUI・構造定義が理解できる最上位番号は3（L9）。
        return ECOTier.L9.getTier();
    }

    @Override
    public int getCrafterParallel() {
        return ECOTier.L9.getCrafterParallel();
    }

    @Override
    public int getOverclockedCrafterParallel() {
        return ECOTier.L9.getOverclockedCrafterParallel();
    }

    @Override
    public int getOverclockedCrafterQueueMultiply() {
        return ECOTier.L9.getOverclockedCrafterQueueMultiply();
    }

    @Override
    public int getOverclockedCrafterPowerMultiply() {
        /*
         * NeoECO Workerが実際に参照する電力倍率はTier由来。
         * Controller表示だけを書き換えず、実抽出値とConfigを同じ値へ揃える。
         */
        return AACConfig.powerMultiplier();
    }

    @Override
    public int getCPUAccelerators() {
        return ECOTier.L9.getCPUAccelerators();
    }

    @Override
    public int getCPUThreads() {
        return ECOTier.L9.getCPUThreads();
    }

    @Override
    public long getCPUTotalBytes() {
        return ECOTier.L9.getCPUTotalBytes();
    }

    @Override
    public long getStorageTotalBytes() {
        return ECOTier.L9.getStorageTotalBytes();
    }

    @Override
    public long getPowerStorageSize() {
        return ECOTier.L9.getPowerStorageSize();
    }

    @Override
    public ResourceLocation getCPUOverlayTexture() {
        return ECOTier.L9.getCPUOverlayTexture();
    }

    @Override
    public IGuiTexture getCraftingOverlayTexture() {
        return ECOTier.L9.getCraftingOverlayTexture();
    }

    @Override
    public String toString() {
        return "VECTOR";
    }
}
