# Continuity Ledger

## Assignment / authority
R214 (2026-10-22): optimize first settlement loading, preserve physical handoff
and durability, verify player visits, commit and deploy. Implementation and live
verification completed; recording final checkpoint. Main alone, no subagents,
no push. No system goal created this turn. Do not restart historical work.
Workflow: AGENTS.md and docs/engineering-agent-protocol.md.
Prior storage integration: docs/frontier-v3-storage-operation-integration.md;
history: docs/archive/continuity-r213-before-field-causal-closure-20261022.md.

## Locations / current deployment
Implementation /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926. Git root parent; tracked pale-mirror/.
Production source4d381880a72f012f0b7a71fd1871ae5d243b8c9d; final documentation/
scenario checkpoint does not change deployed production code.
Clean detached release /home/rd/proj/pm-r214-release.pVis1Y/source/pale-mirror.
Pack /home/rd/proj/minecraft tracked clean; preexisting .f0v-baseline/ untracked.
Governance has extensive unrelated WIP; never stage wholesale. Ledger and three
R214 architecture additions are this task's governance changes.

Live /home/rd/far-frontier-server, far-frontier-v3-live.service, port25565,
wrapperPID3463213, verified startEpoch1792650277. World
frontier-v3-r214-batch-ingress-20261022, seed125/r22, fullpack Java22,
C2ME combined8workers/8loads, heap4–12GiB, view8/simulation6.
Installed and hosted PM SHA512:
9ed28382bf0e85304b568d06bd5f1525dfb96f15f0b5d1d88b6c65b0a04232751ff587b485aa5f48188330a9d9b9a40e8fd9ea2d6314a4281bee49fc576706aa.
Native graceful stop11:23 completed all-dimension saves/C2ME shutdown;
same-world restart and fresh deploy-verify passed. Earlier diagnostic worlds
preserved, none deleted this optimization. Client and private Xvfb117 stopped.
No live fast-forward, background scenario or performance capture remains.

## Implemented causes and changes
- d99e17dd: per-connection native pending index, <=64 fresh/fair retry probes;
  actual readiness, native quotas/acks and exact cleanup preserved. Handoff moves
  into bounded nearest/fair preparation; classified dynamic conflicts remain
  visible but never authorize work. Fixed-container window8 skips deferred
  first-ID sockets. Observer no longer truncates after64 records.
  Measurement19.6s/11.1s: queue bounds alone did NOT improve latency.
- 507a9f44: JFR173/419 server execution samples in global service-turnover
  geometry. Conservative declaration-only column discovery narrows exact current
  geometry to nearby candidates. Shared waiting/egress parameters, exact heights,
  stable precedence and retirement checks retained. Authored exclusion overlays
  retained independently of terrain; UNKNOWN-only fences avoid redundant
  geometry resolution. Follow-up16.3s/11.2s, not sufficient alone.
- 4d381880: field timeline ~2 writes per canonical tick; JFR traced individual
  block writes into durable navigation commit/flush. Common planned-write windows
  prepare bounded durable invalidation groups including contextual dependents.
  Every actual mutation still revalidates its fence; batch is NOT permission.
  Worksite projection uses same entry. Field effect clock starts once after
  bounded durable preparation; shared per-tick count/time ceilings preserved.
  No stale terrain transmission, force-loading or weakened recovery barriers.

## Verification / measured results
Durable details: implementation docs/performance-operations.md R214.
- R213 original fresh visits19.3s/11.1s; warm restart1.4s/0.1s. Different cases.
- Current fresh run3e461bc0-d1c4-4053-afd5-ea07594e9cce,
  build/r214-batch-storage.json/log: first Clearwater6.5s; Ironmeadow target0.9s;
  return0.1s. Second target receipt NOT full rendering: immediate frame still
  missing surrounding terrain. All3 GUI frames inspected; final conflicts0.
- Restart/render scenario live-settlement-ingress-render.json, run
  ca0270a7-cfd0-43b2-9c8b-b6b92eeea236, build/r214-render-restart.json/log PASS.
  Prepared Clearwater4.8s; Ironmeadow target0.2s,3x3 packets/centre mesh~2s;
  new third settlement target0.9s,3x3 packets/centre mesh~4s. Neighbour meshes
  compile longer on llvmpipe20FPS. All3 frames inspected: terrain/buildings
  visible. Capture waits are observation windows, not render-time benchmarks.
  Final revision5920/instant4153,389 residents,0 required/inventory/scene/custody
  conflicts. Not full economy/progress or manual M3 acceptance.
- Focused domain51 and NeoForge15 tests passed; guardrails/package passed in
  clean detached release. Native navigation42/42 and field19/19 PASS including
  shape validation and partial-write recovery. Logs r214-batch-native-navigation,
  r214-batch-native-fields, r214-batch-detached in build/.
- Earlier d99 native395/395 and full NeoForge completed. Aggregate check was
  deliberately interrupted in unchanged economics (~6min), NOT aggregate green.
  R213 unaffected acceptance retained, no redundant full campaign needed.
- JFRs build/r214-live-ingress.jfr, r214-local-ingress.jfr,
  r214-batch-ingress.jfr; print --stack-depth64. Samples are not wall time.

## Remaining limits / retained context
Initial loading improved, not instantaneous. Ingress still produced server lag
peaks2.1–2.3s; first fresh entry6.5s and software-client mesh compilation remain
observed costs. No universal speedup, all-view-radius readiness or x30 scale
claim. No additional defect/quarantine found in bounded visits. Never inflate
0.9s target receipt into complete scene rendering.

R213 retained shared WAL/lazy BLOCKS64-section cache, exact native effect envelope,
owner outbox/aftermath and compaction barriers. Actual saved-player-withdrawal
crash/restart recovery accepted previously; not a global Minecraft ACID claim.
Inactive medical/combat/hive not activated. Unexplained mining-tool loss remains
historical unresolved evidence, not permission for speculative repair.

BuildJava21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
liveJava22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Pilot fullpack /home/rd/proj/pm-r115-client-pack.OWzKjN,20FPS/privateXvfb117.
Retained read skills: performance/evidence, release/matrix, serverops/operations,
engine-source, client-investigation; protocol/execution semantics read.
