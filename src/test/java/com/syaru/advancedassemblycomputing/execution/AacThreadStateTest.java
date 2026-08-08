package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AacThreadStateTest {
    @Test
    void persistedStatesLiveOutsideMixinPackage() {
        assertFalse(
                AacThreadState.class
                        .getPackageName()
                        .endsWith(".mixin"));
        assertEquals(
                "QUARANTINED",
                AacThreadState.QUARANTINED.name());
    }
}
