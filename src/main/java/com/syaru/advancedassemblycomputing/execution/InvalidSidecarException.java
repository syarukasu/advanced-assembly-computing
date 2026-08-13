package com.syaru.advancedassemblycomputing.execution;

/** AAC Thread sidecarを正常な所有状態へ復元できない場合の分類付き例外。 */
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
