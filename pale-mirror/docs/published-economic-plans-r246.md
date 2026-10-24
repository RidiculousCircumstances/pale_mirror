# Published settlement economic plans (R246)

Economic reviews now calculate and publish one durable settlement decision. Production,
mining, farming, procurement, protection and diagnostics consume that decision plus
current exact stock, commitments and custody. Reads do not collect economic facts or
invoke the solver. Cultivation's hypothetical-capacity calculations moved into review;
its executor consumes the retained intent.

Fact owners report addressed changes in inventory/cash, capabilities, market offers,
needs and delivery availability. Ordinary movement and unchanged physical receipts
are excluded. One coalesced review per participant is recorded in the same WAL
transaction as its input change. Existing independent work can continue; affected new
work awaits review. Retained production ceilings share one coverage baseline, so several
stations cannot each obtain the whole incremental quota. Export calculation reads
published buyer envelopes instead of recursively solving buyers.

Schema 302 persists plans, input generations and their causes. Schema 301 is accepted
only from a complete checkpoint without a WAL tail; upgrade must follow graceful stop
of the preceding build. Existing resources, money, jobs and custody remain. Hydration
constructs the aggregate once and does not imitate new publication. Replay consumes
recorded decisions and review effects, never the solver.

## Verification before deployment

- Full frontier integration: 1,864 tests passed (`build/r246-integration.log`).
- Native economy: 55 required GameTests passed in the same integration run.
- That adapter run had 856/858 passing; its two failures concerned test setup:
  immediate environmental sampling while asynchronous persistence still gated the
  turn, and expected diagnostic reserves before the first accepted plan. Corrections
  retain readiness, recovery and diagnostic-budget assertions; focused rerun passed
  (`build/r246-final-adapter-fixes.log`), including runtime interval equivalence and restart.
  Guardrails passed again. The final reason-code change uses explicit stable wire tags.
- Publication, stale input rejection, addressed invalidation, WAL replay, architecture,
  prior-schema rejection and changed work fixtures passed (`r246-final-fixes.log`).
- The earlier native A run had 406/408 passing, with two excluded combat failures;
  this economic change does not claim to close them.

## Measured query cost

Paired R245/new-code probes used the same frozen `build/r244-input.bin`, 36 stations,
identical warmup and thread-allocation instrumentation. All 36 resulting event hashes
matched. Sum of per-station median query times: 18.069462 -> 3.682518 ms (~4.9x).
Corresponding allocated bytes: 3,930,200 -> 1,358,784 (-65.4%). Receipts:
`build/r245-station-allocations.txt`, `build/r246-station-allocations.txt`.
This measures station queries, not total server speed, first-load latency or an 80 ms
bound. The older 41 ms baseline differs with warmup/load; use the fresh paired result.

## Live acceptance still required

Retained-world upgrade, startup publication, COLD/HOT observation and the corrected
`live-field-preparation-handoff` scenario remain to be run on the deployed artifact.
R245's second screenshot was sky; the corrected scenario explicitly aims at and checks
compiled field geometry for each frame. Historical uncertain operations stay explicit
local conflicts, not permission to repeat a possibly completed physical effect.
