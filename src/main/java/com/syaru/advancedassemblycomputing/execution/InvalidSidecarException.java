package com.syaru.advancedassemblycomputing.execution;

/**
 * Raised when an AAC crafting-thread sidecar cannot be validated.
 *
 * <p>This class intentionally lives outside the Mixin package. Mixin's class
 * loader treats every class in a configured Mixin package as a Mixin and
 * rejects normal runtime classes referenced from there.</p>
 */
public final class InvalidSidecarException extends IllegalArgumentException {
    private final AACThreadSidecarFailure category;

    public InvalidSidecarException(
            AACThreadSidecarFailure category,
            String message) {
        super(message);
        this.category = category;
    }

    public InvalidSidecarException(
            AACThreadSidecarFailure category,
            String message,
            Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public AACThreadSidecarFailure category() {
        return category;
    }
}
