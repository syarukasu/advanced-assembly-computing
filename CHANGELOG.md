# Changelog

## [Unreleased]

## [1.1.0] - 2026-08-02

### Added

- Added the NeoForge 1.21.1 release line using Java 21.
- Added pinned support for AE2 19.2.17, Neo ECO AE Extension 21.1.1, ACO
  1.6.x, and optional AQE 2.3.x.

### Changed

- Migrated the production build, resources, persistence hooks, and Neo ECO
  multiblock integration to NeoForge 21.1.247.
- Kept AAC 1.0.x and its release artifacts as the Forge 1.20.1 line.

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
