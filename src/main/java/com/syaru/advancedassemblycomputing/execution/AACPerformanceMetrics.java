package com.syaru.advancedassemblycomputing.execution;

import java.util.concurrent.atomic.AtomicLong;

/** Low-cost counters for validating the event-driven AAC execution path. */
public final class AACPerformanceMetrics {
    private static final AtomicLong POLLS_AVOIDED = new AtomicLong();
    private static final AtomicLong WAKEUPS = new AtomicLong();
    private static final AtomicLong FULL_INDEX_REBUILDS = new AtomicLong();
    private static final AtomicLong THREAD_SCANS = new AtomicLong();
    private static final AtomicLong SNAPSHOT_ALLOCATIONS = new AtomicLong();
    private static final AtomicLong OUTPUT_READY_SLEEP_TICKS = new AtomicLong();
    private static final AtomicLong ACCOUNTING_ONLY_URGENT_AVOIDED = new AtomicLong();

    private AACPerformanceMetrics() {
    }

    public static void pollAvoided() {
        POLLS_AVOIDED.incrementAndGet();
    }

    public static void wakeup() {
        WAKEUPS.incrementAndGet();
    }

    public static void fullIndexRebuild() {
        FULL_INDEX_REBUILDS.incrementAndGet();
    }

    public static void threadScan() {
        THREAD_SCANS.incrementAndGet();
    }

    public static void snapshotAllocation() {
        SNAPSHOT_ALLOCATIONS.incrementAndGet();
    }

    public static void outputReadySleepTick() {
        OUTPUT_READY_SLEEP_TICKS.incrementAndGet();
    }

    public static void accountingOnlyUrgentAvoided() {
        ACCOUNTING_ONLY_URGENT_AVOIDED.incrementAndGet();
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                POLLS_AVOIDED.get(),
                WAKEUPS.get(),
                FULL_INDEX_REBUILDS.get(),
                THREAD_SCANS.get(),
                SNAPSHOT_ALLOCATIONS.get(),
                OUTPUT_READY_SLEEP_TICKS.get(),
                ACCOUNTING_ONLY_URGENT_AVOIDED.get());
    }

    public record Snapshot(
            long pollsAvoided,
            long wakeupNotifications,
            long fullIndexRebuilds,
            long threadScans,
            long snapshotAllocations,
            long outputReadySleepTicks,
            long accountingOnlyUrgentAvoided) {
    }
}
