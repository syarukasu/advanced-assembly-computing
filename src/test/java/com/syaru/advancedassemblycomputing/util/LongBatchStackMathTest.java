package com.syaru.advancedassemblycomputing.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class LongBatchStackMathTest {
    private static final AEKey TEST_KEY =
            new TestKey("compressed_material");

    @Test
    void comparesSameKeyTotalsBeyondSignedLongWithoutWrapping() {
        List<GenericStack> left = List.of(
                new GenericStack(TEST_KEY, Long.MAX_VALUE),
                new GenericStack(TEST_KEY, Long.MAX_VALUE));
        List<GenericStack> right = List.of(
                new GenericStack(TEST_KEY, Long.MAX_VALUE),
                new GenericStack(TEST_KEY, Long.MAX_VALUE));

        assertTrue(LongBatchStackMath.sameTotals(left, right));
        assertFalse(LongBatchStackMath.totalsFitLong(left));
    }

    @Test
    void limitsEachOutputKeyToARepresentableWorkerWave() {
        List<GenericStack> outputs =
                List.of(new GenericStack(TEST_KEY, 9L));

        assertEquals(
                Long.MAX_VALUE / 9L,
                LongBatchStackMath.safeExecutionLimit(
                        outputs,
                        List.of(),
                        Long.MAX_VALUE));
    }

    @Test
    void preservesNineIndependentLongMaximumInputSlots() {
        KeyCounter[] inputs =
                new KeyCounter[9];
        // 同じ素材を九枠で使い、各slotだけがLong.MAX_VALUEへ収まる境界を作る。
        for (int slot = 0;
                slot < inputs.length;
                slot++) {
            KeyCounter counter =
                    inputs[slot] =
                            new KeyCounter();
            counter.add(
                    TEST_KEY,
                    1L);
        }

        assertEquals(
                Long.MAX_VALUE,
                LongBatchStackMath.safeExecutionLimit(
                        inputs,
                        List.of(
                                new GenericStack(
                                        new TestKey(
                                                "output"),
                                        1L)),
                        List.of(),
                        Long.MAX_VALUE));
    }

    @Test
    void limitsAWorkerWaveByTheLargestInputSlotCoefficient() {
        KeyCounter input =
                new KeyCounter();
        input.add(
                TEST_KEY,
                9L);

        assertEquals(
                Long.MAX_VALUE / 9L,
                LongBatchStackMath.safeExecutionLimit(
                        new KeyCounter[] {input},
                        List.of(
                                new GenericStack(
                                        new TestKey(
                                                "output"),
                                        1L)),
                        List.of(),
                        Long.MAX_VALUE));
    }

    @Test
    void checkedScalingRejectsAnUnrepresentablePhysicalTransfer() {
        List<GenericStack> perExecution =
                List.of(new GenericStack(TEST_KEY, 2L));

        assertThrows(
                ArithmeticException.class,
                () -> LongBatchStackMath.scale(
                        perExecution,
                        Long.MAX_VALUE));
    }

    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            this.id =
                    ResourceLocation.fromNamespaceAndPath(
                            "aac_test",
                            path);
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public void writeToPacket(
                FriendlyByteBuf buffer) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
        }
    }
}
