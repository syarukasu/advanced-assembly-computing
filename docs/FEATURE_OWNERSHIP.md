# Feature Ownership

## AAC Owns

- Upper Neo ECO block and Block Entity registration
- AAC-only structure component predicates
- Pattern Bus to Worker routing
- Physical Thread capacity
- One real crafting-table assemble proof
- Neo ECO physical progress and power
- Worker live ownership and terminal receipts
- Prepare/commit boundary for physical crafting-table work
- Thread-side NBT sidecars
- Thread sidecar quarantine, raw-payload retention, and diagnostics

## ACO Owns

- Crafting graph compilation
- Selected-input and coefficient formulas
- Exact ME boundary reservation
- Transaction-local escrow
- Parent CPU job state and capacity
- BigInteger accounting
- Dependency scheduling
- Final ME output insertion
- Cancellation, restart reconciliation, and parent-job quarantine

AAC never marks an ACO parent complete.

## Neo ECO Owns

- Cluster and multiblock lifecycle
- Pattern Bus, Worker, and Thread base behavior
- Real Thread progress
- Real power consumption
- Physical Thread persistence

## AE2 Owns

- Encoded crafting Pattern
- Normal CPU jobs
- Standard Provider and inventory behavior
- Normal execution for unsupported recipes

## AQE

AQE is optional. It may own a BigInteger parent CPU through ACO and supplies
optional progression recipe materials. AAC execution code does not link to AQE
classes.

## Irreversible Boundary

AAC may reject a request before accepting it. Once a Worker owns a request, it
must expose the same live or terminal receipt until ACO acknowledges or cancels
it. It must not silently release the job and invite fallback.
