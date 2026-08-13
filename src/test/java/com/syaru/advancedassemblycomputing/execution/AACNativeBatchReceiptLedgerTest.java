package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class AACNativeBatchReceiptLedgerTest {
    /** 実装契約上、一つのPattern Busが保持する最大Receipt件数。 */
    private static final int LEDGER_CAPACITY = 256;
    /** 20 TPSで10分に相当する終端Receipt保持時間。 */
    private static final long TERMINAL_RETENTION_TICKS = 12_000L;

    @Test
    void preservesAcceptedReceiptAcrossSaveAndReload() {
        AACNativeBatchReceiptLedger source = new AACNativeBatchReceiptLedger();
        UUID id = UUID.randomUUID();
        NativeBatchReceipt pending = receipt(id, NativeBatchReceipt.State.PENDING, 10L);

        assertTrue(source.prepare(pending));
        source.finish(id, NativeBatchReceipt.State.ACCEPTED, 20L);

        AACNativeBatchReceiptLedger restored = new AACNativeBatchReceiptLedger();
        restored.load(source.save());

        assertTrue(restored.isHealthy());
        assertEquals(NativeBatchReceipt.State.ACCEPTED, restored.get(id).state());
        assertEquals(20L, restored.get(id).updatedTick());
        assertTrue(restored.removeTerminal(id));
        assertTrue(restored.isEmpty());
    }

    @Test
    void pendingReceiptCannotBeForgottenAndRejectedReceiptCan() {
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();
        UUID id = UUID.randomUUID();

        assertTrue(ledger.prepare(receipt(id, NativeBatchReceipt.State.PENDING, 1L)));
        assertFalse(ledger.removeTerminal(id));
        ledger.finish(id, NativeBatchReceipt.State.REJECTED, 2L);
        assertTrue(ledger.removeTerminal(id));
    }

    @Test
    void rejectsConflictingTerminalTransition() {
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();
        UUID id = UUID.randomUUID();
        assertTrue(ledger.prepare(receipt(id, NativeBatchReceipt.State.PENDING, 1L)));
        ledger.finish(id, NativeBatchReceipt.State.ACCEPTED, 2L);

        assertThrows(
                IllegalStateException.class,
                () -> ledger.finish(id, NativeBatchReceipt.State.REJECTED, 3L));
    }

    @Test
    void locksDuplicateIdsWithoutRewritingRawPayload() {
        UUID id = UUID.randomUUID();
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema", 2);
        ListTag entries = new ListTag();
        entries.add(entry(id));
        entries.add(entry(id));
        raw.put("entries", entries);

        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();
        ledger.load(raw);

        assertFalse(ledger.isHealthy());
        assertEquals(raw, ledger.save());
        assertFalse(ledger.prepare(receipt(UUID.randomUUID(), NativeBatchReceipt.State.PENDING, 1L)));
    }

    @Test
    void acceptsOnlyAnExactlyMatchingPrepareRetry() {
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();
        UUID id = UUID.randomUUID();
        NativeBatchReceipt original = receipt(id, NativeBatchReceipt.State.PENDING, 1L);

        assertTrue(ledger.prepare(original));
        assertTrue(ledger.prepare(original));
        assertFalse(
                ledger.prepare(
                        new NativeBatchReceipt(
                                id,
                                NativeBatchReceipt.State.PENDING,
                                65_537L,
                                "aac:test-pattern",
                                "aac:test-payload",
                                2L)));
    }

    @Test
    void quarantinesLegacySchemaWithoutInventingAPayloadDigest() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schema", 1);
        legacy.put("entries", new ListTag());
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();

        ledger.load(legacy);

        assertFalse(ledger.isHealthy());
        assertEquals(legacy, ledger.save());
    }

    @Test
    void preservesAnExplicitlyCorruptedMarker() {
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema", 2);
        raw.putBoolean("corrupted", true);
        raw.put("entries", new ListTag());
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();

        ledger.load(raw);

        assertFalse(ledger.isHealthy());
        assertEquals(raw, ledger.save());
    }

    @Test
    void evictsOnlyExpiredTerminalReceiptsWhenFull() {
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();
        UUID oldest = null;
        // 台帳を期限切れ終端Receiptだけで上限まで満たす。
        for (int index = 0; index < LEDGER_CAPACITY; index++) {
            UUID id = UUID.randomUUID();
            // 期限切れ追い出し順を検証するため、先頭IDだけ保持する。
            if (index == 0) {
                oldest = id;
            }
            assertTrue(ledger.prepare(receipt(id, NativeBatchReceipt.State.PENDING, 0L)));
            ledger.finish(id, NativeBatchReceipt.State.ACCEPTED, 0L);
        }
        UUID replacement = UUID.randomUUID();

        assertTrue(
                ledger.prepare(
                        receipt(
                                replacement,
                                NativeBatchReceipt.State.PENDING,
                                TERMINAL_RETENTION_TICKS)));
        assertNull(ledger.get(oldest));
        assertEquals(replacement, ledger.get(replacement).transactionId());
    }

    @Test
    void neverEvictsPendingOwnershipWhenFull() {
        AACNativeBatchReceiptLedger ledger = new AACNativeBatchReceiptLedger();
        // PENDINGは古くても所有権の正本なので、上限まで保持する。
        for (int index = 0; index < LEDGER_CAPACITY; index++) {
            assertTrue(
                    ledger.prepare(
                            receipt(
                                    UUID.randomUUID(),
                                    NativeBatchReceipt.State.PENDING,
                                    0L)));
        }

        assertFalse(
                ledger.prepare(
                        receipt(
                                UUID.randomUUID(),
                                NativeBatchReceipt.State.PENDING,
                                TERMINAL_RETENTION_TICKS)));
    }

    private static NativeBatchReceipt receipt(
            UUID id,
            NativeBatchReceipt.State state,
            long tick) {
        return new NativeBatchReceipt(
                id,
                state,
                65_536L,
                "aac:test-pattern",
                "aac:test-payload",
                tick);
    }

    private static CompoundTag entry(UUID id) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", id);
        entry.putString("state", NativeBatchReceipt.State.PENDING.name());
        entry.putLong("executions", 1L);
        entry.putString("pattern", "aac:test-pattern");
        entry.putString("payloadDigest", "aac:test-payload");
        entry.putLong("updatedTick", 0L);
        return entry;
    }
}
