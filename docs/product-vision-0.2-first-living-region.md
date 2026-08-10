# Pale Mirror 0.2: First Living Settlement

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
implemented 0.2a/0.2b baseline and the corrective 0.2c actor synchronization;
the Definition of 0.2 remains the release gate. The canonical settlement design
is defined in [`settlement-actor-model.md`](settlement-actor-model.md).

## Implemented vertical-slice baseline

The current branch implements one new-world-only region bound to an observed settlement:

```text
Ironhill (observed population; datapack baseline 80, iron demand 12, defence 55)
  <- Mine17 primary IRON route (capacity 18)
  <- Red Valley replacement route (initially PLANNED)
```

- Schema v20 replaces the prototype aggregate with separate
  `SettlementCommunity`, `SettlementPlace`, economy, security, pinned policy,
  independent `WorldSite`, and freshness-bounded `RouteContract` records.
  Resource flow and settlement decisions are sorted and deterministic.
- A discovered Ironhill schedules one global Mine17 infection after its pinned
  intervention delay. Its stock then depletes, defence falls, and one
  audience-scoped `settlement_supply_crisis` offer is created from the actual
  shortage event.
- The accepted crisis has two release-critical resolutions: controller
  clearance and mine recovery, or a validated alternate Create route.
  Prototype evacuation was removed rather than freezing the wrong aggregate.
- A bounded vanilla/Integrated Villages observer registers a settlement only
  from loaded facts: at least two villagers and one stable bell/bed landmark.
  It writes no village blocks, entities, jobs, or development state. A normal
  player can use that bell/bed as a journal and accept the offer without
  operator permission, then pursue combat or alternate logistics.
  `/pale_mirror explain settlement <id>`,
  `/pale_mirror timeline <id>`, and `/pale_mirror logistics status` expose
  causal and physical evidence to operators.
- The campaign job now owns only the two controlled mine templates. It saves a
  terrain anchor and `RUNNING` before each physical operation; after restart
  its ensure operation reconstructs or finishes the same mine without
  overwriting a partial or player-altered volume. It never materializes a
  replacement settlement.
- The first Create observation bridge is read-only and version-pinned to 6.0.10. It
  recognises two named, nearby `Track Station`s only when their chunks are
  already loaded. The same opaque native train UUID must arrive at both
  endpoints within a bounded simulation window to certify LOW/MEDIUM/HIGH
  capacity; two arbitrary trains, missing evidence, or unloaded endpoints
  never invent capacity or force-load chunks. The proof validates a persisted
  contract at full capacity for 8 steps, half capacity through step 24, then
  expires to zero unless another control run refreshes it.
- FTB Quests 2101.1.30 is an optional read-only presentation adapter. It
  installs one PM-owned static, no-reward chapter through FTB's public config
  and reload command, refuses to overwrite an unknown chapter collision, and
  opens it with FTB's public `open_book` command. PM never reads or writes FTB
  team progress.

The core GameTest suite validates the canonical regional plan, typed village
evidence and membership, same-vehicle route proof, and schema-v20 restart while
explicitly rejecting schema v19.
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
- intentional rejection of unsafe schema v5-v18 migration;
- GameTests and packaged-JAR verification;
- a firewall that prevents source items from silently entering the economy.

These measures make a long-lived world less likely to become corrupted after
many hours of play and restarts.

### 4. Story audience is separate from the world fact

An infected mine is global, and its physical controller is unique. A scenario
is addressed to one audience, but another player may physically intervene; the
world outcome remains one shared outcome. This model is appropriate for both
single-player and cooperative servers.

## Canonical direction: a settlement is an actor

Do not first add a third infection mod or a large global war. First prove that
several domain systems and an autonomous settlement policy causally affect one
another inside one living region.

The earlier shorthand `Settlement = society + place` is insufficient for
migration, ruins, and resettlement. The canonical model separates:

```text
SettlementCommunity  -> identity, population groups, memory, relations, policy
SettlementPlace      -> territory, occupancy, representation, integrity
PopulationGroup      -> residents, evacuees, refugees, migrants in transit
SettlementEconomy    -> stock, flow, reserve, production, consumption
SettlementSecurity   -> manpower, fortification, supplies, readiness
WorldSite            -> reusable physical site and typed capabilities
RouteContract        -> validated transport capability, health, freshness
SettlementPolicy     -> deterministic local decisions
```

There is no universal settlement lifecycle. Recognition, observation
freshness, structural integrity, operational state, occupancy, crisis, and
population disposition are orthogonal. A UI phrase such as "Ironhill is
recovering" is a derived view. The complete ownership and state model is in
[`settlement-actor-model.md`](settlement-actor-model.md).

### Reference scenario: Ironhill

Ironhill starts as a community occupying a place and depending on a validated
resource route:

```text
IronhillCommunity
    population = 80
    policy = frontier_mutual_aid

IronhillPlace
    occupancy = INHABITED
    structuralIntegrity = INTACT

IRON account
    stock = 120
    netFlow = +6/step
    reserveSteps = sufficient

SettlementSecurity
    defenceReadiness = 55

Mine: Mine17
iron production = 18/day
routeContract = Mine17 -> Ironhill
```

When Mine17 becomes infected:

```text
Mine17: OPERATIONAL -> INFECTED
production: 18 -> 0

after several simulation steps and settlement decisions:
Ironhill consumes reserves
Ironhill enables rationing
reserveSteps crosses policy thresholds
defence readiness decreases
SettlementCrisisDetected is emitted as an objective world fact
```

The settlement policy reacts before a scenario exists. The Narrator may expose
the resulting crisis to an audience, choose an archetype, delay it, or return
`NO_SCENARIO`; it never creates the crisis by offering a scenario.

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

#### C. Evacuate or abandon (later corrective slice)

The player does not save production but helps part of the population leave.

```text
Ironhill:
population decreases
defence decreases
status = DECLINING or ABANDONED

new world object: RefugeeCamp or MigrantGroup
```

The canonical form separates the people from their original location:

```text
IronhillCommunity -> DISPLACED
IronhillPlace -> RUINED or EMPTY
PopulationGroup(Ironhill refugees) -> hosted by RefugeeCamp12
```

The existing prototype evacuation proves persistence but does not freeze this
aggregate boundary. Evacuation and population groups follow after the first
economic actor loop. Failure must eventually generate a subsequent story, not
merely a terminal `FAILED` screen.

## Minimal economic actor model

Start with one macro-resource and distinguish flow deficit from actual
shortage:

```text
Mine produces IRON
RouteContract validates transport capability
PM simulates the macro-flow while the contract is healthy and fresh
SettlementEconomy records stock, netFlow, and reserveSteps
SettlementPolicy consumes reserve, rations, requests supply, then emits crisis
SettlementSecurity reacts to actual availability and policy decisions
```

`netFlow < 0` means reserves are being consumed. It does not mean there is an
immediate shortage. Availability becomes strained or unavailable only when
stock and reserve thresholds justify it.

Ordinary containers do not mirror canonical stock. A storehouse may provide
capacity, but item delivery requires an atomic physical-item removal followed
by a persisted `DeliveryReceipt`; withdrawal reserves canonical stock before
materializing items. This avoids duplication between abstract and physical
resources.

Later slices may add `FOOD`, `TIMBER`, `TOOLS`, `MEDICINE`, and
`MILITARY_SUPPLIES`. Do not simulate carrots, ingots, or individual tools one
by one before the one-resource actor loop is proven.

Positive development is also first-class. Sustained surplus can increase
reserves, prosperity, migration attraction, labour capacity, capabilities, and
eventually an expansion intent. `STABLE` is not the ceiling of settlement
development, even though physical district construction is deferred.

Economy and defence do not exhaust community identity. The canonical model
reserves `SettlementProfile`, culture/faction, priorities, relations, trust,
local policy, and relevant memory. Two communities facing the same deficit may
ration, request aid, close their borders, raise prices, migrate, or become
aggressive. This social differentiation follows after the one-resource policy
loop, but it belongs to the community aggregate rather than the Narrator.

## Integration order

### SettlementAdapter: observe before changing

Do not begin by taking over Millénaire construction. The first
`SettlementAdapter` is read-only and should:

- discover a settlement;
- assign a stable `WorldObjectId`;
- define bounds and anchor;
- publish membership-aware population estimates;
- distinguish registered role representatives from ambient NPCs;
- publish typed deaths, defender loss, landmark damage, and observation
  freshness;
- classify reliability as `CONFIRMED`, `STRONG`, or `TENTATIVE`, with
  `CURRENT`, `STALE`, or `EXPIRED` freshness tracked separately;
- never map a missing entity or one landmark directly to canonical ruin.

Begin with vanilla villages or Villager Overhaul, then add a Millénaire adapter.

Every adapter declares a field-ownership matrix using `PM_OWNED`,
`NATIVE_OWNED`, `DERIVED`, `OBSERVED_ONLY`, and `RECONCILED`. Vanilla macro
population may be PM-owned while physical villagers are observed. Millénaire
NPCs and local construction remain native-owned; its macro-population and
physical integrity require an audited reconciliation policy. PM must not place
a second local economy over a native simulation that already owns one.

Only after the mapping and authority matrix are trustworthy may an adapter
represent limited state. The concrete effect depends on field ownership:

```text
PM-owned representation -> sparse overlays or PM-owned representative actors
native-owned representation -> supported native request, never silent takeover
observed-only representation -> journal/ambient view; no physical mutation
```

PM does not delete traders or guards to make an abstract number visible. A
confirmed native death may change a reconciled capability; a PM-owned actor
may be removed by PM; a native-owned population changes only through its
adapter's supported ownership policy.

Native settlement development should be a later, audited opt-in. Off-screen PM
outcomes may be represented on the next safe chunk load only inside registered
world/mod-generated bounds, with versioned policies, provenance masks, and
conflict-safe sparse overlays. This can close a registered site, add limited
ruin evidence, or represent refugees; it cannot overwrite player-owned blocks.
Do not start by rewriting settlement AI or building logic.

### CreateAdapter: the next high-value adapter

Create should arrive before more threat providers. It makes the systems model
into a distinctive game rather than an event generator.

PM must not try to infer every player factory. Physical infrastructure is an
independent `WorldSite` with affiliations and one or more typed capabilities:

```text
WorldSite
SiteAffiliation
SiteCapability(STORAGE / LOGISTICS / SHELTER / DEFENCE)
RouteContract
```

One station may serve several settlements, and one site may provide storage
and logistics simultaneously. A bell is only a physical anchor capability;
the community's `WorldObjectId` owns identity.

The player links a real Create installation to endpoints. The adapter checks
only bounded, meaningful capabilities:

- mechanical energy is present;
- a registered container is connected;
- a transport line is connected;
- the registered installation is operational;
- capacity is LOW, MEDIUM, or HIGH.

It publishes generic validation evidence such as:

```text
RouteControlRunSucceeded
ProductionFacilityOperational
SupplyCapacityObserved
```

One successful control run validates that the route can carry supported cargo.
PM then simulates macro-delivery abstractly while the `RouteContract` remains
healthy and fresh. Station damage, a removed train, an invalid line, or stale
validation degrades/closes the contract. Constant physical travel through
unloaded chunks is not required.

Pale Mirror does not need to know which gears were used. Aeronautics can later
be another provider of the same transport contract:

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

FTB Quests is a read-only presentation layer. It never owns canonical state;
the current 0.2c projection presents:

```text
Source: Mine17 stopped supplying iron
Consequence: Ironhill is losing defensive readiness
Responses: clear the mine; create alternative supply
```

Evacuation is a future projection only after canonical population groups and
host-place bindings exist; it is not offered by schema v20.

Add operator-facing explainability as a first-class tool:

```text
/pale_mirror explain settlement:ironhill
/pale_mirror timeline mine:17
/pale_mirror narrator explain <scenarioId>
```

For example, Narrator diagnostics should show severity, settlement relevance,
audience reachability, available capabilities, archetype novelty, and cooldown
status. Settlement diagnostics must separately show objective crisis facts,
policy decisions, route validation freshness, observation reliability, and
causal attribution. A player dismantling a bound storehouse is not an unknown
"mysterious disaster": evidence should distinguish threat, raid, player,
environmental, and unknown damage where the observer can prove it. Without
this, narrative balancing and world causality become opaque.

## Narrator v2 direction

The current single archetype was the correct first step. Settlement simulation
and `SettlementDecisionEngine` produce objective facts and autonomous
decisions first. The next Narrator scores candidates for tension and
opportunity derived from those facts, rather than reacting directly to isolated
events or moving a settlement into crisis:

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

Settlements continue acting when no scenario is selected. They can ration,
seek trade, prepare defence, begin evacuation, invest in recovery, or exploit a
surplus according to deterministic policy. Narrator owns neither these
decisions nor their outcomes.

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

**Pale Mirror 0.2: First Living Settlement** is complete when, without
operator commands:

1. a player discovers one registered mine, community, and occupied place;
2. one validated `RouteContract` supplies `IRON` to one canonical resource
   account;
3. mine infection produces a negative flow, reserve depletion, rationing, and
   an objective crisis fact through deterministic settlement policy;
4. that settlement continues making decisions even if Narrator returns
   `NO_SCENARIO`;
5. the player can understand stock, net flow, reserve, route freshness, policy
   decisions, and consequences through a read-only journal;
6. the player can resolve the first crisis either by clearing the mine or by
   validating an alternate supply route;
7. the world preserves one durable result: restored supply or persistently
   weakened defence;
8. the same state and causal history survive restart without divergence.

The release gate proves a settlement actor, not the entire future settlement
model. Evacuation is absent from schema v20; canonical evacuation,
population groups, physical ruins, positive expansion, Millénaire ownership,
and culture/reputation are follow-up vertical slices.

Minimal stack:

```text
Crimson Curse: primary threat source
Spore: optional/test provider

Mine + Settlement + Route + ResourceFlow

Investigation/Recovery + autonomous Supply disruption + alternate logistics

FTB Quests: read-only presentation
CreateAdapter: one functional logistics contract
```

### Remaining model debts before release

The 0.2c schema-v20 synchronization closes the state-centric aggregate,
autonomous-policy, typed-evidence, and RouteContract debts. Remaining bounded
debts are explicit:

- ordinary physical inventories still need delivery/withdrawal receipts;
- typed evidence deliberately does not yet infer `DAMAGED`, `EMPTY`, or
  `RUINED`, so destroyed-place reconciliation is a later slice;
- positive development, canonical population groups, Millénaire field
  ownership, culture and reputation remain deferred.

Observation freshness, structural integrity, operational state, occupancy,
crisis, and population disposition remain separate. Missing chunks, an absent
landmark, or an isolated death are never sufficient to infer ruin.

## Practical order of work

### 0.2a: close operational evidence gaps

1. Run the final packaged JAR on a dedicated server.
2. Perform process-level restart and crash tests.
3. Run graphical client smoke tests for Crimson and Spore.
4. Verify animations, sounds, particles, AcidBall behavior, and cleanup.

### 0.2b: build the first living-region prototype

The prototype proved the physical campaign, read-only village discovery,
Create same-vehicle evidence and FTB presentation.

### 0.2c: synchronize the settlement actor model

1. Replace the prototype's overloaded settlement state with the minimal
   community/place/economy/security ownership needed by this slice.
2. Add one `IRON` resource account with stock, net flow, reserve steps, and
   availability.
3. Add one independent `WorldSite` logistics endpoint and one freshness-bounded
   `RouteContract`.
4. Add a deterministic settlement policy: consume reserve, ration, request
   supply, then emit an objective crisis fact.
5. Upgrade the read-only observer with membership, evidence type, loaded
   duration, freshness, reliability class, and causation.
6. Keep only two release-critical responses: clear Mine17 or validate the
   alternate route.
7. Upgrade the read-only journal to explain facts, policy decisions, contract
   health, and consequences.
8. Preserve restart safety and prove that a Narrator `NO_SCENARIO` does not
   pause settlement behavior.

These items are implemented in schema v20 and covered by domain, GameTest and
restart gates. Next implement evacuation/population groups, physical ruin
representation, positive development, Millénaire ownership reconciliation,
and social identity as separate slices before broad natural discovery/worldgen.

## Product conclusion

### Implemented 0.2d–0.2e boundary

Schema v21 adds the PM-owned Supply Depot and restart-reconcilable physical
IRON receipts; schema v22 adds PopulationGroup, grace-bound evacuation,
displacement, representative camps and PM-owned ruin overlays. These are
settlement-policy consequences, not Narrator state. Arbitrary village blocks
and ordinary container inventories remain outside PM ownership.

Schema v23 adds the complementary positive loop: sustained surplus produces a
real PM-owned storehouse upgrade, prosperity, housing and bounded population
growth. Recovery can return a displaced community to a sufficiently observed,
non-ruined home without reconstructing native or player-owned buildings.

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
