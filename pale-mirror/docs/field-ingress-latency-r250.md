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
