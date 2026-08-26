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
`ReferenceGrayboxSimulation.save()` document, the pure-domain actor-execution
ledger, a bounded physical-effect ledger, activation state, simulation clock
and a bounded accepted-observation history. The execution ledger has one
record for every exact resident/bioform: its latest source anchor and revision,
fixed sixteenth-block physical position, one `COLD -> PREPARING -> HOT ->
DRAINING -> COLD` lease lifecycle, and monotonic local combat epoch/cooldown.
A
restart moves unfinished leases to `RECOVERING`; only an inspected entity with
the exact old lease can return to `HOT`, while a missing/stale body settles
`COLD` without a blind duplicate. Both documents restore all-or-nothing; an
incompatible or incomplete document is a visible startup failure for the
disposable world, never a regenerated campaign.

The initial HOT coordinator runs every ten ticks. Its demand zone is the union
of non-spectator player chunks plus one chunk; a second one-chunk apron may
prepare only already resident chunks. It never makes a chunk ticket. A live
body keeps its physical position between publications; it is captured in
sixteenths of a block, then may drain only after 200 ticks outside demand and
when no player is within 64 blocks. The executor uses a persisted monotonic
lease-time floor, so `/time` rewind cannot reuse an old lease or turn a body
into an immediate despawn. A separate bounded role brain refreshes only those
HOT lease bodies every ten ticks from source role/deployment or swarm phase,
then makes collision-checked movement steps (at most sixty-four bodies per
Minecraft tick). Native goals stay disabled, so this is not a second strategic
AI; it cannot spawn, damage, mutate source state or run inside an unloaded
chunk. Its sibling combat executor considers at most sixty-four HOT bodies
every five ticks. It must reserve the exact lease-owned action epoch and
cooldown, persist a target-bound physical effect before calling Minecraft, and
store fixed-point target health before/after the hit. A running action becomes
`UNKNOWN_AFTER_RESTART` on recovery and is never replayed; a managed death
still re-enters the source only through its typed observation.

`SourceGrayboxRuntime` is the sole campaign clock after the explicit
`/pale_mirror frontier activate_graybox` command. Its schedule advances the
source day from Minecraft game time, caps catch-up work, and republishes after
each source change. `PaleMirrorRuntime.tick()` returns before the historical
Frontier clock once this source runtime is active, so two autonomous campaign
owners cannot run together.

An operation or field-post cargo barrel is a persisted physical custody
handoff, not a second stock counter. A stable cargo ID is normally `ACTIVE` at
one exact tagged barrel. When a source operation changes cell, schema v23
records the old custody position and its new source target as `RELOCATING`.
It observes the old barrel first, removes only the tracked PM resource, and
then creates the target barrel only when both endpoints are naturally loaded.
A mixed former barrel is released to the player with its remaining items
unchanged. An unavailable former chunk produces a visible pending-custody
board at the target and never causes a ticket, a force-load, or a duplicate
container; missing or foreign custody remains a retained `BLOCKED` conflict.

The future living-world runtime uses a durable layered clock. In the normal
`GAMEPLAY` profile, one complete Python-shaped source day is 24,000 Minecraft
game ticks, or twenty real minutes at 20 TPS. The daily source order remains
one indivisible deterministic transaction: it is not split into pseudo-hourly
economy, population or strategic substeps merely to make actors look busy.
Loaded actors instead move at Minecraft tick rate, consider bounded melee every
five ticks, reconsider their role-level goals every ten ticks, reconcile tactical operation state every
twenty ticks, and perform bounded local work or infection progression every
one hundred ticks. A confirmed physical observation is reconciled immediately,
without waiting for the next source day.

`FAST_GRAYBOX` keeps the current 1,200-tick source day and explicit
complete-day fast-forward for source-parity, calibration and manual testing.
The selected clock profile and last source-day boundary are persisted with the
world. Changing a live profile later requires an explicit versioned setting
and fresh calibration evidence; it may never silently rescale an existing
campaign.

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
  -> coloured structures, growing/retreating infection tissue, readable boards,
     resource-specific PM-owned barrel shelves, Villagers/Zombies and current
     source-day effect markers
```

`SourceGrayboxPresentationLedger` is only the durable physical-ownership
ledger. It cannot create, remove or infer canonical source facts. Each managed
entity has deterministic UUID, source ID, type and snapshot revision. Each
claimed block has an exact source subject, fact kind, revision and—where
interactive—its exact currently represented weight. A claim also records
whether PM actually installed its blocks: a foreign block that prevents a new
claim is a durable `foreign-obstruction`, never a retroactive ownership grant.
Unrecognised blocks, modified claims and stale facts are visible persisted
conflicts; they are not permission to overwrite player work.

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

Each settlement also has one snapshot `Warehouse` whose nine typed stockpiles
are exposed through fixed resource-specific barrel shelves. One matching
Minecraft item is exactly `1/64` of a source unit: a 64-item stack is one
canonical unit. The physical ledger records each PM-installed barrel identity,
resource and last acknowledged item count. A player deposit or withdrawal is
one signed, versioned `ReferenceGrayboxWarehouseObservation`; the domain alone
accepts it into public/company custody or rejects it for stale revision or
insufficient source stock. An untagged barrel, wrong tag, broken barrel,
foreign item or shelf capacity failure never becomes an implicit economic
owner: it remains untouched physical state plus a visible retained conflict.
The adapter does not force-load a shelf and does not invent a Minecraft-side
economy counter.

The immutable plan validates all source claims before writing. Legitimately
co-located facts keep their canonical X/Z and receive distinct deterministic
layers in the compact Y=64–79 stack; labels begin above that stack. The
materializer may relocate only an unchanged PM-owned claim when a later plan
changes its layer; it must not silently cover a player block.

Every non-zero source infection cell additionally owns zero to four independent
three-by-three tissue clumps. They form a growing or retreating six-by-six
surface contour within its sixteen-by-sixteen cell: brown trace, red active
tissue, purple dense tissue, and pink/magenta signal-linked tissue. The source
cell's exact infection and signal values remain canonical; the clumps do not
invent a new infection model. A player obstruction or break retains a conflict
and a visible physical scar rather than being rebuilt on the next publication.
Tissue is descriptive in this wave: it deliberately does not pretend that a
bare block break is a valid source suppression operation. Typed removal will
arrive with the durable operation/effect boundary, where its real geometry and
canonical suppression receipt can be recorded together.

The immutable snapshot also exposes only the current source day's already
committed `CombatReceipt` and `ContainmentReceipt` as typed effects. This is a
one-way rendering boundary, not a second tactical planner: a structural breach
becomes a `breach_bomb`, ordinary pressure becomes an assault cue, and local
human containment becomes a containment flare. The descriptor has a stable
source-day ID, target and calibrated magnitude; its live board says exactly
what source event caused it.

## Autonomous physical causality

The simulation is not a safety shell around the world. A settlement operation
selects an actor's intent and target, but never grants a protection boundary to
blocks or entities outside that target. When a PM-controlled bomber, projectile
or other physical effect is present in loaded space, it follows ordinary
Minecraft flight, collision and effect geometry. Every physically reachable
consequence is eligible: a missed bomb can hit a wall, a player-built
barricade, an unrelated settlement component or a chain reaction. Pale Mirror
defines no privileged safe zone and does not use ownership, parcel type or the
operation boundary to filter a legitimate blast.

Before such a non-replayable effect is executed, its durable identity and
cause are persisted. Its terminal receipt contains the actual affected
entities, changed blocks, terrain/infection residue and structural aftermath.
Reconciliation converts known source subjects into their canonical damage,
casualty, route, terrain or infection consequences. Damage to otherwise
unmodelled space is retained as a bounded canonical physical-scar or obstruction
fact, so it can affect later movement, terrain and infection logic and is never
silently repaired from a materialization baseline. An interrupted effect is
visible and is not replayed automatically after restart.

`SourceGrayboxEffectRuntime` settles each descriptor exactly once at the source
day boundary. It may use Minecraft physics only if the target chunk is already
loaded and inside the non-spectator HOT zone; otherwise it writes a durable
`cold` disposition and never executes a delayed blast when somebody revisits
the location. A hot `breach_bomb` invokes normal `TNT` explosion interaction,
without inspecting parcel ownership, source claim type or player identity.
Immediately afterwards each actually changed claimed block becomes its typed
observation or a persistent conflict; every changed unclaimed block is written
to the bounded `SourceGrayboxPhysicalScarLedger`. The effect lease carries the
causation and bounded impact summary, while the per-subject observations and
scar rows retain the locatable aftermath. The whole state is persisted with
the source world; its v21 loader fails closed when any part is absent or
invalid.

## Readability and verification boundary

The graybox palette is semantic: hostile pressure/disruption is red, hive
signal/quarantine purple, logistics blue, human control cyan, food lime,
medical/housing white, storage brown, observation/tools yellow, extraction or
contested state orange, brood pink, destruction/feral black and neutral
descriptive state grey. Colour never replaces a source fact. Functional
settlement rectangles, resource-sites, organs, cargo, field posts/links,
operations, territorial sectors, chrysalises, current events and actor roles
are all separate readable claims or labels. Trade routes additionally have
sampled colour-coded ground segments between their source endpoints; their
elevated interaction slots remain the sole route-damage authority.

Warehouse shelves are deliberately low 2x2x4 barrel stacks inside the existing
warehouse rectangle, rather than remote technical columns. Their gold board
states the settlement, the exact `64 items = 1 source unit` exchange rule and
the action; right-clicking that board gives the complete typed source stock.
Only the matching resource item is accepted by a shelf. This keeps real player
supply and theft legible without turning every source unit into a separate
entity or hiding an aggregate multiplier.

An active operation or field post receives one ordinary tagged barrel at the
centre of each canonical cargo pallet. Its exact cargo ID, owner and resource
come from the immutable snapshot; one real item is one sixty-fourth of a
source unit. Resource pallets use fixed type slots, so consuming food never
teleports a surviving medicine container. Before a source day advances, a player deposit or withdrawal of
the matching item becomes one revision-validated cargo receipt, so it changes
only that named operation or field-post stock. The barrel never adopts a
foreign block or item type. A missing barrel attempts the exact source loss
once and then becomes a visible blocked conflict. When the canonical cargo
retires, its empty PM barrel may retire too; a mixed barrel retains every
foreign player item and becomes a conflict, rather than deleting the player
state. This is field custody, not pretend transport: a trade already committed
by the source resolver is not rendered as a reversible cart after the fact.

Cell tissue is intentionally more legible than a metric tower alone: its
contiguous extent shows local infection growth and retreat, while the existing
red/purple/cyan/lime sector towers retain the exact territorial comparison.

The player-facing layer is read-only and derived from the same snapshot. A
landmark board gives a short named state, while right-clicking its labelled
structure, a managed Villager or a hive Zombie gives a bounded briefing:
**state, cause, risk and next action**. The observation deck has a frontier
report (forecast and current priority) plus a three-event timeline. Breaking a
declared action block or killing a managed actor gives its player an immediate
receipt of the accepted canonical consequence; breaking a merely descriptive
block says that the world was unchanged. These messages are transient
presentation, not a second event log or an authority path. Exact raw values and
IDs remain available to the permissioned `frontier inspect` QA command, but no
ordinary player briefing depends on it.

Automated acceptance requires the source fixture checks, pure-domain
normal/negative/recovery tests, complete-state persistence tests, the annual
calibration envelope, source-materializer GameTests and packaged-JAR gates.
Those tests prove code and the server-side contract; they do not prove that a
human can read the world in Minecraft. Final acceptance therefore also needs
a client run in the dedicated graybox: enter the level, capture screenshots,
advance time, right-click a settlement, route, hive organ and the labelled
structure beside REPORT/TIMELINE, then confirm the immediate receipt and canonical consequence of a
managed Villager/Zombie death and an interaction-slot break. The same pass
must show one tissue contour growing or retreating and one player-owned
obstruction remaining visibly unpainted, plus one operation/field cargo
deposit or withdrawal with its immediate canonical receipt. A stopped client
leaves that last visual/manual gate pending rather than silently waived.
