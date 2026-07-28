package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import com.syaru.advancedassemblycomputing.util.LongBatchStackMath;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 一つの作業台Patternを実際に一回だけassembleし、数量係数へ掛けてよい式かを証明する。
 *
 * <p>通常long注文とBigInteger注文は必ずこの同じ証明を通る。注文数は実レシピの
 * 反復回数ではなく、証明済み入出力へ掛ける係数としてだけ扱う。</p>
 */
public final class VerifiedCraftingTableRecipe {
    private VerifiedCraftingTableRecipe() {
    }

    public static Optional<Proof> assembleOnce(
            IMolecularAssemblerSupportedPattern pattern,
            KeyCounter[] selectedInputs,
            List<GenericStack> declaredOutputs,
            List<GenericStack> declaredRemaining,
            TransientCraftingContainer craftingInventory,
            Level level) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(selectedInputs, "selectedInputs");
        Objects.requireNonNull(declaredOutputs, "declaredOutputs");
        Objects.requireNonNull(declaredRemaining, "declaredRemaining");
        Objects.requireNonNull(craftingInventory, "craftingInventory");
        Objects.requireNonNull(level, "level");

        KeyCounter[] representativeInputs = copyCounters(selectedInputs);
        KeyCounter[] assemblyInputs = copyCounters(selectedInputs);
        craftingInventory.clearContent();
        pattern.fillCraftingGrid(
                assemblyInputs,
                craftingInventory::setItem);
        ItemStack assembled = pattern.assemble(
                craftingInventory,
                level);
        // 空成果物は有効な作業台実行として所有できない。
        if (assembled.isEmpty()) {
            craftingInventory.clearContent();
            return Optional.empty();
        }

        List<GenericStack> actualOutputs =
                toGenericStacks(List.of(assembled));
        List<GenericStack> actualRemaining =
                toGenericStacks(
                        pattern.getRemainingItems(craftingInventory));
        /*
         * Pattern宣言と実assemble結果が一致する場合だけ数量係数を適用する。
         * レシピ条件や返却物が実行時に変わるPatternは通常経路へ戻す。
         */
        if (actualOutputs.isEmpty()
                || !LongBatchStackMath.sameTotals(
                        actualOutputs,
                        declaredOutputs)
                || !LongBatchStackMath.sameTotals(
                        actualRemaining,
                        declaredRemaining)) {
            craftingInventory.clearContent();
            return Optional.empty();
        }

        return Optional.of(new Proof(
                LongBatchStackMath.flatten(representativeInputs),
                actualOutputs,
                actualRemaining,
                assembled.copy()));
    }

    private static KeyCounter[] copyCounters(KeyCounter[] source) {
        KeyCounter[] copy = new KeyCounter[source.length];
        // fillCraftingGridによる減算から会計用入力を守るため、全slotを複製する。
        for (int slot = 0; slot < source.length; slot++) {
            KeyCounter target = copy[slot] = new KeyCounter();
            // 一つのslotに登録された確定候補と数量をそのまま複製する。
            for (var entry : Objects.requireNonNull(
                    source[slot],
                    "selected input slot")) {
                target.add(
                        entry.getKey(),
                        entry.getLongValue());
            }
        }
        return copy;
    }

    private static List<GenericStack> toGenericStacks(
            List<ItemStack> stacks) {
        List<GenericStack> result = new ArrayList<>();
        // 実assembleが返した空slotを除き、有効なAEKeyだけを会計へ渡す。
        for (ItemStack stack : stacks) {
            // 空ItemStackは返却物なしを表すため無視する。
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            GenericStack generic =
                    GenericStack.fromItemStack(stack);
            // 変換不能または非正数は式を証明できないため失敗させる。
            if (generic == null
                    || generic.amount() <= 0L) {
                return List.of();
            }
            result.add(generic);
        }
        return List.copyOf(result);
    }

    /**
     * 一回分の確定入出力だけを保持する、Minecraft Registry非依存の数量式。
     */
    public record Formula(
            List<GenericStack> representativeInputs,
            List<GenericStack> outputsPerExecution,
            List<GenericStack> remainingPerExecution) {
        public Formula {
            representativeInputs = checkedStacks(
                    representativeInputs,
                    "representativeInputs",
                    false);
            outputsPerExecution = checkedStacks(
                    outputsPerExecution,
                    "outputsPerExecution",
                    true);
            remainingPerExecution = checkedStacks(
                    remainingPerExecution,
                    "remainingPerExecution",
                    false);
        }

        public Map<AEKey, BigInteger> exactInputTotals(
                BigInteger executions) {
            return scaledTotals(
                    representativeInputs,
                    requirePositive(executions));
        }

        public Map<AEKey, BigInteger> exactOutputTotals(
                BigInteger executions) {
            BigInteger checked = requirePositive(executions);
            Map<AEKey, BigInteger> result = new LinkedHashMap<>();
            mergeScaled(
                    result,
                    outputsPerExecution,
                    checked);
            mergeScaled(
                    result,
                    remainingPerExecution,
                    checked);
            return Map.copyOf(result);
        }

        public List<GenericStack> scaledOutputs(long executions) {
            return LongBatchStackMath.scale(
                    outputsPerExecution,
                    executions);
        }

        public List<GenericStack> scaledRemaining(long executions) {
            return LongBatchStackMath.scale(
                    remainingPerExecution,
                    executions);
        }

        private static List<GenericStack> checkedStacks(
                List<GenericStack> source,
                String name,
                boolean requireNonEmpty) {
            List<GenericStack> copy = List.copyOf(
                    Objects.requireNonNull(source, name));
            // 主出力だけは一件以上必要で、入力と返却物は空を許可する。
            if (requireNonEmpty && copy.isEmpty()) {
                throw new IllegalArgumentException(
                        name + " must not be empty");
            }
            // 不変Proofへnullや非正数を保存しない。
            for (GenericStack stack : copy) {
                // 一件でも不正なら数量係数を掛けられない。
                if (stack == null || stack.amount() <= 0L) {
                    throw new IllegalArgumentException(
                            name + " contains an invalid stack");
                }
            }
            return copy;
        }

        private static Map<AEKey, BigInteger> scaledTotals(
                List<GenericStack> source,
                BigInteger executions) {
            Map<AEKey, BigInteger> result =
                    new LinkedHashMap<>();
            mergeScaled(result, source, executions);
            return Map.copyOf(result);
        }

        private static void mergeScaled(
                Map<AEKey, BigInteger> target,
                List<GenericStack> source,
                BigInteger executions) {
            // 同じAEKeyが複数slotにある場合もBigInteger合計へ正確に畳み込む。
            for (GenericStack stack : source) {
                target.merge(
                        stack.what(),
                        BigInteger.valueOf(stack.amount())
                                .multiply(executions),
                        BigInteger::add);
            }
        }

        private static BigInteger requirePositive(
                BigInteger executions) {
            BigInteger checked = Objects.requireNonNull(
                    executions,
                    "executions");
            // 0回以下の係数は物理仕事を表さないため拒否する。
            if (checked.signum() <= 0) {
                throw new IllegalArgumentException(
                        "executions must be positive");
            }
            return checked;
        }
    }

    /**
     * 実assemble結果と、そこから証明した一回分数量式を一組で保持する。
     */
    public record Proof(
            Formula formula,
            ItemStack assembledOutput) {
        public Proof(
                List<GenericStack> representativeInputs,
                List<GenericStack> outputsPerExecution,
                List<GenericStack> remainingPerExecution,
                ItemStack assembledOutput) {
            this(
                    new Formula(
                            representativeInputs,
                            outputsPerExecution,
                            remainingPerExecution),
                    assembledOutput);
        }

        public Proof {
            formula =
                    Objects.requireNonNull(
                            formula,
                            "formula");
            assembledOutput =
                    Objects.requireNonNull(
                                    assembledOutput,
                                    "assembledOutput")
                            .copy();
            // 実クラフトイベントに渡す主出力は空であってはならない。
            if (assembledOutput.isEmpty()) {
                throw new IllegalArgumentException(
                        "assembledOutput must not be empty");
            }
        }

        @Override
        public ItemStack assembledOutput() {
            return assembledOutput.copy();
        }

        public List<GenericStack> representativeInputs() {
            return formula.representativeInputs();
        }

        public List<GenericStack> outputsPerExecution() {
            return formula.outputsPerExecution();
        }

        public List<GenericStack> remainingPerExecution() {
            return formula.remainingPerExecution();
        }

        /**
         * 数量ぶんではなく、実際に行った一回のassembleだけをゲームイベントへ通知する。
         */
        public void fireCraftingEvent(
                Level level,
                IMolecularAssemblerSupportedPattern pattern,
                TransientCraftingContainer craftingInventory) {
            CraftingEvent.fireAutoCraftingEvent(
                    Objects.requireNonNull(
                            level,
                            "level"),
                    Objects.requireNonNull(
                            pattern,
                            "pattern"),
                    assembledOutput(),
                    Objects.requireNonNull(
                            craftingInventory,
                            "craftingInventory"));
        }

        public Map<AEKey, BigInteger> exactInputTotals(
                BigInteger executions) {
            return formula.exactInputTotals(
                    executions);
        }

        public Map<AEKey, BigInteger> exactOutputTotals(
                BigInteger executions) {
            return formula.exactOutputTotals(
                    executions);
        }

        public List<GenericStack> scaledOutputs(
                long executions) {
            return formula.scaledOutputs(
                    executions);
        }

        public List<GenericStack> scaledRemaining(
                long executions) {
            return formula.scaledRemaining(
                    executions);
        }
    }
}
