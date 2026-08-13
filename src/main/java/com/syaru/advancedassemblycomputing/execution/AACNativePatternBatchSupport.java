package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import java.util.ArrayList;
import java.util.List;

/** ACO実装packageへ依存しない、AAC専用の数量計算処理。 */
public final class AACNativePatternBatchSupport {
    private AACNativePatternBatchSupport() {
    }

    public static KeyCounter[] scaleInputs(
            PatternBatchContext context,
            long executions) {
        KeyCounter[] source = context.copyInputsPerExecution();
        KeyCounter[] scaled = new KeyCounter[source.length];
        // Patternのslot境界を維持したまま、各入力だけを実行回数倍する。
        for (int index = 0; index < source.length; index++) {
            KeyCounter counter = scaled[index] = new KeyCounter();
            // 同一slot内の各AEKeyをlongのexact演算で拡大する。
            for (var entry : source[index]) {
                counter.add(
                        entry.getKey(),
                        Math.multiplyExact(entry.getLongValue(), executions));
            }
        }
        return scaled;
    }

    public static List<GenericStack> flatten(KeyCounter[] counters) {
        List<GenericStack> result = new ArrayList<>();
        // ACOのPrepared payloadへ渡すため、slot別Counterを一つの不変Listへ畳む。
        for (KeyCounter counter : counters) {
            // Counter内の各AEKeyと数量をGenericStackへ変換する。
            for (var entry : counter) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(result);
    }

    public static List<GenericStack> scaleAllExpectedOutputs(
            PatternBatchContext context,
            long executions) {
        List<GenericStack> result = new ArrayList<>();
        // 通常出力を実行回数倍し、Receiptの期待出力へ追加する。
        for (var entry : context.copyOutputsPerExecution()) {
            result.add(new GenericStack(
                    entry.getKey(),
                    Math.multiplyExact(entry.getLongValue(), executions)));
        }
        // 容器などの返却物も通常出力と同じ所有権payloadへ含める。
        for (var entry : context.copyRemainingOutputsPerExecution()) {
            result.add(new GenericStack(
                    entry.getKey(),
                    Math.multiplyExact(entry.getLongValue(), executions)));
        }
        return List.copyOf(result);
    }

}
