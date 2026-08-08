# Changelog

## [Unreleased]

## [1.0.3] - 2026-08-08

### Fixed

- Moved the crafting-thread sidecar validation exception outside the configured
  Mixin package so existing Vector Crafting Workers can load without an
  `IllegalClassLoadError`.
- Preserved malformed-sidecar quarantine and failure-category reporting.

## [1.0.2] - 2026-08-08

### Changed

- Added revision-driven worker wakeups and cached batch snapshots.
- Removed repeated full-thread searches from the normal output-ready path.
- Synchronized the required ACO contract with ACO 1.5.7.

## [1.0.1] - 2026-07-29

### Fixed

- Verified after registration that ACO retained AAC's exact native
  crafting-table adapter instance. Missing or replaced registrations now fail
  during integration setup before any transaction can start.
- Added startup identity diagnostics for the loaded AAC version, adapter ID,
  ACO Pattern Batch API, and terminal receipt schema.
- Added a regression test for adapter identity replacement.

## [1.0.0] - 2026-07-28

### Added

- Added upper Neo ECO crafting controller, parallel core, and worker blocks.
- Added AAC-only performance rows while preserving Neo ECO structure rules.
- Added Neo ECO L9 model reuse with enchantment glint.
- Added durable Worker terminal output receipts written before Thread release.
- Added explicit terminal-receipt forget after ACO escrow accounting.
- Added exact output validation for both live and recovered completion receipts.
- Added automated receipt replay, mismatch, and 1,024-digit formula tests.

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
