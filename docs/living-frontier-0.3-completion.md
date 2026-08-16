# Pale Mirror 0.3 completion candidate

This document records the implemented scope that turns the existing Living
Frontier mechanics into a closed-alpha completion candidate. It does not
declare the product gate passed: unaided client playtests, a real Create train
run and a cooperative session remain human acceptance evidence.

The current requirement-by-requirement evidence is recorded in
[`living-frontier-0.3-release-audit.md`](living-frontier-0.3-release-audit.md).

## Player-facing causal loop

The introductory `iron_frontier` definition is now layout version 2. A fresh
region pins the receiving rail terminal and depot before physical work starts.
The first infection is not scheduled merely because a settlement was registered.
It waits until any non-spectator audience has discovered:

1. the settlement;
2. its PM-owned receiving depot;
3. the baseline vanilla minecart line;

and then arms one global incident clock exactly once and waits five more
simulation steps. A later audience cannot reset that clock. Discovery is
audience-scoped canonical knowledge and survives restart. Mine17 appears in Atlas and
JourneyMap only after the player follows the completed line or approaches its
MineSite. Red Valley is revealed when settlement policy requests alternate
supply.

Atlas is the actionable explanation. It distinguishes `NO_SOURCE` from
`ROUTE_DAMAGED`, shows effective versus nominal capacity, reserve/flow/defence,
the remaining active intervention window, and a bilingual project funding
summary. JourneyMap remains navigation-only; the Regional Ledger remains the
detailed causal history.

## Baseline and industrial rail

The primary route is still a low-capacity vanilla corridor. A lease-authorized
representative minecart with a visual cargo display moves only over loaded
segments while Mine17 is operational and canonical route capacity is positive.
It is a singleton, non-colliding PM projection rather than physical cargo:
loaded duplicates are removed, external impulses are discarded and it parks at
the loading yard while the mine has no production. It never simulates deliveries
in unloaded chunks.

The fresh authored manifest validates that baseline topology at region
registration, so an unvisited healthy settlement does not starve merely
because its railway chunks have not yet been loaded. This persistent topology
evidence does not use the Create freshness window. As corridor chunks become
material, missing or disconnected loaded rail explicitly blocks the contract;
unloaded chunks retain their last canonical revision.

Route health is no longer inferred from the exact PM-authored block mask. PM
persists a graph of observed rail nodes and their connections between the fixed
loading and receiving endpoints. A block observation updates only its loaded
local segment; unloading a chunk does not invalidate its last accepted graph
revision. If no connected path remains, the canonical `RouteContract` is
validated at zero capacity. A player may repair or reroute the line with a
different height, curves or rail positions; once the persisted graph connects
both endpoints again, PM adopts that path and restores nominal capacity.

Exact baseline/last-applied provenance still guards PM writes and cleanup, but
is not a service-health test. Chunk-local reconciliation catches crash windows
and changes made without an ordinary placement event. The representative cart
and its progress follow the accepted path; topology, route status and physical
observations survive restart.

Create is unchanged as the alternate industrial path: the same observed
vehicle must prove traversal at both registered endpoints inside the proof
window. The endpoints do not need to be loaded simultaneously: PM retains each
typed observation and only accepts matching vehicle IDs while both facts are
fresh. It is not a substitute for explaining the pre-existing baseline line.

For closed-alpha testing, a permission-level-4 operator may run
`/pale_mirror logistics commission_red_valley [region]`. This persists a
separate Railway Untold `AUTONOMOUS_DEV` exercise whose authority is bounded to
one planned Red Valley → Ironhill connection and one segment footprint at a
time. The exercise can build the line, named stations and scheduled train
without hundreds of blocks of manual track laying. It deliberately cannot
validate the canonical alternate route: normal read-only Create observations
must still see the same train at Red Valley and Ironhill inside the proof
window. Production/world-authored railway plans retain loaded-chunks-only
policy; the exception is explicit, operator-only and intended for disposable
playtest worlds.

Canonical infection is also readable before the player accepts a story. When an
infected MineSite is nearby and loaded, PM materializes the source-specific
passive overlay and its registered controller through a persisted job. Accepting
and entering the scenario adds combat actors and native gate mechanics; it is
not the trigger that makes the already-existing infection visible.

## Fair intervention

Each informed audience has its own source-neutral reachability observation:
`LOCAL`, `REGIONAL`, `REMOTE` or `CONNECTED`. An emergency pins its grace at
open time:

| Reachability | 60-step policy window |
| --- | ---: |
| Local | 60 |
| Regional | 90 |
| Remote | 120 |
| Connected | 40 |

Abstract economy and settlement policy continue while players are offline.
The one global irreversible evacuation grace advances while at least one
informed non-spectator audience is online and pauses only when all are offline.
Refugee permits are validated against the canonical open window rather than an
obsolete absolute deadline.

Every audience receives a private scenario for the same global crisis and may
accept or decline independently. Fighting, repairing, validating a route and
depositing supplies are shared physical help available after settlement
discovery. Alternate dispatch, shelter registration and player-started
evacuation are strategic commands and require that audience's active `RESPOND`
crisis plus their physical prerequisites. The first canonical recovery,
alternate-supply or evacuation outcome resolves every linked nonterminal
scenario exactly once; it never duplicates the world outcome.

## Positive aftermath

Stable surplus still creates an `UPGRADE_STOREHOUSE` intent, but schema 31 no
longer silently reserves 24 IRON at planning time. The intent owns a persisted
project escrow:

```text
accepted development opportunity
  -> player uses physical IRON at the PM depot
  -> persisted resource-transfer receipt
  -> canonical project contribution
  -> physical stack is consumed exactly once
```

Repeated receipt IDs cannot fund a project twice. The contributed IRON never
appears in freely withdrawable settlement stock. If the audience declines or
does nothing, the settlement reserves the missing amount from its own stock
after 24 qualifying prosperous simulation steps. Only a fully funded intent
may start physical materialization. A player-funded physical conflict becomes
`BLOCKED`, not a destructive cancellation that loses its escrow.

The verified storehouse changes the depot from polished andesite to stone
brick, doubles storage capacity and raises prosperity/housing. Atlas reports
funding progress and the active physical result. This is the first deliberately
legible positive continuation of the crisis arc.

## Repeatable closed-alpha exercise

Prepare a clean, deterministic exercise on an existing private server with:

```bash
./scripts/prepare-living-frontier-exercise.sh /absolute/server/directory --confirm
```

The script refuses a broad or running-server target, moves the existing
`level-name` directory into `.pale-mirror-backups`, and pins the seed to
`pale-mirror-living-frontier-03`. It never deletes the old world.

Optional local playtest evidence is enabled for the server JVM with:

```text
-Dpale_mirror.playtest_metrics=true
```

The server writes JSONL to
`<world>/pale-mirror/playtest-events.jsonl`. Player UUIDs are SHA-256
anonymized; the file records Atlas opens and typed domain events only. Metrics
are not SavedData, are not canonical and cannot affect simulation.

## Remaining human release gates

- five unaided clean-room playtests, with the agreed 4/5 comprehension gate;
- one real Red Valley → Ironhill Create scheduled-train validation;
- one 2–4 player StoryAudience session;
- one graphical/audio pass for discovery, route damage/recovery, carrier,
  infection and positive storehouse aftermath;
- explicit verification that combat, logistics, evacuation and refusal are all
  understandable without operator commands.

Until those are recorded, the correct label is `0.3.0-alpha` completion
candidate, not a finished public gameplay release.
