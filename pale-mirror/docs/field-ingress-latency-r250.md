# Field ingress latency investigation (R250)

The earlier R249 report established an approximately19s first ingress, but did not
attribute it to native generation. That attribution is withdrawn. An actual return
login to the already-created field7 on the same R249 world also waited about35s.
At the retained sample the native chunk was loaded and STATIC_CURRENT, but field
handoff was WAITING:field_projection_or_observation, with160 mismatched cells.
See build/r250-login-handoff.txt and r250-first-area.log.

This is waiting for our materialization, not a35s continuous server-thread stall.
The bounded JFR build/r250-first-area.jfr (task-owned JVM110412) includes that login.
The server retained roughly20 native TPS; cumulative native-tick peak at this stage
was81.64ms. Much of the captured server waiting was ordinary Minecraft waitForTasks.
The capture is not a matched before/after benchmark or proof of no individual CPU cost.

An unvisited field9 then needed35s for its receiving chunk; after another30s the
render assertion failed. Actual farmland had arrived, but Sodium's section was not
compiled. Neighbour -92,92 still reported waits for the primary field and two
cultivation fields. One target's presentationReady is insufficient visual evidence.
Receipt build/r250-first-area.json / log; build/r250-final-blockers.txt. The separately
polled handoff JSONL had overlapping shell writers and is incomplete/corrupted; do
not use it as a complete causal timeline.

Source review found the8-cell projection cap unchanged since bf2a23b0bc
(2026-09-17). It remained on current-image catch-up after fields became larger and
dynamic. R245 correctly added exact pre-attempt native preparation, but catch-up
still performed that integration independently for cells instead of declaring a
whole exact planned window through the existing managed-write preparation owner.
Initial/static projection already used that shared mechanism. Each durable phase
and cooperative physical pass adds turns to the small-window backlog; the renderer
then waits on all required neighbouring chunks too.

The changed path declares the next exact pending cell writes as one bounded managed
window. Individual preconditions, durable operation permissions and outcomes still
run for every cell. Actual native effects use the existing shared256-count/5ms turn
budget, admitted after durable preparation and before beginAttempt. A capacity wait
spends no attempt, and an asynchronous fence spends no effect budget. Preparation
and sparse discovery are bounded by the same256-cell window, with fair continuation
for larger fields. Biological crop growth, worker processing time, yield, custody,
foreign-block rules and unknown-outcome recovery are unchanged. The obsolete test
assertion equating catch-up projection with the8-cell work cadence was removed;
existing sparse/fairness and deterministic admission checks were updated.

Verification so far: focused9 adapter tests passed; guardrails and50 native shared
field/storage/body tests passed (build/r250-field-tests.log,
build/r250-shared-native.log). The full adapter run and real rendered-ingress
measurement are still pending. This is a candidate correction of the confirmed
small-window/per-cell-fence path, not yet a claim that all latency or the missing
neighbour has been fixed. Existing world and diagnostic evidence are retained.

## Follow-up after the first candidate

R250 deployment f4119e657 passed clean-source preflight and startup verification
(build/r250-verify.log); same world retained. The full adapter run was intentionally
cancelled while it exercised the unchanged legacy
SourceGrayboxPresentationPlanTest.everySourceClaimRemainsSpatiallyDistinctThroughoutTheSeededFirstYear;
it is not counted as passed. The applicable9 focused checks and50 native shared
checks remain the executed evidence for that candidate.

The repeated field9 visit became visible, but its neighbouring cultivated field had
an old unresolved prepared operation after restart (ticket
d7ca9916-a235-4466-b827-2e47e1e7cee2, cause revision1835127, before this deployment).
The handoff correctly made that historical uncertainty presentable as a conflict.
That quick visit therefore is not accepted as proof of faster normal catch-up or a
healthy field9. The old operation was not replayed or erased.

Exploratory first visits to other untouched areas passed actual compiled-terrain
assertions: field3 took10.300s (r250-north-ingress.json); field5 took7.400s
(r250-west-ingress.json). These are different areas, not a matched speedup ratio,
and neither satisfies the5s target. Both linked images require normal review;
no overall completion claim follows from merely successful scenario status.

The field5 timeline (r250-west-timeline.jsonl, single writer) showed initial cursor
201→256, a roughly4s prepared pause at256/512, then512/activation. Another concrete
source defect was found: navigation preparation accepted a producer continuation,
but the extraction/geology owner discarded that continuation after its asynchronous
canonical durability. The producer could only be rediscovered by a later full pass.
The shared mutation-owner port now passes the continuation to every owner; geology
registers it against its exact pending command and resumes only after durability.
Immediate acceptance continues normally, predecessor waits do not invent a callback,
and no effects or field completion are granted by a notification.

The existing deferred-geology native test now requires an empty environmental turn
to resume the exact operation after the held WAL completes, without manually
polling the producer again. All4 native block-preparation tests and guardrails
passed (r251-native-preparation.log). This change applies to all users of planned
managed writes, including static and worksite projection. The next real first-entry
measurement remains necessary; the target is not declared achieved yet.

## Physical suffix starvation (next correction)

R251's stronger field10 scenario passed healthy READY/zero mismatches and an inspected
complete foreground, but first ingress still took22.300s
(build/r251-first-ingress.json, run254b4db3-2f7b-4e77-a306-a1f98f3b3207).
The camera-chunk support is now checked separately before aiming at the field:
review of the earlier north/west images rejected their void foreground despite the
field target section being compiled. Those images are not full visual acceptance.

Source tracing found another connected scheduling defect. Canonical readiness only
resumed ordinary canonical work; the already-started physical execution suffix had
no equivalent resumption. New ordinary calculations/commits could repeatedly hold
that suffix until a later native tick/pass. The remaining first-field window was
prepared but not serviced promptly even while native ticks continued normally.

The correction lets the lifecycle supply a pending-physical continuation before the
next canonical quantum. A host-only physical pass retains the suffix and one elapsed
work allowance per actual native tick. Idle journal time consumes no allowance;
ready callbacks never renew that allowance or restart a completed round. Thus each
registered actor/effect stage still executes at most once per native tick. Registry
order, canonical predecessor barriers, durable permissions and the shared block-write
budget remain active. The generic canonical wake adapter receives a Runnable; it
contains no farmer, geology or world-state inference.

Deterministic clock tests cover a durable pause, suffix resumption, no duplicate
actor pass, no renewed allowance on successive receipts and carry to the next tick.
The existing order/admission/ready/registry checks are reused. Completion and fresh
first-entry latency still require live verification; no claimed improvement is inferred
from these structural changes.

## R252 deployed measurements

Source c986cf711, clean detached package build and deployment verification passed
(build/r252-{release,preflight,install,publish,verify}.log). Same aged world retained:
frontier-v3-r249-clean-20261025 / seed126 / rulesr26 / schema303. No reset, price,
subsidy, biological clock or worker-cadence change. SHA512:
71e6e10246a4e632dc372a5250e747fe86be6124162f7160181ffef60df6814af19735938834e47bfdeeb23096501864568323cd07efd4f2e78545dd3eee9ad7.

The exact affected checks passed:13 focused registry/cursor/readiness/admission/pass
unit tests, guardrails and50 shared field/storage/body native tests
(build/r252-verified-pass.log). Detached packaged-JAR verification also passed.
The previously cancelled full adapter run is not counted as a fresh full pass.

First untouched field12 (256cells): actual20FPS full-pack client visit4,799.35ms;
READY confirmation by4.900s; camera support and field target sections both checked
by5.000s including scenario dispatch gaps. The linked reviewed frame at5.500s plus
capture settling shows complete foreground and physical field. Field ACTIVE with
zero mismatches. build/r252-first-ingress.json, run
3cebf046-1f1c-4ba8-a134-0bc0c023b673. No overall strict-less-than5s visual-frame claim:
the screenshot is deliberately taken after a rendering-settle barrier.

The checked-in scenario was extended to another still-UNCLAIMED first area, field11
(64cells). build/r252-two-area-ingress.json, run
1ea90b42-90b9-44e3-add7-aafbdf647c60: repeat field12 visit0.09ms at the already-current
position (not a return-travel measurement); first field11 visit1,199.55ms, both
compiled camera/field assertions by1.700s. Its reviewed linked frame has complete
foreground/field; ACTIVE and zero mismatches. Far unloaded terrain is visible at
frame edges; these are bounded scene observations, not an all-visible-radius claim.
Both clients exited after capture; no acceleration was active.

These are different first areas, not a matched speedup ratio or a guarantee for
all settlements/mines. The main demonstrated result is removal of the22–35s wait
in these healthy first-ingress cases. Historical field9 unknown effects were not
replayed or erased. R252 polling timelines are incomplete: the first parser missed
PMV3_DIAG prefixes, the corrected polling process timed out during ingress. Use the
client manifests/trace and actual reviewed frames for these timing claims, not
those incomplete JSONL files.

## Return visit exposes another neighbour fence (R253 candidate)

The third area in build/r252-return-ingress.json (run
9dcb8a8a-b8b3-46f9-9b27-074e9688b1f1) was the already-created field7. Before entry
it had74 stale physical cells. Visit900ms and target chunk27,-2 READY by1s, but
camera27,-1 section compilation waited another5,600ms: both sections only passed
by6.700s. The actual reviewed frame is complete. This contradicts a blanket <5s
claim despite the previous two good cases, so latency acceptance remains open.
Received-neighbour mask255 identifies missing bit8: chunk28,0 (dx=+1,dz=+1), containing
container:7-depot. Its inventory recovery is a distinct barrier from current fields.
The after-unload diagnostic is not a complete in-flight trace of that interval.

Source review confirms an unnecessary coupling: exact existing ACTIVE chest with
matching declaration/provenance could expose terrain during a pending physical
effect, but identical stock mismatch during ordinary COLD-to-HOT reconciliation
returned WAITING. Its neighbouring renderer sections could not compile until the
custody discovery cycle finished. Extend the same PRESENTABLE_PENDING policy to
stock reconciliation of an exact existing ACTIVE provider. Missing/foreign or not
yet active providers still wait; conflict remains explicit. No stock, lease,
operation permission, write/replay or economic decision is changed by this read-only
handoff. Actual stock operations keep their own custody and mutation fences.

The existing real player/tool-ingress GameTest now also checks stock mismatch:
terrain is presentable, dynamic handoff is not READY, actual and canonical inventories
remain untouched, and no operational custody is granted. Removing provenance
returns WAITING. Existing click and subsequent consecutive mixed deposits still run.

## R253 live result

Source19d49af61, clean detached build/package and live preflight/install/start verify
all PASS (build/r253-{release,preflight,install,publish,verify}.log); same world/seed
retained. Native stop before install saved all dimensions. ServiceMainPID1606603,
notBefore1792901175. SHA512:
3003a4c67da1e517bbd3aa82b4ec371a2dba6845e37ac5ed58d51de2a5c770a3b0d3657a90a4be0035e74e80150f2c06953d106f1150a0fe414c968d4a07d04f.
3 HotHandoff unit tests and19 player-storage native tests plus guardrails passed
(build/r253-container-visibility.log). The native ingress test includes the new
stock mismatch/foreign provenance checks and the unchanged consecutive real clicks.

Actual20FPS full-pack client: build/r253-return-ingress.json, run
dd7fb445-2a4b-4a14-90f5-843462a4522b. End-to-end from each visit start to both
compiled camera support and field sections: field12 1.000s; field11 2.601s; field7
1.301s. All three linked frames were viewed: complete foreground and physical field,
nighttime lighting; distant terrain at scene edges is outside this bounded claim.
Each field ACTIVE with zero mismatches at its assertion. No acceleration/reset.

The in-flight neighbour28,0 diagnostic is decisive: PRESENTABLE_PENDING,
container:7-depot / container_stock_reconciliation, presentationReady=true,
dynamicReady=false. The camera actually compiled while this inventory fence remained.
The new visibility lane does not manufacture full HOT readiness. Client disconnected
and its task-owned process exited after capture.

Limit: these R253 visits are returns, not untouched generation. This launch also
logged into field7 first: native player login09:07:12.104, compiled camera/NONE UI
observed during09:07:13 (second-resolution client logger). The subsequent7 return
was already warmed by login, although its depot was demonstrably still reconciling
at the measured boundary. Do not present1.301s as a matched cold-start speedup ratio.
R252's untouched256cell first ingress remains4.799s visit / both checked by5.000s;
its safety/suffix mechanisms are unchanged in R253. These receipts establish the
specific regressions and tested <5s return scenes, not a universal bound for every
mine, load radius or future machine load. Historical field9 uncertainty remains
isolated and retained, not replayed to obtain these results.
