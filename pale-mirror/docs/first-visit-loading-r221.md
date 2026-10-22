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

## Architectural next step — approved 2026-10-22

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

The user approved this ownership/recovery change and requested an active goal.
Retest first settlement/mine construction, COLD->HOT and revisit separately;
retain visible-target/packet timings and investigate the recorded incident.

### Implementation / acceptance boundary (R222)

- `compileInitialSettlementFabric` derives only initial buildings and roads
  from immutable bootstrap, using the same semantic compiler as runtime.
- `FrontierV3GrayboxGenesisFeature` places chunk-local geology and initial
  settlement fabric during generation. Serializable chunk attachments retain
  provider/configuration/seed and complete semantic cell ownership. It never
  reads the running simulation, creates inventory or advances jobs.
- Authored initial mine access (stairs, clearance, platforms) uses a separate
  typed worksite generation receipt. The container socket remains a runtime
  ownership handoff. Later mine development is not moved into generation.
- The block ledger overlays later loss/repair witnesses over that immutable
  provenance; observing air where a generated wall stood is not permission to
  recreate it. Runtime construction and other future COLD mutations remain
  separate owner-controlled current-state reconciliation, not history replay.
- One bounded read-only ingress lane shares the existing per-chunk cursor. It
  can confirm generated cells while unrelated WAL work waits. Dynamic field,
  container and worksite handoff still gates admission; no unsafe partial image.
- The first native run found a connected liveness defect: mine socket remained
  PREPARED/AIR while navigation repeatedly published WRITE_PENDING, then observed
  its preimage before the container writer retried. Static preparation had
  applied zero blocks and was already current. Evidence:
  `build/r222-genesis-recovery.{log,json}` and query at revision1490/1878.
  Shared planned-write preparation now offers an exact post-durability producer
  continuation. Container ownership revalidates its current PREPARED surface and
  native socket there, before an observation pass can undo that write fence.
- The same fence/preimage loop was confirmed in mine worksite projection and
  initial fields; all three now resume through the shared continuation port.
  Continuations revalidate retained ownership and physical preimages and share
  one native-turn mutation budget. Only positions in the prepared write batch
  may consume that permit. The immutable ruleset digest is not recalculated
  per observed block.
- A connected restart defect was traced to unstarted ambient admission:
  asynchronous DRAINING publication lost the cancellation suffix, and no body
  existed to enter the ordinary release path. Exact lease/absence-checked
  resumption closes that cancellation and releases unstarted body authority.
  This does not authorize recreating an absent formerly running body.
- The R221 retained incident is identified: `AMBIENT_LEASE_RESTART_ABSENCE`,
  `bioform:west-1`, first instant77504, at -340/64/340. It is not a settlement
  static-generation conflict. Read-only saved-state evidence:
  `build/r222-live-incident.log`. It remains unclosed, outside this initial
  settlement/mine construction migration; do not label that old world clean.

Checked-in `disposable-genesis-ingress-recovery` passed on a fresh seed125 world,
lite native client, run071375b1-000a-481f-930b-2a94ae894b7a:

- Mine: target rendered3.217s after visit; neighbouring mine handoff current4.369s.
  Linked screenshot captured5.303s after visit (includes camera/capture barrier).
- Previously unvisited settlement8: target rendered2.257s; farm and depot section
  checks2.458s; linked screenshot3.346s. All neighbouring packets received.
- Ordinary player broke a mine source and generated farm wall, left to COLD,
  restarted gracefully and revisited. Both remained removed, canonical loss
  retained, quarry chest ACTIVE/OWNED; zero required/scene/inventory conflicts.
  One preparing replica scope at final immediate mine re-entry is not a claim
  that every background preparation has drained.
- All three frames inspected: mine coal/stone/access/container and settlement
  surfaces visible; wall hole survives restart. No whole-game/economic soak claim.

Receipt: `build/r222-genesis-current.{json,log,pmv3.jsonl}`. Before this repair,
the same generated-world scenario hung specifically on field_initialization
(`r222-genesis-complete`); the static layer was already current with0writes.
Full-pack live acceptance is still OPEN. New worlds are necessary to measure generation;
old already-generated chunks cannot retroactively acquire initial provenance.

Verification before packaging: generated provenance/overlay, field budget,
ambient recovery and runtime tests passed. Native shared-storage48/48 and
adapter-mirrors12/12 passed (`r222-native-storage`, `r222-native-structures`).
Integration `guardrails check`: domain324/324 and NeoForge821/821 passed;
the wider frontier suite was still running when this source checkpoint was made.
No claim of an all-family native aggregate or inactive combat/hive acceptance.

First full-pack live run61a3afbb-b08c-4c43-9c12-97d6f6921070 on31439cfc:
Clearwater visible4.300s; mine source visible5.098s/neighbor current5.199s;
reported southern settlement visible2.199s. All frames inspected, final retained
conflicts0/preparing scopes0, client disconnected. Five-second target NOT met for
the mine. Full frontier unit suite was concurrently consuming one worker's CPU;
do not claim a controlled speedup ratio from this sample.

Source review found a remaining scheduling proxy in WorksiteHandoff: nonempty
projection queue returned WAITING before reading exact physical evidence. That
queue also schedules custody admission for already-generated current cells. The
handoff now uses its existing exact per-cell witness/revision/postimage checks
regardless of queue membership. It does not complete queue entries or acquire
mutation custody; the worksite owner retains that work. Prepared, changed, absent
or externally modified evidence follows the same waiting/conflict checks as before.
