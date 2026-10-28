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
through the unchanged scheduler. Existing physical-demand native test checks a later observed candidate through
the lazy boundary; the no-nearby-player physical-effect negative remains. An added
assertion that the entire shared test level had no players was invalid: the parallel
test creates a mock observer. Removed that assertion, not production demand checks.


Native main c21 stopped22:56:43 after a navigation slice spent11777/11776; all
dimensions saved. A certified shortcut may select a region not yet present in the
search's local map. Fetching its shared cached graph can spend the last unit;
expandStep then spent another unit without rechecking the grant. Keep that exact
selected candidate for the next slice when graph lookup exhausts the budget.
Private old-binary probe with a warm regional cache reproduces12182/12181; retained
in shortcut-slice-probe-before.txt. Extended existing 40k shortcut case proves the
shared-cache boundary and complete ESTIMATED result; worker/refund/full-regional
negative cases and guardrails PASS17s. No limit increase or exception suppression.
Main retained-world recovery and native scale effect remain pending.


b20ead035 exact gate PASS50s, packaged/economy55/route-patrol4. Both retained
worlds verified deployed (SHA5129c662c6cfd9213bc54835a94ed714e4f701084288a75edee1537ba25008bc062b2a23d941955e39d139d12500f6fa7124bb92940bd6047d5b4e876d83a01d3e1).
Main recovered normally, current465204/debt1/recent3msmean7.2msmax. 100ordinary
clock debt still grows; do not close G. Warm profile keeps publication/validation
and exact preparation as material costs; lazy scene demand removes COLD scene
candidate computation. Need distinguish required physical durability from finite
batch flushes before changing any barrier: add read-only append-durability counts,
bounded requiring-event counts and explicit PERSISTENCE_WAIT yield telemetry.
No publication, force, retention, scheduling or recovery semantics changed.
Existing store prefix/replay and runtime/performance checks+guardrails PASS36s.

### Public market integration and next measured limiter (2026-10-27)

`eccdda977` implements the accepted public commodity book under schema311. Clean
release gate passed58s, including selected adapter/store/runtime checks, packaged
JAR, economy55 and route-patrol4. Mainr279/private311 same-seed profiles deployed
and verified; former310 worlds retained. Full-pack terrain check passed27actions;
six exact frames inspected, client/private display exited. This is terrain access,
not terminal production/trade acceptance.

Native100 ordinary interval1→2269 in333s, debt362→4755: phaseG remains failed.
Snapshotat1805 has321orders for100parties, one actual intention per endpoint,
instead of2048 peer copies. Zero retained contracts does not establish no history.
20s public-market-warm.jfr attributes major advice/preparation cost to procurement
provision valuation collecting production capacity; forestry repeatedly resolved
whole regional tree descriptors to obtain quantity. Compact explicit-species
headers and a common forest-source quantity view now reuse bootstrap+forest-region
facts across unrelated market/inventory drafts. Sparse losses/generation checks,
predecessor reads and actual extraction authority remain unchanged. Existing
forestry withdrawal/recovery and procurement/pricing checks plus guardrails pass37s.
Native effect remains to be measured in the retained311 world, without another reset.

### Scoped owner reads after the public-book integration (2026-10-28)

Retained311 `6f810a6e1` ordinary100 interval3431→4436/333s, debt986→6643
stillFAIL; a restart creates a new volatile host-debt epoch and proves no catch-up.
Post-change20s JFR: advice951samples, server673, preparation101, navigation1.
Forest enumeration no longer dominates; remaining valuation work includes field
queries collecting actual tools even when the explicit unbacked branch discards
them. Cargo/crew reads also repeatedly scanned every field to resolve one exact job.

The connected read-path correction reuses ResourceSiteState's exact typed job
directory, separates cargo identity validation from unused interruption-status
computation, filters field ownership from its nominal declaration before building
geometry, and skips ignored tool collection only for unbacked valuation. Real
execution/equipment checks remain unchanged. Bootstrap settlement/structure/home
lookups share one bounded immutable declaration index. Harvest service demand
selects the nominal destination before access-geometry compilation, and generic
storage slot selection indexes already validated/backed claims by container.
Missing/foreign identities and changed claim backing retain their strict checks.
Focused owner/cargo/equipment/sourcing/validation checks+guardrails PASS34s;
changed service/storage/equipment/procurement composition+guardrails PASS42s.
Clean-ref release/deployment and native throughput measurement remain required;
this is not phase-G closure or a measured speed ratio.

The same post-change profile shows a saturated single advice worker (951samples)
while navigation is idle (1sample). Independent immutable forecasts now have two
bounded compute workers; exact-predecessor preparation remains single-lane and all
revalidation/publication remains on the owner. Queue capacity and the reserved
checkpoint slot are unchanged. The existing preparation test proves two independent
forecasts enter while one is blocked, exact preparation remains independent, stale
results reject, and only the owner authorizes/publishes continuations. Focused
preparation/runtime checks PASS37s. This does not increase canonical work allowances
or claim native throughput success; deploy/measure the connected correction together.

### Select quotes before exact reservation (2026-10-28)

Both retained311 worlds verified18bb21759. The100 profile after recovery advances
4620→4637 over~6minutes while host debt805→7449: FAIL, not throughput acceptance.
20s independent-forecasts-recovery.jfr has711preparation samples versus78server;
ExactInventory/GoodsParticipantState construction dominates the preparation lane.
Both navigation workers are also busy (543/519samples), so navigation remains open.
The active procurement path constructs full reserved world drafts for every
supplier/endpoint/mode and only afterwards ranks them. The public book removed
quote duplication but exposes this expensive alternative fan-out.

Procurement now separates non-binding quoted bundle envelopes from exact
candidates. Compact per-order remainders prevent duplicate quantities in an
estimate; they grant no stock, cash, capacity, crew or departure authority. Rank
these estimates first, then construct exact reservations only for a selected
proposal. Reduced/rejected or repriced candidates return to the same ranking;
full current stock/funding/food protection and logistics admission remain mandatory.
Existing per-supplier/mode review bounds and smaller-load fallback remain.
The entire disposable calculation, including selected logistics, stays on the
preparation worker under the common read-only route-demand capture. Only accepted
owner completion acquires demands and publishes events. No worker acquires native
navigation, no canonical publication is moved off-thread, no persistence schema
change, no world reset. Native impact and phase-G closure remain unproved.

### Separate agricultural progress from urgent capacity loss (2026-10-28)

`dbdc6203c` clean release PASS68s (selected pure/adapters, packagedJAR,economy55),
verified in both retained311 worlds. Native100 interval4641→5478/333s,
debt108→5934 stillFAIL. New20s profile removes exact-world construction from the
preparation leaders; navigation and production forecasts remain expensive.

Read-only replay of checksummed snapshot5991/WAL3451transactions through6186
identifies269economic publications. Exact owner-port attribution gives165urgent
CAPACITY invalidations from routine soil preparation (87GRASS→FARMLAND,
44COARSE_DIRT→DIRT,34DIRT→FARMLAND), plus103cultivation capacity changes including
101layout extensions and102sowing changes. Ordinary crop growth/harvest is not
being guessed as their cause; the captured transitions establish this feedback.

Agriculture now owns its separate economic input port. Forward preparation along
the declared soil catalog is work already included in the accepted renewable
forecast; its completion does not revoke that plan. Explicit append-only land
admission and additional sowing become committed forecast inputs reviewed at the
ordinary deadline. Their exact retained predecessor must prove this positive
extension; arbitrary shape/contact changes, removed land/sowing, soil degradation,
obstructions and external crop loss keep urgent capacity invalidation. Current
execution continues to validate actual land, tools, custody and authority.
No new dirty-reason wire value, no state migration, no notification-only actor
or geometry change is added. Planned progress and real loss are exercised through
the existing publication/cultivation composition, not a timing benchmark.

### Recovery previews retain canonical deadlines (2026-10-28)

Field-progress clean release PASS53s with economy55 and affected publication,
cultivation and native-runtime checks; verifiedfc5f4efd9 in retained311 worlds.
100ordinary interval7200→8332/333s,debt114→5645 remainsFAIL. Its20s profile has
server697,nav590/580,preparation246,advice119/113samples; remaining full-state/
geometry work is visible. No isolated speed multiplier is inferred across phases.

Recovery has a separate concrete cost: preparePlanningWaits previewed EVERY future
navigation-capable timer, and ScheduledPlanningRecovery raised execution time to
max(now,due). Thus future economic reviews were fully solved before ordinary
canonical time could resume. Both retained100 starts spent about45s on this.
Recovery now considers only actions due at the captured canonical instant; future
commands retain ordinary deadline admission, with no invented future route demand.
Due/overdue dependency reconstruction and single-owner acquisition/publication
remain intact. The helper moved into the existing recovery capability owner to
keep the catalog within its1000-line guardrail; no second recovery mechanism.

Existing recovery cases cover extraction, meals, clearance, fields, station work,
migration, services, engineering and patrol. Five cases now explicitly check that
future work is not restored early, then restore at its real deadline and retain
positive continuation. Engineering's due action becomes directly runnable rather
than needing a future-deadline wake; the body/approach terminal assertions remain.
First iteration failed the catalog size guard, corrected by moving the helper;
next run exposed four old future-preview expectations. Final38-case run had only
the engineering ready-vs-runnable assertion; its focused correction+guardrails
PASS11s. Exact clean release must still verify the combined retained selection.

### Immutable declaration extents replace repeated mining bounds scans (2026-10-28)

`feaa45421` exactreleasePASS54s, combined38recovery cases and selected native
runtime/adapters, packagedJAR/economy55; retained311 main/private verified. Native
100 ordinary debt still grows; no phase-G closure. Its observer starts late after
startup and cannot by itself prove exact recovery duration.

The field-progress profile attributes93of697server samples to
ExtractionLandValidation.require under normal transaction publication. That
helper's bounded global identity cache misses cause a traversal of source cells,
fixed blocks and substrate; all these inputs are immutable layout declarations.
ExtractionLayout now derives one exact HorizontalBlockExtent from its existing
complete worksite-position index at construction. The shared geometry value checks
horizontal containment with inclusive source extrema/exclusive world maximum.
Land validation becomes constant work without a global proof cache; changed bounds,
new layouts and foreign fixed/substrate coordinates are still checked. Geometry,
collision, land ownership and actor admission are not inferred from the envelope.
The extent is derived, not another persisted authority or schema field.
Existinglandvalidation negative cases extend foreignsubstrate andexclusiveedge
checks; incrementalgeometry/terraces/developmentreplay+guardrailsPASS33s.

### Owner-scoped renewable forecasts and valuation reads (2026-10-28)

`c9a18821d` cleanreleasePASS58s, targeted mining/geometry/recovery plus selected
runtime, packagedJAR/economy55; both retained311 worlds verified. Native100
11761→12721/333s,debt2011→7714 remainsFAIL. Afterchange20s JFR:nav642/620,
preparation487,server465,advice127/109samples. Repeated mining-bounds traversal is
absent from the leading frames; expensive renewable forecasts remain in all
three calculation lanes. These are attribution observations, not isolated ratios.

Every supplier valuation reaches the same pure renewable-source scheduler. A
single global32-input memo competes across100owners, executable/valuation views
and cultivation alternatives; unrelated market states force those reads again.
The field owner now retains bounded memo instances per explicit home and declared
equipment-backed mode. Each retains four complete immutable value input tuples
(sources, workers, day, daily work and horizon); hypothetical executable options
cannot evict another settlement's installed valuation forecast. The generic
scheduler's default API/algorithm remains unchanged; no capacity, output or worker
reservation is cached by an incomplete owner identity. Changed biology/calendar/
worker/horizon inputs retain their own strict comparison and invalid-input checks.

The connected extraction valuation path also computed actual assignments and
owned equipment before discarding both on its unbacked branch. Those reads now
run only for equipment-backed capacity; actual execution/admission remains intact.
Forecast/procurement/publication/cultivation+guardrailsPASS28s, with mining forecast
added in the connected owner-production-valuation-focus.log PASS28s. Final scoped
mode-key simplification still needs its exact clean release check.


### Resumable navigation retains admission age and guaranteed progress (2026-10-28)

`455cc3644` cleanreleasePASS56s, selected forecasts/procurement/cultivation/mining,
runtime/package/economy55; both retained311 worlds verified. Native100 final
interval14274→15232/333s,debt694→6399 remainsFAIL. Latest20s profile: advice933/817,nav776/718,
server449,preparation315; common procurement/crew reads and regional building remain.

At native14987, an oldest22k-block regional estimate waited19983 planning turns
but retained only11776 search units (one slice), alongside46 deferred/8 suspended
requests. Source explains a progress-loss mechanism: preemption appended an old
search behind all unstarted demands, and later bounded suspension eviction could
throw away its graph. Reclamation also chose the next ready request without
protecting the oldest quota of that lane. Repeated short arrivals could therefore
recycle the entire distant working set. A zero worker-restart counter did not
capture eviction followed by construction of another task.

The common fairness owner now retains original arrival order through suspension
and compatible geometry readdressing. Reclaimed slots come from newer borrowed
calculations; each lane's oldest guaranteed quota continues. Existing bounded
suspension/window, executor limits, weighted service and actual geometry dependency
checks remain. No extra workers, memory bound, route authority or schema change.
The existing long-vs-short regression now includes24 long demands: short route
finishes, oldest guaranteed searches remain admitted, and yielded work resumes
ahead of newer unstarted demands. Cancellation, worker order/publication and
regional invalidation checks remain. FocusPASS24s before diagnostic-only additions.
Read-only activeRequests exposes at most8 actual search stages/work/restarts,
separately from oldest capacity waits; existing diagnostic read-only coverage extends.
Exact clean release and retained-world measurement follow; G staysOPEN.


### Site-owned palette reuse and separate planning diagnostic (2026-10-28)

Navigation-resumption exactreleasePASS62s, both retained311 deployments verified
`dfe001307`. Its active-search details made the optional pedestrianPlanning section
exceed the existing8192-byte performance response envelope; the budget correctly
reported omission, but the old observer assumed presence and failed twice. These
are method/diagnostic faults, not proof of missing navigation. The restarted
counter-only observer explicitly retains null for omitted navigation, no zero fill.
Aggregate performance now keeps compact counters; `pale_mirror v3 inspect planning`
uses the existing closed read-only diagnostic vocabulary for bounded detailed
continuations, oldest4 requests and all8 active searches. No limit is raised.

The20s455 profile includes34server samples rebuilding extraction block-type
palettes. Each deposit contributed three immutable parts to one global128-entry
memo; more than42 sites can evict each other's unchanged layouts/cells/observations
on every aggregate refresh. The palette owner now retains four complete immutable
part inputs per explicit site, bounded by the existing256-site register, scoped by
bootstrap. Global terrain/mineral/aggregate parts remain separate. A changed site
still reinterprets its changed data; no spatial observation, body clearance, shape
or route authority is invented from a type declaration. Retained terrain, local/
remote invalidation and geometry-scope negative/recovery checks plus native-runtime/
diagnostic budget checks and guardrails PASS47s. Clean-ref packaging follows.


### Identity-bound participant inputs in sourcing memos (2026-10-28)

Latest recorded allocation deltas (ThreadAllocationStatistics, not sampled weights)
for455's20s trace: navigation19.48GB/17.06GB, advice13.16GB/12.57GB,
server4.60GB, exact preparation3.75GB. Sample weights overestimate several producers
and must not be reported as exact allocation totals. Monitor stalls are sparse;
no evidence that a broad lock is the primary bottleneck. The existing JFR reader
was used directly, without exporting huge allocation-stack JSON.

CPU stacks also show GoodsParticipant.hashCode walking the entire known-party list
inside sourcing/procurement/valuation/options/timing memo lookups. With100parties,
each quote-cache query hashes99 unrelated neighbour records even on a cache hit.
These immutable participant objects now enter the existing ImmutableResultView
identity tuple; only compact scalar quote parameters remain value-hashed. The
outer exact world-state generation and every participant input remain checked;
no owner-id-only proof or omitted stock/funding/crew input. Both positive and
empty quotes retain the existing bounds. Five connected callers migrate together,
without changing prices, selection, departure authority or canonical schema.
Existing procurement/unfunded/mixed-trip/sourcing/immutable-input checks PASS19s
before the final outer sourcing memo used the same representation; exact clean-ref
check must include that last caller. Native100 throughput remains unclosed.


### Compact certified topology and component-directed expansion (2026-10-28)

`fb22d6695` exactreleasePASS58s, both retained311 deployments verified. Ordinary100
native interval17627→17923/151s,debt861→3594 remainsFAIL. Region building dominates
navigation;5ce's20s actual allocation deltas are24.33GB/18.92GB for two navigation
workers,4.24GBserver,5.16GBpreparation,3.09GB/2.48GBadvice. Across evolving retained
world phases this is attribution, not an isolated speed multiplier.

The graph owner now distinguishes a provider-certified clear plane from a layered
graph. A plane retains tile/height and shared adjacency, deriving surfaces/indexes
arithmetically; it does not allocate256 surface/hash records and4096-node builder
arrays. Its refinement still emits adjacent walkable cells cooperatively. The
layered builder grows arrays from256 to the unchanged4096 limit as actual levels
require. Component/edge/unknown limits and complete layered fallback remain.

Regional expansion also scanned every internal node/edge even when its already
built topology proved there was only one mutual component. Those scans cannot
produce an inter-component portal. Single-component expansion/target probing now
skip only that empty scan; borders still validate geometry, and directed links
between distinct components still use exhaustive existing logic. A provider's
initial clear-plane certificate now runs under the per-region worker lock rather
than shared cache lock; unrelated region preparation can proceed concurrently.

Existing certified graph equivalence checks extend adjacent refinement in both
directions/same-cell/foreign-height cases. Layered checks explicitly exercise
regional-estimate one-way descent, alongside bridge/tunnel/body/damaged-step cases.
133navigation/wake/codec cases initially had one over-specified fixture failure:
rebase test banned reads from every old immutable region, including an unfinished
unchanged cache builder. It now forbids the actually changed region and retains
terminalNO_PATH at the new wall. Focused correction16sPASS; no production geometry
validation was weakened. Existing concurrent-region test now barriers in provider
certificate preparation, directly covering the formerly shared-lock seam.
Clean release must combine the complete133selection and this final worker case.


### Reuse immutable collision facts and eliminate contained endpoint sweeps (2026-10-28)

`3f93bbd85` cleanreleasePASS64s, full133navigationselection, native-runtime,
packagedJAR/economy55; both retained311worlds verified. Native100 ordinary interval
18241→18507/151s,debt289→3053 remainsFAIL. Latest20s profile still attributes
most navigation samples to detailed body sweep/region construction. Actual allocation
deltas19.04GB/32.64GB across the two navigation threads do not show a reduction in
this evolving retained workload. The temporary full economic-review snapshot probe
was too broad for a certificate question and stopped (read-only, no native mutation).
No unproved certificate/cache-size hypothesis is treated as the remaining cause.

NavigationBodyGeometry checked each flat transition's horizontal swept box and
both zero-height endpoint swept boxes. The latter are wholly contained in the
horizontal box, including their one-cell query halos. The common collision owner
now omits only those contained zero-height sweeps. Actual ascent/descent, all
horizontal swept volume, fluid/unknown/thin-wall/overhang checks remain exact.
This changes neither allowed body profiles nor known-vs-unknown interpretation.

KnownPedestrianNavigation's explicit immutable regional geometry now retains a
bounded8192-cell memo inside each captured collision view. Region/body/pose checks
reuse both known and missing facts from that exact view; a successor receives its
own cache. Conservative nonregional producers retain direct reads. The existing
shape integration case now exercises explicit revisions and proves that new unknown
geometry cannot reuse cached old clearance or poison a captured predecessor.
Existing navigation/body/layered/withdrawal/recovery selection PASS24s.

The immediate diagnostic consumer receives constant-size regional cache counters
(lookups, buildsStarted, explicit evictions, certifiedPlanes, layeredGraphs). They
cannot survey, admit, wake or repair a route. These discriminate remaining cache
churn versus genuinely detailed topology without increasing workers/cache limits.
No standalone runtime observer or second planning owner is introduced.


### Keep unfinished regional work separate from completed-cache eviction (2026-10-28)

`21516e09a` exact141navigation/shape/runtime/package/economy55releasePASS64s;
both retained311 worlds verified. Native100 interval19247→19313/151s,debt414→3376 remainsFAIL.
Live counters show113168lookups/40284builds/38317evictions initially, later
440594/240528/238595. Both planes(192422) and layeredgraphs(47848) complete; it is
incorrect to call all those evictions unfinished work. Counters identify churn;
source independently proves that graph-retention eviction can discard unfinished
surveys before their next cooperative slice. Repeated cheap region traffic can
therefore erase the same complex continuation indefinitely.

Normal graph/surface pressure now evicts only completed entries. The separate
64-builder ceiling still evicts oldest unfinished work when that ceiling itself
is exceeded. Worst-case builders64×4096 fit below both2048region and524288surface
ceilings, so ordinary overflow always has a completed victim. Neither capacity
nor concurrency increases. Existing certified-region coverage now starts a
partial survey, passes2200unrelated clear regions through the real retention
limit, resumes the original survey without re-reading its first column, and
validates its terminal adjacent route. Worker/region-invalidation coverage and
guardrails PASS19s. This is bounded continuation progress, not a timing benchmark.
The change does not establish that this was the sole remaining native bottleneck.


### Hash-based immutable regional revision lookup (2026-10-28)

4479e1302 exactcleanreleasePASS43s (142navigation/shape/worker/codec +runtime,
packagedJAR); economy55unchanged from215 reused, no new physical effect primitive.
It is not deployed separately; next verified artifact includes both connected
geometry corrections, avoiding an intermediate restart without useful evidence.

215's20sJFR shows221samples under PedestrianGeometryRevision construction;
leafstacks include81MapN.probe,67record-equality method handles,22Map.copyOf and
31Objects.hashCode. A sampled real caller is GoodsTripAssembly.declaration →
ShipmentEndpointComposition.knowledge → forJourneyPrepared → revision → Map.copyOf.
This is preparation of the selected trip, not speculative unrelated work. The
compact linear-probe representation clusters regional coordinate hashes and
reconstructs a long collision chain for each new immutable query/profile view.
The existing ImmutableLookupMap replaces that representation in the revision
constructor, preserving complete facts, defensive immutability, value equality,
full cached hash, bounds and scope/profile fencing. No new collection or hash-only
proof, lossy version key or world-store migration. Existingscope/regionalinvalidate/
certified builder/guardrail coverage PASS28s. Constructor checks the existing
region bound before copying. Native impact still requires the same retained100
ordinary measurement after exactref packaging/deployment.


### Incremental regional facts and body envelopes (2026-10-28)

b0d003a3b exact142navigation/runtime/package release PASS43s; both retained311
worlds deployed VERIFIED. Six ordinary100 observations ended19627/debt3322 FAIL.
20s JFR confirms the MapN constructor hotspot disappeared, but preparation still
spends samples on Facts.hash (36), ImmutableLookupMap.get (30), RegionFacts.hash
(13); real revision capture copies/hashes complete world metadata per local change.
Actual nav allocation19.44/19.35GB and preparation7.36GB remain excessive; these
are ThreadAllocationStatistics deltas, not sampled allocation weights.

The derived geometry owner now uses immutable spatial pages (16×16regions), each
with its exact cached Map hash. Ground and task-view successors replace/remove
explicit changed regions; unchanged pages survive directly. Equality still compares
full facts even for equal hashes. No canonical schema or world mutation changes.
A no-halo resident profile shares its regional map directly. Wider body profiles
retain bounded source/envelope seeds and rebuild only the changed region halo,
folding source facts in the same lexicographic order as the full projection. Seeds
contain immutable maps, never predecessor revision chains or mutable world readers.

Existing scope coverage adds hash collision, removal, predecessor/entry immutability,
remote-fact no-rehash, and local body-envelope equivalence to a fresh full rebuild,
including complete withdrawal. Existing unknown/foreign-world/shape invalidation,
worker and terminal-route coverage retained. Focused guardrails/navigation PASS37s;
final immutable seed and zero-halo selection PASS44s. Native impact remains open
until exactref packaging, retained-world deployment and ordinary100 observation.


### Admit expensive graphs rather than clear-plane traffic (2026-10-28)

c8953a4ea clean release PASS52s (144navigation/shape/scope, runtime/package;
unchanged physical economy55 coverage reused). Both retained311 worlds verified.
Ordinary10020096→20195/151.4s,debt396→3327 still FAIL. New20s JFR no longer
puts world-fact hash/copy among preparation leaders. Navigation still repeatedly
surveys layered regions; region counters show most builds are certified planes.
A source counterexample is direct: at the common2048-region retention boundary,
one-unit certified-plane traffic displaces completed expensive town graphs.

The common cache now checks a positive producer certificate outside its lock
and before reserving any graph capacity. The result is a compact arithmetic plane
returned to the exact requesting search; it is not admitted to the expensive-graph
LRU. Negative certification enters the unchanged layered builder/cache lifecycle.
Graph/surface/builder bounds and worker count are unchanged. No world key, body
profile, unknown-cell rule or denied passage is approximated. Existing graph
comparison and current-world/recovery invalidation tests remain. The existing
retention case now proves both unfinished and completed graph survival under cheap
traffic, and an additional bounded case proves actual layered-capacity eviction
protects unfinished work and subsequent planes cannot evict even a full cache.
Focused navigation/shape/scopes/guardrails PASS30s. Native impact still open.


### Preserve measured trade comparison across addressed route waits (2026-10-28)

d9c8b004d native10020323→20325/151.4s,debt451→3477 remains FAIL.
The bounded ae7b0181d diagnostic, verified privately after37s exact release,
discriminates the next problem:573completions were313ESTIMATED+260FOUND, with
1926distinct scoped tiles/1926entries and524126reserved surface slots. Later
6089ESTIMATED+3460FOUND,1963distinct tiles/1963entries. These are successful
calculations, not mass NO_PATH or multiple stale revisions of the same tile.
An offline read-only snapshot337743/tick20320 endpoint18→48 probe finds the
14389edge journey for both human and donkey,337regions(56layered+281planes),
~0.48/0.86s. It is not a native queue or comparative throughput acceptance.

Actual GoodsTripPlanning selects an optimistic proposal, obtains its exact
journey/cost, reranks it against other optimistic alternatives, and can then
yield on another route. The next callback reconstructs every proposal from
its optimistic quote and forgets the already measured comparison. Bounded
route-result retention cannot be the sole memory of an economic deliberation: a
later async leg can make the same expensive alternative look cheap again.

The economic owner now retains at most2048 measured scalar offer/timing comparisons
(the existing procurement alternative ceiling) per exact unchanged participant/state/
instant decision, bounded256owners and one instant. Inputs use complete immutable identities; a changed world, participant
or execution instant drops the corresponding comparison. No draft state, crew,
reservation, route receipt or dispatch permission is retained in those values.
Selected offers still repeat current exact stock/funding/crew/geometry/provision
and price admission before emitting their ordinary transaction. Every alternative
remains available; neither a top-k cutoff nor a gameplay price/availability change
is introduced. Existing quote/recovery coverage adds an expensive-first-option/
second-wait ranking case and changed funding/participant/time fences. Verification
and native impact pending; no throughput acceptance claimed.


### Separate a provisioning itinerary from one executable movement leg (2026-10-28)

cf511f739 exact release137trade/worker checks+runtime/package+55native economy
PASS102s; both retained311 worlds VERIFIED. Ordinary10020433→20442/272.5s,
debt737→6179 still FAIL. A read-only same-state OPPORTUNITY probe(snapshot339590,
tick20432,settlement21) confirms both callback preparations reuse the identical
canonical state as comparison input; the new memo is wired, not silently fenced
by a fresh draft on that specific captured path. This is not native completion.

Further whole-path source review establishes a separate concrete rejection:
ExpeditionJourneyProof.select calls plannedBetween for each full round-trip leg.
That API delegates to queryBetween, which converts an otherwise FOUND route to
SAFETY_LIMIT after4096surfaces, the TimedKnownRoute movement-leg bound. Scale-r31
settlement journeys commonly exceed that bound. Planner completion counters still
show FOUND because the later API performs this conversion, explaining why success
counts alone did not establish expedition admission.

Common navigation now exposes typed plannedItinerary -> PedestrianPathProof for
whole-journey provisioning, retaining exact endpoints, awaitable unknown/planning
behavior and the existing path-proof safety bounds. Expedition consumes that
explicit API. Ordinary bounded movement/queryBetween retains4096; actual group
motion continues to refine bounded segments through its existing navigator. Same
path-proof codec/schema and geometry validation; no scalar estimate is promoted
to a provisioning proof. Existing navigation coverage adds a real5000edge route
that remains too long for one physical leg but yields a valid full itinerary, plus
foreign-endpoint rejection. This fixes the concrete conflated bound; complete
per-edge provisioning refinement and the65534-edge proof ceiling remain scale
design debt, not a completed regional-journey architecture or G acceptance.

The same read-only snapshot endpoint18→48 case reproduces the complete issue:
FOUND14390surfaces, old bounded-leg API SAFETY_LIMIT, new itinerary proof14389edges
compressed to21runs. This remains exploratory source-side evidence. The first new
model fixture incorrectly required a globally shortest5000edge path; hierarchical
portals legitimately produce5014. It now compares proof length with the actual
returned route and still requires >4096 and exact endpoints; no production policy
was loosened for that invalid test assumption. The focused corrected navigation/
path-validation selection PASS17s; prior181other selected checks passed.

Whole-journey validation also no longer rescans every edge for unrelated remote
geometry changes. After one full exact validation, the common validator retains
only the consulted regional tokens, explicit body/access scope, bounds and pristine
regional dependencies. RLE runs enumerate crossed regions without expanding every
coordinate again. A touched/pristine local change or foreign scope forces real
validation; nonregional/oversized footprints retain full-version validation.
At most128paths and the existing regional dependency bound, no authoritative
receipt fabrication or persistence change. Capture happens outside the shared
cache lock. Existing validation coverage proves remote reuse and actual local/
foreign invalidation. G and the full per-edge proof scalability limit remain open.


### Unfinished calculations own consumed positive route reads (2026-10-28)

3736d08f4 exact183navigation/expedition+runtime/package release PASS123s; both
retained311 deployments VERIFIED. Native10020501→20539/272.5s,debt769→6182
remains FAIL. The concrete4096 admission conflation is repaired, but not sufficient.

The next connected source counterexample is a two-stage preparation: consume
routeA, await routeB; the wake index retains only unresolved requests, so routeA
becomes ordinary evictable shared-cache data. Under >128result traffic or the
shared coordinate/dependency budget it disappears beforeB completes. Re-running
the owner from its start then requestsA again. More successful worker completions
do not establish progress of that unfinished caller. Economic comparison memory
only helps after complete logistics preparation and cannot replace this missing
common calculation lifecycle.

The existing planning boundary now captures used successful reads into bounded
immutable PedestrianCalculationMemory. The exact waiting ScheduledAction owns its
private read set across synchronous, detached and advisory preparation phases;
shared-cache delivery and result retention remain independent. Each read binds
query/profile/accessscope, extent and current exact regional dependencies/baseline;
conservative providers keep exact immutable versions. Only successful facts enter;
pending/unknown geometry cannot be manufactured by memory. No live geometry reader,
parent preparation, actor, world writer or reservation is retained. Existing RLE
losslessly stores distant route geometry rather than every block coordinate.

At most32reads/131072represented coordinates per unfinished calculation. Current
queries validate facts again; actual transitions, cancellation, retirement and a
new generation release the private read set. Shared-worker/result bounds do not
increase, no process family or depot policy enters common navigation.
Existing wake coverage now floods the real result cache between two route stages,
proves onlyB restarts and then reaches terminal cancellation, and proves a later
calculation cannot inherit the released read set. Added demand coverage proves
remote regional reuse but rejection after local change, foreignscope or newbaseline.
Navigation/worker/wake/guardrails focus PASS24s; final addressed-wake and negative
regional cases PASS15s. Exactref release/native impact still pending; G remainsOPEN.


### Deliver completed calculation data independently of business execution (2026-10-28)

ea4f82b2e exact release PASS109s (190 frontier checks/34classes, runtime/package),
both retained311 deployments VERIFIED. Native queue reduced, but ordinary100 still
fails: latest instant20609/debt6425. Named planning evidence
`calculation-read-memory-second-planning.txt` shows24retained/24protected results,
8awaiting publication and all8active searches already terminal (CONNECTED_KNOWN_ROUTE
or CONNECTED_REGIONAL_ESTIMATE). This is shared completed-data backpressure, not
8unfinished searches. Protecting completed data until its exact business owner can
run permits a future/owner-held consumer to block publication required by earlier work.

The common wake index now acknowledges completed data into each exact waiting
continuation's immutable private read memory on RESULT_AVAILABLE. An acknowledged
request no longer protects a shared result slot. Neither the schedule generation,
deadline, canonical state nor business admission is changed by data delivery.
Lossless route packing is prepared once on the search worker; server notification
merges bounded references. Known terminal negatives are also deliverable, with the
full surveyed dependency fence. Pending/missing knowledge is never cached as a
completed answer. Use still checks current geometry, body/access scope and baseline;
retirement discards private delivery. Existing capacity limits remain unchanged.

Regression exercises140actual short searches for future live continuations through
the real128result capacity, without executing any owner. It proves completion,
shared eviction, private reuse and release on retirement. Negative-route coverage
checks remote reuse and relevant-terrain reopening. Focus19sPASS before the final
negative case; exact release and native throughput impact pending. G remainsOPEN.


### Preserve the complete unfinished calculation, not an LRU prefix (2026-10-28)

13f43ecd3 exact release101sPASS192frontier/34classes+29runtime+guardrails/package;
both retained-world deployments VERIFIED. Native10020610→20610/272.4s,
debt396→5845FAIL. Named observations prove shared-data publication is no longer
blocked (protectedResults0), yet preparation continues at an unchanged revision.

Read-only SnapshotPreparationResume on actual checksummed snapshot343049/tick20609,
earliest due opportunity settlement93, executes the ordinary process/continuation
boundary against real snapshot geometry without WAL/native writes. Before repair,
48completed searches lead back to the first request and private memory drops32→0.
This is a concrete caller cycle, not merely a theoretical cache-capacity concern.
A root-resumed calculation needs more than32small answers. The previous arbitrary
read-count eviction discarded acknowledged facts, while a partial retry also
replaced memory with only the subset reread before its next wait.

The common calculation memory now retains untouched acknowledged facts across
partial synchronous/detached retries. Its bound charges metadata, regional tokens
and packed coordinates against131072weightedunits instead of equating every scalar
answer with a full route. It never silently evicts an unfinished calculation's
prefix. Actual quota exhaustion fails explicitly with the retained amount; business
completion/cancellation still releases memory. Shared cache, worker and route limits
are unchanged. This is a calculation-lifetime repair, not permanent world history.
The existing actual eviction/resumption regression now includes40prefix routes;
future-delivery coverage includes an intervening retry which does not read its
received fact. Overflow has a negative case. Focus15sPASS then29checks+guardrails18sPASS.
The same snapshot probe now progresses beyond72private reads without restarting the
first request; terminal/native100 closure still pending. G remainsOPEN.


### Compact exact repeated regional dependency facts (2026-10-28)

f70c63a58 focused29checks/guardrails18sPASS, exact193frontier+29runtime/package
release98sPASS, but deliberately NOT deployed: the actual settlement93source probe
reaches75reads then exceeds132432weightedunits. Fail-visible exhaustion exposed
repeated regional metadata which old LRU silently discarded; it is not acceptance.

PedestrianRegionFacts now represents the exact immutable dependency footprint in
16×16tile pages. Equal tokens share a256bit mask; heterogeneous/small pages retain
ordinary sparse maps. Holes remain absent. Hash collisions never equate facts.
Dependency validation checks the original exact token in every addressed tile;
body/accessscope, bounds and baseline fences remain. No terrain cell is inferred.
The search worker prepares this compact receipt once; native completion reuses it.
Calculation memory charges that actual representation against the unchanged131072
weightedunit ceiling. Worker/result/path bounds are unchanged.

Focused guardrails+navigation lifecycle/invalidation checks PASS20s, including negative
coordinates, holes, colliding tokens, sparse fallback, immutable input and relevant
terrain invalidation. On the SAME actual snapshot343049/tick20609, ordinary process/
continuation/planner source probe now reaches110completed navigation demands, emits
5events including a buyer-collection transport mission, and releases private memory.
`compact-dependencies-source-resumption.txt` endsTERMINAL_PREPARATION_NOT_NATIVE.
This proves preparation terminates against real retained geometry; no WAL/native
state was written and it does NOT prove a native delivery or ordinary100throughput.
Exactref release, BOTH retained-world deployments and native100observation follow.


### Preserve owner-side deliveries while detached preparation is in flight (2026-10-28)

f8345bede exact release98sPASS195frontier/35classes+29runtime/guardrails/package;
both retained311deployments VERIFIED. Ordinary100still accumulating debt; no
throughput acceptance. First native named snapshots show protectedResults0 and
completed searches331→539 while preparation still dominates a20sJFR. Source-only
termination on the earlier snapshot is narrower evidence than native throughput.

Connected asynchronous counterexample: a worker captures an old read view/private
memory, then a route completes and is acknowledged to its owner before that worker
publishes. Its stale acquire could request the evicted result again; publication
also replaced the newly delivered owner memory with its older captured subset.
The same source-level interleaving is reproducible without a timing assertion.
Prepared.acquire now consults the exact owner's latest delivered memory without
creating a new observation scope or abandoning required live work. Calculation-only
publication merges current exact-generation delivery with captured reads. Business
transitions still release memory. The regression completes a real search after
snapshot capture, acknowledges it, evicts the shared result, then publishes older
advice: no new search, a ready owner, terminal cancellation and memory release.
Focused wake/demand/economic-advice checks plusguardrailsPASS23s. Pending calculation
identity is also exposed through the existing named read-only planning diagnostic,
so an evolving native hold can be tied to its exact action rather than a guessed
family. Scheduled calculation/recovery identity and NeoForgecompile checks pending.


### Active container capacity facts and selected-only draft admission (2026-10-28)

be052bf58 exactrelease104sPASS200frontier/36classes+29runtime/guardrails/package;
not separately deployed while this connected preparation-cost correction was built.
f8345 native10020611→20611/272.5s,debt346→5797FAIL; revision343076→343080,
waiting108→104. Some preparation dispositions complete, but ordinary time remains
blocked. JFR compact-route-dependencies-warm.jfr actual allocation deltas over20s:
preparation23.73GB,navigation9.99GB,server0.56GB; no sampled-weight interpretation.
This is phase attribution, not a precise isolated speed comparison.

Source/native stack ties preparation to complete discarded transport admission,
ExactInventory capacity scans and container account indexing. ContainerStockIndex
also held only256per-container ImmutableInputViews for a larger active registry.
A full scan cycles the LRU and creates a new ThreadLocal per dropped view; actual
captured worker stack includes ThreadLocal.expungeStaleEntry beneath this path.

Capacity facts now belong to a thread-local active-container/lot-metadata epoch.
Plain entries retain exact receiver stock/physical-slot inputs; registry membership
bounds them. No per-container ThreadLocal or undersized global LRU, no shared
capacity lock, no historical lot map per entry. Slot identity reuse is thread-local.
Existing stock/claim/equipment invalidation coverage plus300active-container scan
regression verifies unchanged facts survive their own registry scan. Focus56sPASS
(after correcting a compile-only ThreadLocal return-type mistake).

GoodsTripAssembly no longer constructs a fully admitted disposable world for
every candidate merely to discard it after cost comparison. Both actual selecting
callers still invoke the sole TransportMissionProcess.admit owner before publishing
the selected declaration; canonical event replay invokes the same owner again.
Exact stock, cash, crew, custody and provisioning validations remain. Existing
consolidated trade/acceptance/autonomy/expedition checksPASS83s. Source reviewed;
combined exactref release and retained-world native impact still pending.


### Complete the advisory/exact boundary for actual trade matching (next connected repair)

The e9combinedreleasePASS102s220frontier/38classes+29runtime/package; bothretained311
servers VERIFIED. Native has meaningful decisions/work now, but still accumulates
clock debt. Named pendingScheduledCalculation explicitly identifies
frontier.goods.participant.opportunity/settlement93 (due20633, later20654), not a guessed
navigation family. Its query buffer is unblocked. Kernel PendingCalculation retains
one exact world predecessor; native GoodsParticipantProcess exposes advisory capability
only for REVIEW, while OPPORTUNITY invokes full GoodsTripPlanning under that exact
barrier. Accepted review advice also hands full matching back to exact preparation.
Thus all quote/candidate comparison/provisioning waits can pin unrelated canonical time.
This is the remaining connected contradiction in accepted phaseD, not a request for
another worker pool, larger allowance or a second scheduler.

Repair: the economic owner computes full alternative choice as disposable advisory
work on the existing bounded pool. Its output is an explicitly typed conditional
choice (counterparty, endpoints and delivery mode), pending, or no purchase; never a
reserved contract, actor assignment, mutable draft, wallet or cargo. Existing review
advice can carry the same choice alongside its conditional policy result. OPPORTUNITY
gets its own registered advisory capability. The current exact phase rebuilds quotes,
funding/custody/crew/provisions and admission only for the chosen alternative; it must
never restart a search over all alternatives under the global predecessor barrier.
Known route facts still come from common navigation/continuations, and the existing
WAL/admission owners alone publish. Changed/withdrawn endpoints, prices, stock, funding,
roles, geometry or owner identity cannot spend or dispatch stale advice. Pending advice
parks only its exact owner while independent canonical work continues. No priority,
per-tick budget, shared-cache/worker ceiling or business clock is increased.

Keep direct deterministic planning on the same selection/admission implementation;
no active compatibility path. Validate unchanged-input choice equivalence, stale/foreign
negative admission and genuine independent work/clock progress while a market choice
awaits calculation, then exactref build, retained-world deploy and ordinary100 evidence.
This entry is a scoped implementation decision under acceptedD; not completion.


Independent trade-choice source now wired: GoodsParticipantOpportunityAdvice is
registered through the ordinary process/advisory composition. Existing periodic
review advice carries the same conditional choice. GoodsTripPlanning explicitly
produces Selection(seller/buyer full parties, declared typed endpoints, delivery
mode), AWAITING_NAVIGATION or NO_PURCHASE. No prepared transaction/world/crew escapes
as the advisory result. The current exact phase refreshes normal quotes/commitments
and filters preparation to the selected party/endpoints/mode, or records the pending
owner wait/no purchase. It cannot start a new all-alternative comparison. Existing
single admission/WAL owners still validate fresh money, goods, consent and execution.

New affected coverage proves stable-data event equivalence to direct planning,
no dispatch/reservation after buyer funds leave or seller orders are withdrawn,
and no further route calls during the exact completion of pending choice. Existing
real registered-opportunity test now executes its advisory capability and still
proves it does not invoke/republish the economic forecast; the old assertion that
OPPORTUNITY has no advisory capability was an obsolete implementation detail.
Fixture initially lacked the buyer's accepted domestic policy and therefore had
no purchase opportunity; corrected by the existing real policy producer/publication,
not fabricated orders or success. Focus40checksPASS18s; final withdrawal-negative
+guardrailsPASS18s. Generic advisory-kernel independence coverage remains included.
Exactrelease and actual100clock evidence still pending. G remainsOPEN.


### Charge the current completion phase, not its removed comparison loop

44f31e76d225frontier+29runtime/guardrails/packagePASS102s; bothretained311deployments
VERIFIED. Ordinary10020986→21169/272.6s,debt1321→6591FAIL. Named planning no longer
shows a permanently pinned all-alternative exact calculation; real decisions/physical
work advance. This still does not meet ordinaryclock closure. Native counters include
7196WEIGHTED_ACTION yields versus8472preparations. Registered GoodsParticipant weights
still multiply peer count/modes even for a pending/no-purchase exact completion which
the connected regression proves performs ZERO route queries. The old full-comparison
cost was charged again at canonical admission after that work moved to the advisory pool.

Registration now declares OPPORTUNITY as a coalesced ordinary continuation; its current
exact work is one selected candidate, not all market peers. REVIEW retains the existing
base policy-publication weight. The selected path looks up its explicit full seller
party directly instead of enumerating all sellers; current mutual consent is still
checked by GoodsOrderMatching.first before reservation. Removed now-unused peer-count
helper and its obsolete implementation-formula assertion; public opposing-interest wake
coverage remains. Common coalescing, budgets, priorities, pool/concurrency/result limits
are unchanged. The bounded advisory pool owns comparison computation; no full comparison
can fall back into native exact execution when advice slots are busy. This is phase-cost
correction after an ownership move, not a larger global execution allowance.
Wake/advice/kernel/negative focus40checks+guardrailsPASS24s; the final direct seller lookup
was source-reviewed against current matching/consent and awaits exactref gates/runtime.


### One immutable advisory calculation frame, with explicit rejection cleanup

b51b96157 exactrelease95s225frontier+29runtime/guardrails/packagePASS. Its private
deployment did NOT happen: preflightPASS but RCONstop refused because44f had already
quarantined at04:53:49 with retainedfactquota133020, then completed native all-dimensions
save and stopped. No artifact/profile/world mutation was made by that failed deploy.
Raw44flog retained before replacement. The old delivery exception lacked exact wait
owner, so the specific overflowing subscriber is not recoverable from that trace.
Do not label b51runtimeaccepted or call this solely a deployment-method failure.

Connected reachable cause: a parked action kept accumulated route reads, but each
advisory retry captured a NEW world/actor-position/market snapshot under that same
exact action. Its read set could therefore contain many unfinished input epochs,
not one finite decision. The common continuation now retains ONE original immutable
ScheduledAdvisory input closure beside the existing exact Wait only for a declared
calculation-only retry. A newly supplied native view is not silently substituted for
that computation. The world continues independently; the domain owner still validates
current inputs/expiry and builds a fresh selected admission. Rejection/expiry releases
the exact wait, snapshot, subscriptions and reads before a new epoch; business transitions,
cancellation, replacement and retirement release them as well. No second snapshot registry,
canonical writer, worker pool, generic policy inference or larger fact quota.

Common regressions use real cooperative route completion: an unfinished captured
advice resumes its original query despite a later different input capture, reaches
terminal cancellation, and only a later new task can use the new capture. The rejection
case releases its delivered reads and snapshot before replacement. Focus42checks+
guardrailsPASS23s; finalclarity/diagnostic changesPASS20s. Delivery failures now add exact
kind/schedule/subject around the original cause, without suppressing quarantine.
Specific native failure attribution and clock closure still require the new candidate's
runtime evidence; the source counterexample is not universal native acceptance.


### Pending advice follows newly observed terrain

f67b83a38 exact release PASS (71 frontier / 9 classes +29 runtime, guardrails,
packaged JAR); private retained-world deployment VERIFIED. Ordinary observation is
running and already contradicts throughput acceptance: sample0→1 advances21397→21405
in30.4s while debt4747→5349. No claimed scale completion or speedup. Navigation has
only one active search at the discriminating sample; investigation continues into
actual decision/transition cost, not a blind pool increase.

Connected source correction: a retained OPPORTUNITY advice reporting
AWAITING_NAVIGATION must reject a replaced navigation observation epoch. Otherwise
newly observed terrain wakes the producer while its captured snapshot continues
requesting the old unknown cells. Selected dated choices already run fresh exact
geometry admission; unrelated physical crop deltas do not automatically retire the
pending frame. Existing pending-choice regression now verifies rejection and fresh
capture on a real NavigationGeometryObserved transition. Three affected classes
and guardrails PASS23s. Native proof remains open.


### Batch geometry producer changes and reuse the existing spatial pages

Native f67 ordinary10021397→21590 in272.9s, debt4747→10016: FAILED. Its20s warm
JFR attributes repeated global geometry composition to both advisory workers, exact
preparation and server thread: KnownPedestrianRouteKnowledge.refresh →
KnownPedestrianObstacleIndex SITES/WITHDRAWN → RegionalBlockContributions.replace.
Actual thread allocation deltas: advice8.92+8.84GB, server4.21GB, exactprep2.48GB,
navigation1.55+1.46GB; sampled allocation weights are not byte totals. Evidence:
build/simulation-throughput/advisory-snapshot-lifetime-{warm.jfr,attribution.txt,
geometry-attribution.txt,geometry-stacks.txt}. c14 pending-input invalidation and
both retained-world deployments VERIFIED; its observation still accumulates debt.

Source cause is connected common work, not a trade-specific obstacle rule: each
producer replacement constructed intermediate unions, enumerated/copy-constructed
already indexed regional blocks, and all three spatial directories copied/scanned
flat region maps even for a local change. The common contribution owner now batches
a complete input epoch into one final exact union and shares immutable contributor
parts. RegionalBlockSet, RegionalBlockMap and RegionalSurfaceMap reuse EXISTING
ImmutableRegionMap spatial pages; unchanged pages skip deep comparison and local
replacements adjust cached size/hash by changed-part deltas. No parent chain,
probabilistic equality, new cache capacity, discarded obstacles, state schema or
canonical ownership change. All production consumers use the corrected shared types.

Focused guardrails+69frontier/9classes PASS81s. Existing reconstruction, collision,
terrain/unknown, multilevel, historical branch and trade checks retained. Added two
focused obligations: exact size/hash/equality/removal under deliberately colliding
facts and direct shared regional parts across a batched producer handoff/private fork.
No timing assertion or broad native claim. Exact release/deployment and measured
ordinary100 progress still required; this source correction alone does not closeG.


### Resume the market decision after its suspended selected candidate

60767c40f exactrelease110frontier/12classes+29runtime/guardrails/packagePASS83s;
both retained-world deployments VERIFIED. Ordinary10021944→22286/272.6s,
debt208→5319 FAILED: measured1.25canonicalticks/s, not20. Changed phase means no
isolated speed ratio against earlier worlds. New20s JFR has removed regional-set
equality from the top advisory leaves; comparison/reservation/review work now dominates.
Actual allocations advice14.85+14.63GB, server6.60GB, exactprep3.49GB, navigation2.94+
2.82GB. Higher totals in this busier phase are not a claimed allocation improvement.
Evidence paged-geometry-composition-{warm.jfr,attribution.txt,native-samples.jsonl}.

Connected source cause: even with fixed captured inputs, each route-readiness retry
re-ran GoodsParticipantProcess's quote/policy/draft prefix, recreated all proposals
and reservations, then attempted the same selected logistics candidate. f67 fixed
input lifetime, but did not retain computation progress. Market advice now lazily owns
one serial cooperative calculation: fixed assessment/prefix, alternatives and exact
pair-review counters. PLANNING retains the selected private candidate and resumes it;
completed/rejected choices release with the existing exact Wait lifecycle. Completed
alternatives and retired obligations are not rebuilt after every route answer.

Only a typed conditional choice crosses to exact admission. Current canonical stock,
funding, consent, crew, supplies, geometry and transaction/replay validation remain
fresh and unchanged. Private draft reservations never become actual claims; no durable
state/schema, worker pool, budget or priority change. Both OPPORTUNITY and REVIEW use
the same decision calculation. Synchronous planning uses the same implementation.
Existing pending-choice regression now resumes after real direct route calculation,
reaches TransportMissionStarted, equals unchanged-input direct planning, preserves
the original unmodified world and retains stale-funds/withdrawn-quote negatives.
Guardrails+64frontier/7classes PASS83s. Exact release/runtime evidence still required.


### Check physical capacity only where physical packing changed

d1027297c exactrelease71frontier/9classes+29runtime/guardrails/packagePASS89s;
both retained-world deployments VERIFIED. Ordinary10022665→23114/272.5s,
debt344→5347 FAILED. Warm20s JFR resumable-trade-choice-warm.jfr/attribution.txt
identifies GoodsTripPlanning.reserveSelected → GoodsTradeStateSupport.reserve →
ExactInventory.withFungibleResources → admitNoNewContainerOvercommit → slotBudget /
FungibleResourceReadIndex.containerAccounts. One advisory worker has228 capacity
samples inside823total observed preparation samples (inclusive, not additive CPU
percentage). All-container pre/post comparisons can alternate component images and
rebuild whole custody indexes after the planning scope's bounded memo fills.

Every existing capacity-admitting inventory transition now selects affected physical
containers once from exact component changes. Claim-only reservations/title changes
do not change occupied space. Actual lot quantities/custody, lot kind, individual
slots, physical binding addresses and container declarations retain capacity checks.
Stock conservation, owner/custody validation, physical postconditions and the prior
no-increased-overcommit rule are unchanged. No unsafe transient inventory, skipped
canonical audit, second stock ledger, cache increase or trade-specific exception.
The pure immutable-map difference is shared with existing economic change ports;
capacity projection owns no economic policy. Generic resource mutations, equipment
moves, production and cargo handoff use the same corrected inventory admission.

Focused guardrails+67frontier/9classes PASS90s: reservations change no physical
capacity dependency; stock/equipment changes identify their actual storage; changing
one32-item wheat lot to bread with unchanged custody quantities still rejects a
one-slot overflow. Existing ledger, player-edit, compartment, reservation and trade
negative coverage retained. Exact clean release, deployment and ordinary100 runtime
measurement remain required; no measured capacity speedup or complete G claim yet.


### Active field geometry must not evict itself during one registry scan

70235e6ec exactrelease67frontier/9classes+29runtime/guardrails/packagePASS43s,
both retained-world deployments VERIFIED. Ordinary10023672→24125/272.5s,
debt555→5553 FAILED. Warm20s affected-container-capacity-warm.jfr/attribution.txt
shows review/transport-cost/installed-capacity/land preparation now dominates, rather
than all-container admission. Actual allocations advice10.70+10.10GB, server6.72GB,
exactprep5.69GB, nav0.45+0.36GB. Different phase/work, no isolated allocation ratio.
Final planning has252owner waits but ZEROactive/deferred/inflight searches; total
waits are not proof of a navigation queue bottleneck or a need for more threads.

Connected static cause in land preparation: LandUseExclusions.FIELDS was an LRU of
MAX_SETTLEMENTS*2=200 entries, but the current native saved image contains400actual
field cycles (revision464110/instant25383), within ResourceSiteState.MAX_SITES1024.
A complete sorted registry scan evicts its own prior results. That changes every
contributor identity, misses the final protected-land union cache and reconstructs
world-wide geometry again for each economic/development query. Read-only native saved
image probe: build/simulation-throughput/active-field-registry-native-cardinality.txt;
no native state/time edit or simulated outcome.

The land producer now uses a common computing-thread immutable registry projection:
every exact live field/layout retains its own completed column image. Its bound is
the existing declared resource-site registry bound, not an undersized LRU or a raised
domain capacity. Changed layouts recompute; removed rows and a new bootstrap release
old inputs. Published outputs remain immutable; a failed projection installs no partial
trusted basis. No second geometry writer, stale terrain permission, larger worker pool
or approximation. The helper is immediately consumed by LandUseExclusions.

Focused active-registry/land/cultivation/extraction and advice checks+guardrails PASS42s.
One existing input-view test now covers300active rows, one changed/deleted row, immutable
predecessors, failed construction and a new epoch, with computation counts rather than
wall-time assertions. Exactrelease/deploy and sustained ordinaryclock measurement remain
open. Separating unit-cost metadata from timed production forecasts is a possible later
boundary improvement; do not begin that wider refactor solely because this cheaper
source-proven churn had not yet been removed. No incremental ledger rewrite is warranted
by this latest attribution.


### 2026-10-28 — unchanged composed invariant evidence

The dc1 retained-world ordinary100 observation still FAILS: 25680→26139 canonical
ticks over272.5s, debt567→5559. Navigation waiting counts include owner-held work;
they do not establish a search backlog. Both dc1 deployments were VERIFIED.

A fresh owned20s JFR (`build/simulation-throughput/active-field-validation-warm.jfr`,
with attribution/stacks alongside) shows444 server PM samples inside canonical
transaction validation:224 in composed state validation and220 in reference closure
(inclusive sample counts, not additive percentages). Repeated current-meal execution
checks, shipment endpoints, and full-state owner checks are reachable consumers.
The same trace still shows expensive private trade reservation and production-cost
projection on advisory workers. Removing one cost is not scale acceptance.

The composed full-state boundary now retains one successful exact immutable argument
image for each existing delegated owner check using the existing
`ImmutableInvariantCheck`. Every supplied argument remains in that check's identity
read set; a changed argument reruns the original owner implementation. The existing
full ingress/recovery mode bypasses every such image. No unchecked constructor,
state authority, weaker invariant, schema change, larger budget or new cache history
is introduced. Meal, field, production and courier execution-reference owners likewise
retain only their last successful input image. A failed check cannot certify inputs.

Existing incremental-validation coverage now independently withdraws an exact field
job and forges its current execution owner after warming valid evidence; both fail
before publication, and the original world passes forced full ingress validation.
Focused79 checks across9 classes plus guardrails PASS46s; extended negative4 checks
PASS30s. Clean-ref release, native performance and terminal scale acceptance remain
to be recorded. G remains OPEN.


### 2026-10-28 — conservation by the exact cold-reservation operation

1e6c219bb clean-ref release PASS49s (79 affected frontier checks,29 runtime,
guardrails/package), both retained worlds VERIFIED. Its ordinary100 observer8378
completed10samples:27620→28159 over272.5s, debt1494→6406. Still FAIL, no isolated
speed-ratio claim and no terminal scale acceptance.

The fresh dc1 JFR also attributes195/268 advisory-worker samples to ledger
construction and145/214 to account validation (inclusive sample counts). An exact
cold reservation was rebuilding and revalidating every account's kind/backing/pin
tables even though only a fresh claim and one existing account changed. This is now
a scoped operation-construction proof, not a generic trusted-constructor bypass.

FungibleResourceLedger is a final immutable value class retaining its four getters,
value equality/hash and the explicit unchanged wire codec. External/bootstrap/
hydration construction retains full identity, bounds, capacity, conservation and
binding validation. A private cold-reservation operation constructor inherits the
predecessor's exact lots/bindings, requires the exact unbound account, a fresh claim
and available stock, and rechecks ALL backing/pinning rules of the changed account.
Only the fresh claim is added, in full to that one existing account; no other custody,
stock quantity or account can change. This preserves global conservation directly.
There is no general unchecked factory, trust flag, ambient bypass, new schema or
second reservation owner. Bound operations and other transformations still use the
full construction path. Full world ingress/recovery additionally forces the ledger's
complete audit; it cannot rely solely on an operation's previous proof.

The existing validation owner now contains shared local-account and complete checks;
quantity-subset checks moved to that same owner with all immediate consumers updated.
Existing binding-repartition support owns binding-image removal. This keeps the
ledger under the unchanged1000line guardrail without compressing or weakening it.
ImmutableLookupMap.withEntry avoids a redundant mutable→immutable replacement copy;
its result is a flat defensive snapshot, never a parent history or mutable alias.

Extended existing regression: partial pinned reservation equals a fully audited
construction, passes full audit, rejects overlapping lot pins even when aggregate
stock remains, rejects duplicate/foreignowner claims, and leaves its predecessor
unchanged. Existing codec, actor-pocket, bound-allocation, shipment, advice replay and
resource-loss coverage retained. Focus62checks/10classes+guardrails PASS43s. Exact
clean-ref release/deploy and ordinary100 measurements remain pending. G stays OPEN.


### 2026-10-28 — inventory proofs follow their actual immutable components

2b389f20e clean-ref release PASS50s (91frontier/13classes+29runtime,guardrails/package),
both retained deployments VERIFIED. Ordinary100 observer96432 completed10samples:
28883→29463/272.6s, debt1274→6149. Still FAIL. Host all-time max671.8ms and recent
max21.3ms; the peak remains a recorded performance contradiction, not hidden by the
lower recent value. Concurrent diagnostic/build phases do not establish a speed ratio.

Fresh20s cold-reservation-conservation-warm.jfr confirms the full account-validation
construction is no longer a top advisory cost. ExactInventory construction now has
153samples on one advisory worker; canonical validation still276 server samples.
Do not launch a speculative general ledger proof/index rewrite from the prior trace.
The immediate connected contradiction is the enclosing inventory constructor, which
repeats unchanged equipment/title/custody audits for resource or financial reservations.
Read-only saved native image549701/tick28611:3029people,641meals,300shipments(299live),
143missions/groups,446movements,1760lots,717accounts,1089claims. Counts are not terminal
work/trade acceptance. Receipt owned-invariant-native-cardinality.txt.

ExactInventory now delegates its original checks to inventory-owned
ExactInventoryValidation. Each original invariant retains one successful image under
its complete explicit immutable read set: container/surface declarations, item titles,
lot titles, conflict anchors, cargo titles, exact-item forward/reverse custody and
resource custody/binding layout. Financial reservations do not alter economic account
membership. Resource claims do not alter custody geometry. The resource-custody check
therefore reads the complete current account-id→typed-custody projection, plus the
actual binding/container/cargo/occupied-slot images. Its registry uses the declared
MAX_ACCOUNTS bound and prunes withdrawn rows; no undersized LRU or historical worlds.
No check was removed, no claim/quantity rule was delegated to this projection, and no
trust flag or changed-inventory constructor bypass was introduced. The same public
constructor remains the mutation/ingress boundary. Full world ingress/recovery now
explicitly forces BOTH intrinsic resource and composed inventory audits, bypassing
all accepted invariant images.

The common resource balance query also retains only queried unchanged active-account
views, rather than clearing every balance after an unrelated claim. Complete exact
read sets include account quantities and every referenced lot/claim record; changed
metadata revalidates even when custody/account identity stays unchanged. Withdrawn or
replaced account rows release old evidence. One thread-local active image, no per-row
ThreadLocals, persistent state or second stock authority.

Extended existing negatives reject changing an otherwise unchanged stock account to
an absent container, reject withdrawing the owner's economic register after warming
proofs, and verify a full original-inventory audit. Existing balance test reclassifies
a lot while retaining the exact same account map and returns to its predecessor;
availability follows the actual kind in both images. Focus94checks/13classes plus
guardrails PASS48s (prior7-class balance focus PASS30s). Release/deploy and throughput
measurements remain pending; the100town goal and terminal stories remain OPEN.


### 2026-10-28 — shipment proofs retain only their exact owner-declared facts

6044fa728 clean-ref release123frontier/16classes+29runtime/guardrails/packagePASS57s;
both retained deployments VERIFIED. Ordinary100 observer52350 completed:30817→31426
in272.6s, debt4026→8870, still FAIL. Host alltimemax820.3ms remains unexplained;
recent21.7ms does not discharge it. Fresh20s inventory-component-proofs-warm.jfr
confirms inventory construction no longer a top advisory cost. Server335inclusive PM
samples in canonical validation, including57ShipmentStateSupport.validate,
46UnitGroupRelationships and36ActorMovementStateSupport; no isolated speed ratio.

Shipment validation now retains successful exact immutable read sets per current
shipment under existing MAX_SHIPMENTS and bootstrap epoch. The same original checker
runs for changed rows; withdrawn rows disappear, failed computation publishes no
partial accepted registry. All4 endpoint providers, both resource-source providers
and all3 authorization providers MUST declare complete structural continuation read
sets through their closed SPI. Shared logistics composes those declared facts with
its own exact shipment, current cargo account/individual item and mobile asset/mission;
it never inspects concrete issuers' price/job logic or discovers missing owner kinds.
These are exact reference identities, not whole-world revision, deep hash or ID-prefix
inference. Admission-only capacity policy is not reused as a continuation proof.
Live shipment withdrawal still fails before validation; unchanged shipment maps need
no repeated retirement scan. Execution-reference closure keeps its existing owner.

Existing ImmutableRegistryView supports explicit input equivalence in addition to
identity; exact dependency tuples compare each underlying reference. Full initial
AND recovery composed barriers now keep ImmutableInvariantCheck.full active around
both state and reference closure, so every row is rechecked at ingress. Normal
durability/commit semantics and event/codec schemas remain unchanged.

Existing quarry withdrawal regression warms a valid shipment, then moves its exact
source surface without altering the shipment, stock or account: validation rejects it.
The original full initial audit still succeeds, followed by the existing real claim
withdrawal, replay and repeated-effect rejection assertions. Existing immutable-view
regression checks equal read sets with fresh wrapper objects and changed source facts.
Focus81checks/12classes plus guardrails PASS93s. Release/deploy and native throughput
remain pending. G remains OPEN; no claim of terminal100settlement operation.


### 2026-10-28 — exact direct collection changes across the real producer path

7c52de7f2 exact release PASS91s and both retained deployments VERIFIED. Ordinary100
observer95831:35131→35756/272.5s, debt2909→7737, still FAIL. Per-family improvements
have not established the target clock or completed any terminal scale story.

A common representation problem remains in actual economic-input comparison and
canonical actor/reference checks: immutable replacement discards knowledge of its
one changed entry. Every consumer then compares full registers. ImmutableMapImage
is a closed collection contract implemented only by ImmutableLookupMap and the
existing constructor-owned ValidatedImmutableMap. Functional withEntry/withoutEntry
retain an exact direct changed-key set ONLY for those closed immutable predecessors,
using a WEAK predecessor reference. All maps still own flat defensive snapshots;
there is no retained parent/history chain, mutable-source certificate, inferred owner,
new event/schema or canonical dependency on garbage collection. Absent/non-adjacent/
collected evidence performs the existing complete exact comparison. Mutable inputs
can never certify that no other row changed after copying.

ImmutableMapChanges and scheduler subject-membership comparison consume the same
exact witness. Existing registry views consume it for ordinary identity projections,
retain the published projection when changed source rows produce identical values,
and update the accepted input basis only after all computations succeed. An explicitly
supplied comparison always runs its complete reevaluation; full invariant audits
cannot take a same-source shortcut. Withdrawals/epochs remain bounded by each producer.
Collection replacement itself still copies its flat map: this is not a claim that an
entire mutation is constant-time or that all global work has disappeared.

Immediate connected producers include common actor body updates; generic navigator,
meal COLD arrivals/interruption; HOT lease handoff and body observation/death/unload;
and existing field/production checkpoints. The source constructor no longer repeats
nullable-entry validation on the collection implementation that already proved it.
Actor identity uses the exact declared-kind projection, so movement does not look like
a changed population roster. Body-reference checks read that declaration index and
the actual recovery store; body coordinates never establish ownership. Position bounds
validate changed actor records through the same registry mechanism. Presence references
check changed actors when the exact current presence index is unchanged. Full ingress
forces the original complete declaration, life, body-fence and bounds checks; changed
rosters, types, current authorities and recovery facts remain strict.

These are read-only proof/index changes in the existing owners, not new movement or
custody authorities. Hive content is unchanged. Existing projections release removed
rows and prior epochs; no ThreadLocal is allocated per entity.

Existing regressions now cover exact direct update/remove and non-adjacent comparison,
mutable-parent edits of values AND keys, a changed source whose projection is unchanged,
forced full reevaluation, actor wrong-kind/out-of-bounds/withdrawal and death under
unchanged presence authority. Existing body death/inspection/reentry, meal, field and
navigator coverage retained. Focus139checks/17classes+guardrailsPASS63s; final closure
30checks/5classes+guardrailsPASS25s. Release/native throughput remains pending. G OPEN.


### Installed production cost is not a dated supply forecast (2026-10-28)

483d7f7ae exact release/retained-world deployments passed. Ordinary100 observation
still FAILED:38833→39335 in272.54s, debt3655→8605. JFR
`build/simulation-throughput/immutable-direct-change-warm.jfr` confirms that advice
still spends substantial sampled work in GoodsTripPlanning reserve/assembly and
GoodsResourceValuation→SupplyCapacityComposition→FieldSupplyCapacity, while server
reference closure includes UnitGroupRelationships. Inclusive counts are not additive
percentages. No allocation stalls observed;136.8ms host maximum remains unexplained.

Source contradiction: installed replacement pricing constructed full SupplyPlanner
processes with per-cell dated biological releases, windows, labour budgets, current
station heat and occupancy. Consumers used only recipe inputs/output, burn fuel and
labour. A replacement cost must not pretend to be a dated production commitment.
All seven closed capacity ports now declare ProductionCost separately from executable
SupplyPlanner.Process. Market replacement/retention, equipment manufacture/repair and
ingredient substitution consume this narrow type; actual sourcing/allocation continues
to use finite dated equipment-backed capacity. Common pricing never inspects concrete
field/mining/station jobs. No jobs, claims, cash, route or schema changes.

Field valuation uses the same actual admitted productive cells, biological yield,
soil preparation/sowing/harvest and storage-distance labour, without sorting or
simulating a growth/release schedule. Mining retains its real finite prerequisite
work graph and tool capability; forests retain actual remaining trees. Stations
retain installed recipe support, workforce and current stock-based ingredient choice.
Worn replaceable tools do not erase installed cost; destroyed land/absent capability
still does. Actual output promises, stock and funding are unchanged.

Existing batch test checks exact cost arithmetic and independence from stored heat,
release timing and labour envelope; invalid fuel declarations reject. Existing
recovery test checks direct field labour against its dated-source formula, worn tools,
destroyed land and exhausted mining equipment. Focus62checks/10classes, guardrails
PASS27s. Native throughput effect remains unmeasured; G OPEN.


### Bounded owner reference checks and one economic input publication (2026-10-28)

6fc33b50b exact build/deploy passed on both retained worlds. Ordinary100 still FAILED:
recovery sample0 excluded,40444→40827/242.7s, debt980→5445. Fresh production-cost-warm.jfr
shows server transaction validation304samples, UnitGroupRelationships107; advice still
GoodsTripPlanning.reserveSelected519 and installed valuation126 on one worker. These
are inclusive sampled counts, not additive percentages. No allocation stalls.

ImmutableInvariantRegistry retains successful structural checks only for exact current
producer declarations plus their COMPLETE owner-declared immutable inputs. One computing
thread image, explicit producer retention bound, withdrawal/epoch cleanup, no per-entity
ThreadLocal, no failed-proof publication. Unpublished drafts always run their original
check; complete initial/recovery audits bypass reuse. This is no canonical registry,
trust flag, ownership resolver, altered event/schema or skipped changed-state invariant.

TransportGroupMissionPort now owns reusable structural evidence for its actual mission,
sponsor account/company, actual registered home endpoint inputs, exact member declared
kinds/resident profiles/executions, manifest shipments and finite asset. Group/member
movement callers reuse that evidence; changed group/cargo/authority still rejects through
the original validator. Asset cargo checks visit the mission's exact manifest instead
of every shipment in the world. Generic group coordination owns no transport internals.

The same mechanism is wired into ActorMovementStateSupport. Each of ALL11 sealed
movement contexts must declare its own exact immutable structural read set next to its
existing validator. Common navigation composes current execution, exact actor existence,
competing meal and the context-supplied facts without inspecting work-family jobs.
Station/extraction/forestry work, shipments, expeditions, access exits and retained
unsupported-family contexts keep the original nominal validations. No target movement,
physical permission, body/cargo authority, travel speed or HOT/COLD protocol changed.

SettlementEconomicInputChanges previously rebuilt the full participant/planning registry
separately for every affected participant in one transition. It now assembles the same
independent invalidations and publishes one immutable batch. GoodsParticipantState uses
the common immutable map, retaining exact direct changes for singleton replacements.
No invalidation, coalescing, revision, quote or price rule is weakened.

Existing negative/recovery tests cover wrong member kind, competing group execution,
forced full ingress after warming, failed checks, withdrawal/id reuse, exact station
job withdrawal under an unchanged movement, batched=sequential owner changes and rejected
unknown owners. Connected focus76checks/16classes (group, movement, interruption, station, transport,
relationships and economic publication/participants) + guardrails PASS79s. Native
clock/terminal acceptance remains OPEN; do not infer it from these source checks.

Read-only saved-image evidence852955/tick41692 in production-cost-native-decisions.txt:
312shipments,209missions,200travelling groups;272GROUPmovements. Many group COLD legs
are9–16surfaces; other movement legs reach4096. SOURCE lead, not a completed diagnosis:
GroupTravelCohesion.segment arbitrates each member's proposed path against CURRENT peer
positions and clips the admitted individual interval. This can retain frequent COLD
boundaries despite long-distance regional navigation. Evaluate its total live cost after
this coherent increment; no group-clock redesign has yet been implemented or accepted.


### Cold inventory observation and registered stock reads (2026-10-28)

2a5d24d90 exact release76frontier+29runtime/guardrails/packagePASS45s; both retained
world deployments VERIFIED. Ordinary100 still FAILED after recovery:42725→42819 in
61.32s, debt729→1862, maxhost203.5ms unexplained. Fresh owned-reference-batch-warm.jfr
retains advice cost in selected trade drafts; server includes46samples in fungible
observation,45 in ContainerEffectFence.pending. No terminal scale story yet.

Source-proven unnecessary physical work: the fungible layout observer called every
family's pending-effect fence for EVERY container account before checking whether a
physical container was loaded, ticking and acquired. Eligibility lookups inspect no
stacks and mutate nothing. The duplicated early fence/click-ledger probe is removed;
the existing observe() STILL checks the complete same fence immediately before reading
inventory. Its composition includes pending player clicks. COLD/unowned/unloaded
containers therefore do not scan all work families. Physical write, custody release,
recovery and player mutation fences are unchanged. Relevant existing physical executor,
physical pass and runtime35checks/3classes+guardrailsPASS46s.

GoodsParticipantView used a whole participant record (including its known-peer roster)
as a HashMap key on every stock read, and cleared all memoized views at64 rows despite
the active100 settlement registry. It now keeps at most one row per CURRENT registered
participant ID under the SAME complete immutable stock/protection dependencies plus
that exact participant registry. A replaced registry releases old rows. Unregistered
or unpublished participant values still compute normally without entering the memo.
No arbitrary cache quota, new business identity inference, policy or stale-view reuse.
Existing participant/declaration, economic publication/recovery and procurement checks
+guardrailsPASS24s. Native effect remains pending, G OPEN.


### Causal clock tracing — first required step (2026-10-28)

User explicitly requested goal and causal tracing BEFORE another optimization.
Goal1793160688 is active; previous absence is no longer a blocker. No measured
end-to-end improvement is claimed for previous leaf corrections. d9f ordinary100
achieved1.805 canonical ticks/s with growing debt (target20); G remains FAILED.

One on-demand JFR recording joins existing owner-stage metrics to scheduled
passes (exact action/world/revision), exact/advisory worker queue/run/completion,
owner observation/revalidation, transaction append/publication, WAL batch members
and force completion, queued readiness callbacks, clock attempts/budgets and exact
schedule pressure. Navigation worker slices share the same envelope; they do not
claim a terminal route or business owner when the submitting context lacks one.

No canonical state/schema, ordering, pool, budget or business policy changes.
The pure kernel reports values through its existing failure-isolated metrics SPI;
only the native diagnostic adapter reads wall time. JFR is bounded by recording
size/duration and retains no game-lifetime sample registry. Disabled tracing
performs no file writes; observers never poll a transaction or advance a route.
Future readiness and owner publication remain separate; queued/advisory work is
not presumed to block canonical advancement.

`tools/performance/SimulationDelayReport.java` streams one recording (maximum
1million custom events/100k tasks, fails explicitly beyond that bound), unions
nested owner spans and partitions covered gaps by their last actual owner gate.
Computed dependencies split queued/running time from result waiting for owner.
Missing dependencies, data loss and recording edges remain explicit. RUNNABLE
unattributed dispatch can include Minecraft, GC or unrelated host work; it is not
proof of a lost wake. Inclusive nested stage costs/parallel worker durations are
reported separately and MUST NOT be added to exclusive wall-gate totals.

Next: verify/deploy diagnostic increment to retained worlds, record ordinary100
without fast-forward/player interference, establish measured blocking map. Only
then choose the connected optimization; terminal/HOT/COLD/restart/5footprints
acceptance remains open. Instrumentation itself is not a product speedup.

Verification: core metrics canonical-byte/noninterference2 checks and native
preparation/readiness/runtime/WAL checks pass with guardrails. Expanded native
navigation focus exposes an EXISTING failure: restartRebindsLostCalculationAndActualServerCompletionWakesWaitingGroup
finishes fixture clock6004 with READY group/one zero-work route instead of
TRAVELLING. Identical isolated failure reproduced on clean d9f297eb9 (baseline
log delay-trace-navigation-baseline.log). It is retained OPEN, not suppressed
or explained as product/harness without further diagnosis. This does not turn
focused diagnostic delivery into full navigation/scale acceptance.


### First measured causal map and confirmed remedy (2026-10-28)

1d2b8b78f clean diagnostic release:11frontier/45native checks, guardrails/package
PASS31s; both retained worlds verified, SHA5125acb24b66924bf7385b584e252dc125c5cb9b08edef9813e26d2b4b81dab01efbdcc539f28d045c48cab2fc86cb9c5bc837bfc8619db7523f0c6487076e71bbe.
Source/pack tracked clean; no client update/reset/push. Existing navigation rebind
failure reproduced unchanged on cleand9f, still OPEN.

`build/simulation-throughput/delay-trace-ordinary100.jfr` (45s,47.1MB) and
`...-report.txt`/`...-cpu.txt`:402130custom events, no JFRDataLoss event; completed
owner interval44.784682s accounts EXACTLY to the nanosecond. Ordinary clock
51624→51687=1.406731ticks/s; debt1511→2350. Window begins after recovery; the
previous observer's final read overlaps its leading edge, no player/fast-forward.

Exclusive wall gates: owner execution20.271566s(45.26%), exhausted work allowance
13.460964s(30.06%), exact-worker execution8.054786s(17.99%), WALexecution1.970926s
(4.40%), exact result waiting owner0.854955s(1.91%). Other/queue gaps<0.4%.
These partition time; inclusive stages/parallel workers MUST NOT be added.
Owner canonical validation8.340142s and schedule allocation2.826561s are nested
attributions. Exact blocking includes actor movement4.252512s and resource hauling
2.060783s. Worker CPU says reserveSelected/assembly still expensive, but is not
itself proof of global blocking.

NEW CONFIRMED structural defect:39057 advice submissions include38787 QUEUE_BUSY;
42439 charged action passes yield only1631 transactions in advance results
(1634 actual PUBLISHED points). Kernel parks pending/full-lane advice with empty
domain wake keys. Any changed owner snapshot restarts the unaddressed polling
round, reclassifies technical waits as business-ready and charges retries against
ordinary action/execution budget. Readiness delivery itself is prompt; do not
raise pools/budgets or optimize WAL first to hide this.

Remedy underway: separate reconstructible technical computation/capacity waits
from domain-keyed holds and their audit/poll rounds. Only bounded in-flight advice
readiness is checked. Capacity waiters remain exact canonical members and share
one fair admitted slot; native host supplies actual capacity/readiness, kernel
rechecks ordinary owner permission and exact revalidation at execution. Domain
invalidation cannot manufacture compute readiness. No timers, fake owner keys,
retry transactions, changed deadlines or second authoritative scheduler. Recovery
reconstructs from canonical actions; worker/ticket state is never persisted.
A still-computing result may finish after its owner becomes held; it has no effect
until completion rechecks that owner and releases/rejects the result. The previous
fixture's repeated polling-charge/immediate-cancel mechanics are intentionally
replaced by non-progress/no-budget and held-owner-before-publication obligations.


### Advisory readiness result and a retained-world recovery defect (2026-10-28)

0bda8df97:75frontier/45native affected checks+guardrails/package PASS36s;
clean detached release PASS29s; retained scale deployment verified with
SHA5126a7faf154fff038f35cbdf7461ebf9e6ed6152f67ff84060d8a1cb75f6ace0cab3719ae758f9c8f6ed881d927454c3b262ff71ebfc8f79cc685c95dbbf67892d.
The main deploy verifier initially returned OK but the subsequent runtime check
caught a recovery quarantine and normal save/exit. That is NOT a successful
ongoing main deployment. No world reset or diagnostic suppression.

`advice-readiness-ordinary100.jfr`/report/cpu:118087custom events, noDataLoss,
44.840548s exactly accounted. Clock53096→53205=2.430836canonicalticks/s,
debt1085→1875: G STILL FAILED. ADVICE QUEUE_BUSY=0 (330 actual admissions),
charged action passes4046 vs42439 in preceding window. BUDGET wall gate0.055328s
(0.12%) vs13.460964s(30.06%). This confirms removal of the retry storm, not full
scale acceptance or a controlled global speed ratio between evolving workloads.
Remaining gates: OWNER53.97% (validation11.185438s nested), EXACT_RUNNING36.17%
(actor movement6.282856s, objective.review4.987001s, stock_reconsider2.685710s),
WAL3.61%, runnable unattributed5.13%, exact-ready0.88%.

Main failure exact chain: ScheduledPlanningRecovery restores due planning waits
by previewing ordinary eligible planners. Harvest preview reaches ToolWorkRate
with equipment that no longer meets maintenance reserve. Saved image1443223/
tick698304 (`main-field-tool-startup-preimage.txt`) names field6/job190/worker6-13,
stone_hoe damage130/131, targetHARVESTED/serviceablefalse. This is a still-existing
tool, NOT destruction or corrupt inventory. After previous work, the selected
operation can change through sowing, skip/retarget, regrowth or batch selection;
checking serviceability only immediately after the previous crop receipt is
insufficient. The ordinary labour-start path had no normal service disposition,
so recovery legitimately hit the same missing branch. Planner/field/tool code
is unchanged between1d2b8b78f and0bda8df97; no inference that parking introduced it.

Remedy: FieldEquipment checks the CURRENT operation before work. Existing field
equipment event gains explicit unused wire tag8 SERVICE_REQUIRED; owner validates
current equipped item, service requirement, execution and absence of pending
physical work, then ends this work offer while preserving cells, quantities and
item. Existing cargo/tool return and job retirement finish normally. COLD preview
and ordinary continuation share that transition; the HOT equipment adapter uses
the same owner before labour. ToolWorkRate remains strict. This is not an exception
catch, repaired inventory, skipped recovery rule or fabricated harvest.


### Repeated validation visits to an immutable snapshot (2026-10-28)

48449362f field-service release39frontier/45native+guardrails/packagePASS30s;
both retainedworld deployments verified, SHA512379d8026d8c2f57538767e7163b021d769deb4684a3e0d0dcb7a53e7bc3d1d7d719dbd26656c7908113090109235ef7e277e7ac9e4ba77de30ebb2ada03c4819.
Main follow-up confirms completed recovery, ordinarytick699128/rev1444914/debt0;
no newquarantine, hostmax50.27ms/recentmax7.05ms. No reset/client/push.

Further analysis of the retained0bda recording (no new campaign):
`advice-readiness-clock-costs.txt` shows926 exact movement preparations/6.460s;
40objective reviews/5.022s,22stock reconsiderations/2.645s;1147canonicalvalidation
spans/11.185s (~9.75ms each). `...-validation-stacks.txt` confirms repeated
TransportGroupMissionPort.validate visits inside group relationship closure,
member execution reads and movement preparation. A successful row check was
memoized, but every repeat still constructed the same complete dependency array
and re-read roster/account/endpoint facts within ONE immutable aggregate.

The EXISTING ImmutableInvariantRegistry now accepts a complete immutable-snapshot
visit scope with lazy dependencies. It retains only a weak snapshot reference and
bounded current registered row declarations, not historical world roots. Another
snapshot still checks the SAME complete declared read set; full ingress bypasses
both layers; withdrawal/reuse/epoch and failures cannot install/reuse false proofs.
Transport composition is the immediate production consumer, with unchanged
validation inputs/body. No root-family check, relationship, authority, invariant,
map/queue limit or recovery check is removed. Focus48checks (including warmed
wrong-kind/foreign-execution/negative/recovery) and guardrails PASS53s.

This removes repeated same-snapshot input construction; native aggregate gain
remains to be measured. It does NOT fix the remaining family-wide traversals or
change COLD group formation. No imaginary overlay-chain defect: current
ImmutableLookupMap is a defensive LinkedHashMap snapshot with hashed lookup,
not a64-deep persistent overlay. Keep future changes tied to actual source.


### Shared immutable preparation results (2026-10-28)

14ac76d91 deployed and verified on both retained worlds (no reset), SHA512
5fe0e7ce3e48a3d97b1272ea2e07c11ae482700d429b9cee5d19672229193c00c5563ea519ff4e152a1a8c8a29e83dbbe4be25b52484eb03ed9feeb30a02746a.
`snapshot-validation-ordinary100.jfr` has89816events/noDataLoss;44.824867s
exactly accounted. Clock60001→60079=1.740106/s, debt1541→2361: G STILL FAILED.
OWNER51.37% (validation9.491580s nested), EXACT_RUNNING44.51%, BUDGET0.10%,
WAL2.62%. Dominant exact kind is stock_reconsider11.449246s, followed by
movement3.693290s. Evolving workloads preclude a controlled aggregate speedup
claim for the snapshot-validation increment. The retry fix remains effective.

Source and CPU samples agree on the expensive stock path: staffing → equipment
acquisition → sourcing offers → transport/fuel/capability valuation. Staffing is
recomputed for the exact same state/home/instant during proposal, its projected
reduction, and final owner reduction. Mutable thread-local planning caches cannot
share completed results with the server owner. The current increment shares only
completed immutable permissions, equipment requests/acquisition, equipment
valuation and observed sourcing facts. It uses the existing thread-safe bounded
ImmutableResultView/BoundedValueMemo via a snapshot wrapper (four exact snapshot
identities,256queries per snapshot per producer). Mutable allocations remain local;
changed state identity/home/tick and eviction recompute normally. Reducers still
validate complete current declarations; there is no new economic policy, queue,
TTL, dropped decision, altered event schema or authority bypass.

Focused verification includes actual worker-prepared policy acceptance and
rejection after living-worker facts change, existing stale/forged policy tests,
equipment and sourcing negatives, and bounded snapshot eviction/failure coverage.
Native throughput gain remains unmeasured until the next capture.


### Preserve unchanged aggregate identity (2026-10-28)

Connected source contradiction: StrategicObjectiveProcess builds decisionState
through withStrategicPlans even when every retained perception/doctrine component
is unchanged. StrategicPlanState already returns itself, but WorldState.withChanges
unconditionally constructs another full world, runs validation/input retention and
invalidates all exact-snapshot-derived views. This defeats reuse inside a single
review independently of cross-thread sharing.

FrontierWorldStateUpdate now exhaustively compares only explicitly supplied
components by identity. An empty patch or exact no-op returns the already validated
world; any distinct candidate, including an equal distinct map, still goes through
the constructor and economic-input retention. Full invariant audits always bypass
this fast path. There is no equality scan, hash validity proof or cached authority.
The existing patch tests cover named replacement, duplicate-writer rejection,
retained no-op root identity and full-audit reconstruction. World/relationship/
reference-closure and staffing affected checks plus guardrails pass in30s.

ad114da60 shared-economics clean release verification passed; both retained
worlds deployed/verified, SHA512e9a9ad45d0c22132046b6190f015974da01748d6e91ef9a95803e03a4666e5a2f7e505075140aea17eb801ddc32feae2f7d0f3bbd67951cf479c0858e876b446.
Main follow-up tick738539/rev1529861/statusok/debt0/recoveryfalse. Private ordinary
capture shared-economics-ordinary100 is running/completing; no measured gain yet.
