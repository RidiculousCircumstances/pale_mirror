# Pale Mirror: product review 0.2 and vision 0.3

## Status of this document

This is the canonical product-owner review as of 2026-08-10. It evaluates the
implemented system, defines the remaining release gate for 0.2, and fixes the
product direction for 0.3. Implementation history remains in
[`product-vision-0.2-first-living-region.md`](product-vision-0.2-first-living-region.md),
but where the two documents differ on current product status or priority, this
review takes precedence.

## Current assessment

Pale Mirror is an **internal product alpha and an almost-ready technological
release candidate for 0.2**.

The principal technical hypothesis is proven: a canonical event passes through
a scenario, is materialized in Minecraft, accepts a player action, and returns
its result to the domain model without losing causality. The first real
cross-system chain also works:

```text
mine infection
-> supply interruption
-> settlement stock depletion
-> rationing
-> defence loss
-> crisis
-> evacuation
```

The product hypothesis is only partially proven. The system creates a coherent
story, but the player still learns much of it through operator commands,
opaque IDs, diagnostics, and technical physical objects. The live exercise
exposed this gap directly: a floating cube represented the mine, infection and
freight infrastructure were not self-explanatory, and neither the affected
resource nor the available actions were obvious without developer guidance.

| Area | Maturity |
| --- | ---: |
| Domain architecture | **9/10** |
| Persistence and resilience | **8.5/10** |
| Cross-mod abstraction | **8.5/10** |
| Systemic gameplay loop | **6.5/10** |
| Physical world coherence | **4/10** |
| Player comprehensibility | **3/10** |
| Repeatability and replayability | **2.5/10** |
| Readiness as a public gameplay product | **4/10** |

In short: **the technology is stronger than the product**.

## What Pale Mirror is now

Pale Mirror is no longer merely a threat orchestrator. It is:

> **An authoritative world-state engine, settlement simulation, persistent
> materialization framework, and early storyteller runtime.**

It owns mines, settlements, stocks, production, consumption, routes,
population, defence, settlement policy, scenarios, causal history, and
physical jobs.

A settlement is already more than a passive meter. Independently of Narrator,
it can:

- consume reserves;
- introduce rationing;
- request alternate supply;
- lose defence readiness;
- begin evacuation;
- create a displaced population group;
- return population home;
- accumulate prosperity;
- expand stock and housing capacity.

Narrator does not cause these facts. It decides which already-existing facts
are worth presenting as a player-facing story.

This gives Pale Mirror a potential category of its own:

> **A systemic causal layer over modded Minecraft that connects
> infrastructure, settlements, threats, and player actions into durable
> stories.**

## Product thesis and moat

Pale Mirror's value is not that it can launch content from several mods. Its
value is that a result supplied by one mod can become the reason to interact
with another:

```text
not:
infected mob appears
-> kill infected mob

but:
mine becomes infected
-> production stops
-> route fails
-> settlement consumes its reserve
-> defence weakens
-> the player can clear the mine,
   build alternate logistics,
   evacuate the population,
   or accept the loss
```

Crimson, Spore, Create, and future settlement integrations provide physical
expression. They are not the product by themselves.

The canonical product statement is:

> **Pale Mirror makes modded Minecraft causal: places depend on one another,
> settlements make decisions, infrastructure has geographic value, and the
> world remembers the consequences of player choice.**

## Principal product risk

The largest current risk is the **strong invisible engine trap**.

Internally, Pale Mirror already has safe physical resource transfers,
autonomous settlement policy, evacuation and recovery, Create route proof,
settlement adapters, and branching scenario definitions. The player, however,
sees a cube, a mob, a chat line, or a changed number. If the causal model has no
clear physical and presentational counterpart, deep simulation feels random.

Future versions must therefore be evaluated by complete stories that a player
can understand, not by counts of classes, adapters, invariants, or supported
threat forms. A player must be able to answer:

1. What happened?
2. Why did it happen?
3. What is at risk?
4. What can I do?
5. What changed because of my decision?

## Pale Mirror 0.2 release definition

The current build should not yet be declared a complete product release of
0.2. Its technical slices are implemented, but its player-facing release gate
is still open.

The release name is:

> **Pale Mirror 0.2 — First Living Region: Ironhill**

It is complete when one region can be played from discovery to durable outcome
without operator commands or developer explanation.

### Remaining mandatory work

1. Replace the technical cube with a real `MineSite`: entrance, underground
   section, loading endpoint, controller chamber, and physically readable
   infection stages.
2. Materialize the initial `LEGACY_FREIGHT_ROUTE`: mine loading site,
   settlement receiving depot, and a readable road, corridor, or freight
   trace. A Create railway must be understood as an upgrade or replacement,
   not as the first explanation for a previously invisible flow.
3. Complete a positive live end-to-end Create route test with a real scheduled
   train.
4. Provide dynamic player-facing presentation of the causal chain, canonical
   stock, net flow, intervention window, consequences, and available actions.
5. Make Ironhill a repeatable exercise or provide an automatically recreated
   demonstration world.

These are 0.2 release debts, not work to hide inside 0.3.

## Pale Mirror 0.3 — Living Frontier

The transition from 0.2 to 0.3 is:

```text
0.2:
one technically proven living region

0.3:
a repeatable system of living regions
that creates understandable branching stories
```

### North Star

In a new world, without commands, the player:

1. discovers a settlement and its dependent sites;
2. understands where its resources come from;
3. sees a physical disruption of that system;
4. receives several meaningful responses rather than one instruction;
5. responds through combat, logistics, evacuation, or deliberate refusal;
6. observes a durable physical consequence;
7. later receives a continuation caused by that exact outcome.

The canonical demonstration story is:

```text
Mine17 becomes infected
-> Legacy Freight Route stops
-> Ironhill consumes its reserve
-> Ironhill introduces rationing
-> defence falls
-> Narrator presents the crisis

The player chooses:

A. Clear Mine17
B. Build a Create route from Red Valley to Ironhill
C. Prepare a RefugeeSite and evacuate
D. Ignore the crisis

The world preserves the outcome
and later derives another story from it.
```

## Product pillars for 0.3

### 1. Physical truth

Every important domain relationship needs a readable physical representation:

```text
Mine produces IRON
-> the mine has a loading site

Route transfers IRON
-> a road, station, or freight corridor represents it

Settlement receives IRON
-> it has a receiving depot or storehouse

Evacuation occurs
-> a RefugeeSite and representative population appear

Recovery occurs
-> route activity, guards, production, or another visible capability returns
```

Pale Mirror does not need to physically simulate every shipment. It must make
the infrastructure that represents the abstract flow understandable.

### 2. Explainability without debug

0.3 needs a minimal built-in **Pale Mirror Journal**. FTB Quests can remain an
optional read-only projection, but cannot be the only core presentation
because it may be absent and the existing projection is static.

The first native journal only needs to show:

- known regions, settlements, and sites;
- the current causal chain;
- stock and net flow;
- intervention time remaining;
- available responses;
- coordinates or basic navigation;
- recent events;
- consequences of previous decisions.

Operator diagnostics remain valuable for development but must not be required
for ordinary play.

### 3. Meaningful choice

0.3 should prefer one complete branching arc over many linear archetypes. It
must support at least:

- **combat:** clear the infected mine;
- **infrastructure:** connect an alternate source with a real Create route;
- **social response:** prepare evacuation and preserve population at the cost
  of a place or industrial site.

Refusal is also a valid response. It creates a world consequence and later
story rather than merely setting a quest to `FAILED`.

### 4. Visible consequences

| Outcome | Required visible consequence |
| --- | --- |
| Mine cleared | Infection recedes, the MineSite recovers, and legacy freight can resume |
| Create route built | A real train proves route capability and Ironhill receives supply |
| Evacuation | A RefugeeSite appears and Ironhill's resident population falls |
| Crisis ignored | Defence and activity fall; selected PM-owned sites become damaged or disabled |
| Sustained recovery | Guards or activity return and a development opportunity becomes available |

Positive development must be at least as legible as decline. 0.3 does not need
to construct entire districts, but recovery cannot exist only as a number.

### 5. Repeatable regions

The singleton campaign bootstrap must evolve into:

```text
RegionArchetype
RegionLayoutPlan
SitePlacementPlan
RouteCorridorPlan
```

The first archetype is sufficient:

```text
iron_frontier
```

It contains one settlement, one primary mine, one alternate source, one legacy
freight route, potential Create endpoints, a refugee placement area, and
threat-compatible sites. It must instantiate on several world seeds and at
least twice in one world with independent IDs, coordinates, flows, jobs,
scenarios, and histories.

### 6. Narrator v2

Narrator must move from handling a narrow event/rule stream to scoring story
candidates across multiple regions:

```text
ThreatCandidate
SupplyCrisisCandidate
RecoveryOpportunityCandidate
EvacuationCandidate
DevelopmentOpportunityCandidate
```

Scoring considers urgency, object significance, audience relationship,
distance, available combat and transport capabilities, active scenarios,
recent archetype repetition, recent story intensity, and the option to choose
`NO_SCENARIO`.

The defining change is not scoring complexity by itself. Narrator must have
several regions and several real stories from which to choose.

### 7. Continuation after the outcome

At least one delayed consequence must prove that event history is world memory
rather than a log of closed quests:

```text
Mine recovered
-> IRON supply resumes
-> prosperity rises
-> DevelopmentOpportunity appears later
```

or:

```text
Ironhill evacuated
-> PopulationGroup becomes DISPLACED
-> Resettlement, Supply, or Escort opportunity appears later
```

## Minimum functional scope of 0.3

### Domain

- multiple `Region` and `Settlement` instances;
- `MineSite` and `AlternativeSourceSite`;
- `RouteContract` and `ResourceFlow`;
- `RefugeeSite`;
- one strategic resource: `IRON`;
- one prosperity/development consequence;
- delayed consequences derived from history.

### Physical world

- a real MineSite;
- a readable legacy freight corridor;
- a receiving depot;
- a Create logistics endpoint;
- a refugee camp;
- minimal damage and recovery representations.

### Scenarios

- `investigation_recovery`;
- `settlement_supply_crisis`;
- `alternative_logistics`;
- `evacuation`;
- one `aftermath` or `development` archetype.

### Narrator

- candidate generation and scoring;
- pacing and novelty penalty;
- team relevance;
- explicit `NO_SCENARIO`.

### Presentation

- a minimal Pale Mirror Journal;
- coordinates or basic region navigation;
- a causal timeline;
- selected and available choices;
- an explanation of the resulting consequence.

## Explicit non-goals for 0.3

To protect the milestone, 0.3 excludes:

- a third infection provider;
- mixed Crimson and Spore zones;
- global war or a global scalar `WorldTier`;
- a complete commodity economy or many resources;
- PM ownership of Millénaire's full local economy;
- villager AI control;
- automatic construction of large city districts;
- recognition of any arbitrary Create factory;
- completion of every Crimson and Spore mob/content catalogue;
- stabilization of a broad public adapter SDK;
- complex faction or diplomacy simulation;
- a costly full world-map UI.

Crimson remains the default threat source. Spore remains optional and useful as
a compatibility test or deliberately authored local threat.

## Release gates for 0.3

### Gameplay

- A region appears or is discovered without operator commands.
- The player naturally finds Ironhill, Mine17, and their freight relationship.
- The crisis is understandable without `/timeline`, `/debug`, or developer
  explanation.
- Combat, real Create logistics, and evacuation paths all complete.
- Every outcome has a visible physical consequence.
- At least one causally linked follow-up story appears after an outcome.

### Repeatability

- The region archetype instantiates successfully on at least three seeds.
- Two independent regions can coexist on one server.
- Their objects, flows, scenarios, jobs, and histories never mix.
- Restart and crash recovery produce no duplicates.
- The complete exercise can be repeated in a clean exercise world.

### User experience

In a usability test with people who have not read the architecture document,
at least four out of five participants must answer without prompting:

1. What happened?
2. Why is Ironhill in crisis?
3. Which responses are available?
4. Which consequence did their decision cause?

No player should need an opaque internal ID during normal play.

### Technical quality

The existing strengths remain release gates:

- pure-Java domain tests;
- packaged-JAR verification;
- restart/crash harnesses;
- graphical smoke profiles;
- GameTests;
- provenance and conflict safety;
- idempotency;
- adapter health;
- fail-closed migrations.

## Development priority

1. Close 0.2: MineSite, legacy freight route, dynamic journal, real Create
   end-to-end validation, and a repeatable exercise.
2. Replace singleton bootstrap with `RegionArchetype` and placement plans.
3. Complete the three outcomes of one supply-crisis arc.
4. Add visible recovery and evacuation consequences.
5. Add multi-region Narrator candidate scoring.
6. Add delayed aftermath.
7. Run external usability testing.
8. Only then expand resources, settlement types, and adapters.

## Product conclusion

Pale Mirror is currently **a strong systemic engine inside a weak
demonstration shell**. It has proven the negative causal chain:

```text
Mine
-> Route failure
-> Shortage
-> Defence loss
-> Evacuation
```

It has not yet proven the complete product loop:

```text
natural discovery
-> causal understanding
-> informed choice
-> combat or infrastructure
-> physically visible consequence
-> next story
```

0.3 must therefore not be “more simulation.” Its definition is:

> **The player sees and understands a living region without developer help,
> changes its fate through several fundamentally different choices, and the
> world continues the story from the resulting outcome.**
