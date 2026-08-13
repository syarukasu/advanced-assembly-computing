package com.syaru.advancedassemblycomputing.execution;

/**
 * AAC Thread sidecarの検証に失敗した理由。
 *
 * <p>管理者向け診断へ安定した分類を残すための値であり、自動復旧の判断には使わない。</p>
 */
public enum AACThreadSidecarFailure {
    UNKNOWN_SCHEMA,
    MISSING_IDENTIFIER,
    INVALID_STATE,
    INVALID_MODE,
    INVALID_DIGEST,
    INVALID_AE_KEY,
    INVALID_OUTPUTS,
    DUPLICATE_KEY,
    OVERSIZED_PAYLOAD,
    PERSISTED_QUARANTINE,
    INTERNAL_VALIDATION_ERROR
}
