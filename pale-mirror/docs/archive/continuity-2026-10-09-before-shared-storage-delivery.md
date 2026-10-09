# Continuity Ledger
Updated2026-10-09. Main alone; Terra stopped, subagents prohibited.
Canonical governance is here; sole workflow docs/engineering-agent-protocol.md.
Prior chronology: docs/archive/continuity-2026-10-07-before-addressed-planning.md.
## Current assignment and source
CURRENT IMPLEMENTATION2026-10-09: user accepts connecting all eight remaining
useful physical registries and deleting the tenth obsolete field blast registry
with related producers/consumers. Also explicitly requires SS-01/02/05/06:
delta field/click writes, before-effect receipts, legacy runtime pre-construction
gate and common all-store recovery/drain. Main alone/no subagents/no active goal.
Active source /home/rd/proj/pm-f06r3-facility-lane-recovery, base e3377dd2.
Implement connected cut in docs/work-orders/PM-SHARED-STORAGE-20261009.md;
preserve canonical WAL/native storage meaning and all exact physical witnesses.
Live R75 unchanged. No new deployment/commit/push requested in this turn.
PRIOR AUDIT2026-10-09 supersedes the completed delivery assignment below.
User asks complete shared-storage inventory, no remaining legacy. Main alone;
no goal/subagents. Static audit complete, migration NOT implemented. Ten v3
SavedData ledgers: actor journal already adopted, eight useful migration
candidates, one obsolete stage-field explosion ledger to retire with old field
protocol. Confirmed full-file field/click writers, unfenced dirty-before-effect
witness paths, incompatible aggregate limits, legacy field codec/consumers,
unconditional PaleMirrorRuntime/PaleMirrorSavedData construction before v3 gate,
actor-only journal drain/read composition and dead/ordinal explosion codecs.
No reproduced data-loss claim or attribution of ~549ms first-visibility peak.
Findings, exact inventory, boundaries and connected plan:
docs/work-orders/PM-SHARED-STORAGE-20261009.md. Proposal linked in implementation
plan; user has not requested implementation of this expanded cut yet. Source
e3377dd2 clean/live R75 unchanged; no new test/client/restart/reset/commit/push.
Governance documentation WIP only; unrelated existing WIP preserved.
CURRENT DELIVERY2026-10-09 supersedes optimization/R74 status below. User asks
commit/deploy plus measurements. Main alone, no active goal. Implementation
57f0278e committed; additional checked-in live admission scenario e3377dd2.
Active implementation clean /home/rd/proj/pm-f06r3-facility-lane-recovery;
clean detached release /home/rd/proj/pm-carrier-journal-r75-release-20261009.
Verified guardrails/JAR/package; deployed NEW format11 test world
frontier-v3-carrier-journal-r75-20261009, same seed20260918065. Pack unchanged.
Five alternating production-ledger A/B pairs405actors/16warmup/32changes:
median20.824ms old vs2.714ms new,7.674x; encoded291562 vs19904bytes/-93.17%.
Not totalTPS, physical disk bytes or snapshot-rollover performance.
Full-pack ordinary20FPS boundary PASS:2.400s/1.200s/0.100s/0.300s. Fresh short
visit admitted no bodies, so targeted live_carrier_journal_admission also run:
resident7-1 actualINDEXED/HOT exactUUID,8leases/durableSeq9/queue0; PASS.
Two bounded JFR profiles, no new2s keep-up warning or quarantine; not all-lag
closure. save-all-flush acknowledged then ordinaryRCONstop, all dimensions
saved10:16:47/Java3579655 exited. SAME-world restart fresh verifierPASS:
invocation4f001fe3fdb149169ea29bfcc54e2c73/start1791523022/wrapper3590590.
Postrestart instant4134/revision1883 green/allconflictcounts0; recovered journal
durableSequence23/queue0. Clients/private displays gone; release advance hold
normally for interactive testing. No push. Pack .f0v-baseline/original nested
23WIP unchanged; mixed unrelated governance WIP preserved. Detailed receipt
docs/frontier-v3-carrier-journal-r75-20261009.md. No pending implementation task.
PRIOR OPTIMIZATION2026-10-09 (now committed/deployed; historical verification). User
accepts bounded physical-ledger delta journaling, group durability receipts and
background snapshots; main alone/no subagents/no active goal. Active source
/home/rd/proj/pm-f06r3-facility-lane-recovery (Gradle root pale-mirror), base
e55cb673, current changes uncommitted. Live server remains unchanged R74.
Retained JFR r74-settlement-boundary-20261009.jfr action03:56:03–15Z:216
server samples,50 body-ledger saves/16 WAL force/2 field saves; sample counts
are not elapsed percentages. Confirmed source full ledger compression and
two pre-insertion saves without an intervening physical effect. Implementing
format11 carrier journal: per-actor deltas, ordered bounded disk lane, receipts
only after force, immutable background checkpoints; server-only canonical and
Minecraft mutation. Unstarted body tickets defer insertion until receipt and
fresh validation. Recovery/offline readers consume checkpoint plus journal;
old worlds are incompatible and deployment requires a fresh disposable world.
Implemented and reviewed journal receipts, bounded group force, immutable
background snapshots, read-only torn-tail recovery, exact dirty-actor tracking,
complete offline recovery readers and graceful drain. Store image32MiB/4096rows,
queue64/16MiB, journal64MiB; corruption/failure remains visible. Common insertion
is receipt-driven and keyed by explicit world+actor (native concurrent worlds
exposed an initial missing-world key; corrected). Positive unstarted cancellation
is integrated into custody/reservation closure; no in-flight effect is retried.
Required existing departure/acknowledgement/WAL callers still wait for durability.
104 distinct focused tests green via103PASS/1 stale composition expectation,
then corrected composition5PASS; final production source unchanged by that test
correction. Guardrails/JAR/package PASS18s; canonical architecture/ledger
validators PASS. Actual native body-lifetime20PASS52s, including new async receipt
and before-effect cancellation cases. Final storage image-capacity bound and
test-only fixture naming were verified locally afterward; no whole-pack/release
claim. Native server saved all dimensions/stopped; no task process remains.
Uncommitted WIP artifactSHA512
8a27b9510da281138719e96df9ce35ddf20b86a771d0c4ca19359977ddd2cd38d9046c627c4c7e12cd9b526e7dc203f1f15dc5ed23ac9ea100bab48407bd054a.
No candidate deployment or measured live speedup. Live unchanged R74; normal
deployment needs clean verified release identity and a new disposable world.
Do not add speculative parallel route workers: current evidence points to I/O.
Original pack/nested WIP untouched; governance mixed WIP must not be blanket staged.
CURRENT IMPLEMENTATION2026-10-09: user accepts separating first-visibility read/
write budgets and prioritizing chunks. Main alone; active implementation clean
e55cb6735a671e3d126b3036485743740703292b (base changeb7154266 plus retained-pass
yield-order fix). Detached release /home/rd/proj/pm-first-visibility-r74a-release-20261009.
Reads use existing operational weight256/soft3ms, initial mutations remain8;
nearest-player selection alternates FIFO, deduplicates ingress queues. READY
cache/invalidation and initial current-owner handoff preserved, no persisted
geometry guess. Focused14 checks/guardrails/JAR/package PASS11s; clean detached
guardrails/JAR/package PASS10s. Old named graybox-projection native slice is
EMPTY (No test functions were given despite Gradle success); not accepted.
First intermediateb715 live same-world boundary PASS, visits2.9s/3.1s vs prior
21.6s/16.8s, reentry0.3s. Warning2064ms remains; JFR shows required body admission/
WAL/departure fences, not static queue alone. That result is NOT the final ref.
Finale55 live same-world installed SHA512
366f37afd8ac4cfd95117455c68220732731c50b0feae916ec3577d71f12ceb871192066cb6701b775bf10eca3301939f7102c6d87c606aaee3a1d3083053cbd.
Invocation844d7a1e61f8495983371c76d9576cd8/start1791518320/wrapper3436284;
fresh deploy verifier PASS. Final unchanged full-pack boundary scenario PASS,
build/r74a-boundary-profile-20261009.json/run3d2b2368-5d4a-414e-b2f5-52993b5ac7a2:
Clearwater2.800s/Ironmeadow0.900s/reentry0.500s/exit0.900s. Same world/seed,
ordinary20FPS client, restarted candidate; not a general speedup percentage.
Terminal679641/revision720839 and postexit680039/revision721128 green,
required0/scene0/inventory0/custody0; retained incidentIndex1 is historical.
Warning08:59:36.173 remains2019ms/40ticks behind; no all-lag closure. Static
queue delay repaired for observed geometry; remaining synchronous body admission/
persistence cost is a separate follow-up. Client exited; display3427180 and
poll3437497 confirmed gone, Java3436312 stays live. No world reset/manual state
repair/push. Current source commits local, governance changes WIP. Previous
R73 assignment and evidence below are historical; current result supersedes it.
CURRENT2026-10-09: user authorizes bounded settlement-boundary profiling and
repair. Main alone. Active implementation clean587b6cd4, detached release
/home/rd/proj/pm-departure-batch-r73-release-20261009. Actual live profile
build/profiles/r72-settlement-boundary-20261009.jfr paired with ordinary full-pack
boundary scenario e923fe4e-8f2d-4519-87c0-844510d886f3 PASS; same live R72 world,
warning2252ms/45ticks. Boundary8s sample window has117 server CPU/native samples:
47 body-ledger save,7 WAL-force,4 field-ledger save,59 other. These are samples,
not exact elapsed percentages. Deep stacks identify per-body saved-departure
acknowledgement as avoidable whole-ledger publication. Commit587b6cd4 publishes
one positively written body batch once; publication failure rolls back new saved
proof markers, not body/cargo/history. Exact current/write/sync/read fences remain.
Focused43 checks/guardrails/JAR/package PASS11s; native body-lifetime18 PASS;
clean detached build/package/guardrails PASS11s, same-world preflight PASS.
R72 normal save-all-flush/stop and all-dimensions-save confirmed07:46:50, old
PIDs2443547/2443498 exited. Exact R73 artifact installed, SHA512
b496a0c075e0a7982abb4cd691bc5b4ebeaa523134bd9ea1e4119da1677e9e653c2b6cf8d02c7ae60c81d51233b16f2127885bfbc534c0acd30db5a29665cce6.
Same-world R73 service start1791514050/invocationabf497463b2344268e7217518b297e07;
fresh verifier PASS, Java3303084. No world reset or physical/identity rewrite.
Candidate boundary scenario PASS/run67fb4d00-3839-4735-a904-3b133f97b038,
build/r73-boundary-profile-20261009.json and corresponding90s JFR. Terminal
597847/revision664554 green/all conflict counts0; later599784/revision665886
also green/0. No server keep-up warning in the fresh invocation. Whole action
window46s:501 server CPU/native samples,33 body-ledger,28 WAL-force,19 field-ledger,
421 other; no body-ledger save sample under departure acknowledgement. This is
not exact write counting or a general speedup measurement. First two client
visits still wait21.6s/16.8s for client ingress readiness (position + received
chunk); reentry0.8s. Source confirms readiness is broader than server tick delay.
Cause of that remaining client wait UNCONFIRMED; native chunk/cache work,
body admission writes and field projection remain in profile. Restarted JVM
vs8-hour baseline prevents a causal percentage comparison. No all-lag closure.
Client exited07:50:02; disconnect reset coincident with exit, not quarantine.
JFR auto-stopped; private display3284025 stopped, no task client/private server.
Code587b6cd4 is locally committed, not pushed; current governance changes WIP.
FOLLOW-UP2026-10-09: user asks to establish chunk-wait cause, not implement another
fix. Production source unchanged. One exploratory ordinary20FPS visit to previously
unobserved settlement9 with read-only first_visibility polling reproduced17.699s
ingress. Teleport07:56:30.149; field ready07:56:37 but target-22,21 still PENDING/
presentationReady=false; static READY07:56:47, client arrival07:56:47.873. Depot
ready dynamically07:56:35, statically PENDING until07:56:53. Cause confirmed:
PM initial static-presentation queue blocks vanilla chunk sends, checks entire
settlement/adjacent roads, and spends global8-cell/tick budget even on CURRENT
unchanged cells. Target has58 static cells but waits behind shared queue. Dynamic
field initialization contributed first~7s; remaining~10s held by static queue.
This is admission latency, not17s server freeze or demonstrated texture-meshing
delay. Existing JFR second-wise tick averages during first old visit fall to
2–4ms while ingress remains pending. Runtime diagnostic evidence:
build/r73-chunk-wait-{diagnostic.json,result.json,poll.log,client.log},
runba37e8f2-dde6-403a-8ee0-7cc90d11d008, terminalgreen/0. Temporary exploratory
scenario, not formal release/M3 acceptance. Client exited; display3320994 and
poll3321189 stopped. No restart/reset or production-code mutation.
Proposed next fix: separate read-check and write budgets, prioritize player target
and nearby chunks over package backlog, retain initial current-state/ownership
barrier and exact invalidation. Do not bypass barrier or bulk force-load sites.
Do not rerun terminal caravan/bakery cycles for confidence.
Prior results below are historical and superseded by this assignment.
CURRENT FOLLOW-UP2026-10-08: user requests working caravans at-346,64,-178 and fixes for all encountered active-feature defects, explicitly bakers. Main alone/no subagents. Source /home/rd/proj/pm-f06r3-facility-lane-recovery, Gradle root pale-mirror, clean HEAD2d4e6a02. LIVE R72 NEW world frontier-v3-caravan-bakery-r72-20261008, same seed20260918065/artifactd1a9b513; service active/fresh verifier PASS, invocation5687d208c62b44e29242c9b8d8defecb/start1791484036/wrapper2443498/Java2443547. Canonical hold released normally. Full-pack ordinary Clearwater→Ironmeadow→Clearwater→overworld boundary scenario PASS (build/r72-live-boundary.json, run7ee66a12-93ad-4cb3-a2d8-289081e0aa96), actual naturally loaded depots OBSERVED_CURRENT. Terminal6719/revision2812 and later10188/revision3843 green/scene0/required0/inventory0/custody0; client disconnected normally23:35:59. No new conflict/quarantine, but two boundary lag warnings2028/2025ms remain UNFIXED; cumulative PM physical max295ms/validation18ms/WAL31ms alone cannot attribute the2s server stall. Do not promote this narrow diagnostic to a measured latency closure. Old world frontier-v3-common-inventory-r65-20261008 preserved offline, NOT repaired. Both reported old groups COMPLETE/CLOSED and home members released COLD; exact repaired overlapping HOT birth still unobserved. R71 shared connected body-birth fix committed/deployed; pure14+architecture7/native local-navigation26 PASS.
Full actual donkey expedition completed as linked SAME-world native segments: provisioning/HOT motion→normal restart→16bread delivery/payment32→home return/actors+animal+budget release. Before and terminal manifests green; after manifest partial failed due absent final summary action, intentional clientTERM/normal server save-stop. Missing terminal subset alone PASS, seller132/buyer68, same donkey UUID60c79a93-cc39-3460-b31b-342c50275e25. Exact evidence/limits docs/frontier-v3-caravan-bakery-r72-20261008.md. No whole-trip rerun/full original green/HUMAN/M3/push claim.
Baker causes confirmed: depot custody wrongly readmits no-demand scene during native unload; exact insertion pending misclassified CONFLICT; saved PREPARED body no longer recognized after insertion-save acknowledgement. Repair committed d1a9b513: shared demand/readiness admission, no birth into hidden/unloading section, exact pending→DEFERRED, current saved PREPARED-body recognition, guarded family recovery through ordinary body owner/unchanged exact batch receipt. Focused pure/architecture/guardrails PASS; native production2 PASS/body-lifetime18 PASS (old pending expectation corrected, no-duplicate preserved). Clean detached R72 build/package PASS; first SAME-world deploy verified but live recovery visit FAILED:9-15 absent/CONFLICT after90s. Saved ledger all4 has physical_history but no inactive/pending/body-departure proof for current PREPARED incarnations. Read-only persisted entity-region scan finds0 selected bodies (selected dimension only, not a global absence permit), after normal save/stop same result. Old corrupted test history is not manually healed or called recovered; fresh-world policy used above. First unconnected pack pilot boot copied43GB of worlds, second stopped hashing; commitd61122d3 filtered client Sync and removed redundant full-root input. Sync PASS80s including one-time cleanup, profile245MB/preflight check PASS. Actual bakery full-cycle first native FAILED invalid visit height65 vs actual64; product had already taken wheat/loaded station/processed. Commit12f650be corrects declared visit heights and explicit canonical hold release before/after restart. Corrected ordinary native full-cycle+graceful restart PASS, build/r72-bakery-complete-restart.json/run790f6e30-2b94-4f2e-8b93-6161f13d68b1: actual resident1-9 pickup→station load→graceful restart→processing80→physical unload→depot delivery64bread→job retired; canonical output64bread and actual chest64bread/OBSERVED_CURRENT/no pending mismatch. Linked frame inspected, not M3/human acceptance. Private server port26720 closed/client exited; final runner cleanup exact_owned_post_semantic_disposal, not an invented normal final save. No task client remains; private display92 stopped after final live diagnostic. No manual canonical/WAL repair/push/HUMAN/M3 claim. Next unresolved performance question is attribution/removal of actual settlement-boundary2s stalls; do not rerun completed whole caravan/bakery cycles merely for confidence.
## Runtime identity and next work
Deployed clean detached implementation ref 57f0278ea278015f26be7d84d0008080e35ca65d,
runtime /home/rd/far-frontier-server, service far-frontier-v3-live.service.
Installed+hosted Pale Mirror SHA512:
3f9279ae8a9b020516fdf0ea6b38cb5b75c2379efba12020a4060b3864aaf7952d195ad32fcb62d0756e256c57705fd6dc91d353b7fb60bfdc151f13a3dc7a48.
Visuals unchanged SHA512:
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
No active goal. Implementation clean, optimization committed/deployed above. Prior user-authorized push completed for
implementation2d4e6a02 and scoped governance5ab4de83; current R73/R74 not pushed.
Original pack root has unrelated .f0v-baseline; original nested Pale Mirror has
23 unrelated WIP paths, untouched. Governance mixed WIP must not be blanket staged.
No task test client/private server/display remains; live service stays running.
Current profiling/correction evidence is above; long static ingress-queue delay
is repaired in the observed boundary scenario. Remaining approximately2s tick
lag is not closed; intermediate JFR points to synchronous body admission and
persistence fences, not a final exact attribution. Do not infer a repair from
cumulative stage maxima, or reset the
world for confidence. Preserve old corrupted world for its named body question.
Do not manually clear conflicts or synthesize lost identity history.

## Retained history
Older evidence and assignments are preserved in
docs/archive/continuity-2026-10-08-r72-before-compaction.md.
Those assignments are historical; only the current assignment above is active.
The R72 receipt owns detailed causes, test limits and artifacts:
docs/frontier-v3-caravan-bakery-r72-20261008.md.
