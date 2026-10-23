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


## Region-demanded worksite facts

ce475c494 removed the redundant post-admission body-link waits from the next
120s startup/HOT JFR (r239f-startup-hot). First login was253ms versus574ms in
the preceding observed run; this is an observation, not a guaranteed bound.
Worksite projection still consumed100ms: discovery eagerly constructed every
cell even though the final physical queue was already regional.

The projection owner now exposes immutable region lists and exact point lookup.
ExtractionLayout indexes declaration addresses once. The common WorksiteRegionIndex
composes captured immutable sources lazily per requested region; it does not
inspect unrelated cell contents. Unchanged regions retain object identity.
Extraction remains the owner of depletion, overrides and revision semantics.
Duplicate positions fail before a requested region returns any facts. Rollback,
player edits and extensions use the same view, without a parallel legacy path.

The scheduled-subject existence view also retains its union when changed maps
only change values. Nested machine operations and field jobs remain explicit
identity inputs; retirement still fails validation. Physical-store lookups reuse
the exact bound owner and resolve filesystem paths only during hydration.

Focused region/cell, queue, scheduled-subject and storage checks passed. The
region rollback check initially compared identities after returning to an older
branch; corrected to semantic differences while retaining the unchanged-region
identity assertion. Two native block-extraction tests passed (r239g-extraction-gates).
These do not establish live performance or complete end-to-end mine acceptance.
Saved-body causal fences, native canonical WAL waits, global navigation geometry
monitor contention and GC remain separately measured leads. The80ms target is open.


## Completed geometry publication and nonblocking native reads

33c8bf0f4 deployed/restarted02:30:39+05, same world; package/preflight/verifyPASS
(r239g-*). Pilot a682f504-5eed-4609-8dc8-d1ff7b354104 completed both visits with
zero retained conflicts. HOT debt532then320,166 after exit; no hidden debt claim.
JFR r239g-startup-hot retained startup125.8ms, login222.6ms and transitions160.6ms.
The former100ms eager worksite composition did not recur in that profile.
GC pauses reached78.3ms; native WAL and field/actor physical journals still wait.

KnownSiteGeometry previously invoked expensive projection while holding its
world-wide monitor, inside the route-knowledge monitor. Extraction also shared
mutable incremental builders across owner/worker snapshots. Completed results
now cross threads through the existing exact-input bounded memo; each computing
thread owns its mutable builder. The navigation lock receives an already-captured
site view, and ground/field views consume that same capture. Atomic ground lineage
and scope capture remain intact; no independent per-thread revision epoch.
Fifty focused geometry/architecture checks passed, including concurrent old/new
snapshot preparation, rollback, source invalidation and exact region reuse.

The same live JFR proved forced chunk loading from ResourceFieldObservation.read
via Level.getBlockState -> ServerChunkCache.getChunkBlocking/managedBlock and
ChunkSerializer.read. hasChunkAt is ticket/holder availability, not completed
chunk availability. ModLens compile snapshot ac5901effb63c8add2e7c49e51ec16803ded5b4fa4ce09c982f62b9aa90c1075:
ServerChunkCache.hasChunk lines250-259 checks the holder's ticket level;
getChunk lines140-169 may managedBlock; getChunkNow lines174-207 returns only
an immediately available native chunk. Runtime stack confirms the affected mixin
path rather than assuming compiled source alone describes runtime behavior.

FrontierV3LoadedWorld centralizes nonloading readiness for active field, storage,
scene, body and navigation callers. Field condition/foreign evidence reads capture
the chunk and read it directly; no default AIR or force-load fallback. Existing
unloaded-cell and ownership-negative native tests remain acceptance for the changed
field boundary. Inactive hive/combat-specific callers are outside this cut.
The80ms objective remains open; no quiet rolling window establishes completion.

Candidate gates:50focused geometry/architecture tests,20nativefield-turns,43native
local-navigation testsPASS (r239h-geometry-gates, r239h-field-gates, r239h-nav-gates).
The expected unavailable-writer quarantine in the navigation negative fixture is
not a live-server fault. Common readiness uses block coordinates explicitly;
the former two-int hasChunkAt call was migrated to readyAtBlockColumn.

### Independent geometry snapshots: exact equality reuse

The d9844caa9 deployment exposed a regression: scout-triggered common geometry
refreshes reached 2.52 seconds. `r239h-regression.jfr` attributes 298/443 server
CPU samples in the selected spike window to `RegionalBlockMap.Part.equals`;
independent preparation/owner builders repeatedly compared equal regional maps.
This is shared geometry cost, not a reason to alter scout business behaviour.
Immutable regional parts now weakly remember their last exactly equal peer,
symmetrically. Only an actual content comparison establishes the proof. Hash
collisions still require comparison, new regional contents have no inherited
proof, and weak references cannot retain a chain of obsolete world snapshots.
The focused regression checks these properties without an elapsed-time budget.
Runtime improvement and the overall 80ms target still require measurement.

The 1eddca26e repeat removed the multi-second map comparison pattern (one
regional-equality CPU sample in the initial partial recording), but retained
363ms startup allocation and 229ms first login. Source/profile tracing found
that RegionalNavigationProjection treated every definition-map replacement as
an instruction to reproject every region. The projection now owns an inverse
block-type-to-region index and compares actual coarse classifications. New or
changed unrelated definitions do no spatial work; removed types leave the index.
Detailed native collision-shape revisions remain separately owned by geometry
lineage. Catalog rollback and unavailable facts retain exact invalidation.

The same JFR attributes first-login chunk decode samples to
`BaseMapCodec.decode -> Object2ObjectArrayMap.put/findKey`, reached by native
player collision while ChunkSerializer restores GeneratedGeology's attachment.
The retained geology uses large coordinate maps. Inspection of pinned DFU8.0.16
bytecode confirms CompoundListCodec decodes entries with a linear list builder;
LinearMapCodec establishes uniqueness with a hash index and preserves the native
compound wire representation. GeneratedGeology now uses this adapter codec.
Focused NBT format round-trip, malformed value and colliding decoded-key cases
pass; actual chunk-loading timing is measured on the same retained world.

### Prepared geometry handover, not a second full reconstruction

The 2e23e350f run no longer exhibited the initial admission reproject spike,
but a 356.8ms first scene pass constructed the server thread's independent
extraction projection from scratch. Completed-result identity misses may follow
ordinary non-geometric state changes. Each computing thread now forks a private,
quiescent prepared seed: only small mutable owner/type indexes are copied;
immutable regional facts and projections remain shared. No live mutable builder
is published or modified by another thread. Seeds are bounded to the last
prepared view and have no authority over domain state. The common regional
composition/projection fork operations support other geometry providers.
A worker-then-owner branch test proves unchanged remote regions retain object
identity while a depleted local cell changes and predecessor facts stay intact.

The common all-service-points geometry calculation also runs outside the
ServiceBoundaryComposition declaration/column monitor. Its completed immutable
list is shared by exact captured inputs; preparing every service apron can no
longer hold local admission behind the registry monitor (58ms measured in r239i).

r239j retained a 513.6ms first-login tick (native distance-ticket processing,
first-use class loading and chunk attachment restoration), 95.6/112.5ms other
host ticks, and GC pause maximum88.9ms. Thus <=80ms is still NOT achieved.
Next measurement compares the prepared-seed change and the pinned Java22
Generational ZGC collector with the same4–12GiB heap/world. Collector flags were
validated on the actual runtime; throughput/pauses remain to be measured.

### Local worksite lookup and ordinary field receipts

The r239j profile attributed73 server CPU samples to point lookup probing every
extraction deposit. ExtractionGeometryIndex now separately indexes complete
worksite-region candidates (including natural terrain and infrastructure), while
its source-custody regions retain their narrower meaning. Exact lookup and overlap
validation still belong to the selected producer. The physical owner reuses the
index for unchanged immutable deposit input rather than rebuilding the declaration
map on each queried block. Retirement coverage prevents stale owner candidates.

Road support maps were also thread-local although immutable. A 94.7ms allocation
spike in r239k included their reconstruction; exact bootstrap/topology-keyed
completed results are now shared across preparation and server threads.

Ordinary field work confirmation and retirement acknowledgement now await the
existing exact per-field durable receipt without blocking the server. Native
pre-loot fatality retains its synchronous effect AND retirement barriers. The
first native check caught the effect becoming deferred; after preserving that
barrier, its next assertion caught deferred final retirement. Both causal paths
were repaired, with no relaxed assertions: all20field-turns native testsPASS
r239l-field-final.log. Initial failures retained r239l-gates/field-recheck.

R239k ZGC:280recorded pauses, maximum0.03788ms, versus G1 maximum88.9ms in r239j.
Prepared-seed scene reconstruction spike was not reproduced in this short run;
startup193.1/login191.2/transition108.8ms and extraction-extension93.5ms remain.
Time debt851→1459ticks during twoHOTvisits; prior G1r239j1191→1796, r239i81→284.
Changing accumulated world state prevents attribution of throughput differences
to the collector alone. Investigate scheduled preparation/owner completion rate;
low host-tick time cannot substitute for timely simulation progress.
Pack commit f631234 adds explicit reversible Java22 --gc profile support; runtime
uses its managed generational-z profile with the same4–12GiB heap. No client JVM
change or implicit installer-wide collector switch.

## R239m — readiness-driven canonical resumption

R239l (02f23781a), same world, managed Generational ZGC: the two-visit pilot
74f6adda-80f2-4c92-901b-d22f2a67ccf9 completed. JFR240s has291 GC pauses,
max0.036327ms. Host outliers remain: first login304.1ms, transition113.3ms,
ordinary103.1ms, departure92.5ms. No80ms acceptance. The ordinary field barriers
are absent from >=10ms journal waits; saved-body fencing remains (8 waits,
81.3ms total). Eight unresolved historical field-projection tickets were logged
on Stonefield admission (source revision2081274); green retained-conflict summary
does not prove those projection operations healthy. Diagnose separately; do not
replay an uncertain physical operation merely to suppress the warning.

Worker JFR from R239k attributes1825/3025 samples to GoodsParticipantProcess,
including market-book construction and repeated supply-policy projections.
A separate structural throughput limit is confirmed in the caller chain:
ScheduledPreparation and FrontierFileStore had no owner wake on completion;
a ready result waited for the next host tick. R239l ordinary time debt reached
2817ticks with4858 completed preparations averaging33.8ms. CPU relocation alone
therefore did not meet the product performance goal.

FrontierV3CanonicalResumption now binds coalesced readiness from the preparation
worker and ordered WAL/snapshot writer to Minecraft's existing task queue.
No second canonical executor: runtime remains the sole publication owner.
Ready tasks recheck runtime identity, lifecycle/operator holds and recovery;
they cannot reenter a native tick, repeat physical execution, accrue host time
or renew the observed tick's deterministic work allowance. Cancellation/closed
runtime notifications cannot revive a session. A calculation already captured
at the current instant may finish with zero remaining elapsed-time debt.
`pm-ready-continuation` is separately measured, with exact >80ms outlier logs;
it must not be hidden by reporting only the native through-Post span.

Pinned engine source: BlockableEventLoop.tell always queues and unparks;
execute may run inline on the owner and is deliberately not used. Existing
Minecraft TickTask scheduling retains event-loop fairness. Core kernel event
ordering/commit protocol and captured execution instant are unchanged.

Focused readiness/runtime/file-store checks PASS (r239m-tests-recheck.log).
The old host-debt test counted polling calls as elapsed progress, which depends
on worker completion timing; it now checks actual multi-tick canonical advance
and retains exact final checkpoint equivalence. One new fixture initially
omitted kernel schedule codecs; corrected fixture, no product workaround.
Guardrails PASS after keeping new resumption logic outside the large lifecycle
class. End-to-end single-host-tick asynchronous completion/restart and live
throughput measurement follow before an improvement claim.

The single-tick asynchronous test exposed a real retention deadlock: after one
of two retained slots was used, the second preparation was ready, but runtime
requested two free slots before accepting it. Checkpoint capture correctly
refused while that same calculation remained pending. The failure receipt shows
value=1,pending=true,completed=2,running=0,queued=0,awaitingDurability=false.
Runtime now bounds a retained calculation by actually available slots; it does
not demand a fresh full-turn reservation or cancel/recompute its exact input.
Also retain the explicit deferred ordinary boundary when elapsed-time debt
reaches zero, so completion and checkpoint notifications can drain that boundary
without manufacturing another host tick.

R239m final41 focused tests PASS, including six asynchronous scheduled actions,
WAL forces and capacity checkpoints completed at instant1 after exactly one
observed host tick, followed by exact restart. Guardrails PASS:
`build/r239m-acceptance.log`. No elapsed-speed acceptance is inferred from tests.

## R239n: bounded ready publication and per-record departure durability

Live ca12a2ccb improved throughput: ordinary debt was0–2 at03:17–03:20,
compared with2817 in the previous run. This is not an exact speed ratio across
evolving states. Login347.2ms, transitions142.4/107.6ms and ready82.5ms still fail
80ms. Pilot Gradle preparation overlapped initial10s; login was after it finished.

Ready callbacks now publish one action with a small classification prefix,
sharing the original host tick allowance. The ordinary execution boundary is
captured across yields; processing many actions due together cannot manufacture
one simulation tick per successor. Completed consumption is retained when a
checkpoint defers the next prefix. Async six-action/restart coverage retains
execution instant1 with either1 or17 observed host ticks.

Saved-body departure previously waited synchronously for journal forces
(14–18ms samples in r239m). FrontierV3RecordDurability extracts the existing
record-prefix rule from JournaledSavedData, reused by the actor ledger. Only
submission of the same actor replaces its receipt. Dirty successors cannot
borrow an earlier image; failures propagate; confirmed/deleted rows retire
receipt state. Saved departure checkpoint/release now poll that exact receipt.
The related stale-body discard also requires durability, so this change does
not expose removal before the fence is on disk. Native death and join barriers
remain synchronous pending a separate protocol review.

Verification: runtime/carrier/adoption/recognition focused checks and guardrails
PASS r239n-gates.log;24 native body-lifetime tests and physical-store/field-witness
checks PASS r239n-body.log. Existing record dirty-successor test is retained with
two focused receipt isolation/failure checks. Live peak acceptance remains open.

R239n live candidate b797746e6 passed startup verification but failed subsequent
player entry at03:28:01. The server saved all dimensions and stopped on rejected
`scene-reclaimed`: HOT requires independently confirmed physical participants.
`reclaimObservedBodies` checked identity/ownership but bypassed the common body
execution-readiness gate used by first materialization. Recovered object presence
can precede canonical admission. Recovery now uses that same readiness gate for
HOT, preserving separate DRAINING behavior and the common owner of confirmation.
No catch-and-ignore, forced RUNNING state or new recovery executor is introduced.
Receipt: r239n-server-failure.log and failed pilot840b58e0-b331-431c-98ed-59307171448f.
The short-wait JFR attempt used a relative settings path resolved by the target
JVM and did not start. No timing attribution is claimed from that missing capture;
use an absolute JFC path on the next candidate.

The startup visibility index also performed two whole-plan scans per settlement
and mutated its search set while admitting roads. One road could recruit the
next, so a local package depended on map iteration order and could spread along
remote roads. Build one owner/chunk index, probe the fixed settlement halo, then
append managed field chunks. Package order is stable and no remote road inherits
local ingress permission. Existing whole-settlement visibility coverage now also
rejects road expansion beyond the local footprint. Focused index/actuation checks
PASS r239o-local-index.log; guardrails and native first-admission PASS
r239o-scene-gate.log. This is not yet live recovery or latency acceptance.

## R239p: finish polling rounds and remove native terrain/read stalls

R239o live recovery no longer crashes (pilot cb6fb072-525b-4d04-8606-5bc7de3d84f1,
two visits complete), but debt2537 at03:34/instant593447 exposed an R239n regression.
Only9 preparations completed, ready0/checking5/future606. Each eight-classification
continuation began a new keyless-held polling round, refilling checking as quickly
as it drained. The clock boundary correctly stayed pending, revealing starvation.
ScheduleExecutionIndex now captures the polling count per canonical Revision and
target SimInstant. Continuations drain that same round; a changed state or time
reopens it. Predicate-only queue selection has no revision proof and keeps fresh
rounds. A13-held-action regression proves draining and both invalidation paths.
Do not accept low CPU/tick peaks while ordinary simulation debt grows.

The all-short-waits JFR shows login208.5ms inside native Player.tick, specifically
fluid/collision reads invoking getChunkBlocking and doing many queued chunk loads.
SectionStorage parks total only~9ms around that tick; sparse CPU sampling alone
had not explained it. Pinned source confirms ServerPlayer.doTick guards only
spectators with Entity.touchingUnloadedChunk; that uses hasChunksAt, not completed
LevelChunks. A narrow WrapWithCondition now gates only world-dependent Player.tick
on the same bbox+1 halo via shared getChunkNow readiness in the PMphysicalworld.
Native tickets continue loading; network, inventory and player bookkeeping remain
outside the gate. No air substitution, synchronous chunk requests or repositioning.
Native tests prove absent terrain stays absent under readiness inspection.

Departure-return fencing had98 journal waits totaling82.9ms in r239o. Native
entity-read completion now awaits its journal receipt asynchronously. Both
publication and completion use EntityStorage's own mailbox, so native flush can
drain them without the separate server-task pump. Reservations survive pending
writes; failed writes fail deserialization. An unchanged return marker still
awaits the earlier submitted prefix, never borrows an in-memory marker as durable.
Mailbox tests cover delayed success, synchronous and asynchronous failure, and
completion only on the native owner mailbox.

Focused kernel/runtime/mailbox checks+guardrails PASS r239p-gates.log;25 native
body-lifetime/readiness tests PASS r239p-native.log. No80ms claim before live
measurements with actual ordinary time progression. JFR/async-profiler receipts
r239o-profile.tsv and ~/.local/state/pale-mirror/profiles/r239o-transition-wall.jfr.

## R239q: stationary player re-anchor and native save acknowledgements

R239p live test (same world, pilot afb7cff0-c273-48cd-9968-d98138b0b3e6)
restored progress: instant 595114/debt46 then596640/debt3. Both visits completed;
no retained conflict, but this is not gameplay-cycle acceptance. Peak205.8ms
at login,128.6ms on first transition; target80ms still not achieved.

The exact login interval22:39:12.459–.664UTC contains
ServerGamePacketListenerImpl.tick → absMoveTo → Entity.setPosRaw →
NeoForge's unconditional getChunk → managedBlock. All three wall samples inside
the interval run queued chunk restoration below that same blocking demand.
The previous base-player-tick guard did not cover this independent native
re-anchor. Same-position resets now preserve interpolation/rotation bookkeeping
without calling setPosRaw while PM terrain is incomplete. Actual position
changes, corrections and teleports still use native code; network ticks continue.
Source receipts: build/r239p-packet-tick-source.json, r239p-setpos-source.json;
profile: r239p-wall.tsv, r239p-profile.tsv.

The transition interval22:39:42.914–43.043UTC includes30.17ms of repeated
EntityPersistenceObservers.departed journal waits. Raw observations now submit
to the same ordered journal asynchronously. Positive saved-departure publication
and per-actor durable release checkpoints remain gates, as do return-read fences.
NativeOperationSaves likewise submits acknowledgements after native data is
already saved. Marker pruning still requires durable retirement; a pending or
failed journal cannot discard the native recovery marker.

An attempted extra native reset test required the PM dimension absent from the
focused vanilla GameTest fixture; removed this invalid fixture, not a product
assertion. Existing loaded-terrain GameTest plus actual packet/client login are
the appropriate evidence. Async-retirement test explicitly controls the writer
and checks marker retention before completion and recovery after completion.

Verification: build/r239q-verification.log — focused operation-ledger/retirement
checks, guardrails and25native body-lifetime tests PASS, all dimensions saved.
Live effect must still be measured; no <=80ms claim from these checks.

R239q live: pilot200ce83a-652d-46e8-ae13-5de7e600970c completed both visits.
No >80ms login/transition tick. One subsequent extraction-effect tick96.4ms,
plus startup107/123/83ms; still NOT target acceptance. Instant603510/debt9 then
604454/debt73, preparations1428. Report progress and debt together.

## R239r: physical confirmation does not own future economic planning

The exact96.4ms peak at22:46:32.613UTC entered economic supply/forecast from
ExtractionHotObserved command planning, through appendAfterObservation. Confirming
one mined block synchronously assessed demand and considered opening/extending
the deposit. It now records the fact and emits the existing coalesced settlement
extraction-review request. The registered detached review owns development,
staffing and access waits. Earliest-deadline coalescing prevents one queued review
per extracted block. COLD depletion and external edits use the same boundary.
The existing replay test now explicitly proves zero path calculations in the
observation, real access calculations in the review, and recovered pending waits.

Other evidenced work removed: Graybox Cursor.cellsIn did a linear search of every
plan chunk for each chunk-load/send probe (wall samples r239p-wall.tsv). Its exact
chunk index is built once and retained across dynamic overlay/staging changes.
HierarchicalPedestrianSearch rebuilt positive route/itinerary proofs at each
publication/poll, including main-thread samples at a transition. Immutable proof
is assembled on the worker once; compatible rebases preserve its exact tokens.
An itinerary is offered to the bounded reuse cache once, not every refinement.

JFR startup peaks were lazy service class transformation (planner/physical
registry) and first field-ledger recovery. Service composition and field recovery
now belong to the existing explicit startup boundary. This moves one-time work
before ordinary gameplay; it does not eliminate startup CPU or pretend to do
physical work in a warmup. Navigation recovery itself remains asynchronous.
No search breadth, route correctness, canonical timing or durability is weakened.

Verification r239r:36frontier +10adapter checks andguardrails PASS
(build/r239r-gates.log);2native block-extraction checks PASS andall dimensions
saved (r239r-native.log). Next step is same-world live peak/progress measurement.

R239r deployment rejected at startup on the retained world: `review ID collision`
from the first COLD extraction at instant609746. The review registry is explicitly
coalescible, but the generic queue incorrectly treated a changed deadline at the
same stable owner/kind address as an identity collision. No mined-block transaction
was committed: validation rejected its queue overlay before publication. Server
saved all dimensions and stopped. Client attempt was cancelled; no performance
acceptance is claimed for this deployment.

R239s corrects the common coalescing boundary: same ID + same declared owner/kind
may request an earlier/later deadline; priority/cost must still match. Earliest
wins, foreign owner/kind/cost stays rejected. Ordinary `Created` exact facts remain
strict. Queue tests cover both directions and atomicity; the existing kernel WAL
replay test now checks stable-address replacement as well as distinct causal IDs.

R239s same-world runtime progressed609746→613128, final debt5; no new quarantine.
Pilot e3344c26-1ee6-4a8c-a198-a84e098219ec visited both towns. Its zero-conflict
assertion FAILED: retained incident revision2257046 is exactly the prior R239r
queue rejection (one occurrence at609746), not a fresh failure. `why
FRONTIER_INSTANCE/frontier:graybox` receipt build/r239s-conflict.txt. Do not erase
this history or report the pilot as passed. Post-start peaks:89.1ms at first login,
including46.8ms object-board preparation. Both subsequent transitions stayed<=80ms.
Startup136.9ms sampled vanilla Raids.tick class transformation, outside PM work.

R239t addresses the board's eager geometry: previously index initialization built
cells for every structure and bootstrap organ worldwide before checking loaded
recipients. StableObjectGeometry now indexes exact immutable declarations and
materializes/caches only requested object footprints from the SAME shared grammar.
Cache is bounded by that geometry's declared owners, discarded with the geometry;
physical scars and current contamination remain uncached live facts. Full-plan
callers can still explicitly request all footprints. No stale text or visibility
shortcut. Focused39readability/graybox +7architecture tests andguardrailsPASS
(build/r239t-gates.log), including removal/restoration against a reused footprint.

R239t live pilot fa6e8173-ec92-431f-8be7-286e7c4e737d completed both visits,
canonical instant reached619506. It explicitly retains/checks the one historical
R239r incident (revision2257046, occurrence1); this is an observational latency
probe, not zero-conflict/full-cycle acceptance. First login max76.98ms; later
transition peaks90.91ms and96.59ms remain above target. JFR identified a global
first-admission history sort during native chunk save and ~50ms of synchronous
field-projection journal waits during farmer admission. Profiles were stopped
before subsequent Gradle work. Receipts: r239t-hot-result.json, r239t-profile.tsv,
r239t-wall.tsv in build/.

R239u replaces three evidenced repeated/blocking paths:
- Queue pressure maintains an exact derived due-prefix count, updating only
  deadlines crossed and actual schedule mutations. Canonical order/time is
  unchanged; diagnostic backward-time reads remain exact. Next deadline uses
  the existing ordered deadline index.
- Native adoption observation starts with actual bodies serialized in that
  chunk and directly looks up each full declared identity. It no longer sorts
  all first-admission history worldwide. Foreign UUID/type/owner/epoch evidence
  cannot acknowledge or duplicate a genuine candidate.
- Production single-cell growth projection uses the same deferred operation
  protocol as batch projection: durable predecessor, durable operation
  permission, real effect, durable result. Admission waits for the field receipt;
  interrupted crop work treats pending durability as pending rather than a
  conflict. Other cells and independent simulation work continue.
Focused queue/kernel/adoption/composition/first-admission checks and guardrails
PASS (r239u-gates.log). All20 native field-turn tests PASS, including partial
physical application recovery followed by deferred single-cell projection
(r239u-native.log). Live measurements follow; target is not yet achieved.
