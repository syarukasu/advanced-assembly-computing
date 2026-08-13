package com.syaru.advancedassemblycomputing.execution;

/** AAC管理下のNeoECO Threadへ保存する所有権状態。 */
public enum AacThreadState {
    NONE,
    RUNNING,
    OUTPUT_READY,
    QUARANTINED
}
