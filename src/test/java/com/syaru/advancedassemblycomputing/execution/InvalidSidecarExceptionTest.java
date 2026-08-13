package com.syaru.advancedassemblycomputing.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class InvalidSidecarExceptionTest {
    @Test
    void preservesFailureCategoryOutsideMixinPackage() {
        InvalidSidecarException failure =
                new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_OUTPUTS,
                        "invalid output sidecar");

        assertFalse(failure.getClass().getPackageName().endsWith(".mixin"));
        assertSame(AACThreadSidecarFailure.INVALID_OUTPUTS, failure.category());
    }
}
