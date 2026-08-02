package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class AACCraftingTableTerminalReceiptLedgerTest {
    @Test
    void retainsExactOutputUntilTheParentExplicitlyForgetsIt() {
        AACCraftingTableTerminalReceiptLedger ledger =
                new AACCraftingTableTerminalReceiptLedger();
        UUID transactionId =
                UUID.randomUUID();
        String digest =
                "aco:test-terminal-receipt";
        AEKey output =
                new TestKey(
                        "output");
        BigInteger amount =
                BigInteger.TEN.pow(
                        1_024);
        Map<AEKey, BigInteger> exactOutputs =
                Map.of(
                        output,
                        amount);

        assertTrue(
                ledger.record(
                        transactionId,
                        digest,
                        exactOutputs));
        // 完全に同じ完了通知の再送だけを冪等な成功として受理する。
        assertTrue(
                ledger.record(
                        transactionId,
                        digest,
                        exactOutputs));
        CraftingTableBatchSnapshot snapshot =
                ledger.snapshot(
                                transactionId,
                                digest)
                        .orElseThrow();
        assertEquals(
                CraftingTableBatchSnapshot.State.ACKNOWLEDGED,
                snapshot.state());
        assertEquals(
                exactOutputs,
                snapshot.exactOutputs());

        // Payloadが違う親へ、同じUUIDの完了証明を公開しない。
        assertTrue(
                ledger.snapshot(
                                transactionId,
                                "aco:another-payload")
                        .isEmpty());
        assertFalse(
                ledger.forget(
                        transactionId,
                        "aco:another-payload"));
        assertTrue(
                ledger.forget(
                        transactionId,
                        digest));
        assertTrue(
                ledger.snapshot(
                                transactionId,
                                digest)
                        .isEmpty());
        // 親側のforget再送は、既に削除済みでも成功として扱う。
        assertTrue(
                ledger.forget(
                        transactionId,
                        digest));
    }

    @Test
    void rejectsAnotherCompletionForTheSameTransactionId() {
        AACCraftingTableTerminalReceiptLedger ledger =
                new AACCraftingTableTerminalReceiptLedger();
        UUID transactionId =
                UUID.randomUUID();
        AEKey output =
                new TestKey(
                        "output");

        assertTrue(
                ledger.record(
                        transactionId,
                        "aco:first",
                        Map.of(
                                output,
                                BigInteger.ONE)));
        // 同じUUIDへ異なるPayloadや数量を上書きしない。
        assertFalse(
                ledger.record(
                        transactionId,
                        "aco:second",
                        Map.of(
                                output,
                                BigInteger.ONE)));
        assertFalse(
                ledger.record(
                        transactionId,
                        "aco:first",
                        Map.of(
                                output,
                                BigInteger.TWO)));
    }

    /** Minecraft Registryを起動せず、終端Receiptの所有権だけを試験する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(
                String id) {
            this.id =
                    id;
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
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "advanced_assembly_computing",
                    id);
        }

        @Override
        public void writeToPacket(
                RegistryFriendlyByteBuf buffer) {
            // Packet処理を試験しないため書き込まない。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(
                    id);
        }

        @Override
        public void addDrops(
                long amount,
                List<ItemStack> drops,
                Level level,
                BlockPos pos) {
            // ワールド処理を試験しないためドロップを作らない。
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
