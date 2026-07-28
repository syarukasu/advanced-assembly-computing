package com.syaru.advancedassemblycomputing.registry;

import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.item.GlintBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AACItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AdvancedAssemblyComputing.MOD_ID);

    public static final RegistryObject<Item> VECTOR_CRAFTING_CONTROLLER = ITEMS.register(
            "vector_crafting_controller",
            () -> new GlintBlockItem(
                    AACBlocks.VECTOR_CRAFTING_CONTROLLER.get(),
                    new Item.Properties().rarity(Rarity.EPIC),
                    GlintBlockItem.Role.CONTROLLER));

    public static final RegistryObject<Item> VECTOR_CRAFTING_PARALLEL_CORE = ITEMS.register(
            "vector_crafting_parallel_core",
            () -> new GlintBlockItem(
                    AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get(),
                    new Item.Properties().rarity(Rarity.EPIC),
                    GlintBlockItem.Role.PARALLEL_CORE));

    public static final RegistryObject<Item> VECTOR_CRAFTING_WORKER = ITEMS.register(
            "vector_crafting_worker",
            () -> new GlintBlockItem(
                    AACBlocks.VECTOR_CRAFTING_WORKER.get(),
                    new Item.Properties().rarity(Rarity.EPIC),
                    GlintBlockItem.Role.WORKER));

    private AACItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
