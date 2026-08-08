# Advanced Assembly Computing

Advanced Assembly Computing (AAC) is a Forge 1.20.1 add-on that connects AE2
Crafting Optimizer's exact crafting-table transactions to Neo ECO AE
Extension's real crafting multiblock.

AAC is not a second crafting planner. It is the physical executor:

- ACO plans and owns exact accounting.
- AAC routes one proven recipe step to a Neo ECO Worker.
- Neo ECO owns real Thread progress, power, and persistence.

Requested quantity is an exact multiplication coefficient. It does not create
one Worker, Thread, Pattern push, or Java loop per craft. The persistent branch
is `mc/1.20.1`, and its artifact is named `aac<version>_1.20.1.jar`. The
NeoForge line is maintained independently on `mc/1.21.1`.

## Target Environment

- Minecraft `1.20.1`
- Forge `47.4.18+`
- Java `17`
- Applied Energistics 2 `15.4.10`
- Neo ECO AE Extension `20.3.0`
- AE2 Crafting Optimizer `1.5.4+` in the `1.5.x` series
- Advanced Quantum Engineering `2.1.2` through `2.2.x` is optional
- Dedicated server, singleplayer, and Arclight as a normal Forge mod

AE2, Neo ECO, ACO, and AAC are required on both client and server. AQE is not a
code dependency. AAC's AQE progression recipes load only when AQE is present.

## Added Blocks

- `advanced_assembly_computing:vector_crafting_controller`
- `advanced_assembly_computing:vector_crafting_parallel_core`
- `advanced_assembly_computing:vector_crafting_worker`

The structure keeps Neo ECO's original casing, interface, Pattern Bus, vent,
hatches, orientation, mirroring, and size rules. An AAC controller requires AAC
workers and AAC parallel cores in the corresponding rows; mixing L9 performance
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
representative stacks, and a terminal-receipt reservation complete before any
coolant or event side effect. Thread start and physical coolant commit happen
once; a rejected or pre-commit exception rolls the physical Thread back and
releases the reservation. A post-commit exception is retained as a quarantine
instead of retrying the request. Crafting-event listener failures are logged
after the physical commit and cannot revoke ownership or duplicate the work.

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

### Thread sidecar quarantine

An AAC Thread sidecar that fails schema, identifier, mode, AEKey, duplicate-key,
or count validation is retained as a `QUARANTINED` sidecar. Its original NBT,
failure category, bounded summary, and any safely readable UUIDs are preserved.
The Thread is excluded from new work, exact-output snapshots are empty, and
neither ME recovery nor block-break drops can consume the uncertain stacks.
Quarantine is persistent and is not cleared by normal `clearWork`; recovery or
discard must be an explicit administrator action.

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
maximumExecutionsPerWave = 9223372036854775807
```

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
components when AQE is installed. They are Forge-conditional recipes, so AAC
can load without AQE.

KubeJS may remove or replace these recipe IDs normally.

## Visuals

Blockstates and item models reference Neo ECO L9 models directly. AAC does not
copy Neo ECO textures. AAC BlockItems add the normal enchantment glint.

## Build

Pass the Forge 1.20.1 ACO contract explicitly, then:

```powershell
.\gradlew.bat clean build --no-daemon
# -PacoJar=C:/path/to/ae2-crafting-optimizer-<version>.jar
```

The output JAR is written to `build/libs`.

## License

AAC is licensed under `GPL-3.0-only` because it directly subclasses and
integrates with GPLv3 Neo ECO classes. Dependency source, models, and textures
are not redistributed.

Report AAC issues to this project first. Do not report an AAC-only failure to
Neo ECO, AE2, ACO, or AQE until it reproduces without AAC.
