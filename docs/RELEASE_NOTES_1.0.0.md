# Advanced Assembly Computing 1.0.0

## English

Advanced Assembly Computing adds an upper-tier Neo ECO AE crafting
multiblock for quantity-independent physical crafting-table batches.

- Adds upper crafting controller, parallel core, and worker blocks while
  preserving Neo ECO's existing multiblock rules.
- Uses real Neo ECO Pattern Bus, Worker, and Crafting Thread execution.
- Integrates with ACO 1.5.4 for exact long and BigInteger recipe counts.
- Performs one real Minecraft `assemble` call per proven crafting-table
  recipe step, then applies the verified execution coefficient through ACO.
- Stores durable Worker terminal receipts before releasing a Thread.
- Supports receipt replay protection, restart recovery, and exact-output
  validation.
- Uses the same physical proof path for normal long and BigInteger jobs.
- Keeps AQE optional; AQE progression recipes are loaded only when AQE is
  installed.
- Reuses Neo ECO L9 models at runtime and adds enchantment glint without
  redistributing Neo ECO texture files.

Required versions:

- Minecraft 1.20.1
- Forge 47.4.18 or newer
- Applied Energistics 2 15.4.10
- Neo ECO AE 20.3.0
- AE2 Crafting Optimizer 1.5.4

Install the same JAR on the dedicated server and every client.

## 日本語

Advanced Assembly Computingは、注文数量に依存しない作業台物理Batchを
実行するNeo ECO AE上位マルチブロックを追加します。

- Neo ECO本来の構造ルールを維持した上位Controller、Parallel Core、
  Workerを追加。
- Neo ECOの実Pattern Bus、Worker、Crafting Threadを使って処理。
- ACO 1.5.4と連携し、longおよびBigIntegerの正確なレシピ実行数を扱う。
- 証明済みの作業台レシピ段ごとにMinecraftの実`assemble`を一回実行し、
  検証済み実行係数をACO経由で反映。
- Thread解放前にWorker端末Receiptを永続保存。
- Receiptの再送防止、再起動復旧、正確な出力検証に対応。
- 通常long注文とBigInteger注文で同じ物理証明経路を使用。
- AQEは任意依存のまま維持し、AQE進行レシピはAQE導入時だけ読み込む。
- Neo ECOのテクスチャを再配布せず、実行時にL9モデルを参照して
  エンチャント光を追加。

必須バージョン:

- Minecraft 1.20.1
- Forge 47.4.18以上
- Applied Energistics 2 15.4.10
- Neo ECO AE 20.3.0
- AE2 Crafting Optimizer 1.5.4

専用サーバーと全クライアントへ同じJARを導入してください。
