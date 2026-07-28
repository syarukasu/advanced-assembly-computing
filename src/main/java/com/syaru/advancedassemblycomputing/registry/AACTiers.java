package com.syaru.advancedassemblycomputing.registry;

import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.IECOTier;
import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.tier.AACTier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AACTiers {
    private static final DeferredRegister<IECOTier> TIERS =
            DeferredRegister.create(NERegistries.Keys.ECO_TIER, AdvancedAssemblyComputing.MOD_ID);

    public static final RegistryObject<IECOTier> VECTOR =
            TIERS.register("vector", () -> AACTier.VECTOR);

    private AACTiers() {
    }

    public static void register(IEventBus bus) {
        TIERS.register(bus);
    }
}
