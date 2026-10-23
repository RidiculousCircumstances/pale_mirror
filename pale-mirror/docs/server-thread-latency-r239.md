# R239 — bounded server work and detached advice

Target: remove evidenced owner-thread stalls and verify individual runtime tick
peaks <=80ms in COLD and HOT. This target is not satisfied by average TPS,
recent-window sampling gaps, slower simulation or discarded work. No feature
semantics, durable-before-effect rules or geometry/profile validity are relaxed.

The R238 profile identifies synchronous full economic assessments in work
eligibility and review diagnostics, synchronous group-rendezvous search, blocking
checkpoint publication, and geometry-cache churn. Registered policy reviews now
prepare the whole proposed event list against immutable canonical input and a
frozen read view of retained navigation evidence. They do not acquire live searches,
change wake indexes or invoke Minecraft. Owner completion publishes only selected
route dependencies and registers exact continuation generations. A route completed
between capture and publication emits a ready signal rather than losing its wake.
The kernel's existing exact state/revision/action fence discards obsolete advice.
Small semantic continuations retain their ordinary direct path; there is one
bounded preparation worker, not a pool or new execution order per domain family.

Snapshot preparation captures immutable state, schedules and receipts on the owner;
encoding runs through the same bounded preparation worker. Ordered persistence
publishes a checksum-bound captured prefix, then retires covered files. New WAL
appends continue after that prefix. Only a confirmed receipt compacts corresponding
engine history; later records remain. Explicit shutdown drains unfinished work;
ordinary turns poll without waiting. Failure never acknowledges a partial snapshot.

Navigation surface LRU eviction is performed at insertion, removing a redundant
lookup and the profiled high-frequency overflow branch.

Every through-PM-post host tick >80ms now emits a bounded single-line diagnostic
with its sample number and longest measured nested stage. This catches outliers
between the UI's recent-64 samples. It is observation only, not a time-based test
or a new admission mechanism. It still excludes listeners after our post handler.

Verification: guardrails and affected kernel/navigation/economy tests passed;
runtime/store/preparation checks passed, including captured-prefix recovery and
abandoned/read-only route advice. The existing body-lifetime native slice passed
all 24 cases. Receipts: build/r239-affected2.log, r239-runtime-final.log,
r239-runtime-frozen.log and r239-native.log. Runtime deployment/measurement follows
this candidate; no numerical improvement or achieved 80ms limit is asserted yet.


First live candidate fa0caeb41 recovered the existing world without conflicts.
COLD recent windows initially max31–71ms, but this was not terminal acceptance.
The HOT profile found cultivation recomputed in the reducer (135 server CPU
samples), synchronous extraction review/progress and harvest admission, and
physical journal waits (roughly10–33ms). HOT canonical debt reached440ticks and
later drained; therefore cheap host windows alone are insufficient. Full-pack
pilot run4dc8eb73 completed, with17managed bodies inStonefield; first visit140/-2
was outsideClearwater (center440/0), not evidence of a second loaded settlement.
Startup first tick3.16s and later612ms ingress remain explicit unresolved peaks.

Follow-up: immutable completed-results cache reuses exact input identities across
preparation/reduction, without sharing mutable per-thread memo maps. Cultivation
and economic assessment use it. Extraction review/progress and harvest admission
explicitly opt into detached preparation without changing scheduler priority,
weight or coalescing. Ordinary retained mining/field/food/production/shipment
physical steps use existing asynchronous before-effect receipts. Native player,
death and other non-repeatable callbacks retain their explicit synchronous API.
This does not yet claim elimination of every physical confirmation force.

Second candidate072e5f57d: first three COLD minutes had only the startup3.394s
outlier; recent64 maxima about17ms. Exploratory full-pack HOT run
`aed6b88f-7b0f-4fcd-89a2-37bc6f95e345` visited Clearwater440/0 and
Stonefield-600/-1360, completed with zero retained conflicts. Login620ms included
object-board172ms; field projection reached107ms and repeated50ms spans. Later
settled HOT windows do not erase those failures. No JFR in this measurement;
client startup and one local compile overlapped initial ingress.

The next correction rebuilds volatile navigation wait hints on the existing
bounded preparation worker. It discards preview events, publishes only exact
retained schedule generations and never overwrites a newer wait registration.
The synchronous test/recovery entry uses that same protocol. No first-tick full
policy preview remains. Board discovery now builds placements without rendering
worldwide economic text; only loaded selected boards render current facts. The
physical cursor compares only overlay inputs, avoiding global population/route
readout scans on every presentation turn.

Fragmented physical tables now expose exact current-record durability receipts.
Unrelated new rows cannot postpone a saved record; a changed row cannot borrow
its prior receipt. Field growth batches retain their discovery cursor while
permissions are pending, apply disjoint operations through the common deferred
boundary, and close through the shared asynchronous owner outbox. The outbox
waits for the family's completed transition, then the operation closure, before
clearing its captured entries; later entries are not cleared with an older batch.
Native/player synchronous APIs retain their existing causal guarantees.

Verification: guardrails, recovery/replay, readability, and affected adapter tests
PASS (`r239-ingress-tests.log`). Delayed-prefix record/outbox tests and journal
recovery PASS;28 native field/board tests PASS (`r239-ingress-native-final.log`),
including split physical batch recovery with actual durable permissions. The
initial native test incorrectly treated partial batch progress as complete;
its terminal per-cell check now awaits both actual cell outcomes. This correction
did not weaken the terminal assertion. Live measurement of this next correction
is still required;80ms is not yet established.

Third candidate b7aeb741d live: startup844ms (navigation definitions333ms),
versus3.394s previously. HOT run1d969332-b594-4c40-9524-c494b021c6c9 completed
with zero retained conflicts. First login511ms remains, settled windows are not
acceptance. JFR r239c-hot.jfr:647 server CPU samples,35 non-idle PM blocking
spans>=10ms, native geometry invalidation up to28ms, body confirmation34ms,
saved-body checkpoint22ms. GC90ms also exceeds target. Local client startup and
focused verification overlap this exploratory recording; it is attribution,
not an isolated capacity benchmark. Canonical debt419 during one HOT sample.

Next correction separates declared block palettes from spatial site geometry:
provider-owned immutable type sets include terrain, geological materials and
current layout/cell/observed blocks. Native definitions do not reveal positions
or grant traversal through unknown terrain. Rejected domain commands no longer
cancel unchanged scheduled preparation; accepted commands still invalidate it.
Diagnostic cancelledReady distinguishes discarded completed work. Focused
kernel/palette tests, guardrails and43 native navigation/shape tests PASS
(r239d-gates.log). Remaining synchronous native/confirmation boundaries and
canonical throughput require further work; no80ms completion claim.

Fourth candidatee0bc246e6: firsttick126ms andnativecatalog32ms, butsecondtick
3.15s withscheduleallocation2.89s. This is NOT a win againstthe80ms criterion.
The detachedstartupreconstruction had no matchingexecution-readiness barrier:
owner readiness could enter spatialgeometry whiletheworker was rebuilding it.
Startup now preserves original recovery-before-execution ordering cooperatively;
host ticks accumulate and are not discarded. Remaining first-ingress cost still
needs an isolated profile.

Ordinary operation confirmation now uses the same protocol with a pluggable
receipt wait: exact physical owner binding durable beforecanonical submission;
canonical receipt remains pinned until physicalclosure durable. Sharedphysical
storage delivers bounded owner callbacks, including whenadmissionlackscapacity.
No writer-thread callback, noearlypinrelease, no second business queue. Native
required/non-replayable boundaries remain synchronous. Carrier journal ordinary
lifecycle now actually calls its existingasyncwriteAPI. Focusednegative/crash
checks and24nativebody-lifetimetestsPASS (r239e-gates.log). Explicit saved-body
checkpoint andnative-world canonicalwrite barriers are remaining synchronous
sources, not silently waived. Source is not yet proof of80msliveperformance.


## First-pass batching and resident activity (next candidate)

3f3318 startup no longer reproduces the 3.15s schedule-allocation stall. An
isolated90s startup JFR (r239e-startup.jfr) finds a302.5ms first physical pass,
79.8ms worksiteprojection, and max51.6ms GC pause. These are not <=80ms acceptance.
The registry had only a preparation budget: required stages accumulated in one
pass. Its existing ordered continuation now yields between complete stages at
the hostsoftbudget, preserving dependencyorder and admission guards. One admitted
stage always progresses; current state is re-read on resume. Canonicaltime and
native movement are not budget-skipped. Individual stage overruns remain visible.

Pending worksite queues retain immutable chunk declarations and enumerate cells
only for available/requested chunks. Replacement and stale completion checks
remain exact; unavailable chunks cannot claim physical completion. This removes
per-cell queue allocation for every COLD mine. Initial domain geometry composition
is still eager and is not claimed eliminated.

An independently logged resident activity review took80.97ms (tick92.72ms).
Its registered planner now uses the same detached preparation capability as
extraction/reviews, with unchanged semantic priority, instant and hold rules.
Owner-side route acquisition and exact snapshot validation remain mandatory.
Throughput/debt must be measured; a lower tick peak alone is not acceptance.

The first native gate used unsupported slice `aftermath`; Minecraft did not run
tests although Gradle returned success. That is invalid native evidence, replaced
by the actual `physical-turn` slice, with test-count/result inspection.

The120s HOT profile of3f3318 (r239e-hot.jfr, no concurrent build) attributes
49server waits >=10ms,566.6ms total,max20ms, to post-admission carrier-link
cleanup. That redundant link now saves asynchronously; the common operation
journal still owns closure and its receipt pin. Restart with the old link follows
the existing ownerClosed/retired recovery, never repeats entity insertion.
11saved-departure waits (139.2ms total,max29ms) remain a separate causal fence.
GCmax61.64ms; login574ms included vanilla classloading/commands/chunk processing
and first PM board geometry. No attribution of all574ms to PM is justified.
HOT run9f5ba759-9a47-4a61-a428-a8166349a9de: zero retainedscene/inventory/required
conflicts; debt337then declining, but cancelledReady101 requires follow-up.

Unchanged physical journal flushes now reuse the most recent submitted receipt,
including unfinished writes, instead of scheduling empty tasks behind that same
prefix. Initial empty baseline creation and failure publication are preserved.
The deterministic queued-executor check proves no early acknowledgement and
that a later write cannot borrow an older completed receipt.

Physical-turn negative/recovery slice:9passed after replacing its obsolete
single-invocation READY expectation with bounded continuation to the same result.
Focused kernel/activity/pending-queue checks passed; final body/journal checks
and deployment evidence follow below.

Final source gates: guardrails,47kernel/activity tests,12queue/worker tests,
10journaltests,9nativephysical-turn and24nativebody-lifetimetestsPASS.
Receipts: build/r239f-gates.log (initial physical-turn assertion failed as
documented), r239f-physical-turn.log, r239f-body-journal.log.
