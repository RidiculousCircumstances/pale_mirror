# Frontier v3 resource-site contract

This document specifies the first reusable renewable-resource vertical slice:
the twelve settlement wheat fields.  It is a binding design contract for the
implementation, not a compatibility layer for V2 or the Python model.

## Ownership and identity

`ResourceSiteState` is canonical Frontier state.  It owns the dynamic state of
each site; it does not own a second inventory.  A wheat field is a deterministic
site derived from its settlement's `FARM` structure:

- `site:<settlement-suffix>-wheat-field` is stable for the life of a fresh v3
  world;
- its owner is that settlement and its geometry is a fixed, non-overlapping
  8×8 crop plot adjacent to the farm's semantic structure footprint;
- each of the 64 crop positions is an individually addressable physical crop
  slot, not an aggregate yield multiplier;
- the field's fixed soil and crop slots are capital embodied in the named
  site.  They are not unnamed wheat, seed, or hidden stock.

The bootstrap contains no mutable crop count.  State, crop geometry and
projection are reconstructed from the stable bootstrap plus `ResourceSiteState`.
The state has a fixed maximum of one field per settlement in the initial
profile.  Future mine, forest and hive-harvest sites use the same owner/
lifecycle boundary rather than a settlement-specific parallel system.

## Canonical lifecycle

The initial field lifecycle is:

```text
UNPREPARED -> GROWING -> READY -> HARVESTING -> GROWING
                    \-> CONFLICT
                    \-> DESTROYED
```

- `UNPREPARED` means no owned physical field has been confirmed.  A naturally
  loaded field may start preparation, but no code loads its chunk.
- `GROWING` holds a deterministic growth epoch and stage.  COLD time advances
  it even while no player is nearby; HOT projection shows that exact canonical
  stage, rather than using random-tick growth as an alternate simulation.
- `READY` means all 64 canonical crop slots are mature and harvest may be
  planned if a named living FARMER, an intact farm and a free active depot slot
  exist.
- `HARVESTING` binds one persisted harvest job, its worker, its exact output
  stack identity and one physical intent.  A site owns at most one such job.
- `CONFLICT` is a visible failed materialization state.  A changed, occupied,
  missing or foreign crop/soil block is evidence, never permission to recreate
  a farm over the player/world.
- `DESTROYED` follows destruction of the owning farm structure.  Existing
  physical aftermath remains observable; the site creates no new crop or item.

An infection or other physical effect can reduce field condition through typed
site evidence.  It may lower growth or make harvest unavailable, but cannot
silently delete an exact output or repair a changed world block.

## Exact yield and physical causality

One completed wheat-field harvest creates exactly one identified 64-item
`minecraft:wheat` stack in one free slot of the owning active depot.  It does
not increment an aggregate stock counter.  The value originates from the 64
identified mature physical crop slots: one successful observed crop slot is one
wheat in the immutable harvest receipt.

The execution order is deliberately strict:

1. The pure farm process emits a durable job and `FARM_HARVEST` intent only
   after canonical field, worker, facility and storage preconditions hold.
2. In a naturally loaded chunk, the NeoForge executor verifies all 64 owned
   mature slots and the tagged output depot slot, persists `RUNNING`, performs
   the physical harvest, writes the tagged output stack, then submits the
   complete observed receipt.
3. The reducer atomically changes the site back to `GROWING` and publishes the
   exact item.  A partial, missing, altered or foreign result is conflict or
   `UNKNOWN_AFTER_RESTART`; it is never rounded into a successful 64-stack.

The first implementation may use the canonical 64-slot harvest transformation
instead of Vanilla crop-drop RNG.  This is a deterministic recipe whose input
is visible physical mature crops, not a fake Vanilla drop.  Any future use of
Vanilla drops must record every actual drop and cannot replace this receipt
with an estimate.

## Materialization and reverse causality

The pure `FrontierResourceSitePlan` exposes soil/crop cells and stage only; it
does not mutate Minecraft.  A dedicated loaded-chunk executor owns field
provenance separately from structural graybox and infection-overlay ledgers.
It may write only a fresh, supported natural-soil baseline during the first
prepared field effect, then only its still-owned cells while applying a newer
canonical growth stage.  It never overwrites an unowned block.

A player break, explosion, fluid, unsupported block, or incompatible crop
state first becomes typed site-loss/conflict evidence.  The engine reacts
immediately: crop loss changes the site and blocks a pending harvest; an intact
site may later recover only through a separately planned, exact-resource repair
operation.  Desired-state projection has no authority to replace the player
change.  Unloaded cells defer; no executor, observer or inspector force-loads
them.

`RUNNING` preparation or harvest work is recovered by inspecting the naturally
loaded field, owned depot slot and persisted postcondition.  It completes only
when the exact result is visible; otherwise it becomes an explicit unknown or
conflict.  It never replays a harvest or creates a replacement stack.

## AI and player readability

The settlement planner considers an intact field with no active work before it
declares a wheat shortage.  A FARMER is selected through `HumanPopulation` and
`ActorCondition`; dead, leased, migrated or otherwise committed people are not
eligible.  The field's board reports a player-facing condition such as
`SOWING`, `GROWING`, `READY TO HARVEST`, `HARVEST BLOCKED`, or `FIELD DAMAGED`,
the current risk and the actionable cause without canonical IDs.

The graybox field is readable from a distance: distinct prepared soil/crop
geometry beside the lime farm, stage-specific crop colour, and one local board.
This is intentionally separate from the future art pass; it uses normal
Minecraft farmland/wheat in addition to the existing coloured farm building.

## Required proofs

The implementation is not complete until it proves all of the following:

- deterministic multiseed geometry has twelve bounded, non-overlapping fields;
- COLD growth, state codec/WAL recovery and a due-action restart preserve the
  same stage/job/output identity;
- missing farmer, destroyed farm, full/conflicted depot, crop loss and a stale
  or duplicate command fail closed without an item;
- loaded-chunk materialization, player crop break, external blast and restart
  postcondition paths preserve foreign state and emit visible conflict/evidence;
- exactly 64 confirmed owned mature crop slots create exactly one tagged 64
  wheat stack, with no duplicate stack across crash/retry;
- a GameTest and a real-display pass show the prepared field, growth, harvest,
  storage update and player-caused failure without force-loading.
