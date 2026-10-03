# Frontier v3 hive-expedition continuity contract

Status: accepted target contract for `MAT-005` and the remaining half of
`V3-AUD-038`. Implementation and evidence remain pending.

## Player promise and sole owner

A player can encounter one remote hive expedition already in progress: the
same exact Overseer-led organisms that woke, assembled and departed remain a
recognisable formation while travelling, entering contact and retreating or
returning. Approaching, leaving or restarting changes only execution detail;
it never creates an expedition, substitutes nearby bioforms, teleports the
group or resets its purpose and progress.

The canonical assault/expedition operation is the sole persisted owner. A
technical implementation may extend the existing settlement-assault owner or
replace it coherently, but may not introduce a second roster, target, route,
tactical plan or progress authority merely for presentation. It retains:

- the originating hive task, fresh exact sighting/perception cause, named
  target and operation epoch;
- the ordered exact roster, its exact living Overseer, weighted command
  authority and the same individual identities handed off by mobilisation;
- one retained tactical plan, operation phase and contact/retreat conditions;
- bounded immutable surveyed `GROUND_BIOFORM` approach and return topologies,
  direction/cursor and distinct formation body positions for every member;
- typed obstruction, member/controller loss, command-memory and recovery
  evidence.

Ambient bioform goals, a generic assault endpoint, nearby Zombies, diagnostics
or Foundry findings cannot execute or confirm the expedition.

## Continuous lifecycle

```text
same-roster mobilisation DEPARTED
  -> APPROACHING over one retained expedition topology
  -> CONTACT hand-off to the same operation/front
  -> ENGAGED or typed ABORT/RETREAT decision
  -> RETREATING/RETURNING over retained topology
  -> exact survivors returned, stranded, dead or retained for recovery
```

Mobilisation departure atomically hands its already staged bodies, original
sighting, Overseer and ordered roster to this owner. The hand-off fails visibly
if any identity, body, target or command fact changed; it never reselects a
group. Contact similarly transfers the same roster and current bodies into the
existing operation/front boundary without a second lease, duplicate attacker
set or endpoint teleport. Retreat is an owned phase caused by the retained
tactical plan, command loss or operation outcome, not disappearance after a
battle result.

## HOT, COLD and local movement

COLD advances only the next retained OPEN topology edge and atomically updates
the complete formation plus the sole `ActorLocation` records. It may use the
declared command-memory policy but cannot query unloaded Minecraft terrain,
skip a checkpoint or continue coordinated movement in `INSTINCT`.

A naturally demanded expedition-march scene is one registered coordinated-
traversal adapter in the Process/Scene SDK. Its durable lease contains exactly
the operation, roster, Overseer, formation, cursor and current authority epoch.
The shared Minecraft movement provider owns only bounded sub-block movement
inside the retained local envelope and commits one edge only after every
required member is observed at its legal distinct next body. It may handle
ordinary collision locally but may not choose another strategic route,
sidestep outside the envelope, force-load, spawn, despawn or replace a body.

Ordinary demand loss checkpoints the unchanged operation/formation/cursor and
returns it to COLD after the lease closes. An expedition and its contact/combat
front never hold competing physical leases over the same member.

## Obstruction, command loss and recovery

A missing/changed owned body, death, occupied next formation cell, damaged
support/edge, player/world obstruction or stale operation epoch becomes typed
evidence for the same expedition. Survivors and confirmed casualties remain
exact; no normal gameplay disruption quarantines the whole frontier.

Loss or disconnection of the exact Overseer preserves every subordinate,
body, wound, lease and retained order while command phase degrades through the
existing bounded `SIGNAL_MEMORY` policy. After `INSTINCT`, coordinated COLD/HOT
march cannot advance or open a new battle scene. Eligible reclaim requires a
physically present exact Overseer and preserves the unchanged roster; otherwise
the tactical owner selects only its already declared local fallback, retreat,
stranding or failure result.

Graceful or crash recovery retains the exact operation, roster, command phase,
formation and completed cursor. A physical scene becomes bounded
`UNKNOWN_AFTER_RESTART` until naturally loaded exact bodies and the retained
next-edge postcondition are inspected. Confirmed edges, deaths, contact and
retreat are never replayed. A fresh-world format change rejects obsolete
snapshot/WAL bytes rather than inferring missing expedition facts.

Foundry may report the retained edge as `OPEN`, `BLOCKED` or `UNVERIFIED` from
already loaded cells. It never loads, clears, repairs, reroutes or changes the
operation.

## Evidence and limits

MAT-005 M2 closure requires composed evidence for the production owners:

- pure admission/handoff, exclusive roster, non-flat formation edge, COLD
  progress, contact/retreat hand-off, obstruction, member/Overseer loss,
  command degradation and snapshot/WAL recovery;
- a focused expedition-march Scene GameTest with exact ordinary HOT progress,
  no ambient/endpoint substitution, blocked or damaged next edge and owned-
  body/controller loss;
- active-march graceful restart and ordinary HOT-to-COLD demand loss with the
  same roster, formation, cursor and command phase; and
- one candidate-bound disposable ordinary-player scenario with a semantic
  terminal assertion, bounded single-run PMV3 trace and player-height
  in-progress expedition frame. Accepted mobilisation/departure and F0.4
  contact/return receipts may compose where their identities and unchanged
  inputs genuinely match; they need not be ceremonially re-proved.

One final critical integration gate applies to the coherent candidate. These
facts establish M2 only. M3 still requires an unbriefed player to recognise the
Overseer, march direction, command loss, contact and retreat without a debug
board. Natural-terrain hardening, broad combat balance, final art, scale changes,
deployment, production cutover and v2 removal remain separate.
