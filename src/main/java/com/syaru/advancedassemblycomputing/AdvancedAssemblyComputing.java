package com.syaru.advancedassemblycomputing;

import com.mojang.logging.LogUtils;
import com.syaru.advancedassemblycomputing.config.AACConfig;
import com.syaru.advancedassemblycomputing.integration.AACIntegrationBootstrap;
import com.syaru.advancedassemblycomputing.multiblock.AACMultiBlocks;
import com.syaru.advancedassemblycomputing.registry.AACBlockEntities;
import com.syaru.advancedassemblycomputing.registry.AACBlocks;
import com.syaru.advancedassemblycomputing.registry.AACCreativeTabs;
import com.syaru.advancedassemblycomputing.registry.AACItems;
import com.syaru.advancedassemblycomputing.registry.AACTiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AdvancedAssemblyComputing.MOD_ID)
public final class AdvancedAssemblyComputing {
    public static final String MOD_ID = "advanced_assembly_computing";
    public static final Logger LOGGER = LogUtils.getLogger();
    public AdvancedAssemblyComputing() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        AACBlocks.register(modBus);
        AACItems.register(modBus);
        AACBlockEntities.register(modBus);
        AACCreativeTabs.register(modBus);
        AACTiers.register(modBus);
        modBus.addListener(this::commonSetup);

        // Common Configを使い、専用サーバーとクライアントで同じconfigフォルダ構成にする。
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                AACConfig.SPEC,
                "advanced_assembly_computing-common.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AACBlockEntities.bindBlocks();
            AACMultiBlocks.initialize();
            AACIntegrationBootstrap.initialize();
            LOGGER.info(
                    "Advanced Assembly Computing initialized: physicalThreadsPerWorker={}, maximumExecutionsPerWave={}, progressPerTick={}, powerMultiplier={}",
                    AACConfig.physicalThreadsPerWorker(),
                    AACConfig.maximumCraftingTableBatchExecutions(),
                    AACConfig.progressPerTick(),
                    AACConfig.powerMultiplier());
        });
    }
}
