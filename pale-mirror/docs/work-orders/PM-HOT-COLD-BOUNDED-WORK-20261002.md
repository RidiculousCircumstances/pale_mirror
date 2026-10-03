# HOT/COLD bounded work and pedestrian contention

Accepted by the user on 2026-10-02. Main implements alone; no subagents.
Active source: `/home/rd/proj/pm-f06r3-facility-lane-recovery`.

## Outcome

Remove source-proven unnecessary work without changing world semantics:

1. Ordinary revision/time/continuation queries must not serialize a checkpoint.
2. Canonical reference validation must become dependency-aware, preserving the
   complete equivalent recovery audit and atomic rejection before WAL publication.
3. Chunk handoff must use affected-owner/cell indexes rather than global scans;
   any reused physical proof must be invalidated by actual physical changes.
4. Bound discovery/retry work as well as mutation count. Container observation
   and deferred chunk preparation must fairly progress without auditing every
   unchanged subject on every turn or hiding recovery obligations.
5. Advance empty COLD intervals to the next causal boundary, preserving due order,
   newly scheduled work, budget/backlog, physical safety and periodic checkpoints.

User also reports a baker carrying grain stops against another resident instead
of walking around. Trace and repair shared HOT pedestrian navigation, not bakery
logic: moving bodies are transient local collision evidence, never permanent
COLD terrain. Retain the same actor, goal, cargo and hard spatial scope; no
teleport, task completion by proximity, or force-loading.

## Verification

Static source contradictions justify changes. Extend focused existing coverage
for snapshot-free reads, reference retirement/recovery, indexed handoff,
fair deferred work, causal interval equivalence and native pedestrian contention.
Run relevant checks once after a coherent change; no broad matrix/profiling
campaign or numeric speed claim without a discriminating need. Player-visible
avoidance requires physical evidence. Build/source verification is not a
deployment or HUMAN_CANDIDATE claim. Preserve current live incident/world.

## Status

Scoped implementation is in the active checkout as uncommitted WIP. Focused
automated checks cover snapshot-free reads, dependency-aware relationships,
indexed handoff, fair discovery, COLD interval/recovery equivalence and native
pedestrian avoidance. Receipts belong in the canonical continuity ledger.
This is not release or graphical acceptance, and the live server is unchanged.

Deliberate safety limits: scheduled-subject validation still audits the complete
queue; physical handoff proofs are observed afresh, not cached by canonical
revision. Deferred preparation is bounded per turn, not yet an event-driven
physical dirty queue. No numeric live speedup is claimed. The pre-existing
codec-size debt failure was resolved by extracting replica/custody serialization
without a format or ceiling change; full guardrails now pass.
