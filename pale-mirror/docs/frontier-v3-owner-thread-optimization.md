# Owner-thread optimization

Approved scope: all four measured owner-thread directions. Baseline source70e4b15cf,
produceraf28fd3ad; prior trace in frontier-v3-causal-delay-tracing.md. Preserve exact
canonical order, negative/recovery validation, native ownership and WAL fences.

1. Narrow changed-record validation using the existing immutable reference index;
   owners declare complete reads, full ingress/recovery still audits everything.
2. Route remaining expensive pure continuation planning through existing registered
   preparation. Owner publication retains the exact predecessor and current checks.
3. Address native worksite custody/projection maintenance to active obligations,
   reusing current queues and lease/geometry evidence. Loaded-block facts stay native.
4. Reuse exact admission results within unchanged state/action and owner-declared
   holds across turns; budgets still account deterministic classification/execution.

Source analysis first, focused semantic negative checks after connected changes.
Then frozen build, normal retained-world deployment, native recovery/work verification
and bounded comparable measurement. No claim of20TPS or savedseconds from source alone.

## Implemented boundary

- UnitGroupRelationshipChecks uses four existing ImmutableReferenceIndex images
  for supply claims, groups, missions and shipment back-references. The same
  owner checks serve full diagnostic/recovery entry points. The mission port
  declares group reads; life/kind, missing rows, payment and endpoint changes are
  dependencies. Pending material preimages retain the complete fungible image.
- FungibleBindingReadIndex reuses ImmutableGroupView for physical bindings by
  declared account;23 repeated full-filter reads in validation, admission,
  transfers, ground recovery and cargo paths now use it. Exact producer order,
  epochs and immutable branch history remain observable; mutable inputs are
  defensively captured. Mutational ledger passes are outside this read change.
- Expensive planning retains the existing preparation boundary (harvest start,
  extraction, resident activity and navigation). Additional exact offload of
  harvest COLD progress and transport continuation was tested and withdrawn:
  reduced owner planning time did not improve the whole clock, while adding
  worker handoffs. Canonical predecessor/order and publication checks remain.
- Worksite custody selection is a shared read-only join over declared scopes
  and current live/preparing leases. Families retain native support, witness and
  durability checks. Stable selections retain declaration order/identity; a
  changed selection resets the bounded departure cursor, including shrinkage.
- Harvest reservation declarations contain complete capacity/account facts,
  separately from work progress and time. Existing registry, grouping and reverse
  reference views now invalidate only changed declarations and addressed account
  or container rows. Missing rows, foreign custody and duplicate destinations
  retain their original rejection behavior, including full-audit entry.
- All six container claim ports publish their complete immutable values through
  the existing equality cutoff, avoiding downstream backing scans for unchanged
  capacity facts. Native permissions and current epochs remain separate checks.
- Immutable-map consumers share exact fallback differences between closed
  immutable images. Producer lineage remains the first path; mutable maps never
  use the memo. A bounded 32-pair per-thread window holds weak image references,
  preserves traversal order and carries no validation or mutation authority.
- Commercial participant legal identities/peer homes and economic account kinds
  are projected independently of quote reviews and balance changes. The existing
  reverse index rechecks addressed participants/peers, missing accounts and company
  homes; full audit runs the same owner checks. This removes the repeated whole
  known-peer network traversal during unrelated resource/account changes.
- The engineering crew distinctness owner avoids assembling unrelated worker sets
  when there are no engineering or active medical crews. Every original assertion
  still executes when such a crew exists, including retained ready projects.
- Admission retains confirmed HELD wake addresses within the exact immutable
  state/action/pass. Cross-pass reuse still requires owner proof. READY selection
  still rechecks permission; all deterministic budget charges remain unchanged.

Verification:160 affected Frontier tests,17 adapter tests across two focused
runs,55 native economy and4 native block-extraction tests, guardrails and packaged
JAR verification passed. Broad unfiltered Frontier run was stopped in the
unchanged non-target cold-bomber/settlement-assault fixture (stack retained), not
reported as a passing full suite; no hive test/source was changed. Performance
measurements and final delivery follow below after the paired native run.
The corrected second candidate additionally passed186 focused Frontier cases
(including immutable collections and harvest interference),23 native field-turn
cases and guardrails. Native economy55 passed before the shared map-difference
follow-up; this is not a claim of rerunning that lane on the final source.

The third candidate passed117 focused trade/engineering cases across the main run
and the corrected registry fixture rerun, plus55 native economy cases. The new
negative fixture initially expected an ordinary immutable value update to run the
publication audit; it now invokes the actual participant validation boundary,
checks full audit too, and verifies recovery of the valid predecessor. Production
checks were not weakened. Frozen package verification is recorded with delivery.

## Native comparison and final result

All four legs restored the same complete109-file native-saved SCALE checkpoint
(`primitive-spatial-paired/checkpoint`), checked file hashes, seed126, ruleset,
server config and JVM args.100 settlements, about3031 residents, two advice and
two navigation workers,2G/8G Generational ZGC; no players or fast-forward. Each
leg waited for recovery, warmed60s, then took seven performance samples10s apart.
No task-owned build, profile or client overlapped the ordinary measurement.

| Source | Canonical ticks | Seconds | Ticks/s | Debt before → after |
| --- | ---: | ---: | ---: | --- |
| af28fd3ad baseline | 610 | 61.731 | 9.881565 | 1279 → 1904 |
| 260c0abe7 first offload | 597 | 61.709 | 9.674448 | 973 → 1610 |
| 4bd5f7146 reservation follow-up | 559 | 61.776 | 9.048882 | 1061 → 1738 |
| cbeb1f6e3 final | 694 | 61.725 | 11.243458 | 938 → 1479 |

Final ordinary throughput is13.78% above the starting leg. This is a bounded
same-checkpoint comparison on a shared evolving host; asynchronous work mix and
different advancing canonical start positions limit causal precision. It is not
an isolated contribution estimate for each edit, a confidence interval, a HOT/
five-player guarantee or proof of20TPS. The first two candidates failed to show
an overall gain and were never deployed to MAIN. Their measurements are retained.
Debt still grows: the scale20canonicalticks/s-without-debt objective remains unmet.

Separate30s JFR windows are attribution evidence, not the ordinary TPS samples.
Baseline/final profiled rates were6.065905/6.306271; owner CPU12.474/11.315s.
Exclusive measured stage wall times, baseline → final:

| Instrumented owner work | Seconds in baseline | Seconds in final |
| --- | ---: | ---: |
| VALIDATION / relations-groups | 0.577910 | 0.021747 |
| PHYSICAL / worksite-projection | 0.855195 | 0.106717 |
| VALIDATION / owned-state | 2.291891 | 1.772225 |
| VALIDATION / relations-trade | 0.518072 | 0.339266 |

These are observed window costs across different work mixes, not percentages of
universal CPU savings. Final exact-computation running wait6.989s, WAL running
wait4.634s, and owner uninstrumented3.004s remain substantial. Accounting does not
pretend all costs are known: capture-edge/missing causal references and unexplained
time remain explicit; both profiles report incomplete causal closure despite
zero JFR data-loss events. No ownership/check/durability relaxation was used to
reduce these waits. No Reactor, new scheduler or new authoritative graph was added.

Each ordinary sample was statusOK, with recovery and fast-forward off. In the
five sampled towns, no starving residents were reported; two towns had active
hunger/meal work. No new terminal harvest or fulfilled commercial contract appeared
in those five bounded views. This run therefore does not prove a whole-economy
cycle, new caravan delivery, HOT visual acceptance or player comprehension. Native
field-turn/economy gates establish their respective tested physical boundaries.

Receipts: `build/simulation-throughput/owner-thread-optimization/` contains
`measured-summary.json`, all four `native-*/samples.jsonl` and `trace/report/`
outputs, deployment identity/config/checkpoint receipts and frozen build logs.
Raw JFR/capture identity are beside each report. The R2 deploy helper accidentally
reused the R1 deployment log directory; corrected current R2 receipts live in
`deploy-final/receipt-path-correction.txt`. Overlapping R1 deploy logs are lost;
its native samples, JFR and exact capture identity remain intact. Final R3 uses
its own `deploy-r3/` receipts and has no such ambiguity.

## Delivery

Final production source `cbeb1f6e3c704e4ea0bfa16e4863ed6a85939dfb` was built
from clean detached RELEASE; guardrails, JAR and packaged-JAR verification passed.
MAIN and SCALE installed SHA512:
`4ba6dcf82a1be219d336de63636df4234debfd42092bf2474652b167cf80151cabe1794a3d4e44648c926c0d7ffcd281e2541b85e9bec9e6b73e7753051f1b61`.
Existing root publish/install/preflight/verify scripts confirmed source/runtime/
profile/target identity, ordinary native save/exit and fresh startup. MAIN world
`frontier-v3-r281-cold-atomic-20261028` was retained throughout. Actual Java PIDs
at final verification: MAIN3185448, SCALE3167783 (resolve again before future ops).

Three final observations per server report statusOK, no fast-forward or recovery;
MAIN has zero pending ordinary ticks, SCALE debt continues to grow. Neither fresh
runtime log contains new ERROR/quarantine. No test client was left running. MAIN
and SCALE services remain active. These checks establish this deployment and its
bounded runtime health, not a long-duration or whole-feature acceptance claim.
MAIN receipts: `deploy-main-final/`; final checks: `final-runtime-check.log`.

IMPL is committed separately from PACK; RELEASE stays clean detached at the
production ref. PACK tracked files remain unchanged at `f727133`, unrelated
`.f0v-baseline/` is preserved. Governance WIP remains separate. No automatic push.
