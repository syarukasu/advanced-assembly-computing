package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.api.contract.BatchTargetRevision;
import com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupApi;
import com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class AACRevisionInfrastructureTest {
    @AfterEach
    void resetMetrics() {
        AACRevisionMetrics.resetForTests();
    }

    @Test
    void publishesOnlyOnChangesAndKeepsAllRevisionsMonotonic() {
        AACRevisionState state = new AACRevisionState();
        List<BatchTargetRevision> received = new ArrayList<>();
        RevisionWakeupRegistration registration =
                RevisionWakeupApi.register(received::add);
        try {
            state.touchAndPublish(
                    "aac:test-target",
                    "aac:test-runtime",
                    UUID.randomUUID().toString(),
                    AACRevisionState.Change.PROGRESS,
                    "RUNNING");
            // 変化なしのtickはtouchAndPublish自体を呼ばない契約なので通知も増えない。
            assertEquals(1, received.size());
            state.touchAndPublish(
                    "aac:test-target",
                    "aac:test-runtime",
                    received.get(0).transactionId(),
                    AACRevisionState.Change.RECEIPT,
                    "OUTPUT_READY");
            assertEquals(2, received.size());
            assertTrue(
                    received.get(1).receiptRevision()
                            > received.get(0).receiptRevision());
            assertTrue(
                    state.current().aggregateRevision()
                            >= 2L);
        } finally {
            registration.close();
        }
    }

    @Test
    void indexesTenThousandTransactionsWithoutRepeatedStructureScans() {
        AACRevisionIndex<String> index = new AACRevisionIndex<>();
        List<String> owners = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            String owner = "worker-" + i;
            UUID id = UUID.nameUUIDFromBytes(owner.getBytes());
            owners.add(owner);
            ids.add(id);
            index.put(id, "payload-" + i, owner);
        }

        for (int i = 0; i < 10_000; i++) {
            int selected = i;
            assertEquals(
                    Optional.of("worker-" + selected),
                    index.lookup(
                            ids.get(selected),
                            "payload-" + selected,
                            ignored -> true));
        }
        assertEquals(10_000, index.size());
        assertEquals(0L, AACRevisionMetrics.snapshot().threadScans());

        index.requestRebuild();
        index.rebuild(
                owners,
                owner -> {
                    int selected = Integer.parseInt(
                            owner.substring("worker-".length()));
                    return Optional.of(
                            new AACRevisionIndex.IndexedTarget<>(
                                    ids.get(selected),
                                    "payload-" + selected,
                                    owner));
                });
        assertEquals(1L, AACRevisionMetrics.snapshot().fullIndexRebuilds());
        assertEquals(10_000L, AACRevisionMetrics.snapshot().threadScans());
    }

    @Test
    void reusesStableSnapshotsAndAllocatesAfterRevisionChanges() {
        AACSnapshotCache<Object> cache = new AACSnapshotCache<>();
        AtomicInteger builds = new AtomicInteger();
        Object first = cache.get(4L, () -> {
            builds.incrementAndGet();
            return new Object();
        });
        Object second = cache.get(4L, () -> {
            builds.incrementAndGet();
            return new Object();
        });
        Object third = cache.get(5L, () -> {
            builds.incrementAndGet();
            return new Object();
        });

        assertSame(first, second);
        assertEquals(2, builds.get());
        assertTrue(first != third);
        assertEquals(2L, AACRevisionMetrics.snapshot().snapshotAllocations());
    }
}
