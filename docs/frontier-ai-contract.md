# Frontier AI contract

`frontier` is the replacement autonomous-world domain. It is pure Java and
does not import Minecraft, NeoForge, persistence or wall-clock APIs.

## Profiles

- `source_v2` is the pinned Python conformance profile: 64×44 cells, twelve
  settlements and two infection seeds. Its complete day-state trace is the
  only Java source-parity oracle until the transfer is formally complete.
- `graybox_1_40` is the playable profile: the same 64×44 cells map to a
  centred 1024×704 arena within a 1024×1024 flat world, at 16 blocks per cell,
  with twelve settlements and two infection seeds. It begins with roughly
  16–38 real people per settlement. Every resident is one canonical person
  and every bioform is one canonical zombie; neither is a cohort.

The profile owns scale. Human population, stock, credit and capacity use the
explicit 1:40 profile conversion; territorial ecology and organ biomass remain
at source spatial scale, while combat effects are calibrated for the smaller
human world. Materialization must not invent a smaller population or hide
residents to meet a performance target. `tools/frontier/verify_reference_balance.py`
checks pinned inputs against the separately owned Python checkout. The empty
top and bottom bands of the 1024×1024 world are neutral readable borders, not
extra simulation cells.

## Authority and reconciliation

The forward path is `FrontierWorldState -> FrontierSnapshot ->
FrontierProjection -> VisualProvider`. The reverse path is a typed
`FrontierPhysicalObservation` validated by identity, revision, provenance and
a bounded persisted observation id before it becomes a `FrontierCommand`.

The observation contract covers resident death, facility damage, cargo loss,
hive-organ destruction and bioform death. They never bypass it. Once a
managed physical fact is accepted by the provider it is drained in that same
server event into `FrontierSavedData`; it is not left in an in-memory queue
until a later tick. Rejected observations emit bounded history and leave their
target unchanged. Restart retains both target revision and accepted observation
ids, so a physical event cannot replay. An accepted or rejected reconciliation
also republishes the immutable projection before that server event returns, so
the graybox reports the new consequence without waiting for its periodic pass.

## Graybox materialization

The projection is explicitly activated per disposable graybox world by an
operator. Activation establishes the physical world border at centre `(0, 0)`
with a 1024-block size; server setup must additionally use a flat generator
with `generate-structures=false`, so no foreign structure can be mistaken for
the simulation. It never activates merely because the mod starts in an existing
campaign. Its server config must explicitly set `genesis.authoredEnabled=false`:
this is a separate admission mode, not a waiver after authored planning fails.
It skips legacy terrain authoring and admits players with a visible
`frontier-graybox` readiness record. Normal campaigns retain the default
`true` and remain fail-closed until their authored catalog is ready. The visual
provider accepts only `graybox-10`, requires the local flat Y=64 surface and
never force-loads a chunk. Each loaded settlement gets
all ten functional cubes, role-colored normal Villagers, a label containing
population, civic regime, measured threat, food-reserve days, ration,
food/weapons/ammo/power/credit and functional-status labels. A deterministic
settlement policy runs after actual raids, field responses and hive actions: its
`NORMAL/WATCH/EMERGENCY/SIEGE/RECOVERY/DECLINED` ledger is canonical, persists
through restart and sets the civilian food issue (92% in emergency, 82% in
siege). Farming,
mining, forestry and power form a visible production chain; a Workshop consumes
ore, wood and power to make weapons and ammunition, while a Clinic consumes food and power
to make medicine. After production, one immutable settlement snapshot compares every resource's local scarcity, reserves stock, mutual-credit room and exactly one physical load per route, then commits chosen loads together. A cargo label shows its resource, physical amount and exact `credits` value; a player-destroyed crate reverses only that value through the same validated observation. Every hive gets five
colored organ cubes; every live bioform is a type-colored Zombie. Cargo is a
named physical crate, so its transit and loss are observable.

One live Harvester is a bounded, individual collection operation rather than a
hidden income tick. Its green Zombie uses the same managed identity through
`OUTBOUND -> FORAGING -> RETURNING`, and the co-located `[R] HARVEST` label
shows its phase, carried organic mass, milli-genetic samples and receiving organ. It
may only credit 68% of that payload after reaching a still connected Core or
Digestive Pool. Killing the Zombie, or destroying its source, Core or selected
receiver, aborts the run in that same accepted physical event and leaves its
unassimilated cargo in the local ecology. Java deliberately keeps the same
carrier Zombie visible through completion; that is a graybox materialization
calibration over the Python shell's removal of a returned swarm, not a claim
that their presentation lifecycle is identical.

One live `PropagationRun`, materialized as a Spore Carrier, is a different bounded transaction. A live, signalled
Sporulator may spend 30 biomass on at most one carrier per hive; its magenta
Zombie and `[S] SPORES` label expose the actual outbound progress and chosen
target. The target is canonical: it must remain inside the profile, away from
all existing organs, and scores its local ecology and nearby living settlement
pressure. Arrival consumes that same Zombie and deposits one blue-wool `[L]
LATENT` cube. The player-facing label publishes spores, tissue strength and `clearable`; the canonical field is a neutral propagule count, so the pure domain does not depend on the optional Spore integration.
colony remains visible through its delivery day, then decays by 3.5% per day.
Only a still-present colony with propagules ≥550 and tissue ≥340, at least 20
simulation cells from every organ, can become an exact new Core. Breaking its
current blue cube queues `LATENT_COLONY_CLEARED`; the canonical observation
removes it, so no delayed Core may appear.

Genetic material is stored in milli-units (`1000 = 1.000` reference sample),
therefore graybox labels say `genetic-milli` rather than imply whole samples.
Physical combat, Synapse loss, colony cleansing and starvation feed a bounded
decaying pressure memory. Every tenth day, only if all hives together own the
finite 12,000±10% milli-unit cost, it deterministically pays the richest hives
and records one visible level (maximum two). Rapid Digestion changes harvesting
and assimilation; Synaptic Redundancy changes command signal. The remaining
reference adaptation mappings are retained and visible, but their dependent
territorial/illness/quarantine mechanisms are not yet in the Java conformance
slice and are deliberately not presented as active effects.

The hive's biomass now comes from an authoritative finite ecology, not from a
settlement inventory or a hidden daily grant. Every simulation cell persists
flora, fauna, detritus, nutrients, moisture and scar in integer units. The
daily order regenerates those pools before hive metabolism; a living Digestive
Pool consumes its own displayed cell and turns only that organic matter into
bounded biomass. Its graybox label therefore shows `organic=<mass>` and
`scar=<0..1000>` beside the green organ cube. This deliberately uses a label,
rather than repainting player terrain: it is readable while preserving the
loaded-chunk, no-overwrite ownership contract. A partial ecology save is
rejected, not replaced with a clean map.

Hive command authority is likewise derived, not cached. Every hive owns a
finite, persisted grid of tissue cells with a strength threshold. A live Core
can reach a live Brood Sac only through one connected above-threshold component;
the connected Core/Synapse organs derive its `brood-signal=<0..1000>`. A raid
or brood action requires that signal, while a decapitated or severed network
loses it visibly. The hive label publishes the exact brood signal, so an
operator can distinguish a live organ cube from a working command path. A
partial or duplicate tissue save is rejected rather than silently reconnecting
the genesis network.

A hive raid is a separate materialized field operation: an `[A]` label records
its target, phase and real bioform count, while its participant Zombies move
with its canonical cell position through `ASSEMBLING -> EN_ROUTE -> ENGAGING
-> RETURNING`. The underlying operation, rather than an entity's absence,
determines outcomes. Killing every currently identified participant aborts the
operation in the accepting physical transaction; unattended operations resolve in the simulation and project their
resident/facility consequences back to the same world.

A settlement response is a separate `[D] DEFEND` operation. Its label names the
raid, phase and `living/assigned` normal Villagers; those same role-coloured
Villagers move from their settlement positions to the defence position and back.
The response consumes visible food, medicine, weapons and ammunition before it
can launch. Its integer costs are the conservative 1:40 ceiling of the pinned
Python requirements (24 food, 0.6 medicine, 7 weapons, 32 ammunition); its
movement uses the source speeds of 1.55 human cells/day and 0.65 hive cells/day.
Killing every assigned Villager aborts it in the accepting server event, while
destroying a raid's Core immediately starts its return.

A strategic `[C] HIVE_CLEARANCE` campaign is a separate bounded coalition, not
a renamed `DEFEND` response. The first coalition decision waits until day 36
so a graybox player can see a hive approach; all later decisions retain the
Python `v2.frontier.planning_interval=4` cadence. Each viable live Core may
receive at most one campaign with two or three contributing settlements,
visible 1:40-ceiling `raid_nest` food/medicine/weapons/ammunition reservations
and two to twelve real Guard identities. Its persisted phase vocabulary exactly
matches Python `FrontPhase`: `RECON -> ASSEMBLE -> ESTABLISH -> CORDON -> CLEAR
-> HOLD -> RESTORE -> WITHDRAW -> COMPLETE|FAILED`; the pinned hold and restore
durations are 18 and 8 days and the supplied-front threshold is 48%. The front
runs its sector-level supply route through the finite ecology/tissue map, and
shows the actual phase, living people, coalition size, supply readiness and
risk in one `[C]` label from its launch event onward. During deployment the
same role-coloured Villagers move to the front; a guard cannot join both a
local response and a coalition. Killing enough current participants to leave
fewer than two fails the campaign in that accepting physical event. A
successful clear uses the same Core-destruction consequence path as a player
break, so raids, harvesters, propagation and other campaigns receive one
canonical withdrawal/cleanup decision instead of a visual special case.

Initial structure placement may write only air. A bounded persisted graybox
claim ledger then permits subsequent state colours only across the known
graybox palette. Any other block is a conflict and is left untouched. Managed
entities have deterministic UUIDs plus canonical identity/revision NBT; they
are safely retired only when the projection no longer owns them. Cube writes
preflight the complete loaded, local-flat footprint and roll back if a later
block write fails; a partial structure never receives a claim.

## Current implementation boundary

The current slice provides deterministic genesis, differentiated settlement
economies, functional operations, in-transit cargo, two seeded hive complexes,
bounded raid lifecycles, supplied local `DEFEND` responses, one-to-one residents and bioforms, immutable
snapshots, validated physical transactions, persisted restart state and the
loaded-chunk graybox projector. The first Python-hive conformance slice adds
finite ecological substrate, gradual scar recovery, local digestive intake and
fail-closed complete-state persistence (Frontier NBT format 15, SavedData
schema 14). It now also
has a bounded, persisted tissue topology and derived command signal. A live
Core/Synapse may commit one strong local tissue cell to an explicit bounded
`MorphogenesisProject`: the project retains kind, source organ, target, cost,
remaining days and revision; `[M] KIND | GROWING | days=n/total` labels it
without claiming a temporary cube. Only completion creates the separately
identified organ cube. Core or source-organ destruction cancels it in that
same canonical observation event; project and dynamic-organ NBT lists carry
content digests, so missing entries fail closed on restart. This remains a
small conformance slice: a separate `HarvesterRun` persists origin, destination,
phase, payload, receiver and revision behind its own content digest. Its
outbound/forage/return flow, failed receiver/source and physical-death spill
paths are restart-tested. Propagation runs, latent colonies and adaptation maps have
their own content digests and reject a partial or contradictory restart; the
carrier, player-clear and Core-maturation paths are domain, NeoForge and visual
GameTest-covered. The supplied coalition campaign is deliberately a bounded
port of the Python front lifecycle: its actual guards, stock reservations,
phase, sector supply result and terminal record all persist as one fail-closed
ledger, and its materialization/recovery has dedicated tests. It does not claim
global territorial beliefs, diplomacy, investment or migration equivalence.
Dead spawned bioforms compact after their retention window once no active
operation references them; deterministic genesis carriers remain for
fail-closed hydration. `tools/frontier/verify_reference_balance.py` pins and
verifies the Python phase vocabulary, `raid_nest` costs/personnel and the
campaign supply/hold/restore constants, while Java tests prove their scaled
use. The first-day readability window is an explicit materialization timing
choice, not a hidden balance fork. Activating physical graybox projection makes
Frontier the exclusive campaign runtime for that world: the historic
campaign/bootstrap clocks do not run alongside it. The current Java raid
cadence and simplified settlement production recipes are explicitly not
claimed as a full Python semantic port. Civic policy and the supplied campaign
are bounded ports of the reference layers; territorial control, investment and
migration remain separate conformance cuts.
