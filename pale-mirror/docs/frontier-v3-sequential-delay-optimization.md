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


## Shared directed regional boundaries (October30)

Inter-region expansion and the isolated-goal incoming probe previously enumerated
border surface pairs and called body traversal again in every search. The
existing region graph shared only internal edges. The existing individual edge
memo holds8192 answers per profile/scope; its poor hit ratio cannot by itself
prove that a bigger memo would solve the workload.

PedestrianRegionCache now owns a bounded PedestrianBoundaryCache under its same
scope lifecycle. Each directed boundary publishes all valid portals, with source
and destination component IDs, independently of the search's goal. Hierarchical
expansion selects the same goal-dependent winning portal from those facts;
the target probe consumes the reverse direction and retains unknown-terrain
semantics. Internal local search and current path validation remain unchanged.
Keys contain exact scope/profile, both regional version tokens (including the
existing body halo), hard bounds and direction. A remote edit can reuse an
unchanged boundary; a changed neighbour/profile/scope cannot. Retired scopes and
clear release their boundary entries. No graph references are retained by completed
boundary values, and no occupancy/reservation/arrival authority is cached.

A producer builds cooperatively and shares one locked builder per exact key;
concurrent readers yield rather than duplicate a miss. No geometry callback runs
under the global cache lock. Only complete results, including genuine empty
lists, are published.64 builders,8192 completed/building entries and131072 retained
portals bound retention; they do not truncate a complete boundary. Directed
multi-level enumeration preserves the original stable portal ordering and body
checks. Cheap analytical plane traffic cannot evict expensive layered passages.
A positive certificate for the WHOLE two-region union permits a constant-size
analytical portal list without individual body checks; two separate plane flags
alone are insufficient. The existing certificate contract is also used by open
region shortcuts. Navigation detail, worker counts, rules/schema and domain
execution/custody/freshness checks are unchanged.

New nested boundary diagnostics count lookups, hits, builds, actual traversal
checks, analytical proofs and bounded retention. They do not turn a cache hit
into movement or execution authority. Current acceptance covers profile, directed
multi-level links, changed/remote geometry, alternative snapshots, clear/retirement,
concurrent single construction and analytical traffic, plus existing navigation
regressions and native economic flows. Receipts are under
build/simulation-throughput/region-boundary-reuse/.
The preexisting adapter restart test failure recorded above remains outside this
pure connectivity cut and is not reported green.


Focused existing navigation/architecture plus initial boundary checks passed;
guardrails/JAR/package passed43s. Final six boundary cases passed, including
single construction with concurrent readers and clearing while its worker is
inside a controlled traversal.55 native economy cases passed (combined command59s,
native test interval11.37s). A final six-case focused clear check passed15s.
No wall-performance threshold or higher timeout was introduced. This verifies
calculation/recovery boundaries and the selected economy flows, not all HOT/M3.


Static review before final delivery additionally found deep hashing of complete
regional collision tokens in both the old RegionalKey and the new boundary key.
Their hash functions now use bounded spatial/scope inputs; full equality still
checks local versions and the explicit baseline identity. The baseline fence is
retained even when local tokens happen to be equal. A connected warm lookup test
counts token hash visits (no wall-time assertion), and rejects reuse after a
baseline replacement. The final seven boundary/key cases plus regional,
clear/retirement and architecture checks passed; guardrails/JAR/package passed32s.
The earlier preliminary4a9d6c80d observation is preserved but not used as the
final optimized result; its binary predates these key corrections.


### Scale incident found before promotion: private read-phase lifetime

The first final8f57b6a37 comparison did not finish. At about45899x, SCALE normally
saved/stopped after quarantine: the opportunity calculation for settlement12
retained131404 fact units against the existing131072 ceiling. Evidence is kept
in region-boundary-reuse/incident-retained-facts/ and native-candidate-final/.
Its incomplete six-sample window is not a successful benchmark or deployment
acceptance. MAIN remained on4f195546f throughout this diagnostic correction.

Static connected cause: GoodsTripPlanning.Calculation advances a persistent
alternative cursor, but the generic preparation/owner-delivery memory merged
acknowledged reads from every already completed alternative. Those accumulated
proofs no longer served the suspended attempt, yet remained charged until the
whole trade choice completed. The existing overflow guard correctly exposed the
unbounded lifetime; increasing its ceiling or silently evicting an unfinished
root-replayed prefix would conceal the defect or cause repeated preparation.

PedestrianRoutePlanning now offers an explicit completed-read-phase checkpoint.
The resumable trade cursor invokes it when a candidate attempt finishes, including
rejection or repricing, and never when the attempt suspends for a pending route.
A later smaller/repriced offer is a new attempt. Immutable calculation memory
carries prefix retirement through nested observations and detached owner merges,
including an empty successor phase. Ordinary/root-replayed calculations retain
all acknowledged data unless the caller explicitly completes its phase. Shared
route results, outstanding demand, current geometry validation, budget and
canonical economic admission do not change. Regression coverage exercises the
actual advice/wake publication chain, nested and empty retirement, retained
suspended data, late delivery/shared eviction and multi-leg root replay.

A bounded30s pinned async-profiler capture on the isolated SCALE candidate is
retained under region-boundary-reuse/profile/. It confirms substantial regional
build/connect and geometric memo work on navigation threads, but overlaps early
JIT/GC warm-up and is not a final CPU-share or TPS comparison. No profiler/build/
client overlaps an ordinary comparison window.


### Final matched observation: a009ef1e2

The read-phase correction passed50 selected frontier cases:7 boundary,11 demand,
24 wake/continuation,1 trip feasibility,4 opportunity advice and3 procurement
transport. All55 native economy cases passed again (15.73s native interval;
combined command61s). Earlier focused guardrails/JAR/package passed36s; the clean
detached a009ef1e2 frozen guardrails/JAR/package passed13s. No ceilings or test
assertions were weakened. The preexisting adapter recovery failure remains open.

The ordinary SCALE comparison restored the same complete109-file checkpoint,
required explicit recovery=false and no fast-forward, warmed60s, then waited
for the baseline's canonical window. Same rules/JVM/two advice/two navigation
workers. No task build/profiler/client overlapped either measured window.

| Ordinary SCALE leg | Canonical interval | Ticks / seconds | Ticks/s | Debt before → after |
| --- | --- | --- | ---: | --- |
| 4f195546f baseline | 458107 → 459219 | 1112 / 61.685735 | 18.026858 | 1186 → 1308 |
| a009ef1e2 final | 458114 → 459223 | 1109 / 65.675261 | 16.886115 | 932 → 1136 |

This is **no demonstrated global speedup**: observed rate difference−6.3280%,
with growing ordinary debt. It must not be presented as sustained20 or added to
older speed ratios. Same checkpoint and nearly matching canonical intervals do
not make asynchronous completion/task mix identical: final completed8165 searches
versus5879 baseline,15393 versus8885 worker slices,5404 versus4584 advice tasks;
revisions/tick20.3291 versus21.3858. Thus neither global benefit nor a universal
6% algorithmic slowdown is established by this one comparison.

The new producer's measured work is explicit:1,120,163 boundary lookups,
127,590 hits,991,157 whole-union analytical proofs,1413 completed layered builds,
23,500 body traversal checks,1393 evictions. End retention remains bounded;
see comparison-summary.json for exact counters. Aggregate directed geometric
computations nevertheless rose9,126,890→12,156,815, and completed layered region
graphs4995→11010. The repeated-boundary mechanism is removed, but the whole
calculation path still does too much regional work for the20-tick target.
These counters do not attribute all geometry work to any one caller.

The final observation passed the earlier quarantine point and completed normally,
with no fresh ERROR/quarantine. Five sampled towns had zero starving residents
before/after and ordinary meal activity. This is bounded COLD/runtime evidence,
not long-term100-town economic equilibrium or HOT/player/M3 acceptance. Earlier
preliminary4a9 and failed8f57 windows remain preserved and excluded from the final
successful-window claim. Receipts: build/simulation-throughput/region-boundary-reuse/.

Source-bound final artifact SHA512:
`f3f2802d1ca47213576c5a0a7a3f7a6cea2cc35b264721a0a5cedd649737d7eaa5d3ae17b9d63c5b22379a69ddc07593bd3eb168d87042b93026a2da32bdcf95`.

Both runtimes installed the exact frozen a009ef1e2 artifact through the existing
root deployment owners, with native saved-dimensions/exit and fresh verification.
MAIN retained frontier-v3-r281-cold-atomic-20261028; SCALE used the authorized
scale-r31-schema313-seed126 checkpoint only. Final three ordinary checks on each
explicitly statusOK/recovering=false/noFF: MAIN2540020→2540442,debt0;
SCALE460665→461479,debt1444→1053. The later catch-up does not erase the measured
window's deficit or establish sustained20. No fresh ERROR/quarantine. Actual
Java22 MAIN3227416/birth38590576, SCALE3207388/birth38561198; exact installed/frozen
JAR hashes match (final-identity.json). No task build/profiler/client/observer is
left running. Pack tracked state remains clean, unrelated .f0v-baseline/ preserved;
canonical architecture amendments remain among preserved governance WIP. No push.


## Attribution of the apparent boundary-cache regression

User requested the exact cause of18.03→16.89, before another optimization.
This investigation changes no production behavior. MAIN remainsa009ef1e2;
SCALE alone normally saved/stopped/restored the same authorized checkpoint for
causal observation. Receipts: build/simulation-throughput/boundary-regression-diagnosis/.

### Exact accounting of the original measured loss

The disjoint HOST_TURN parent timers on the single owner thread account for the
extra wall time. Ready continuations cannot execute inside the native tick
(FrontierV3CanonicalResumption checks NATIVE_TICKS); these two parent families
must not be added to their nested validation/transaction stages.

| Original window | Baseline4f195 | Finala009 |
| --- | ---: | ---: |
| Elapsed observation, seconds | 61.685735 | 65.675261 |
| Ready-continuation owner time, seconds | 31.211383 | 35.319604 |
| Ordinary owner-turn time, seconds | 2.213534 | 2.098411 |
| Sum of those disjoint owner times, seconds | 33.424917 | 37.418015 |
| Ready-continuation invocations | 34448 | 40450 |
| Mean ready-continuation wall duration, microseconds | 906.04 | 873.17 |

Observation grew3.989527s; these owner timers grew3.993098s (difference3.57ms).
The lost time is additional owner continuation work, rather than a larger mean
cost per continuation. This is attribution of the observed windows, not proof
that the cache alone caused every additional invocation. Background elapsed
sums and nested stages are not additional exclusive shares.

### Fixed-input navigation and trade calculations

Two ignored standalone read-only consumers load the actual native-saved
schema313 checkpoint at revision7796944,tick456001. They invoke public production
geometry/profile APIs and the ordinary CooperativePedestrianPlanner in separate
Java22 JVMs with2G heaps. Baseline is the retained exact4f195 JAR (SHA512de51…13faa),
candidate the frozen exacta009 JAR (SHA512f3f2…dcf95). No task build/profiler/client
or another offline leg overlapped either measurement. Input/JAR/source hashes
and scope are retained in offline-identity.json. These are diagnostic consumers,
not new timing assertions or permanent CI tests.

First40requests agree on observed terminal fields. The400-request run spans all
100towns, local residents and distant villager/donkey estimates, and exceeds the
strong graph cache's capacity. Three warmed rounds each use a fresh planner.
Exact captured endpoints/bounds/extents are equal. Status,reason,regioncount,
estimate,length and32-bit route fingerprint agree; a fingerprint is not claimed
as a full serialized route equality proof or movement authority.

| Fixed400requests | Baseline | Candidate |
| --- | ---: | ---: |
| Mean current-thread CPU, seconds | 33.637218 | 27.372476 |
| Mean elapsed processing, seconds | 35.372889 | 28.491405 |
| Layered regional graphs per round | 7475 | 7475 |
| Graph evictions per round | 5427 | 5427 |
| Deterministic work units per round | 21627695 | 20831653 |

CPU−18.6244%; allocated bytes−17.5644%. This fixed workload does not reproduce an
intrinsically slower navigator or doubled graph construction. It does not prove
all dynamic geometry/callers or a global TPS benefit. Ten fixed actual trade
NO_PURCHASE decisions also agree on choices,passes and search counts; warmed CPU
3.379132→2.244634s. The extra live searches therefore cannot be inferred from
cache cost on an unchanged input; the evolving asynchronous workload matters.

### Concrete read-delivery defect found in a009

A source-level interleaving through the actual continuation owner, private read
memory and ordinary planner is reproduced by ignored ReadCheckpointRace.java:

1. A completed trade phase leaves retiredPrefix=true in the stored Wait.reads.
2. A detached preparation captures that memory and waits for the next route.
3. The next route completes; the owner acknowledges it in its newer read memory.
4. Ordinary shared-result pressure evicts the route (the probe uses the real
   128-result policy, not a forced invalidation).
5. The older preparation publishes. Its inherited retiredPrefix marker is
   applied again by memory.merge during PedestrianPlanningContinuations.publish.
6. The newer delivered fact disappears. The next resume asks for the same route
   again despite previously acknowledged delivery.

Exact probe output: BEFORE_LATE_PUBLICATION delivered=true; SHARED_EVICTED=true;
AFTER_OLDER_PUBLICATION delivered=false,pending=0; NEXT_RESUME=PLANNING;
DUPLICATE_SEARCHES=1. This is a source-proven defect, not a guessed failure story.
A retirement instruction was retained as reusable state instead of being
consumed once at its publication boundary. The fix belongs to the common read
phase/delivery protocol, retaining late data and exact generation boundaries;
raising cache capacity or weakening geometry validation would not fix it.

Its share of the original6.3% is NOT measured. Preliminary4a9 already observed
17.1356/s before read-phase retirement existed, so this later bug cannot explain
the entire original difference. No production fix is included in this diagnostic.

### Confirmed trade retry amplification and causal limits

Original windows: completed searches5879→8165; worker slices8885→15393;
advice4584→5404; CHANGED_EMPTY_MARKET rejections306→590. The source guard in
GoodsParticipantOpportunityAdvice rejects NO_PURCHASE when either the entire
order-book object or the buyer input revision changed. Rejection flows through
PedestrianPlanningWakeIndex.obsoleteAdvice, called by the continuation adapter:
its captured cursor/read memory
is removed, then the same due opportunity can restart. The reason currently
combines book changes and buyer input changes; these counters cannot classify
all590 as false/irrelevant invalidations. Relevant new offers must still reopen
a previous absence. Dependency precision, rather than disabling freshness, is
the appropriate correction boundary.

A fresh30s JFR starts at459052, overlapping the last167canonical ticks of the
original window and continuing to459380. It is not another matched full-window
TPS benchmark. It records5259navigation slices,1510queued opportunity advice,
12300ready turns and181complete queued→running→computed→rejected opportunity
lifecycles with reason CHANGED_EMPTY_MARKET. Those181 computations consume
2.547182 current-thread CPU seconds before rejection. Some opportunity schedule
IDs have64 preparations in that interval; the count includes valid partial
resumptions and newer generations, not64proven duplicate jobs. Worker slice
completion calls ready.run; the adapter coalesces queued notifications, then
re-enters navigation retention/publication/wakes and ordinary canonical work.
Repeated attempts therefore reach the owner, not only the worker pool.

The capture has624492events/no JFR data loss but reports trace_incomplete for
missing capture-edge dependencies and unexplained wall time. Positive closed
lifecycles/counters remain evidence; no fully closed end-to-end attribution or
universal causal percentage is claimed. An earlier quiet trace at534875→535476
is preserved separately and excluded from old-phase reasoning. A command-envelope
parsing error delayed the targeted capture; raw receipt/error are preserved,
and the actual459052→459380coverage is stated above. No extra reset was used
to hide an unsuccessful product result.

Conclusion: the measured wall deficit is owner work amplification. Fixed-input
navigation is cheaper. A concrete common delivery bug creates repeat searches;
broad trade freshness checks cause observed recomputation. The individual share
of each in the6.3% remains unallocated; presenting that number as cache overhead,
or claiming every retry is needless, would exceed the evidence.

Report guardrails passed11s. Final health/source identity receipts confirm both
still run the unchanged a009 artifact: MAINJava3227416/birth38590576,
instant2625367,debt0; SCALEJava3462879/birth38970822,instant464078,debt1464.
Both explicit statusOK/recovering=false/noFF/no freshERROR/quarantine. SCALE
profiling/restore does not establish its sustained throughput; no new production
behavior or MAIN deployment occurred. No task probe/profiler/client remains.
Implementation report is committed separately; pack tracked state is unchanged,
existing.f0v-baseline/ and governance WIP preserved. No push.


### 2026-10-30 — connected read retirement and negative opportunity repair

The user authorized fixing both confirmed boundaries above. Stored calculation
memory now contains completed facts only. A disposable update carries its exact
captured basis, resulting facts and explicit phase retirement through nested and
chained advisory/exact/completion observations. Publication removes only captured
read identities, preserving later acknowledged evidence even for the same demand.
The instruction cannot survive in a stored wait and be replayed on the next turn.
Recovery now retains its successful prefix as well as pending dependencies.
The same fact budget, geometry fences, exact schedule ownership and no-checkpoint
root replay semantics remain in force.

Negative opportunity advice now retains trade-owned bounded input evidence,
constructed only for an actual negative advisory choice. Its prospective buy
commodities/limits come from the decision producer, including commodities without
a current input-book bid. The existing commodity index supplies current crossing
known-seller offers, including explicit absence. The buyer's hard economic basis and declared identity/policy/knowledge/prices,
relevant peer identity/policy/home and transport/order retention thresholds remain
guarded. Peer stock/prices/crew remain dated observations, as for positive advice;
current exact positive admission alone can grant their resources or workforce. Unrelated
order changes, above-limit asks and MARKET-only notification generations do not
restart it. New or changed relevant offers, hard inputs, navigation observations
and ordinary expiry reopen it. A selected choice still passes existing current
exact stock/money/crew/custody/mission admission; no evidence grants a reservation.
No new durable schema, quota, worker count, timing assertion or reactive framework.

Focused checks currently pass: 54 Java cases (navigation wait/demand, market
interest/advice and architecture), guardrails and adapter production/test
compilation. They cover late delivery plus real shared-result eviction, a
checkpoint during nested capture, same-demand later replacement, empty retirement,
recovery prefix retention, missing/relevant/unrelated/noncrossing/expired offers,
funding changes, unrelated seller production, geometry refresh and dated-choice admission.
The pre-existing adapter restart test reported above remains open; these focused
checks do not label it green. Frozen artifact/native economics, paired SCALE
measurement and deployment receipts are recorded below after execution.

First frozen repair c29d60cb2 passed the54 focused and55 native economic checks
and was deployed, but its SCALE window was worse:1098ticks/75.278146s=14.585907/s,
debt1483→1890, versus a0091109/65.675261=16.886115/s. Navigation work fell
(7816vs8165searches;14,839vs15,393slices;96,391,596vs99,463,672work), while
CHANGED_EMPTY_MARKET rejections rose757vs590. Do not call it a performance fix.
The introduced negative proof also subscribed to every seller hard-input token
and every seller resident's admission, beyond the dated-peer contract. An unrelated
cobblestone stock change at a bread seller could retire the buyer's unchanged
bread opportunity. Those guards are removed: retained quote consent and declared
peer identity/policy/home remain addressed; seller stock/prices/crew are dated
observations, with all actual current checks preserved at positive admission.
The buyer's hard inputs stay strict. No new expiry or missed-offer fallback.
Receipts for this unsuccessful candidate remain under
`build/simulation-throughput/boundary-delivery-fix/native-corrected/`.

Final narrowed repair **53b5f78fd** passed54 focused Java checks and guardrails;
the clean detached JAR passed verifyPackagedJar. The55 native economic tests
passed on c29 (same physical/delivery code); they were not repeated for the
final trade-proof narrowing. Adapter production/test compilation passed for
the connected route change. No full-suite, real-client/HOT/M3 or sustained20/s
claim; the pre-existing adapter restart test remains open as recorded above.

Paired final window:458117→459219,1102ticks/67.562569s=**16.310807/s**,
debt1441→1690. Original a009:458114→459223,1109/65.675261=16.886115/s,
debt932→1136. Navigation7909vs8165searches,15074vs15393slices and
97,894,356vs99,463,672work units. Opportunity/advice completions4884vs5404;
all rejections294vs683; CHANGED_EMPTY_MARKET230vs590 (−61.0%). These
are observed counters, not proof that every remaining rejection is necessary.
The same complete109-file checkpoint, rules, worker counts and heap profiles
were used, with ordinary simulation/no fast-forward. Asynchronous task evolution
differs; this single matched range does not isolate a per-algorithm speed change.
The two confirmed defects are corrected, but **overall performance is not fixed**:
measured rate is3.41%below the prior a009 window, not an improvement toward20/s.
Ready owner turns39478vs40450,36.231660vs35.319604s; ordinary owner turns
2.154729vs2.098411s. Mean callback time/mix is still more costly despite fewer
retries. No causal allocation of the remaining speed loss is asserted.

The fixed10-buyer replay still has identical choices, pass counts, completed
searches and search work for all builds. Its one warmed CPU sample is
a0092.244634s,c292.467875s,53b53.299014s; captures occurred at different times
and are not an isolated attribution. These figures are retained rather than
selecting only favourable results. Source-proven absence dependency narrowing
and late-delivery regression coverage establish the corrections; the live
measurement does not establish a throughput win.

Both MAIN and SCALE deploy verification passed for53b5, frozen JAR SHA512
`0a5c89d5a606d64dba65246b9fccb3e14bdf1950cbb410ebb4846e227797717135da04b48deacf7e41668793678e87cc56b9791267f8ed4d0c253cfbb6381af1`.
MAIN retains frontier-v3-r281-cold-atomic-20261028; only SCALE restored the
hashed checkpoint. The reported09:48server outage coincided with our authenticated
RCON stop09:48:08 and confirmed native save/restart09:48:48, not a new crash
report. Separate old-a009 BODY_JOIN_REJECTED/NO_CURRENT_AUTHORITY errors at
09:42/09:46 are preserved in pre-main-body-join-errors.txt and remain unrelated
unclosed evidence; no claim that this repair fixes that physical lifecycle.
Final fresh health/source identities are under boundary-delivery-fix, including
deploy-main-final,deploy-paired-final,comparison-summary-final.json and
final-runtime-check.log. Previous unsuccessful candidate evidence is retained.
Pack tracked inputs remain unchanged, .f0v-baseline/ and governance WIP preserved;
no push.


## Owner throughput continuation (2026-10-30)

Scope is the four remaining performance costs plus exact body retirement and the
old group-restart failure. Receipts: `build/simulation-throughput/owner-throughput-closure/`.

Alternating old/new/new/old forks replayed the identical checkpoint and ten buyers
for eight rounds each, same runtime22/heap/affinity. All32 rounds retain the same
choices, passes, searches and work units. Last-four-round CPU medians were old
1.9576/1.9564s and new1.9654/2.0506s. The previous isolated2.24/3.30s samples do not
establish a47% regression. Host load is not isolated.

Fresh53b5 untraced baseline:458126→459225 in66.142s (16.6158ticks/s), delivery
debt947→1171. The subsequent2ms JFR is a separate instrumented window, not that
TPS baseline. It is trace_incomplete; stack samples identify hotspots but cannot
be treated as exact whole-interval causal percentages.

Connected source corrections:
- Compile reusable itinerary graphs on the existing immutable calculation worker;
  owner publication checks/installs the bounded prepared cache image.
- Queue-owned subject/ID indexes serve pre-WAL reference retirement, including
  unchanged future actions and transactional replacements; no full queue copy
  for a single removed subject. Complete ingress/recovery validation remains.
- Negative trade advice retains actual offer consent/availability, excluding the
  historical retired-commitment counter; real availability/price/expiry changes
  still invalidate. Separate bounded rejection labels expose remaining causes.
- Recovery compaction now consults the explicit asset retention Strategy. The
  latest body retirement cannot be forgotten before a newer exact incarnation:
  saved dependent resources and late-body joins need that owner fact. Global
  epoch-floor retention alone never proves the actor's outcome.
- The old restart fixture failed to bind the common calculation continuation
  owner before the crash. Correct setup retains the due action and rebinds it
  after recovery. Its actual supply-loading→outbound assertion passes without a
  longer timeout. This is a fixture defect, not evidence of failed product recovery.

Focused connected checks pass, including old restart coverage. Deployment and
performance outcomes for these corrections will be recorded after the frozen
candidate run; source changes alone do not establish20ticks/s or HOT acceptance.


### First contemporary candidate and follow-up

Frozen8ccad2ef3 passed focused reference/navigation/body/recovery checks, guardrails,
packaged-JAR verification and55 required native economy checks. The ordinary
same-checkpoint SCALE window458131→459230 measured15.9517ticks/s (68.895s),
debt949→1228, versus contemporary53b5 baseline16.6158ticks/s. Ready owner time
36.6437s/39979calls versus35.0050s/39983; search work98.5603m versus100.7847m.
No throughput gain is established. Negative-choice rejection breakdown233:
77buyer inputs,27accepted plan,129offers. Four overlength diagnostic reasons
fell into OTHER; shortened bounded labels repair that observability issue.
These are dated computation dependencies; current exact positive admission is
not weakened to make the rejection count smaller.

Next connected source changes use existing shared immutable mechanisms:
- Trade owns addressed order/contract/claim reads; unchanged contracts still
  reject a missing financial hold, resource claim, item or endpoint. Commitment
  totals and item exclusivity retain their exact complete owner dependencies.
- Split exact equipment, fungible holder declarations and physical hand/body
  reference checks. Changing quantities alone cannot force full equipment and
  holder scans. Passive-presence references use the common addressed index.
- Retire the shared individual-edge LRU. Baseline read-only counters showed
  33,458,930regional reads and33,439,668computations (19,262hits,0.058%). JFR
  confirms its memo/key/locking path consumes navigation CPU. Per-capture column,
  volume, contact, clearance and edge caches remain; regional graphs, boundaries,
  itineraries, exact positive/negative completed routes and dependency fences
  remain. Changed/unknown geometry is computed normally, never presumed free.
  The cross-view test stops requiring an optional cache hit while retaining
  the semantic remote/local/halo/profile/baseline damage cases.

MAIN snapshot5399530 has35current+4061retired recovery rows and compacted
epoch floor808; both actors rejected at epochs778/780 lack exact rows. Together
with the only removal path this confirms the arbitrary-compaction failure. A
fresh MAIN world can remove already-lost historical evidence; the source fix
prevents recurrence rather than guessing the missing outcome.


### Second contemporary candidate

Frozen20b3b93f8 passed the connected checks/guardrails, packaged-JAR verification
and55 required native economy checks. Same checkpoint, ordinary window
458117→459231 in63.7993s:17.4610ticks/s, debt855→1017. Against the fresh53b5
baseline this is5.09% more throughput; debt still grows, so20/s remains open.
Ready-owner time31.7167s/40497calls, ordinary1.9397s, canonical validation6.5029s
(versus35.0050s,2.2261s and9.2963s). Navigation96.3761m work/7928completed
searches;327advice rejects. Negative-choice breakdown233 is128offers,83buyer
hard inputs and22accepted plans, with no overlength OTHER category. Those
actual changed dependencies remain strict; lower counters alone are not a fix.

A fresh2ms JFR (separate from the ordinary measurement) identifies whole-area
certificate queries repeatedly traversing the same immutable geometry layers,
and ShipmentExecutionCapability scanning every parcel on unrelated activity
changes. The next source correction keeps bounded per-capture positive/absent
area certificates using the existing memo mechanism, with read-only hit counters.
Shipment/execution/mission forward references and current/suspended courier
backlinks use the existing common addressed reference index. Source/negative
checks preserve unknown terrain, obstacle/profile boundaries and either endpoint
disappearing. A test initially supplied a point predicate as if it were an empty
area certificate; corrected fixture uses the existing explicit indexed capability.
No product fallback or deadline was changed. The new focused reference cases
were split from the test file when it reached the1000-line guardrail.


### Third contemporary candidate and remaining indexed work

Frozen9f0ff731f passed connected area/parcel/navigation/group-recovery checks
and guardrails, package verification and native SCALE startup. Ordinary
458126→459219 in61.7282s:17.7067ticks/s, debt831→973; ready owner31.6529s,
canonical validation6.2275s,92.0971m search work,7899completed searches,
294advice rejects (negative216:108offers,76inputs,28plans,4prices). This is6.57%
above the contemporary53b5 baseline;20/s without growing debt remains open.
Area-cache counters show3,484,779queries/3,209,700computations in this window;
not every area repeats. Further work must reduce actual miss cost too.

Source follow-up repairs the profiled miss/validation paths using existing data:
- Exact inventory owns one projected identity/title/custody table. Intrinsic
  titles and world reference checks reuse it across wear/count/material edits;
  intrinsic item/lot/cargo title checks address the exact existing economic
  account, so balance updates cannot invalidate all titles. The common
  constructor derives its slot index from the same facts; wear no longer
  recopies all occupied slots. Explicit supplied indexes still undergo strict
  equality/duplicate/custody checks. One current immutable image is retained,
  no new item admission limit or history.
- Existing16×16 regional pages retain four exact occupancy words. Bounded
  rectangle membership probes pages/row masks instead of performing a separate
  point lookup for every covered chunk. ChunkSurfaceIndex uses this same
  membership capability while keeping its layered point lookup. Bounds, signed
  coordinates, removals, missing facts and conservative oversized refusal remain.
  These masks answer only membership, not walkability or permission.

Focused negatives cover wear followed by removed title/forged keys, existing
container/cargo/player/fungible custody/recovery rules, and rectangle answers
against the ordinary set scan across signed/page boundaries and removals.


### Fourth contemporary candidate and forest coverage

Frozen f6655c76e passed focused inventory/navigation/reference checks and
guardrails, packaged-JAR verification and55 required native economy checks.
Same checkpoint458115→459233 in61.5530s:18.1632ticks/s (+9.31% against53b5),
debt718→831;92.3260m search work,7935completed searches,341advice rejects.
The final diagnostic omitted stages at its payload limit: owner and validation
durations are UNKNOWN for this window. The comparison helper now preserves
missing sections as null instead of subtracting an absent value as zero.

The same JFR identifies forest coverage loops on geometry area queries. Authored
forest patches and dynamic forest coverage now use the common exact regional
page-membership query, preserving dynamic tree admission, cross-region links,
immutable old snapshots and conservative unknown/oversized answers. Focused
forest/navigation tests and guardrails PASS (forest-coverage-checks.log,37s).
No walkability rule, simulation rate, admission quota or worker count changed.


### Fifth candidate: rejected throughput-only interpretation

Frozen a45a3e531 ordinary458132→459275/48.7039s reported23.4684ticks/s and
debt706→538. This is NOT accepted as healthy throughput: at458927 navigation
reached8 ready publications,20 protected results and then stopped at
275,685,517work/20,573completions. Later canonical462564 had0time debt but
697waiting requests and idle navigation workers. The fifth window is retained
as a failure receipt, not a win. Native fifth-stalled-planning.txt confirms
all8 active distant estimates already completed but unable to publish.

Source inspection identifies a registration/delivery gap: advisory revalidation
acquires ready data before the exact continuation registers its dependency.
Those notifications see no subscriber; the subsequent registration pins already
completed results without acknowledging their data. Every new staged continuation
now checks existing completed read receipts after registering. The common wake
owner records delivery; it does not query a new path, execute a business action,
relax geometry checks, advance a future deadline or raise retention/worker limits.
A connected regression reproduces the pre-registration interleaving and verifies
acknowledgment, shared-capacity release and absence of business execution.

The source also addresses the profiled machine-validation cost: unchanged idle
installed machines depend on their exact installation, not every carried item's
wear/count. Active operations/fuel/recipe effects deliberately retain complete
inventory evidence; recipe effects retain tool definitions. All existing machine
checks remain authoritative, including disappearance/replacement and full ingress.
Focused machine/input/fuel/recovery checks and guardrails PASS57s.

The fifth2ms JFR was captured, but the analyzer crashed on a JDK frame with no
RecordedMethod. The export now labels such a frame unresolved instead of assuming
a Java method; the same recording exports successfully. No recapture or invented
method attribution is needed. Original failed analysis receipt remains preserved.


### Sixth unblocked comparison and remaining local edge memo

Frozen c178c52f8: ordinary458116→459220 in61.7549s,17.8771ticks/s, debt745→876.
No blocked ready publication;94720793search work/7803completions. Owner30.6187s,
ordinary1.8041s, canonical validation5.9185s.354advice rejects, negative273
(154offers,80inputs,38plans,1price). This is healthy progress compared with the
fifth stall but still below20/s. Subsequent navigation continues; no false23/s win.

The fresh2ms sixth JFR (separate from ordinary throughput) attributes the main
remaining profiled-geometry memo stack to canTraverse. Actual ordinary counters:
14568393edge queries,14336132computations:232261hits,1.5943%. Even this remaining
per-capture edge LRU mostly adds an object key, synchronized lookup, eviction and
insertion to a one-use regional graph edge. Remove this duplicate LRU, retain
column/body/volume/contact/area caches and cross-view region/boundary/itinerary/
route reuse. Exact directed traversal still runs against the same captured facts.
No work/safety bound or geometry detail is lowered.

A separate same-source operational trial tests3advice workers against2, retaining
2navigation workers,3ms host soft budget,256weight and8slices. It is not labelled
a source-code gain; the exact paired checkpoint/config differences are retained.

The3-advice-worker trial c178c52f8/seventh-advice3 worsened throughput to
15.8580/s (458116→459227/70.0592s), debt940→1230,120.2047m search work,6215advice
completions/505rejections. Two-worker reference was17.8771/s,94.7208m work,
4961completions/354rejections. More concurrency increased work rather than
closing the target. Revert to2workers for the next source comparison.
Connected local-edge/profile/shape/region/worker/demand/read-delivery tests and
guardrails PASS34s(local-edge-retirement-checks.log); no new mirrored cache test.


### Eighth comparison and owner-local field reads

Frozen01b5e8018, restored2advice/2navigation workers:458123→459222/61.2608s,
17.9397ticks/s,debt680→806;31.1407s ready owner,5.9344s canonical validation,
96.4760m search work/7747completions,357rejections. No publication stall.
This does not establish a substantial TPS gain from local-edge retirement.

The sixth JFR's largest owner lookup caller is SettlementWakeAliases.changed,
which traverses every field whenever an access event subject is not itself a
site. ResourceSiteState now owns the common grouped work→site directory for
harvest/preparation work, preserving first-declared matching and fork isolation.
Cultivation-home membership is checked only when its complete registry/bootstrap
changes, independently of crop progress. Field geometry validation now uses
addressed declaration/layout checks plus common exact position-membership
uniqueness, so one changed field does not revalidate every other field. Full
ingress still rebuilds complete evidence. Warm predecessor, removed work, forged
layout/out-of-bounds and changed-field overlap with an unchanged field remain
rejected correctly; ResourceSite/Cultivation tests+guardrails PASS50s. Existing
field/harvest/resident wake checks PASS62s before the final geometry extraction.

Captured collision-cell facts have many shared reads in the same JFR. The common
bounded memo adds an explicit read-mostly mode: ConcurrentHashMap hits, serialized
bounded insertion-order eviction, compute outside the monitor. Only the immutable
collision capture selects it, still8192cells, including unknown answers; ordinary
producer caches retain their LRU behavior. Equality/pressure/failed-computation
checks and connected collision/profile/region/worker/delivery checks+guardrails
PASS32s. No global cache, TTL, new geometry validity assumption or mutable-world
read is introduced.

An overlap audit of1466captured advice tasks found only7same-subject running
pairs. It does not justify making all same-owner advice exclusive; no coherence
lock or new scheduler policy was added on that hypothesis.


### Ninth comparison and producer-owned supply facts

Frozen e65e8840e ordinary458118→459233/61.4546s:18.1435ticks/s,debt629→743;
ready30.8433s,ordinary1.8486s,canonical validation5.8673s.98.7364m search work,
7803completions,329advice rejects. Navigation progresses;20/s remains open.

JFR also identifies ProductionStockDemand reading global promises repeatedly.
Source inspection finds StockSupplyComposition's manually maintained root tuple
omits forest entirely. Forestry admission changes forest/execution state while
all listed roots can remain unchanged, leaving cached future wood supply stale.
Composition now reads complete immutable promises from the six existing owners,
which retain their own complete dependency tuples; unrelated stock/account/need
changes cannot reconstruct every producer. Current promises are grouped by
actual economic owner/item for production demand, preserving quantities, source
order and completing-subject exclusions. No second stock ledger or business
policy is introduced. Legacy policy-only export intentions retain their existing
path, outside the current economics-enabled obligation projection.

Focused production/harvest/extraction/automatic station/supply-window checks PASS;
initial new forestry fixture incorrectly requested an r28 undeclared oak_log
production target. Corrected test reads the same public stock-promise composition,
warms it before real forestry admission, verifies the new incoming logs without
inventory/population writes, then verifies its unchanged predecessor. Forestry
lifecycle checks+guardrails PASS49s(producer-supply-forest-checks-fixed.log).


### Tenth comparison and bounded weak graph directory

Frozen ba34d4c4b ordinary458118→459226/57.1058371s:19.40257ticks/s,
debt784→818;90.2790m search work/7688completions/14332slices.
4637advice completions,4282accepted,353rejected. Negative results changed
124offers/79buyer inputs/35buyer plans/5prices; nonnegative rejects67buyer inputs,
21plans,14owner inputs,8expired. Completed publications drain and navigation
continues;20/s without growing debt is still open. Endpoint stage totals were
omitted, so owner/validation durations are unknown rather than zero.

The regional strong cache retains2048graphs. Its weak directory previously
forgot exact keys after512evictions even if an admitted search still held the
graph. Extend only bounded weak metadata to8192entries (four strong turnovers);
strong graph/surface capacity, revision/profile/scope checks and cleanup stay
unchanged. Existing pressure coverage now holds a graph through more than512
evictions and proves reuse without another survey, plus unchanged strong bounds,
changed-permission rejection and clear. Connected focused checks PASS29s
(weak-graph-window-checks-fixed.log); the preceding fixture incorrectly put its
new test tiles outside declared bounds, corrected without relaxing product rules.
Benefit requires a same-checkpoint ordinary comparison, not a test-time claim.


### Eleventh comparison and addressed itinerary candidates

Frozen e14e62f83 ordinary458116→459229/58.0522594s:19.17238ticks/s,
debt539→587;85.6322m search work/8073completions/14172slices.
4820advice completed,4489accepted,330rejected. This confirms substantially fewer
layered rebuilds (15925cumulative versus40214at tenth endpoint), not an overall
TPS gain; completed workers keep progressing.

The sixth JFR also samples MacroSearch.advance repeatedly. Source shows each
expanded macro node scans up to128itineraries, including unrelated graphs.
Itinerary cache now shares a fixed ordered snapshot per present geometry scope/
bounds, with at most1024memoized node-membership rows. First lookup examines
at most128graphs; repeated lookups address only matching candidates. Publication,
replacement, scope eviction and clear retire the corresponding current snapshot;
admitted searches retain their original candidates. Exact dependency validation,
arc ordering, regional fallback and strong graph/portal limits remain unchanged.
Connected hint churn/revision/planning/hierarchical checks+guardrails PASS29s.

Existing8KiB operator output sometimes omits the detailed phase table. Add three
compact cumulative ownerWork counters (ready/ordinary/validation) to its required
envelope; absent attribution remains null, never invented zero. This measures
the same existing telemetry and adds no runtime instrumentation. Performance and
detail-budget checks+guardrails PASS22s; initial new parser assertion forgot the
existing PMV3_DIAG prefix, corrected without changing product rendering semantics.


### Twelfth comparison and independent strategic invariants

Frozen97d1146d7 ordinary458133→459219/54.6165814s:19.88407ticks/s,
debt531→537;64.7153m search work/7927completions/12228slices.
Ready owner26.8415s/37931calls;ordinary1.6731s/1092;validation5.2946s/10940.
4783advice completed/4452accepted/331rejected. Remaining negative changes are
144offers/95buyer inputs/18buyer plans/2prices; other46buyer inputs/13plans/
9owner inputs/4expiry. A changed actual supply/input/consent remains a valid
rejection; the count must not be forced to zero by accepting stale results.

The sixth JFR attributes29owned-validation samples to strategic state. Its one
whole-state validation tuple made any task progress repeat all authorities'
policy, workforce and commitments plus every objective/patrol. Split these
existing checks by their complete immutable inputs in the same semantic owner:
declarations, workforce, commitments, objective targets, patrol references and
infection knowledge. Existing common ImmutableInvariantCheck retains only its
last successful tuple; complete recovery ignores cached evidence and rebuilds
active membership. No validation rule, actor authority or domain detail is removed.
Workforce membership reads existing priority keys directly, avoiding another
projected roster map per authority. Warm foreign/missing-commitment and missing
worker checks remain strict. Connected strategic/objective/retirement/group/trade
checks+guardrails PASS2m11s; final full-audit focused receipt recorded separately.
