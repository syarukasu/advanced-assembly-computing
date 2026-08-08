# Implementation

## Integration Method

AAC subclasses Neo ECO's crafting controller, parallel core, worker, and Block
Entity classes. It does not create an independent cluster.

`NECraftingClusterCalculatorMixin` changes only two predicates while an AAC
controller is being validated:

- the Worker row requires AAC Workers;
- the parallel-core row requires AAC Parallel Cores.

Normal Neo ECO structures retain their original predicates.

## ACO API Contract

AAC requires ACO Pattern Batch API version `4`.

AAC uses only ACO's public packages. Recovery receives the public read-only
`api.batch.v2.BatchTransactionRecord` view; it does not import ACO's internal
transaction, config, batch, engine, lifecycle, or mixin packages. The build
has a dedicated forbidden-import task for this boundary. The public target,
receipt, exact-count, and provider-ownership interfaces are checked before
the AAC adapter is registered. Revision/wakeup support is optional on the
Forge 1.20.1 contract and is reported as such; it is not silently treated as
available.

`ECOCraftingPatternBusBatchMixin` implements:

- `ProviderOwnedPatternBatchTarget`;
- `CraftingTableBatchTarget`;
- `NativeBatchReceiptStore`.

`ECOCraftingWorkerBatchMixin` assigns one request to one free
`ECOCraftingThread`.

`ECOCraftingThreadBatchMixin` owns the one-assemble proof and exact output
sidecar.

## Request Validation

A request is accepted only when:

- the controller is a formed AAC controller;
- the Pattern Bus currently exposes the exact Pattern;
- the Pattern is molecular-assembler compatible;
- the Worker has or can create a free physical Thread;
- the real assemble result is non-empty;
- actual output and remaining items equal the Pattern declaration;
- actual one-craft input totals multiplied by the coefficient equal ACO's
  aggregate input totals;
- actual one-craft outputs multiplied by the coefficient equal ACO's expected
  output totals;
- Neo ECO accepts the physical coolant check for one real craft.

The coefficient is never converted into a Thread count or loop bound.

All representative and checked-long stack conversions complete before AAC
consumes coolant or fires the crafting event. A conversion failure therefore
leaves no physical side effect and no rejected Thread entry.

## Thread Representation

For `AE2_JOB`, all aggregate stacks must fit signed `long`. The Thread retains
those real long stacks so Neo ECO and AE2 can perform their original CPU output
accounting.

For `BIG_INTEGER_JOB`, the Thread stores only representative one-craft stacks.
The exact aggregate output lives in the AAC sidecar and ACO parent transaction.
Representative stacks are blocked from normal recovery, drops, and ME output
because doing so would duplicate inventory.

## Terminal Receipt

Before a completed BigInteger Thread is released:

1. Worker snapshots `OUTPUT_READY`.
2. Worker records transaction ID, payload digest, and exact outputs in
   `AACCraftingTableTerminalReceiptLedger`.
3. Worker calls the Neo ECO output-flush completion using representative
   outputs as already accepted.
4. Thread is released.
5. Worker continues to report `ACKNOWLEDGED` until ACO calls `forget`.

The terminal ledger is bounded to `16,384` receipts and `65,536` output keys per
receipt. These are corruption and memory limits, not requested-quantity limits.

Malformed terminal-ledger entries are quarantined individually when their
identity is still provable. Structurally malformed ledgers remain locked as a
whole and are never silently discarded.

## Runtime Ownership Index

Pattern Bus and Worker keep transient UUID indexes:

```text
transaction UUID -> Worker
transaction UUID -> Thread
```

The index is not authoritative and is not saved. On restart or a structure
change it is rebuilt from Neo ECO's persisted Worker and Thread state with one
bounded scan. Every cached Worker is also checked against the current Block
Entity at its saved position before use.

Normal snapshot, acknowledge, forget, and cancel polling therefore use direct
lookup instead of repeatedly scanning every Worker and Thread. A missing lookup
is remembered until a new acceptance or reload invalidates that negative entry.

## Revision and Wakeup Path

AAC keeps separate monotonic ownership, progress, receipt, and capacity
revisions. A Thread snapshot is reused while its progress and output-ready
state are unchanged; unchanged polling therefore does not allocate a new
`CraftingTableBatchSnapshot` or output map.

The Neo ECO `wakeTickingDevice` lifecycle hook is invoked only after an AAC
ownership, receipt, acknowledge, or cancellation transition. An accounting-only
BigInteger Thread that is already `OUTPUT_READY` returns `SLEEP` instead of
forcing Neo ECO's `URGENT` path every tick. The wakeup restores the normal
worker schedule before the target is acknowledged or cancelled.

Ready Threads are kept in an identity set, so AAC's custom output flush does
not scan every Thread merely to discover that no output is ready. The set is
rebuilt once from persisted Neo ECO state after load, and stale entries are
removed when a Thread is cleared.

`AACPerformanceMetrics` exposes counters for avoided polls, wakeups, one-time
index rebuilds, Thread scans, snapshot allocations, output-ready sleep ticks,
and avoided accounting-only urgent ticks. These counters are diagnostics only;
they do not decide ownership or invent a result when a notification is absent.

## Physical Progress and Power

AAC keeps Neo ECO's physical Thread tick.

- default progress is `100` per tick;
- Neo ECO's full progress is `100`;
- default power multiplier is `64`;
- one accepted recipe step occupies one physical Thread;
- requested quantity does not multiply physical Thread count or tick loops.

The old synthetic whole-tree duration, fixed micro-AE schedule, and direct
conversion executor do not exist.

## Normal AE2 Batch Adapter

`AACCraftingTableBatchAdapter` implements ACO Transactional Batch V2 for normal
signed-long jobs.

It:

- preserves provider-owned target identity;
- prepares exact scaled long inputs and outputs;
- writes source and target receipts;
- reports one physical CPU operation;
- lets Neo ECO own physical energy;
- reconciles pending ownership from Worker sidecars after restart.

The adapter may reduce the execution coefficient so every normal AE2 aggregate
stack fits signed `long`.

Input safety is checked per crafting slot because Neo ECO persists those slot
entries independently. Output and remaining-item safety is checked after
key-wise merging because Neo ECO combines them into a `KeyCounter`. Thus nine
identical input slots may each carry `Long.MAX_VALUE`, while a single slot with
coefficient nine is limited to `Long.MAX_VALUE / 9`.

## BigInteger Physical Adapter

ACO's physical tree calls the same `CraftingTableBatchTarget` directly with
`BIG_INTEGER_JOB`.

AAC does not:

- extract BigInteger ME inventory;
- choose parent recipe order;
- hold parent capacity;
- credit intermediate escrow;
- insert final root output;
- complete the parent CPU task.

These remain ACO responsibilities.

## Structure Capacity

The AAC controller derives available physical Thread capacity from:

```text
formed structure worker length * physicalThreadsPerWorker
```

The result remains bounded by Neo ECO's integer-facing structure APIs. This
capacity limits concurrent physical recipe steps, not total logical craft
executions.

## Optional AQE Dependency

No Java class references AQE. `mods.toml` declares AQE optional.

The three AQE progression recipes use `forge:conditional` with
`forge:mod_loaded`, preventing unresolved AQE item IDs when AQE is absent.

## Dedicated Server Safety

Common code references no client-only Minecraft class. AAC uses no Bukkit,
Paper, Spigot, or Arclight API.

Mixins are required and pinned to the exact Neo ECO `20.3.0` manifest in
`docs/contracts/1.20.1.json`. A missing target must fail before a job is
accepted rather than leave decorative hardware with incorrect accounting.
