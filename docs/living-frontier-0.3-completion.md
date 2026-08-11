# Pale Mirror 0.3 completion candidate

This document records the implemented scope that turns the existing Living
Frontier mechanics into a closed-alpha completion candidate. It does not
declare the product gate passed: unaided client playtests, a real Create train
run and a cooperative session remain human acceptance evidence.

## Player-facing causal loop

The introductory `iron_frontier` definition is now layout version 2. A fresh
region pins the receiving rail terminal and depot before physical work starts.
The first infection is not scheduled merely because a village was registered.
It waits until the primary audience has discovered:

1. the settlement;
2. its PM-owned receiving depot;
3. the baseline vanilla minecart line;

and then waits five more simulation steps. Discovery is audience-scoped
canonical knowledge and survives restart. Mine17 appears in Atlas and
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
It freezes rather than simulating deliveries in unloaded chunks.

Loaded critical rail/support damage suspends the physical route and validates
the canonical `RouteContract` at zero capacity. PM stores the exact damaged
cells. When the player restores their registered postconditions, the route
resumes and revalidates its nominal capacity. Decorative unknown changes remain
diagnostic and are not overwritten. The cart and its progress, the damaged
cells and the route state survive restart.

Create is unchanged as the alternate industrial path: the same observed
vehicle must prove traversal at both registered endpoints inside the proof
window. It is not a substitute for explaining the pre-existing baseline line.

## Fair intervention

Each primary audience has a source-neutral reachability observation:
`LOCAL`, `REGIONAL`, `REMOTE` or `CONNECTED`. An emergency pins its grace at
open time:

| Reachability | 60-step policy window |
| --- | ---: |
| Local | 60 |
| Regional | 90 |
| Remote | 120 |
| Connected | 40 |

Abstract economy and settlement policy continue while players are offline,
but irreversible evacuation grace does not decrease when no member of the
StoryAudience is online. Refugee permits are validated against the canonical
open window rather than an obsolete absolute deadline.

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
