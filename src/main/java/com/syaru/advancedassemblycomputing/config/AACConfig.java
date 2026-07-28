package com.syaru.advancedassemblycomputing.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AACConfig {
    /** NeoECO L9の作業台並列数と同じ、ワーカー一台の物理Thread既定値。 */
    public static final int DEFAULT_PHYSICAL_THREADS_PER_WORKER = 256;
    public static final int DEFAULT_PROGRESS_PER_TICK = 100;
    /** 一tick完了時も一物理Threadを100 AE/tに収める既定電力倍率。 */
    public static final int DEFAULT_POWER_MULTIPLIER = 1;
    /** 一ワーカーへ設定できる物理Thread数の防御上限。 */
    private static final int MAX_PHYSICAL_THREADS_PER_WORKER = 65_536;
    // 誤設定でdouble電力計算を極端に膨らませない運用上限。
    private static final int MAX_POWER_MULTIPLIER = 1_000_000;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLE_VECTOR_EXECUTION;
    private static final ForgeConfigSpec.IntValue PHYSICAL_THREADS_PER_WORKER;
    private static final ForgeConfigSpec.IntValue PROGRESS_PER_TICK;
    private static final ForgeConfigSpec.IntValue POWER_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_EXACT_PATTERN_OWNERSHIP;
    private static final ForgeConfigSpec.BooleanValue ENABLE_NATIVE_CRAFTING_TABLE_BATCH;
    private static final ForgeConfigSpec.LongValue MAXIMUM_CRAFTING_TABLE_BATCH_EXECUTIONS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("vectorCrafting");

        ENABLE_VECTOR_EXECUTION = builder
                .comment(
                        "Neo ECOの原子的なAggressive Fast PathをAAC設備だけ拡張します。",
                        "falseではNeo ECO L9相当の通常経路へ戻ります。")
                .define("enableVectorExecution", true);

        PHYSICAL_THREADS_PER_WORKER = builder
                .comment(
                        "AACワーカー一台が同時所有できる物理Neo ECO Thread数です。",
                        "一ThreadがLong.MAX_VALUE回までを一つの合算仕事として所有するため、",
                        "論理クラフト数をこの値へ掛けたり、その個数ぶんThreadを生成したりしません。")
                .defineInRange(
                        "physicalThreadsPerWorker",
                        DEFAULT_PHYSICAL_THREADS_PER_WORKER,
                        1,
                        MAX_PHYSICAL_THREADS_PER_WORKER);

        PROGRESS_PER_TICK = builder
                .comment(
                        "一tickで進むNeo ECO作業進捗です。",
                        "Neo ECOの一工程は100進捗なので、100なら電力が足りる時に一tickです。")
                .defineInRange("progressPerTick", DEFAULT_PROGRESS_PER_TICK, 1, 100);

        POWER_MULTIPLIER = builder
                .comment(
                        "AACのNeo ECO物理Thread一個あたりの電力倍率です。",
                        "注文個数ではなく、実際に動作している物理Thread数へだけ適用します。",
                        "既定値1ではprogressPerTick=100の一Threadが100 AE/tを要求します。")
                .defineInRange("powerMultiplier", DEFAULT_POWER_MULTIPLIER, 1, MAX_POWER_MULTIPLIER);

        builder.pop();

        builder.push("nativeCraftingTableBatch");
        ENABLE_NATIVE_CRAFTING_TABLE_BATCH = builder
                .comment(
                        "ACO V2 APIを使い、AACの作業台Patternを実Workerへ一括配送します。",
                        "全ツリー直接変換ではなく、一つの作業台レシピを一つの実仕事として処理します。",
                        "falseではNeo ECO本来のPattern配送へ戻ります。")
                .define("enabled", true);
        REQUIRE_EXACT_PATTERN_OWNERSHIP = builder
                .comment(
                        "trueではPattern Busが実際に保持するPatternだけを一括Threadへ受理します。",
                        "ACO親Jobは常にAE2が返したProviderからこのBusを選ぶため、既定値trueが安全です。")
                .define("requireExactPatternOwnership", true);
        MAXIMUM_CRAFTING_TABLE_BATCH_EXECUTIONS = builder
                .comment(
                        "一つのAAC Worker仕事が所有できる論理クラフト回数です。",
                        "各入力slot、主出力、返却物の実量がsigned longへ収まるよう実行時にも縮小します。",
                        "既定値はMinecraft/AE2 APIで一回に表現できる最大の正数です。")
                .defineInRange(
                        "maximumExecutionsPerWave",
                        Long.MAX_VALUE,
                        1L,
                        Long.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private AACConfig() {
    }

    /** AAC Vector設備全体のMaster Switch。 */
    public static boolean vectorCraftingEnabled() {
        return ENABLE_VECTOR_EXECUTION.get();
    }

    public static int physicalThreadsPerWorker() {
        return Math.min(
                MAX_PHYSICAL_THREADS_PER_WORKER,
                Math.max(1, PHYSICAL_THREADS_PER_WORKER.get()));
    }

    public static int progressPerTick() {
        return PROGRESS_PER_TICK.get();
    }

    public static int powerMultiplier() {
        return POWER_MULTIPLIER.get();
    }

    public static boolean requireExactPatternOwnership() {
        return REQUIRE_EXACT_PATTERN_OWNERSHIP.get();
    }

    public static boolean nativeCraftingTableBatchEnabled() {
        return vectorCraftingEnabled()
                && ENABLE_NATIVE_CRAFTING_TABLE_BATCH.get();
    }

    public static long maximumCraftingTableBatchExecutions() {
        return Math.max(
                1L,
                MAXIMUM_CRAFTING_TABLE_BATCH_EXECUTIONS.get());
    }
}
