# Pedestrian passages and partial contacts — 2026-11-05

The accepted repair is shared navigation for ordinary passages and departure from
fractional furniture contacts. The original diagnosis is in
[the investigation](frontier-v3-bakery-guard-navigation-investigation-20261105.md).

## Delivered responsibility boundary

Building spatial annotations contribute ordinary floors/circulation and obstacle
supports. `KnownPedestrianPreferences` compiles one immutable projection per
bootstrap. `PedestrianTraversalPolicy` owns the soft support cost. Its balance
comes from `FrontierRuleset.pedestrianTraversal()` and participates in the
ruleset content hash, following existing locomotion/evasion selector storage.
The initial obstacle penalty is12, ordinary cost1. Different costs do not grant
collision clearance, access or body authority. No role-specific bakery routing.

COLD local A*, regional refinement, incremental goal fields and portal scoring
consume the same traversal cost. Unit-cost fields retain the cheap unweighted
path. HOT native bounded expansion uses the same policy, including native path
alternatives and rejecting a penalized hint as an automatic shortcut. Combat
maneuver and patrol destination ranking read that policy rather than inventing
obstacles. Crossing furniture remains possible when the usable route requires it.

When the centred pose on a partial support is obstructed, common body geometry
now enumerates a bounded footprint/contact set (maximum24 additional poses).
Existing connected contact components and swept collision checks prove the
edges. Semantic work stations still require a clear centre. The native actuator
starts from the actual body and proves its physical path; no teleport, false
arrival or new canonical XYZ writer was added. This addresses the reproduced
Nora Bell overhanging stair-seat pose rather than banning stairs or seating.

## Additional real deployment failure repaired

The first navigation deployment on the existing world reached a retained bread
job whose one-shot progress review had been parked during movement. Resume
called strict `Rescheduled` for an absent schedule and quarantined the world at
simulation106324. `ProductionContinuation.wake` now requests reconsideration at
the exact job address. The registered production progress kind explicitly
accepts this coalesced review and scheduled recovery preserves that capability.
Other strict cancel/reschedule operations remain strict. A movement/continuation
integration case advances to actual deadlines, proves one review after arrival,
and verifies that waking does not consume inputs or replace the job.

On the same world after this fix, runtime resumed, Nora left her seat in COLD,
ate and returned to guard movement. No new quarantine occurred. The one retained
required diagnostic incident is the historical failure at106323–106324, not a
new navigation conflict; the other retained incident is a resolved production
wait at81745. Historical diagnostic records were not erased to claim a green run.

## Verification and limits

- Source commit f8d1b657c: shared routes/contact repair. Full native439 and
  NeoForge920 passed. Full Frontier2331 had one obsolete ruleset whitelist;
  adding the actually installed terrain-r31 restored the focused dependent76.
  Guardrails, affected navigation and package/build checks passed.
- Source commit ad26a8971: exact production wake/recovery. Affected42 tests and
  guardrails/package checks passed. Same-world checksum6be2311e2061… deployed and
  remained active through client checks and normal all-dimensions save14:24:48.
- Source commit443f12bff: hashed traversal balance and declarative player setup.
  Affected40 tests (ruleset recovery, geometry, goal fields and station movement)
  passed. Final local native navigation48/48 passed with the same initial cost.
- A mistaken aggregate native invocation exposed one unrelated legacy test
  requiring HOT-body admission by accelerated tick121. Its own adjacent helper
  already waits for actual asynchronous readiness. The body-loss test was
  converted to that same finite15-second semantic wait, retaining actual body
  removal and exact blocked-reason assertions. The aggregate was stopped after
  classification; it is not claimed as a new all-green full-suite run.

A real graphical full-pack20FPS pilot followed passages to the courtyard,
observed Nora around the table, changed player position and received guard
strikes. Receipt `build/walk-preference/courtyard-full-pack-passages.json`, run
6655ddd9-5c3a-4c08-a522-890dd42b8208. Two distinct frames were opened/reviewed.
The test player's knockback was disabled during this exploratory run to let its
simple waypoint walker complete the route; the checked-in scenario now declares
that setup explicitly. Native exact-seat departure does not alter knockback.
This is scoped exploratory client evidence, not formal whole-life semantic
progress/human acceptance. A final replay was stopped because Nora had patrolled
to a different distant post: the fixed player waypoints no longer described the
same courtyard pursuit fixture. No actor was teleported or repaired to force a pass.

Earlier invalid player methods (teleport beyond pursuit leash; straight waypoint
through a building wall) are method failures, not successful pursuit evidence.
Completed task traces3109… and0950… were archived reversibly to free capture
capacity; the original user tracecd22… remains at its original path.

## Delivery

Final release4c0d368ce, native patrol4/4, and fresh-world deploy verifier passed.
Runtime wrapper4016602/start1793870972. SHA5126d9adf16fa773f10…; complete checksum
and selected world are recorded in the active continuity ledger. Initial native
summary at874/revision1330 is green with zero retained conflicts/incident index. Wire schema336 remains; the traversal rules
change the world content hash, so the final release uses a fresh seed139 world.
The previous world is retained for the exact stall/crash evidence.

IMPL and RELEASE are independently checked; PACK has no tracked changes and
preserves its pre-existing untracked `.f0v-baseline/`. Hosted artifacts are
published/installed through PACK scripts. No push: supplied building assets
remain private under their existing redistribution restriction. Task client,
private display and temporary operator privilege are removed after inspection.
No broad economy, combat balance or TPS improvement is claimed by this repair.
