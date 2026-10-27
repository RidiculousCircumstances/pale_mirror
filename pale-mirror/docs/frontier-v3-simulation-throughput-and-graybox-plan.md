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
checks its own input generation, participant terms, actually quoted buyer envelopes,
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

The same-world queue run then accepted202reviews but rejected1066: every
known peer publication/input generation invalidated each forecast, including
unreferenced alternatives. Advice now distinguishes dated market observations
from exact conditional offers. Own input generation, participant identity/policy, geometry and expiry remain
strict. Peer prices and stocks are dated observations; only actual quoted buyer
plan/input envelopes become publication dependencies. Current stock/cash/crew/contract admission is unchanged.
The targeted market/advice/conditional-offer and architecture checks pass in
local-advice-dependencies-final.log. This does not yet establish scale throughput.

The2GB long-run failure was traced with a task-local heap dump, rather than
fixing its last allocation stack. Completed land facts retained273MB through
FieldDevelopmentLand alone;32old full-world protected-column sets retained
another320MB. Land permissions now compose shared immutable regional producer
parts. Field witnesses retain only consulted permission/geometry tokens; current
samples and exact admission checks still run. The same column/block mechanism
serves field and extraction ground readers, preserving local-vs-foreign worksite
protection. Existing cultivation, damaged/unknown ground, extraction extension
and navigation tests plus local permission-change checks pass45s. The retained
heap report is scale-live-retained_Leak_Suspects.zip; no simulation snapshot or
physical ownership protocol changed.

The next7m29 COLD pressure run passed the prior memory failure and reached the
physical lifecycle retention boundary: the257th concurrent harvest obligation
exceeded the256per-owner share. The100town profile permits300farmers. Canonical
lifecycle shares now512 and the matching aggregate intent/observation bound8192;
composition still rejects overcommitted/mismatched shares and unresolved work
still cannot compact. HOT scene/body/work budgets are unchanged. Changed
composition requires fresh schema310; no old-world conversion or compatibility
inference is added. The existing calibrator now prints bounded6000tick progress
and the exact failing canonical instant instead of a silent long wait.

A60s actual100town JFR found repeated global resident filtering in labour and
procurement, plus whole-container station lookup (including a nested machine
validation scan). HumanPopulation supplies a derived settlement-membership
index; ProductionStationDirectory indexes explicitly declared station/facility
identities. Current life, migrations, eligibility, stock, access and machine
operation checks remain at their existing owners. Migration, replacement, fuel,
processing, procurement and conditional-supply regressions exercise these paths.
Separate verification directories avoid mutating the classes of a running audit.

Fresh schema310 native pressure still rejected forecasts when any declared peer
published a price. Price observations now follow the same dated-input boundary
as stock. Actual conditional quote buyers retain both captured input generation
and accepted plan version; current matching/admission still checks current terms,
cash, stock and crew. Focused advice/conditional-quote/participant and guardrails
checks pass25s (dated-market-observations-final.log). No sustained throughput
claim follows from this source check. The terrain pilot also uses grounded visit
anchors: creative mode alone does not suspend gravity, so airborne exact anchors
were an invalid visit precondition, not a chunk-loading defect.

The100town canonical JFR (canonical-scale-progress.jfr,30s) attributes158
execution samples directly to the nested global pending-order predicate. Quote
publication now derives one exact local key set after withdrawals and updates it
only after a successfully placed order. All peer/item/side/container/availability/
expiry predicates are preserved. Existing autonomous dispatch, expiry, partial
purchase, stock/payment and conditional-consent checks plus guardrails PASS36s
(local-quote-book-final.log); no timing assertion or new test framework.

909 terrain full-pack pilot passed27actions and six visibility assertions; all six
frames inspected. Tunnel/bridge/narrow/low-ceiling geometry is visible. The field
frame is partly obscured by the smeltery, so it does not prove a complete work
story or visual acceptance. Task-owned20FPS client/display exited after the run.
Main/private same-schema same-world deployments909 verified with pinned SHA;
ordinary-time samples remain in dated-market-live-samples.jsonl. No debt-closure
claim yet.

The909ordinary336s sample remains insufficient: main5.92canonicalticks/s and
100town6.54, with growing debt. Native100JFR again attributes95samples to the
global pending-order predicate, corroborating8ee's local-key repair. Completed
forecast acceptance improved, but that is not clock closure. Existing preparation
telemetry now counts bounded owner-supplied rejection causes (no policy reads);
it will distinguish own-input/geometry/conditional-consent churn on the same world.
The ordinary ready prefix now yields after a2ms host slice rather than exactly
four cheap transitions, always under the original tick's structural allowance.
Required physical fences still stop immediately. Host time affects only yielding,
never canonical elapsed time or domain permissions. The existing grouped-WAL/
recovery test uses a controlled host clock instead of assuming the real machine
can always execute multiple transactions in2ms. Runtime/file-store/ready-turn
and guardrails PASS43s; advisory/preparation diagnostics PASS28s.

Actual dec0 main telemetry identifies119owner-input and65buyer-consent rejects in
the first retained sample, with zero geometry rejects. MarketEconomicInputChanges
marks incoming quotes as MARKET, but the previous forecast fence treated those
notifications exactly like lost stock/cash. The owner now accepts a shifted input
generation only when the same accepted plan is retained and the accumulated dirty
causes contain MARKET alone. Hard causes are never cleared by that proof. It
rebinds publication and conditional-quote source generations together, preserving
assessment/expiry, exact buyer envelopes and all current admission checks. Existing
actual runtime-composition fixture checks valid refreshed publication and rejects
CASH after MARKET; conditional offers/autonomous trading/input tests and guardrails
PASS45s (dated-own-market-input-final.log). No persistence schema change.

The same MARKET-only proof applies to an actually quoted buyer without changing
its accepted plan binding. A changed buyer plan remains rejected; changed cash,
stock and commitments remain rejected. A real conditional-offer fixture now proves
that a buyer's unrelated incoming quote is not funding withdrawal and CASH still
is. Diagnostic reasons split BUYER_PLAN from BUYER_INPUT. Focused actual advice/
conditional-offer plus guardrails PASS21s (dated-buyer-market-input-final.log).

A separate generic accounting contradiction was found in the actual pending-advice
branch: every readiness poll recharged the owner's full computation weight
(e.g.301units for100known peers). Fresh asynchronous admission still pays that
full weight; retained/busy polling now pays one bounded unit plus the unchanged
action/classification/execution allowance. Final accepted exact work still pays
its full declared execution cost. No scheduling budget is raised, due action is
not consumed by waiting, and capacity busy is not a calculation. The existing
retained-action/cancellation test now distinguishes initial7-unit admission from
a1-unit subsequent poll; kernel/advisory and guardrails PASS18s in
advice-poll-accounting-final.log. Host shared-budget/recovery check follows.

Exact-predecessor preparation shared the forecast/checkpoint worker and queue.
A blocked independent forecast therefore held the entire canonical predecessor
when an unrelated exact preparation queued behind it. The host now reserves one
bounded exact-preparation worker/queue, separate from one bounded independent
forecast/checkpoint worker. Publication/validation/completion remains on the sole
server owner; workers only calculate immutable snapshots. Existing ownership test
now holds a forecast blocked while an independent exact preparation completes;
focused preparation/runtime and guardrails PASS39s in
independent-exact-preparation-final.log. No physical or canonical work budget raised.

Next evidenced integration correction: GoodsParticipantWakeup currently aliases
all quote opportunities to the full economic REVIEW; MARKET is an urgent
SettlementEconomicReviewRequests cause. Therefore each quote publication can
trigger whole-economy re-evaluation by every counterparty. Split existing
OPPORTUNITY kind into current-plan quote/matching work and retain full reviews
for addressed hard inputs/ordinary review deadline. This is not artificial price
smoothing or delayed physical work. Current consent, expiry, resources, finances
and actual contract admission remain authoritative.

Current-plan trading now uses the already registered OPPORTUNITY schedule with
its own coalesced identity. It matches/publishes actual orders from retained
consent and current stock/cash/expiry; it neither runs the economic solver nor
renews prices, accepted plans, review revisions or the full-review deadline.
MARKET-only input changes retain the accepted plan's full-review deadline; NEED,
CAPACITY, CASH and DELIVERY still request prompt full review. New order identities
include the exact action/execution generation because an opportunity does not
increment economic reviewRevision. A typed trip navigation-wait result retains
its opportunity successor through common route waits/recovery rather than losing
selected pending work or relying on a diagnostic string. No-business completion
uses the already declared cancellation effect, not an undeclared consumed payload.

Commercial expiry/partial-payment/conditional-offer and mixed-trip in-flight
recovery/return checks passed. One bootstrap fixture selected a held schedule and
relied on the removed REVIEW alias; it now selects an actually eligible action.
Current runtime-composition/ordinary trading/publication recovery checks PASS31s
in current-plan-recovery-final.log. The broader preceding commercial slice had
only that classified fixture failure; its mixed-trip terminal checks passed.

The obsolete2c100town diagnostic was intentionally stopped after62m36 with last
reported18000/rev145685, no memory/capacity quarantine;30000 was not proved. It
still ran the superseded immediate full-review market cascade. Its JFR/logs are
retained. A fresh current-code run, not further waiting on old classes, follows.

Recovery profile additionally found TransactionReplayer encoding the full world
after every WAL transaction (actual publication-recovery stack in
current-plan-test-thread.txt). Every transaction, event envelope, schedule effect
and transition is still validated in order; snapshot encoding now checks the
final recovered output once. No partial recovered state is exposed on any
validation/encoding failure. Existing kernel corrupt-history, scheduled recovery
and economic recovery checks PASS13s (replay-negative-final.log), together with
the actual economic publication/WAL fixture in the31s connected slice.

Cooperative callback boundaries now share a bounded host WAL group across
continuations (50ms operational deadline, original64record/64MiB bounds). The
delayed wake only notifies the canonical owner; disk receipts and publication
still belong to that owner. DURABLE_BEFORE_EFFECT immediately forces its entire
prefix. Save/close still force remaining facts, and failed/backpressured writes
cannot acknowledge memory. Obsolete test-only turn methods were removed; focused
store/runtime/durability recovery and guardrails passed in45seconds
(`cooperative-wal-groups-final.log`). This closes the source contradiction where
every cooperative yield forced a tiny group; deployed throughput remains to be
measured and G stays open.

Subsequent native100town profiling found repeated world-sized destination claim
sets on the server thread. Read-only surface indexes now retain immutable body,
movement and meal inputs separately; callers query exact local candidates and
keep original self/incoming/physical exclusions. Current2fb2 native55 and affected
service/meal negative/recovery cases pass. After this correction the100stand
advanced24012->24784over333seconds while ordinary debt still grew378->6267;
this is progress, not sufficient throughput. A native profile then established
repeated body-profile regional revision expansion/hashing, immutable facility
port reconstruction and pending-food-container scans. These projections now
reuse complete immutable owner inputs with explicit retention bounds; changed
terrain, structure or meal/obligation maps still invalidate them. Existing narrow/
wide-body, neighbour-change, service and meal cases passed39seconds in
immutable-service-geometry-focused.log. G remains open.

The retained100town world established an additional liveness contradiction:
a checksummed1437-record WAL tail across11ticks contained1437reschedules,833
meal and400machine reviews, almost no domain work. A saved image had399idle
machines and over1600meals without travel; direct read-only route probes found
valid paths. The cooperative navigator evicted ready results at128entries while
over1000canonical continuations could be waiting. A delayed consumer could
therefore lose its completed calculation and repeat it. Ready calculations now
remain protected for live continuations, including their future retry deadline,
and due/runnable consumers until they consume or yield. Future
recovery previews and owner-held work remain optional cache consumers, preventing
a future or refused activity from blocking current clearance. Full result storage
applies backpressure to at most8completed searches; compute slots, worker count,
128result/131072surface/16384region bounds are unchanged. Deferred request addresses
have a separate65535metadata bound and retain FIFO/service demand instead of
evicting live requests at the cache's128entry bound. A normalized derived demand
key preserves exact world/query/profile scope and avoids quadratic release scans.
No durable identity, movement duration or resource permission is inferred.

Idle attended machines now park on their exact station/container wake rather
than emit retries while no operation exists. The station owner publishes changed
machine wake identities even when the initiating event names a job. Its retained
proof covers bootstrap, exact machine, container declaration and surface. Automatic
machine policy and running clocks retain their existing execution semantics.
Focused station operation/recovery checks passed46seconds. Route-buffer overflow,
late consumer, worker capacity, real geometry invalidation, owner refusal/future
previews and actual adapter restart/provisioning checks passed18seconds in
retained-route-results-connected2.log. The adapter fixture's obsolete drain-all-
world helper now yields on actual completion/backpressure so canonical consumers
can run; terminal outbound/supplies and6000canonical-tick assertions are unchanged.
These are correctness checks, not deployed100town throughput acceptance.


A subsequent ordinary100town sample isolated a second route-delivery bottleneck:
2100 computed continuations remained deferred while receipt capacity sat exactly
at its reserved quarter (1024/4096), leaving zero optional notification allowance.
The former adapter represented even an already-due external-readiness signal as
a durable deadline-rescheduling command. The kernel now accepts an exact
already-due derived readiness signal, invalidates only that admission proof and
rechecks the owning process under normal budgets. It changes no canonical state,
deadline, membership, revision, receipt or WAL fact. Future deadline acceleration
still requires the existing bound durable command, and its capacity/predecessor
wait cannot block ready already-due actions. Unpublished canonical predecessors
are preserved. Live retry demand and detached recovery previews now carry explicit
producer-supplied origins; future live retries retain calculated results rather
than losing them before their next deadline. Owner refusal still yields capacity.
Focused actual adapter full-receipt and graceful recovery, kernel exact/future/
stale/off-thread/owner-refusal, and planner consumer checks pass23seconds in
addressed-route-readiness-focused2.log. The preceding e950 full scoped check/build/
packaged and55scene+2production native gate passed7m44 in retained-route-results-
release.log. Updated native100town measurement and G acceptance remain open.


The next connected source audit found false readiness from compatible geometry
refresh: synchronizeScope emitted INVALIDATED for pending/queued calculations,
and INVALIDATED+RESULT_AVAILABLE for a completed route, merely because the view
object changed. With100towns a32entry clear-all journey cache continually rebuilt
those views. This created domain retries without new route evidence. The common
wake index now keys calculation subscriptions by the complete normalized world/
query/profile/bounds/endpoints scope, preserving exact canonical continuation
ownership separately. Compatible refresh readdresses computation without waking
its consumer; true dependency loss still invalidates, and actual completion reaches
the original subscriber. Repeated immutable aliases within advice are one demand,
and only the last consumer releases it once. Journey views use the existing
bounded local-view capacity and LRU eviction of one entry rather than clearing all.
Existing terrain damage, world/profile/bounds invalidation and worker cases plus
an unchanged200view-refresh/original-consumer terminal regression and actual
adapter restart pass27seconds in compatible-route-refresh-focused2.log. Three old
assertions requiring spurious refresh notifications were replaced with no-wake
assertions; changed-terrain NO_PATH and final result delivery remain required.
No domain outcome, physical timing or cache-validity requirement was relaxed.
G remains open pending current-code native throughput and lifecycle evidence.


Current100town liveness is still NOT accepted: a9 advanced38622->48004 in575s,
with intermittentcatchup but netdebt454->2579; snapshot44465 still had2937hungry,
2935MOVE/noTravel meals and399idle stations. An actual resident had service access,
108usable bread and an overdue calculation-held meal. The bounded continuation
probe and read-only saved-state calculation trace then isolated compound meal
routing: residents93-19/93-2 required two separate cached queries to enter the
same service point; resident1-1 required four alternative queries before a legal
waiting route. A successful prefix could be evicted while awaiting the next query.
The meal owner now declares one semantic interaction/waiting goal to the common
navigator, granting the actual departure and selected destination permissions in
one geometry view. Waiting approaches exclude the service boundary while outside;
private departure retains its existing escape permission and no-reentry validation.
The retired exitToPublic prefix builder had no other production callers and was
removed; its contact/permission/pending tests now exercise the current shared
knowledge APIs. Meal, custody loss, approach/replay, HOT service and actual adapter
restart checks pass24s(single-goal-meal-approach-focused2.log); final retirement
checks pass20s(single-goal-departure-retirement.log). This does not award arrival or change service admission/timing.
The original-prefix data is compound-meal-route-demands.txt; no native outcome is
inferred from those direct read-only probes. G requires updated native results.


Actuald9ordinary100town continuation now reached CONSUME/CLEAR_ACCESS with238
in-flight meals, but debt still grew; do not closeG. The next source-proven
publication race: process planning registers its computed successor before the
kernel's asynchronous WAL publication. Ready-index cleanup previously removed
that exact successor because only its predecessor was still canonical. Producer
registration now explicitly stages both exact generations. A result stays retained
and cannot be exposed as runnable while the predecessor is canonical; after
publication it can wake the successor. Neither generation retained means actual
cancellation/supersession and releases it. Pending disk is never interpreted as
stale work. Focused original-consumer/delayed-publication/stale-generation and
actual adapter recovery+guardrails pass21s(continuation-publication-focused.log).
The graphical terrain visit produced5inspected landmark frames; the field check
was an invalid fixed-crop assertion, then an airborne visit unsupported by the
ordinary player's gravity. The checked scenario now targets the actual board and
uses a grounded east-side camera. Earlier failed receipts are retained explicitly;
no full graphical or station-terminal acceptance is claimed from them.


The next connected scheduler audit replaces revision-based unaddressed polling
with exact immutable owner inputs plus canonical time. A queue-only commit cannot
change held(state,action), so it must not restart an entire captured polling round.
The kernel regression drains10queue-only commits while13unchanged refusals are
checked once, then changes actual owner state and requires every waiter to resume.
Actual addressedsignals remain independent. NativeJFR also measured whole-demand
hasCalculation/discard scans and rebuilding retention snapshots. The planner now
indexes exact normalized calculation addresses; the continuation owner reuses its
immutable retention request/key snapshot until declared membership/hold/readiness
changes or a future recovery preview becomes due. Boundaries/cancellation/profile/
terrain/recovery checks pass21s(derived-scheduling-indexes-focused.log); no route,
worker or result capacity was raised. Added read-only counters identify which
ordinary deterministic budget is exhausted, instead of guessing from an idle tick.

The corrected9116full-pack terrain scenario completed27actions/6frames. Landmark
geometry is present; final field/board is visible but dark rainy framing is not
readableM3 or a terminalworkstory. Clients/private displays exited. Native100town
snapshot67152 had1810hungry (previous~3000) and72movements; actual meals progressed,
but9116ten-minute ordinary debt reached10013. G remains open; do not hide this behind
component greens. A WALsample2335records/71ticks had126command receipts (~5percent),
so optional planning commands alone were NOT established as the dominant cost.
No speculative deadline-model replacement is authorized by that sample.


Follow-up native discrimination on3b924f5c8 did not establish the Minecraft task
queue as the main wait:5657deliveries, latest3.7microseconds, maximum96milliseconds
in the retained sample. The100town ordinary debt still grew. CPU profiles are
ready-delivery-native.jfr and ready-delivery-hash-cost.jfr; inspect with full stack
depth (jfr print defaults to only5frames). The earlier shallow record-hash lead was
not established as a dominant defect. Deep stacks attribute146/285server samples
to physical executor passes, including62container-effect-fence samples: fuel,
recipe and field equipment repeatedly scan all owners for each queried container.
Those owners now derive pending-container sets from their complete immutable
inputs; station work uses the same owner-local view and the existing station
lookup. Immutable facility socket specifications share a bounded identity-based
result view. No fence or recovery check is removed; changed/predecessor/recovered
branches must retain their distinct answers.

A newer retained WAL interval had1951records over23canonical ticks,1483schedule
reschedules and518executor commands. Source tracing confirms calculation-only
continuations emit a future retry even though no business transition occurred;
ready delivery then spends another durable command to accelerate that deadline.
The shared continuation publisher now retains the exact due generation only for
one same-owner schedule replacement with actual unresolved calculation demands.
The kernel parks that action on addressed readiness; completion rechecks it through
the existing due-action path. Mixed business transitions and genuine timers retain
their own deadlines; legacy retained future generations still use the exact-bound
wake command. This repairs that observed repeated-boundary path, not a claim that
it alone explains all100town debt. G stays open until ordinary native liveness and
remaining terminal/HOT/capacity stories are demonstrated.


The first cd26052ba private100town run is NOT accepted:72203->73174 in575s,
debt378->10912. Early samples overlap the deliberately bounded baseline native
run and are not a clean speed comparison. A later WAL slice showed582records over
only2canonical ticks, dominated by exact past-due self replacements. Retaining the
due generation exposed another connected kernel contradiction: those no-change
results still created WAL records and ran downstream reconsideration. The kernel
now keeps a single exact self replacement as a bounded derived hold, charging the
same preparation/action/weight allowance, preserving canonical membership, and
letting independent work proceed. Actual business events, changed deadlines,
foreign subjects and cancellation still use the normal commit path. A historical
WAL-order test now emits an actual business Delta along with its continuation;
no-work retries no longer serve as timestamp evidence. The new kernel case
requires independent completion, no duplicate WAL while held, and exact addressed
resumption of the retained action.

A separate source-proven readiness error re-signalled an already retained
UNKNOWN_GEOMETRY-with-missing-facts result from Prepared.acquire. It now remains
awaitable until actual search/geometry change. The real cooperative planner test
requires20stable acquisitions without notifications and a wake on physical
knowledge ingress. Native geometryWaits was empty in the inspected cd sample;
this corner is NOT established as the observed main cause.

Supplementary broad native scene check on cd:131/135passed; failures:execution
plan test still requires retired aggregate id "scenes" although each behavior now
has its own scene-* stage; ambient first-body fixture; occupied patrol admission;
inactive hive assault. Exact previous3b baseline reproduced the diagnostic,
ambient and hive failures (132/135). Candidate-only patrol admission remains to
be discriminated by the existing focused route-patrol slice. These supplementary
failures remain recorded, not an unfiltered green claim. Economy55/55passed and
production-effect5/5passed on cd. New kernel/wait changes require their affected
selected native gate before the next deployment. Main stillfec4, privatecd; Gopen.


B42 selected pure/adapter/guardrails+packaged/economy55+route-patrol4gate passes54s;
the earlier isolated patrol admission failure did not reproduce in its focused
native slice. Ordinary private100town still accumulatesdebt, so G remains open.
Actual checked snapshot74814 contains137hungry,126MOVEmeals and89movements versus
previous~3000hungry: feeding makes terminal progress, but clock throughput is low.
It also shows396/400machinesidle and296overdue station reviews. The automatic
machine owner previously parked only attended empty operations; input-empty
automatic machines still committed a retry every20ticks. They now park on their
exact input custody row (empty/absent only), retaining catalog/machine/container/
surface dependencies. A real changed input row wakes its station independently of
the event's courier subject. No demand/price/fuel-duration decision is inferred
from an empty input, and occupied automatic inputs retain ordinary policy review.

The connected input-wake path exposed another repeated full scan: the resource
read index mixed custody-section lookup with available-balance rows. Alternating
pre/post ledgers recreated all container entries even when only lots/claims
changed. Section lookup now has its own four-entry immutable result view keyed
only by the exact accounts map; local balance computation retains its existing
full dependency set. Branch/recovery/stock tests retain correctness; this is not
another ledger or slot authority. The no-change post-plan owner assessment now
spends the existing classification allowance; exhausted allowance defers the
original action instead of performing an uncharged evaluation. Added read-only
readyDelivery.totalDelayNanos and ordinaryYields distinguish callback delay and
runtime gates; latest-delay samples alone were not sufficient attribution.

The830 native100town stand still accumulates ordinary debt:78221->80230 in575s,
462->9958 debt. Feeding reaches zero hungry in the checked79009 snapshot, but
G remains open. A retained forestry SELECT_TREE owner repeats two local route
legs with mismatched geometry scopes: actual actor profile on the approach,
unprofiled storage return. Shared storage/equipment admission APIs now require
an explicit navigation profile and validate receipts with that same envelope.
Assigned actors supply their retained profile; preassignment bakery opportunity
queries explicitly declare VILLAGER. Mining and production admission approaches
also bind the actual actor. A generic bounded reverse-local-proof reuse path
checks every current support, clearance and directed edge, rejects changed/unknown
blocks and foreign body/world scope, and acquires no movement or access authority.
This avoids calculating a compatible local round trip twice; unsafe candidates
fall back to the existing search. Focused common-navigation, storage, forestry
and mining tests pass46s (profiled-route-focused.log). Native effect unmeasured;
not a debt-closure claim.

E377 exact release gate passes1minute, including affected navigation/storage/work
and adapter tests, architecture, packaged artifact, economy55 and production-work2.
Both retained worlds deployed/verified SHA512dd2995fabe132b2160e94389edaa2cb92af0cc982a4e4d478cabb868e55e312ca0ff9c817915141edbf47eb27f9f085591c7b3edbb3c8e22e73a72f81d850c92.
Actual forestry2 moves from SELECT_TREE/revision18 to the next operation/revision19;
a second worker is admitted.100town ordinary sample84049->85782 in333s still
accumulates412->5347debt. This is real owner progress, not G closure.

A bounded20s exact-native JFR (profiled-storage-route-native.jfr, full-stack JSON)
identifies97/335preparation samples constructing route views and89/355server
samples in complete-state validation. Source establishes that ForestryKnownGeometry
used thread-local snapshot identity for immutable natural facts. Switching from
preparation to publication therefore changed Composite identity and cleared all
navigation views despite identical forest regions. That provider now shares four
immutable snapshots over the exact bootstrap/region map; its bounded spatial memo
is already thread-safe. The focused cross-thread predecessor/successor case
requires identity reuse and genuine-change separation. No terrain/route authority
is weakened. SettlementPolicyValidation also skips its row traversal when all
six actual immutable inputs are unchanged; full audits still bypass the cache.
StationWorkValidation uses the existing duplicate-rejecting installation directory
instead of scanning every container per job. Focused changed/negative/recovery
validation, forest, population, nutrition and station tests plus guardrails pass34s
(shared-forest-and-invariant-focused.log). Native speed effect still unmeasured.

354 exact release/packaged/economy55 gate passes39s; both retained worlds verified
SHA51268c5273f0868f13c3f493a6e1e5ca48250139fc8183e36d91c1de7b8ebddef5b5632ec13ab4042c6ccb5926c23f9079e9549f3fb0db54516ac6f1e57e6ae3ad9.
Native observer86844->87960/333s,debt710->6257 stillFAILS throughput. The new bounded
20sJFR(shared-forest-facts-native.jfr/full-stack CPUJSON) changes the attribution:
route-view construction31/207preparation samples vs97/335before, but main-thread
fairness/admitDeferred now273/516server samples. nextAdmission re-scanned every
deferred request, including empty priority lanes, at every repeated query. It now
indexes waiting heads by declared service and stable queue sequence. Promotion,
suspension, cancellation and immutable geometry readdress preserve the exact
service/order story; active quota/rotation and bounded worker capacity are unchanged.
No domain priority, resource or actor authority enters this derived index.

A connected source-selection error kept trying all trees/stations after an
unresolved first route, producing hundreds of speculative demands for one worker.
Forestry admission/selection now waits on that candidate; only proven NO_PATH
tries the next one. The existing external-tree-loss regression now also checks
one retained route dependency on the real resumed SELECT_TREE path. Existing
real-cooperative worker cases exercise FIFO across geometry refresh, promotion,
lending/reclaim, late cancellation, result retention/backpressure and eventual
local/distant completion. Queue/negative tests+guardrails pass17s; combined
forestry/queue pass29s; augmented external-loss lifecycle passes25s. No timing
assertion or capacity increase. Native effect remains to be measured.

B212 exact selected release/packaged/economy55/route-patrol4 passes58s; both
retained worlds verified SHA5127f6c3d459f89852ee8f601b4da581cb640b9b14256175984555fb66bb8081e537dcb870eea2f17706a4f8895c444e687045f6e91941d9f6e36e75c4aa1679c51.
Ordinary main12town sample has zero debt and recent3–7ms host ticks.100town
88907->90119/333s still accumulates199->5648debt. The indexed-admission JFR removes
the former fairness hotspot; navigation workers now account for773samples while
the server has252. Most navigation cost constructs16×16graphs by repeatedly
reading the full block/provider/body pipeline even over homogeneous known ground.

The shared geometry API now admits an OPTIONAL exact uniformSupport certificate.
It means one known dry plane, no other levels/obstacles/unknown blocks above it,
and legal horizontal edges for the bound body. Default is no certificate.
Sparse regional coverage rejects surveyed-height overrides, authored floors/roads,
site withdrawals/missing knowledge, hard obstacles, scars, observed contextual
shapes, natural relief and forest regions. Each overlay either supplies its
complete affected-region coverage or conservatively withholds the certificate.
An indexed NavigationObstacleView supplies point and area facts from one immutable
set; a bare point predicate supplies no area proof. Body envelopes request an
expanded certified rectangle. Existing refined departures withhold it locally.

Only the existing region builder consumes the capability. It creates the same
flat directed graph from the canonical stencil, using the same resumable work
budget, cache, dependencies, fallback search and ownership. There is no second
navigator, forced chunk observation, assumed unknown air, increased budget or new
durable schema. Exact topology/stable edge order matches the ordinary builder.
The real provider withdraws the certificate for an unclassified player edit,
including after codec recovery; unknown stays UNKNOWN_GEOMETRY. Focused region,
negative/recovery, body-profile, forest and guardrails checks pass19s. Runtime
speed and full long-route completion are still unmeasured; G remains open.

D5 selected release/packaged/economy55/route-patrol4 gate passes61s and both retained
worlds are verified SHA5125a818105ee45bf590881db43c855b861e26d6aec6e4010977c7ebcf2b52e1523367fbf232da89cd07a2a9543554f56b0a98bd2a47f37b356cbad0518b8b4a8a5.
Native100town ordinary debt still grows; no G closure. Static review identifies a
remaining scale contradiction: even a flat long itinerary expands/builds several
16×16neighbours per traversed tile, so the4096region safety bound can refuse a
valid tens-of-thousands-block journey. Merely raising it would retain millions
of extra detailed surfaces for each concurrent search.

The SAME optional positive plane certificate now also supplies16region shortcuts
inside HierarchicalPedestrianSearch. Each shortcut records every crossed regional
token, has its actual regional distance, and expands back into ordinary adjacent
portals during bounded rewind. Movement still refines only its normal near prefix;
quotes count the ordinary connected edges. The original full search remains as
fallback, including all ordinary edges. No shortcut crosses an uncertified area,
changes arrival/time semantics, raises worker/graph/result capacity or adds another
navigation owner. Coverage probes are bounded to320block side/128regions, including
body/context halos; they inspect sparse producer metadata, not each remote block.
A40kjourney now reaches ESTIMATED and an adjacent movement prefix within the
existing graph bounds. A complete wall stays NO_PATH. An edit inside an unexpanded
shortcut invalidates its dependency proof; a remote irrelevant edit preserves it.
Focused changed-region/body/worker/negative tests and guardrails pass21s; extended
proof case passes11s. Native throughput effect remains to be measured.


996 selected release/packaged/economy55/route-patrol4 gate passes61s; both retained
worlds verified SHA512db2325f8fdb80d664f39ed1d5ab7d84a1de1f9a8778a61de30c55e8c0ad292ec0e12277daa86b8824ab0b46a7f5ccfca1757495fe3553a80ae88b910b8e0230a.
Native100town95512 has only LOCAL oldest requests (35planningturns), rather than
distant850+turn searches. Distant service improved, but ordinary100debt still
grows881->6590 while clock95231->96182 over333s. G remains OPEN.

The next source-proven contradiction was anonymous geometry epochs minted by
read order. Detached preparation can read an immutable predecessor after the
owner reads its successor: A->B->A produced a third terrain revision even without
another physical edit. A completed-snapshot cache alone did not fix reconstruction.
The ground version owner now derives exact immutable local fact tokens, including
projected contextual collision halos and explicitly provided dynamic natural facts.
It incrementally updates changed regional partitions and shares four completed
images; eviction or predecessor reads cannot change their semantic identity.
Pristine terrain is fenced by bootstrap identity; actual unknown/known, support,
obstacle, player scar, forest and shape changes remain part of the proof. The
query-specific overlay owner remains separate and removes obsolete empty tokens.
The old anonymous refresh/shape/natural mutation APIs are retired; existing negative
shape-boundary and resolution tests exercise the new owner. A real provider test
covers A->B->A and twelve distinct successors exceeding cache capacity, plus
UNKNOWN_GEOMETRY after codec recovery. Focused checks pass17s. Native aggregate
throughput effect is not yet measured; this is not100town acceptance.


E2c77fa70 exact selected release/packaged/economy55/route-patrol4 gate passes65s;
both retained worlds verified SHA51227946e1c5353f70704c89754354f4f401d1a5d9a79015e09631ab8eec29a2c4918bf1c762a842f05bc37bfedd2d4cb93066ee3aad2a64c93d4b3ba5ff81c3529.
Native20sJFR stable-ground-versions-native.jfr exposes a regression in this change:
using the bootstrap itself as the pristine regional token makes scoped RegionFacts
hash the complete world (94/350server-thread samples). The existing identity-fenced
world scope already supplies isolation. Pristine regional facts now use one compact
enum token; no global identity registry or mutable world epoch is introduced.
The existing regression also exercises foreign pristine-world rejection and return
after twelve other worlds evict the completed-image cache. Ordinary100debt is still
OPEN; do not interpret source correctness as throughput acceptance.


B7453d0c9 compact pristine facts: focused42s and exact selected release58s PASS;
both retained worlds verified SHA5128ed418edfc3c17e75526ed4f434aec97a9c0a5a551e2ead36675ab0c853f75229601007eb69fe919a8b155d62a23577a470735dda823850433e38543f361c683.
Compact-pristine-region-native.jfr has0whole-bootstrap-hash samples vs94/350server
samples before;180server-thread samples remain, including22in queue snapshots.
Ordinary100clock still accumulates debt; no throughput acceptance.

The kernel's pre-WAL barrier unconditionally materialized both complete schedule
images even when no validator read them. It now supplies borrowed synchronous
lazy readers plus the exact retained-action delta through one transaction API.
Both ordinary publication and WAL replay use it. Subject retirement still requests
the full post-queue; exact physical retirement still reads pre/post membership.
Ingress/recovery audits remain complete. No queue mutation, durable state, action
ordering or retirement permission changes. Existing kernel/replay and reference
negative tests retain rejected retirement and valid atomic cancellation; the
ordinary terrain-change case rejects any unnecessary complete-queue read.
Affected checks pass25s; strengthened real changed-state case passes14s.


6242 exact selected release/packaged/economy55/route-patrol4 gate passes65s; both
retained worlds verified SHA512f749311e2e02d5d7b3790ccf4b63560ffc5e6d7e57eafc75d59ba99fc0e6a8b4b01af88d2078197613f2c1a52bc2c89d8cae0d0f4fc28ca11ac1b145ffdfff0e.
Ordinary100 observer completes12samples:102534->105934/333s, debt1044->4305.
Approx10canonicalticks/s stillFAILS. Different retained lifecycle intervals are not
an isolated speed ratio. Fullqueuecopies are source-eliminated, not goal closure.

The wait profile provides a stronger next discriminator: in20s of b745 runtime,
navworkers parked18.7s each, exact-preparation13.3s, owner9.5s, withhundreds of LOCAL
queries queued. ActualModernFix5.27.2 bytecode keeps normal LockSupport wake semantics;
no engine/mixin patch is justified. The common async planner did not notify its
owner when a worker slice completed, and charged the entire large minimum slice
reservation even when a tiny local route used only a fraction. Unlike synchronous
search, it never returned unused work. Thus new work/result publication waited for
another native50ms poll while bounded workers were idle.

One PedestrianPlanningAllowance now retains the original host work quanta. Each
in-flight slice reserves credit; owner publication settles actual work and refunds
only that original still-open turn. A native turn closes the old allowance even
when persistence yields. Late/cancelled generations keep their executor slots until
return and never fund the next turn. Worker/new-demand notifications queue the
existing owner resumption; a coalesced pending flag avoids scanning navigation on
every unrelated calculation callback. Navigation shares the original remaining
owner-CPU budget across resumptions. Eight in-flight slots, worker count, work
quanta, fairness, result/graph limits and canonical action allowances stay bounded.
Fast-forward/holds permit this calculation-only resumption without ordinary clock
or physical replay. Unknown geometry, current-dependency checks and single-owner
publication remain unchanged. The existing worker suite now covers24short queries
within one original allowance, no worker publication, foreign-turn refund denial,
no double credit and cancellation/capacity. Focused worker/queue/invalidation/adapter/
ready-turn plus guardrails PASS16s. Native throughput effect is still unmeasured.


5c898d832 exact selected release/packaged/economy55/route-patrol4 gate PASS57s;
both retained worlds verified SHA512672f902e0e2e150f65c16b12ba888576c1cd9d252d9b22e29468830620158bc8b0429ba8a604a96e80097d2a8fe085d04fe5c07deb9cc2747053bfe89fed9dd1.
Main12town remains ACTIVE/zero debt/recent3ms host mean/max~10ms at418296.
100town regresses:108939->108958/333s,debt533->7175 despite>100kcompletedpreparations.
Navigation now completes far more queries but repeated discovery consumes them
without productive admission. This is a retained contradiction, not acceptance.
Navigation-credit-native.jfr attributes313/394exact-preparation samples to harvest
planning (275admissions), rather than generic route search. The guessed missing
wake/handoff is NOTestablished: Prepared.acquire explicitly re-signals already-ready
results and the wake index retains runnable consumers until actual replacement.

A connected concrete source defect remains: Optional.empty from work discovery
conflates genuine refusal with a pending route. Harvest admission continues over
all other residents; collection-based production does the same, and equipment
selection tries more containers after an unresolved query. This can create a
self-sustaining set of speculative alternatives under the bounded result cache.
Shared ResidentWorkSelection now exposes Discovery(offer,planning), captured from
explicit unresolved query evidence without executing another route. Batch scans
stop at a pending candidate; proved unavailability can try the next worker.
Farmer/miner/forester loops use the same result; bakery/forge/stock use the shared
batch scan. Equipment selection waits on its first pending endpoint. No worker,
resource or cell is reserved by waiting, and a later current-state review still
validates all real admission facts. Existing labour regression distinguishes pending
from NO_PATH, alongside actual harvest/forestry/extraction replay/bakery cases.
Focused changed families+guardrails PASS38s; exact final ref/native effect pending.


2fd66c9d4 exactselectedrelease/packaged/economy55/route-patrol4 PASS64s; notdeployed
separately. Completing the connected harvest path before the next native comparison:
new jobs explicitly start ACQUIRING, whose semantic first goal is the tool endpoint.
Admission nevertheless cloned the strategic/site state and recalculated that same
trip under a field-scoped geometry; even earlier equipment waits cloned task ACTIVE.
The selected tool already carries its accepted shared-storage route. Admission now
reuses it, activating the projected task only after a real offer. Equipment/depot
navigation uses the same generic storage knowledge as other jobs; only WORK_CELL
uses the typed field overlay. Actual field travel is planned by the retained job
from its post-tool position. Current-job ownership, route endpoint/body/profile
checks, service permission, reservation and reducer validation stay intact.
The pending-farmer existence inquiry also uses existing residentsIn(home), not a
whole-world resident scan. No new lookup owner or canonical schema. Changed field,
equipment, labour and navigation negative coverage +guardrails PASS28s.


54d0f0bcd exact release PASS66s and verified same-world deployment on both hosts.
Native100 sample109061->110237 over333s, clock debt509->5996: stillFAIL.
Latest20s field-immediate-goal-native.jfr attributes833server samples versus75
exact-preparation samples;526visit transaction validation,429full owned-state
validation.133MapN/77SetN samples expose clustered immutable compact-table probes;
field UAE lookups also scan all sites repeatedly. The next correction uses the
existing hashed immutable map representation for inventory/economic/field registers,
plus immutable hashed actor membership and a bounded exact FIELD_HARVEST job
directory. Producer uniqueness and declared kind checks remain mandatory.
Common physical-intent existence checks retain successful proofs only across their
complete immutable read set; the field owner projects nonterminal identities and
exact conflict lifecycles. Confirmation checks retain both intent and observation
images. Full bootstrap/recovery audits bypass proof reuse. No schema, authority,
scheduling allowance, durability or cache-size increase. Existing focused
resource-site, inventory, fungible-production, actor-identity and incremental
negative/recovery checks and guardrails PASS; runtime effect not yet measured.


c21c747f3 verified/deployed both retained worlds (SHA51294615f70bc7c2753ce7d36a6c4785f4941f97e914c3bc0a88424a3058abdceb94e12d3441e17831c3dd0cdc0bdfa1081b1be2c39fb7e0ff29a7456e9f181ff80).
The next20sJFR shows183server samples versus833; validation27 versus429.
This is sampled attribution, not an isolated speed ratio. Ordinary100debt still
grows.52server samples now collect/filter field-scene candidates before asking
whether any observer exists. All six common scene-admission callers now supply
lazy read-only candidate inventories to the shared demand gate. No ordinary
observer means no candidate calculation; retained scenes still execute/drain
through the unchanged scheduler. Existing physical-demand native test now rejects
any observer-free supplier evaluation and checks a later observed candidate.
