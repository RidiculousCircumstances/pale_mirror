# Frontier v3 hive physiology and infection contract

Status: accepted target design; implementation is incomplete.

This document is normative for Frontier v3. It refines the hive, infection,
bioform and materialization requirements in `frontier-v3-contract.md`. Numeric
radii, rates, capacities and costs belong to the persisted `FrontierRuleset`;
the identities, ownership boundaries and causal rules below are invariants.

## Player promise

The player encounters one distributed living organism whose territory,
circulation, perception, command, mobilization, feeding, wounds and adaptation
are visible physical processes. Every capability has a locatable source and a
physical counterplay; neither chunk loading nor an unseen global query may
create knowledge, awaken bodies or restore damage.

## Three distinct physical layers

Infection, hiveroot and organs are related but never interchangeable.

1. **Infection surface** is the alien biosphere and territorial substrate. It
   retains sparse 4×4 canonical cells with intensity, finite extractable
   substrate and metabolism. It may spread, retreat, recover and contaminate
   structures, but is not itself a transport or command network.
2. **Hiveroot** is a bounded connected graph of stable, destructible vascular
   segments and junctions. One physical graph carries two independently
   degradable capacities: nutrient/biomass flow and synaptic signal.
3. **Organs and bioforms** attach to that graph. They can use only the
   connectivity, perception and exact local resources which actually reach
   them.

Passive extraction is never an infinite resource faucet. It reduces a cell's
finite substrate or consumes bounded regeneration produced by the ecology,
carcasses or delivered items. A disconnected infected surface may remain
alien terrain without supplying or informing the wider hive.

## Hiveroot geometry and damage

Hiveroot is a visible infrastructure network rather than a uniformly recolored
infection carpet.

- Primary trunks are readable raised routes, approximately three to five
  graybox blocks wide; secondary branches are one to two blocks wide.
- Branches visibly converge into organ bases. Most of the graph remains above
  ground; short shallow buried sections are allowed, but a player must be
  able to trace the important topology without an operator overlay.
- The body, vascular channel, synaptic strand, junction and growth tip are
  separate semantic parts. Decorative loss alone does not sever a segment;
  destruction of its required cross-section or junction does.
- The immutable segment plan owns its geometry. Canonical condition plus
  bounded sparse physical deltas retain damage; the simulation does not copy
  every Minecraft block into canonical state.
- An explosion may damage any reachable part. Desired-state materialization
  never restores the former graph over physical aftermath or a player change.
  Repair is a later hive operation consuming exact local resources.

The graybox visual grammar is stable even though final art may replace its
blocks: alien surface is purple, root tissue is dark red, vascular flow is
orange, synaptic activity is bright pink, starved tissue is desaturated brown
and destroyed tissue is black or physically absent. Flow is shown through
bounded local pulses or particles, not by repainting the entire network every
tick. Infection contours, root topology and organ silhouettes must remain
distinguishable from player eye level and at approach distance.

## Distributed cognition and organs

`HIVEMIND` names the whole distributed intelligence and is not an organ.
`HEART` is not a valid final organ identity. The accepted functional organ set
is:

| Organ | Owned capability |
| --- | --- |
| `GANGLION` | Local strategic integration and connection of one nest branch to the distributed Hivemind. |
| `RELAY` | Large-radius stationary synaptic coverage while connected to a functioning signal path. |
| `STORE` | Exact local item/nutrient custody and physiological reserves. |
| `DIGESTER` | Timed conversion of carcasses and accepted organic items into exact usable biomass/nutrients. |
| `BROOD` | Creation and maturation of new exact organisms. |
| `HIBERNACULUM` | Exact cocoon slots for mature dormant or recovering organisms. |
| `MORPHER` | Resource-backed transformation and installation of visible adaptations. |
| `SPORULATOR` | Production of infection pressure and propagules. |
| `SENSOR` | Stationary local observation; it never grants global hidden knowledge. |

Vascular pumps, valves and graph junctions are hiveroot infrastructure rather
than extra organ kinds. Healing is a resource-consuming connected process, not
a free global healing organ. Destroying one Ganglion degrades its branch but
does not kill the distributed Hivemind or erase facts already transmitted to
another surviving owner.

The Java organ vocabulary now uses the complete accepted set with explicit
non-reused tags (`GANGLION=10` through `SENSOR=18`). The seed profile creates
only its currently implemented `GANGLION`/`BROOD`/`STORE`/`HIBERNACULUM` organs; `RELAY` is
the first growth output, and the remaining organ types must not be fabricated
until their own owning process exists. That organ-vocabulary cut was schema 103
and persistence envelope 12; the current physiology cut supersedes it with schema
109/envelope 19, still fresh-world-only. The retired `HEART` byte `0` is intentionally
unmapped and fails closed rather than being reinterpreted as `GANGLION`.

## Exact bioform model

One bioform remains one canonical organism. The model separates four axes:

- **chassis**, initially `RUNT`, `OVERSEER`, `SENTINEL` and a future heavy
  chassis;
- **visible mutations**, initially explosive, mucus, armored, spore and
  carrier capabilities;
- **assignment**, such as watch, scout, harvest, carry, repair, defend,
  assault, siege or retreat;
- **lifecycle**, vitality, wounds, structural biomass, reserve biomass,
  controller and physical custody.

The initial Java profile cut is schema 104 and persistence envelope 13. It
removes `WORKER/SCOUT/GUARD/BOMBER` entirely: a Sentinel Scout is
`SENTINEL + SCOUT`, a defender is `DEFEND`, and the current bomber is
`RUNT + EXPLOSIVE + ASSAULT`. Chassis, visible mutation set and current
assignment are canonical fields and independent stable-tag persistence values.
Vitality remains the existing exact `ActorLocation.condition`; current body
position and HOT physical custody remain that actor location plus its persisted
ambient lease. Schema 109/envelope 19 retains cocoon/lifecycle, exact task mobilisation and
the one-edge expected-cursor COLD assembly receipt only as
an exact phase
plus an optional hibernaculum slot; wounds, structural/reserve biomass and
controller ownership remain later owning-process cuts rather than duplicated
prematurely in this profile.

Graybox colour/geometry must expose chassis, important mutation and current
assignment without creating a cohort or hidden population multiplier.

### Runt

The Runt is the persistent low-cost base organism, not an aggregate population
or disposable visual effect. It can fight, carry, forage, guard and feed other
forms. A Runt may retain its exact identity while being modified or matured
rather than being deleted and replaced by an unrelated elite.

### Dormancy and mobilization

A mature inactive organism occupies one exact cocoon slot. Its lifecycle is:

```text
DORMANT -> WAKING -> ASSEMBLING -> ACTIVE -> RETURNING -> RECOVERING -> DORMANT
                                      \-> DEAD -> CARCASS -> RECYCLED
```

While `DORMANT` or `RECOVERING`, the exact identity is physically claimed by
the cocoon and no duplicate Minecraft mob exists. A cocoon may feed, repair or
modify its occupant only through connected exact resources. Breaking the
cocoon may wound, kill or prematurely release that same occupant according to
the observed damage; it never teleports the organism to another slot.

Chunk loading is never a mobilization cause. Only exact current work or threat
does so. The quiet territory keeps a bounded set of purposeful Sentinels,
guards, harvesters, carriers and repair forms outside; it has no ambient random
swarm. Mobilization has a visible warning, wake-up interval, assembly and
physical departure. Survivors physically return before a cocoon can reclaim
them.

### Current implemented boundary

Each seed nest has three 3×3-slot `HIBERNACULUM` trays. The fresh profile has
48 exact bioforms: one Scout and one Defender per nest begin deployed; the
other 44 occupy individually owned visible white cocoon blocks in cyan trays.
A dormant cocoon has no ambient Zombie. Breaking its owned cocoon records the
ordinary `KNOWN_SEMANTIC_LOSS`, moves that same `ActorLocation` to the tray
floor and changes only that bioform to `WAKING`. A naturally loaded successful
ambient lease materializes that same identity and changes it to `ACTIVE`.

The first task/threat-driven mobilization boundary is now also implemented.
Every settlement-assault group includes one exact dormant `OVERSEER` as its
durably retained controller plus its exact breach members; selection fails
closed when the named nest cannot provide that controller. One current assault task may select one bounded nest-local exact group, turn
only those dormant identities to `WAKING`, then open their still player-breakable
cocoons one at a time through a naturally demanded durable release effect. A
confirmed early member remains `ASSEMBLING` with no ambient body until every
selected cocoon has an observed release receipt; then the whole exact group may
materialize and visibly assemble. An external player break always wins as an
ordinary physical delta: it wakes only that occupant and visibly conflicts the
interrupted operation instead of being misrepresented as the executor's receipt.
Patrol, route-engagement and settlement-assault selection reject cocoon-retained
or task-committed bioforms. Before a group is admitted, its exact non-Overseer
members consume a persisted ruleset command weight derived from chassis and
visible mutations; an overloaded/forged group fails closed. Departure and
return/recovery remain later operation boundaries; chunk loading and direct
body moves are never wake authority.

#### Assembly/departure contract (next vertical)

`ASSEMBLING` must not mean “spawn the released bodies near the nest.” The task
will retain one exact `HiveTaskAssembly`: its named Ganglion departure port,
one distinct staging surface per released member, and one bounded
`GROUND_BIOFORM` topology/cursor per member. The port is compiled from the
same immutable nest/terrain plan as the organ supports; a consumer never
derives it from a fixed coordinate relative to a nest anchor. Its outward
orientation is selected deterministically from the retained target settlement,
then retained in the task assembly rather than recalculated from a later world
query.

The compiler may use the selected members' own open Hibernaculum trays as
their first support surfaces, but it must not traverse another organ's tissue,
invent a height, or treat an unloaded Minecraft block as an edge. Every
retained edge has the ordinary 3D grade/clearance/capability evidence; an
off-grade or organ-obstructed plan fails visibly before departure. The later
COLD/HOT progression owner advances only this cursor from observed arrival. A
loaded obstruction or missing body will be one member-specific durable
deferral/conflict; it neither teleports a replacement nor asks COLD to route
around it. Until that owner is registered, a retained assembly cannot depart
or create an assault. Only when all exact members occupy their retained staging
slots may one atomic transition create the existing first-class settlement
assault from those identities. That transition retains the same Overseer and
group roster; it may not reselect whichever ambient forms happen to be closest.

### Biomass, wounds and consumption

Each living organism owns structural biomass, reserve biomass, health and
bounded wounds. A death creates one exact carcass or observed destroyed
remainder. Eating transfers conserved biomass out of that source; digestion
may lose a configured fraction, and healing or growth consumes the receiver's
reserve. Feeding cannot both heal a creature and leave the same biomass in the
source.

The hive may consume carcasses, sacrifice a living Runt through an explicit
physical task, or transfer biomass between forms. These are visible,
interruptible acts. Dormancy has lower but nonzero maintenance cost; active
forms consume more, giving the hive a systemic reason not to wander with its
whole population.

## Observation and command topology

### Territorial command

Inside connected territory, a functioning `RELAY` supplies a ruleset-defined
large command radius through the synaptic capacity of hiveroot. A relay has no
knowledge of actors it did not sense or receive through a valid report.

`SENTINEL` is a flying purposeful observer. It detects only through its
physical sensor model and line of sight, retains an exact observation, and
must transmit that observation to a connected Relay, Ganglion or Overseer.
Killing it before transmission prevents the Hivemind from learning that fact;
killing it after transmission does not erase knowledge already received.

### Expeditions and Overseers

A coordinated operation outside stationary relay coverage requires at least
one exact `OVERSEER`. The Overseer is a mobile synaptic relay with bounded
weighted command capacity and an exact list of subordinate bioform IDs.
Modified and heavy bodies may consume more capacity than a basic Runt.

If the last valid Overseer is lost or disconnected, subordinates retain the
last order for a bounded signal-memory interval and then fall back to explicit
instincts: engage an immediate threat, protect current cargo, seek signal,
return or become locally feral. They do not die, freeze, disappear or continue
complex coordinated replanning. Another Overseer may later reclaim them.

Overseer survival comes primarily from behavior: distance, cover, subordinate
screens, route choice, early retreat and mucus obstruction. It must not be an
HP sponge, gain hidden invulnerability, teleport, or redirect damage without a
physical intermediary.

Direct proximity or sustained line of sight may cause synaptic interference:
bounded edge blur, afterimages, doubled silhouettes and spatial audio
distortion. Intensity depends on distance, sight duration and active signal
capacity, drops when line of sight breaks, never reverses controls or forces
camera motion, and has an accessibility mode using high-contrast cues without
strong blur.

## Accepted alien mechanics

These are target mechanics. The first seven belong to the core hive lifecycle;
the remaining mechanics may arrive as later vertical slices but their state
model must not be designed out.

1. **Reflexive surface.** Dense connected infection can sense configured
   physical disturbances and send a visible bounded signal toward a Relay.
   Severed tissue cannot inform the Hivemind, and false disturbances may cause
   a real costly response.
2. **Mucus signal lines.** Mucus Runts create visible temporary conductive,
   slowing tissue which extends only local signal and movement support. It
   dries, burns or is removed physically and never replaces the mandatory
   Overseer for a remote expedition.
3. **Biomass transfer and sacrifice.** One form may visibly give exact reserve
   or structural biomass to heal an elite, sustain a carrier or charge a
   payload, weakening or killing the donor.
4. **Carcass recovery.** The hive assigns carriers to retrieve its own and
   foreign bodies. A returned observer body may yield observations not already
   transmitted; destroying or taking that body denies both material and any
   retained evidence.
5. **Evidence-backed adaptation.** A survivor, carcass or valid observation
   must reach a Morpher/Ganglion before a local visible counter-adaptation is
   selected. Adaptations cost resources, occupy bounded capability slots and
   never become universal immunity.
6. **Emergency circulation.** Valves may starve one branch to reinforce
   another, visibly changing flow, organ activity, cocoon recovery and local
   defense. This is resource redistribution, not resource creation.
7. **Premature waking.** A threatened hive may mobilize incomplete, wounded or
   underfed occupants, allowing sustained pressure to exhaust its reserves and
   recovery capacity.
8. **Grafting.** A named bioform may enter `GRAFTED` and become a temporary
   sensor, relay, pump, barrier or weapon while retaining its identity,
   biomass and wounds. It is not a free new organ.
9. **Unstable payload maturation.** An explosive form visibly accumulates
   exact biomass, trading speed and safety for blast strength. Ordinary damage
   may detonate it early and harm any nearby hive or player structure; no
   friendly-fire protection exists.
10. **Live digestion window.** A captured living actor may progress through
    restrained, enclosed and digesting stages before irreversible consumption.
    Each stage is physically visible and allows a real rescue or interruption;
    completion transfers conserved biomass and exact casualty consequences.

## HOT/COLD and physical causality

HOT and COLD execute the same lifecycle, assignments, signal topology,
resource transfers and operation cursors. HOT uses actual movement, sight,
combat, collision, inventories and effects; COLD advances only retained exact
actors and causal events. Neither side owns a substitute population or combat
result.

On return to a naturally loaded area, the player sees the current exact
continuation: occupied or empty cocoons, active assigned bodies, severed or
rerouted roots, consumed carcasses, current adaptations and the aftermath of
off-screen operations. A physical executor never spawns idle bodies merely to
make the hive look active, and never replays elapsed animation or an already
accounted blast.

All root segments, organs, cocoon slots, carcasses, grafts and active bodies
have stable semantic ownership. Loaded-world changes enter normal physical
observation and reconciliation. Unknown or player-authored geometry is not
silently overwritten; legitimate autonomous effects have no protected-zone
filter and may damage any reachable object.

## Required evidence

Implementation is not complete until focused normal, negative and restart
scenarios prove at least:

- a Sentinel sees and reports a player, while a killed pre-report Sentinel
  leaves no hive knowledge;
- a quiet loaded nest retains most exact organisms in readable cocoons and
  awakens only named workers or defenders for real tasks;
- a remote assault cannot begin coordinated travel without its exact Overseer;
- Overseer loss breaks command after signal memory without deleting actors,
  and a second Overseer can reclaim eligible survivors;
- a severed root independently blocks nutrient and/or signal consequences,
  persists across restart and is repaired only through a resource-backed task;
- cocoon damage affects its exact occupant and cannot duplicate or relocate it;
- carcass consumption conserves biomass and player removal denies recovery;
- a charged explosive form can cause unrestricted friendly physical damage and
  the full aftermath reconciles once;
- departure, COLD continuation and return retain the same bodies, cocoons,
  wounds, signal ownership and operation state;
- player-eye and approach-distance frames distinguish infection, hiveroot,
  Ganglion, Relay, Hibernaculum, active flow, starvation and severance without
  relying on operator diagnostics.

Numeric balance and final art remain unproven until same-seed scale/JFR runs
and unbriefed player-comprehension audits pass. Passing domain or GameTests is
not visual or product acceptance.
