package com.syaru.advancedassemblycomputing.multiblock;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.syaru.advancedassemblycomputing.registry.AACBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class AACMultiBlocks {
    private static volatile MultiBlockDefinition craftingSystem;

    private AACMultiBlocks() {
    }

    public static synchronized void initialize() {
        // Common Setupが重複通知されても同じ定義を二重登録しない。
        if (craftingSystem != null) {
            return;
        }

        BlockState casing = NEBlocks.CRAFTING_CASING.getDefaultState();
        // 座標・反復方向・最大長はNeo ECO 20.3.0のL9設計図をそのまま保つ。
        craftingSystem = MultiBlockDefinition.builder(
                        AACBlocks.VECTOR_CRAFTING_CONTROLLER.get().builtInRegistryHolder())
                .setBlock(pos(1, 1, 0), AACBlocks.VECTOR_CRAFTING_CONTROLLER.get().defaultBlockState())
                .setBlock(pos(1, 0, 0), casing)
                .setBlock(pos(2, 0, 0), casing)
                .setBlock(pos(2, 1, 0), casing)
                .setBlock(pos(1, 2, 0), casing)
                .setBlock(pos(2, 2, 0), casing)
                .setBlock(pos(1, 0, 1), casing)
                .setBlock(pos(2, 0, 1), NEBlocks.OUTPUT_HATCH.getDefaultState())
                .setBlock(pos(2, 1, 1), NEBlocks.CRAFTING_INTERFACE.getDefaultState())
                .setBlock(pos(1, 1, 1), casing)
                .setBlock(pos(1, 2, 1), casing)
                .setBlock(pos(2, 2, 1), NEBlocks.INPUT_HATCH.getDefaultState())
                .setBlock(pos(0, 0, 0), casing)
                .setBlock(pos(0, 1, 0), casing)
                .setBlock(pos(0, 2, 0), casing)
                .setBlock(pos(0, 0, 1), casing)
                .setBlock(pos(0, 1, 1), casing)
                .setBlock(pos(0, 2, 1), casing)
                .setBlockRepeatable(
                        pos(-1, 1, 0),
                        Direction.WEST,
                        AACBlocks.VECTOR_CRAFTING_WORKER.get().defaultBlockState())
                .setBlockRepeatable(
                        pos(-1, 2, 0),
                        Direction.WEST,
                        AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get().defaultBlockState())
                .setBlockRepeatable(
                        pos(-1, 0, 0),
                        Direction.WEST,
                        AACBlocks.VECTOR_CRAFTING_PARALLEL_CORE.get().defaultBlockState())
                .setBlockRepeatable(
                        pos(-1, 0, 1),
                        Direction.WEST,
                        NEBlocks.CRAFTING_PATTERN_BUS
                                .getDefaultState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH))
                .setBlockRepeatable(
                        pos(-1, 1, 1),
                        Direction.WEST,
                        NEBlocks.CRAFTING_VENT
                                .getDefaultState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH))
                .setBlockRepeatable(
                        pos(-1, 2, 1),
                        Direction.WEST,
                        NEBlocks.CRAFTING_PATTERN_BUS
                                .getDefaultState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH))
                .setBlockWithRepeatShifted(pos(-1, 1, 0), Direction.WEST, 0, casing)
                .setBlockWithRepeatShifted(pos(-1, 2, 0), Direction.WEST, 0, casing)
                .setBlockWithRepeatShifted(pos(-1, 0, 0), Direction.WEST, 0, casing)
                .setBlockWithRepeatShifted(pos(-1, 0, 1), Direction.WEST, 0, casing)
                .setBlockWithRepeatShifted(pos(-1, 1, 1), Direction.WEST, 0, casing)
                .setBlockWithRepeatShifted(pos(-1, 2, 1), Direction.WEST, 0, casing)
                .expandMin(1)
                .expandMax(NEConfig.craftingSystemMaxLength - 4)
                .onFormed((blockPos, level) -> {
                    BlockState state = level.getBlockState(blockPos);
                    // Neo ECOの形成表示を持つ構成ブロックだけをformedへ切り替える。
                    if (state.hasProperty(NEBlock.FORMED)) {
                        state = state.setValue(NEBlock.FORMED, true);
                    }
                    // Controller周囲のCasingだけをNeo ECO本来の非表示状態へ切り替える。
                    if (state.hasProperty(ECOMachineCasing.INVISIBLE)) {
                        Vec3 blockCenter = blockPos.getCenter();
                        Vec3 controllerCenter = new Vec3(1.5D, 1.5D, 0.5D);
                        boolean hideCasing = blockCenter.distanceToSqr(controllerCenter) <= 3.0D;
                        state = state.setValue(ECOMachineCasing.INVISIBLE, hideCasing);
                    }
                    level.setBlockAndUpdate(blockPos, state);
                })
                .create();
    }

    public static MultiBlockDefinition craftingSystem() {
        // Common Setupより前のデータ参照だけはNeo ECO L9定義へ安全に退避する。
        if (craftingSystem == null) {
            return cn.dancingsnow.neoecoae.all.NEMultiBlocks.CRAFTING_SYSTEM_L9;
        }
        return craftingSystem;
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
