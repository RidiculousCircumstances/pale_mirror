# Sequential delay attribution and optimization

User approved fresh end-to-end delay attribution followed by sequential repair of
measured costs. Main alone. Preserve canonical order, exact state/detail, physical
ownership and WAL durability; no worker-count/ruleset shortcuts or new scheduler.
MAIN world retained. Existing JFR producer/analyzer remains the single tracing path.

## Initial mature-world attribution

Production cbeb1f6e3, SCALE100 settlements/about3031 residents, ordinary simulation,
no player/fast-forward or concurrent task build.60s capture: ticks451121–451642,
8.686147/s **inside the profile**, not ordinary throughput. Owner CPU26.029s;
exclusive owner stages25.978s, exact running wait14.225s, WAL running wait9.761s,
owner uninstrumented6.281s. Unexplained10.795%;593631events, zero JFR data loss.
Graph references close, dependency closure is incomplete; unknowns remain visible.

Largest owner details: due-action allocation4.214s, owned-state validation3.319s,
COLD harvest planning1.810s, scheduled-subject references1.424s, ambient1.199s,
meal planning1.003s. Stack sampling implicates shared destination claims, readiness
maintenance and inventory/field checks, but sample counts are not exact durations.

## Connected detail capture

Extend optional existing detail spans: named owner invariant callbacks; admission
held/proof/wake-key evaluation by action kind; transaction wake/retirement/
reconsideration/publication; navigation host retention/progress/readiness delivery.
The lexical validation context is installed only with enabled detail capture;
no cache, checks, scheduling, clocks or publication permission come from tracing.
Observer failures cannot affect canonical work, and nested failure restores scope.
Focused tests compare canonical bytes with/without/failing metrics and validate
failed check inputs, scope cleanup and the unchanged admission/collection contracts.

Then inspect actual duration ranks and callers, repair the largest connected
contradiction, verify affected negative/recovery semantics, and compare ordinary
clock progress from an identical saved checkpoint. Repeat only for a newly measured
remaining cause. Instrumentation alone is not this task's delivered product result.
Commit and deploy the verified final candidate through existing root scripts.

Receipts: `build/simulation-throughput/causal-sequential-optimization/`.

## Detailed attribution and first connected fixes

Existing optional producer expanded in6e05ab181.57 focused Frontier cases and one
NeoForge trace case, guardrails and frozen JAR/package checks passed. No business
change in that diagnostic build; MAIN remainedcbeb. Same mature SCALE world retained.
30s detailed capture:6.215444ticks/s in-profile, unexplained6.8045% (still not full
causal closure). Largest detailed costs include COLD-harvest admission.554s,
resident-activity admission.506s, extraction admission.406s; navigation host
retention/advance/wake .298/.646/.249s; transaction wake-key compilation.601s;
owned-state remainder1.210s; scheduled-reference checking.536s.

Source-proven repeated work repaired next:

- Scheduled-subject existence joined all machine operations and all field jobs
  whenever either machine or site state changed. Separate complete station-ID
  publication and per-site immutable registry declarations now retain the union
  through progress-only changes; actual birth/removal still invalidates references.
- Service destination claims rebuilt whole actor, movement and meal directories
  when one source row changed. Existing immutable registry/group views now update
  changed spatial memberships and retain immutable read images. Another actor on
  the same surface remains a blocker; incoming COLD arrivals and preferred meal
  spots retain their original distinct semantics.
- Economic account identity existence was scanned unconditionally by every world
  validation. Existing reverse-reference checks now follow each account's explicitly
  declared legal kind and the corresponding company/resident membership. This
  does not add personal money/accounts; absent/new/removed identities still fail.

The first two paths passed66 focused semantic cases and55 native economy cases;
subsequent account-reference change passed24 focused composed/negative cases.
The new account fixture initially assumed every resident starts with a financial
account; corrected to explicitly declare a zero-money title account in the test.
Production creates no such accounts. Full-audit/failed-predecessor and removal/
branch-rollback behavior remain covered. No timing assertions or new client harness.

A new complete109-file native-saved mature checkpoint, source6e05ab181, is hashed
with JVM/config identity under this task's receipts. Ordinary baseline and candidate
will start from that same checkpoint, without profiling/build/client overlap.

## Ordinary native comparison

Both legs restored the exact mature109-file checkpoint, checked manifest/config/
JVM args and used100 settlements with the same two advice/two navigation workers.
Recovery completed, then60s warmup and seven10s-spaced samples. No task build,
profile or client overlapped either ordinary window. No fast-forward or recovery
in any sample; all fourteen samples statusOK.

| Source | Ticks | Seconds | Ticks/s | Debt before → after |
| --- | ---: | ---: | ---: | --- |
| 6e05ab181 (detail producer, old calculations) | 590 | 61.881 | 9.534494 | 1185 → 1833 |
| 260f61a5b (sparse membership updates) | 698 | 61.790 | 11.296376 | 1133 → 1671 |

Observed throughput +18.479%. Work intensity was similar by revision count:
11993/590 versus14180/698 revisions per logical tick. Shared evolving host,
asynchronous scheduling and different advancing canonical start positions remain
limits; this is not an isolated contribution estimate or a confidence interval.
Do not combine this percentage with the prior different-checkpoint comparison.
Scale20ticks/s without growing debt remains UNMET; debt still grows.

## Reprofile and remaining costs

A separate30s detailed JFR after the optimized ordinary window is attribution,
not the ordinary throughput measurement. Its5.779834ticks/s must not be substituted
for11.296376. No JFR data loss; unknown7.219%, capture-edge/missing references and
incomplete causal closure remain explicit. Owner stages14.234s, exact-running
wait7.175s, WAL-running wait4.592s, owner uninstrumented1.656s.

Per-invocation CPU from recorded stage counters (inclusive for each named span;
never add nested rows) helps distinguish changed cost from changed call volume:

| Span | Before calls | Before mean CPU µs | After calls | After mean CPU µs |
| --- | ---: | ---: | ---: | ---: |
| Scheduled subject references | 1850 | 255.61 | 1598 | 159.22 |
| Harvest continuation admission | 3532 | 136.88 | 2704 | 102.17 |
| Extraction continuation admission | 10989 | 30.52 | 9570 | 27.82 |
| Resident activity admission | 2895 | 140.57 | 4439 | 157.74 |
| Transaction wake-key compilation | 2126 | 258.35 | 1818 | 343.04 |

Thus the target reference check became cheaper; resident activity and wake-key
costs are NOT claimed fixed. Their workloads/branches differ across profile
windows. Owned-state remainder also remains substantial (1.319s exclusive,
908.17µs mean inclusive CPU across1598 invocations); the new account-reference
check itself occupied.0155s, not an explanation of the whole bucket.

Blocking-task attribution now identifies local hauling review1.799s/96 distinct
blocking tasks and goods opportunity1.759s/610; WAL gates together4.889s. These
are exclusive clock-blocking portions, not sums of parallel worker CPU or route
lifetimes. Subsequent targets are the real hauling/trade calculation inputs,
activity admission and event wake compilation. No further production changes to
those calculations were made in this cut. Remaining optimization is open.

Receipt summaries: `native-summary.json`, `detailed-summary.json`,
`optimized-summary.json`, `span-invocation-summary.json`. Complete source-bound
JFR, normalized trace, causal reports, HTML and Perfetto are under each capture's
`report/`. The per-span summary is a bounded query of existing normalized evidence;
there is still one causal analyzer and no second runtime telemetry engine.

## Delivery and evidence scope

Final production `260f61a5b3500efe0aaf9825d0ddd18bb26cbcff`, clean detached RELEASE,
guardrails/JAR/package checks passed22s. MAIN and SCALE verified installed SHA512:
`176da4bb63bec98ecac54a22cfb0d8c5326859e59850cbc1841becc83f8986dc8d18d8efb7e07f9988e0aa27b1568ba86d330e8c74abd0ea51b88462052ec29a`.
Existing root publish/install/preflight/verify owners used; native save/exit and
fresh startup checked. MAIN world retained throughout. Services remain active;
actual Java at final check MAIN3749021/SCALE3636623 (resolve again before ops).
Three final samples each: statusOK, no fast-forward/recovery; MAIN debt0, SCALE
continues accumulating debt. No new ERROR/quarantine in fresh runtime logs.

The five sampled towns had no starving residents; some hungry residents/active
meals remained. This is not a complete economic cycle, HOT/five-player, caravan
arrival or human visual acceptance. Physical economy gates cover their declared
adapter cases; ordinary scale observations cover runtime health/progress only.
No task client remains connected. Final RCON response contained an additional
players diagnostic; the check now selects the exact performance envelope by kind
rather than assuming there is only one diagnostic in the response. No server
fault was hidden by that method correction; original response/log is retained.

IMPL committed; RELEASE clean at exact production ref. PACK tracked files remain
unchanged at f727133, unrelated `.f0v-baseline/` preserved; governance WIP separate.
No automatic push. Deployment/final checks are in `deploy-main/`, `deploy-final/`,
`installed-identity.json` and `final-runtime-check-typed.log` under this task's receipts.

## Fresh read-only analysis, 2026-10-30

User requested new latency analytics, without another implementation increment.
Installed production remains260f61a5b, with the same frozen SHA512 above on MAIN
and SCALE. No restart, checkpoint restore, fast-forward, client, build, code or
configuration change during this observation. Both service JVM identities were
checked before and after the ordinary window. Use the existing capture/analyzer;
this section introduces no second tracing system.

Receipts: `build/simulation-throughput/delay-refresh-20261030/`, including
`identity.json`, seven raw samples per server, `ordinary-summary.json`,
`profile-summary.json`, `extra-summary.json`, `span-summary.json`, and
`scale-trace/report/timeline.html`. Ordinary observation began01:26:25 UTC+05.
The separate30s SCALE profile followed that ordinary window.

| Runtime | Ordinary seconds | Canonical ticks | Ticks/s | Debt before → after |
| --- | ---: | ---: | ---: | --- |
| MAIN,12 settlements | 63.285 | 1265 | 19.989059 | 0 → 0 |
| SCALE,100 settlements | 63.284 | 1034 | 16.339049 | 6614 → 6846 |

All fourteen diagnostic samples statusOK, explicitly not recovering, no
fast-forward. No ERROR/quarantine or logged PM slow-tick/ready-turn span above80ms
in this bounded ordinary window. This does not prove a universal80ms maximum or
HOT/player/chunk latency. SCALE debt grows3.666ticks/s: about11seconds of canonical
time lost per wall minute at this observed rate. Revisions/tick1.935 on MAIN versus
23.821 on SCALE; scale work intensity must not be inferred from town count alone.

The old11.296376/s and fresh16.339049/s use the SAME binary but different advancing
world states and work mixes. This is not a new optimization gain or a controlled
before/after result. The earlier same-checkpoint +18.479% result remains separate.

### Exclusive clock-blocking partition

Inside the fresh profile:469ticks,15.637855ticks/s; debt6852→6982,599 observed host
ticks, exact debt-balance difference0. Owner CPU13.096s. The following rows account
for disjoint portions of30.002s elapsed time; background CPU is reported separately.

| Gate/activity | Seconds | Window share |
| --- | ---: | ---: |
| Instrumented owner/server-thread stages | 12.376 | 41.25% |
| Waiting on exact calculations, all phases | 7.951 | 26.50% |
| Waiting on WAL confirmation, all phases | 5.352 | 17.84% |
| No ordinary execution allowance, BUDGET | 2.502 | 8.34% |
| Ready-dispatch thread park/sleep | 0.993 | 3.31% |
| Unexplained, including uninstrumented owner work | 0.808 | 2.69% |

Small remaining host/park/GC intervals complete the partition. Exact work is mostly
RUNNING7.758s; QUEUED only0.039s, result-ready waiting for owner0.131s. WAL RUNNING
5.319s, queued0.004s, result-ready waiting0.022s. Thus a long queue before starting
these exact tasks is not the dominant gate in this window. WAL worker CPU is only
0.117s;65 native samples point to `UnixFileDispatcherImpl.force0`. This supports
durability-confirmation/I/O waiting, not five seconds of serialization CPU. It does
not isolate physical-device latency from scheduling or other storage contention.

BUDGET cause is EXECUTIONS2.421s, CLASSIFICATIONS0.078s, WEIGHT0.003s. Runtime
`clockTraceGate()` checks allowance before pending calculation; this is the observed
active guard, not proof that all this time would disappear after raising limits.
Preserve host responsiveness and canonical order when assessing this cost.

Largest owner stage groups: validation3.169s, allocation/admission2.506s,
planning2.474s, transactions1.611s, reduction1.319s, physical observation1.104s.
Within them, exclusive detail includes owned-state remainder1.132s, transaction
wake-key compilation0.835s, COLD harvest planning0.812s, harvest admission0.661s,
resident activity admission0.484s, meal planning0.480s, due-action allocation
remainder0.443s, trade relation validation0.429s and scheduled references0.384s.
These details are subsets of the group totals, not additional elapsed time.

### Background CPU versus actual clock blockers

Measured task CPU counters: goods participant review13.273s, trade opportunity
3.817s, local hauling1.805s, navigation slices1.451s and objective review1.137s.
Parallel CPU seconds cannot be added to the exclusive wall table. In particular,
13.273s review CPU does not mean the clock waited13.273s for that review.

Largest exact blocking-task contributions: hauling1.651s/228 distinct blocked
tasks; trade opportunity1.388s/1402; objective review1.037s/125; cultivation
0.786s/231; movement progress0.740s/2673. Counts are task IDs, not complete routes
or total work invocations. Navigation is not the largest measured CPU consumer.
Worker stack samples additionally identify field-cell reads, agricultural yield,
renewable capacity forecasting and procurement travel forecasting. They are leads
to connected economic calculation inputs, not quantified costs of those helpers.

Existing named stage counters give mean inclusive CPU/call: scheduled references
81.18µs/4718 calls, harvest admission63.96µs/10339, activity admission68.43µs/7041,
extraction admission12.37µs/28873, wake keys152.67µs/5461. Nested spans cannot be
summed. Against the previous capture these figures also show why total duration
alone cannot distinguish per-call cost from changing call volume/world state.

### Conclusions and limits

MAIN currently sustains20ticks/s in this sample; SCALE does not. The largest
remaining connected areas are server-side validation/admission/wake maintenance,
economic forecasting and hauling calculations, and the durability gate. Merely
splitting queues or increasing worker count does not address the measured mix.
Next source investigation should quantify repeated input construction/cross-owner
invalidation in those economic paths, then assess safe WAL batching and the
execution allowance separately. No speculative repair was made in this analysis.

819971 events, zero JFR data-loss events. Unexplained2.694%, two absent graph nodes
and19 absent append records for the first observed flush remain explicit. Causal
closure is INCOMPLETE; do not call this a fully closed dependency trace or interpret
missing trace records as missing durable game writes. The analyzer and HTML retain
these limitations. Both services remain active; no observer/profiler/client remains
running. Source changes are documentation only, so no new binary is deployed.

## Four-category optimization following the fresh trace

User authorized actual optimization of all four measured cost groups. Preserve
canonical order/detail, physical custody, exact write-before-effect and the
persisted execution quotas. No new scheduler, worker pool or reactive framework.

Source-proven work and the connected implementation:

- Harvest stock commitments reread every active field after any site change.
  Existing immutable read projections now select only changed site/job/cycle and
  declaration readers. Per-site mature yield is reused across job-only changes;
  flattening retains the original producer order. Field capacity separately
  retains local cell facts across foreign edits and economic drafts.
- Hauling's winning destination called the complete shipment preparation twice.
  The common ordered storage selection now carries its successful producer result
  instead of discarding it after a predicate. Rejected/pending routes and partial
  quantities retain the same selection behavior.
- Every complete state audit called geometry bounds traversal even when its cell
  image was unchanged. The existing invariant mechanism now retains the exact
  bounds + cells success; changed cells and full ingress still run the check.
- Exact calculation resumption repeated business admission and spent another
  execution slot for the same retained predecessor. It now polls under its
  original admission, while accepted commands still cancel the old calculation.
  Classification exhaustion no longer discards the independent execution budget;
  after the admitted calculation completes, an exhausted execution budget cannot
  keep rescheduling classifications. Host signals never renew the original quota.
- WAL admission globally stopped even for BATCHABLE work while another immutable
  group was being forced. One ordered writer now permits a BATCHABLE memory tail
  within the SAME combined 64-record/64MiB bounds. Tail expiry/capacity still yields;
  a critical effect waits for its own complete prefix, never an earlier receipt.
  Durable progress renews the no-progress watchdog even with a continuous tail.

The WAL scheduling amendment is explicit in canonical execution semantics and
architecture. Focused checks cover independent budgets, stale command cancellation,
changed geometry rejection, immutable forecast branches/recovery, pipelined prefix
limits, writer failure and a critical effect waiting for both groups. Existing
native economy coverage and a same-checkpoint ordinary scale comparison follow.
Receipts: `build/simulation-throughput/four-gate-optimization/`. Implementation
and measured results are not claimed complete until the source-bound checks and
deployment results below are recorded.

Initial kernel/model selection passed89 tests. The first adapter run exposed one
obsolete expectation that every queued group blocks all BATCHABLE continuation,
and two controlled-writer cleanup timeouts (the test held its own close task).
Updated the expectation to assert actual pipelined progression/recovery and made
the controlled lane release before store close. No production timeout was raised.
Final selected checks passed:4 field/valuation cases,9 architecture cases,
22 adapter durability/store/runtime/readiness cases;55 required native economy
cases; guardrails and packaged-JAR verification. Final command completed94s.
The earlier unaffected kernel/cancellation/order checks remain retained evidence.

Fresh baseline260f61a5b restored the identical109-file checkpoint, recovered,
warmed60s, then advanced588ticks/61.845627s =9.507544/s; debt1454→2103.
11539 revisions,840 WAL groups,11536 BATCHABLE appends and3 required-effect appends
in the measured window. No build, profiler or task client overlapped that window.
Candidate results follow from the same restored checkpoint and configuration.

### Delivered result: cab6e1a23

Clean detached source `cab6e1a23d268f9f1f80b82c209a5035bf50aaaa`, frozen guardrails/
JAR/package verification passed17s. Both SCALE and MAIN installed/verified SHA512:
`3457d17ee601694601d4a03257d94c3a9ccb4d2e64395baf38a6dcb7e40a72ec653ff82ec045b54340d6fcf21e1be6dfd405c9d93babdcebd80ad9b727c2b2b0`.
Normal Minecraft saves/exits preceded installation; MAIN's existing world was
retained. SCALE alone restored the same hashed109-file checkpoint for both legs.

| Ordinary SCALE leg | Canonical ticks | Seconds | Ticks/s | Debt before → after | Revisions/tick |
| --- | ---: | ---: | ---: | --- | ---: |
| Baseline260f | 588 | 61.846 | 9.507544 | 1454 → 2103 | 19.624 |
| Candidatecab6 | 874 | 61.745 | 14.154971 | 882 → 1243 | 20.005 |

Observed throughput +48.881%; growing debt649→361ticks per measured window.
Candidate did more state-changing work per tick, not less. Both legs recovered,
warmed60s and used seven10s-spaced samples, unchanged two advice/two navigation
workers and hashed ruleset. No task build, profiler or client overlapped either
ordinary window. Different advancing start instants, asynchronous work mix, JIT
warmth and shared host remain comparison limits. This does not establish a
constant speedup, isolated contribution per fix or sustained20ticks/s.

Candidate performed17484 revisions,17479 BATCHABLE/5 required-effect appends and
1025 forces. More absolute forces than baseline840 accompany substantially more
work; groups/1000 revisions fell72.80→58.63. Execution-budget exhaustions64→27,
but candidate also exhausted actions19 times, classification2 and weight1; quotas
were not raised. `comparison-summary.json` retains the exact ordinary samples.

Separate30s candidate JFR is attribution only:5.582029ticks/s inside the capture,
165 advanced ticks, debt1451→1896. It must not replace the ordinary14.154971/s.
This phase has a different workload and substantially greater worker CPU than the
previous advancing-world profile; do not treat the following wall rows as a
controlled component-speedup experiment.

| Exclusive clock category | Prior fresh260f capture, s | Candidate capture, s |
| --- | ---: | ---: |
| Instrumented owner stages | 12.376 | 16.799 |
| Exact calculation wait, all phases | 7.951 | 9.185 |
| WAL wait, all phases | 5.352 | 0.070 |
| Budget gate | 2.502 | 0.000325 |

The two observed waiting gates became very small; the remaining computation
categories did NOT all become cheaper. Candidate owner CPU10.150s is separate
from owner wall intervals. Process CPU273.31s over the34.90s capture envelope,
versus54.05s in the previous fresh260f capture envelope, is another clear warning
against declaring those different phase windows equivalent.

Current worker task CPU counters identify navigation slices35.091s, trade
opportunity16.102s and economic review9.224s. Exact clock blockers include trade
opportunity2.213s, movement1.470s, hauling1.288s, objectives1.279s and cultivation
0.945s. Navigation worker stacks include layered-region edge construction;
trade workers include selected trip preparation and comparison. These remaining
paths need connected input/reuse/invalidations analysis, not larger execution
quotas or a claim that queues are now solved. Thread CPU/heap evidence is retained
in `scale-thread-cpu.txt`; the one snapshot does not establish a GC leak or JIT
regression. The existing trace configuration does not record GC-cycle/allocation
counters, so its negligible stop-the-world pauses do not measure concurrent GC CPU.

312626 events, zero JFR data-loss events, unexplained10.1125%, four missing graph
nodes and no missing WAL append members. Causal closure remains INCOMPLETE.
`candidate-profile-summary.json` and `candidate-trace/report/` preserve the gaps.

Final ordinary three-sample checks after MAIN deployment: both runtimes statusOK,
explicitly no recovery/fast-forward, no fresh ERROR/quarantine. MAIN advanced
2080230→2080655 with debt0; SCALE459596→459823 with debt3342→3539. Thus a later
work phase still accumulates debt; the paired14.15/s is not a universal rate.
Five sampled SCALE towns had zero starving residents before/after both legs,
with some hungry residents/active meals. This is not an entire economic-cycle,
HOT/five-player, caravan-terminal or human visual acceptance claim.

No task client, profiler, build or observer remains active. IMPL task changes
committed, RELEASE clean at exact production source; PACK tracked files unchanged,
unrelated `.f0v-baseline/` preserved. Canonical governance amendments are kept with
its preexisting WIP. No push. Receipt groups include `deploy-{baseline,candidate,main}`,
`native-{baseline,candidate}`, `final-verification.log`, `frozen-build.log`,
`candidate-trace.log` and `final-runtime-check.log`. Sustained SCALE20/no growing
debt remains OPEN; no old paused goal is marked achieved by this increment.


## Addressed delivery facts and wake deltas (October30 follow-up)

The next source-proven costs were global invalidation of provisional delivery
lengths on every actor/missions/groups/surfaces edit, sorting and scanning all
missions for each endpoint pair, and scanning every actor execution and every
settlement to derive local wake aliases. These are preparation/maintenance costs,
not evidence that Reactor or more worker threads are required.

Delivery advice now consumes existing immutable read/group projections: a
container's declared position reads only its mobile actor's supporting surface;
a retained mission length reads its own mission and exact group's existence.
The pair lookup preserves the former smallest-mission-ID selection and outward/
return direction rules. Complete value inputs use the existing bounded256-entry
memo. Inventory admission and identical-draft quote memoization remain current;
this change does not reuse stale receiving capacity, authorize a trip from an
estimated length or waive current provisioning/geometry checks. Hydration and
alternative immutable planning branches have independent source epochs.

Wake maintenance reuses ImmutableMapChanges for execution, storage policy,
surface, stock-account and custody changes. Bootstrap settlement aliases are
prepared once, then only changed addressed records are inspected. Removed
storage/surfaces and the previous storage owner now receive wake keys as well.
This does not remove the conservative all-settlement geometry wake or the
existing resource-site first-match hint scan. These hints grant no owner,
behavior, stock, movement or execution authority.

Navigation retention policy/capacity is unchanged. New bounded diagnostic
history (at most512 exact evicted bucket/version keys, cleared on scope retirement)
reports completed hits, layered misses, overlapping new-version misses and
recent exact-version evicted rebuilds. It does not drive retention or traversal.
Recent rebuilds are a lower bound: older evictions fall outside the history;
absence from it does not prove previously unseen geography. Native measurements
below decide whether actual repeated-key pressure warrants a separate policy
change. Certified analytical planes remain deliberately outside graph retention.

Verification and deployment/measurement receipts are retained under
`build/simulation-throughput/addressed-forecast-wake-optimization/`.
The existing cab6 same-checkpoint ordinary leg is the unchanged baseline;
no numerical improvement is claimed before the candidate is measured.


### Delivered measurements: d8d93de1d

86 focused forecast/advice/activity/storage/navigation tests passed. Final changed
storage/missions/hydration plus architecture selection passed18 cases; native
Minecraft economy passed55 required cases. Guardrails/JAR/package passed79s;
clean detached release repeated packaging/guardrails successfully26s. One new
fixture local-name collision was corrected before the successful final run.
No new physical execution or durable schema was introduced, and unchanged adapter
WAL/order evidence from cab6 remains retained rather than repeated.

| Same-checkpoint ordinary SCALE leg | Ticks | Seconds | Ticks/s | Debt before → after | Revisions/tick |
| --- | ---: | ---: | ---: | --- | ---: |
| Prior cab6 baseline | 874 | 61.745 | 14.154971 | 882 → 1243 | 20.0046 |
| d8d93de1d candidate | 902 | 61.773 | 14.601885 | 779 → 1113 | 19.6674 |

Both restored the exact complete109-file checkpoint, recovered explicitly,
warmed60s and sampled seven times10s apart with unchanged rules/JVM/workers and
no task build, profiler or client during observation. The candidate started54
simulation ticks later in the advancing workload. Observed difference+3.157% is
small, with different work mix/JIT/shared-host conditions; this is not evidence
of a reliable3% speedup or sustained20ticks/s. Do not compound it with previous
cuts. Debt still grew334ticks in the candidate window (baseline361).

The candidate made8490 layered misses, with2107 recent exact-version evicted
rebuilds (24.82% lower-bound count share),41563 completed hits and only3 misses
whose tile still retained another version. The latter is not a complete count
of geometry invalidations. At most512 evictions are remembered, so other missed
keys may also be previous versions or evictions. Graph building CPU is not
measured by these counters. Baseline completed9445 layered graphs versus8492
candidate graphs, but work-mix differences prevent attributing that delta to a
particular change. Cache churn is confirmed; its optimal policy and isolated CPU
contribution remain unproven. No speculative capacity or retention change made.

Five sampled towns had0 starving residents before/after, ordinary meal activity
and existing terminal harvests. This narrow check neither certifies all100 towns
nor establishes new HOT/player/product acceptance. Both runtime deployment
receipts and final health samples are linked in the evidence directory; MAIN's
world is retained and only SCALE was checkpoint-restored. Installed artifact:
`e3493e7015fd0eab278b98584c3c5a3466e089a422c448694ccf203a691db0cbcb04046e41a69bad7a5de0ca7409fde5ea5ff838829c6de4ad23f068ddd059d0` (SHA512).


Final three ordinary checks explicitly reported OK, no fast-forward and
recovering=false on both runtimes. MAIN instant2115227→2115651, debt0; SCALE
458786→459066, debt1811→1954. Fresh logs contained no ERROR/quarantine. Actual
Java22 processes MAIN334816/birth36458391 and SCALE320292/birth36438257 had the
same installed SHA512 as the frozen artifact. No task client/profiler/build or
observer remains active after the checks. Implementation and pack are checked
separately; no tracked pack changes were needed, and unrelated `.f0v-baseline/`
was preserved. The remaining goal is sustained scale20ticks/s/no debt growth,
with cache reuse/working-set policy and connected preparation cost still open.


## Reusing live regional graphs after eviction (October30)

The measured repeated builds have a concrete source-level duplicate-work path:
HierarchicalPedestrianSearch, CorridorJoin and MacroSearch retain completed
immutable graphs, while the shared bounded cache can evict the same version.
Another request then builds another copy although an admitted reader still owns
that graph. Strong-cache eviction need not mean the completed object is gone.

The existing bounded512-key eviction directory now holds weak completed-graph
references instead of only Boolean history. An exact key hit returns the existing
graph in one work unit before surveying; it creates no builder, does not reinsert
or pin the graph in the strong cache and adds no strong graph retention. Collected
or absent weak references follow ordinary construction. Retirement/clear severs
all old sharing. Geometry scope/profile, local version and hard bounds remain in
the original key; changing the world or a local collision halo cannot borrow the
old graph. No navigation detail, path checks, worker count, strong cache capacity,
ruleset or persistence contract changes. New weakGraphHits counter distinguishes
queries served through weak sharing from ordinary strong-cache hits and remaining
recent rebuilds. It is a query count, not a distinct avoided-build count. Ordinary eviction and GC can still require later recomputation.

The existing pressure test now holds an evicted graph as an active reader would,
then proves identical-object reuse with a one-unit allowance and unchanged build
count. It also checks changed local geometry cannot reuse it, and cache clear
severs sharing even if that old object remains alive. Existing profile, scope,
non-flat, unknown, blocked route and worker checks remain selected.
Receipts: build/simulation-throughput/shared-region-reuse/.


49 initial selected navigation/architecture checks passed; guardrails and JAR
packaging passed32s. Updated pressure/changed-version/clear checks passed in the
final focused run. The additional adapter selection had3 passes and1 failure:
FrontierV3PedestrianPlanningTest.restartRebindsLostCalculationAndActualServerCompletionWakesWaitingGroup.
The unchanged clean d8d93de1d baseline reproduces the same failure at simulation
6005 with group READY, worker pool/search work/region lookups all0. It is therefore
not attributable to this graph-reuse change; its cause remains OPEN. The test
has not been weakened, removed or reported green. Current changed calculation
checks and source-key/retirement invariants are verified, not complete adapter
or product acceptance. Existing unaffected native economic evidence is retained.


### Fresh comparison and delivery: 4f195546f

Source4f195546f86704d6ec520af70376ed4671bc306b; frozen guardrails/JAR/package
passed15s. Candidate measured first, then clean unchanged d8d93de1d rebuilt and
measured in this session because the earlier baseline was several hours old.
Each leg normally saved/stopped, restored the same hashed109-file SCALE checkpoint,
recovered explicitly, warmed60s and took7x10s samples. No task build/profiler/
client overlapped either ordinary measurement. Same rules/JVM/two advice/two
navigation workers. MAIN is retained, never checkpoint-restored.

| Fresh ordinary SCALE leg | Ticks | Seconds | Ticks/s | Debt before → after | Revisions/tick |
| --- | ---: | ---: | ---: | --- | ---: |
| d8d93de1d baseline | 964 | 61.7803 | 15.603675 | 794 → 1066 | 19.6525 |
| 4f195546f candidate | 994 | 61.7332 | 16.101543 | 814 → 1055 | 19.6006 |

Observed rate difference+3.1907%, not sustained20/no growing debt. Candidate first
sample was339simulation ticks later; phase mix, warm-up/JIT/GC and shared-host
variation remain limits. Do not compound this with previous gains or treat the
small difference as a universal guaranteed speedup.

Recent exact-version eviction rebuilds2104→0; layered misses9137→8561; completed
layered graphs9134→8562; total region lookups653074→654836. Candidate44126 completed
hits include2775 weak-sharing queries. These are actual lookup counts, not2775
distinct builds saved. The512-key bound means zero recent rebuilds does not prove
zero repeated builds outside its retained history. Cleared/collected or older
unindexed graphs still require ordinary reconstruction. Both returned immutable
graphs and newly built graphs retain the same exact collision/version checks.
The measured duplicate-work path is removed; other owner/worker costs still
prevent20ticks/s. No cache capacity increase, time/detail reduction or new worker.

Source-bound artifact SHA512:
`de51e28d82aa918cba72bbf664878b0839955a8706adefd467c2b09c447d6cce8a174e1e336c597ee714f9beb641d16e5c1acc5d72a146da55e6efd071a13faa`.
The measured candidate JAR is retained in ignored receipts and the final rebuilt
artifact must match it before deployment. Complete receipts are under
build/simulation-throughput/shared-region-reuse/, including comparison-summary.json,
native-{candidate,baseline}/ and deploy-{candidate,baseline,candidate-final,main}/.


Final candidate rebuild matched the measured JAR byte-for-byte (SHA512 above).
Both servers installed/verified that artifact through the existing root owners
after normal native saves. MAIN world remained frontier-v3-r281-cold-atomic-20261028;
SCALE alone restored scale-r31-schema313-seed126. Final three ordinary checks on
each explicitly statusOK/noFF/recovering=false: MAIN2427612→2428036,debt0;
SCALE456500→456788,debt841→978. No fresh ERROR/quarantine in either log. Java22
MAIN2717791/birth38024294, SCALE2714602/birth38020191. Five candidate-sampled towns
had0starving residents before/after and ordinary meal activity; not full economy,
HOT/player or M3 acceptance. No task observer/build/profiler/client remains active.
Implementation working tree is clean after the results commit; pack has no new
tracked edits and unrelated .f0v-baseline/ remains untouched. No automatic push.
The preexisting adapter recovery failure above remains open and is not concealed
by the successful graph checks or live health. Sustained scale20 remains OPEN.
