# Frontier v3 route-maintenance contract

Status: implementation contract for closing `V3-AUD-022`.

`RouteMaintenance` is a bounded exact engineering operation. It repairs one
observed, PM-owned route surface or foundation cell and is deliberately not a
`STRUCTURAL_REPAIR` intent, a topology rewrite, or a special form of
`RouteConstruction`.

## Authority

- `PhysicalDelta` is the durable evidence that the exact semantic cell was
  lost. It blocks the affected retained traversal edges immediately.
- One `RouteMaintenance` owns one such delta, its originating settlement, the
  exact expected semantic part/material, one retained engineering team, one
  optional exact cargo batch and one bounded work-site assembly.
- `RouteMaintenance` owns only repair of the retained route. It cannot adopt a
  player road, pick an alternate path, clear another delta, or change route
  waypoints. `RouteConstruction` remains the distinct owner of a strategic
  bypass and its eventual topology cutover.
- The maintenance operation itself, its cargo and all physical intents use
  stable IDs. At most one active maintenance operation exists for a repair
  cell; all active operation, team, cargo and scene claims are mutually
  exclusive with patrol, migration, cargo and construction claims.

## Canonical lifecycle

```text
observed route loss
  -> ADMITTED (exact cell + exact team)
  -> ASSEMBLING (one retained COLD cursor per engineer)
  -> SUPPLIED (one observed source decrement -> exact cargo)
  -> HOT_WORKSITE (only in naturally loaded player-demanded chunks)
  -> REPAIRING (durable-before-effect intent)
  -> CONFIRMED (exact block/ledger observation)
  -> CLOSED
```

`CONFLICT` and `UNKNOWN_AFTER_RESTART` are terminal visible states until a
separate, explicitly admitted future operation handles them. They may never
silently recreate a cargo unit, a worker body, a block or an edge.

The COLD/HOT work-site hand-off reuses the generic engineering-work protocol:
the exact team first reaches its retained support slots in COLD, then the
loaded scene materializes only the same IDs. The HOT scene may request the
current repair intent, but it cannot select people, source material, repair
cell or route edge. The physical executor is the only Minecraft block writer.

## Exact physical consequence

The source maintenance chest and damaged route are separate naturally loaded
interactions:

1. At the source, a durable material-loading intent removes exactly one item
   from its observed chest slot and transfers that item identity to the
   maintenance cargo batch.
2. At the target, the retained crew's HOT worksite creates a
   durable-before-effect repair intent. The executor requires player demand,
   the exact missing PM claim and an empty target block. It writes the expected
   block once, records provenance and confirms the exact postcondition.
3. Confirmation consumes the cargo identity, removes only that repaired
   `PhysicalDelta`, and recomputes availability only for retained edges. An
   edge becomes open only if no remaining semantic loss affects it.

Neither step force-loads a chunk. A loaded source chest does not authorize
remote repair, and a loaded target does not authorize source extraction. A
player/foreign block, changed chest slot, missing body or altered restart
postcondition is an observed conflict rather than permission to overwrite.

## Persistence and recovery

The operation, team assembly, cargo identity, physical intent and observation
are part of the canonical snapshot/WAL. They use new explicit stable wire tags;
the v3 fresh-world format is bumped atomically and accepts no preceding
snapshot/WAL format. A restart inspects only a naturally loaded target: an
already applied matching repair confirms once; missing or changed evidence
becomes `UNKNOWN_AFTER_RESTART`; the effect is never replayed.

## Required evidence

- Pure normal path: exact loss -> exact source decrement -> cargo -> receipt
  removes that delta and reopens only its no-longer-affected edge.
- Pure negatives: foreign material, wrong source slot, player/foreign target,
  duplicate owner, competing worker claim and another remaining loss keep the
  edge blocked and preserve the conflict.
- Snapshot/WAL and graceful restart: before pickup, with cargo, and after
  `RUNNING` intent; no duplicate item, person, block or availability change.
- Materialized GameTests: only the retained team/lease can prepare the repair;
  unsupported/foreign blocks fail closed.
- One disposable native scenario: normal player break -> exact maintenance
  pickup -> naturally loaded HOT repair -> same-edge recovery, with a bounded
  correlation trace and clean semantic frame. It must not use a force-load or
  a test command that mutates canonical state.
