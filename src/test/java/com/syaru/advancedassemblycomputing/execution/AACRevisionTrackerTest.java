package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AACRevisionTrackerTest {
    @Test
    void unchangedStateDoesNotAdvanceCounters() {
        AACRevisionTracker tracker = new AACRevisionTracker();

        assertEquals(0L, tracker.ownershipRevision());
        assertEquals(0L, tracker.progressRevision());
        assertEquals(0L, tracker.receiptRevision());
        assertEquals(0L, tracker.capacityRevision());
    }

    @Test
    void eachTransitionAdvancesOnlyItsOwnRevision() {
        AACRevisionTracker tracker = new AACRevisionTracker();

        tracker.ownershipChanged();
        tracker.progressChanged();
        tracker.receiptChanged();
        tracker.capacityChanged();
        tracker.capacityChanged();

        assertEquals(1L, tracker.ownershipRevision());
        assertEquals(1L, tracker.progressRevision());
        assertEquals(1L, tracker.receiptRevision());
        assertEquals(2L, tracker.capacityRevision());
    }
}
