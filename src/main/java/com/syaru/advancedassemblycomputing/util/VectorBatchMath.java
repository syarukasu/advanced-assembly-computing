package com.syaru.advancedassemblycomputing.util;

public final class VectorBatchMath {
    /**
     * Integer.MAX_VALUEは外部実装で「無制限」の番兵になり得るため、実データには使わない。
     */
    public static final int MAX_SAFE_VECTOR_SLOTS = Integer.MAX_VALUE - 1;

    private VectorBatchMath() {
    }

    public static int capacity(int workers, int craftsPerWorker) {
        // 形成前または不正な設定値では実行枠を公開しない。
        if (workers <= 0 || craftsPerWorker <= 0) {
            return 0;
        }
        long value = (long) workers * craftsPerWorker;
        return value >= MAX_SAFE_VECTOR_SLOTS ? MAX_SAFE_VECTOR_SLOTS : (int) value;
    }

    public static int available(int capacity, int running) {
        return Math.max(0, Math.max(0, capacity) - Math.max(0, running));
    }

    public static int saturatedAdd(int left, int right) {
        long value = (long) Math.max(0, left) + Math.max(0, right);
        return value >= MAX_SAFE_VECTOR_SLOTS ? MAX_SAFE_VECTOR_SLOTS : (int) value;
    }

}
