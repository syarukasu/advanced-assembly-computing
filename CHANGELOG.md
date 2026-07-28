# Changelog

## [Unreleased]

### Changed

- Replaced whole-tree direct conversion with ACO-owned physical
  recipe-by-recipe execution.
- Limited AAC to Neo ECO Pattern Bus, Worker, Thread, progress, power, and
  durable receipt integration.
- Normal long and BigInteger jobs now use the same one-real-assemble proof.
- Removed synthetic tree duration, fixed tree energy, coolant, and direct
  output ownership.
- Made AQE an optional dependency; AQE progression recipes are now
  Forge-conditional.
- Reduced the default physical Thread power multiplier from `64` to `1`.
  With the default progress speed, one active Thread now requests `100 AE/t`.

### Added

- Durable Worker terminal output receipts written before Thread release.
- Explicit terminal-receipt forget after ACO escrow accounting.
- Exact output validation for both live and recovered completion receipts.
- Automated receipt replay, mismatch, and 1,024-digit formula tests.

## [1.0.0] - 2026-07-25

### Added

- Added upper Neo ECO crafting controller, parallel core, and worker blocks.
- Added AAC-only performance rows while preserving Neo ECO structure rules.
- Added Neo ECO L9 model reuse with enchantment glint.
