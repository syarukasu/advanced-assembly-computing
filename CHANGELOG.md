# Changelog

## [Unreleased]

## [1.0.7] - 2026-08-13

### Added

- Added a configurable `minimumLogicalExecutions` threshold so small normal
  AE2 jobs return immediately to Neo ECO's ordinary physical-thread path.
- Added persistent `QUARANTINED` Thread sidecars with bounded diagnostics and
  raw-NBT preservation for malformed or unknown state.
- Added descriptor-level Neo ECO 21.1.1 Mixin contract tests and expanded
  receipt, cancellation, restart, expiry, and corruption regression tests.
- Added AQE-present and AQE-absent resource-contract gates for the three
  optional progression recipes and their four referenced AQE item models.

### Changed

- Replaced the Pattern Bus dependency on ACO implementation internals with an
  AAC-local ledger that uses only ACO's public receipt API.
- Added a platform-specific 1.21.1 contract manifest and artifact boundary.

### Fixed

- Reserve terminal-receipt capacity before physical side effects and release
  only uncommitted reservations on rejection or cancellation.
- Prevent legacy, duplicate, malformed, or explicitly corrupted receipt NBT
  from being silently normalized into an authoritative ownership record.
- Prevent quarantined work from being reallocated, recovered, dropped, or
  acknowledged without an explicit administrator recovery decision.

## [1.0.6] - 2026-08-10

### Changed

- Synchronized the NeoForge 1.21.1 artifact version with the Forge 1.20.1
  AAC 1.0.6 maintenance release.
- Kept the NeoForge runtime implementation unchanged because the public batch
  payload-digest compatibility repair applies to the Forge 1.20.1 sidecar
  implementation.
- Aligned runtime dependency metadata with ACO 1.5.9 and AQE 2.2.4.

## [1.0.4] - 2026-08-08

### Changed

- Synchronized the NeoForge artifact version with the Forge 1.20.1 AAC 1.0.4
  maintenance release.
- Kept the NeoForge runtime implementation unchanged because the reported
  Mixin classloader failure is specific to the Forge sidecar implementation.

## [1.0.3] - 2026-08-08

### Changed

- Synchronized the NeoForge artifact version with the Forge 1.20.1 AAC 1.0.3
  maintenance release.
- The 1.20.1 line fixes the crafting-thread sidecar classloader failure; this
  line keeps the same versioning scheme without changing its NeoForge runtime
  implementation.

## [1.0.2] - 2026-08-08

### Changed

- Published the NeoForge 1.21.1 build with the same AAC mod version `1.0.2`.
- Kept the Minecraft version (`1.21.1`) separate from the AAC mod version and
  artifact suffix.

## [1.1.1] - 2026-08-08

### Changed

- Replaced AAC's repeated Worker/Thread lookup scans with revision-aware
  transaction indexes. Full rebuilds now happen only after persistence load or
  an explicit rebuild request; stale lookup entries fail closed without
  scanning the whole structure.
- Added ownership, progress, receipt, capacity, and aggregate revisions with
  weak ACO wakeup notifications. ACK and cancellation wake Neo ECO directly,
  while accounting-only output waits use `SLEEP` instead of an `URGENT` tick.
- Reused immutable live and terminal-receipt snapshots while their revision is
  unchanged, and added counters for avoided polls, wakeups, index rebuilds,
  thread scans, snapshot allocations, and output-ready sleep ticks.
- Updated and verified the AAC build contract against the ACO 1.6.x public
  contract.

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
