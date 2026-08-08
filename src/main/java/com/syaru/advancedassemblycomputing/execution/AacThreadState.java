package com.syaru.advancedassemblycomputing.execution;

/** Persisted lifecycle states for an AAC crafting thread. */
public enum AacThreadState {
    NONE,
    RUNNING,
    OUTPUT_READY,
    QUARANTINED
}
