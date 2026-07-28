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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AACBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AdvancedAssemblyComputing.MOD_ID);

    public static final RegistryObject<BlockEntityType<ECOCraftingSystemBlockEntity>> VECTOR_CRAFTING_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "vector_crafting_controller",
                    () -> BlockEntityType.Builder.<ECOCraftingSystemBlockEntity>of(
                                    VectorCraftingControllerBlockEntity::new,
                                    AACBlocks.VECTOR_CRAFTING_CONTROLLER.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<ECOCraftingParallelCoreBlockEntity>>
            VECTOR_CRAFTING_PARALLEL_CORE = BLOCK_ENTITIES.register(
                    "vector_crafting_parallel_core",
                    () -> BlockEntityType.Builder.<ECOCraftingParallelCoreBlockEntity>of(
                                    VectorCraftingParallelCoreBlockEntity::new,
                                    AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<ECOCraftingWorkerBlockEntity>> VECTOR_CRAFTING_WORKER =
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
