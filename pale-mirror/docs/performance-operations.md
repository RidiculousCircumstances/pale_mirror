# Pale Mirror runtime performance

Agent workflow, evidence reuse and retry decisions follow
[`engineering-agent-protocol.md`](engineering-agent-protocol.md).
Terra owns performance diagnosis, technical design and verification as senior
tech lead; PM checks promised behavior and system constraints, not each trace,
method or command. No intermediate technical approval is required.
The explicit scale-limit gates below apply when increasing those limits, not
to every bounded-work correction. Such corrections may use existing runtime
diagnosis plus whole-path and focused regression evidence without a fresh
benchmark/JFR campaign; a measured speedup still needs comparable measurements.

## Frontier v3 bounded execution

The [Frontier contract](frontier-v3-contract.md#maintainability-performance-and-observability)
owns current v3 release budgets. Its full profile is a release/scale obligation,
not a repeated campaign for every correction. ARC-001 aligns the structural
work and retention model; it does not certify an unmeasured runtime speedup.

Due work, dirty subjects, semantic slots and naturally available chunks use
bounded indexed work sets. Reuse unchanged immutable topology/static plans;
one changed organ, field generation or actor must not reconstruct unrelated
world plans or scan accumulated history. Bound the complete caller path,
including index validation, callback fan-out, projection and diagnostics.

Separate simulation advancement, physical interaction/recovery and presentation
queues. Budget each with fair scheduling, bounded count/bytes and visible
backpressure. Optional presentation can be deferred/coalesced only when safe;
never drop a critical cause, irreversible effect or recovery obligation to meet
a budget. Expose simulation lag, oldest deferred work, queue count/bytes, active
custody and dirty-set work so starvation is distinguishable from cheap ticks.

Retired cycles compact under referential/recovery fences, not elapsed TTL alone.
Long-running COLD cycles must not make the next first visit replay every cycle.
Multiple arrivals share current indexed work; they cannot multiply canonical
progress. Bounded admission must have an explicit outcome, never invisible
starvation. No thread pool substitutes for eliminating redundant/unbounded work.

## Shared physical journals (2026-10-09 adoption)

All nine current physical SavedData families use the same bounded journal core:
actors, fields, block provenance, depot/player clicks, object boards, hopper
identities, external observations, managed effects and infection provenance.
They remain separate explicitly typed world/dimension/store streams. The family
adapter owns codecs, immutable evidence, physical admission and retirement;
storage never chooses a crop, actor, effect, conflict or canonical transition.
The obsolete stage-field explosion registry and its writer are removed, not
migrated. Canonical transaction WAL and Minecraft region/entity/player storage
retain their existing owners and non-interchangeable contracts.

`FrontierV3PhysicalStoreKind` declares stable store/table wire tags; composition
rejects duplicate table tags, store tags and paths. `FrontierV3PhysicalStores`
registers exact owners, flushes them at physical turn exit and drains every
registered level on orderly server release, including stores opened without a
canonical runtime. An explicit owner handoff drains before replacing a ledger;
ordinary lookup cannot silently replace a competing writer.

Actor streams retain the4096-row/32MiB profile. Other physical streams use the
explicit131072-row/64MiB profile, allowing65536 block/patch records plus headers
without globally increasing actor capacity. The hard recovery ceiling is the
same declared profile; checkpoint NBT accounting permits128MiB structural
overhead for physical streams. The frame, queue and retained-tail bounds below
remain unchanged. Aggregate capacity is checked before durable admission; an
oversized effect fails visibly rather than admitting an unrecoverable witness.
This is a bounded refusal, not a promise that every theoretical combination of
per-effect maxima fits one stream.

Field initialization/ownership use immutable256-cell ID buckets plus small
headers. External/managed observation queues retain immutable256-record shards
and absolute cursors: advancing one candidate changes a header, not a copied
whole remaining list. Tombstones retire completed shards and codec caches.
The player-click stream journals only the exact changed container witness.
Initial population and recovery may inspect the complete image; ordinary
progress does not re-compress/re-encode an unchanged registry. Background
checkpointing still compresses immutable complete images at rollover.

Player edits, external/managed explosion capture and infection replacement
require a forced journal receipt before their non-replayable physical action.
Result publication and canonical acknowledgement remain separate boundaries;
managed effect evidence retires only after canonical confirmation. Infection
replacement retains its exact predecessor in PREPARED evidence. `setDirty` or
queue submission alone is never durability. Co-publication of metadata and
facets is atomic only inside one stream, not across PM/native files.

All live and read-only file readers recover checkpoint plus tail and validate
kind/world/dimension/schema and record key/content. Vanilla failed-load fallback
uses that same recovery and cannot fabricate an empty ledger. Old field15,
observation2, managed-effect4 and infection5 schemas are rejected; deployment
requires a fresh disposable world, with no dual write or compatibility decoder.
Supported unchanged schemas still use the new physical journal envelope.

`v3 inspect performance.physicalJournal` is now an array of registered entries:
`world`, `kind`, `journal` pressure (durable/checkpoint sequence, queued writes
and bytes, retained bytes, force groups, appended bytes). Consumers must not
treat it as the former actor-only object. Legacy simulation startup/preflight
and broad callbacks are excluded before runtime construction during a selected
v3 launch; the actually shared DH service has its own lifetime.

Implementation and verification receipt:
`work-orders/PM-SHARED-STORAGE-20261009.md`. Source63a7c011 is deployed to fresh
test world R76; its delivery receipt records live validation. This adoption has
no comparable before/after TPS or whole-path speedup measurement. The R75 actor
measurement below does not establish a speedup for the other eight streams.

## Physical carrier journal (2026-10-09)

The generic keyed-image journal owns storage ordering, checksummed frames,
durable receipts and checkpoint publication. The carrier codec alone owns its
closed actor facets; the body controller alone owns permission to insert bodies.
Canonical simulation/WAL and Minecraft-world mutation remain single-writer on
the host. This is an I/O lane, not parallel world mutation or a second authority.

Carrier format11 replaces full compressed registry writes with dirty-actor
images and explicit tombstones. The compressed `.dat` is only a checkpoint;
the sibling `.dat.journal` contains the required ordered tail. Every recovery,
offline repair and diagnostic file reader must combine both. Existing disposable
format10 worlds are rejected; deployment needs a new test world, not inferred
migration or a checkpoint-only fallback.

An ordered bounded writer groups up to16 queued requests within one64-record
segment and acknowledges them only after file force and required directory
force. Each request retains its exact sequence and immutable submitted bytes.
The queue admits at most64 requests/16MiB per store; the shared disk executor
is bounded. Writer failure latches visibly and fails outstanding receipts;
capacity pressure never drops a critical cause or silently declares success.
Only one writing store owns a path at a time; offline publication requires a
stopped writer, not simultaneous repair of an active server ledger.

At128 new records a separate bounded checkpoint lane compresses an immutable
image. Atomic checkpoint publication precedes retirement of complete covered
segments; the active tail remains authoritative. Retained journal bytes are
bounded at64MiB; the keyed image is bounded at4096 rows/32MiB and keys at256
characters, so a published snapshot remains within its recovery budget.
Compression may overlap appends; file publication/deletion is
serialized with recovery reads. A truncated final frame is not an acknowledged
cause and is ignored read-only on recovery; the next authorized append repairs
that exact tail. Complete corruption, a sequence gap or missing checkpoint
fails closed. Orderly shutdown drains admitted writes and pending checkpoints.

Body insertion now retains an explicitly declared unstarted ticket containing
exact identity/epoch, admission predecessor, residence generation and receipt.
The first request returns DEFERRED without inserting. A later server turn checks
the receipt, current declaration, geometry and resource projection again before
consuming the ticket and attempting insertion once. A proven unattempted request
can be cancelled on demand loss or shutdown; cancellation itself is durably
ordered. Unknown physical insertion outcomes retain the existing ambiguity
protocol, never a fabricated absence or a retry permission. Required departure,
entity-write acknowledgement and canonical WAL fences still wait synchronously
where their current caller requires the result; their safety is not weakened.

`v3 inspect performance.physicalJournal` reports durable/checkpoint sequence,
queued writes/bytes, retained bytes, forced groups and appended bytes. The
counts are observed work, not a measured speedup. No speculative parallel
navigation/calculation pool is introduced: immutable candidate computation may
be delegated later only for an evidenced CPU bottleneck, with revision-bound
validation and canonical commit on the single owner.

Implementation evidence: active checkout `pm-f06r3-facility-lane-recovery`,
implementation commit `57f0278e` (base `e55cb673`). Focused selection104 cases:
103 passed initially, one obsolete compiled-owner expectation corrected and
its complete five-case suite passed. Generic journal tests cover shared force
receipts (two requests/one force), immutable submission, row/tombstone recovery,
checkpoint preservation, torn tails including a full segment boundary, corruption,
publication failure, background snapshot plus later tail and image capacity.
`guardrails`/`verifyPackagedJar` pass; canonical governance validators pass.
Native `runFrontierV3SceneGameTestServer -PfrontierV3GameTestSlice=body-lifetime`
executed20 nonempty cases, all passed, including real async acknowledgement and
proven unstarted cancellation. An earlier failure exposed the missing world
identity in the ticket key; corrected before the green native run. Storage
capacity hardening and explicit test-only crash-fixture renaming were verified
locally afterward, not claimed as another native artifact run. The native
server saved all dimensions and exited. Clean detached release was deployed as
R75 with a fresh format11 world. Controlled five-pair real-file A/B measured
32 identical synchronous durable actor mutations against the baseline JAR:
median20.824ms old versus2.714ms new (7.67x), encoded bytes291562 versus19904
(93.17% reduction). This excludes checkpoint rollover and is not total TPS,
physical disk-device bytes or whole-pack speedup. Ordinary full-pack boundary
and actual indexed actor-admission scenarios passed; see
`frontier-v3-carrier-journal-r75-20261009.md` for identity, JFR and limits.
No full player acceptance or asynchronous canonical-WAL change is claimed.

## Legacy runtime and Visuals reference

The following schema-v40/Visuals values describe their respective historical
pipelines, not Frontier v3 simulation targets or current-stage acceptance.

Schema v40 has one server-thread admission controller. Non-critical regional,
railway, settlement and projection work shares a default soft budget of 3 ms
and 256 weighted operations per tick. Safety-critical combat attribution,
reconciliation and an already-started idempotent operation are not abandoned
when that soft budget is exhausted.

Use `/pale_mirror performance` as an operator to inspect the current tick,
deferred work, cumulative overrun count and per-work average/max duration.
The settings live in `pale-mirror-server.toml`:

```toml
[runtime]
softBudgetMillis = 3.0
weightBudget = 256
```

Authored residents have a separate visual-only AI LOD in
`pale-mirror-visuals-server.toml`. All 48 stable entities remain visible, but
only 16 nearby residents run vanilla AI by default.

Fresh-world planning is also bounded. Visuals samples mountain biomes cheaply,
derives nearby foothill candidates and ranks their footprint without constructing
NoiseChunks. The default six-worker genesis pool evaluates indexed terrain-ranking
work and region-feasibility waves concurrently. A bounded two-waves-ahead window
lets a free worker take another candidate instead of waiting for the slowest probe. Nested work runs inline to avoid
pool starvation; final acceptance remains sequential and deterministic. Set
`genesis.plannerWorkers` between 1 and 16 in `pale-mirror-visuals-server.toml`;
`1` retains the serial recovery profile. An accepted region normally spends one exact five-point settlement
survey, two bounded mountain-face and local-pad searches and one bounded railway-corridor search.
For a production-sized batch, each surveyed center may try up to 24 exact
settlement candidates and each MineSite may try at most 24 exact candidates.
The accepted Township then caches a 130-column, sixteen-block-grid relief
snapshot and may evaluate 24 deterministic layout transforms inside one shared
768-probe ceiling. Only the selected rail corridor is verified column by
column; local cut/fill remains current-chunk worldgen work. `performance` reports site, mine
and rail probe classes separately, along with planning and catalog compile
time, worker count, evaluated candidates and discarded speculative attempts. The production output range remains 3/5/6 with a 160-center survey cap,
20,000-block radius and 2,500-block final spacing. Packaged two-start and visual
audit gates may explicitly request one complete region because cardinality and
multi-region spacing have separate tests. The latest full-modpack natural visual
audit on seed `7391842605318702447` planned one strict dual-mountain region in
86.932 seconds and compiled 263 chunk slices before observing exact genesis
stamps. This is an integration/aesthetic fixture, not a production five-region
throughput claim. Production never weakens terrain to force its target.

On the full private-pack seed `7391842605318702447`, the production 3/5/6 survey
fell from 172.745 seconds in the sequential profile to 51.245 and 51.330 seconds
with six workers (3.37x). Both clean starts produced the same probe counts, catalog
hash `f9abf85a50a91e27` and byte-identical genesis SavedData. Equal-distance coarse
height samples use a coordinate-key tie-break so immutable-map order cannot alter
roads or managed-area heights between JVM processes.

For an evidence-grade profile, run the private server on its JDK 22 runtime and capture JFR:

```bash
scripts/capture-runtime-jfr.sh <server-pid> 120s build/profiles/pale-mirror-runtime.jfr
```

The Visuals GameTest and packaged two-start harness use only the current supported
runtime profile; the retired Sable renderer integration is not loaded.

The hot-path acceptance target is: no loaded-chunk static construction, no
full loaded-entity census, no vertical chunk-volume rail scan, no repeated
unchanged projection reconciliation, and no watchdog stall. Worldgen slice timing
is also reported by the Visuals runtime and in
the server log when its immutable catalog becomes ready.

## Frontier v3 physical-scene scale protocol

The v3 world keeps exact people, bioforms, cargo and objects. It must not
answer a large event by turning forty residents into one representative body or
by maintaining a second aggregate combat state. A large operation is instead
split by spatially indexed, bounded HOT subscenes; each subscene retains the
same canonical actor/cargo IDs and durable lease/recovery rules as a small
operation.

Before raising a configured physical-scene or active-mob limit, record a
baseline and candidate using the same commit family, seed, world/profile,
player route, view distance and warm-up. Capture a JFR from the actual server:

```bash
scripts/capture-runtime-jfr.sh <server-pid> 120s build/profiles/frontier-v3-scene-<seed>-<limit>.jfr
```

The evidence bundle must state active scene/subscene count, exact managed body
count, operation/lease/cargo correlation, queue depth and deferral age, p50/p95/p99
MSPT, maximum stall, CPU, allocation/GC and save/recovery result. Pair it with
a checked-in declarative causal scenario at the same seed. The candidate is
rejected on any duplicate/lost exact identity, unbounded queue, silent COLD
fallback, missing post-restart evidence or regression outside normal variance.
TPS alone is not a scale result.
