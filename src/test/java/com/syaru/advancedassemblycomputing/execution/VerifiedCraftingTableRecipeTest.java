package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class VerifiedCraftingTableRecipeTest {
    private static final AEKey INPUT =
            new TestKey("input");
    private static final AEKey OUTPUT =
            new TestKey("output");

    @Test
    void oneProofUsesTheSameFormulaForLongAndBigIntegerOrders() {
        VerifiedCraftingTableRecipe.Formula formula =
                new VerifiedCraftingTableRecipe.Formula(
                        List.of(
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L),
                                new GenericStack(INPUT, 1L)),
                        List.of(new GenericStack(OUTPUT, 1L)),
                        List.of());

        BigInteger signedLongExecutions =
                BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger hugeExecutions =
                BigInteger.TEN.pow(1_024)
                        .subtract(BigInteger.ONE);

        assertEquals(
                Map.of(
                        INPUT,
                        signedLongExecutions.multiply(
                                BigInteger.valueOf(9L))),
                formula.exactInputTotals(
                        signedLongExecutions));
        assertEquals(
                Map.of(OUTPUT, signedLongExecutions),
                formula.exactOutputTotals(
                        signedLongExecutions));
        assertEquals(
                Map.of(
                        INPUT,
                        hugeExecutions.multiply(
                                BigInteger.valueOf(9L))),
                formula.exactInputTotals(
                        hugeExecutions));
        assertEquals(
                Map.of(OUTPUT, hugeExecutions),
                formula.exactOutputTotals(
                        hugeExecutions));
    }

    /** Minecraft Registryを起動せず、数量式だけを試験する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(String id) {
            this.id = id;
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
            return new ResourceLocation(
                    "advanced_assembly_computing",
                    id);
        }

        @Override
        public void writeToPacket(
                FriendlyByteBuf buffer) {
            // Packet処理を試験しないため書き込まない。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // ワールド処理を試験しないためドロップを作らない。
        }
    }
}
