# Advanced Assembly Computing codebase map

> **Navigation only.** このMapはCodex・LLM・reviewerの探索量を減らすためのindexです。製品仕様の正本はREADMEと現行docsです。

## 使い方

1. [`../AGENTS.md`](../AGENTS.md)を読む。
2. 下のTask routeを1つ選ぶ。
3. `Read first`と`Source scope`だけを開き、symbol検索から始める。
4. test failureまたはcompile dependencyが示した場合だけscopeを広げる。

初期読込の対象外:

```text
build/**
.gradle/**
生成JAR / run directory / logs
全resourcesの一括読込
全docs / 全testの再帰読込
対象外package
```

## 固定座標

```text
Minecraft              1.21.1
NeoForge               21.1.247+
Java                   21
AE2                    19.2.17
Neo ECO AE Extension   21.1.1
ACO                    1.6.x
AQE                    optional 2.3.x
```

## Task router

| Route | Task | Read first | Source scope | Verification scope |
| --- | --- | --- | --- | --- |
| `C0` | 製品定義、ownership、文書 | `../README.md`, `FEATURE_OWNERSHIP.md`, `IMPLEMENTATION.md` | docs中心 | 文書差分、`build` |
| `X1` | exact recipe proof、BigInteger coefficient、escrow/receipt | `IMPLEMENTATION.md`, `TESTING.md` | `execution`, `integration` | execution/integration tests |
| `M1` | Neo ECO/AE2 Mixin、runtime method contract、persistence hook | `IMPLEMENTATION.md`, `TESTING.md` | `mixin`と直接呼出先だけ | `NeoEcoRuntimeMethodContractTest`、関連test |
| `B1` | vector multiblock、controller/worker/core、block entity | READMEのstructure節 | `multiblock`, `block`, `blockentity`, `registry`, `tier` | `AACMultiBlocksTest`、実structure確認 |
| `K1` | config、throughput、progress/power、feature gate | READMEのConfiguration、`IMPLEMENTATION.md` | `config`, `tier`, config参照元 | config test、起動確認 |
| `R1` | registry、recipe、loot、models、lang | READMEのAdded Blocks/Optional Recipes | `registry`, `src/main/resources`の対象namespaceだけ | resource validation、game load |
| `V1` | Build、CI、release、test evidence | `TESTING.md`, `.github/workflows/build.yml` | `build.gradle`, `gradle.properties`, `src/test` | `clean test build` |

## Package map

| Package/path | Responsibility |
| --- | --- |
| `AdvancedAssemblyComputing.java` | NeoForge mod entrypointとcommon registration |
| `execution` | physical Thread work、recipe proof、terminal receipt ledger、wide-stack math |
| `integration` | ACO batch adapter登録とNeo ECOへの接続 |
| `mixin` | Neo ECO Worker/Thread/Pattern Bus、AE network persistence、cluster計算への限定hook |
| `multiblock` | AAC controller/worker/coreを使うstructure definition |
| `block`, `blockentity` | blockとpersistent runtime state |
| `registry` | blocks/items/block entities/tabs/tier registration |
| `config` | common configurationとlimits |
| `tier` | AAC performance tier metadata |
| `item` | item表示などの小規模client/common behavior |
| `src/main/resources` | NeoForge metadata、Mixin config、assets、recipes、loot/tags |
| `src/test` | exact accounting、receipt、runtime method、multiblock、math contracts |

## 主要entrypointとhot files

| Purpose | Path |
| --- | --- |
| Mod entrypoint | `src/main/java/com/syaru/advancedassemblycomputing/AdvancedAssemblyComputing.java` |
| ACO adapter | `src/main/java/com/syaru/advancedassemblycomputing/integration/AACCraftingTableBatchAdapter.java` |
| Integration bootstrap | `src/main/java/com/syaru/advancedassemblycomputing/integration/AACIntegrationBootstrap.java` |
| Receipt ledger | `src/main/java/com/syaru/advancedassemblycomputing/execution/AACCraftingTableTerminalReceiptLedger.java` |
| Recipe proof | `src/main/java/com/syaru/advancedassemblycomputing/execution/VerifiedCraftingTableRecipe.java` |
| Pattern Bus bridge | `src/main/java/com/syaru/advancedassemblycomputing/mixin/ECOCraftingPatternBusBatchMixin.java` |
| Thread bridge | `src/main/java/com/syaru/advancedassemblycomputing/mixin/ECOCraftingThreadBatchMixin.java` |
| Worker bridge | `src/main/java/com/syaru/advancedassemblycomputing/mixin/ECOCraftingWorkerBatchMixin.java` |
| Structure definition | `src/main/java/com/syaru/advancedassemblycomputing/multiblock/AACMultiBlocks.java` |
| Config | `src/main/java/com/syaru/advancedassemblycomputing/config/AACConfig.java` |
| Mixin descriptor | `src/main/resources/advanced_assembly_computing.mixins.json` |
| Mod metadata | `src/main/resources/META-INF/neoforge.mods.toml` |

大型fileは全文から読まず、対象transaction/method/mixin targetを検索して必要範囲だけ読む。

## Test map

| Concern | Primary tests |
| --- | --- |
| Receipt durability/idempotency | `execution/AACCraftingTableTerminalReceiptLedgerTest.java` |
| One-craft proof | `execution/VerifiedCraftingTableRecipeTest.java` |
| Adapter registration | `integration/AACIntegrationBootstrapTest.java` |
| Neo ECO target compatibility | `mixin/NeoEcoRuntimeMethodContractTest.java` |
| Structure rules | `multiblock/AACMultiBlocksTest.java` |
| Arithmetic | `util/LongBatchStackMathTest.java`, `util/VectorBatchMathTest.java` |

## 文書の読み分け

| Need | Document |
| --- | --- |
| ユーザー向け契約、dependencies、config | `../README.md` |
| feature ownership | `FEATURE_OWNERSHIP.md` |
| runtime/execution設計 | `IMPLEMENTATION.md` |
| test手順と受入 | `TESTING.md` |
| 調査背景 | `RESEARCH.md` |
| release history | `../CHANGELOG.md`, release notes（必要なversionだけ） |

## 最小検証コマンド

```text
./gradlew test --no-daemon
./gradlew clean test build --no-daemon
```

実NeoForge process、実multiblock、power consumption、save/restartを実行していない場合は、その未実施を結果に明記する。

## 省トークン用prompt

```text
AGENTS.mdとdocs/CODEBASE_MAP.mdの<Route ID>だけを基準に作業する。
Task: <作業内容>
最初はroute記載の文書、package、直近test以外を読まない。
別scopeへ広げる場合はcompile dependencyまたはtest failureを根拠として示す。
```
