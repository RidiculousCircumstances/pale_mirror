# Frontier v3 human capability and unit model

Status: accepted normative Frontier v3 design.

This document defines how exact human residents work, specialize, organize and
fight. It prevents a resident's identity, profession, current task and visible
combat presentation from collapsing into one permanent class.

## Player promise

Every visible resident is one persistent person whose useful abilities,
livelihood, current duty, equipment and relationships survive HOT/COLD
transitions. When a settlement mobilizes workers, the player can see both the
defence it gained and the production, transport or medical capacity it gave up.

## Core rule

One resident is one canonical person. A human unit is an organization of exact
resident IDs, never a cohort multiplier or a substitute population counter.

`profession`, `assignment`, `equipment` and `unit membership` answer different
questions and must remain distinct:

- **profile and condition:** who the person is and whether they can act;
- **skills:** what the person has learned;
- **profession and employment:** what work normally supports them and under
  which exact agreement;
- **assignment:** what bounded task they are doing now;
- **equipment:** what physical capabilities they currently possess;
- **organization:** which household, company, work crew or tactical unit
  currently coordinates them.

No enum called `role`, Minecraft entity subtype, uniform colour or nameplate may
silently own all six meanings. The current `ResidentRole` is only a provisional
bootstrap affinity and must not become the permanent assignment or combat
model.

## Resident profile and condition

A resident retains a stable ID, household, home settlement, birth instant and
bounded learned skills. Health, disease, nutrition, wounds, fatigue and stress
are mutable condition, not professions. Death remains an exact terminal fact.

Condition constrains admission and effectiveness. It must not silently erase a
resident from an already acknowledged physical effect or committed work:

- an unavailable person cannot begin new work;
- an interruptible task follows its typed interruption and resource-release
  path;
- a durable running physical effect settles by observation before ownership is
  released;
- absence of a Minecraft body never proves injury or death.

Exact fatigue and stress formulae are persisted ruleset calibration, not Java
type structure.

## Skills

Skills are bounded, persistent and trainable capabilities. A person may have
several useful skills; training and experience can change their values without
changing identity.

The initial capability families are:

1. **Agriculture** — cultivation, livestock and food handling.
2. **Extraction** — mining, quarrying and raw-resource handling.
3. **Industry** — machine operation, processing and fabrication.
4. **Engineering** — construction, repair, fortification and demolition.
5. **Logistics** — warehousing, dispatch, loading and rail operation.
6. **Medicine** — treatment, triage, quarantine and evacuation care.
7. **Security** — weapon handling, patrol, fieldcraft and tactical discipline.
8. **Civic** — administration, negotiation, planning and command.

Narrow specialties may refine these families later, but must not create a new
identity layer or bypass training, equipment and assignment. Skill gain is
bounded and caused by training or completed work; a materialized costume never
grants canonical proficiency.

## Profession and employment

Profession is a durable vocational affinity. Employment is the exact legal and
economic relationship that reserves work, invoices an owner and pays the named
resident. Neither one states the person's current physical action.

The initial profession families mirror the capability families:

- agricultural worker;
- extractor;
- industrial worker;
- builder/engineer;
- logistician or rail worker;
- medical worker;
- security worker;
- civic worker.

A resident may retrain, change employer or become unemployed. Mobilization does
not rewrite their profession: a mobilized machinist remains a machinist whose
current assignment is military. Existing contracts and committed jobs must be
settled, suspended or terminated through explicit events; mobilization cannot
silently confiscate labour or delete wages and inputs.

## Assignments

An assignment is one current, bounded responsibility owned by a durable task or
operation. Examples include farming, mining, operating a machine, construction,
repair, loading cargo, driving a train, treating a patient, patrolling,
scouting, escorting, defending, assaulting, evacuating and operating a fixed
weapon.

Current assignment is a pure exact projection of its owning canonical job,
operation, patrol or journey until a future unit process needs additional
durable state. It is therefore not a second mutable ledger: one source may
claim one resident once, conflicting claims fail closed, and recovery derives
the same assignment from persisted owner records.

Admission names the exact resident, required capabilities, equipment,
organization, location and conflicting claims. One resident cannot perform two
simultaneous assignments. A task completion, interruption, casualty, equipment
loss or doctrine change emits an explicit transition before reassignment.

## Equipment and derived tactical function

Combat capability comes from the combination of skill, assignment, equipment,
condition and unit support. It is not a permanent species-like class.

The first graybox tactical functions are:

- militia or rifle fighter;
- guard;
- scout;
- sapper/field engineer;
- combat medic;
- heavy-weapons operator;
- fixed-weapon crew;
- squad leader.

These are derived projections for AI and presentation. For example, a resident
with engineering skill, a demolition assignment and the exact required tools
acts as a sapper. Removing their tools or changing the assignment changes the
function without replacing the person. A weapon requires its exact ammunition;
armour, tools, medical supplies and carried cargo likewise retain ordinary
physical custody and can be lost, stolen or destroyed.

Graybox colours, equipment silhouettes and nameplates may expose the derived
function, but colour is presentation evidence only.

### Exact equipment issue, loss and return

Equipment is not granted by a role projection.  The first issue path is an
owned, one-stack transfer from an active settlement depot to one named member
of an active defender unit.  Its durable issue request retains the assault,
resident, source container slot, exact item ID, intended physical hand slot and
the expected resident body identity.  It is admitted only when all of these
facts still agree: the resident is an exact active unit member, the source item
is a suitable settlement-owned item in its claimed active slot, and neither
another equipment request nor existing actor custody already claims that item.

The loaded-chunk executor is deliberately a physical effect, not a canonical
shortcut:

1. it waits for both the active source chest and the exact owned Villager body
   to be naturally loaded;
2. after the request is durable, it moves the identity-tagged Minecraft stack
   from that exact chest slot into the named body hand;
3. it confirms only after observing the source slot empty and the same tagged
   stack in that hand; the receipt then changes canonical custody from the
   container to that resident.

Until confirmation the item stays in its source custody.  An unloaded endpoint,
foreign stack, changed hand, stolen source item or duplicate tag is a visible
deferral or conflict, never permission to spawn a replacement weapon.  Recovery
inspects the same two endpoints: a complete observed handoff may confirm, an
unchanged source may resume, and any mixed or missing evidence becomes
`UNKNOWN_AFTER_RESTART` for that exact request.  A missing body alone never
proves that the person died or dropped the weapon.

Return is a separate durable physical request after the unit releases the
resident.  It moves the same tagged stack from the still-living resident's hand
to a named free slot of the active home depot and confirms only the inverse
postcondition.  If the resident dies, a player steals the item, a blast destroys
it, or ordinary Minecraft drops it, the existing exact custody/destruction
observation boundary records the actual result first; the tactical function then
changes from that custody fact.  A leader loss therefore degrades the retained
unit, and equipment loss degrades the same person, without either creating a
replacement person, weapon or hidden reserve.

## Crews and tactical units

A work crew or tactical unit retains:

- one stable organization ID and lifecycle;
- exact member IDs and at most one current leader;
- one owning task/operation and bounded objective;
- exact equipment and supply claims;
- formation or work positions where spatial execution requires them;
- derived readiness, casualties and explicit release or recovery state.

Membership never duplicates individual ownership. A resident remains owned by
their population register while the unit holds an exclusive assignment claim.
Losing a leader degrades coordination, reconsideration speed or morale; it does
not freeze, despawn or automatically kill the remaining members. Replacement
leadership must be selected from perceived surviving people.

### Accepted initial unit composition

The first playable human force is deliberately small and heterogeneous. It is
not a new population tier and not a permanent set of Minecraft entity classes:
each entry is an organization of the same exact residents, admitted from their
current work only when its owner can name the people, equipment and objective.

- **Settlement defence element:** normally four to eight residents. It has one
  retained leader, armed defenders where exact weapons exist, and may add a
  medic or field engineer only when their real equipment and assignment are
  admitted. This is the first implementation target for an assault response.
- **Patrol / scout pair:** two to four residents. Its purpose is local
  observation, perimeter response and escort, not a hidden strategic sensor;
  it contributes only observations physically or canonically available to it.
- **Rail or cargo escort:** two to four residents attached to one named cargo
  operation. It is distinct from the train crew: losing the escort must not
  turn logistics workers into soldiers or permit the operation to invent a
  replacement guard.
- **Engineering / recovery team:** one to four residents for repair,
  fortification, demolition, construction or aftermath recovery. It can be
  guarded, but its engineering capacity and tools remain a separate exact
  claim.
- **Medical / evacuation team:** one to three residents, attached to named
  patients or an evacuation operation. It is not a combat-healing aura; its
  supplies, treatment and casualty outcomes are ordinary exact processes.

Larger formations are compositions of these units under a named operation, not
a cohort abstraction. The numeric ranges are ruleset calibration, not Java
constants: a small settlement may field fewer people, and a depleted or
unequipped settlement may field none. Unit labels and colours communicate the
current assignment; they never grant a skill, weapon, medical capability or
authority by themselves.

### Implemented foundation

The first exact tactical organization is the defender unit owned by one
`SettlementAssault`. It has a deterministic stable ID derived from that assault,
an ordered exact member list and a fixed initial leader: the best available
security resident, then civic/security capability and canonical ID. Its members
derive `SETTLEMENT_DEFENCE` only from that active assault; they are not copied
into a second roster. Admission excludes dead, starving and already assigned
residents, so the current foundation refuses an assault rather than silently
stealing a person from civilian work. Snapshot and WAL recovery retain the same
ordered member/leader identity through the existing assault owner record.

The first interruption path is now implemented for COLD, market-backed bread
production. The exact active defender candidate retains the same fresh Scout
sighting as the assault; before the unit can claim them, the interruption
returns the original wheat stack to its original depot slot, releases its exact
invoice reservation, cancels its exact market order and blocks that production
task. Materialized input, a prepared/running physical transform, every other
civilian process, equipment/supply claims and morale remain
separate Wave-4 work. A lost leader stays the retained leader ID and therefore
visibly degrades the unit instead of being silently replaced.

Exact equipment custody now uses the generic `InventoryCustody.Actor` surface:
the named item remains one stack in the common inventory ledger, but its current
physical holder is one resident or bioform. Snapshot and observed-transfer WAL
codecs retain that identity. This is a custody primitive, not implicit issue
authority: an owning unit process must still reserve, hand off and recover the
item through explicit observed transitions.

The first outbound defender issue is implemented. Its deterministic review
process selects one unarmed exact member and one suitable exact active-depot
stack; it creates a durable `EQUIPMENT_ISSUE` request binding the assault,
resident, source slot, item and expected body. The loaded executor performs
the outbound hand-off only after that request is `RUNNING`, and confirms only
the exact empty-source/held-item observation. The normal actor projection then
refreshes the resident to a visible `MILITIA`/`ARMED DEFENDER` nameplate with a
second `UNIT …` readiness line, without turning presentation into a roster. The first native scenario proves the
outbound custody and graceful-restart inspection. The separate inverse-return
flow is also implemented: after a resolved assault it reserves one named empty
active-depot slot, moves the same tagged hand stack only through a durable
request, and confirms the inverse receipt. Its native scenario proves terminal
slot custody, correlation trace, player-opened depot and graceful restart.
Observed loss, drop and destruction now retain the actual item custody or
destruction first. The first managed-death executor moves a matching tagged
hand stack from `Actor` to its exact `WorldCarrier` before vanilla creates the
ordinary drop, or records exact destruction when the hand no longer contains
that identity; later player pickup remains the ordinary `WorldCarrier` to
`Player` observation.

The implemented first read model is intentionally narrower than the full
future roster. It derives `CIVILIAN`, `MILITIA`, `ARMED_DEFENDER`, `GUARD` and
`SQUAD_LEADER` solely from an exact current assignment, retained defender-unit
leadership, profession and actor-held exact weapon. `SQUAD_LEADER` identifies
the retained unit leader; it does not grant a weapon or replace their ordinary
equipment truth. An exact weapon without an owning tactical assignment remains
`CIVILIAN`. The projection is pure and persisted nowhere, so loss, theft or
recovery of the same item changes the next projection rather than requiring a
role migration. Scout, sapper, medic, heavy-weapon and fixed-weapon functions
must wait for their real operation and equipment owners; they are not labels
invented ahead of those systems.

### First exact defender readiness

The initial settlement-defence readiness is a pure projection, not a second
unit record or a mutable morale counter. It compiles only the retained assault
unit, the same current `SETTLEMENT_DEFENCE` assignment, current actor vitality
and exact actor-held graybox weapons:

- `UNAVAILABLE`: no living member still holds the unit's exclusive assignment;
- `IMPROVISED`: living assigned people remain, but none currently carries an
  exact weapon;
- `DEGRADED`: armed living people remain but the retained leader is dead or no
  longer operational;
- `READY`: the retained leader and at least one armed living member are
  operational.

The status is recomputed from the exact facts after every custody or casualty
observation and survives restart because those facts survive restart; it is
never serialized independently. COLD settlement-assault damage consumes the
same projection: only an armed member of a `READY` unit receives the configured
guard output. An unarmed member, an `IMPROVISED` unit, or all survivors after
leader loss use the configured militia output. Thus a stolen/destroyed weapon
reduces that same person's capability immediately, while leader loss degrades
the surviving exact unit without replacing, deleting or freezing it. HOT
Minecraft combat stays physics-authoritative; this rule governs only the COLD
continuation after the same HOT lease releases.

## Work, mobilization and opportunity cost

Mobilization is a visible resource-allocation decision:

```text
perceived threat and doctrine
  -> requested tactical capability
  -> exact available residents, equipment and supplies
  -> explicit release/suspension of conflicting work
  -> exact unit assembly and operation
  -> casualty, return, reassignment or recovery
```

Emergency militia may draw from civilian professions, but their effectiveness
reflects actual skills and equipment. Removing farmers, machinists, medics or
rail crews from work reduces the corresponding settlement throughput. A
settlement cannot receive both their civilian output and their military action
during the same exclusive assignment.

Strategic AI chooses whether that opportunity cost is worthwhile from perceived
facts. Local HOT AI receives only its retained assignment, permitted target
policy and nearby observations; it cannot invent a strategic mobilization.

## HOT/COLD continuity

The same resident, assignment, unit membership, equipment and operation exist
in both execution locations.

- COLD advances bounded work, travel and combat from canonical state.
- HOT leases the exact people and items to Minecraft for ordinary movement,
  collision, item use, combat and physical consequences.
- HOT admission cannot manufacture a better profession, skill, weapon or crew.
- departure captures exact position, health, equipment/custody and operation
  continuation before COLD resumes;
- restart inspects uncertain physical effects instead of replaying or replacing
  people and equipment.

## Future Create boundary

The pure domain uses semantic assignments such as machine operation, rail crew,
loading, engineering and fixed-weapon operation. It does not encode Create
blocks, contraptions or API types. A future Create provider may make those same
assignments physical while the exact resident, employment, item custody and
operation remain canonical Frontier state.

Breaking a machine or railway can make an assignment impossible. It cannot
convert the operator into another profession, silently finish their work or
grant a COLD output while the physical lease remains active.

## Migration from the provisional model

The present `ResidentRole`/`ResidentSkill` representation is implementation
debt, not a compatibility requirement for the final model. Its replacement
must:

- introduce explicit stable, non-reused wire tags for every persisted family;
- version snapshot and WAL payloads rather than reinterpret old bytes;
- map the old six bootstrap affinities deterministically into initial
  profession and skill values;
- create no current assignment or military membership merely from an old role;
- preserve exact resident IDs, households, health, nutrition, contracts and
  custody;
- add negative and recovery tests for unknown tags, conflicting assignments,
  death during committed work and restart during HOT unit execution.

## Acceptance gates

The human model is not complete until evidence proves:

- one person can change job or assignment without changing identity;
- mobilization removes exact labour from civilian production and return
  restores availability without duplicating completed output;
- an underskilled emergency militia performs differently from an equipped,
  trained unit through persisted rules rather than presentation;
- equipment loss immediately removes the corresponding capability and remains
  reconciled after restart;
- leader loss degrades but does not delete a surviving exact unit;
- HOT departure and COLD return retain the same people, organization,
  equipment, casualties and objective;
- death, disease, hunger, migration and employment conflicts fail visibly and
  cannot double-assign one resident;
- an unbriefed player can identify what a group is doing, what it needs and
  what settlement function its mobilization has interrupted.
