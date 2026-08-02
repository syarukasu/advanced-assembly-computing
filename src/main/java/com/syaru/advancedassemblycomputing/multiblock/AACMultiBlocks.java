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
    /** NeoECO 21.1.1のクラフティング設備で、反復部を除いた固定長。 */
    private static final int CRAFTING_SYSTEM_FIXED_LENGTH = 4;
    /** NeoECO 21.1.1のServer Configが定義する既定の設備最大長。 */
    private static final int NEO_ECO_DEFAULT_MAXIMUM_LENGTH = 15;
    /** MultiBlockDefinitionが許可する最小反復長。 */
    private static final int MINIMUM_EXPAND_LENGTH = 1;
    private static volatile MultiBlockDefinition craftingSystem;
    private static volatile int craftingSystemExpandMax = Integer.MIN_VALUE;

    private AACMultiBlocks() {
    }

    public static synchronized void initialize() {
        refreshCraftingSystemDefinition();
    }

    private static void refreshCraftingSystemDefinition() {
        int desiredExpandMax = resolveExpandMax(NEConfig.craftingSystemMaxLength);
        // Config値が変わっていなければ、同じ定義を再生成しない。
        if (craftingSystem != null && craftingSystemExpandMax == desiredExpandMax) {
            return;
        }

        BlockState casing = NEBlocks.CRAFTING_CASING.getDefaultState();
        // 座標と反復方向はNeoECO 21.1.1のL9設計図をそのまま保つ。
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
                .expandMax(desiredExpandMax)
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
        craftingSystemExpandMax = desiredExpandMax;
    }

    public static synchronized MultiBlockDefinition craftingSystem() {
        /*
         * NeoECOのServer ConfigはCommon Setupより後に確定する場合がある。
         * UI表示・形成判定の直前に現在値を再確認し、古い上限を使い続けない。
         */
        refreshCraftingSystemDefinition();
        return craftingSystem;
    }

    static int resolveExpandMax(int configuredMaximumLength) {
        /*
         * Config未読時のstatic intは0になる。0から固定長を引いて負数を作らず、
         * NeoECO既定長を使ってCommon Setup中にも有効な定義を生成する。
         */
        if (configuredMaximumLength
                < CRAFTING_SYSTEM_FIXED_LENGTH + MINIMUM_EXPAND_LENGTH) {
            return NEO_ECO_DEFAULT_MAXIMUM_LENGTH - CRAFTING_SYSTEM_FIXED_LENGTH;
        }
        return configuredMaximumLength - CRAFTING_SYSTEM_FIXED_LENGTH;
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
