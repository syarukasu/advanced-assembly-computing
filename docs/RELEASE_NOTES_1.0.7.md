# Advanced Assembly Computing 1.0.7 - Forge 1.20.1

## English

AAC 1.0.7 moves the physical crafting integration to Neo ECO AE 20.4.0 and
hardens transaction ownership across rejection, cancellation, save, reload,
and malformed NBT.

### Highlights

- Supports Neo ECO AE Extension 20.4.0.
- Adds `minimumLogicalExecutions` for immediate fallback of small normal jobs.
- Reserves durable receipt capacity before physical side effects.
- Preserves malformed Thread sidecars as persistent `QUARANTINED` records.
- Uses only ACO public APIs for Pattern Bus native receipt storage.
- Adds descriptor-level dependency checks and expanded accounting tests.
- Verifies the optional AQE recipes both without AQE and against an explicitly
  supplied Forge 1.20.1 AQE artifact.

Required: Minecraft 1.20.1, Forge 47.4.18+, AE2 15.4.10 or the verified UELM
15.5.0 profile, Neo ECO AE 20.4.0, and ACO 1.5.15-compatible 1.5.x.

## 日本語

AAC 1.0.7では、物理クラフト連携をNeo ECO AE 20.4.0へ移行し、拒否、
キャンセル、保存、再読込、破損NBTをまたぐTransaction所有権を強化しました。

### 主な変更

- Neo ECO AE Extension 20.4.0へ対応。
- 小さな通常注文を即座に通常経路へ戻す`minimumLogicalExecutions`を追加。
- 物理的な副作用より前に、永続Receipt枠を予約。
- 不正なThread sidecarを永続`QUARANTINED`状態として原文付きで保全。
- Pattern BusのNative Receipt保存をACO公開APIだけで実装。
- 依存先descriptor検査と会計回帰試験を拡充。
- AQE未導入時の条件付きレシピ境界と、明示指定したForge 1.20.1 AQE成果物を
  使う素材契約を検証。

必須環境はMinecraft 1.20.1、Forge 47.4.18以降、AE2 15.4.10または検証済み
UELM 15.5.0プロファイル、Neo ECO AE 20.4.0、ACO 1.5.15互換の1.5.xです。
