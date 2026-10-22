# First-visit loading: R221

Acceptance: an actually received and rendered settlement/mine on first visit in
under five seconds. A warm revisit, central source cell, server-only READY flag,
client JVM startup or a failed exact-feet fixture is not that measurement.

## Confirmed observations

- R220 reported settlement readiness took approximately 28 seconds. Subsequent
  target-chest rendering still took 9.6 seconds on one warm visit.
- R221 quarry at 403/-339: central packet approximately 6 seconds after teleport,
  central section compiled approximately 8 seconds. The visit action itself
  timed out because the requested Y64 fell to Y61; its 120 seconds are invalid
  as a loading measurement.
- Settlement3 at 140/-340: central packet approximately 4 seconds; neighbouring
  packets and first central mesh approximately 16 seconds. During that interval
  the field was current, but adjacent chunks remained static PENDING.
- Diagnostic build a217537c: previously unvisited settlement9 at -340/340 took
  about 5 seconds for the central packet, with neighbouring static preparation
  continuing much longer. Between 13:03:52Z and 13:03:54Z preparation admissions
  and applied-cell counters remained unchanged while the server kept ticking.

Evidence (ignored local build outputs): r220-live-audit, r220-render-target,
r221-ingress-baseline, r221-ingress-owner, r221-static-owner manifests/logs;
r221-static-center-samples.jsonl; profiles/r221-ingress-baseline.jfr.
The neighbouring RCON sampler timed out and is partial, not a completed trace.

## Confirmed scheduling defect

`FrontierV3PreparationAdmission.begin` advanced its preferred owner every native
tick, even if the physical stage continuation executed only observations or a
suffix after the projection stage. `FrontierV3CooperativeStageCursor` correctly
retains that suffix across a pending canonical commit. The unrelated rotation
could therefore repeatedly skip the same projection owner at its actual visit.

Deterministic reproducer: observation yields; next turn reaches structures and
fields, then yields; third turn reaches worksites and finishes. With the old
rotation structures never receives admission. No wall-clock threshold, sleeping
or throughput assertion is needed to prove this liveness defect.

Fix: advance preference only on an admitted request or an actually encountered
owner with no preparation request (disabled/recovery-only). A denied request
keeps its preference. The registry reports each completed preparation owner;
unrelated ticks cannot consume its turn. Ordered execution, asynchronous WAL
fences and existing physical work bounds remain unchanged.

## Acceptance status

Fix committed/deployed as bfa33068. Focused admission/continuation/window checks
6/6, guardrails, clean-detached JAR/packaging, preflight and deployment verification
pass. Same retained R220 world, restart1792674491, source gameplay changed only
in preparation scheduling.

Live first visit to settlement8 at380/0 (`r221-fairness-live`): teleport18:08:54,
central packet18:08:58, all central-neighbour receipts/first central mesh18:09:05.
The target is **not met** (~11s). Different settlement/state from the prior run:
do not claim a controlled 16->11s speedup. Existing settlement9 login was~3s,
which is not first construction evidence. Parallel RCON samplers timed out
before the visit and are not usable attribution for this interval. Native client
finished and was closed. Its terminal summary has one retained required incident
and one preparing replica scope (no scene/inventory conflicts); not yet attributed,
and must not be called a clean acceptance run. Current server remains ACTIVE.

## Proposed architectural next step — requires user agreement

Do not just multiply the tick budget. Static first construction is still a
post-load simulation-stage task, sharing cadence with durable physical work;
neighbour chunks withheld by that task prevent the client's chunk mesh from
becoming visible even after its own packet arrived.

Propose moving the immutable initial roads/building baseline into chunk
generation, extending the existing generated-geology baseline integration.
The generator owns initial blocks/provenance, runtime owners own subsequent
changes and never repair player damage from that baseline. Dynamic field and
container state still needs exact current projection and recovery before usable
handoff; do not replay historical work or expose stale inventories to achieve
a timing number. A bounded ingress pipeline should prioritize the player's
render dependency neighbourhood, with explicit per-chunk completion rather than
waiting for a whole gameplay-stage rotation. No duplicate write authority,
weakened durable-before-effect rule or second navigation truth is proposed.

This changes ownership of initial construction and its recovery boundary, so it
has **not been implemented** pending the user's requested architecture agreement.
Retest first settlement/mine construction, COLD->HOT and revisit separately;
retain visible-target/packet timings and investigate the recorded incident.
