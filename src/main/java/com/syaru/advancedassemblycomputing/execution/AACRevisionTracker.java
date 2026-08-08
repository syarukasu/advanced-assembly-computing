package com.syaru.advancedassemblycomputing.execution;

/** Monotonic change counters used to avoid rebuilding unchanged runtime views. */
public final class AACRevisionTracker {
    private long ownershipRevision;
    private long progressRevision;
    private long receiptRevision;
    private long capacityRevision;

    public long ownershipRevision() {
        return ownershipRevision;
    }

    public long progressRevision() {
        return progressRevision;
    }

    public long receiptRevision() {
        return receiptRevision;
    }

    public long capacityRevision() {
        return capacityRevision;
    }

    public void ownershipChanged() {
        ownershipRevision++;
    }

    public void progressChanged() {
        progressRevision++;
    }

    public void receiptChanged() {
        receiptRevision++;
    }

    public void capacityChanged() {
        capacityRevision++;
    }
}
