package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

class AACThreadSidecarMigrationTest {
    @Test
    void migratesLegacyRunningAndOutputReadyStates() {
        AACThreadSidecarMigration.StateResolution running =
                AACThreadSidecarMigration.resolve(1, false, "", true, false);
        AACThreadSidecarMigration.StateResolution outputReady =
                AACThreadSidecarMigration.resolve(1, false, "", true, true);

        assertEquals(AacThreadState.RUNNING, running.state());
        assertEquals(AacThreadState.OUTPUT_READY, outputReady.state());
        assertTrue(running.migrated());
        assertTrue(outputReady.migrated());
    }

    @Test
    void keepsExplicitCurrentStates() {
        AACThreadSidecarMigration.StateResolution running =
                AACThreadSidecarMigration.resolve(2, true, "RUNNING", true, false);
        AACThreadSidecarMigration.StateResolution outputReady =
                AACThreadSidecarMigration.resolve(2, true, "OUTPUT_READY", true, true);

        assertEquals(AacThreadState.RUNNING, running.state());
        assertEquals(AacThreadState.OUTPUT_READY, outputReady.state());
        assertFalse(running.migrated());
        assertFalse(outputReady.migrated());
    }

    @Test
    void rejectsContradictoryOrUnknownStates() {
        InvalidSidecarException explicitNoneWithPayload =
                assertThrows(
                        InvalidSidecarException.class,
                        () -> AACThreadSidecarMigration.resolve(
                                2, true, "NONE", true, false));
        InvalidSidecarException unknownState =
                assertThrows(
                        InvalidSidecarException.class,
                        () -> AACThreadSidecarMigration.resolve(
                                2, true, "BROKEN", true, false));
        InvalidSidecarException unknownSchema =
                assertThrows(
                        InvalidSidecarException.class,
                        () -> AACThreadSidecarMigration.resolve(
                                99, true, "RUNNING", true, false));

        assertEquals(
                AACThreadSidecarFailure.INVALID_STATE,
                explicitNoneWithPayload.category());
        assertEquals(AACThreadSidecarFailure.INVALID_STATE, unknownState.category());
        assertEquals(AACThreadSidecarFailure.UNKNOWN_SCHEMA, unknownSchema.category());
    }

    @Test
    void requiresDedicatedLoaderForPersistedQuarantine() throws Exception {
        CompoundTag sidecar = fixture("persisted-quarantine.snbt");
        InvalidSidecarException failure =
                assertThrows(
                        InvalidSidecarException.class,
                        () -> AACThreadSidecarMigration.resolve(
                                2, true, "QUARANTINED", true, false));

        assertEquals(AACThreadSidecarFailure.INVALID_STATE, failure.category());
        assertEquals(99, sidecar.getCompound("rawSidecar").getInt("schema"));
    }

    @Test
    void shipsRequiredRegressionFixtures() throws Exception {
        CompoundTag running = fixture("current-running.snbt");
        CompoundTag outputReady = fixture("current-output-ready.snbt");
        CompoundTag unknownState = fixture("malformed-unknown-state.snbt");

        assertEquals("RUNNING", running.getString("state"));
        assertEquals("OUTPUT_READY", outputReady.getString("state"));
        assertEquals("BROKEN", unknownState.getString("state"));
    }

    @Test
    void shipsUnknownSchemaAndMissingIdentifierFixtures() throws Exception {
        CompoundTag unknownSchema = fixture("malformed-unknown-schema.snbt");
        CompoundTag missingUuid = fixture("malformed-missing-uuid.snbt");

        assertEquals(99, unknownSchema.getInt("schema"));
        assertFalse(missingUuid.hasUUID("transactionId"));
        assertTrue(missingUuid.contains("ownerTransactionId"));
    }

    @Test
    void shipsDuplicateKeyFixture() throws Exception {
        CompoundTag duplicateKey = fixture("malformed-duplicate-key.snbt");

        assertEquals(
                2,
                duplicateKey
                        .getList("exactOutputs", Tag.TAG_COMPOUND)
                        .size());
    }

    @Test
    void rejectsOversizedExactCountBeforeBigIntegerAllocation() throws Exception {
        CompoundTag sidecar = fixture("malformed-oversized-count.snbt");
        CompoundTag oversized =
                sidecar
                        .getList("exactOutputs", Tag.TAG_COMPOUND)
                        .getCompound(0);

        // fixtureはACOの8,192 byte上限を一byteだけ超える破損countを保持する。
        assertEquals(8_193, oversized.getByteArray("amount").length);

        assertThrows(
                IllegalArgumentException.class,
                () -> com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec
                        .readNonNegative(oversized, "amount"));
    }

    private static CompoundTag fixture(String name) throws Exception {
        try (InputStream input =
                AACThreadSidecarMigrationTest.class
                        .getResourceAsStream("/sidecars/" + name)) {
            // fixture欠落を空NBTとして通さず、回帰試験の構成ミスを明示する。
            if (input == null) {
                throw new IOException("missing sidecar fixture: " + name);
            }
            String snbt =
                    new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return TagParser.parseTag(snbt);
        }
    }
}
