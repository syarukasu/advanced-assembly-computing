package com.syaru.advancedassemblycomputing.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VectorBatchMathTest {
    @Test
    void oneWorkerProvidesItsConfiguredPhysicalThreads() {
        assertEquals(256, VectorBatchMath.capacity(1, 256));
    }

    @Test
    void manyWorkersSaturateBeforeIntegerSentinel() {
        assertEquals(
                VectorBatchMath.MAX_SAFE_VECTOR_SLOTS,
                VectorBatchMath.capacity(65_536, 65_536));
    }

    @Test
    void availableSlotsNeverBecomeNegative() {
        assertEquals(0, VectorBatchMath.available(100, 101));
        assertEquals(25, VectorBatchMath.available(100, 75));
    }
}
