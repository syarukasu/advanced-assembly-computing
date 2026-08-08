package com.syaru.advancedassemblycomputing.execution;

import java.util.Objects;
import java.util.function.Supplier;

/** Revisionが変わらない照会で、同じ不変Snapshotを再利用する。 */
public final class AACSnapshotCache<T> {
    private long cachedRevision = Long.MIN_VALUE;
    private T cachedSnapshot;

    public synchronized T get(long revision, Supplier<T> builder) {
        Objects.requireNonNull(builder, "builder");
        if (cachedSnapshot != null
                && cachedRevision == revision) {
            AACRevisionMetrics.pollAvoided();
            return cachedSnapshot;
        }
        cachedSnapshot = Objects.requireNonNull(
                builder.get(),
                "snapshot builder returned null");
        cachedRevision = revision;
        AACRevisionMetrics.snapshotAllocated();
        return cachedSnapshot;
    }

    public synchronized void clear() {
        cachedSnapshot = null;
        cachedRevision = Long.MIN_VALUE;
    }

    public synchronized long cachedRevision() {
        return cachedRevision;
    }
}
