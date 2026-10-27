# Simulation throughput and navigable graybox profiles

Accepted implementation assignment, 2026-10-27. Main alone; no subagents.
Baseline source `3d6f14756`, active disposable r276/r30 world. The system goal
tracks this entire plan, not a source-only checkpoint. Scoped commits and verified
deployment are authorized; push is not. Preserve the separate pack/source histories.

## Outcome and scale

Remove sustained canonical-time debt under ordinary server time while preserving
individual residents, resources, work duration, deterministic publication and
recovery. Target a world up to 100,000 by 100,000 blocks, up to 100 settlements,
20–40 residents each (2,000–4,000 total), and demand from up to five players.
Do not generate or retain every chunk of that world. Keep room for future hive
activity; hive behavior, combat and flow fields are outside this implementation.

The existing regional geometry, component/portal/macro navigation, shared actor
execution (UAE), timed movement, storage, services and Minecraft-operation protocol
remain the owners. No second route planner, resource ledger or COLD population.
Selective detail changes computation, not the elapsed duration or identity of
people, their tasks and cargo. Unknown geometry is never assumed to be air.

## Established contradictions

- r275b retained observations: native ~20TPS but canonical ~17–18ticks/s. A later
  1,295second interval had23,913canonical ticks versus25,896native ticks,
  106,362forced WAL groups, and pending debt3,988->5,971. These are observations,
  not an additive attribution or proof of an unmeasured speedup.
- `FrontierV3ServerRuntime.resumeOrdinaryTurn` admits one transaction per ready
  continuation. `runPersistenceTurn` closes that continuation with a flush, so
  even BATCHABLE progression frequently receives a separate durable group.
- `StationWorkProcess` consumes the common route one cell at a time through
  `BakeryColdStep.MOVE`, then wakes after20ticks. Other work families already use
  `ActorMovementProcess` and timed segments. Shared pathfinding alone did not
  unify temporal execution.
- `FieldDevelopmentLand.survey` reuses regional geometry but re-reads its retained
  block witness and refreshes admitted cells. Economic reviews include this land/
  forest survey. JFR confirms cost; exact cache churn is not established.
- The kernel retains one exact pending scheduled calculation and its predecessor;
  awaiting it yields canonical advancement. Current asynchronous I/O frees the
  native thread but does not by itself remove serial publication waits.
- `FrontierWorldLayout` and bootstrap require exactly12settlements; bootstrap
  names/cardinality and agriculture mapping also assume this set. HumanPopulation
  caps4096residents/1024households; additional registries/codecs need a coherent
  bounded scale audit. Raising one constant does not prove scale readiness.

Evidence: `build/wood-demand-and-latency/{findings.json,cold-before.jfr,
predeploy-delta.json}` and `build/server-economy-r275b-check/`. Capture current
r276 measurements for the new comparison; older r29 measurements have different
economic content and cannot establish a precise r30 speed ratio.

## Implementation sequence

- [x] A. Restore useful batching inside bounded owner work. Preserve the original
  per-observed-tick allowance across all callbacks. Several admissible BATCHABLE
  transitions may share a persistence turn; required physical-effect boundaries
  still await their exact durable prefix. Bound cooperative work and preserve
  order, clocks, checkpoint fences, backpressure, error propagation and recovery.
  Do not hide debt by dropping ticks, increasing timeouts or merely raising budgets.
- [x] B. Migrate station-worker travel completely to common timed ActorMovement.
  Add one explicit typed station-work context/provider through existing closed
  composition. The job owns phase/materials/station attendance; movement owns
  route, elapsed position, arrival and interruption checkpoint. Integrate service
  approach/exit, UAE, HOT observation, hunger, death/cancel, source loss and replay.
  Remove the active per-cell movement writer and its obsolete private-mechanic
  tests. Validate stale/foreign owner and no duplicate actor/cargo consequences.
- [x] C. Reuse immutable geometry and production facts at their proper boundaries.
  Eliminate evidenced repeated field/forest work using complete relevant regional
  dependencies and bounded caches/indexes. A block or permission change invalidates
  its dependent facts. The economic owner consumes suitability/capacity facts;
  it does not own terrain surveying, collision or resource production.
- [x] D. Decouple expensive advisory preparation from global canonical waiting.
  Use explicit owner-declared input versions and bounded snapshot calculations.
  Publish decisions under the existing single canonical authority after validating
  current dependencies. Retain safe behavior while new advice is pending; stale
  advice cannot create stock, spending, access or physical execution. Preserve
  actual due-event ordering and deadlines. Reuse existing preparation/navigation
  workers; introduce neither thread-per-settlement nor independent state writers.
- [x] E. Generalize graybox content and bounded population to declared cardinality.
  The layout/content profile owns explicit placement, names, population and
  resource/territory distribution. Bootstrap, agriculture, storage, codecs,
  workforce, peer discovery and diagnostics consume it consistently. Retain
  stable historical profiles; new immutable content selects a fresh test world.
  Supply a controlled flat baseline and a100settlement/2,000–4,000resident scale
  profile with irregular distances and world bounds100,000square. Reuse production
  owners; do not precompute all pairwise detailed routes or generate the full map.
- [x] F. Add the main terrain profile through one deterministic geometry definition
  used by native generation and COLD facts. Include hills with legal ascents,
  river/ravine crossings, bridge/tunnel multi-level contacts, narrow pedestrian
  passages with pack-animal bypasses, dead ends/low ceilings, passing places and
  forest corridors. Preserve sensible settlement/building pads and economic
  environments. The navigator receives geometry, not an authored correct answer.
  Player edits flow through the existing observation/invalidation mechanism.
- [ ] G. Integrated verification and delivery. Compare controlled before/after
  throughput/work counts and verify real production, meals and trade. Run edge
  cases below, retain decisive evidence, review/commit each repository separately,
  build from a clean detached source and publish/install with fresh identity proof.
  Deploy the terrain profile as the primary manual-test world. One server has one
  selected world; switching is operator-owned and needs no client-side procedure.

## Acceptance scenarios

1. Ordinary COLD production/trade with live-clock debt observed over a sustained
   interval: debt does not grow indefinitely; temporary load can be caught up.
   Compare simulation/native time, work and publication counts, preparation cost,
   disk groups and complete native host-turn peaks. A lower empty-world tick is
   not proof of meaningful throughput.
2. Station worker completes pickup->load->process->unload->delivery, including a
   need interruption with held materials, HOT->COLD->HOT and restart. Actual
   positions and work duration remain causal; entry never replays an old journey.
3. Multiple farmers/miners, food collection and caravans share routes/service
   points. A slow review, unavailable store or one blocked worker cannot silently
   stop unrelated settlement activity. Genuine absence of a route stays visible.
4. Block/unblock a bridge, alter a stair, obstruct a narrow passage, remove a
   production input or destroy a container. Validate local invalidation, correct
   alternate route/wait and resumption without reconnect, duplication or resource loss.
5. Distinct body profiles choose legal routes; bridge and tunnel can coexist at
   the same X/Z. Check player ingress during an ascent/obstacle detour and a
   caravan's passage, retaining linked frames and semantic progress evidence.
6. Scale profile reaches100settlements and the declared resident range without
   registry/codec overflow. Observe ordinary and accelerated COLD, five independent
   player-demand footprints and transition pressure, plus an actual full-pack
   graphical client for representative HOT stories. Distinguish simulated demand
   from five-real-player/co-op acceptance; do not fabricate that evidence.

Use focused semantic/negative/recovery coverage for the changed paths and
existing declarative native scenarios. Timing is observational performance data;
do not add arbitrary-duration unit assertions or tests of deleted implementation
details. Broad inactive-hive debt stays explicit and outside this scope. Full
applicable gates belong at coherent integration boundaries, not after every helper.

The plan is not an implementation-completion claim. Progress and exact running
operations belong in the sole canonical governance CONTINUITY.md.


## First implementation evidence

A is implemented as a bounded prefix of up to four cheap ready transitions in
one persistence turn. A declared costly action, pending calculation, pending
commit or exhausted original budget yields. No store flush policy, physical
fence, per-tick allowance or canonical event coalescing changed. The focused
runtime case proves multiple individual transitions share one queued WAL force
and recover their exact remaining schedules. Existing delayed-durability,
captured-instant and shared-budget cases passed in
`build/simulation-throughput/cooperative-batching-focused.log` (14seconds).
This establishes batching correctness, not a measured deployed throughput gain.

Current unmodified r276 baseline over187seconds advanced4,475canonical ticks
versus3,741host ticks; debt decreased4,084->3,350. It still forced19,238groups
(~4.3per canonical tick). The debt is workload-dependent, not permanently
increasing at every instant. See`build/simulation-throughput/r276-baseline-*.json`.

Phase B source now delegates station-work travel to a typed common movement provider,
retains an exact phase/material-trip/selected-service relationship in schema309,
and parks completion until arrival or a safe owner transition. Station scenes no
longer actuate journeys; delivery cleanup can settle outside work hours. The old
per-cell bakery MOVE and its private HOT result translator are deleted. Coherent
production/food/multi-input/recovery and process-catalog checks passed (47seconds); no native or deployed
performance claim is made from this source checkpoint.

Phase C adds opt-in complete regional read versions to shared block knowledge.
A versioned completed witness retains regional tokens instead of duplicating its
block image. Development ground includes observed block state/uncertainty, scars,
geological exclusions/declarations, bootstrap and protected-column permissions.
Unversioned providers retain pointwise validation. New canonical land admission
still refreshes selected block samples. Land, uncertainty, permissions and recovery
checks passed in build/simulation-throughput/land-version-focused.log (39seconds).
This is local query reuse, not permission to publish stale economic decisions.

Phase E source checkpoint introduces r31 baseline and100settlement scale profiles.
The latter has100k-square bounds, irregular placement,2k–4k initial residents and
explicit mineral-producer content. Bootstrap/names/agriculture consume declared
cardinality. Local access roads replace eager expansion of world-spanning roads
in that profile; shared road point queries now use a chunk index of the same
segments used by physical compilation. Historical profiles retain their original
content fingerprints. Canonical capacities/hydration cover residents, authorities,
work/deposits, actors, peers and strategic records; physical mob/scene limits are
unchanged. Complete100settlement state and snapshot round-trip passed in
scale-state3.log (25seconds). Runtime load/throughput and demand-footprint checks
remain phase G; this is not scale performance acceptance.

Phase F source uses one bounded immutable natural voxel declaration for native
chunk generation/base columns/heightmaps and COLD multi-level collision. Fixtures
include a terraced hill/tunnel, river/ravine/bridge, pedestrian-width passage with
an animal bypass, and a low-ceiling recess. The shared block-surface overlay also
serves forest knowledge; observations and unknown-state fences retain precedence.
The new terrain profile is frontier-v3-simulation-terrain-r31. Native ingress,
visual evidence and deployed throughput remain G, not established by source tests.

The first100settlement runtime found and repaired an initial schedule-capacity
mismatch and an OOM caused by full-world service geometry and repeated global
container overlays. Clearance now uses the common local declaration index and
validates boundaries touched by the actual route. Static container exclusions
are shared before local permission overlays. Immutable extraction-layout validation
retains the declared maximum cohort instead of repeatedly clearing a64entry cache.
No live occupancy or resource admission is cached as geometry.

The ordinary canonical engine then reached1000ticks with100settlements/3029people
under the existing2GB test heap. A complete saved image is23,113,049bytes. Snapshot
bounds now accommodate a complete100settlement state independently of the unchanged
16MB transaction payload bound; both envelope directions enforce their matching
bounded capacities. Diagnostic absent plans are represented as null. This early
horizon precedes the first economic reviews: it does not establish market balance,
long-run memory, host throughput, five-player performance or HOT acceptance.
Evidence: build/simulation-throughput/{local-service-geometry-final.log,
scale-runtime-1000e.log,terrain-scale-final.log,scale-runtime-1000c.jfr}.

## Advisory execution boundary (phase D)

Registered scheduled owners may explicitly expose disposable snapshot advice through
ScheduledAdvisory. The kernel holds only a bounded volatile calculation binding;
the original action and deadline remain canonical. Pending/busy/obsolete advice
uses fair existing readiness polling, consumes the same original turn allowance,
and emits no retry WAL. Independent due work continues. Cancellation/recovery
rebuilds advice from the retained action, never inferred mutations.

The owner validates current dependencies and freshness, then supplies a fresh
ScheduledPreparation bound to the current exact predecessor. Only this short exact
stage may publish events under the ordinary WAL fence. Kernel/coordinator code
contains no economic or other family branches. The existing bounded preparation
worker serves both stages and checkpoint encoding; busy capacity is backpressure,
not a fatal rejected execution. Navigation read snapshots/dependencies are carried
through the same existing planning-continuation owner, including final wake binding.

Economic advice contains prices and a conditional supply/cultivation forecast,
not reservations, contracts, worker assignments or resource effects. Its owner
checks its input generation, participant terms, peer input/publication bases,
geometry captures and review expiry before exact current trade planning. Ordinary
body motion does not invalidate this conditional forecast or award arrival.
Exact pickup, stock/title/cash/worker admission and physical effects remain with
their existing owners. The host wrapper also forwards the economic owner's
addressed reconsideration requests; it previously omitted that capability.

D focused evidence: advisory-owner-final.log passes kernel/owner/company/common
travel and real worker boundaries; advisory-integration-final.log passes the
Frontier architecture/quote/economic slice and30of31runtime tests. The remaining
clock-debt test incorrectly compared two independently timed full economic worlds;
it now checks the same clock/debt equivalence with four real pure due transactions
and WAL, preserving budget, operator-time and recovery assertions. The corrected
case passes in clock-debt-final.log. Native throughput/late-result pressure remain
G. Advice diagnostics expose separate completed/accepted/rejected work counts and
measured CPU duration, never using them to control canonical policy.

The integrated100town stand exposed advice queue starvation: ready results were
being evicted by new submissions before their owners were polled. Capacity now
backpressures new advice; held/obsolete exact owners release their slots. This is
not a relaxed forecast-validation rule. The retained native diagnostic records
5251completed/9accepted/0rejected/5235cancelled-ready at canonical2. Same-world
native verification follows the correction.

The longer100town COLD run also exhausted the2GB diagnostic heap while extending
one quarry: every retained draft rebuilt all declarations' block ownership. The
geometry directory now shares immutable per-site parts and validates conflicts
only between parts in the same chunk. Depletion/custody remain outside the
immutable declaration key. Existing extension/observation/recovery checks plus
source-conflict and remote-part-sharing assertions cover this boundary. A passing
focused test alone does not close the long scale run.
