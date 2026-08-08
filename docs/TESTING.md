# Testing

## Automated

Build ACO first, then run:

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
.\gradlew.bat clean build --no-daemon
```

Automated tests cover:

- one-craft formula multiplication at signed-long and 1,024-digit quantities;
- exact input/output totals;
- normal long stack scaling limits;
- physical capacity arithmetic;
- durable terminal receipt identity;
- idempotent receipt replay and explicit forget;
- refusal to overwrite one transaction with another payload or output.
- nine identical input slots at `Long.MAX_VALUE` each without merged-input
  overflow rejection;
- a coefficient-nine input slot being limited to `Long.MAX_VALUE / 9`.
- the exact Neo ECO `20.3.0` JAR's thread, worker, Pattern Bus, and cluster
  method/field descriptors;
- the public ACO API boundary, including the public transaction view and
  receipt/target interfaces.

The bytecode test reads `neoecoae-20.3.0.jar` from the `aacLocalModsDir`
passed to Gradle. `verifyAcoPublicApiBoundary` fails if AAC imports an ACO
implementation package. These checks do not start Minecraft.

## Live Registration

```mcfunction
/give @s advanced_assembly_computing:vector_crafting_controller
/give @s advanced_assembly_computing:vector_crafting_parallel_core
/give @s advanced_assembly_computing:vector_crafting_worker
```

Check placement, pick block, loot, wrench behavior, glint, creative tab, EMI,
and AQE-conditional recipes.

## Structure

1. Form the Neo ECO crafting-system shape with AAC performance parts.
2. Confirm all-AAC rows form.
3. Replace one Worker with Neo ECO L9 and confirm the AAC structure fails.
4. Restore it and replace one parallel core with L9; confirm failure.
5. Confirm an ordinary all-L9 Neo ECO structure still forms.
6. Test every horizontal orientation, mirrored layout, structure break/reform,
   chunk unload, and reload.

## One Recipe

1. Encode a deterministic `9 input -> 1 output` crafting Pattern.
2. Request `1`, `1,000`, and a large signed-long amount.
3. Confirm one physical Thread owns each accepted batch.
4. Confirm the Thread performs real progress and power.
5. Confirm exact inputs, output, and remaining items.
6. Confirm quantity does not create quantity-sized Thread or NBT lists.

## Physical Tree

Use ACO's twenty-stage deterministic test chain.

1. Test `1`, `Long.MAX_VALUE`, and a supported BigInteger root.
2. Confirm twenty physical recipe completions.
3. Confirm each parent waits for child receipt output.
4. Add independent branches and confirm multiple Workers may run them in
   parallel.
5. Confirm the final result comes from escrow rather than direct tree output.

## Receipt Recovery

Stop or unload at:

- Worker accepted;
- Thread halfway through progress;
- `OUTPUT_READY`;
- terminal receipt recorded before Thread release;
- Thread released before ACO credit;
- ACO credit before `forget`.

After recovery, one and only one exact output receipt must remain observable.
No representative stack may enter ME storage or world drops.

Also verify that the first post-restart lookup rebuilds the UUID ownership
index and subsequent polling does not scan the full Worker/Thread lists.

For the revision path, verify with the AAC diagnostics that unchanged running
polls reuse the same snapshot, `OUTPUT_READY` accounting-only Threads sleep,
and acknowledge/cancel cause a targeted Neo ECO wakeup. A restart may perform
one bounded index rebuild; repeated misses for the same transaction must not
rescan every Thread.

## Cancellation

1. Cancel a running BigInteger physical step.
2. Confirm AAC releases only the representative Thread.
3. Confirm ACO returns the real reserved input escrow.
4. Cancel after output readiness and confirm output is not converted back into
   original input.

## Normal AE2 Job

1. Enable Transactional Batch V2.
2. Run a normal signed-long AE2 job through AAC.
3. Confirm AE2 task and waiting output accounting remain exact.
4. Restart with a pending source/target receipt and confirm reconciliation.
5. Fill the target and confirm rejected work remains on the original path
   without input loss.

## Optional AQE

Test once with AQE installed and once without it.

- With AQE: the three progression recipes load.
- Without AQE: AAC starts, blocks remain registrable, and those recipes are
  absent without unknown-item recipe errors.

## Not Proven by Gradle

Gradle cannot prove:

- Mixin runtime application;
- live multiblock formation;
- actual Neo ECO power draw;
- real chunk save ordering;
- client rendering;
- dedicated-server or Arclight startup;
- production TPS.

These require live testing with identical server/client JARs.
