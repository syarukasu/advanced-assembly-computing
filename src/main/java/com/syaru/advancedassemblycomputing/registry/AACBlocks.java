package com.syaru.advancedassemblycomputing.registry;

import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.block.VectorCraftingControllerBlock;
import com.syaru.advancedassemblycomputing.block.VectorCraftingParallelCoreBlock;
import com.syaru.advancedassemblycomputing.block.VectorCraftingWorkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AACBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AdvancedAssemblyComputing.MOD_ID);

    public static final RegistryObject<VectorCraftingControllerBlock> VECTOR_CRAFTING_CONTROLLER =
            BLOCKS.register(
                    "vector_crafting_controller",
                    () -> new VectorCraftingControllerBlock(machineProperties()));

    public static final RegistryObject<VectorCraftingParallelCoreBlock> VECTOR_CRAFTING_PARALLEL_CORE =
            BLOCKS.register(
                    "vector_crafting_parallel_core",
                    () -> new VectorCraftingParallelCoreBlock(machineProperties()));

    public static final RegistryObject<VectorCraftingWorkerBlock> VECTOR_CRAFTING_WORKER =
            BLOCKS.register(
                    "vector_crafting_worker",
                    () -> new VectorCraftingWorkerBlock(machineProperties()));

    private AACBlocks() {
    }

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
