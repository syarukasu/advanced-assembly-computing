# Advanced Assembly Computing 1.0.7 - NeoForge 1.21.1

## English

AAC 1.0.7 hardens transaction ownership across rejection, cancellation, save,
reload, and malformed NBT while preserving Neo ECO AE 21.1.1's real physical
crafting path.

### Highlights

- Adds `minimumLogicalExecutions` for immediate fallback of small normal jobs.
- Reserves durable receipt capacity before physical side effects.
- Preserves malformed Thread sidecars as persistent `QUARANTINED` records.
- Replaces ACO implementation-internal receipt access with public APIs only.
- Adds the 1.21.1 platform contract and artifact boundary checks.
- Adds descriptor-level Neo ECO 21.1.1 checks and expanded accounting tests.
- Verifies the optional AQE recipes both without AQE and against an explicitly
  supplied NeoForge 1.21.1 AQE artifact.

Required: Minecraft 1.21.1, NeoForge 21.1.247+, AE2 19.2.17, Neo ECO AE
21.1.1, and ACO 1.5.15-compatible 1.5.x.

## 日本語

AAC 1.0.7では、Neo ECO AE 21.1.1の実物理クラフト経路を維持しながら、
拒否、キャンセル、保存、再読込、破損NBTをまたぐTransaction所有権を
強化しました。

### 主な変更

- 小さな通常注文を即座に通常経路へ戻す`minimumLogicalExecutions`を追加。
- 物理的な副作用より前に、永続Receipt枠を予約。
- 不正なThread sidecarを永続`QUARANTINED`状態として原文付きで保全。
- ACO内部実装へのReceipt依存を廃止し、公開APIだけを利用。
- 1.21.1専用の契約manifestと成果物境界検査を追加。
- Neo ECO 21.1.1のdescriptor検査と会計回帰試験を拡充。
- AQE未導入時の条件付きレシピ境界と、明示指定したNeoForge 1.21.1 AQE成果物を
  使う素材契約を検証。

必須環境はMinecraft 1.21.1、NeoForge 21.1.247以降、AE2 19.2.17、
Neo ECO AE 21.1.1、ACO 1.5.15互換の1.5.xです。
