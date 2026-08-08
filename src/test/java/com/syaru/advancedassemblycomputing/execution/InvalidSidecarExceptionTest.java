package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class InvalidSidecarExceptionTest {
    @Test
    void runtimeSidecarExceptionIsOutsideMixinPackage() {
        assertFalse(
                InvalidSidecarException.class
                        .getPackageName()
                        .endsWith(".mixin"));
    }

    @Test
    void preservesFailureCategory() {
        InvalidSidecarException exception =
                new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_OUTPUTS,
                        "invalid output sidecar");

        assertSame(
                AACThreadSidecarFailure.INVALID_OUTPUTS,
                exception.category());
    }
}
