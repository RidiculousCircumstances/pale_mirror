# Pale Mirror 0.2: First Living Region

## Purpose and status

Pale Mirror has proven its primary technical hypothesis: an abstract canonical
state can be safely materialized through different mods, and the result of a
player action can be reconciled back into the domain model.

This is a substantial milestone. The product value of Pale Mirror, however, is
not the number of infection adapters or implementation invariants. It appears
when a player can tell a causal story such as:

> The mine supplying a settlement with iron became infected. I did not clear it
> in time, the town's defence weakened, and attacks began. I closed the mine,
> found another ore source, and built an aerial supply line.

The next product objective is to make that kind of story possible.

**Current product position:** Pale Mirror 0.1 is a proven engine for one
managed aggregate and one causal story type:

```text
Mine + Threat + Scenario + Physical representation
```

It is authoritative for controlled threat objects. It is not yet a broad,
authoritative world simulation: the causal relationships below are still to be
proven in one coherent region.

```text
Mine -> Route -> Settlement -> Defence -> Migration -> Infrastructure
```

This document began as product direction. The status section below records the
implemented 0.2a/0.2b vertical-slice work; the Definition of 0.2 remains the
release gate, not a claim that every later presentation or settlement adapter
is complete.

## Implemented vertical-slice baseline

The current branch implements one new-world-only region bound to an observed settlement:

```text
Ironhill (population 80, iron demand 12, defence 55)
  <- Mine17 primary IRON route (capacity 18)
  <- Red Valley replacement route (initially PLANNED)
```

- The pure domain owns `Settlement`, bounded `ResourceStock`, `Route`,
  `ResourceFlow`, `MigrantGroup`, and `LivingRegion` aggregates. Resource flow
  is sorted and deterministic.
- A discovered Ironhill schedules one global Mine17 infection after its pinned
  intervention delay. Its stock then depletes, defence falls, and one
  audience-scoped `settlement_supply_crisis` offer is created from the actual
  shortage event.
- The accepted crisis has three implemented canonical resolutions: controller
  clearance and mine recovery; an observed alternate route; or an explicit
  evacuation that creates a persistent abstract migrant group. A caravan is
  deliberately not faked.
- A bounded vanilla/Integrated Villages observer registers a settlement only
  from loaded facts: at least two villagers and one stable bell/bed landmark.
  It writes no village blocks, entities, jobs, or development state. A normal
  player can use that bell/bed as a journal and accept the offer or evacuate
  without operator permission. `/pale_mirror explain settlement <id>`,
  `/pale_mirror timeline <id>`, and `/pale_mirror logistics status` expose
  causal and physical evidence to operators.
- The campaign job now owns only the two controlled mine templates. It saves a
  terrain anchor and `RUNNING` before each physical operation; after restart
  its ensure operation reconstructs or finishes the same mine without
  overwriting a partial or player-altered volume. It never materializes a
  replacement settlement.
- The first Create contract is read-only and version-pinned to 6.0.10. It
  recognises two named, nearby `Track Station`s only when their chunks are
  already loaded. The same opaque native train UUID must arrive at both
  endpoints within a bounded simulation window to certify LOW/MEDIUM/HIGH
  capacity; two arbitrary trains, missing evidence, or unloaded endpoints
  never invent capacity or force-load chunks.
- FTB Quests 2101.1.30 is an optional read-only presentation adapter. It
  installs one PM-owned static, no-reward chapter through FTB's public config
  and reload command, refuses to overwrite an unknown chapter collision, and
  opens it with FTB's public `open_book` command. PM never reads or writes FTB
  team progress.

The core GameTest suite validates the canonical regional plan, observed village
provenance, same-vehicle route-proof persistence, and v19 restart snapshot.
Separate checksum-pinned Create and FTB profiles boot their real mod stacks;
the FTB profile loads the PM chapter. Packaged dedicated restart/crash harnesses
cover core, Crimson, Spore, Create, and FTB. Graphical render bootstrap smoke
profiles cover core, Crimson, Spore, Create, and FTB under Xvfb.

An automated GameTest does not manufacture a fake Create vehicle. The real
scheduled-train acceptance is instead a documented interactive server
walkthrough: it must observe a player-built native train schedule at both
stations before canonical capacity changes.

## What is already strong

### 1. The domain-to-mod boundary is proven

Crimson and Spore are plugged in through the source-neutral
`SourceThreatAdapter`. The domain does not know their entities, blocks, native
mappings, or source policy. This demonstrates that a threat source is
replaceable.

The two adapters are presently valuable primarily as an abstraction and
compatibility test, not as two mandatory simultaneous global apocalypses.

Product policy for 0.2:

- Crimson Curse is the primary canonical infection threat.
- Spore is an optional profile, a second compatibility test, and a potential
  local source for deliberately designed future scenarios.
- Do not activate Crimson and Spore as parallel global defaults. Competing
  aesthetics and progression models obscure causality for players.

### 2. This is a reconciliation loop, not a spawn manager

The important implemented loop is:

```text
desired domain state
-> persisted materialization job
-> physical representation
-> player action
-> typed observation
-> canonical state update
```

That loop is the foundation for routes, settlements, infrastructure, and
long-lived stories. It is more valuable than the ability to spawn a particular
infected creature.

### 3. The system has an operational safety discipline

The current foundation includes:

- idempotent materialization;
- desired and observed revisions;
- protection of unknown/player changes;
- persisted leases for dangerous physical effects;
- explicit adapter capability health;
- intentional rejection of unsafe schema v5-v15 migration;
- GameTests and packaged-JAR verification;
- a firewall that prevents source items from silently entering the economy.

These measures make a long-lived world less likely to become corrupted after
many hours of play and restarts.

### 4. Story audience is separate from the world fact

An infected mine is global, and its physical controller is unique. A scenario
is addressed to one audience, but another player may physically intervene; the
world outcome remains one shared outcome. This model is appropriate for both
single-player and cooperative servers.

## The next vertical slice: Mine--Settlement--Route

Do not first add a third infection mod or a large global war. First prove that
several domain systems causally affect one another inside one living region.

Add the second and third object families:

```text
Mine
Settlement
Route / ResourceFlow
```

This slice determines whether Pale Mirror is a general systems core rather
than an excellent threat orchestrator.

### Reference scenario: Ironhill

Ironhill starts with an abstract, observable state:

```text
Settlement: Ironhill
population = 80
iron demand = 12/day
defence = 0.55

Mine: Mine17
iron production = 18/day
route = Mine17 -> Ironhill
```

When Mine17 becomes infected:

```text
Mine17: OPERATIONAL -> INFECTED
production: 18 -> 0

after several simulation steps:
Ironhill iron stock decreases
tool availability decreases
defence readiness decreases
```

The Narrator should identify the causal chain and present several valid
responses, rather than one linear objective.

#### A. Clear and recover the mine

The existing investigation/recovery loop remains valid:

```text
investigate source -> destroy controller -> wait for recovery -> restore production
```

#### B. Seal the mine and replace supply

The player does not clear the site. They contain further spread, find another
iron source, establish a replacement route, deliver temporary stock, and later
build Create infrastructure. The old mine may remain lost while the settlement
survives.

#### C. Evacuate or abandon

The player does not save production but helps part of the population leave.

```text
Ironhill:
population decreases
defence decreases
status = DECLINING or ABANDONED

new world object: RefugeeCamp or MigrantGroup
```

Failure must generate a subsequent story, not merely a terminal `FAILED`
screen. This one branching scenario will provide more product evidence than
several additional linear archetypes.

## Minimal next domain model

Start with a deliberately small economy:

```text
Settlement
Route
ResourceFlow
Stock
Production
Consumption
Shortage
Surplus
Defence
```

Use only a few resource categories at first:

```text
FOOD
IRON
TIMBER
TOOLS
MEDICINE
MILITARY_SUPPLIES
```

The first cross-domain proof needs only:

```text
Mine produces IRON
Settlement consumes IRON
Route transfers IRON
Shortage lowers DEFENCE
```

Do not simulate carrots, ingots, or individual tools one by one. Those details
add accounting without necessarily creating stories.

## Integration order

### SettlementAdapter: observe before changing

Do not begin by taking over Millénaire construction. The first
`SettlementAdapter` is read-only and should:

- discover a settlement;
- assign a stable `WorldObjectId`;
- define bounds and anchor;
- estimate population;
- observe deaths and defenders;
- map physical settlement facts to canonical `Settlement` state.

Begin with vanilla villages or Villager Overhaul, then add a Millénaire adapter.

Only after the mapping is trustworthy should PM materialize limited observable
state, for example:

```text
food shortage -> fewer traders, weaker assortment, visibly empty storehouse
low defence -> fewer guards, damaged fortification, alert state
prosperity -> market activity, additional NPCs, improved storehouse
```

Native settlement development should be a later, audited opt-in. If Millénaire
offers safe development controls, PM may request them; otherwise use PM-owned
prefab expansion policies. Do not start by rewriting settlement AI or building
logic.

### CreateAdapter: the next high-value adapter

Create should arrive before more threat providers. It makes the systems model
into a distinctive game rather than an event generator.

PM must not try to infer every player factory. Instead, introduce an explicit
physical-to-domain contract:

```text
FacilityAnchor
LogisticsNode
RouteEndpoint
```

The player links a real Create installation to a PM object. The adapter then
checks only bounded, meaningful capabilities:

- mechanical energy is present;
- a registered container is connected;
- a transport line is connected;
- the registered installation is operational;
- capacity is LOW, MEDIUM, or HIGH.

It publishes generic observations such as:

```text
TransportRouteOperational
ProductionFacilityOperational
SupplyCapacityObserved
```

Pale Mirror does not need to know which gears were used. Aeronautics can later
be another transport capability:

```text
AIR_SUPPLY
MEDIUM_CARGO
LONG_RANGE
```

## Player-facing explanation is now product work

Chat commands were enough to prove the architecture. They are not enough for a
player to understand a settlement crisis. The player needs to see what changed,
why it changed, what evidence PM used, what choices remain, how long they have,
and what refusal will cost.

Prioritize FTB Quests as a read-only presentation layer. It must never own
canonical state, but it can present:

```text
Source: Mine17 stopped supplying iron
Consequence: Ironhill is losing defensive readiness
Responses: clear the mine; create alternative supply; prepare evacuation
```

Add operator-facing explainability as a first-class tool:

```text
/pale_mirror explain settlement:ironhill
/pale_mirror timeline mine:17
/pale_mirror narrator explain <scenarioId>
```

For example, Narrator diagnostics should show severity, settlement relevance,
audience reachability, available capabilities, archetype novelty, and cooldown
status. Without this, narrative balancing will become opaque.

## Narrator v2 direction

The current single archetype was the correct first step. The next Narrator
should score candidates for tension and opportunity, rather than react directly
to isolated events:

```text
TradeDisruptionCandidate
SettlementCrisisCandidate
ThreatEscalationCandidate
RecoveryOpportunityCandidate
ExpeditionCandidate
```

Candidate selection should consider:

- urgency and object significance;
- the audience's relationship and distance to the object;
- real available technology/capabilities;
- recent stories and archetype repetition;
- active scenarios and possible continuations;
- storyteller profile;
- a valid `NO_SCENARIO` outcome.

The Narrator must regulate pacing and variety, not simply choose the largest
numeric problem. After two combat stories, it should be able to favor trade,
recovery, construction, or expedition.

## Progression model and fairness policy

Do not introduce a single global `WORLD_TIER` now. It would flatten regional
identity and make the whole world harder at once. Use separate axes instead:

| Axis | Role |
| --- | --- |
| `WorldEra` | Rare global stages such as `FRONTIER`, `INDUSTRIAL`, `AIR_AGE`, `ENDGAME`; unlocks pools, not creature HP multipliers. |
| `RegionalThreatLevel` | Local danger for a territory. |
| `SettlementDevelopment` | Maturity, resilience, and available institutions of one settlement. |
| `AudienceCapability` | What an audience can actually do: long-range travel, mass supply, air transport, heavy cleanup, endgame combat. |
| `SourceThreatTier` | The local escalation of a specific infection source. |

The Narrator should compare threats to audience capability, not merely to a
global era.

Autonomy must not make known player investments disappear without a fair chance
to act. The default policy is:

- abstract state and gradual degradation may advance without players online;
- known, significant objects usually receive an intervention window before an
  irreversible catastrophic transition;
- the window accounts for distance and transport capability;
- a hardcore profile may deliberately reduce this protection;
- unknown remote objects may still fail without warning.

For example:

```text
Mine infected -> settlement shortage -> audience warning -> grace period
-> defence deterioration -> possible settlement loss
```

## Explicit deferrals

Do not prioritize these before the First Living Region is proven:

- a third infection adapter;
- mixed Crimson/Spore zones;
- global item-by-item commodity economy;
- full source-mod recipes and equipment;
- automatic recognition of arbitrary Create factories;
- automatic takeover of all existing structures;
- physical city growth and district construction;
- dozens of scenario archetypes;
- a complex custom client UI.

They broaden the surface area without proving the core product value.

## Definition of Pale Mirror 0.2

**Pale Mirror 0.2: First Living Region** is complete when, without operator
commands, a player can:

1. discover a registered mine and its connected settlement;
2. see that the mine materially supplies the settlement;
3. encounter its infection;
4. understand the causal chain through a journal/presentation layer;
5. receive at least three meaningful responses;
6. solve the crisis through combat, logistics, or deliberate abandonment;
7. observe a durable settlement change;
8. restart the server without state divergence;
9. receive a follow-up story after either success or failure.

Minimal stack:

```text
Crimson Curse: primary threat source
Spore: optional/test provider

Mine + Settlement + Route + ResourceFlow

Investigation/Recovery + Supply disruption + Abandonment/Evacuation outcome

FTB Quests: read-only presentation
CreateAdapter: one functional logistics contract
```

## Practical order of work

### 0.2a: close operational evidence gaps

1. Run the final packaged JAR on a dedicated server.
2. Perform process-level restart and crash tests.
3. Run graphical client smoke tests for Crimson and Spore.
4. Verify animations, sounds, particles, AcidBall behavior, and cleanup.

### 0.2b: build the first living-region slice

1. Add `Settlement`, `Route`, and `ResourceFlow` domain aggregates.
2. Implement a controlled, read-only settlement adapter.
3. Connect Mine17-style production to settlement supply and defence.
4. Implement shortage and defence consequences.
5. Implement the one branching crisis scenario.
6. Add FTB Quests read-only presentation and explain commands.
7. Add the first Create logistics capability.
8. Only then implement Narrator v2 and natural discovery/worldgen.

## Product conclusion

The technical foundation is now good enough that horizontal expansion is the
main risk: more sources, forms, and adapters could grow without demonstrating
that the result is an interesting game.

The next goal is not:

> Pale Mirror supports another mod.

It is:

> Pale Mirror creates a story in which a threat to one object changes a
> settlement, the player chooses a response, applies combat or logistics, and
> the world preserves the consequence.

That milestone turns Pale Mirror from a strong orchestration engine into the
systemic core of the intended modpack.
