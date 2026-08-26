# Frontier AI contract

## Semantic authority and acceptance

The active `/home/rd/proj/pale_mirror_ai/simulation/` checkout is the semantic
reference for the source port. Its active `source_v2` profile is the only
Python boundary that must be transferred: a 64x44 grid, twelve settlements and
two infection seeds. The reachability fixture records the deliberately
unreachable legacy modules; if Python makes one of those modules reachable,
that change creates a new required port wave rather than a Java compatibility
fallback.

The Java implementation is the pure-domain
`io.farfrontier.palemirror.frontier.reference` model. `ReferenceGrayboxSimulation`
owns the complete mutable source-shaped world and exposes only immutable
`ReferenceGrayboxSnapshot` views plus versioned observations. It neither
imports Minecraft/NeoForge nor derives canonical state from blocks, entities,
or wall-clock time.

Parity has two intentional levels:

- Source-generated micro-traces, source-state documents, public snapshots and
  short multiseed canonical checkpoints retain exact branch, identity, custody,
  order and RNG evidence. Finite binary64 leaves use the documented 4096-ULP
  canary policy; a named trace may state a narrower tolerance.
- Longer runs are accepted by the versioned four-seed annual calibration
  envelope. Small numeric drift and a downstream legal choice are acceptable
  only inside the declared behavioural bands. Missing people, invalid custody,
  topology violations, lost events, non-deterministic Java runs or a material
  behavioural change are failures.

This deliberately does not require a Python release before every Java edit.
Until the transfer gates are accepted, an active source change and its Java
port land together with the affected trace or calibration evidence. The
complete fixture inventory, generator commands and reachability policy live in
[`frontier-source-port.md`](frontier-source-port.md).

## Graybox_1_40 profile

`graybox_1_40` is the playable scale profile of that domain, not an adapter
which aggregates actors. Its 64x44 logical cells map to a centred 1024x704
arena at sixteen blocks per cell inside a 1024x1024 flat world at first-air
Y=64. It creates twelve settlements and two infection seeds.

- Every canonical resident is exactly one person and, when their chunk is
  loaded, one managed Villager.
- Every canonical bioform is one managed Zombie. Type changes use colour and
  a readable name; they do not introduce an invisible population multiplier.
- Human population, stock, credit and capacity are explicitly scaled 1:40.
  Territorial ecology and organ biomass retain source spatial scale; combat
  effects are calibrated for the smaller human world.
- The north/south space is a neutral border, not an extra simulation grid or a
  place for hidden simulation state.  Its one fixed 7x7 yellow, lit operator
  observation deck is outside the logical arena and is a fail-closed physical
  entry boundary only; it carries no source claim or canonical state.

`ReferenceGrayboxLayout` owns these coordinates. A materializer must never
choose a second scale, merge co-located source objects, relocate their logical
X/Z identity, or remove canonical people to meet a presentation budget.

## Runtime authority and persistence

`SourceGrayboxSavedData` persists the complete
`ReferenceGrayboxSimulation.save()` document, activation state, simulation
clock and a bounded accepted-observation history. It restores all-or-nothing;
an incompatible or incomplete document is a visible startup failure for the
disposable world, never a regenerated campaign.

`SourceGrayboxRuntime` is the sole campaign clock after the explicit
`/pale_mirror frontier activate_graybox` command. Its schedule advances the
source day from Minecraft game time, caps catch-up work, and republishes after
each source change. `PaleMirrorRuntime.tick()` returns before the historical
Frontier clock once this source runtime is active, so two autonomous campaign
owners cannot run together.

The profile lives only in the disposable data-driven
`pale_mirror:frontier_graybox` level. The Far Frontier deployment owns the
world datapack because Minecraft level stems must exist before world creation.
Activation rejects a missing level, never flattens or claims the ordinary
overworld, never force-loads chunks, and applies the 1024-block border to the
dedicated level. `/pale_mirror frontier enter_graybox` is the explicit
operator transport for manual testing.

## Projection and two-way facts

The forward path is:

```text
ReferenceGrayboxSimulation
  -> immutable ReferenceGrayboxSnapshot
  -> SourceGrayboxPresentationPlan
  -> SourceGrayboxMaterializer (loaded chunks only)
  -> coloured cubes, labels, Villagers and Zombies
```

`SourceGrayboxPresentationLedger` is only the durable physical-ownership
ledger. It cannot create, remove or infer canonical source facts. Each managed
entity has deterministic UUID, source ID, type and snapshot revision. Each
claimed block has an exact source subject, fact kind, revision and—where
interactive—its exact currently represented weight. Unrecognised blocks,
modified claims and stale facts are visible persisted conflicts; they are not
permission to overwrite player work.

The reverse path is synchronous in the accepting server event:

```text
managed Villager/Zombie death or declared interaction-slot break
  -> versioned ReferenceGraybox*Observation
  -> revision + identity + bounded deduplication validation
  -> ReferenceGrayboxObservationExecutor
  -> canonical source state and immediate republish
```

Resident death, bioform death and the eight non-entity facts are closed over
their exact source subject: facility, resource-site, route and organ damage;
operation cargo loss; field-post cargo loss and structural damage; and
field-link damage. Cargo is represented by a named controlled pallet, so its
intentional graybox interaction is a typed cargo-loss fact rather than an
unbounded interpretation of arbitrary player inventory changes. A successful
slot break is consumed and its remaining sibling slots are rebalanced from the
new canonical quantity. A rejected, stale or foreign change remains a conflict
and changes no source state.

The immutable plan validates all source claims before writing. Legitimately
co-located facts keep their canonical X/Z and receive distinct deterministic
layers in the compact Y=64–79 stack; labels begin above that stack. The
materializer may relocate only an unchanged PM-owned claim when a later plan
changes its layer; it must not silently cover a player block.

## Readability and verification boundary

The graybox palette is semantic: hostile pressure/disruption is red, hive
signal/quarantine purple, logistics blue, human control cyan, food lime,
medical/housing white, storage brown, observation/tools yellow, extraction or
contested state orange, brood pink, destruction/feral black and neutral
descriptive state grey. Labels expose exact identities and values, so colour
does not replace a domain fact. Functional settlement rectangles,
resource-sites, organs, cargo, field posts/links, operations, territorial
sectors, chrysalises, current events and actor roles are all separate readable
claims or labels. Trade routes additionally have sampled colour-coded ground
segments between their source endpoints; their elevated interaction slots remain
the sole route-damage authority.

Automated acceptance requires the source fixture checks, pure-domain
normal/negative/recovery tests, complete-state persistence tests, the annual
calibration envelope, source-materializer GameTests and packaged-JAR gates.
Those tests prove code and the server-side contract; they do not prove that a
human can read the world in Minecraft. Final acceptance therefore also needs
a client run in the dedicated graybox: enter the level, capture screenshots,
advance time, and confirm immediate canonical consequences of a managed
Villager/Zombie death and an interaction-slot break. A stopped client leaves
that last visual/manual gate pending rather than silently waived.
