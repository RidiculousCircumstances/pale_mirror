# Runtime performance: five connected corrections

Scope: main executor alone; current quarry graybox, shared scheduler/storage and
observational diagnostics. Preserve causal order, physical custody, canonical
clocks and durable-before-effect. No speculative remedy for historical isolated
overload warnings, no parallel world mutation, no infrastructure proof campaign.

## Design and responsibility

1. `ReconsiderationRequested` is a new explicit kernel payload, admitted only by
   closed planner capabilities for settlement stock and trade opportunities.
   Producers still persist every causal request. The atomic queue overlay keeps
   one pending exact owner/kind review at its earliest deadline; consumption
   precedes requests emitted by that review, so its invalidations retain a
   successor. Ordinary Created facts, progress, transfers and receipts remain
   exact, separate operations. Owner/kind capability checks apply live and replay.
   Stock policy reads current state at current execution time, not an old hint's
   deadline. Recurring backstop cadence and domain clocks are unchanged.
2. Fresh quarry ruleset `frontier-v3-quarry-graybox-r2` hashes execution bounds:
   at most64 actions and128 weight per canonical turn; declared policy reviews
   cost at least8, other actions retain their weights. Same budget in ordinary
   and operator advancement, same due/priority/subject/id order. No wall-clock
   admission decisions in the domain. Older selectors retain their old digest;
   state schema267 explicitly rejects old disposable saves. This is a service
   ceiling, not a promise to execute64 costly reviews or a claimed TPS speedup.
3. `ExtractionGeometryIndex` derives stable cells/regions/chunks once from exact
   immutable site declarations. Depletion/worker/custody progress reuses it;
   replaced declarations rebuild it. Native worksite projection/departure query
   this view rather than repartitioning1536 cells repeatedly. Loaded-world and
   current authority/effect checks still apply. No forced chunk loads or cached
   physical postcondition.
4. `FrontierFileStore` retains an owned mutable tail and identity indexes after
   verified recovery. Append validates only the new record, eliminating a full
   accumulated-tail copy/audit per transaction. Read/checkpoint boundaries
   materialize immutable completely validated RecoveryImage; restart still
   validates the disk prefix, checksums and sequence. Existing force grouping,
   turn-exit barrier and immediate physical-effect fence are unchanged. This is
   not asynchronous canonical WAL or a change in failure semantics.
5. Diagnostics publish a timestamped ready/held/future cut. Ready wait excludes
   parked time; original deadline age remains separately visible. Stale owner
   rows have null current values and explicit last-observed values; ordering is
   by latest observation rather than lifetime worst lag. Host-turn timing
   includes PM persistence exit, and Pre-through-PM-Post timing includes native
   work that Minecraft's earlier MSPT tally omits. This is not timing of later
   Post listeners or idle sleep. Recent bounded64-sample windows remain
   observational, outside snapshots/WAL and scheduling policy.

## Acceptance and finite verification

Focused queue/engine tests cover atomic merge, exact facts, next-generation wake,
identity/cost rejection, codec/replay, weighted stable order and held-time
telemetry. Actual366-resident initial composition must drain its initial review
cohort within10 canonical turns under the new rules without quarantine. Storage
checks cover immutable read cuts, invalid append rejection and forced recovery.
Geometry checks cover reuse after depletion and replacement on declaration
change. Relevant native tests and one connected ordinary HOT/COLD/restart check
verify current integration, followed by safe fresh-world deployment.

Report measured work reductions separately from wall-time gains. A larger
admission ceiling does not itself prove lower server latency; use current host
metrics and comparable workloads for any numerical performance claim. No claim
that two old isolated2s warnings were reproduced or fixed.

## Verification and delivery receipt

Clean detached candidate28db4de786d2521b8a0750270ff4c3966ad05023,
tree13a2f4cfa393db7479f04c3860bcc6371a4919f7 in
`/home/rd/proj/pm-runtime-performance-r85-release-20261010`.
Private local release ref only; working branch/index preserved, no remote push.
Artifact SHA512:
`29c4ac1a610bbdbc39bc83ce6a331d8d1f1fdf33446c3f4ca9717cbb8399e59e899bfad1360579e24ae9a9b461faba26e6e6de1bdfbf582d812d0e6f73d0a29a`.

Selected domain146tests0failure0skip, including architecture, exact queue/engine,
persistence codec, ruleset actual cohort, state/process catalogue, activity,
stock/trade and extraction geometry. Native v3 unit618tests0failure1existing
skip; harvest-support3 GameTest PASS. `:check`, packaged JAR verification and
native build PASS. Original broad native launch was interrupted because its
worker entered unchanged old reference-model annual simulation; that report is
not green. Final scoped native gates PASS1m43s. Logs:
`/tmp/pm-runtime-performance-r85-release-gates.log` (interrupted broad build,
successful domain reports retained) and
`/tmp/pm-runtime-performance-r85-native-gates.log` (final native/build/static).
No confidence rerun of unrelated adapters, visuals, hive or yearly matrices.

Exact published/installed core passed source-ref/JAR/new-world preflight and
live post-start verifier. New disposable world
`frontier-v3-runtime-performance-r85-20261010`, seed20260918065, quarry-graybox-r2,
state267. Old R84 world retained; no deletion, only required datapacks copied.
Initial live invocation3ac58608e3fb414783e1976868220563, wrapper2404038,
start1791617009. Unchanged Visuals SHA512 starts2fd468cb382d64fa.

Graphical ordinary full-pack20FPS scenario13 actions PASS, run
`3af44d5d-a941-4ee7-b9a6-b926c9232811`; candidate Gradle root
`build/runtime-performance-live-final-result.json` and companion JSONL. Main
reviewed both `hot-current-quarry` and `cold-progress-on-hot-return` PNGs.
It verifies current quarry container/source projection, natural release to
COLD, ordinary operator advancement, actual AIR source on HOT return and
green summary. It does not prove all distant chunk presentation, every trade
mission, a long-duration workload, co-op or player comprehension. Initial test
attempt mistakenly released an absolute hold after relative advancement and
failed only that carrier step. Next launch failed before connection in FML
early-window handoff. Corrected the task-local scenario and disabled only the
generated pilot's intermediate FML window; same source/artifact/server, no
domain, oracle or physical outcome override. Logs retained separately.

Early empty live cut tick374: ready0/held12/future807, ready-lag0; lifetime
budget lag4ticks. Recent64 PM turns total134.59ms/max12.77ms; initial PM max807ms
is retained, not hidden. Later cut tick4288: ready0/held6/future816;
recent64 PM turns total480.06ms/max29.15ms. Relative2400tick request reached
its exact3586 target,257 slices; summed slice work3.414s (not wall latency).
These are current measurements, not comparable before/after TPS speedup.

Same-world graceful recovery PASS. Before stop tick12584/revision14212,
366 residents and all required/inventory/scene/custody diagnostics0. Confirmed
`save-all flush`, then RCON `stop`; all dimensions saved and port closed.
Same artifact/world restarted at1791617449, invocation
`b02b6a08c9b84650a86924605da045ce`, wrapper2420189. Post-start verifier PASS;
recovered then advanced to tick13259/revision15325,366 residents/49bioforms,
all required/scene/inventory/custody diagnostics0 and no quarantine. Tick13260
pressure ready0/held9/future820; missed-wake audit0. Recent64 PM turns
total570.73ms/max35.95ms; native-through-PM-Post max36.25ms. Startup/recovery
PM maximum1.182s remains observable and is not declared solved.
Task-owned client and GameTest JVMs exited; no background graphical observer.
Canonical governance and source architecture maps carry the same new flow;
both diffs pass whitespace checks. Implementation own WIP retained (branch
5455bb5 unchanged); original nested source23 unrelated paths and outer pack's
unrelated `.f0v-baseline/` preserved. No user branch commit or remote push.

All five scoped corrections are integrated, verified and deployed. This closes
the implementation goal, not a claim that every historical latency spike or
future stress workload is resolved.

## Authorized publication

User subsequently requested commit and push. Implementation committed as
`15fdec7ea375a0abaaac1b621f2cc00ae371d703` and pushed to
`origin/feat/baker-carry-orders-20260926`. Its source tree exactly matches the
verified R85 candidate `13a2f4cfa393db7479f04c3860bcc6371a4919f7`;
no rebuild or server restart was needed. Working implementation is clean.
This receipt, current ledger and only the new performance documentation hunks
are published separately on governance `main`; unrelated earlier WIP is excluded.
