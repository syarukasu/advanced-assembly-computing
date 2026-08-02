package com.syaru.advancedassemblycomputing.registry;

import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.syaru.advancedassemblycomputing.AdvancedAssemblyComputing;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingControllerBlockEntity;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingParallelCoreBlockEntity;
import com.syaru.advancedassemblycomputing.blockentity.VectorCraftingWorkerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AACBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AdvancedAssemblyComputing.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ECOCraftingSystemBlockEntity>> VECTOR_CRAFTING_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "vector_crafting_controller",
                    () -> BlockEntityType.Builder.<ECOCraftingSystemBlockEntity>of(
                                    VectorCraftingControllerBlockEntity::new,
                                    AACBlocks.VECTOR_CRAFTING_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ECOCraftingParallelCoreBlockEntity>>
            VECTOR_CRAFTING_PARALLEL_CORE = BLOCK_ENTITIES.register(
                    "vector_crafting_parallel_core",
                    () -> BlockEntityType.Builder.<ECOCraftingParallelCoreBlockEntity>of(
                                    VectorCraftingParallelCoreBlockEntity::new,
                                    AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ECOCraftingWorkerBlockEntity>> VECTOR_CRAFTING_WORKER =
            BLOCK_ENTITIES.register(
                    "vector_crafting_worker",
                    () -> BlockEntityType.Builder.<ECOCraftingWorkerBlockEntity>of(
                                    VectorCraftingWorkerBlockEntity::new,
                                    AACBlocks.VECTOR_CRAFTING_WORKER.get())
                            .build(null));

    private AACBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    public static void bindBlocks() {
        AACBlocks.VECTOR_CRAFTING_CONTROLLER
                .get()
                .setBlockEntity(
                        ECOCraftingSystemBlockEntity.class,
                        VECTOR_CRAFTING_CONTROLLER.get(),
                        null,
                        (level, pos, state, blockEntity) -> blockEntity.tick(level, pos, state));
        AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE
                .get()
                .setBlockEntity(
                        ECOCraftingParallelCoreBlockEntity.class,
                        VECTOR_CRAFTING_PARALLEL_CORE.get(),
                        null,
                        null);
        AACBlocks.VECTOR_CRAFTING_WORKER
                .get()
                .setBlockEntity(
                        ECOCraftingWorkerBlockEntity.class,
                        VECTOR_CRAFTING_WORKER.get(),
                        null,
                        null);

        AEBaseBlockEntity.registerBlockEntityItem(
                VECTOR_CRAFTING_CONTROLLER.get(),
                AACItems.VECTOR_CRAFTING_CONTROLLER.get());
        AEBaseBlockEntity.registerBlockEntityItem(
                VECTOR_CRAFTING_PARALLEL_CORE.get(),
                AACItems.VECTOR_CRAFTING_PARALLEL_CORE.get());
        AEBaseBlockEntity.registerBlockEntityItem(
                VECTOR_CRAFTING_WORKER.get(),
                AACItems.VECTOR_CRAFTING_WORKER.get());
    }
}
