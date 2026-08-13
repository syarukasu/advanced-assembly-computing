package com.syaru.advancedassemblycomputing.execution;

/** AAC Thread sidecarの版別状態移行規則。 */
public final class AACThreadSidecarMigration {
    /** AAC 1.0.1でstateを保存していなかった旧schema。 */
    public static final int LEGACY_SCHEMA = 1;
    /** stateを必須にしてNONEと所有中を区別する現行schema。 */
    public static final int CURRENT_SCHEMA = 2;

    private AACThreadSidecarMigration() {}

    public static StateResolution resolve(
            int schema,
            boolean hasState,
            String storedState,
            boolean hasActivePayload,
            boolean outputReady) {
        // 対応していないschemaを推測で正常状態へ移行しない。
        if (schema != LEGACY_SCHEMA && schema != CURRENT_SCHEMA) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.UNKNOWN_SCHEMA,
                    "unknown AAC crafting-table batch sidecar schema");
        }

        String state = storedState == null ? "" : storedState;
        // state欠落を許すのは、実際にstateを持たなかったschema 1だけ。
        if (!hasState || state.isEmpty()) {
            if (schema != LEGACY_SCHEMA) {
                throw new InvalidSidecarException(
                        AACThreadSidecarFailure.INVALID_STATE,
                        "AAC crafting-table batch sidecar is missing its state");
            }
            return new StateResolution(
                    hasActivePayload
                            ? outputReady
                                    ? AacThreadState.OUTPUT_READY
                                    : AacThreadState.RUNNING
                            : AacThreadState.NONE,
                    true);
        }

        AacThreadState parsedState;
        try {
            parsedState = AacThreadState.valueOf(state);
        } catch (IllegalArgumentException invalidState) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_STATE,
                    "invalid AAC crafting-table batch state",
                    invalidState);
        }

        // 永続化済み隔離は専用loaderだけがraw NBTを復元する。
        if (parsedState == AacThreadState.QUARANTINED) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_STATE,
                    "quarantined sidecars must use the quarantine loader");
        }
        // NONEに所有payloadがある矛盾状態をRUNNINGへ昇格しない。
        if (parsedState == AacThreadState.NONE && hasActivePayload) {
            throw new InvalidSidecarException(
                    AACThreadSidecarFailure.INVALID_STATE,
                    "AAC crafting-table batch sidecar has payload in NONE state");
        }
        return new StateResolution(parsedState, schema != CURRENT_SCHEMA);
    }

    public record StateResolution(AacThreadState state, boolean migrated) {}
}
