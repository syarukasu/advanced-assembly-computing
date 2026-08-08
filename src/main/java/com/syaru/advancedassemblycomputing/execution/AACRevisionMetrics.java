package com.syaru.advancedassemblycomputing.execution;

import java.util.concurrent.atomic.AtomicLong;

/** AACの通知・索引・Snapshot待機を、同じ粒度で観測する軽量メトリクス。 */
public final class AACRevisionMetrics {
    private static final AtomicLong POLLS_AVOIDED = new AtomicLong();
    private static final AtomicLong WAKEUP_NOTIFICATIONS = new AtomicLong();
    private static final AtomicLong FULL_INDEX_REBUILDS = new AtomicLong();
    private static final AtomicLong THREAD_SCANS = new AtomicLong();
    private static final AtomicLong SNAPSHOT_ALLOCATIONS = new AtomicLong();
    private static final AtomicLong OUTPUT_READY_SLEEP_TICKS = new AtomicLong();
    private static final AtomicLong URGENT_ACCOUNTING_TICKS = new AtomicLong();

    private AACRevisionMetrics() {
    }

    public static void pollAvoided() {
        POLLS_AVOIDED.incrementAndGet();
    }

    public static void wakeupNotification() {
        WAKEUP_NOTIFICATIONS.incrementAndGet();
    }

    public static void fullIndexRebuild() {
        FULL_INDEX_REBUILDS.incrementAndGet();
    }

    public static void threadScan() {
        THREAD_SCANS.incrementAndGet();
    }

    public static void snapshotAllocated() {
        SNAPSHOT_ALLOCATIONS.incrementAndGet();
    }

    public static void outputReadySleepTick() {
        OUTPUT_READY_SLEEP_TICKS.incrementAndGet();
    }

    public static void urgentAccountingTick() {
        URGENT_ACCOUNTING_TICKS.incrementAndGet();
    }

    /** テストと診断表示用の、瞬間値をまとめた不変Snapshot。 */
    public static Snapshot snapshot() {
        return new Snapshot(
                POLLS_AVOIDED.get(),
                WAKEUP_NOTIFICATIONS.get(),
                FULL_INDEX_REBUILDS.get(),
                THREAD_SCANS.get(),
                SNAPSHOT_ALLOCATIONS.get(),
                OUTPUT_READY_SLEEP_TICKS.get(),
                URGENT_ACCOUNTING_TICKS.get());
    }

    static void resetForTests() {
        POLLS_AVOIDED.set(0L);
        WAKEUP_NOTIFICATIONS.set(0L);
        FULL_INDEX_REBUILDS.set(0L);
        THREAD_SCANS.set(0L);
        SNAPSHOT_ALLOCATIONS.set(0L);
        OUTPUT_READY_SLEEP_TICKS.set(0L);
        URGENT_ACCOUNTING_TICKS.set(0L);
    }

    public record Snapshot(
            long pollsAvoided,
            long wakeupNotifications,
            long fullIndexRebuilds,
            long threadScans,
            long snapshotAllocations,
            long outputReadySleepTicks,
            long urgentAccountingTicks) {
    }
}
