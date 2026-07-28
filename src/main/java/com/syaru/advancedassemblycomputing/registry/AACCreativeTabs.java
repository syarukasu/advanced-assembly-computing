package com.syaru.advancedassemblycomputing.registry;

import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AACCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AdvancedAssemblyComputing.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.advanced_assembly_computing"))
                    .icon(() -> AACItems.VECTOR_CRAFTING_CONTROLLER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(AACItems.VECTOR_CRAFTING_CONTROLLER.get());
                        output.accept(AACItems.VECTOR_CRAFTING_PARALLEL_CORE.get());
                        output.accept(AACItems.VECTOR_CRAFTING_WORKER.get());
                    })
                    .build());

    private AACCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
