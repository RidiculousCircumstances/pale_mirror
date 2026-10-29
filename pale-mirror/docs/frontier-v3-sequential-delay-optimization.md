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
