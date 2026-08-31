# Frontier v3 post-graybox Create direction

Status: accepted future product direction; provider implementation is outside
the current graybox completion gate.

This document is normative for seams which Frontier v3 must preserve while the
graybox is completed. It does not authorize Create dependencies in the pure
domain or make a decorative Create build a substitute for a complete,
validated simulation.

## Player promise

After the graybox proves the autonomous world, its placeholder transport and
facilities can be replaced by real Create railways and machinery without
changing who owns decisions, people, resources, cargo, damage or history. A
visible mechanism matters: stopping its power, breaking its track or taking
its exact output changes the same canonical simulation.

## Accepted scope

The accepted transport provider is **rail only**:

- long inter-settlement rail corridors;
- exact freight trains and wagons;
- stations, freight yards, sidings, switches and signals;
- bridges, tunnels, loading platforms and maintenance depots;
- ordinary physical loading, travel, obstruction, derailment, unloading and
  repair consequences.

Road freight vehicles and aeronautical transport are not part of this accepted
phase. Roads may remain settlement circulation, pedestrian paths or graybox
operation surfaces, but they do not become a second canonical freight network.
Future acceptance of another transport family requires its own provider,
ownership, HOT/COLD and recovery contract.

The previously stated settlement direction remains factories, farms,
industrial mechanisms, weapons and turrets. The following five additional
Create-oriented sectors are accepted:

1. **Extraction:** mines, quarries, drilling, sorting and bulk dispatch.
2. **Energy:** visible kinetic/steam generation, distribution, load and bounded
   emergency supply.
3. **Metallurgy and materials:** ore processing, metal, mechanical parts,
   rails, construction materials and weapon inputs.
4. **Warehousing and rail logistics:** exact storage, classification, cranes,
   rail loading/unloading, freight yards and dispatch.
5. **Construction and engineering:** staged structures, track and bridge work,
   repair trains, cranes, field engineering and infrastructure recovery.

Medicine, sanitation, communications, passenger transport, salvage and other
suggested sectors are not accepted by this decision and remain possible future
scope.

## Authority boundary

Create is a physical capability provider, never a second economy, planner or
world simulation.

```text
canonical need and operation
  -> exact resources, actors, route and capability request
  -> durable exclusive physical lease
  -> Create mechanism or train
  -> observed physical result
  -> typed canonical consequence
```

The pure Frontier v3 domain contains no Create classes, blocks, recipes,
networks or API types. It owns semantic facilities, recipes, energy/capability
demand, exact inventory and cargo, route topology, operations and outcomes.
NeoForge owns a registered Create provider which translates those contracts
into supported physical mechanisms and observations.

At most one provider owns a production, transport or effect execution at a
time. While a Create mechanism or train holds the lease, the corresponding
COLD process is suspended. A provider failure, missing capability, changed
machine or unknown train never permits a simultaneous canonical fallback that
could duplicate work or cargo.

## Graybox readiness obligations

The current graybox remains the first physical implementation of these
provider-neutral contracts. Before it is considered final, its architecture
must avoid assumptions which would force Create into the domain later:

- facilities expose semantic input, output, work, energy and maintenance ports
  rather than graybox block colours as capability;
- recipes reserve and transform exact identified input/output items;
- route topology owns stable rail segments, junctions, stations, bridges,
  tunnels and loading ports independently of a future Create graph object;
- every train-equivalent operation owns one exact vehicle/carrier identity,
  wagon/cargo membership, route cursor and execution lease;
- physical executors and capability providers are closed registered
  extensions, not composition-root type switches;
- construction and repair use staged jobs, exact materials, provenance and
  observed postconditions;
- energy and throughput are semantic facility constraints in the persisted
  ruleset, not hard-coded Create stress values;
- machine operators, rail crews, loaders and engineers are semantic human
  assignments over exact residents, skills, employment and equipment as defined
  by `frontier-v3-human-capabilities.md`; Create never introduces a parallel
  profession or crew registry;
- materialization and observation retain unrestricted physical damage and do
  not restore a broken rail, bridge, machine or player change from a template.

The graybox may use simple rails, corridors, chests, carts and coloured
machines, but its state names and causal flow must remain usable by the future
provider.

## Industrial chains

The first Create phase should close a small set of real loops rather than
placing unrelated animated machinery:

```text
mine/quarry -> sorting -> ore train -> metallurgy -> parts/materials
farm -> processing -> warehouse -> food train -> settlement depot
fuel/water -> generation -> facility capability
metal/parts -> rails, machines, weapons and turret supply
materials/parts -> engineering train -> construction or repair
```

Every arrow is exact custody or a named production/transport operation. There
is no aggregate shipment hidden behind an empty train, no decorative mill
granting food output and no turret with implicit ammunition or power.

## HOT/COLD execution

When naturally HOT, real Create machinery performs visible work and a real
train moves, collides, carries exact inventory and can be boarded, obstructed,
robbed or damaged. Canonical completion follows only accepted physical
postconditions.

When COLD, the same retained production or rail operation advances through its
canonical events and exact route/cargo state. It does not simulate individual
cogs. HOT admission adopts or creates only the exact leased train/mechanism;
departure captures its exact cargo, position, damage and continuation before
COLD resumes. If a native provider keeps an object physically authoritative,
its lease remains active and COLD cannot advance it concurrently.

No PM path force-loads a railway merely to prove movement. A naturally loaded
player following a train keeps the witnessed operation HOT; an unwitnessed
operation may use COLD continuation and later materialize the same state.

## Physical consequences

- Breaking a rail segment or switch changes route availability and may stop,
  divert or strand the exact train.
- Destroying a bridge affects its semantic structure and every physically
  reachable object; there is no infrastructure safe zone.
- Taking an exact wagon stack changes canonical custody and the owning
  delivery.
- Loss of energy or a required machine stage blocks or slows the exact work;
  it does not merely stop an animation while canonical output continues.
- A turret requires a recognized sensor/crew policy, energy, exact ammunition,
  a physical firing arc and ordinary projectile consequences.
- Repair requires a named task, workers, exact materials and observed world
  results. Desired-state projection never silently rebuilds over aftermath.

## Future acceptance gates

The Create phase is not accepted until normal, negative and restart evidence
proves at least:

- one same-identity freight train loads exact cargo at one naturally loaded
  station and later unloads that cargo at another without duplication;
- player theft, track obstruction, bridge loss and station damage change the
  owning route/operation and remain after restart;
- a powered loaded facility produces only its exact observed output, while a
  stopped or altered mechanism cannot receive hidden COLD output;
- a construction or repair train consumes its exact materials only after the
  physical stage postcondition;
- a turret consumes exact power/ammunition and reconciles unrestricted real
  projectile damage;
- HOT departure, COLD continuation and return retain train, wagons, cargo,
  workers, damage and route cursor;
- scale/JFR evidence covers the accepted long railway network and simultaneous
  industrial activity without relaxing identity or causality;
- an unbriefed player can trace where material came from, why a machine or
  train stopped and what physical action can change the outcome.

Exact Create version/add-ons, recipes, energy ratios, rail topology and visual
assets remain later technical and balance decisions. They may refine this
direction but cannot move canonical authority into Create or add a second
transport economy.
