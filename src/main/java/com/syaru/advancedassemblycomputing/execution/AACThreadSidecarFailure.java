package com.syaru.advancedassemblycomputing.execution;

/**
 * AAC Thread sidecarの検証に失敗した理由。
 *
 * <p>文字列だけをログへ出すのではなく、管理者が診断・集計できる安定した分類を
 * 保存する。分類は復旧を自動判断するためには使わない。</p>
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
