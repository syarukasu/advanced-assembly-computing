package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class AACWideStackEligibility {
    private AACWideStackEligibility() {
    }

    /**
     * Neo ECO標準と同じ不変アイテム条件を保ち、amountのint上限だけをlongへ広げる。
     */
    public static boolean isSafe(List<GenericStack> stacks, boolean input) {
        // 全要素が不変な通常アイテムであることを証明できた時だけwide経路を許可する。
        for (GenericStack stack : stacks) {
            if (!isSafe(stack, input)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafe(GenericStack stack, boolean input) {
        // null、非正数、Item以外のAEKeyはNeo ECO組立レシピとして扱わない。
        if (stack == null || stack.amount() <= 0L || !(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        // NBT・耐久値付きアイテムは同一性や返却状態が変化し得るため合算しない。
        if (!itemKey.getReadOnlyStack().getComponentsPatch().isEmpty() || itemKey.isDamaged()) {
            return false;
        }
        ItemStack itemStack = itemKey.toStack(1);
        // 耐久消費または容器返却がある入力は一括実行せず、Neo ECO通常経路へ戻す。
        if (itemStack.isEmpty()
                || itemStack.isDamageableItem()
                || input && itemStack.getItem().hasCraftingRemainingItem(itemStack)) {
            return false;
        }
        return true;
    }
}
