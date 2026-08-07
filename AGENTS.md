# Advanced Assembly Computing agent entrypoint

このファイルはCodex・LLM・自動レビューが、リポジトリ全体を毎回読み込まずに作業範囲を決めるための入口です。

## 最小読込手順

1. 最初に本書と [`docs/CODEBASE_MAP.md`](docs/CODEBASE_MAP.md) だけを読む。
2. MapのTask routeを1つ選び、そのrouteに書かれた文書・package・直近testだけを開く。
3. Javaファイルは全文読込から始めず、対象symbolを検索して必要な範囲だけ読む。
4. compile error、test failure、実依存関係が示した場合だけ隣接packageへ範囲を広げる。
5. `build/**`、`.gradle/**`、生成JAR、全resources、全docs、全testの再帰読込を開始条件にしない。

## 固定契約

```text
Minecraft                 1.21.1
Loader                    NeoForge 21.1.247+
Runtime Java              21
Applied Energistics 2     19.2.17
Neo ECO AE Extension      21.1.1
AE2 Crafting Optimizer    1.6.x
Advanced Quantum Engineering 2.3.x optional
Sides                     client + server
```

役割分担を変更しません。

```text
ACO      = crafting計画、exact input/output accounting、escrow所有
AAC      = 証明済み1 recipe stepのphysical executor
Neo ECO  = Worker/Thread、progress、power、structure、persistence
AQE      = optional progression recipe dependencyのみ
```

AACを第二のcrafting plannerにしません。要求数に比例するWorker、Thread、Pattern push、Java loopを作りません。BigInteger transactionへ`longValue()`を使わず、完全にfitすると証明された経路だけexact conversionを行います。

## 安全規則

- input ownership前に全checked conversionとrecipe proofを完了する。
- whole crafting treeをroot outputへcollapseしない。
- child outputがescrowへcreditされる前にparent stepを開始しない。
- receiptはtransaction UUID、payload digest、exact outputを一致確認して一度だけcreditする。
- Mixin targetやNeo ECO runtime methodが不一致なら推測で継続しない。
- build/test成功だけで実Minecraft、実multiblock、restart recoveryを検証済みと書かない。

## 編集規則

- source変更では同じpackageの`src/test`と [`docs/TESTING.md`](docs/TESTING.md) を先に確認する。
- ownership変更は [`docs/FEATURE_OWNERSHIP.md`](docs/FEATURE_OWNERSHIP.md) とREADMEを同じ変更で更新する。
- entrypoint、主要package、重要testの位置が変わる場合は `docs/CODEBASE_MAP.md` を更新する。
- `AACCraftingTableBatchAdapter`、`ECOCraftingThreadBatchMixin`、receipt ledgerなどの大型fileは対象method周辺だけを読む。

## 検証順

```text
対象test class
-> ./gradlew test --no-daemon
-> ./gradlew clean test build --no-daemon
-> 必要な場合だけNeoForge実環境でmultiblock / power / save / restart確認
```

unit testやCIだけの結果をruntime verifiedとして扱いません。
