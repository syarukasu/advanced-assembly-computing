# Research

## Investigated Artifacts

- Applied Energistics 2 `15.4.10`
- Neo ECO AE Extension `20.3.0`
- Neo ECO source commit `d0c7a1d`
- AE2 Crafting Optimizer `1.5.4`
- Advanced Quantum Engineering `2.1.2` through `2.2.x`
- InsaneAE Quantum CPU source architecture

No class name, method, Registry ID, or model path was selected from a
placeholder.

## Neo ECO Physical Path

Verified classes:

- tier API: `cn.dancingsnow.neoecoae.api.IECOTier`
- controller BE: `ECOCraftingSystemBlockEntity`
- Pattern Bus BE: `ECOCraftingPatternBusBlockEntity`
- Worker BE: `ECOCraftingWorkerBlockEntity`
- Thread: `cn.dancingsnow.neoecoae.api.me.ECOCraftingThread`
- structure calculator: `NECraftingClusterCalculator`
- cluster: `NECraftingCluster`

The real path is:

```text
Pattern Bus -> Worker -> ECOCraftingThread -> progress/power -> output flush
```

Neo ECO persists Thread work and restores it after chunk load. AAC extends this
path rather than replacing it with a second CPU.

Two structure predicates require a narrow Mixin:

1. the Worker row directly compares with Neo ECO's Worker block;
2. the parallel-core row accepts tier-compatible parts and would otherwise
   allow mixed L9/AAC performance rows.

All other structure rules remain Neo ECO-owned.

## InsaneAE Design Reference

InsaneAE's Quantum CPU demonstrates that a deterministic crafting-table batch
can:

- assemble the real recipe once;
- verify the result;
- multiply exact input/output bookkeeping by the accepted execution count;
- flush the aggregate result without one operation per craft.

AAC adopts that arithmetic idea only at a single recipe step. It does not adopt
whole-tree direct final-output conversion.

## Chosen Combined Design

- ACO compiles and schedules the recipe DAG.
- ACO owns exact boundary inventory and intermediate escrow.
- AAC performs one real assemble per recipe step.
- Neo ECO provides physical Worker progress, power, and NBT.
- A durable Worker receipt bridges physical completion back to ACO.

This preserves visible multistage progress while keeping runtime independent of
requested quantity.

## Exact Amount Boundary

Normal Neo ECO and AE2 aggregate stacks use signed `long`. AAC checks all
normal-job aggregate amounts before exact conversion.

BigInteger parent work stores only a one-craft representative Thread. Its exact
coefficient and output maps remain in ACO/AAC sidecars and are never narrowed
through `longValue()`.

## Resources

Verified Neo ECO model identifiers:

- `neoecoae:block/crafting_controller/controller_l9_off`
- `neoecoae:block/crafting_controller/controller_l9_formed`
- `neoecoae:block/crafting_controller/controller_l9_formed_mirrored`
- `neoecoae:block/crafting_core/parallel_core_l9`
- `neoecoae:block/crafting_core/parallel_core_l9_formed`
- `neoecoae:block/crafting_worker`
- `neoecoae:block/crafting_worker_formed`
- `neoecoae:block/crafting_worker_working`

AAC references these models and does not copy Neo ECO textures.

## Recipe Registry IDs

Neo ECO:

- `neoecoae:crafting_system_l9`
- `neoecoae:crafting_parallel_core_l9`
- `neoecoae:crafting_worker`
- `neoecoae:eco_cell_component_256m`

Optional AQE:

- `advanced_quantum_engineering:big_integer_quantum_core`
- `advanced_quantum_engineering:modified_quantum_storage`
- `advanced_quantum_engineering:modified_quantum_accelerator`
- `advanced_quantum_engineering:modified_quantum_multi_threader`

AQE recipes are conditionally loaded; AQE is not a Java runtime dependency.
