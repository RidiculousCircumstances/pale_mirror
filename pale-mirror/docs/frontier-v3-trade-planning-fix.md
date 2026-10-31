# Trade planning correction

Accepted 2026-11-01: close the diagnosed trade-planning cost in one connected
implementation and verification pass. Main alone, no subagents or push. MAIN
stays stopped; verified delivery is to the private 50-settlement benchmark.

1. Prepare shared immutable navigation inputs by their actual site/region facts.
   Remove registry-wide projection and scratch-basis oscillation from repeated
   delivery evaluation. Reuse existing geometry owners and regional types;
   unknown data, native collision evidence and profile fences stay exact.
2. Preserve choice progress through technical route waits and avoid repeating
   expensive endpoint preparation for unchanged candidate inputs. Keep current
   resource, cash, crew and full journey checks at final admission.
3. Reuse unaffected economic forecast parts when peer input changes; resolve the
   actual dependency before publication, without accepting stale consent.
4. Distinguish due-date lateness from technical queue/route wait. Use evidence
   tied to the changed path rather than a new generic tracing framework.
5. Verify connected geometry/trade/continuation negatives and native affected
   boundaries; compare a frozen baseline/candidate from the same saved 50-town
   checkpoint and ordinary life interval, same workers/flags/config. No own
   builds/tests/profiling during ordinary measurement. Correct any connected
   failures, commit reviewed changes and leave both benchmark and MAIN stopped.

No economy rebalance, simulation simplification, thread-count increase, unsafe
freshness relaxation or compatibility migration is part of this correction.

Implementation:

- Extraction keeps completed, immutable site parts in a bounded shared cache.
  Frozen composition seeds share these parts; only a changed site forks its
  projection writer. An unchanged projection does not re-enter contribution
  unions. Local exclusion cursors also fork their immutable regional images.
- Geological input comparison uses actual declarations and survey permissions
  per footprint region. The aggregate still checks site metadata; it no longer
  resamples every site's blocks after a remote geological change.
- Economic review keeps a bounded immutable domestic stage under exact owner,
  action, hard-input, peer-declaration and geometry bases. A new peer-dependent
  completion reads the current market; original forecast dates and expiry stay
  dated. Cash, stock, current buyer consent and final admission are unchanged.
  The market capture instant is explicit: old domestic facts cannot resurrect
  an expired bid. Cache residency never proves time validity.
- Existing cooperative purchase-choice continuation was already wired and is
  retained. No speculative replacement or second active choice engine was added.
- Admission diagnostics name the FIFO `capacityHead` and publish its current
  `capacityHeadSinceTick`. `instant - dueAt` remains whole-action lateness, not
  capacity residence, worker CPU or continuous route wait. Wall-phase timing
  remains with the existing execution trace.

Verification and paired runtime measurements — 2026-11-01:

- Product commit `9adef9fcd`; fixture-only follow-up `635b8d452`.
  Clean detached release build and packaged-JAR verification passed. Artifact
  SHA-512 `e077136b36e972007826d6498dcee2068af58dcee50dbfd564d38150f8e3ebc0c01afe10c8351c74b8a48992530a5e29fc199f27051dedc38d6f8c5b1a9bd52c`.
- Connected checks: 318 Frontier and 15 adapter tests, guardrails and architecture
  checks passed. Native economy 55/55 and local navigation 45/45 passed.
  A broader adapter run passed 887/888; the remaining NavigationScope setup
  expected REVIEW to admit transport, contrary to the already installed separate
  OPPORTUNITY owner. Corrected that setup through the ordinary successor, keeping
  all stale/foreign/group-vs-parcel authority assertions. All five tests in that
  class then passed. Unchanged 887 results were reused rather than rerun.
- Same complete, native-saved 52-file checkpoint, schema313/seed126, 50 towns,
  1,450 residents, 100k × 100k bounds, zero players; same 2 advice/2 navigation
  workers, Java22, 2G/8G Generational ZGC and configuration. The manifest was
  checked before and after restoring the candidate world. No acceleration,
  profiling, own builds or tests overlapped either ordinary measured window.
  Both JVMs warmed beyond 100 seconds. Candidate measurement was aligned by
  simulation age; startup/recovery durations are outside this comparison.

| Metric | Frozen baseline | Candidate |
| --- | ---: | ---: |
| Ordinary interval | 22,754–26,539 | 22,768–26,553 |
| Wall seconds | 189.265 | 189.397 |
| Simulation ticks/s | 19.9984 | 19.9845 |
| Process CPU, average cores | 3.9761 | 3.5802 |
| CPU milliseconds per simulated tick, all threads | 198.82 | 179.15 |
| Completed advice | 9,965 | 10,985 |
| Accepted advice | 9,023 | 9,965 |
| Mean advice worker wall milliseconds | 20.69 | 16.84 |
| Total advice worker seconds, summed | 206.20 | 184.94 |
| Owner ready processing seconds | 62.62 | 58.96 |
| Peak sampled capacity waiters | 61 | 49 |
| Capacity waiters at final sample | 24 | 0 |
| Navigation searches | 27,187 | 27,232 |
| Search work units | 101,100,414 | 118,949,127 |
| Ordinary debt, first → last (peak) | 0 → 0 (15) | 0 → 3 (29) |
| Peak sampled RSS, GiB | 10.502 | 10.116 |

Observed effect: about 10% lower process CPU per simulated tick and 18.6% lower
mean advice cost, with 10.2% more completed advice. Owner ready processing fell
5.8%. This is reduced work/cost at an already capped approximately20TPS, **not**
a TPS increase or a proof of100-town/five-player capacity. Navigation work rose
17.7% despite near-equal search count: asynchronous business ordering and chosen
paths diverge, so this is not an identical-instruction replay or an isolated
attribution of the gain to each changed helper. Host memory headroom stayed
above40GiB; no material swapping/memory-pressure episode affected either window.

Strict revalidation remains active: rejected advice941→1,019 (9.44%→9.28% of
completions). Peer consent changes are not accepted to improve a counter.
All five sampled participants continued publishing reviews; navigation completed
throughout. Final summary was green with zero required, inventory or replica
custody conflicts. No new runtime ERROR/quarantine/authority warning occurred;
pre-existing optional class-probe warnings remain. These few-minute COLD windows
contain no completed sales in the sampled participants and do not prove an
entire long-distance commercial round trip or a balanced mature economy.

Latency interpretation is deliberately separate from these cost gains. The
largest sampled FIFO-head business due age did **not** fall: 3,141→3,888 ticks.
In that candidate sample, settlement33 had due22,036 at25,924, while its current
capacity wait began25,921: three ticks, not3,888. Maximum sampled current
capacity residence was30ticks (1.5 simulation seconds); the old artifact did
not record that entry instant, so its comparable value is unknown. Long complete
purchase decisions and necessary invalidations have not been declared absent;
there is no claim that every trade resolves within1.5s, nor that one observed
short queue residence reconstructs its entire earlier history.

Receipts (ignored build output): `build/simulation-throughput/trade-planning-fix/`
contains checkpoint manifest/identity, frozen baseline artifact, paired metrics,
full comparison JSON, verification logs and `stopped-verified.json`.
`build/simulation-throughput/fifty-settlements/paired-trade-{baseline,candidate}/`
contains raw performance/planning/domain samples, host pressure/CPU/RSS and
candidate deployment/source/process identity. Both runtimes ended by normal
Minecraft save; all-dimensions save, exact JVM exit and both ports were verified.
MAIN was never restarted; no public pack/client publication or push was made.

Repository state: IMPL product/test changes committed; RELEASE clean detached at
product9adef9fcd. PACK tracked files unchanged, unrelated `.f0v-baseline/` retained.
Canonical governance ledger/architecture updated without staging its unrelated WIP.
