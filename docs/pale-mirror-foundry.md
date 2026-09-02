# Pale Mirror Foundry

Foundry is the internal materialization SDK and audit laboratory for authored
Pale Mirror regions. It is deliberately read-only: it compares the immutable
manifest/catalog with already loaded Minecraft chunks, writes diagnostic
artifacts on explicit operator request, and never repairs blocks, loads a
chunk, or changes canonical state.

## Lifecycle

The same rule model is used at five checkpoints:

`PLAN -> COMPILED -> MATERIALIZED -> SETTLED -> RELOADED`

Production genesis runs the `COMPILED` gate before publishing a catalog and
before players are admitted. `ERROR` and `BLOCKER` findings fail that gate.
Heuristics that still require visual confirmation, such as a disconnected
small component or externally supported falling detail, are warnings.

Runtime phases inspect only chunks already resident in the server chunk
source. An unloaded authored chunk produces one bounded `unverified` warning;
Foundry never tickets it. `SETTLED` should be run after neighbour updates have
completed. `RELOADED` is the same physical audit after an ordinary
unload/reload or restart and exists to make recovery evidence explicit.

For a v3 traversal surface, the runtime result is deliberately three-way. A
fresh air cell that exactly belongs to the immutable plan but has no ledger
claim is `pending`: its naturally loaded projection has not yet received its
fair executor turn, so it is not physical damage. `current` requires all three
facts to agree: the plan cell, an unconflicted matching ledger claim, and the
observed block. Every other loaded case is a mismatch: a foreign or changed
block, a missing/wrong/conflicted claim, or a matching-looking unclaimed block
is never adopted as truth. A strict v3 `SETTLED` or `RELOADED` proof therefore
requires zero pending and zero unverified cells in its declared scope.

## Rules in the P0 gate

- every declared module compiles non-air cells inside its true footprint;
- imported NBT contains no forbidden controllers, creative power, explosives
  or untyped payloads, while counting curated one-shot block entities and
  flagging unsupported falling cells;
- public/freight ports connect to authored circulation; service and rail ports
  remain subject to their specialized infrastructure rules;
- canonical rail nodes are adjacent, compile exactly once, use real rail
  blocks, and rest on non-falling supports;
- compiled structural components expose plan-visible support evidence;
- loaded physical cells match their authored block type;
- attached blocks satisfy Minecraft survival predicates;
- entrance throats retain two blocks of headroom over an empty cell or a
  non-double slab/stair/carpet sill; full blocks, fences and other solid
  obstructions remain runtime errors;
- every public street component belongs to one compiled graph, shared junctions
  have one datum, facade throats terminate on that graph, and walkable grades
  change by no more than one block per horizontal cell;
- each gate compiles as one upper-datum frame across local one-block terrain
  steps, without a lowered jamb or disconnected lintel;
- loaded railway nodes remain rails and stay out of fluid;
- each loaded `SiteSurfaceColumn` retains supported walkable ground at its
  authored first-air datum.

World differences are findings, not repair authority. A player edit therefore
appears as physical drift and remains governed by the normal parcel/provenance
rules.

## Frontier v3 terrain-topology gate

`V3-AUD-019` extends Foundry's structural role to the provider-neutral v3
movement contract. The flat graybox is audited as a uniform-datum provider;
later terrain and rail providers must publish the same typed evidence rather
than letting a runtime navigator infer entrances or grades.

At `COMPILED`, Foundry must be able to locate each semantic facility port,
prove its exterior approach, threshold/throat clearance, interior connector
and public-topology connection, and validate every declared traversal edge's
grade, clearance, support and capability class. At `SETTLED` and `RELOADED`, it
observes only loaded cells and reports blocked/damaged/unknown ports or edges;
it never opens a door, clears a path, chooses an alternate entrance or changes
canonical availability. Non-flat, blocked-entrance and bridge/rail-grade
fixtures are mandatory exit evidence for the T0 foundation gate.

The read-only v3 diagnostic may narrow a runtime proof to one semantic facility
or one retained topology: `/pale_mirror v3 inspect traversal_foundry
settled@structure:1-infirmary` or `settled@topology:resident-ingress-1`.
A facility scope is that port's declared ingress and public topology; a topology
scope is precisely its retained nodes and edges. Neither is a nearest-route
lookup. They neither load nor project cells; they merely make a naturally
visited terminal proof practical while keeping foreign world changes visible as
errors.

One named hive organ is a separate exact scope: `/pale_mirror v3 inspect
hive_foundry reloaded@organ:west-heart`. Its immutable grammar is the same
`HiveOrganSupportPlan` used by bootstrap, projection and physical-loss
accounting: the organ cells must be `HIVE_TISSUE`, while every lower
terrain-provider-owned hiveroot cell retains that organ's exact `FOUNDATION`
provenance. `COMPILED` proves that grammar without a world; `SETTLED` and
`RELOADED` inspect only already-loaded cells. Air with no claim is reported as
`PENDING`; matching-looking unclaimed blocks and conflicting claims are drift,
never adopted provenance. This scope does not load, project, repair or change
the hive; a terminal runtime proof still requires zero pending and unverified
cells.

For every scoped runtime pass the diagnostic also reports the derived count of
`OPEN`, `BLOCKED` and `UNVERIFIED` semantic ports, plus the corresponding
declared topology-edge counts. A port is `OPEN` only when both of its declared
throat body cells are loaded and clear; one occupied cell is `BLOCKED`, while a
missing loaded-world observation is `UNVERIFIED`. An edge is `BLOCKED` only by
observed support drift, not by initial projection `PENDING` work or an unloaded
chunk. These are read-only Foundry conclusions: the operation/physical-observation
owners remain responsible for any canonical availability transition.

## Operator commands

All commands require permission level 4. A settlement object ID or region ID
may identify the region.

```text
/pale_mirror debug foundry audit <region> [plan|compiled|materialized|settled|reloaded]
/pale_mirror debug foundry export <region> [plan|compiled|materialized|settled|reloaded]
/pale_mirror debug foundry batch [plan|compiled|materialized|settled|reloaded]
/pale_mirror debug foundry inspect <region>
/pale_mirror debug foundry markers <region>
```

`inspect` traces the operator's view up to 64 blocks and reports semantic
owner, expected state, actual state and target ground datum without loading a
chunk. `markers` emits one-shot particles for at most 64 severe findings.
`visual-audit list` automatically includes up to 24 `foundry/...` cameras
focused on severe findings, so the existing client screenshot harness captures
defects rather than only curated beauty shots.

## Exported evidence

An explicit export creates
`<world>/pale-mirror/foundry/<region>-<phase>-tick-<gameTime>/` containing:

- `report.json`: stable metrics, findings, coordinates and map samples;
- `report.html`: a self-contained review index;
- `target-height.png`: authored first-air datums;
- `observed-height.png`: supported walkable ground in loaded chunks;
- `cut-fill-delta.png`: observed minus target ground;
- `expected-top.png`: highest authored cell per managed column;
- `ownership.png`: deterministic semantic-owner colors.

Purple observed/delta cells were not physically inspectable. Reports are
diagnostic files, are not persisted into canonical SavedData, and may be
deleted at any time.

## P1 laboratory

The compiled audit passes every template through the production curator and
reports per-asset footprint, typed block-entity and falling-support defects.
Curated block entities are a metric rather than a defect; missing types,
forbidden external controllers and invalid source states fail closed. Raw NBT
is parsed once into a bounded 128-template immutable cache; climate and
rotation transforms still compile independently. The existing 468-state asset
showcase and 216 public-realm materialization matrix remain the batch gold
masters. `foundry batch` applies the same rules to all live terrain variants,
while elapsed time, compiled cell counts, checked cells and loaded/unloaded
chunk counts expose the cost of each pass.

Foundry is an internal framework, not a public adapter SDK. New rules should
produce a stable rule ID, bounded locatable findings, a numeric metric, and a
negative or recovery GameTest.
