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

Focused admission/continuation/window checks pass. Live latency after this fix
has not yet been measured. This is not a claim that the five-second target is
met or that every latency contribution has been fixed.
