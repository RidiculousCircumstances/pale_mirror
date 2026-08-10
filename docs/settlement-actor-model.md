# Pale Mirror: canonical Settlement Actor Model

## Status and intent

This document is the canonical product model for settlements. It corrects the
earlier state-centric description in `product-vision-0.2-first-living-region.md`.
Schema v20 implements the first bounded actor slice described here. It replaces
the v19 `SettlementState`, `SettlementStatus`, `RouteState`, and
`MigrantGroupState` prototype rather than migrating ambiguous identity.

The product promise is that settlements live, make bounded decisions, grow,
decline, migrate, and create causal history without waiting for a player or the
Narrator. The Narrator selects which already-existing developments become
player-facing stories; it never creates settlement reality by selecting a
scenario.

## Society is not a place

The public term `Settlement` may remain, but its canonical model separates
three identities:

```text
SettlementCommunity
    people, shared identity, memory, relations, priorities, policy

SettlementPlace
    territory, occupancy, physical representation, structural integrity

PopulationGroup
    residents, evacuees, refugees, migrants, settlers in transit
```

A community can survive the loss of its original place. A place can remain as
a ruin after its population leaves. A camp can host groups from several
communities without immediately becoming a new settlement.

Example:

```text
IronhillCommunity
    identity = ironhill
    population = derived from hosted and displaced groups

IronhillPlace
    structuralIntegrity = RUINED
    occupancy = EMPTY

IronhillRefugees
    kind = PopulationGroup
    population = 64
    disposition = DISPLACED

RefugeeCamp12
    hosts = [IronhillRefugees]
```

Stable object IDs, not a bell or another physical landmark, preserve these
identities. Destroying a landmark changes observed capabilities and physical
evidence; it never erases the community's identity.

## No universal settlement lifecycle

`OBSERVED -> INHABITED -> PRESSURED -> CRISIS -> RUINED` is not a valid state
machine. Those labels describe different dimensions that may coexist. The
canonical model uses orthogonal state families:

```text
RecognitionState
    DISCOVERED / RECOGNIZED / RETIRED

ObservationState
    UNKNOWN / STALE / CURRENT

StructuralIntegrity
    INTACT / DAMAGED / RUINED

OperationalState
    OPERATIONAL / DEGRADED / OFFLINE

OccupancyState
    INHABITED / EVACUATING / EMPTY

CrisisState
    NONE / PRESSURED / CRITICAL / RECOVERING

PopulationGroupDisposition
    RESIDENT / IN_TRANSIT / DISPLACED / RESETTLED
```

UI labels such as "Ironhill is recovering from a crisis" are derived views,
not authoritative state. A place may simultaneously be inhabited, damaged,
operationally degraded, under shortage pressure, and recovering.

## Economy models balance, reserve, and availability

`DEFICIT` and `SHORTAGE` are not sequential status levels. A deficit is a
negative flow balance; a shortage is insufficient available stock. A
settlement can run a deficit for a long time while reserves remain adequate.

Each macro-resource records at least:

```text
ResourceAccount
    stock
    capacity
    production
    incomingFlow
    consumption
    netFlow
    reserveSteps
    availability
```

Example:

```text
IRON
    netFlow = -8/step
    stock = 120
    reserveSteps = 15
    availability = STRAINED
```

The first slices continue to use coarse resources such as `IRON`, `FOOD`, and
`MEDICINE`; they do not simulate individual ingots or carrots.

### Abstract stock and physical items

Ordinary inventories are never mirrors of canonical stock. A storehouse may
provide storage capacity while its normal item contents remain player-owned
Minecraft state.

Physical-to-abstract delivery is an explicit transaction:

```text
validate endpoint and items
-> atomically remove physical items
-> persist DeliveryReceipt
-> increase canonical stock
```

Abstract-to-physical withdrawal uses the inverse ordering:

```text
reserve and decrease canonical stock
-> persist withdrawal lease
-> materialize physical items
-> record terminal receipt
```

This boundary prevents one resource from existing simultaneously as reusable
physical items and unchanged abstract stock.

## Settlements have autonomous policy

A settlement is an actor, not merely a resource meter. A deterministic
`SettlementDecisionEngine` evaluates local policy from canonical facts and
emits normal domain commands/events.

The first policy can be deliberately small:

```text
reserveSteps < 10
-> enable rationing

reserveSteps < 5 and a viable alternate contract exists
-> request alternate supply

defenceReadiness < 20 and threatPressure is critical
-> prepare evacuation

stable surplus and free housing capacity
-> accumulate development pressure
```

Policy can use community priorities without making every settlement a unique
AI agent. Later profiles may prefer trade, isolation, mutual aid, militarized
response, migration, or opportunism.

The decision engine owns settlement decisions. The simulation owns facts. The
Narrator owns presentation and pacing only.

## Crisis is a world fact

A crisis exists independently of any scenario:

```text
low reserve
+ falling defence
+ active threat pressure
-> SettlementCrisisDetected
```

The Narrator may expose it to an audience, select an archetype, delay it, or
return `NO_SCENARIO`. The settlement may also resolve the problem without the
player through rationing, another community's trade offer, threat retreat, or
a policy decision. Scenario state never becomes the source of settlement
state.

## Positive development is first-class

`STABLE` is not the top of the model. Sustained surplus and security can
produce growth:

```text
surplus
-> reserves
-> prosperity
-> migration attraction
-> labour and population growth
-> new capabilities
-> expansion intent
```

The model leaves explicit room for:

```text
housingCapacity
labourCapacity
prosperity
developmentPressure
expansionIntent
plannedSites
developmentMemory
```

Physical district construction is deferred. Domain growth and capability
unlocks can exist before a trusted adapter can represent new buildings.

## WorldSite is reusable infrastructure

Sites are independent world objects, not settlement-owned template slots:

```text
WorldSite
    id
    type
    dimension
    bounds
    provenance
    observation state
```

Relationships are separate records:

```text
SiteAffiliation
SiteCapability
SettlementSiteBinding
```

A site may serve multiple settlements or regional objects, and it may provide
multiple typed capabilities:

```text
STORAGE(capacity)
LOGISTICS(capacity)
SHELTER(capacity)
DEFENCE(capacity)
```

Examples include a station shared by two towns, a regional fort, an airfield,
a multi-community refugee camp, or a storehouse that also acts as a logistics
endpoint. `CivicAnchor` is a physical anchor capability, not identity.

## Routes are validated contracts

A single train pass does not create permanent canonical supply. The domain
uses a `RouteContract`:

```text
RouteContract
    originEndpoint
    destinationEndpoint
    provider
    supportedResources
    nominalCapacity
    observedHealth
    lastSuccessfulValidation
    freshness
    status
```

A successful physical control run validates that the route can operate. PM
then simulates macro-flow abstractly without requiring trains to run through
unloaded chunks for every delivery.

```text
successful control run
-> RouteContract VALIDATED
-> abstract supply flow

station damaged, contract stale, train removed, or line invalidated
-> typed observation
-> capacity degrades or contract closes
```

The existing same-native-vehicle proof is useful evidence for validation, but
it must evolve from direct momentary capacity into contract health and
freshness.

## Observations carry evidence semantics

Observations do not use an unexplained floating-point confidence value. They
record what was known when the observation was made:

```text
observedAt
loadedDuration
evidenceType
freshness
sourceAdapter
reliabilityClass
causation
```

Initial reliability classes are explicit:

```text
CONFIRMED
STRONG
TENTATIVE
```

Freshness is separate:

```text
CURRENT
STALE
EXPIRED
```

Evidence families are different:

- confirmed facts: registered controller destroyed, registered guard killed,
  bound endpoint consumed a delivery, registered landmark destroyed;
- estimates: visible resident count, damage extent, bed sufficiency, local
  activity;
- weak negative evidence: resident not found, guard absent, endpoint silent.

Negative evidence is invalid when chunks are not loaded long enough, the
observation window is incomplete, or data is stale. Canonical ruin or
abandonment requires an evidence-windowed typed observation, never one missing
entity or landmark.

## Population uses cohorts and physical representatives

Macro-population is organized into coarse cohorts:

```text
CIVILIANS
WORKERS
SPECIALISTS
GUARDS
CHILDREN
```

A physical NPC may be a registered representative of a cohort, a carrier of a
functional role, or an unregistered ambient entity. Membership rules prevent
any villager moved into a radius from silently becoming part of a settlement.

A random resident death contributes casualty evidence. The confirmed death of
a registered guard may immediately reduce an observed defence capability even
when macro-population changes only after reconciliation.

## Damage retains cause and actor

Physical damage is attributed where evidence permits:

```text
ThreatDamageObserved
RaidDamageObserved
PlayerDamageObserved
EnvironmentalDamageObserved
UnknownDamageObserved
```

The observation retains responsible player/source identity, causation ID, and
affected world objects. This supports correct narration now and reputation or
justice systems later. Unknown causation remains explicit rather than being
invented.

## Adapter ownership is field-specific

Each settlement adapter declares how canonical fields are owned:

```text
PM_OWNED
NATIVE_OWNED
DERIVED
OBSERVED_ONLY
RECONCILED
```

Initial matrix:

| Field | Vanilla/Integrated Villages | Millénaire direction |
| --- | --- | --- |
| Macro-population | `PM_OWNED` | `RECONCILED` |
| Physical NPCs | `OBSERVED_ONLY` | `NATIVE_OWNED` |
| Regional resource flow | `PM_OWNED` | `PM_OWNED` |
| Local construction | `OBSERVED_ONLY` | `NATIVE_OWNED` |
| Physical integrity | `RECONCILED` | `RECONCILED` |
| Community memory/policy | `PM_OWNED` | `PM_OWNED` unless audited otherwise |

The concrete matrix is version-pinned per adapter capability audit. PM does
not declare a second local population, building system, or stock ledger over a
native simulation that already owns them.

## Off-screen outcomes need safe representation

The abstract simulation may conclude that a registered place is damaged or
ruined while its chunks are unloaded. On the next safe load, a versioned
materialization policy may represent that outcome only inside registered
world/mod-generated bounds and only on provenance-safe cells or PM-owned
actors.

Possible representative effects include closing bound sites, applying sparse
ruin overlays, removing PM-owned defenders, adding battle debris, or creating
a small representative refugee group. Unknown and player-owned changes remain
conflicts. The physical result is a projection of canonical history, not the
event that retroactively caused it.

## Community identity and social behavior

Economy and defence are the first mechanics, not the definition of society.
The model reserves room for:

```text
SettlementProfile
FactionOrCulture
Priorities
Relations
TrustAndReputation
LocalPolicies
RelevantMemory
```

Two settlements facing the same deficit may request aid, ration, close their
borders, raise prices, raid a neighbour, or evacuate according to policy and
memory. This social layer is deferred until the economic actor loop is proven,
but its ownership belongs to the community aggregate rather than the Narrator.

## First Living Settlement slice

The next corrective vertical slice proves actor behavior without attempting
the full future model:

1. One observed vanilla/Integrated Villages settlement.
2. One mine and one macro-resource: `IRON`.
3. One `ResourceAccount` with stock, net flow, and reserve steps.
4. One `WorldSite` acting as a logistics endpoint.
5. One `RouteContract` with validation, health, and freshness.
6. One deterministic settlement policy: consume reserve, ration, request
   supply, then emit an objective crisis fact.
7. Two player responses: clear the mine or validate alternate supply.
8. One visible, durable result: restored route or persistently weakened
   defence.
9. A read-only journal explaining the causal chain.

Evacuation and `PopulationGroup`, physical ruins, positive expansion,
Millénaire field reconciliation, and culture/reputation follow as separate
vertical slices. Prototype evacuation was removed from schema v20 so it cannot
freeze the final aggregate boundary.

## Schema-v20 synchronization

The completed breaking slice replaces:

- `SettlementState` with community/place/economy/security ownership;
- `SettlementStatus` with orthogonal state families and derived UI status;
- `MigrantGroupState` with no canonical evacuation state until population
  groups and host bindings receive their own vertical slice;
- `RouteState` with route contracts and validation freshness;
- raw death counters with typed evidence and a reconciliation window;
- direct observed route capacity with contract validation followed by
  abstract flow;
- the scenario-triggered crisis path with a settlement policy and objective
  crisis event.

Schema v19 is intentionally rejected with a new-world diagnostic. Physical
ruins, population groups, positive development, Millénaire reconciliation and
social identity remain follow-up slices rather than compatibility shims.

## Physical economy boundary (0.2d)

Schema v21 introduces the first item-to-macro-resource bridge. A versioned
PM-owned Supply Depot is placed in a deterministic loaded safe footprint and
records baseline and last-applied state for every mutable cell. Its barrel is
an interaction endpoint only: its inventory is never canonical stock.

Deposits and withdrawals use a bounded persisted `ResourceTransferLedger`.
Each operation pins the mapping version, player, audience-owned site, amount
and domain resource, then advances through physical reservation and a domain
command before completion. Reserved stacks carry the transfer identity and
cannot be used while reconciliation is active. Withdrawal is limited to 16
IRON per player per simulation step and must leave two consumption steps in
reserve. Unknown or changed depot cells block materialization rather than
overwriting the village or player construction.
