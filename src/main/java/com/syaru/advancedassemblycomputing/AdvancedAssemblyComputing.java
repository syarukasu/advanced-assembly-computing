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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(AdvancedAssemblyComputing.MOD_ID)
public final class AdvancedAssemblyComputing {
    public static final String MOD_ID = "advanced_assembly_computing";
    public static final Logger LOGGER = LogUtils.getLogger();
    public AdvancedAssemblyComputing(IEventBus modBus, ModContainer container) {
        AACBlocks.register(modBus);
        AACItems.register(modBus);
        AACBlockEntities.register(modBus);
        AACCreativeTabs.register(modBus);
        AACTiers.register(modBus);
        modBus.addListener(this::commonSetup);

        // Common Configを使い、専用サーバーとクライアントで同じconfigフォルダ構成にする。
        container.registerConfig(
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
