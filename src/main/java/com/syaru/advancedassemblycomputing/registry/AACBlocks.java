package com.syaru.advancedassemblycomputing.registry;

import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.block.VectorCraftingControllerBlock;
import com.syaru.advancedassemblycomputing.block.VectorCraftingParallelCoreBlock;
import com.syaru.advancedassemblycomputing.block.VectorCraftingWorkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AACBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AdvancedAssemblyComputing.MOD_ID);

    public static final DeferredHolder<Block, VectorCraftingControllerBlock> VECTOR_CRAFTING_CONTROLLER =
            BLOCKS.register(
                    "vector_crafting_controller",
                    () -> new VectorCraftingControllerBlock(machineProperties()));

    public static final DeferredHolder<Block, VectorCraftingParallelCoreBlock> VECTOR_CRAFTING_PARALLEL_CORE =
            BLOCKS.register(
                    "vector_crafting_parallel_core",
                    () -> new VectorCraftingParallelCoreBlock(machineProperties()));

    public static final DeferredHolder<Block, VectorCraftingWorkerBlock> VECTOR_CRAFTING_WORKER =
            BLOCKS.register(
                    "vector_crafting_worker",
                    () -> new VectorCraftingWorkerBlock(machineProperties()));

    private AACBlocks() {
    }

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
