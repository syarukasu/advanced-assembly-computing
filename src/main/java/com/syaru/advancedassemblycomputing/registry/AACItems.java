package com.syaru.advancedassemblycomputing.registry;

import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.item.GlintBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AACItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AdvancedAssemblyComputing.MOD_ID);

    public static final DeferredHolder<Item, Item> VECTOR_CRAFTING_CONTROLLER = ITEMS.register(
            "vector_crafting_controller",
            () -> new GlintBlockItem(
                    AACBlocks.VECTOR_CRAFTING_CONTROLLER.get(),
                    new Item.Properties().rarity(Rarity.EPIC),
                    GlintBlockItem.Role.CONTROLLER));

    public static final DeferredHolder<Item, Item> VECTOR_CRAFTING_PARALLEL_CORE = ITEMS.register(
            "vector_crafting_parallel_core",
            () -> new GlintBlockItem(
                    AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get(),
                    new Item.Properties().rarity(Rarity.EPIC),
                    GlintBlockItem.Role.PARALLEL_CORE));

    public static final DeferredHolder<Item, Item> VECTOR_CRAFTING_WORKER = ITEMS.register(
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
