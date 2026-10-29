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

Pending paired ordinary scale observation and verified deployment. The target
of20 canonical ticks/s without accumulating debt remains open until measured.
No five-player, graphical product acceptance or whole-economy balance claim is
made by the offline replay or the focused gates.
