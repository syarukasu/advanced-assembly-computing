# Advanced Assembly Computing

Advanced Assembly Computing (AAC) is a NeoForge 1.21.1 add-on that connects AE2
Crafting Optimizer's exact crafting-table transactions to Neo ECO AE
Extension's real crafting multiblock.

AAC is not a second crafting planner. It is the physical executor:

- ACO plans and owns exact accounting.
- AAC routes one proven recipe step to a Neo ECO Worker.
- Neo ECO owns real Thread progress, power, and persistence.

Requested quantity is an exact multiplication coefficient. It does not create
one Worker, Thread, Pattern push, or Java loop per craft.

## Target Environment

- Minecraft `1.21.1`
- NeoForge `21.1.247+`
- Java `21`
- Applied Energistics 2 `19.2.17`
- Neo ECO AE Extension `21.1.1`
- AE2 Crafting Optimizer `1.5.15` through the compatible `1.5.x` contract
- Advanced Quantum Engineering `2.2.4` through the compatible `2.2.x` line is optional
- Dedicated server and singleplayer

The persistent branch for this line is `mc/1.21.1`, and its artifact is named
`aac<version>_1.21.1.jar`. The independent Forge 1.20.1 line is maintained on
`mc/1.20.1` and uses `aac<version>_1.20.1.jar`.

AE2, Neo ECO, ACO, and AAC are required on both client and server. AQE is not a
code dependency. AAC's AQE progression recipes load only when AQE is present.

## Versioned Contracts

The exact dependency and bytecode contract is recorded in
`docs/contracts/1.21.1.json`. AAC imports only
`com.syaru.ae2craftingoptimizer.api.*`; the build rejects ACO implementation
package imports. Required Neo ECO methods and fields are checked by exact JVM
descriptors before release.

## Platform Releases

- `mc/1.20.1` uses Java 17 and produces `aac<version>_1.20.1.jar`.
- `mc/1.21.1` uses Java 21 and produces `aac<version>_1.21.1.jar`.

Release tags are Minecraft-qualified: `aac-v<version>-mc1.20.1` and
`aac-v<version>-mc1.21.1`. Platform-specific Mixin and persistence code is not
shared as a compiled binary.

## Added Blocks

- `advanced_assembly_computing:vector_crafting_controller`
- `advanced_assembly_computing:vector_crafting_parallel_core`
- `advanced_assembly_computing:vector_crafting_worker`

The structure keeps Neo ECO's original casing, interface, Pattern Bus, vent,
hatches, orientation, mirroring, and size rules. An AAC controller requires AAC
workers and AAC parallel cores in the corresponding rows; mixing lower-tier performance
parts does not form the upper structure.

## Execution Model

### One Real Craft Plus an Exact Coefficient

For each accepted crafting-table step:

1. AAC fills a real Neo ECO Thread crafting inventory from the selected AE2
   Pattern slots.
2. It calls the Pattern's real `assemble` once.
3. It verifies the actual output and remaining items against the encoded
   Pattern declaration.
4. It verifies that `one-craft formula * exact executions` equals ACO's
   aggregate input and expected output maps.
5. It starts one real Neo ECO Thread.
6. Neo ECO advances real progress and consumes real power.
7. On completion, AAC returns the exact multiplied output as a durable Worker
   receipt.

AAC fires one crafting event for the one real assemble. It never fires events
once per logical execution.

Acceptance uses a prepare/commit boundary. Recipe proof, exact conversions,
representative stacks, and terminal-receipt reservation complete before
coolant or crafting-event side effects. Rejection and pre-commit failure
release the reservation. A failure after physical commit is quarantined rather
than retried.

### Multi-Stage Trees

AAC does not collapse an entire tree to its root output. ACO sends individual
recipe steps in dependency order.

A child step's output must be credited to ACO escrow before its parent step can
start. A linear twenty-stage chain therefore runs twenty physical stages.
Independent branches can use different Workers simultaneously.

### Long and BigInteger

Normal signed-long AE2 jobs and BigInteger parent jobs use the same
`CraftingTableBatchRequest` and one-assemble proof.

- `AE2_JOB`: Neo ECO and AE2 retain their normal long CPU accounting.
- `BIG_INTEGER_JOB`: ACO retains exact parent accounting and AAC stores only a
  representative one-craft Thread plus exact output sidecar.

AAC never calls `longValue()` on a BigInteger transaction. It uses exact
conversion only for a path whose complete counts were already proven to fit.

## Receipt Safety

The Worker-local terminal ledger stores:

- transaction UUID;
- payload digest;
- exact BigInteger output map.

At common setup AAC verifies that ACO retained the exact adapter instance
registered for `advanced_assembly_computing:native_crafting_table_batch`.
Missing or replaced registration fails before a transaction can own inputs.

The Worker records this receipt before releasing the physical Thread. ACO
credits the receipt once and then explicitly deletes it. A mismatched payload,
changed output, duplicate transaction, malformed NBT, or over-limit ledger is
rejected rather than overwritten.

Receipt entries use a versioned state and payload identity. Legacy native
receipts without a payload digest remain raw and non-authoritative; AAC never
invents the missing identity during migration.

### Thread sidecar quarantine

An AAC Thread sidecar that fails schema, identifier, mode, AEKey, duplicate-key,
state, or count validation is retained as `QUARANTINED`. Its raw NBT, bounded
failure summary, and any safely readable UUID are preserved. The Thread is not
eligible for allocation, ME recovery, block-break drops, output accounting, or
normal `clearWork`. Recovery or discard requires an explicit administrator
decision.

Current sidecars use schema `2`. A complete legacy schema `1` sidecar is
migrated only when its full identity and payload validate; unknown current
states and incomplete identities are quarantined.

Cancellation before output completion releases only the representative Thread.
ACO owns the real input escrow and decides what must be returned.

Live transaction lookup is indexed by transaction UUID:

- Pattern Bus resolves a transaction directly to its owning Worker.
- Worker resolves it directly to its owning Thread.
- The indexes are rebuilt with one bounded scan only after restart or structure
  change; normal polling does not rescan the full multiblock.

All checked-long conversions are completed before coolant consumption or
crafting-event emission. A rejected request is never appended to the Worker's
Thread list.

Nine crafting slots containing the same key remain nine independent signed-long
entries. AAC does not reject that valid shape merely because their merged
BigInteger total exceeds `Long.MAX_VALUE`.

## Power and Progress

AAC does not invent a tree-wide duration or fixed energy schedule.

- `progressPerTick` controls Neo ECO physical Thread progress.
- `powerMultiplier` is used by the AAC controller's real Neo ECO power path.
- The default `100` progress and `1` power multiplier request `100 AE/t` per
  active physical Thread.
- Logical requested quantity does not multiply the number of physical Threads.
- Every accepted recipe step still consumes physical power and exposes physical
  progress.

## Configuration

File:

```text
config/advanced_assembly_computing-common.toml
```

Defaults:

```toml
[vectorCrafting]
enableVectorExecution = true
physicalThreadsPerWorker = 256
progressPerTick = 100
powerMultiplier = 1

[nativeCraftingTableBatch]
enabled = true
requireExactPatternOwnership = true
minimumLogicalExecutions = 256
maximumExecutionsPerWave = 9223372036854775807
```

`minimumLogicalExecutions` applies after all signed-long safety limits. Smaller
normal AE2 jobs are not held for coalescing: they immediately use Neo ECO's
physical-thread path and retain AAC's one-tick progress and configured
parallelism. Exact BigInteger parent jobs are not clamped by this long-only
threshold.

`maximumExecutionsPerWave` is a coefficient ceiling, not a loop count.
Per-stack signed-long safety may reduce the normal AE2 batch. ACO's exact
BigInteger parent route does not use this value as a BigInteger clamp.

ACO's transactional V2 route must also be enabled on both sides:

```toml
[experimentalCraftingEngine]
enableTransactionalBatchingV2 = true
```

If it is disabled, AAC remains loadable but normal AE2 jobs fall back to Neo
ECO's physical-thread path. In that fallback, `physicalThreadsPerWorker` is
throughput rather than the logical output coefficient.

## Optional AQE Recipes

The upper controller, worker, and parallel-core recipes use AQE endgame
components when AQE is installed. They are NeoForge-conditional recipes, so AAC
can load without AQE.

KubeJS may remove or replace these recipe IDs normally.

## Visuals

Blockstates and item models reference Neo ECO L9 models directly. AAC does not
copy Neo ECO textures. AAC BlockItems add the normal enchantment glint.

## Build

Build AAC with the dependency directory and released ACO contract used for the
target platform:

```powershell
.\gradlew.bat clean check build --no-daemon `
  -PaacLocalModsDir=C:/path/to/1.21.1/mods `
  -PacoJar=C:/path/to/aco1.5.15_1.21.1.jar
```

The output JAR is written to `build/libs`.

## License

AAC is licensed under `GPL-3.0-only` because it directly subclasses and
integrates with GPLv3 Neo ECO classes. Dependency source, models, and textures
are not redistributed.

Report AAC issues to this project first. Do not report an AAC-only failure to
Neo ECO, AE2, ACO, or AQE until it reproduces without AAC.
