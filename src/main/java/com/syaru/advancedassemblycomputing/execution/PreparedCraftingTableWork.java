package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;

/**
 * NeoECO Threadへ渡す一件の物理作業を、副作用の前に確定した不変値。
 *
 * <p>この値を作れた後でだけ冷却材消費とクラフトイベントを実行する。これにより、
 * 数量境界や変換失敗で拒否する要求が設備側へ副作用を残さない。</p>
 */
public record PreparedCraftingTableWork(
        List<GenericStack> outputs,
        List<GenericStack> inputs,
        List<GenericStack> remaining) {
    public PreparedCraftingTableWork {
        outputs = checked(
                outputs,
                "outputs",
                true);
        inputs = checked(
                inputs,
                "inputs",
                false);
        remaining = checked(
                remaining,
                "remaining",
                false);
    }

    private static List<GenericStack> checked(
            List<GenericStack> source,
            String name,
            boolean requireNonEmpty) {
        List<GenericStack> copy =
                List.copyOf(
                        Objects.requireNonNull(
                                source,
                                name));
        // 主出力だけは必須で、入力と返却物は空のレシピを許可する。
        if (requireNonEmpty
                && copy.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must not be empty");
        }
        // Threadへ渡す前にnullと非正数を排除し、開始後の変換例外を防ぐ。
        for (GenericStack stack : copy) {
            // 一件でも不正なら物理仕事として開始できない。
            if (stack == null
                    || stack.amount() <= 0L) {
                throw new IllegalArgumentException(
                        name + " contains an invalid stack");
            }
        }
        return copy;
    }
}
