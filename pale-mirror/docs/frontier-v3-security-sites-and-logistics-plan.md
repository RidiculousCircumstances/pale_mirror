# Settlement protection, outlying sites and batched logistics

Accepted implementation scope, 2026-11-01. Main implements alone. No subagents,
automatic push or API goal. MAIN remains stopped; verify and deploy to the
explicit private disposable runtime. Fresh schemas/worlds are allowed.

## Player-visible result

Settlements operate nearby and distant agricultural/mining/forestry sites, choose
available sites through explicit rights, collect output in ordinary local storage
and transport useful batches. Security planning allocates guards according to
actual protection obligations and the settlement's workforce. Armed guards watch
and patrol, share witnessed threats, pursue and attack within explicit bounds,
then resume their mission. An attacking player's UUID remains hostile after
departure/reconnect/restart. Guard and ordinary pedestrian walking share a slower
physical/time profile. No combat against fabricated COLD enemies is introduced.

## Authoritative boundaries

| Owner | Authoritative facts / decisions | Reused mechanisms |
| --- | --- | --- |
| World-site geography | Stable natural-site IDs, kinds, bounds, terrain/material opportunities; no town ownership | Authored terrain, geology, forest and field geometry |
| Site use | Explicit exclusive operating right, holder, generation and release; knowledge is separate | Existing operator/site relationships, typed domain references |
| Settlement economy / workforce | Whether a site is useful and affordable in labour/logistics; approved security staffing | Existing economic publications, demand and staffing arbitration |
| Storage / inventory | Actual items, lots, title, custody, capacity and player interventions | Dynamic warehouses, ground sources, exact item / fungible ledger |
| Internal logistics | Retained batch plan/manifest, source/destination choice, dispatch timing and transport capacity | Shipment, fleet, UAE, common service access and navigation |
| Security planning | Protected objects, priorities, posts/coverage visits and required/approved guard places | Site use, live facilities, shipment/group declarations |
| Security mission | Retained assignment and patrol/watch/respond/return continuation | Guard appointment, equipment service, shared actor execution |
| Perception / relations | Bounded witnessed contacts, last observation; confirmed incidents and persistent player hostility | Spatial indexing, native observations, stable actor/player identities |
| Combat | Exact encounter/engagement, target, attack readiness and outcome references | Weapon capabilities, health/item owners, native effect/recovery protocol |
| UAE / navigation | Exactly one current body/actuation owner, movement and safe handoff | Existing closed capability/provider registries; no second actuator |

No generic coordinator inspects a guard, recipe, field or mining phase. New family
policy enters typed closed capabilities. No inferred owner/type from IDs, geometry
or collection membership. Site rights never duplicate item ownership. A claim is
not block protection: ordinary player edits keep their existing causal handling.

## Source findings driving the implementation

1. `GuardEquipment.target` and staffing read the same fixed labour target2; guard
   appointment already has equipment, meal and body-lifecycle integration.
2. `FrontierResourceSitePlan` creates one initial field beside each FARM building.
   Quarry and forest content is generated relative to town placement. Natural
   `MineralDeposit` already has no settlement/storage owner; `ExtractionSite` has
   an explicit operator. Preserve that distinction.
3. `ResourceHaulingPolicy.offer` dispatches any positive stock up to a literal64,
   using first source/kind traversal, without batch accumulation/deadline policy.
   `ShipmentCargo` currently admits one fungible kind or one individual item.
4. `ResidentActivityChoice` is WORK/EAT/IDLE. GUARD is a registered UAE family,
   but response to immediate danger needs explicit common activity arbitration.
5. Legacy `FrontierCombatRules` uses category damage; the scene-strike adapter
   applies fixed damage. `RoutePatrolProcess` inspects obstructed roads. Neither
   is the new guard combat/patrol implementation and neither is enabled by this cut.
6. Shared `TravelPace` exists, while some native moves still use fallback speed
   and some COLD owners use per-edge cadence. One speed change must cover both.

## Delivery sequence

### 1. Site geography and operating rights

- Introduce bounded typed site geography and an independently owned canonical
  site-use registry: available/claimed/releasing, exact holder and generation.
- Nature is declared independently; an operating field/development retains an
  explicit validated use relationship. No second mutable resource-site catalogue.
- Initial authored grants are explicit; later selection evaluates currently free,
  known, reachable suitable sites and atomically acquires the exact right. No
  scan-order ownership inference, disputed conquest or free stock generation.
- Site retirement waits for actual work/cargo obligations; clearing a right does
  not erase geology, crops, stored items or prepared physical effects.
- Persist new facts under a fresh schema and register their references/owners.

### 2. Outlying graybox production

- Separate residential fabric from agricultural plots. Use mixed nearby/distant
  sites and multiple available candidates; retain meaningful environmental
  specialization. Seeded placement must be reproducible and terrain-compatible.
- Author compact, dispersed agricultural and mining/forest examples, including
  slopes and constrained approaches. Distances are content, never route answers.
- Install initial capital explicitly; subsequent soil preparation/sowing/mining
  uses current workers/effects. No worldgen overwrite of later player changes.
- Local buffers are ordinary dynamically selected stores. Fields and mines keep
  no mandatory original-chest relationship. Existing hauling serves both.
- Map/diagnostics show natural sites, use holder, active work and storage contacts.

### 3. Batch dispatch policy through existing logistics

- Add one common bounded dispatch decision over available stock, real carrier
  capacity, distance/ETA, receiver need, existing manifests and source pressure.
- Prefer useful loads; retain a dated wake/deadline for accumulation rather than
  create a new trip after each resource observation. No full-market recalculation
  from every item/position update and no route search in cheap eligibility checks.
- Send partial loads when waiting would miss a real supply deadline, local capacity
  is threatened or a finite inactive/exhausted source needs residual collection.
  No unconditional64 minimum and no universal 'urgent' bypass for ordinary output.
- Workers deposit locally and keep producing while capacity permits. Haulers and
  producers have independent executions; neither waits for the other's full trip.
- Reserve only actual cargo and receiving capacity through their current owners;
  source withdrawal, player edits and destroyed/full destinations replan normally.

### 4. Common cargo manifest and capacity

- Extend the existing shipment boundary to a bounded multi-line manifest where
  carrier capacity permits. Preserve exact lot/item/owner/custody allocation;
  never emulate one trip with competing independently owned actor shipments.
- A pedestrian's hand remains one physical stack unless its registered native
  inventory provider explicitly supports more. Reuse fleet/mobile containers for
  larger loads; do not invent invisible capacity or a second resource ledger.
- Support combined coal/raw iron/stone collection without waiting for every kind
  to fill a stack. Source, transport, pickup/drop receipts and recovery account
  for each manifest line exactly once. Selection of transport is policy.
- Internal hauling is not a sale; trade matching/money remain with trade owners.

### 5. Weapon profiles, perception and incident memory

- Separate reusable combat properties from work-tool properties: damage, attack
  interval, reach and wear rules. Concrete item condition remains inventory-owned.
- Minecraft supplies actual physical weapon/attack facts through the integration
  boundary. Domain profiles are explicit immutable versioned inputs, not guessed
  sword-name/category damage. Unsupported effects are explicit, not silently lost.
- Native perception uses bounded nearby candidates, actual range and line of
  sight; confirmed harmful events retain causal attacker/victim identities.
- Share last-confirmed contacts within the settlement; no invisible-target live
  coordinate tracking. Persistent hostility uses playerUUID, not display name.
- Death/disconnect/unload ends the physical opportunity; it does not erase the
  settlement's incident memory. No attack is inferred from a cancelled/zero effect.

### 6. Security planning and dynamic staffing

- Derive protected obligations from active settlement facilities, claimed worksites
  and relevant routes/groups. Geometry provides feasible observation positions;
  security chooses importance/coverage. Never stand in service throats/buffers.
- Required, workforce-approved and armed-ready counts are separate. No fixed-two
  floor; approved places create finite equipment demand before weapons exist.
- Coverage workload includes simultaneous posts, travel, observation dwell and
  external obligations. Nearby objects can share a patrol; distance alone is not
  proof of danger. Staffing changes use explicit stable policy, not per-tick churn.
- Shared workforce protects essential food/material labour and admits exact people.
  Reduced staffing ends missions safely and returns actual weapons normally.

### 7. Patrol, response, melee and COLD continuity

- Mission targets are typed settlement area/static site/moving group. Initial
  behavior is settlement protection; the common target contract supports later
  mine/field protection and transport escort without another navigation/combat AI.
- Patrol visits cover important areas with watch pauses and staggered guards.
  Threat response interrupts a safe checkpoint through UAE; no parallel activity
  may steer the same actor. Food policy and imminent danger have explicit priorities.
- Maintain separate observation radius/contact expiry and protected-area chase
  envelope. Loss of contact permits only bounded last-known-position checking.
  Exceeding pursuit scope, target death or departure returns to the retained mission.
- Shared combat uses distinct reachable melee positions, actual reach/visibility,
  attack intervals and confirmed physical outcomes. Weapon wear follows real use.
- HOT executes native effects and accepts observed health/item consequences;
  COLD owns scheduled encounter boundaries only after physical custody is released.
  The architecture supports modeled COLD opponents, but this cut creates none and
  never simulates disconnected players or unloaded unmanaged mobs as phantom targets.
- Reuse one native mutation/recovery protocol for hit/health/item consequences;
  prepared or possibly-applied attacks cannot replay on restart or be abandoned
  during role/mode changes. No new legacy assault/patrol activation.

### 8. Walking pace, diagnostics and release

- Common resident walking pace drives both native navigation and COLD travel/ETA;
  guards use the same pace. Patrol dwell is separate. Remove changed-family speed
  bypasses instead of tuning only one native actuator.
- Diagnostics explain site right/operator, batch readiness/deadline/capacity,
  guard demand/approved/ready, mission, observed target/contact age, pursuit scope
  and exact wait/return reason. Evidence must not become gameplay authority.
- Native scenarios cover attack on resident, shared response, sword hit, bounded
  pursuit/return, repeat offender after reconnect/restart and HOT/COLD patrol;
  outlying production/storage intervention and mixed/partial batch delivery.
- Pure tests cover policy, ownership, unsupported/stale declarations and discrete
  COLD boundaries. Use real queue integration, not only manual reducer calls.
  No wall-time performance assertions or tests mirroring retired internals.
- Measure bounded observation/dispatch/navigation work on current private scenes;
  retain honest evidence scopes. Commit verified source, clean detached packaged
  build, pinned private install and fresh health. MAIN remains stopped.

## Completion criteria

- No active fixed-two guard demand, original-depot route or per-resource hauling
  policy remains in the changed production path.
- A remote site can keep producing into available local storage while useful
  transport batches collect; finite residuals and real shortages cannot deadlock.
- Claimed geography/operator and item title/custody have distinct single owners.
- Guard patrol→alarm→attack→bounded disengagement→mission resumption works in the
  actual composition; meals, lost equipment, death, edits and restart are accounted.
- No second body, route, damage, cargo or scheduling authority is introduced.
- Cold combat scaffolding does not fabricate enemies or claim background battles.
- Applicable source/negative/recovery and native evidence passes; player-visible
  and continuous-operation claims retain M0/M1/M2/M3 distinctions. Completion is
  implementation+verified private delivery, not merely this plan's existence.

Implementation status: schema315 source implemented, integration/native acceptance in progress.
The initial site policy keeps existing operating declarations bound for their lifetime;
unbound reservations may be released, while a bound right cannot disappear under
work or stored effects. Automatic operating-site decommissioning, contested claims
and diplomatic transfer are outside this first policy. Actual autonomous free-area
acquisition is wired to surveyed cultivation expansion. Mines and forest operations
start with explicit authored allocations, independent of their natural geography.
Existing release/retirement owners must precede any future operating-site release.

Modeled connected evidence currently covers actual surveyed claim/extension,
patrol queue travel/watch/successive posts, response post retention/contact expiry,
prepared combat/wear/persistence fences, and mixed internal transport through actual
mission/group/guide/animal release without a sale. Native acceptance is pending;
see the canonical continuity ledger for current exact identity and receipts.


## Verified implementation boundaries

The source now carries exact operating-right bindings through admission, continuations,
root validation and snapshots. Autonomous free-area acquisition is exercised by actual
cultivation admission. Existing operating rights remain bound for the operation lifetime;
automatic decommissioning and autonomous acquisition of new mines/forests are deferred.

Guard demand is coverage-derived, approved places are workforce-owned, and equipped
readiness is separate. Security cadence, observation/chase radii and contact lifetime
are hashed ruleset inputs. Patrols and temporary responses retain their own exact post
bookmark and use the existing movement owner. Response placement admits one cardinal
neighbour with at most one support-height step; moving outside that contact envelope
requires another approach rather than waiting outside sword reach.

Real engine-queue checks cover successive patrol/watch visits, exact response bookmarks,
free-area acquisition, and mixed internal cargo through delivery/return/retirement with
no money change. Shared storage discovery now separates candidate approaches from the
single retained interaction station; actual carrier dimensions select that station.

First native disposable client carrier completed ordinary manufacture/issue, player
assault, real sword retaliation (health20 to14), departure, graceful restart, remembered
hostility, repeated retaliation without a second assault, and disengagement. Receipt:
`build/security-native/native-security-policy.json`. The frame was inspected. This is
focused native evidence for one armed guard and persistent player relations, not full
cohort/scale/visual acceptance. A final native run after the adjacent-contact correction
and a clean private packaged deployment remain required.

The full native milestone exposed two unrelated legacy assault/route-patrol failures;
both recur on clean baseline de34b065f. They remain explicit existing gate debt, not a
reason to enable or redesign those inactive families. A separate obsolete diagnostic
assertion expected the removed composite scenes executor; it now checks actual SCENE
stages after observation. All49 execution rows fit the unchanged8KiB response budget.
