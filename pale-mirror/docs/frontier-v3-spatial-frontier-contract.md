# Frontier v3 retained spatial-frontier contract

Status: accepted target contract for `MAT-008`; implementation and evidence are
pending. It is not active implementation authority before `MAT-007` acceptance
and an explicit work order.

## Player promise

When a loaded field grows, infection advances or retreats, or living terrain
extends and repairs itself, the player sees a bounded frontier move through
space rather than an entire object changing between two frames. Breaking,
harvesting, blocking, burning or cleansing the active area changes that same
canonical process. Going away lets the world continue semantically; returning
shows its current state and aftermath without replaying missed spectacle or
repairing player changes.

## Scope and sole ownership

`SPATIAL_FRONTIER` is the execution archetype for a distributed environmental
process whose meaningful duration is spatial. Each concrete canonical family
continues to own its domain state and decisions:

- the resource-site lifecycle owns exact field and crop state;
- the infection/ecology lifecycle owns exact sparse infection cells, intensity,
  substrate, expansion and retreat;
- the hiveroot lifecycle owns stable segment growth, damage and resource-backed
  regrowth; and
- canonical physical aftermath owns unresolved or confirmed external block
  consequences.

The generic frontier SDK owns only validated registration, bounded leasing,
cursor/work-unit mechanics and typed observation dispatch. Minecraft projection,
boards, particles and chunks own no second field, infection, root or progress
state. `MAT-007` remains the owner of one exact organ/bioform morphogenesis;
`MAT-008` closes the reusable environmental frontier and the remaining crop,
infection, territorial growth/retreat and root-regrowth families.

## Retained frontier model

Every duration-bearing spatial process retains:

- stable process/family/ruleset identity and exact owning object or region;
- one bounded immutable semantic envelope and current revision;
- exact ordered or deterministically prioritized work units, completed and
  pending sets/cursors, direction and partial progress;
- exact required resources/capacity and confirmed consumption per unit;
- current HOT custody/version or COLD continuation, next due action and typed
  wait/conflict/terminal outcome; and
- bounded physical observations and aftermath references for affected units.

Work units are semantic cells, crop slots, clumps, segment sections or another
family-declared bounded unit; they are not an unbounded mirror of every loaded
Minecraft block. The family descriptor declares its maxima, neighborhood,
ordering, cadence and atomic postcondition. The frontier cannot discover a new
target by scanning loaded chunks, output size or nearest changed block.

```text
canonical need and bounded envelope
  -> exact work set/frontier admitted
  -> one or more budgeted semantic units become eligible
  -> COLD semantic progress or naturally demanded HOT physical execution
  -> typed unit confirmation, obstruction or external consequence
  -> next retained frontier revision
  -> completed, exhausted, retreated, conflicted or recovery inspection
```

One adapter tick may process only the declared bounded current work set and
budget. Large extents advance across multiple canonical turns; they are not
made incremental merely by looping through every block in one tick.

## Crop progression

One active field retains all exact crop slots and their canonical stages while
one deterministic frontier selects bounded next slots or groups. COLD advances
the same slots under the persisted ruleset. HOT changes only naturally loaded,
plan-owned slots after a whole affected-unit precheck and confirms their exact
postcondition before advancing the frontier.

Ordinary player harvest, trampling, block replacement, fire or another physical
effect is observed against the exact slot and immediately changes the same
field process. It cannot be overwritten by a later batch repaint or counted as
autonomous progress. Vanilla random or bonemeal growth remains rejected for
owned crops unless a future explicit canonical input rule admits it. Harvest
work from `MAT-001` consumes the current exact mature slots and never owns a
parallel crop-growth counter.

## Infection and territorial progression

One spread or retreat owns an exact source cell, direction/adjacency proof,
target cell or current-cell clumps, finite substrate/resource cost and retained
spatial work. Infection surface remains distinct from hiveroot and organs.
Increasing intensity, creating a neighboring cell, retreating or cleansing must
progress through bounded exact clumps/columns while HOT; a whole sixteen-column
replacement in one adapter loop is M1 unless the canonical cause is genuinely
instantaneous.

Fire, explosion, decontamination, excavation, construction or player block loss
at the active frontier enters through ordinary physical observation and changes
the affected process or leaves bounded aftermath. An unknown or foreign cell
is not adopted, skipped or repainted. A blocked target does not cause hidden
redirection; a later alternate frontier is a new canonical decision based on
the side's bounded knowledge.

## Hiveroot extension and regrowth

An extending or repairing root retains exact planned segment sections,
attachment/junction, vascular/synaptic requirements and resource allocations.
HOT shows ordered tissue work and confirms support/geometry per bounded unit;
COLD advances the same semantic sections without querying terrain. A destroyed,
unsupported, blocked or player-owned section changes that exact growth/repair
and cannot be restored by desired-state projection. Confirmed completed units
remain real even when later units fail, and only confirmed work consumes its
corresponding exact allocation.

Foundry may prove the immutable plan and inspect already-loaded settled/reloaded
geometry. It never advances, redirects, force-loads or repairs a frontier.
Natural-terrain provider hardening remains a later gate unless a defect also
violates the provider-neutral bounded-unit contract.

## HOT/COLD, switching and first visibility

Observer demand never starts, stops or selects environmental work. COLD advances
only canonical semantic units and bounded knowledge. A naturally demanded HOT
lease binds the exact process revision and current work units, suspends only
that COLD writer and commits progress only from matching physical observations.
Demand loss drains after each physical-effect boundary and returns the exact
unfinished frontier to COLD.

First visibility materializes current retained partial state within the ordinary
projection budget. It cannot fast-forward the frontier, replay prior growth or
hide a contradiction between COLD state and loaded blocks. Rapid HOT/COLD
switching preserves process identity, completed/pending units, resource use,
direction and terminal outcome without systematic progress or resource benefit.

Fixed facilities and dynamic frontiers have different currentness rules. A
fixed multi-chunk facility cannot claim `CURRENT` or operational capability
while a naturally exposed required sibling unit is pending, unobserved or
conflicting. A dynamic frontier may be intentionally partial, but its complete,
active, pending, damaged and unknown units must all derive from one retained
revision. Natural approach from either side of a chunk boundary and reversed
observation order must converge to the same classification without loading a
sibling chunk or overwriting foreign state.

## Recovery, aftermath and bounded retention

Snapshots and WAL retain the exact frontier revision, completed/pending work,
resources, lease/effect boundary and relevant physical deltas. A physically
started unit without a durable matching postcondition becomes locally
`UNKNOWN_AFTER_RESTART` until naturally loaded inspection; unrelated frontier
units and regions may continue. Recovery never replays a confirmed unit,
repaints an ambiguous cell or rolls back external custody/damage.

Terminal frontiers compact only after active/unknown physical work, resource
claims and aftermath references close. The retained summary preserves source,
extent, direction, confirmed cost and result without server-lifetime growth.
A fresh-world format cut rejects obsolete frontier bytes rather than guessing
unit order or completion.

## MAT-008 M2 evidence boundary

Closure requires:

- pure and codec coverage for deterministic bounded work selection, monotonic
  progress/retreat, exact per-unit resource accounting, COLD continuation,
  stale/duplicate observations, switching and recovery;
- focused production-owner evidence for ordinary crop, infection and root
  progress plus player removal/counter-action, blocked or damaged unit, demand
  loss and graceful/abrupt recovery;
- the smallest candidate-bound ordinary-player native lifecycle set showing
  more than before/after states for each applicable family, one intervention
  changing the same frontier, neutral leave/return and terminal semantic facts.
  The retained set also exercises a multi-chunk envelope from at least two
  materially different ingress directions and after restart, including a
  pending or conflicting sibling unit;
- player-height frames of active direction and partial state as M2 presence
  evidence, never automatic M3 comprehension; and
- one coherent final critical integration gate after changed local lanes pass.

Existing F0 ownership/recovery, MAT-001 harvest, MAT-003 decontamination,
MAT-007 metabolism/morphogenesis and physical-aftermath evidence may compose
unchanged. Fixtures may admit a due frontier but cannot inject the asserted
spatial progress or player consequence. A genuinely instantaneous explosion
remains an `ATOMIC_INTENT`; it does not need fake incremental animation. No
standalone infrastructure proof, native matrix or repeated confidence run is
required.

This target closes `MAT-008` at M2 only. It excludes M3/unbriefed direction and
meaning comprehension, final art, natural-terrain release hardening, complete
decision-to-consequence coverage (`MAT-009`), resident symptom presentation
(`MAT-010`), annual balance, deployment, production cutover and v2 removal.
