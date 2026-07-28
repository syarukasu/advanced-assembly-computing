package com.syaru.advancedassemblycomputing.util;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * long一件ごとの実搬送を維持しつつ、複数slot合計だけをBigIntegerで検証する。
 */
public final class LongBatchStackMath {
    /** signed longで正数として使える値部分のbit数。符号bit一つを除く。 */
    private static final int SIGNED_LONG_MAGNITUDE_BITS =
            Long.SIZE - 1;

    private LongBatchStackMath() {
    }

    public static List<GenericStack> flatten(
            KeyCounter[] counters) {
        List<GenericStack> result = new ArrayList<>();
        // 作業台slotの順序と同一キーの重複を維持したまま、Thread保存用の列へ展開する。
        for (KeyCounter counter : counters) {
            // 一slot内でPlannerが確定した候補だけを個別要素として追加する。
            for (var entry : counter) {
                result.add(new GenericStack(
                        entry.getKey(),
                        entry.getLongValue()));
            }
        }
        return List.copyOf(result);
    }

    public static List<GenericStack> fromCounter(
            KeyCounter counter) {
        List<GenericStack> result = new ArrayList<>();
        // KeyCounterの各キーを一度ずつ、不変なGenericStack列へ写す。
        for (var entry : counter) {
            result.add(new GenericStack(
                    entry.getKey(),
                    entry.getLongValue()));
        }
        return List.copyOf(result);
    }

    public static List<GenericStack> scale(
            List<GenericStack> perExecution,
            long executions) {
        List<GenericStack> result = new ArrayList<>(
                perExecution.size());
        // 通常AE2 Jobだけは、各一回分スタックをchecked long係数で実量へ拡大する。
        for (GenericStack stack : perExecution) {
            result.add(new GenericStack(
                    stack.what(),
                    Math.multiplyExact(
                            stack.amount(),
                            executions)));
        }
        return List.copyOf(result);
    }

    public static boolean sameTotals(
            List<GenericStack> left,
            List<GenericStack> right) {
        try {
            return totals(left).equals(totals(right));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public static boolean totalsFitLong(
            List<GenericStack> stacks) {
        // NeoECOのKeyCounterへ合算される各キーがsigned long正数へ収まるか確認する。
        for (BigInteger amount : totals(stacks).values()) {
            // 非正数または符号bitを必要とする数量は通常long経路へ渡せない。
            if (amount.signum() <= 0
                    || amount.bitLength()
                            > SIGNED_LONG_MAGNITUDE_BITS) {
                return false;
            }
        }
        return true;
    }

    public static long safeExecutionLimit(
            KeyCounter[] inputsPerExecution,
            List<GenericStack> outputsPerExecution,
            List<GenericStack> remainingPerExecution,
            long offered) {
        BigInteger safe =
                BigInteger.valueOf(
                        offered);
        BigInteger maximum =
                BigInteger.valueOf(
                        Long.MAX_VALUE);
        /*
         * 入力はキー別合算せず、NeoECO Threadへ保存するslot要素ごとに上限を求める。
         * 同一素材を九slotで使う正当なレシピを、合計overflowだけで拒否しない。
         */
        for (KeyCounter slot :
                inputsPerExecution) {
            // 一slot内の各確定候補がsigned longへ収まる最大実行数を採用する。
            for (var entry : slot) {
                // 非正数入力は有効な作業台式ではないため、安全実行数を0にする。
                if (entry.getLongValue() <= 0L) {
                    return 0L;
                }
                safe =
                        safe.min(
                                maximum.divide(
                                        BigInteger.valueOf(
                                                entry.getLongValue())));
            }
        }
        Map<AEKey, BigInteger> perKey = totals(
                concatenate(
                        outputsPerExecution,
                        remainingPerExecution));
        // 出力と返却物はNeoECOのKeyCounterへ合算されるため、キー別合計で上限を求める。
        for (BigInteger amount : perKey.values()) {
            // 非正数出力は物理仕事を表さないため、安全実行数を0にする。
            if (amount.signum() <= 0) {
                return 0L;
            }
            safe = safe.min(maximum.divide(amount));
        }
        return safe.longValueExact();
    }

    /**
     * 入力を持たない式向けの互換オーバーロード。
     *
     * <p>新しい実行経路は必ず入力slotを渡す。</p>
     */
    public static long safeExecutionLimit(
            List<GenericStack> outputsPerExecution,
            List<GenericStack> remainingPerExecution,
            long offered) {
        return safeExecutionLimit(
                new KeyCounter[0],
                outputsPerExecution,
                remainingPerExecution,
                offered);
    }

    public static List<GenericStack> concatenate(
            List<GenericStack> first,
            List<GenericStack> second) {
        List<GenericStack> result = new ArrayList<>(
                first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private static Map<AEKey, BigInteger> totals(
            List<GenericStack> stacks) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        // 合計時だけBigIntegerを使い、同一キーのlong加算overflowを防ぐ。
        for (GenericStack stack : stacks) {
            // nullと非正数は物理仕事を表せないため、呼出元へ明示的に失敗を返す。
            if (stack == null || stack.amount() <= 0L) {
                throw new IllegalArgumentException(
                        "long batch contains an invalid stack");
            }
            result.merge(
                    stack.what(),
                    BigInteger.valueOf(stack.amount()),
                    BigInteger::add);
        }
        return result;
    }
}
