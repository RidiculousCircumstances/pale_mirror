# Owner facts at the economic input boundary

The approved follow-up to the Reactor experiment corrects the data boundary
before considering a reactive adapter. Previously, a new world snapshot made
valuation consumers reconstruct stock and installed capacity even when their
local inputs were unchanged. This cut keeps the existing calculation and moves
reusable immutable facts to their producers. Reactor remains experiment-only.

## Responsibilities and flow

`ResourceStorageFacts` publishes ownership, policy, custody eligibility and actual
fixed/mobile position. `FungibleResourceReadIndex` publishes unclaimed quantities
by stock container; `ExactItemReadIndex` publishes actual individual items by
container. Equipment work owners publish their existing reservations through
`WorkEquipmentClaimComposition`. `GoodsStorageFacts` joins these facts into local
market stock; it owns neither inventory nor reservations.

`ResidentWorkforceFacts` publishes the resident profiles available for long-run
capacity, using explicit life status and migration presence. Current hunger,
assignment and feet position are not a loss of installed capacity. Dated worker
selection still uses its existing current-state owner. `InstalledStationFacts`
publishes station declarations and installation eligibility; current operation
contents, readiness and custody remain separate.

Field pending-cell presence, settlement-local deposits and known local forest
amounts come from their respective state owners. All seven registered valuation
ports consume these facts: bakery, field, extraction, automatic stations,
individual stations, attended stock and forestry. `SupplyCapacityComposition`
combines their published costs; `GoodsResourceValuation` consumes those costs and
completed-sale reference prices. Neither a whole-world revision nor a mutable
participant review is its invalidation basis.

`ImmutableReadProjection` composes the existing reference/delta index. Readers
declare exact nominal sources and addresses, including absent keys. Relevant
changes recalculate their readers. Equal complete values retain their identity
while the input evidence advances. This is value reuse, not fresh permission.
One current image per thread and one shared completed image are retained; there
is no subscription graph, scheduler, effect executor or historical world cache.
Per-scope lookups are bounded. Changed sources, removals, branching, epochs and
failed computations use the same invalidation path. No persistence schema changes.

Actual stock identity, title, quality, claims, funding, consent, crew, navigation,
physical custody and WAL admission remain with their existing owners. Quantities
cannot authorize a transfer. Destroyed storage is an absent source, not a reason
to invent stock or make the world fail. Station installation is distinct from
machine readiness. Forest discovery and dated supply promises are unchanged.

## Verification and isolated measurement

Evidence directory: `build/simulation-throughput/owner-facts-boundary/` (local,
ignored). The diagnostic uses the actual `GoodsResourceValuation.replacement`
public API. The same compiled driver runs against the frozen baseline JAR and
candidate classes. It reads the retained 100-town snapshot at tick439523,
covered WAL7454613, SHA256
`0e8a158534f7ec4deb05eca720f92f711a7328f636b2b4453ce235db49a1bb5d`.
Later WAL is not replayed in this offline measurement.

Across 27 immutable scenario snapshots, baseline and candidate agree exactly on
43,200 valuation answers, 43,200 stock quantities and 5,076 equipment-claim rows.
Scenarios include unrelated movement/foreign stock, local withdrawal, profile,
schedule, installation, and branch-to-base reads. The fixture selects/removes
actual commodities deterministically; an earlier nondeterministic pilot is
excluded. The baseline JAR is retained separately and never rebuilt as candidate.

Java21, G1, Xms1G/Xmx4G, one warm-up and three measured passes per JVM:

| Public valuation replay | Baseline median CPU | Candidate median CPU | Baseline allocated | Candidate allocated |
| --- | ---: | ---: | ---: | ---: |
| One town, 2,700 queries | 541.351ms | 393.245ms | 476.571MB | 295.516MB |
| 100 towns, 2,700 queries | 341.635ms | 54.057ms | 368.571MB | 38.400MB |

These are thread CPU and allocated-byte deltas for this workload, not retained
heap, native TPS or a universal scaling ratio. The 100-town CPU medians differ by
6.32x; allocation falls89.6%. This is one sequential comparison on a shared host.
Native paired throughput/debt is reported separately after deployment.

Final affected gates: guardrails,61 frontier Java tests,7 integration/architecture
Java tests,55 native economy GameTests, build and packaged-JAR verification PASS
(`affected-gates.log`). Absence/new source, source removal, failed publication,
branch/epoch invalidation, local changes and unrelated changes are covered by
existing focused suites. No timing threshold or operator-detail test was added.

The broad gate was attempted and is not green. Three Java failures reproduced
against the frozen baseline. One identified an old compatibility-role read in
trade retirement; this cut removes it while preserving explicit cause, shipment
authorization and typed relation closure. Its existing tests now pass. Two
unrelated navigation-fixture failures remain. The full native run passed413/416;
the remaining old scene-registry assertion and two inactive hive scenarios are
outside this cut. A whole-world production-codec unit fixture was cancelled after
over ten minutes with its stack retained. These are disclosed limits, not a claim
that the full suite passed or that inactive hive behavior was fixed.

## Runtime result

Both ordinary COLD legs restored the same complete native-saved109-file
checkpoint (manifest checked), with100 towns, seed126, two advice workers,
Xms2G/Xmx8G and GenerationalZGC. Each used60s warm-up and16 samples over five
minutes. No own build, profiling, deployment or client overlapped either window.
Baseline isfb488779f; candidate is59b8e1b63.

| Ordinary scale observation | Baseline | Candidate |
| --- | ---: | ---: |
| Elapsed seconds | 304.463 | 304.408 |
| Canonical interval | 440059–443206 | 440016–442898 |
| Canonical ticks/s | 10.336 | 9.468 |
| Pending ordinary ticks, start → end | 1000 → 3942 | 1046 → 4253 |
| Completed advice | 13,156 | 10,974 |
| Navigation edge queries | 78,335,731 | 58,016,579 |

The new leg was8.40% slower and accumulated debt9.03% faster. Overall acceleration
is **not established**. This one sequential pair has an evolving shared host and
asynchronous execution; neither identical work mix nor the cause of the difference
has been established. The difference must not be dismissed as host noise or called
an improvement. The offline valuation gain remains a narrower measured result.
All32 samples were statusOK with no recovery/fast-forward. Five sampled towns had
no starving residents at the end; no new retained completed harvest/contract IDs
were observed in this short window. This is not whole-economy acceptance.

A separate guarded45s JFR after the candidate window retains actual process/thread
CPU, allocations and method stacks in `candidate-profile-*`. It still shows
substantial trip preparation, navigation and immutable-index work. It is not a
paired causal attribution of the8.40% difference and does not authorize deleting
freshness checks. Raw observations and `native-summary.json` retain both legs.
An initial receipt-path pilot was stopped during recovery before ordinary samples;
its receipts are separate and it is excluded from this comparison.

The verified clean detached artifact was built with guardrails and packaged-JAR
verification (15s). SHA512:
`37d2abc74b373272b3f49d161851bc8864efcfb5bc640003121c972053e0d9363537bc2355b842372c4eddccbe025ce3865c73f19ef5dbcce0c38a9a94e5b738`.
Both deployments are verified. Main retains its existing world and uses
the same artifact; its final receipt is `main-deploy.log` and
`deploy-owner-facts-candidate-main/verify.log`. Client update is unnecessary for
this server-only change. The final MAIN diagnostic is statusOK, instant1151368,
pending ordinary ticks0, no recovery/fast-forward. SCALE statusOK, instant444290,
debt7208 after its additional profiling window. No automatic push.

The target of20 canonical ticks/s without accumulating debt remains OPEN and unmet.
No five-player, graphical product acceptance or whole-economy balance claim is
made by the offline replay, focused gates or short ordinary windows.


## Bounded decision-branch reuse — 2026-10-29 follow-up

The read-only follow-up identified233 exact repeated account read bases within
100 actual trade decisions. `ImmutableReadProjection` retained only its current
working image, so an A→B→A reservation comparison could recalculate A. The common
projection and nested producer directory now reuse completed immutable outputs
through the existing `ImmutablePlanningScope`. Its64-view/64-basis bounds are
unchanged; nested calls share the lexical scope and outer exit releases history.
Saturation takes the ordinary calculation path. No persistent/global branch cache,
reactive library, trade-specific helper logic or new scheduler is added.

The scope key contains the exact epoch, producer declarations and every registered
source-root identity, including maps containing absent read addresses. A hit does
not advance or restore mutable reverse indexes; a subsequent miss still compares
against their actual working basis. Full reference ingress bypasses scoped
projection success. Errors still discard failed working images and cannot publish
an unsuccessful result. Source roots are captured once per projection evaluation;
reference validation uses that same capture instead of invoking suppliers again.
Stable published keys are reused rather than allocated on each cache hit.

The existing scope/reference/nested-index tests cover branching A→B→A→C, appearance,
removal, failures, epoch separation, full audit, saturation and scope exit. Final
`branch-publications/final-gates.log`:66Java tests in16classes and55native economy
GameTests PASS, with guardrails, build and packaged-JAR verification (57s). This
reuses unchanged boundaries from the previous acceptance; the previously disclosed
unrelated full-suite failures remain outside this cut, not reported green.

Diagnostic evidence is under `build/simulation-throughput/branch-publications/`.
Untimed callbacks show1487→1260 stock/account row computations and233→10 exact
repeated bases (95.7% fewer). Storage/workforce/station recomputations remain zero.
Ten repeated account bases remain; whole-publication granularity and bounded
history are not a promise to eliminate every row-level repeat. Neither percentage
is a percentage of total CPU or TPS. The final key-reuse edit changes metadata
allocation only; a final counter receipt is recorded before deployment.

The same baseline-compiled actual trade driver runs against the frozen current
baseline JAR and candidate classes. It executes100participant decisions up to
explicit absent route evidence, with one warm-up and three measured passes/JVM,
Java21/G1/Xms1G/Xmx4G. All300decision/await/event/demand rows agree exactly:
SHA256`dea5bf2cf03f0a7f84f835180d69cec4441323b5486c4b9fc59157fd70b98e86`.
Final median threadCPU1527.604→1532.623ms; allocated811.773→799.446MB. CPU ranges
overlap: no demonstrated CPU gain. Allocation falls1.52%. Earlier key-allocation
pilots are retained separately, not relabelled as final measurements.

Native ordinary baseline/candidate comparison and verified deployment receipts
are recorded after the final scale observation; the overall20/no-debt goal stays
open. No client update or new world schema is needed.
