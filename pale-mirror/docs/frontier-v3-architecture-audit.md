# Frontier v3 simulation architecture audit

Status: accepted defect register and remediation input.

Current follow-up (2026-09-22): [F0.6R3 static audit and accepted remediation](frontier-v3-static-audit-2026-09-22.md)
records OPEN findings SA-01 through SA-10 against `eaaa6e70` plus preserved WIP.
It covers the farm/production lifecycle and shared scheduling, scene, custody
and reference barriers, not all subsystem internals. Main owns the user-directed
correction without Terra. Shared-owner repairs are accepted; a whole-system
rewrite, weaker invariants and another broad proof campaign are not. Historical
closures below retain their original scope and do not close these new findings.

Audit date: 2026-08-31. Audited revision: `5ca5291`. Scope: the pure v3
kernel/model, persistence codecs, NeoForge lifecycle and physical execution
boundary. This is a static architecture audit; it does not replace the annual,
JFR, restart or player-visible gates in the implementation plan.

Materialization-completeness follow-up: 2026-09-03. This follow-up inspected
the current working implementation of canonical processes, physical executors,
scene/ambient ownership and checked-in pilot assertions. It adds defects
V3-AUD-037 through V3-AUD-040 and the detailed active `MAT-*` inventory in
`docs/frontier-v3-materialization-completeness.md`. It does not revise or weaken
the evidence recorded for earlier causality/recovery closures.

## Execution-model requirements amendment — 2026-09-18

The accepted contract and execution-semantics amendment requires independent
execution dimensions, projection/action/aftermath separation, generation-safe
retirement, bounded changed-set work and inspectable ownership. Mandatory
[ARC-001](work-orders/PM-ARC001-SIMULATION-EXECUTION-ALIGNMENT-01.md), OBS-001
and XACT-001 are accepted foundations at their recorded scope. The current
F0.6R3 fix is followed by OBS-002 and ARC-001E before MAT-006 resumes.

This is a requirements amendment, not a new source audit, discovered-defect
claim or implementation acceptance. ARC-001 maps current families to existing
evidence and genuine gaps, retaining earlier findings and their closure scopes.
It grants no increase to machine-checked debt ceilings and no blanket rerun.

## Classification

Accepted semantics follow-up (2026-09-05):
`frontier-v3-execution-semantics.md` refines the exits for V3-AUD-043 through
V3-AUD-051 and F0.V/F0.1–F0.6. This is a requirements amendment, not a new code
audit or evidence of closure. Its original F0.VA activation timing is consumed;
family coverage remains binding at its recorded scope. Current alignment is
owned by ARC-001, with its owning boundaries and negative/recovery coverage. Required coverage
includes independent physical activity, bounded knowledge/unknown geometry,
causally justified aftermath, cross-front effects, physical/player partial-save
confirmation and repeated-switch neutrality. Retain historical evidence and
existing defect statuses; never raise debt ceilings or reinterpret old endpoint
tests to satisfy these stronger exits.

- **Confirmed defect** means current code contradicts an accepted invariant or
  permits a state/format path that the invariant forbids.
- **Structural debt** means current behavior is correct, but another domain or
  scene cannot be added safely without first changing the shape of the code.
- **Measured risk required** means the code suggests a scale risk, but no
  performance defect is claimed until a reproducible profile proves it.
- Explicit exhaustive dispatch in a sealed wire codec is not itself a defect.
  The defect is using unstable tags or repeating lifecycle policy outside its
  owner.

## Evidence summary

| ID | Severity | Classification | Evidence at audited revision |
|---|---:|---|---|
| V3-AUD-001 | P0 | Confirmed defect | 48 `LogisticsSceneCause`/`SettlementAssaultSceneCause` type tests in 22 main-source files; generic `SceneLease` exposes logistics-only accessors that throw for assault scenes. |
| V3-AUD-002 | P1 | Confirmed defect | `FrontierDevelopmentScenarios` and 15 development configuration entry points are in `src/main`; `FrontierV3ServerLifecycle` can select 13 profiles from JVM properties whose only guard is a nonblank run ID. |
| V3-AUD-003 | P1 | Structural debt | `FrontierWorldRuntimeDefinition` is 947 lines: its command planner has 35 type tests, scheduled planner has 41 string cases, and reducer has 104 cases. |
| V3-AUD-004 | P1 | Structural debt | `FrontierV3ServerLifecycle` manually invokes 22 physical executor `tick` methods in a causally significant source order with no declared phase/dependency contract. |
| V3-AUD-005 | P1 | Structural debt | 38 direct positional `new FrontierWorldState(...)` calls occur in 18 production files; many adjacent arguments have the same `Map` type. |
| V3-AUD-006 | P1 | Confirmed defect | 223 persisted enum encodes/decodes use `ordinal()` or `values()[tag]` in 19 v3 codec files. Reordering or inserting an enum can reinterpret an existing snapshot/WAL without a decoding error. |
| V3-AUD-007 | P1 | Structural debt | Balance and cadence rules such as review intervals, radii, infection gain and COLD step sizes are private process constants and are not identified by a persisted ruleset/version. |
| V3-AUD-008 | P2 | Structural debt | Pilot profile names are repeated in Gradle, lifecycle selection and scenario tooling rather than owned by one test-only catalog. |
| V3-AUD-009 | P2 | Measured risk required | Most aggregate transitions copy immutable maps and most non-infection transactions run the complete cross-domain audit. Correctness is strong, but allocation and validation cost at complete Wave 5/6 load is not yet measured. |
| V3-AUD-010 | P2 | Measured risk required | One globally ordered due-action queue and one physical tick budget have bounded work, but there is no complete-domain pressure/fairness proof for simultaneous fronts and hundreds of bodies. |
| V3-AUD-011 | P1 | Structural debt | The stable interface defines separate `model`, `process` and `persistence` ownership, but 33 `*Process` and 23 `*Codec(s)` source files currently reside in `frontier.v3.model`. |
| V3-AUD-012 | P1 | Confirmed defect | `FrontierPayload` is open and `FrontierWorldPayloadCodecs.create()` manually enumerates codecs; no gate proves every process input/output payload has a wire codec. This mechanism already omitted `ResourceSiteHarvestObservation` once. |

## Follow-up finding during closure

| ID | Severity | Classification | Evidence discovered during remediation |
|---|---:|---|---|
| V3-AUD-013 | P1 | Confirmed defect | Seven v3 GameTest calls used `ServerLevel.getChunkAt` to force-load canonical scene/field chunks; two test-only v3 helpers also remained in `src/main` and were not rejected by packaged-JAR verification. A GameTest level is intentionally placed outside the finite canonical 1024×1024 world, so treating it as a real remote-scene integration world either forces chunks or creates invalid canonical positions. |
| V3-AUD-014 | P1 | Confirmed defect | The due-action queue had a per-tick admission budget but no maximum retained future-work cardinality. A malformed or unexpectedly prolific process could therefore retain unbounded scheduled work despite the bounded-runtime invariant. |
| V3-AUD-015 | P1 | Confirmed defect | A real r41 restart exposed that the codec recognized a pre-ruleset snapshot's named legacy ruleset only after the lifecycle had already pinned current production rules. The resulting bootstrap mismatch correctly quarantined the world, but made a supported persisted world undeployable. |
| V3-AUD-016 | P1 | Confirmed defect | After V3-AUD-015 selected r41's legacy bootstrap correctly, the same real snapshot exposed an internally inconsistent schema-79 lineage: its raw strategic ordinal `8` retained a field target and therefore meant harvest in the pre-assault layout, while the current stable decoder read it as route-bypass construction. Quarantine preserved the world but made recovery incomplete. |
| V3-AUD-017 | CLOSED | `8797424b` | Schema 83 embeds one immutable exact `RouteUnitManifest` inside each `RoutePatrol` and `RouteOperation`; new patrols retain a leader plus one-to-three scouts, while cargo operations retain distinct crew plus two-to-four escorts. Schema-82 snapshots and historical patrol WAL bytes hydrate only their explicit understrength legacy members. Assignment, COLD formation, death/loss, snapshot/WAL and native HOT unload/reload evidence retain the same people and cargo custody; the critical gate passed 251/251 GameTests. |
| V3-AUD-018 | P1 | Confirmed defect | A real retained-r41 restart left ambient HOT leases `UNKNOWN_AFTER_RESTART`. In a naturally loaded actor column with no saved exact UUID, the executor retained UNKNOWN forever instead of recording the loaded negative postcondition and returning the unchanged canonical actor to ordinary materialization. This made a living world appear empty. |
| V3-AUD-019 | P1 | Structural debt | Movement corridors and several validation rules are horizontally adjacent at one Y level; `BlockPosition` still mixes semantic floor/support and feet-air meanings; current facility ports derive compass-specific coordinate offsets; HOT controlled motion steers only in X/Z. The flat graybox works, but another terrain provider cannot preserve the same HOT/COLD cursor across slopes, entrances and future rail grades. |
| V3-AUD-020 | P1 | Confirmed defect | The HOT logistics assembly executor could let a following body approach a cursor currently occupied by another retained member. On observed arrival it constructed the invalid overlapping `OperationAssembly` before command validation, throwing on the server thread and quarantining a live r44 world. Engineering assembly used the same recursive notion of a move being safe if its leader might later move, although one accepted command advances only one exact person. |
| V3-AUD-021 | P1 | Confirmed defect | A live r51 JFR found the ordinary graybox tick scanning and sorting the whole bounded provenance ledger merely to locate temporary worksite staging, while player boards copied and structurally compared every actor condition on motion-only canonical revisions. Both violate the v3 no-world-wide-per-tick-scan contract and become scale risks before a large physical scene is reached. |
| V3-AUD-022 | P1 | Confirmed defect | A route `ROUTE_FOUNDATION` loss blocks the retained edge correctly, but the generic `STRUCTURAL_REPAIR` executor requires the damaged cell and the remote maintenance chest to be naturally loaded in the same turn. Admitting route losses there would make repair impossible at ordinary view distances or invite force-loading; the existing bypass project cannot repair a claimed baseline cell or reopen its original edge. |
| V3-AUD-023 | P1 | Confirmed defect | Terminal logistics removed a delivered operation/contract pair while retaining its confirmed `CARGO_HANDOFF` physical receipt. Aggregate validation correctly resolves that receipt through its live route owner, so the next state validation quarantined the otherwise successful world with `physical observation does not match its route operation`. |
| V3-AUD-024 | P1 | Confirmed defect | A managed ambient body can enter the Minecraft level with `isAddedToLevel()` true before `ServerLevel#getEntity(UUID)` publishes it. The join-to-index bridge discarded that exact body on the weaker flag/20-tick timeout, so the next materialization turn could attempt a second deterministic UUID. Live r52 logs reproduced duplicate resident UUID warnings during ordinary chunk entry. |
| V3-AUD-025 | P1 | Confirmed defect | A live r53 JFR proves the graybox route compiler repeatedly constructs the full route-surface set in `FrontierRouteNetwork.surfaceCells`: once redundantly while deriving route foundations, and again whenever a readability-board contamination view recompiles the complete plan merely to inspect buildings/organs. The same live entry logged 2.2 s/2.1 s server backlog warnings; the capture excludes a comparably long GC pause, so route-plan churn is a confirmed hot source although the exact share of each stall remains unmeasured. |
| V3-AUD-026 | P1 | Confirmed defect | Despite v3 launch ownership, broad NeoForge callbacks still constructed `SourceGrayboxRuntime`. Loading a v3 graybox chunk therefore deserialized/ran `frontier.reference` (v2) code; r54 JFR sampled `ReferenceV2State`/`ReferenceGrayboxProjection` during the 2.384 s player-entry backlog. This violates the frozen-v2/one-physical-writer invariant and makes an entry hitch indistinguishable from v3 work. |
| V3-AUD-027 | P1 | Confirmed defect | After the v2 exclusion, fresh r55 west-hive entry still logged 2.522 s/50 ticks behind. Its JFR contains repeated `FrontierWorldStateCodec.encode`/`decode` samples on the server thread during the stall: each physical adapter read or successful command invalidated the runtime checkpoint cache, then rebuilt a complete persistence snapshot only to inspect the current state/revision/instant. The physical read path therefore scaled with complete-world serialization rather than its bounded work set. |
| V3-AUD-028 | P1 | Confirmed defect | `ResourceSiteHarvestProcess.plan` emitted `ResourceSiteHarvested` and stored wheat in canonical inventory before any physical intent, while the already-present `RESOURCE_SITE_HARVEST` executor required a loaded mature field and exact active depot chest. A disposable Redwillow run observed the phantom stack briefly, then the strict container reconciler correctly reported `CONFLICT` because Minecraft never contained it. |
| V3-AUD-029 | P1 | Confirmed defect | The equipment issue and return executors selected the first globally sorted pending intent. A naturally unloaded earlier chest/body therefore deferred every later loaded hand-off in the same family. The native route-maintenance restart scenario repaired its target but retained `RETURN_INTENT_PENDING` until timeout, so the exact engineering team and route operation could never compact. |
| V3-AUD-030 | P1 | Confirmed defect | The engineering return model creates an instant `EQUIPMENT_RETURN` from the worksite actor hand to the settlement depot. In the disposable native proof the same engineer was physically at `(-381, 65, -305)` while the owned depot was around `(-404, 66, 250)`: no ordinary player view can make that one physical exchange coherent. The retry after V3-AUD-029 still timed out at `RETURN_INTENT_PENDING`, proving the missing return journey rather than a queue policy is the terminal route-maintenance blocker. |
| V3-AUD-031 | P1 | Confirmed defect | `FrontierV3RouteMaintenanceExecutor` still selected its first sorted pending material-loading intent directly. One naturally unloaded earlier maintenance depot could therefore block every later loaded route repair, even though those operations have disjoint exact source, cargo, target and team owners. Its invalid entries also returned without a durable visible conflict, making the queue both unfair and potentially permanently stalled. |
| V3-AUD-032 | P1 | Confirmed defect | `RouteMaintenanceStateSupport` permits 12 distinct repair aggregates and the maintenance contract permits one operation per repair cell, but `RouteMaintenanceProcess.plan` advanced only the first global `BUILDING`/`READY` entry and admitted no candidate while any entry existed. A remote or under-supplied first repair could therefore suppress every other observed route loss in the whole 1024×1024 world. The same serialized flow made V3-AUD-031's honest two-maintenance native proof unreachable. |
| V3-AUD-033 | P1 | Confirmed defect | The first cocoon lifecycle cut allowed COLD assault/engagement planners and development fixtures to move a dormant exact bioform directly. This violates custody, can create a canonical body outside its owned cocoon and lets a strategic planner silently mobilize a sleeper. |
| V3-AUD-034 | P1 | Confirmed defect | The new task-driven cocoon release executor could persist `RELEASING` on ordinary player demand before the bounded graybox projector had claimed and placed that exact cocoon. Its next tick then correctly found no owned block and durably conflicted the operation, turning normal first materialization into a false `COCOON_CHANGED` outcome. The pure semantic baseline also described a cocoon as `HIVE_HIBERNACULUM` while projection used `HIVE_COCOON`. |
| V3-AUD-035 | P2 | Confirmed defect | Four checked-in development fixtures advanced the normal engine through up to 12,000 ticks, then decoded every sampled checkpoint with an unpinned generic codec. Each decode reconstructed the same immutable twelve-settlement bootstrap, including full structure occupancy and access-port validation. A complete gate therefore spent minutes repeatedly compiling already-proven genesis geometry and obscured the real route-planning regression. |
| V3-AUD-036 | P1 | Confirmed defect | `HiveRouteEngagementProcess.attackers` directly selects active bomber/defender bodies by distance and `RouteEngagement` persists no Relay-coverage fact or exact Overseer. Consequently a remote coordinated interception can begin without the mobile controller required by the accepted hive physiology contract, and neither controller loss nor recovery has an owner. |
| V3-AUD-037 | P0 | IN PROGRESS | Resource-site preparation/harvest, production transformation, generic structural repair and decontamination began as strict exact-item/block endpoint executors without a retained physical actuator for duration-bearing work. Harvest's schema-114 named-worker topology/cursor and one-cell HOT frontier have normal, negative, restart and native visible evidence: `disposable_redwillow_harvest` run `5110c424-f5b0-4321-894e-a6fdec3b7f36` restarts during the same active harvest and reaches its exact terminal receipt. Production now also owns a class-C `PRODUCTION_WORK` scene with its exact industrial worker, immutable workshop topology, distinct input/work stations and durable staged progress; its native restart proof reaches the exact receipt. It remains M2-partial until the real HOT GameTest covers approach/input/processing and worker/input/station/blocked/restart recovery. Generic structural repair and decontamination remain actorless, so this cross-class P0 finding remains open. |
| V3-AUD-038 | P0 | Closed at M2; M3 remains separate | MAT-004 is closed at M2 on `aa12f919`; MAT-005 is closed at M2 on `741d4dda`. Exact patrol and Overseer-led expedition rosters now retain one formation/topology/cursor across COLD/HOT movement, demand loss, typed obstruction/loss and restart, and the expedition continues through contact and owned retreat/return rather than ambient motion or disconnected endpoints. Natural-terrain and unbriefed-player readability remain M3/release gates, not this architecture defect. |
| V3-AUD-039 | P0 | Confirmed defect | Settlement provisioning changes exact recipients after aggregate depot consumption, birth creates a resident after a food receipt/delay, hive nutrient transfer advances a COLD corridor between endpoint chests and hive growth atomically adds organ/bioform/infection state. None retains the loaded feeding, household/birth, vascular-flow or morphogenesis stages required by its own world meaning. Exact resident health/nutrition also has no ordinary HOT symptom/recovery presentation or exact physical-contact proof. |
| V3-AUD-040 | P1 | Confirmed defect | Existing acceptance frequently proves only terminal diagnostics and before/after frames. Crop/infection projection batch-replaces a complete aggregate stage, and scenarios for harvest/hive transfer/growth assert endpoint state without an in-progress physical actor/frontier. The evidence model did not distinguish canonical correctness, physical endpoint, continuous HOT execution and unbriefed player comprehension. |
| V3-AUD-041 | P0 | Confirmed defect | The initial `SettlementServiceWork` foundation retained only a worker-to-worksite topology while its exact reagent remained in a depot slot. Any subsequent executor would have had to consume remotely, create an untracked pickup, or silently place the item in the worker hand. |
| V3-AUD-042 | P0 | Confirmed defect | Live disposable r69 proved a production terminal-ordering violation: a COLD completion could prepare `PRODUCTION_TRANSFORMATION` after its exact worker reached `OUTPUT_READY` but before the same `PRODUCTION_WORK` lease released. Confirmation then removed the job while the non-closed lease still required it, causing `NullPointerException: production-work scene job`; the runtime correctly quarantined at 11:15:30 rather than accept an unowned HOT body. |
| V3-AUD-043 | P0 | Confirmed defect; replacement contract accepted | The resource-harvest implementation treats the HOT scene as the only meaningful work driver. A native unload/return probe can close the lease while retaining the harvest reservation and canonical work, then fail to rematerialize its exact farmer; off-screen progress and arrival-mid-process continuity are not established. This makes player demand part of starting/continuing work and splits authority among job cursor, actor location and lease recovery position. The accepted replacement makes every scene only an atomic physical-execution lease over one process-owned cursor, with paired COLD/HOT drivers and no demand-created operation. |
| V3-AUD-044 | P0 | Confirmed defect; replacement contract accepted | `ContainerSurfaceStatus.ACTIVE` is irreversible and process code selects COLD versus physical economy from that historical state. Once a depot or hive store has been visited, later viable work can wait for its unloaded chest, so observation changes future liveness. FND-01 separates persistent replica evidence from temporary physical custody. |
| V3-AUD-045 | P0 | Confirmed contract defect; replacement accepted | The physical-effect contract lets a durable effect remain pending until a player visits its origin. Used as a process prerequisite, this makes player demand the clock for construction, repair, consumption or destruction. FND-02 commits known semantic consequences COLD and retains bounded deferred physical aftermath without force-loading or replay. |
| V3-AUD-046 | P0 | Confirmed defect; replacement contract accepted | One permanent ID per ordinary Minecraft stack cannot represent Vanilla split/merge: a split duplicates the tag and a merge of distinct tags is rejected or becomes foreign/duplicate evidence. FND-03 retains exact unit quantities and stable allocations while making physical stack bindings temporary. |
| V3-AUD-047 | P0 | Confirmed contract/scale defect; replacement accepted | The contract assigns one scene lease to a complete battle/operation while `SceneLease` accepts at most 32 members. Raising the cap conflicts with bounded physical work and cannot support exact hundred-body operations. FND-04 partitions one exact strategic operation into disjoint bounded spatial fronts, each with its own local lease. |
| V3-AUD-048 | P0 | Confirmed product-architecture defect; replacement accepted | The common HOT actuator disables AI, stops navigation and attempts only one direct vector with no sidestep. Exact cell ownership is useful graybox evidence but cannot deliver living role-aware movement across doors, slopes, local avoidance and combat. FND-05 gives the canon semantic checkpoints/envelopes and a HOT provider bounded local navigation. |
| V3-AUD-049 | P0 | Confirmed autonomy/recovery defect; replacement accepted | After restart, an unloaded `DRAINING`/unknown projection can retain physical authority until that location is naturally inspected. A never-revisited chunk can therefore freeze its exact actor or operation indefinitely. FND-06 uses durable authority epochs and stale-projection fencing while retaining separate postcondition inspection for ambiguous non-replayable effects. |
| V3-AUD-050 | P0 | Confirmed failure-model defect; replacement accepted | Expected gameplay actions and ambiguous reconciliation frequently converge on terminal `CONFLICT`/blocked paths designed for invariant safety. Without a generic owning response, ordinary death, theft, obstruction or partial transfer accumulates frozen work. FND-07 separates domain disruption, isolated reconciliation ambiguity and frontier-corrupting invariant failure. |
| V3-AUD-051 | P1 | Underspecified product contract; correction accepted | “Same truth and fidelity” does not define whether neutral HOT and COLD combat/work require identical outcomes or only equivalent rules. Exact equality scripts HOT physics; unconstrained divergence makes observation alter history. FND-08 defines identity/conservation equality plus fixed-seed calibrated outcome tolerances and observer-neutrality gates. |
| V3-AUD-052 | P0 | Confirmed defect | Six physical scene executors asked their support for one globally sorted ready candidate and only then tested player demand. An unloaded earlier field, workshop, service work, patrol, treatment or engineering site could therefore suppress a later naturally demanded process. Fresh F0.V `arrival_checkpoint_one` evidence reached the exact fourth harvest job's retained COLD cursor but could never grant its HOT lease because another site sorted first. |
| V3-AUD-053 | P0 | Confirmed defect | The revision-5 candidate correctly kept the traversal-only harvest effect intent `PREPARED`, but guarded the HOT semantic traversal checkpoint with the disabled irreversible-crop capability. The physical worker can therefore move while the process-owned cursor and due continuation cannot advance, contradicting the paired-driver F0.V/F0.1 contract. |
| V3-AUD-054 | P1 | Confirmed test-lifecycle defect | Fresh r70 and isolated r71 reached a valid `arrival_checkpoint_two` terminal state, then the non-restart runner requested RCON stop without an authenticated `client_normally_disconnected` barrier or ordinary post-evidence demand-loss hand-off. r71 remained server-thread `RUNNABLE` in `ChunkMap.processUnloads -> MinecraftServer.stopServer` beyond 90 seconds and emitted no durable-save receipt; r69 proves the receipt path itself works on a correctly bounded graceful restart. |
| V3-AUD-055 | P1 | Confirmed lifecycle/platform risk | F0.2B run `600057` completed ordinary client disconnect and demand-loss release, then stopped world I/O while the server thread consumed almost one core recursively rescheduling one not-ready holder through `ChunkMap.scheduleUnload -> processUnloads`; late world digests and write counters were stable and worldgen/I/O workers were parked. The fresh-world recovery carrier had requested stop about 4.48 seconds after a view-distance-8 teleport and proved only its target chunk ready, not surrounding generation quiescence. This invalidates that carrier and exposes a separate unclosed production risk for graceful stop during real natural generation; the exact originating holder/mod is not yet attributed. |
| V3-AUD-056 | P1 | Confirmed player-visible execution defect | In the accepted F0.5 deployment a human observes the active farmer advance approximately one block and then wait several seconds before the next block. Exact route/cursor ownership remains intact, but the presentation exposes the coarse semantic-step cadence instead of continuous HOT Minecraft locomotion. FND-05 already assigns sub-block pose/facing/velocity and moment-to-moment movement to the reusable local-navigation provider; this is a current-runtime/product conformance gap, not a request to accelerate canonical labor or special-case one farmer. |
| V3-AUD-057 | P1 | Confirmed player-visible process-liveness defect | A second human observation in the accepted F0.5 world finds the resident farmer stationary on the first field cell, no crop cell processed and the field indefinitely reporting harvest in progress. The healthy live runtime records harvest handoff and HOT admission followed by release without a correlated harvest progress/effect/confirmation event, consistent with the report and with no quarantine explaining the stop. MAT-001/process ownership requires the exact HOT worker to make meaningful progress on the same canonical job or expose a typed owned reason why it cannot; an active label and a materialized body are not progress. |
| V3-AUD-058 | P0 | Confirmed observer-neutrality/first-visibility defect | In the accepted F0.6R1 live world, after about fifteen hours of server uptime, first natural visits to settlements 2, 3, 4, 8 and 12 each admit their exact field-1 harvest lease at `crop-0`; several of those same jobs then advance to later crop cursors only while HOT. The repeated cross-settlement zero-start shows that first player demand starts or resets meaningful harvest progress instead of materializing current COLD work. This contradicts the autonomous-world, paired-driver and first-visibility contracts; it is not a cosmetic field-projection issue or a request to force-load unvisited chunks. |
| V3-AUD-059 | P0 | Confirmed hive first-visibility defect | On first arrival at a hive in the accepted F0.6R1 deployment, the user sees organ labels suspended over absent organ structures; only cocoons and their mobs are materially visible. The same live session's ordinary breaks are attributed to exact `organ:west-hibernaculum-*` and `organ:west-brood` cells, so canonical/provenance ownership exists but is not presented as coherent physical geometry. A label is not materialization, and a cocoon-only shell cannot satisfy the exact organ footprint/semantic-part or first-visibility contracts. |
| V3-AUD-060 | P0 | Confirmed HOT physical-embodiment defect | Managed mobs in the accepted F0.6R1 deployment can remain suspended one block above the visible surface and continue hovering at the same Y after the player removes blocks beneath them. HOT bodies are required to be ordinary colliding Minecraft entities whose physical outcomes become canonical observations; retained positions and disabled goal AI may not disable gravity, cancel a legitimate fall each tick or make a body immune to support loss. |
| V3-AUD-061 | P0 | Confirmed live first-visibility boundedness defect | Exact candidate `aaa0f047` passed its native and 327-GameTest gates, but the fresh deployed server hung during the user's first ordinary travel and was killed by the watchdog at 09:36:30. The user independently observed TPS degradation before the watchdog. The authoritative server-thread stack is inside `FrontierV3GrayboxExecutor.retainSiblingHiveVisibility -> projectPendingFirstVisibility -> tick`; the crash reports one player and 2,160 loaded graybox chunks. First-visibility reconciliation may not rescan or cross-correlate an unbounded resident-chunk/plan population in one physical tick. Removing only the terminal crash is insufficient if ordinary ingress still creates visible tick stalls. The service disappeared and port 25565 closed, so startup and a prior deploy-verifier pass do not establish operational readiness. |
| V3-AUD-062 | P0 | Confirmed architecture/evidence dependency defect | The F0.6R3 exact-actor correction changes the shared live-body/unloaded-carrier boundary used by earlier work, service, patrol and hive-operation materialization. Existing documentation had no closed impact classification or composition gate, so local M2 receipts could remain labelled fully accepted after a shared dependency changed, or trigger an equally wasteful blanket rerun. Before MAT-006, inventory every production body creator/adopter, mechanically reject carrier bypass and mark affected evidence `ACCEPTED_UNCHANGED`, `NEEDS_TARGETED_REVALIDATION`, `PROVEN_NARROWER` or `REJECTED`, followed only by representative dependency-driven lifecycle evidence. |

## Remediation status

The evidence table above remains the immutable record of revision `5ca5291`.
This table records only completed corrections and the next owned work; a
finding is not silently removed merely because its ceiling no longer grows.

| ID | Status | Evidence / next owner |
|---|---|---|
| V3-AUD-001 | CLOSED | `e9bc0d0`: closed pure and NeoForge `SceneBehavior` registries own every generic lifecycle decision; duplicate/missing registration is rejected. The focused scene slice passes 30/30 and the complete critical gate passes 250/250. |
| V3-AUD-002 | CLOSED | Fixture builders/catalog are test-fixtures only; a pilot-only bootstrap selects the already-built configuration before the normal lifecycle. Production bootstrap is property-independent, and JAR verification rejects every former fixture entry point, catalog and bootstrap. The catalog/lifecycle negative tests, pilot smoke and complete critical gate pass. |
| V3-AUD-008 | CLOSED | One test-only declarative properties catalog is parsed and validated by Java, Gradle and the Node pilot. Unknown/duplicate profiles fail before a scenario starts; Gradle no longer owns a copied profile list. |
| V3-AUD-003 | CLOSED | `FrontierWorldRuntimeDefinition` remains a 62-line `runtime` composition root. Exact command/event ownership is resolved by the deterministic registry and dispatched only to one of nine closed executable domain modules; missing executable module ownership fails during catalog initialization. The former 34 trusted-command branches and 104 reducer cases now live with their physical, ambient, logistics, population, economy, resource-site, hive, infrastructure and strategy owners; the facades are 33 and 16 lines respectively. The debt ratchet sets their branch ceilings to zero. Focused pure coverage and the full critical gate pass (248/248 GameTests, build and packaged-JAR verification). |
| V3-AUD-004 | CLOSED | The server lifecycle now invokes one closed staged registry. Every executor has a stable ID, explicit non-ordinal stage, dependencies, exclusive writes and a one-invocation bound; pure cycle/duplicate/reversed-causality tests and the NeoForge scene slice prove admission. Read-only `execution` diagnostics expose the final order. The manual-call ceiling is 0. |
| V3-AUD-011 | CLOSED | The composition root is in `runtime`; all 33 behavioural processes, the command planner, reducer and process catalog reside in `process`; all 23 snapshot/WAL codecs reside in `persistence`. `model` has zero `*Process`/`*Codec(s)` source files and a zero-tolerance source guard against imports from `runtime`, `process` or `persistence`. Processes consume public immutable model state and named state transitions; the move uncovered a Scout-observation regression, covered by retaining the observation's durable sensor position rather than validating against a subsequently moved Scout. Focused pure recovery tests and the full critical gate pass (248/248 GameTests, build and packaged-JAR verification). |
| V3-AUD-012 | CLOSED | Every process now declares individual emitted wire type IDs rather than inheriting a domain union; the new ratchet rejects the former domain-emission fallback. Persistence separately maps each of the ten process owners to its codecs, while runtime composition rejects a missing/duplicate owner, descriptor/persistence disagreement, missing codec, missing reducer or undeclared emission before an engine starts. Negative composition coverage includes omitted codec, missing reducer, duplicate scheduled owner and persistence-owner disagreement. One representative payload from every owner round-trips directly and together through one WAL transaction envelope. The complete critical gate passes: `guardrails`, `check`, NeoForge build/package verification and 248/248 GameTests. |
| V3-AUD-005 | CLOSED | `FrontierWorldStateUpdate` is the sole typed named replacement set for process/support transitions: duplicate declarations fail before aggregate construction, and every undeclared component retains the identical previous object. A declared no-op remains legal for revalidation. The source ratchet now permits full construction only in the aggregate, fresh bootstrap and versioned hydration (38 sites → 14); property coverage proves each unrelated component retains identity. |
| V3-AUD-006 | CLOSED | Every snapshot/WAL codec now uses explicit `tag → enum value` pairs in `FrontierWireTags`, never enum declaration order; unknown tags fail closed. The historic company-registration byte fixture plus stable-tag tests cover retained tags, and debt ratchets reject codec `ordinal()`/`values()[tag]` use and positional registry derivation. |
| V3-AUD-007 | CLOSED | `FrontierRuleset` is immutable hashed world data. The bootstrap manifest and snapshot header retain its ID/schema/content hash, while recovery resolves exactly that installed selector or fails before hydration. Canonical processes and physical executors obtain cadence, radii, COLD strides, gains, economic rates, structure capacity and COLD combat output from the bootstrap ruleset; the source ratchet rejects private process tuning constants. Current test fixtures explicitly declare `ruleset=production` in their sole catalog and fail if their resulting bootstrap differs. Pre-ruleset snapshots select a named legacy anchor rather than the current default. Focused deterministic, manifest, fail-closed recovery, physical decontamination recovery and domain regression tests pass; the complete critical gate passes (`guardrails`, `check`, build/package and 248/248 GameTests). |
| V3-AUD-009 | CLOSED | `FrontierExecutionMetrics` observes command/schedule planning, allocation, reduction, validation, transactions and every staged physical executor without entering the canonical state, WAL or snapshot. The fixed seed-41 live JFR route completes a naturally loaded 27-member settlement assault with no forced chunks: server-tick p95 is 5.912 ms, p99 is 16.773 ms, the longest actual GC pause is 26.885 ms, and the bounded read-only performance diagnostic reports zero dropped attributions. The first profile found repeated full graybox/slot compilation in ambient reservation; the indexed exact reservation set removes that churn without changing canonical outcomes. |
| V3-AUD-010 | CLOSED | `frontierV3ScaleAudit` runs the same seed twice and proves one-action-per-tick total order, 12 simultaneous ordinary field fronts, 434→435 exact bodies, hive interception/assault decisions, maximum queue depth 86, maximum lag 30 ticks, retained-WAL replay and one identical SHA-256 checkpoint (`14849b68b9ca7d8192989d95bce48f9de24ab366c8016058ace89ea51455758b`). The physical JFR route deliberately proves the current 27-member naturally loaded scene limit, not an invented force-loaded twelve-scene claim. |
| V3-AUD-013 | CLOSED | Test-only fixture helpers and the resource-site GameTest now live only on the pilot source set; `FrontierV3GameTestSceneLeases` and `FrontierV3ResourceSiteGameTests` are rejected from the production JAR. The fast debt validator enforces zero `getChunkAt` calls in every v3 GameTest. The focused scene slice passes 28/28. Fresh visible native runs on `DISPLAY=:0` passed both canonical-coordinate flows with a graceful restart: `disposable_hot_scout_sighting_restart` retained the exact observed carrier/intercept operation after its scene closed, while `disposable_settlement_assault_restart` reloaded a 27-member assault with its confirmed strike and durable ownership. These scenarios, not an out-of-bounds GameTest surrogate, prove physical canonical coordinates. |
| V3-AUD-014 | CLOSED | `EngineLimits.maxPendingSchedules` bounds bootstrap, transaction overlays and recovered schedules before canonical engine installation, WAL append or canonical mutation. Over-cap work quarantines visibly without consuming the due action; bootstrap and recovery reject before engine install. The debt ratchet rejects removal of every guard, focused negative/recovery tests pass, and the 12-settlement pressure route remains at 302 pending schedule keys and depth 86 under the production 4,096 safety capacity. |
| V3-AUD-015 | CLOSED | Recovery now reads only the verified snapshot header before engine construction, selects the exact installed persisted ruleset, then proves world ID/seed and pins that bootstrap. Foreign headers and unavailable selectors remain visibly fail-closed. The critical gate passed with 248/248 GameTests, and the deployed r41 world restarted and replayed on hosted SHA-512 `61e5d3c0…c71c1d0` without quarantine. |
| V3-AUD-016 | CLOSED | Versioned strategic hydration recognizes the pre-assault r41 ordinal layout only from its unambiguous raw field-target evidence, uses explicit objective/task maps and retains the normal schema-78/79 assault layout otherwise. An old-byte regression and full critical gate pass; the same deployed r41 snapshot and WAL replayed on SHA-512 `61e5d3c0…c71c1d0`, reached `Frontier v3 runtime started`, and remained healthy on port 25565. |
| V3-AUD-017 | CLOSED | `RoutePatrol` and `RouteOperation` now solely own one immutable exact `RouteUnitManifest`, with stable explicit duty tags and no global roster. New patrols retain one leader plus one to three scouts; new cargo operations retain one logistic crew member plus two to four separately named escorts. Schema-82 snapshot and historical WAL patrol bytes decode only to explicit understrength legacy manifests, never a fabricated replacement. Focused admission, assignment, COLD formation, death/loss and byte-recovery tests pass. Native HOT cargo and route-return scenarios prove the exact named three-person caravan before and after ordinary unload/reload. |
| V3-AUD-018 | CLOSED | `AmbientLeaseRestartAbsenceObserved` is the sole typed evidence for a naturally loaded exact hand-off column that lacks its expected UUID after restart. It validates UNKNOWN status plus exact canonical/lease position, closes only that physical lease and never infers death or moves the actor; the ordinary next demand prepares the same deterministic UUID. Pure normal/forged evidence tests and the 29/29 HOT/COLD GameTest slice pass. |
| V3-AUD-019 | IN PROGRESS | T0.1/T0.2 now carry `BodyPosition` through every current scene family: ambient-to-scene captures, scene release, and HOT death evidence cannot use a support cell. Fresh schema-102 and persistence-envelope v11 reject all previous bytes before hydration/WAL replay. T0.3 now pins one bounded immutable `TerrainSurfacePlan` (baseline plus sparse surveyed support columns) in the bootstrap identity and snapshot header; it is never a loaded-world height query. Raised route surfaces compile their own `ROUTE_FOUNDATION` cells from that survey, and observed foundation loss blocks the same retained edge instead of allowing a hidden bypass or a cosmetic repair. Settlement bootstrap now derives one site datum from exact structure and public-approach survey columns; every lower structure or public-access column gets a plan-owned vertical foundation rather than a floating deck, while fixed-grid route junctions use the same survey instead of `Y=64`. Each settlement now additionally derives a bounded capacity-sized resident apron on that finished datum, exact home slots, a Hall-port connector and one grade-checked retained natural ingress; it fails closed when no surveyed endpoint is reachable in bound. Resource-site compilation now uses finite farm-relative candidate sides and reserves each exact 64-crop/64-soil/four-water field against all immutable structures, organs, routes, circulation, ingress/foundation cells and earlier fields before it chooses a location. Settlement-assault compilation and lease admission consume that same derived local envelope, so a valid planned perimeter resident cannot be rejected by an independent legacy radius. Pure non-flat/recovery and unreachable-ingress regressions, a 41/41 physical GameTest slice, and native scoped `COMPILED → SETTLED → RELOADED` evidence cover this plan without a load or height fallback. `OperationTravel` and every `OperationAssembly` member retain exact topology/cursors, and the registered provider proves the support-to-feet boundary. T0.5 has native `COMPILED`, scoped `SETTLED` and scoped graceful-`RELOADED` Foundry evidence: fresh unclaimed expected air is explicit pending projection work, while exact plan/ledger/block provenance remains required for current materialization and all other drift stays an error. Runtime Foundry reports one semantic port as `OPEN`/`BLOCKED`/`UNVERIFIED` and each retained edge as `OPEN`/`PENDING`/`BLOCKED`/`UNVERIFIED`; the native player block/restart proof keeps the same Infirmary port `BLOCKED` after recovery without an inferred alternative entrance. The debt remains open for bridge/rail-grade evidence, an exact foundation repair/replan lifecycle and cross-family HOT/COLD continuity. |
| V3-AUD-020 | CLOSED | The one exact assembly rule now means immediate occupancy, not a speculative future vacancy: a follower waits until the currently occupying member has durably advanced. `OperationAssembly` owns the bounded safe-cursor selection and single-member advance; COLD and HOT logistics both consume it before movement and before observed arrival. Engineering assembly uses the same immediate rule and rejects a direct unsafe advance. A focused queue/negative regression proves leader-first hand-off without overlapping canonical locations; the complete critical gate is required before deployment. |
| V3-AUD-021 | CLOSED | `FrontierV3GrayboxLedger` now retains a derived sorted index only for the bounded `WORKSITE_STAGING` claim family, rebuilt from durable provenance on load and updated only on exact claim/retire transitions; ordinary retirement no longer scans structural claims. `FrontierReadabilityPlan.ActorConditionView` compares only exact vitality/health, retaining actor-location maps without copying a second map, so motion-only HOT revisions retain the board cursor while injury/death invalidates it. Focused pure equality coverage plus a reload/retirement GameTest prove the noncanonical indexes do not grant write authority or survive independently of the ledger. |
| V3-AUD-022 | CLOSED | One bounded `RouteMaintenance` aggregate owns exactly one PM route surface/foundation loss, one local team, one optional exact cargo unit and one COLD/HOT worksite; it is neither structural repair nor a bypass alias. The registered chain keeps source extraction and target repair independently naturally demanded, blocks foreign/unclaimed source or target adoption, records only exact loss/cargo receipts, and reopens only edges no longer affected by retained losses. Native runs cover restart after pickup (`19d96775-65e4-4d4c-874f-45a3dac1e6fb`), restart after repair/return (`d8e7ef3d-196a-4cde-94c1-31a541dabf42`) and a cold-source fairness path (`5bc82e07-68cb-4db4-babb-7181168c938b`) without a force-load. Snapshot recovery accepts only exact source/target postconditions; forged or absent evidence preserves source/cargo/loss and conflicts only its aggregate. A route-maintenance GameTest additionally proves a copied exact ID on a different Minecraft item kind cannot confirm the one-unit decrement; the shared predicate protects route construction too. Focused HOT/COLD slice passes 41/41 and the critical gate passes guardrails/check, build/package verification and 273/273 GameTests. |
| V3-AUD-023 | CLOSED | The terminal-logistics owner now retires only the confirmed `CARGO_LOADING` and `CARGO_HANDOFF` receipt pairs that exactly belong to the same terminal operation/contract/cargo before removing that active graph. It rejects a missing retained observation and does not sweep unrelated confirmed effects by subject ID. Focused normal and snapshot/recovery tests prove the old failure reproduces before the change and the detached state has neither receipt while retaining its bounded terminal ledger. |
| V3-AUD-024 | CLOSED | The bounded volatile bridge correctly retains an exact joining body until the global UUID index resolves to that same Java object or the body is removed; it never treats `isAddedToLevel()` or elapsed time as identity publication. Fresh live r57 evidence nevertheless reproduced a second race one layer earlier: blocks can be loaded while `PersistentEntitySectionManager` is still restoring saved entity columns, so `getEntity(UUID)` is temporarily empty and a new deterministic ambient body may be created beside the saved one. Production ambient, scene-member and cargo admission now defer on `ServerLevel.areEntitiesLoaded(chunk)` without force-loading or inferring death; isolated GameTests explicitly use fixture-only entry points because their synthetic columns do not publish entity storage. The focused 37/37 scene slice and clean detached critical gate (`guardrails`, `check`, build, packaged JAR, 266/266 GameTests) pass. Fresh `frontier-v3-live-r58` deployed SHA-512 `c8a2bd58…fee9c3b` on `25565`; the full-pack fullscreen run `c70e58ea-fd16-4d0b-97da-70b0a2077205` naturally entered the western hive, retained one indexed UUID for `bioform:west-0` and observed continuous exact positions over four samples, with no duplicate-UUID or quarantine trace. |
| V3-AUD-025 | CLOSED | One immutable `RouteFootprint` compiles route surface and foundation cells together, so graybox compilation never rebuilds the surface just to derive foundations. Readability contamination now uses a pure local building/organ cell index plus exact physical-loss mask instead of the full plan, route grid and worksites. A pure aftermath regression compares that index against the complete plan for every building/organ, including a loss; focused tests, the 37/37 scene slice, the re-run 266/266 GameTests and `guardrails`/`check`/build/packaged-JAR verification pass. Fresh live r54 JFR/TPS confirmation remains the deployment evidence. |
| V3-AUD-026 | CLOSED | Frozen-source admission now fails before v2 `SavedData` construction. Every broad SourceGraybox callback is launch-gated, including chunk loading, entity join/leave, explosion, death, block break and object briefing; a v3 launch remains excluded even when the v3 runtime is absent/quarantined. The launch-ownership regression proves both selected modes and the no-construction gate; the 37/37 scene slice and full critical gate (`guardrails`, `check`, build, packaged-JAR, 266/266 GameTests) pass. A fresh r55 entry JFR/TPS run remains required before a live-performance claim. |
| V3-AUD-027 | CLOSED | `b1dc2b5f` exposes an owning-thread immutable canonical state/revision/instant view for physical adapters; `CheckpointImage` remains an explicit persistence/diagnostic boundary and every mutation still enters only through `submit`. The runtime regression proves repeated physical reads plus a command make no codec call until an explicit checkpoint. Fresh r56 evidence has two same-route runs: the cold teleport still took 2.362 s/47 ticks while JFR sampled Minecraft chunk load/serialization and no `FrontierWorldStateCodec`/population-codec frames; the warm replay had no `Can't keep up` line and 3.15–7.13 ms server ticks while the pilot observed one HOT bioform advance in four consecutive samples. Thus the complete-world snapshot hot path is closed; first-view chunk loading remains a distinct Minecraft loading path, not a simulation claim. |
| V3-AUD-028 | CLOSED | Harvest planning now reserves only the exact farmer, output identity and depot slot, emits durable `RESOURCE_SITE_HARVEST` preparation and leaves the task/field `ACTIVE`/`HARVESTING`. The sole canonical wheat stack and growth epoch are created only by the executor's observed `CONFIRMED` receipt. The direct `ResourceSiteHarvested` payload and codec were removed; snapshot schema 100/envelope 9 reject prior worlds rather than decoding the obsolete direct-output path. A second defect in the same boundary selected the first retained global intent, so an unloaded earlier field starved a ready visited field. Admission now iterates only the bounded resource-site work set, skips unloaded surfaces and performs harvest as an `EFFECT` after container provenance has become `ACTIVE`; a focused head-of-line regression binds that selection. Restart recovery accepts only its original preparation intent or the exact same site’s durable neutral-baseline projection claim; no other active claim can be adopted. The native `disposable_redwillow_field_restart` run `b043766f-2b2c-4fde-9a7d-ca03df0fcb0c` proves the naturally loaded field remains `GROWING` at the same immutable crop anchor after graceful restart. |
| V3-AUD-029 | CLOSED | `FrontierV3PhysicalIntentScheduling` classifies each pending equipment intent as `INVALID`, naturally `DEFERRED`, or `RUNNABLE`; deterministic selection skips only deferred entries and retains an invalid head for the existing fail-closed path. Both issue and return executors use the same bounded 4,096-intent retention boundary, so an unavailable earlier endpoint cannot starve a later loaded exact hand-off. Focused fairness regressions pass. The native route-maintenance restart run `19d96775-65e4-4d4c-874f-45a3dac1e6fb` then confirmed the exact return and terminal compaction after repair. |
| V3-AUD-030 | CLOSED | Route maintenance and construction now retain a reverse engineering journey from the observed worksite cursor to one compiled depot service port; the same exact person and tool survive restart, and only observed arrival may admit the hand-to-chest receipt. Issue and return share one NeoForge adapter predicate over the pure named station, rather than two arbitrary chest-distance radii. The native `disposable_route_repair_cargo_restart` run `19d96775-65e4-4d4c-874f-45a3dac1e6fb` proved normal player loss → exact repair → graceful restart → return of `item:bootstrap-1-engineering-tool-1` → maintenance compaction, without a force-load. The economy slice passes 16/16 and Node scenario checks 30/30. |
| V3-AUD-031 | CLOSED | Route-maintenance source and target selection uses the bounded `INVALID`/`DEFERRED`/`RUNNABLE` classification: an unloaded endpoint defers only its own exact intent, while malformed canonical work becomes visible conflict. A naturally loaded first visit retains a `KNOWN_SEMANTIC_LOSS` tombstone only when the exact owner/semantic part is still air; foreign or unclaimed damage cannot acquire repair authority. If the normal projector has not yet made that provenance visible, the worksite stays `DEFERRED`, never conflicts or force-loads. Focused normal/foreign/unknown/recovery checks, the 40/40 HOT/COLD scene slice and the complete critical gate (`guardrails`, `check`, build/package, 272/272 GameTests) pass. Native `disposable_route_maintenance_cold_source_fairness` run `62d89442-49fb-4550-be65-5e6bcafb58e4` proves an ordinary visit repairs the loaded second target while the first source remains COLD, cargo-free and pending. |
| V3-AUD-032 | CLOSED | `RouteMaintenanceProcess` now admits/progresses a bounded deterministic round-robin over retained owners plus one independently checked candidate per scan. Distinct cells require disjoint exact teams and cargo; a nonterminal material-loading intent reserves its exact source stack until its own observed transition changes it. Worksite completion/conflict atomically drains only its exact HOT engineering lease, so no historical body remains attached to a terminal aggregate. `MAX_MAINTENANCE=12`, one aggregate per cell and no forced chunk load remain invariant. Pure multi-loss/order/reservation/lifecycle regressions, materialized foreign/unknown-loss checks, the 40/40 scene slice, the same native fairness run and the complete 272-GameTest critical gate pass. |
| V3-AUD-033 | CLOSED | Exact cocoon custody is now a cross-component invariant owned by `HiveLifecycleStateSupport`: a `DORMANT` or `RECOVERING` bioform retains its one exact HIBERNACULUM-slot body and no active ambient lease. COLD patrol, route-engagement and settlement-assault selection exclude cocoon-retained IDs; fixtures must declare an explicit deployment. `BioformLifecycleStateCodec` retains the schema-105 lifecycle bytes outside the aggregate codec. Focused lifecycle/state/selection tests, Scene 41/41 and the normal-player `disposable_hive_cocoon_wake_restart` proof (break → durable physical delta → graceful restart → same exact `ACTIVE`/`HOT` Zombie) pass; the complete critical gate passes: `guardrails`, `check`, build/package verification and 278/278 GameTests. |
| V3-AUD-034 | CLOSED | The release gate treats an absent provenance claim as `PENDING` projection work and opens only a physically exact claimed `HIVE_COCOON`; an exact prior claim plus a changed block remains a visible conflict. The reusable pure classification regression and exact baseline-palette check pass. Native `disposable-hive-mobilization-release-restart` run `d6a28a3e-e6e6-46e2-b663-7bbf69ac0b67` proves naturally demanded four-body `WAKING → ASSEMBLING → HOT` after graceful restart; its clean frame displays `ASSEMBLY · 4 FORMED`. A full-gate regression exposed a separate local-planning cost error: compiling the entire three-wide world route footprint to service one engineering approach made one exact worksite spend minutes in allocation/JIT. `EngineeringApproachCorridor` now lazily memoizes only bounded candidate columns from the analytic declared graph; it does not compile materialization geometry. The exact conflict-drain regression has a two-second local-compilation ceiling, while preserving the same approach result and terminal HOT-drain assertions. The final critical gate passes (`guardrails`, `check`, build/package and 278/278 GameTests). |
| V3-AUD-035 | CLOSED | A generic codec now has a process-local access-ordered cache of at most eight immutable bootstraps, keyed by the complete world/seed/ruleset/terrain header; all cache access is synchronized and it retains no mutable canonical state. A pinned codec remains the explicit runtime path, while repeated generic checkpoint inspection no longer recompiles the same genesis geometry. Header mismatch still fails before mutable hydration, and focused cache-isolation, 25-profile catalog and exact maintenance-compilation regressions pass in 15 seconds or less; the final critical gate passes (`guardrails`, `check`, build/package and 278/278 GameTests). |
| V3-AUD-036 | CLOSED | Schema 112/envelope 24 now persists one exact `HiveOperationCommandAuthority` on every route engagement: a named operational same-nest Relay/Ganglion/radius proof covering every retained route cell, or a named Overseer plus its ordered weighted roster. `HiveRouteEngagementCommandSupport` is the sole admission/replay boundary; forged starts cannot invent a coverage proof, capacity balance or free body. The same authority gates COLD advance/combat entry, COLD strikes and HOT candidacy/transition. A durable compare-and-set change permits only `CONNECTED`/`RECLAIMED → SIGNAL_MEMORY → INSTINCT`, or a distinct living eligible Overseer at the exact intercept reclaiming the unchanged roster. Relay loss survives snapshot restart into bounded memory then instinct; instinct cannot strike, advance or create a scene. Focused normal, no-controller, Relay, loss, forged-event, reclaim and restart tests pass. |
| V3-AUD-037 | CLOSED at its original retained-actuator scope; MAT-001 carrier dependency reopened under V3-AUD-058/062 | Harvest, production and service/decontamination have retained exact workers, semantic stations, inputs and progress rather than actorless endpoint execution. The exact deployed revision-15 history nevertheless invalidates MAT-001's complete M2 duty-cycle claim across live-body release, inactive carrier and return. MAT-002/003 local evidence remains valid, while any changed carrier-dependent path awaits V3-AUD-062 impact classification and targeted revalidation. M3 remains separate. |
| V3-AUD-038 | CLOSED at operation-continuity scope; shared carrier dependency awaits V3-AUD-062 classification | MAT-004 route patrol at `aa12f919` and MAT-005 hive expedition at `741d4dda` retain exact rosters, formations and topology cursors through their proved COLD/HOT, demand-loss, obstruction and restart paths. Those local facts remain accepted. XACT-001 must determine whether the new shared unload/return carrier changes either production path and apply only the resulting targeted revalidation. Contact/retreat comprehension and natural terrain remain separate M3/release evidence. |
| V3-AUD-039 | OPEN; target contracts and hardening pipeline accepted | Close `MAT-006`, `MAT-007` and `MAT-010` after their predecessors. MAT-006 is suspended until F0.6R3, OBS-001 and XACT-001 are accepted; it additionally requires temporary-recipient duty return and exact-once child emergence. MAT-007 requires mixed HOT/COLD transfer, irreversible digestion and exact organ/bioform activation/emergence. MAT-010 follows MAT-009 and requires same-resident COLD deterioration, current HOT cues, exact treatment and duty resumption. Existing endpoint receipts remain narrower foundations only. |
| V3-AUD-040 | OPEN; target contracts and hardening pipeline accepted | Enforce the M0–M3 evidence levels and class matrix from `docs/frontier-v3-materialization-completeness.md`. MAT-008 additionally requires truthful complete-envelope admission across multi-direction natural ingress; MAT-009 requires each decision row to reach its real owned terminal/downstream consequence even when native presentation is sampled by family. The cross-stage natural-story gate rejects endpoint-only or pre-successor claims and schedules small family product probes before final M3. |
| V3-AUD-041 | CLOSED | The class-C `SERVICE_WORK` owner retains the exact medic, source slot/port, reagent issue intent, distinct source/work stations and traversal cursors. HOT advances the same work through observed arrival and 80 retained work ticks; the terminal adapter consumes only the held exact reagent at `EFFECT_READY`. Focused theft/death/station/cursor/restart coverage and native `disposable_service_decontamination_restart` prove exact naturally loaded reclaim and one confirmed effect without remote depot consumption or replay. M3 visual distinctness remains separate. |
| V3-AUD-042 | CLOSED | The first correction retained the job until its HOT lease released, but the required native scenario then found the remaining schedule-owner defect: release attempted `ScheduleEffect.Created` for the already retained stable `schedule:production-task-complete-*` action and correctly quarantined rather than duplicate it. `ProductionProcess.planCompletion` now reschedules that one durable review while the exact worker lease is HOT/DRAINING; the release receipt reschedules the same ID to the next tick, where the closed lease permits the terminal transform. The focused ordinary/negative regression, Economy 17/17, Scene 56/56 and critical gate (guardrails, check, build/package, 287/287 GameTests) pass. Native `disposable_materialized_production_work_restart` run `74b66054-6464-4279-afb0-7cd8f3730591` reaches the exact 64-bread depot receipt, survives graceful restart and completes without quarantine. |
| V3-AUD-043 | CLOSED at the paired-driver/traversal scope; not current MAT-001 closure | F0.1 remains accepted at runtime candidate `3d46ec2634300614246bf6df4839748594539665`, proof-harness follow-up `8217f246079acc9220279ce66386b687c4a7441c` and four-worker run `34124239973`. It proves one engine continuation/cursor through its named COLD/HOT traversal and recovery boundaries. The exact deployed revision-15 history shows that it did not prove the later terminal, inactive-carrier, return and successor lifecycle; those facts remain reopened under MAT-001/V3-AUD-058. Earlier F0.1 evidence is `PROVEN_NARROWER`, not discarded. |
| V3-AUD-044 | CLOSED | Accepted F0.2A/B/C separate immutable replica evidence from current epoch-fenced physical custody and canonical economic ownership. Settlement and hive reference containers remain live when never visited or safely observer-free, changed replicas are observed before write, and unsupported historical `ACTIVE` authority is rejected in the fresh-world format. Focused ownership/recovery evidence and the accepted F0.2 native HOT/restart/COLD receipt close the foundation scope; deferred provider/release evidence remains explicitly separate. |
| V3-AUD-045 | CLOSED | Accepted F0.2C owns bounded chunk-indexed semantic consequences, physical receipts and deferred aftermath under one causal identity. Deterministic cross-front/unknown/conflict coverage plus the retained native HOT effect, graceful restart and later COLD continuation prove exact-once realization without force-load, replay or unrelated-front stall. This closes the foundation protocol, not every future effect family's product evidence. |
| V3-AUD-046 | CLOSED | Accepted F0.3 introduces fungible lots, stable claim allocations, custody accounts and transient physical bindings across all current item owners. Clean candidate `aa2783b8` and three native receipt chains prove exact partial 64 -> 32 + 32 movement, split/merge, observer-free release, theft/drop-pickup and abrupt visible-effect recovery without permanent stack identity, duplication or blanket inventory rollback. |
| V3-AUD-047 | CLOSED | F0.4 is accepted at clean candidate `c8be658f01ac5adb1a26a489c8b89648427a758f`. One retained strategic operation owns its complete roster and lifecycle while typed children own disjoint allocations/fronts and at most one local lease. Focused parent-lifecycle and four-kind mixed HOT/COLD cross-front tests prove independent progress, exact-once/restart behavior and terminal aggregation; the full 310/310 GameTest gate passes. Same-seed JFR `fe835fff...753974` covers the retained native expedition/return path within the unchanged current limits, so no cap increase is claimed. |
| V3-AUD-048 | CLOSED | F0.4 retains semantic ports, checkpoints and a bounded navigation envelope while the registered HOT provider handles physical door, stair, slope and harmless local-avoidance motion without changing route intent or advancing without observed arrival. The full gate includes `hotEnvelopeUsesOpenDoorStairAndHarmlessDetourWithoutChangingCheckpoint`; focused obstruction, checkpoint-skip, envelope-escape, stale-epoch and restart cases remain green. Natural-terrain provider hardening remains separately open under V3-AUD-019 and is not implied by this graybox closure. |
| V3-AUD-049 | CLOSED | F0.5 is accepted at clean candidate `158f5b938ea0a75fb11b6d7ae29f89d1b650e7fc`, schema149/envelope60 and JAR `269967bf...74e1f`. Monotonic authority epochs and bounded stale-projection tombstones cover body, cargo, container and effect bindings. Deterministic and Minecraft evidence proves safe no-visit COLD resumption under a successor epoch, late stale-load rejection without duplicate progress or false death/effect, graceful and authenticated abrupt boundaries and both partial-save arrival orders. The final current-candidate regression closes restore-then-save-then-diverge replay: a successful recovered player custody writes its exact durable completion witness, and later physical divergence becomes local ambiguity rather than replacement. The final gate passes 314/314 GameTests and 1,350 JUnit tests. |
| V3-AUD-050 | CLOSED | F0.5 gives ordinary damage, theft, obstruction, death and partial delivery typed domain-disruption owners; unresolved physical evidence freezes only the smallest asset/front with a visible reason and bounded retry/repair/replan/abandonment/compaction outcome. Injected canonical conservation/ownership and persistence corruption alone quarantine the Frontier before further mutation. The accepted deterministic failure-routing and full-gate evidence retains unrelated settlement/front progress and rejects terminal `CONFLICT` as an unowned wastebasket. This is failure-foundation closure, not proof of every future gameplay family's recovery handler. |
| V3-AUD-051 | CLOSED | F0.6 clean candidate `2190797d` declares one versioned comparison contract for all 16 current families, retains exact identity/accounting/custody/topology/recovery facts and keyed opportunities, and separates exact quiet work from calibrated combat. Independent HOT and COLD runs for seeds 201--216 produce deltas `0/0/288` inside the predeclared `3/3/1200` success/casualty/duration bounds; at least one physical damage result differs, rejecting copied COLD hits. Eight quiet samples and eight repeated HOT/COLD cycles preserve exact work and restart continuity. |
| V3-AUD-052 | CLOSED | The bounded production candidate inventory and generic demand selector pass focused selector coverage. Fresh r82 `arrival_checkpoint_one` and `arrival_checkpoint_two` both admit the demanded later harvest process and reach their exact HOT lease/checkpoint evidence without demand creating canonical work. The architecture debt ratchet and full 292/292 Core GameTest gate pass at published commit `089495ed630f777f63e3b8952e500ea548bc814c`. |
| V3-AUD-053 | CLOSED | Fresh r82 proves the registered HOT driver advances the retained traversal cursor through the exact engine continuation while the irreversible crop/output effect stays zero and the intent remains `PREPARED`. The exact neutral HOT/COLD pair, arrival lanes and all three applicable abrupt boundaries pass; intent kind/status/observation, cursor, custody, conservation and schedule are compared exactly. |
| V3-AUD-054 | CLOSED | Every r82 non-restart lane authenticates terminal semantics before exact normal client disconnect and ordinary demand-loss release, followed by durable save, JVM exit and closed port within the existing bound. Graceful and abrupt lanes retain their declared recovery state. No task-owned native process or declared task port remained at independent review. |
| V3-AUD-055 | OPEN | F0.2B must first prove its narrower recovery semantics at an identity-bound server-owned natural-streaming quiescence boundary, without sleeps, forced chunks or timeout inflation. `save-all flush` success is explicitly insufficient: Minecraft's flush path may skip a `toDrop` holder with non-zero `generationRefCount` and still return success. A point-in-time `toDrop` snapshot is also insufficient because PM demand-loss can precede completion of vanilla player-ticket propagation. The accepted pilot carrier now retains a conservative all-level `updatingChunkMap` superset across the complete affected natural-demand episode, checks full `ChunkHolder.isReadyForSaving()` for every exact retained holder, acknowledges arming before client demand, and uses an identity-bound one-use server-thread command whose invocation-unique outcome separates admission/rejection before durable-save waiting. Watcher-before-read closes receipt lost wakeups; the command remains absent from production and leaves vanilla stop unchanged. Revision33 authorizes one local physical use of this narrower quiescent-recovery carrier. A recurrence after its exact admitted fact is a runtime/platform defect, not a carrier failure. Before production cutover, separately prove bounded graceful shutdown when stop overlaps authentic natural generation or provide a reviewed fail-safe/platform correction; a quiescent F0.2B pass cannot close the broader risk. |
| V3-AUD-056 | REOPENED P1 by direct human retest of the boundedness-correction deployment | On accepted deployed F0.6R2 the user again observed managed mobs move one cell at a time with several-second pauses. F0.6R3 changed the shared capability boundary to pursue the next canonical checkpoint on normal ticks, but the user reports that currently deployed correction candidate `cb64597f` is only smoother and still visibly jerks. Provider-level motion evidence therefore does not yet prove the complete production duty cycle: recurring scheduler/phase plateaus remain a player-visible defect until the exact live path pursues continuously or exposes a legitimate readable dwell. |
| V3-AUD-057 | CLOSED | Native positive evidence binds exact `resident:1-31` and its harvest job from PREPARED/zero crop through one observed crop consequence, cursor 37 -> 38, ordinary leave/release and HOT return with the same worker, intent and progress. The negative path retains progress after an ordinary player removes the next managed cell and exposes exact position plus `PLAYER_REMOVED_MANAGED_CELL`/`TERMINAL_REPAIR_REQUIRED`, rather than an indefinitely active label or false restart state. The final F0.6 Scene and critical gates pass. |
| V3-AUD-058 | REOPENED P0 by repeated direct human/restart retest; prior COLD receipts retained as accounting evidence only | F0.6R3 first rejected `4fb7f01b` because a nonzero traversal cursor still left all crop slots uncompleted and admitted `crop-0`. Successor `38aa970c` proves one completed COLD crop receipt for every current job, but its human retest in exact live world `frontier-v3-f06-r3-cold-r1` exposed a missing field and idle farmer. On currently deployed correction candidate `cb64597f`, farmers first encountered by the user still begin at crop cell 1 or 2. After a server restart and further zero-player wait, the observed farmer remained on exactly the same crop cell where it was left. Retained red r14 then proves canonical progress 12 -> 63 slots but a re-entry conflict at the first crop: the two-chunk field was fenced only in the player's ingress chunk, leaving the adjacent facility half unmaterialized. The correction must retain sustained COLD progress and materialize the complete declared field envelope through ordinary first ingress and restart without force-loading, world scans or overwriting foreign player changes. Reconciliation must use the closed typed managed-facility boundary from `frontier-v3-managed-facility-reconciliation`; null claim/block-pattern heuristics may not classify an owned partial prefix as neutral or foreign. |
| V3-AUD-059 | REOPENED P0 by direct human retest; prior private frames/diagnostics retained only as non-live evidence | In exact live world `frontier-v3-f06-r3-cold-r1`, the user encounters hive organ boards reading `ORGAN NOT CURRENT`. This directly contradicts the retained private carrier's coherent-current conclusion. The board is truthful evidence of incomplete/stale physical ownership, not cosmetic text to suppress: foundations, tissue, infection surface, exact organ geometry, cocoons and boards must agree as one current naturally loaded nest. The correction must prove both seed nests through ordinary player ingress and restart; `SETTLED/current` diagnostics or curated frames cannot stand in for the live path. Natural-terrain art quality remains separate. |
| V3-AUD-060 | CLOSED at automated/native M2 and operationally deployed from `38890fa3`; human retest pending | The accepted candidate composes actual demand admission with an idle managed resident and moving managed bioform, ordinary player-game-mode support removal, normal gravity/collision landing, typed demand-loss release, exact landing-body retention in canonical state and removal of the HOT bodies without duplicate route authority. Its exact JAR is live for direct player observation before M3. |
| V3-AUD-061 | OPEN; blocks F0.6R3 operational and human acceptance | Fresh live crash `/home/rd/far-frontier-server/crash-reports/crash-2026-09-14_09.36.30-server.txt` and the user's preceding TPS-degradation observation disprove the terminal deployment claim for `aaa0f047`. Terra owns the bounded production correction and the cheapest faithful ordinary-ingress recurrence. Retain already-valid field/nest semantics, but no candidate is ready for human retest until a clean exact deployment remains responsive through the triggering live path and passes the operational verifier afterward. |
| V3-AUD-062 | OPEN; accepted XACT-001 exit gate blocks MAT-006, not current F0.6R3 | After F0.6R3 and OBS-001, execute the bounded shared-carrier composition checkpoint. Closure requires a closed producer/adopter inventory, zero unregistered carrier bypass, explicit evidence-dependency classification and the smallest representative natural release/COLD/restart/return coverage for singular resident, resident-roster and bioform-roster shapes. It neither reopens all MAT rows nor authorizes a broad matrix. |

## Findings and required corrections

### V3-AUD-019 — movement and facility access assume a flat coordinate plane

The current graybox is intentionally flat, but reusable movement state has
absorbed that implementation detail. Several corridors require equal Y and add
only X/Z steps; the controlled HOT primitive aims horizontally; facility
approaches can be derived from fixed compass offsets; and `BlockPosition` is
still used for both support-floor and feet-air conventions. This is sufficient
for a flat test world but cannot represent a hillside street, stepped entrance,
bridge approach, multi-datum facility or grade-constrained railway without
either teleporting, recomputing a second HOT path or inventing physical
geometry at runtime.

Correction: separate typed spatial roles; compile each facility's oriented
exterior approach, threshold, interior connector and stations; and introduce a
bounded immutable 3D topology whose stable edges carry traversal capability,
grade, clearance, provenance, revision and availability. Canonical route owners
retain the sole edge/cursor truth. COLD advances that cursor; HOT executes the
same next node through a registered Minecraft movement provider and reports
arrival or obstruction. Player/world changes update topology through typed
evidence and may cause a bounded canonical replan or engineering task, never a
hidden sidestep or desired-state repair. Pedestrian/bioform and rail graphs are
distinct capability views. The flat graybox implements the same contract as a
uniform-datum provider rather than remaining the domain model.

Exit evidence: old movement bytes and snapshots reject fail-closed, requiring a
fresh world rather than a semantic migration;
source/architecture guards reject new constant-Y/fixed-compass route APIs;
compiled ports prove supported connectivity and two-body clearance; focused
fixtures cover a stepped or ramped path, another facility datum, a blocked or
destroyed entrance and a bridge/rail-grade edge; a native HOT/COLD/restart
scenario retains the same actor IDs, topology edge and cursor without
force-loading; Foundry passes the relevant `COMPILED`, `SETTLED` and `RELOADED`
rules.

Current implementation boundary: the terrain provider now compiles the same support discipline
for both seed-hive sites: every nest datum derives from its declared organ base-support columns,
and lower organ tissue receives exact owner-specific hiveroot fill.  Bootstrap, projection,
physical-loss validation and organ-operational thresholds share that immutable plan; no hive
anchor queries a loaded height map or remains at a hidden fixed Y. The read-only
`hive_foundry` scope now audits one named organ and that same plan's exact
terrain-provider-owned roots at `COMPILED`, `SETTLED` and `RELOADED`, without loading,
projecting or repairing a cell. Its materialized GameTest reload proof retains both root and
organ claims as `CURRENT`; matching-looking unclaimed blocks and conflicted claims remain
explicit mismatches. This is a bounded automated materialization/reload proof, not a native
player visit to a non-flat hive and not closure of the remaining bridge/rail-grade, foundation
repair/replan or cross-family HOT/COLD gates.

The hive assembly family now supplies the first cross-family HOT proof for this contract.  Its
pure compiler retains a bounded deterministic 3D `GROUND_BIOFORM` corridor from each exact
cocoon-release surface to its exact Ganglion staging surface, including organ and hiveroot body
clearance.  The loaded executor may move only toward that next retained surface and advances the
cursor only after observed arrival.  A full loaded body-column obstruction, missing support or
missing/foreign body becomes durable `ASSEMBLY_PATH_BLOCKED` evidence naming the actor, expected
cursor and retained target; it never asks Minecraft navigation for a substitute path.  Thin
route/infection overlays of at most one eighth block are explicitly the same surface rather than
a false wall.  A one-block descending retained edge is physically staged as exact horizontal
walk-off followed by bounded vertical settling, so it neither collides with the upper ledge nor
turns into an implicit flight edge.  Focused state/codec checks, a 44-test Scene slice and the
native graceful-restart `disposable-hive-mobilization-release-restart` scenario prove the
four-member group reaches terminal assembled HOT custody after the retained approaches; its
post-restart trace has no path-blocked event.  This proves the assembly family, not arbitrary
terrain replan or bridge/rail grades.

The previously open assault-departure seam is now closed at schema-111/envelope-23.  A
mobilisation retains its original Scout sighting, exact Overseer and exact assembly roster;
the final retained COLD or observed HOT cursor creates one ordered transaction:
`hive_mobilization_assembly_advanced -> hive_mobilization_departed ->
settlement_assault_started`.  The correction found that this was not truly atomic: those two
events briefly exposed an invalid departed roster without an assault. `HiveMobilizationDeparted`
now carries the exact `SettlementAssault`, and its one reducer first verifies every actor on its
persisted staging surface, closes only its assembly leases, changes only those identities from
`ASSEMBLING` to `ACTIVE`, and starts that same ordered roster/controller assault in the same
state update. Missing/stale target facts yield the durable
`DEPARTURE_UNAVAILABLE` conflict rather than a fresh ambient-body selection.  Focused tests
cover COLD completion, final HOT receipt, stale-sighting conflict, stale departure replay and
snapshot/WAL round trips.  This closes only the assembly-to-assault ownership seam; controller
loss/signal-memory degradation and return/recovery remain open operation cuts.

The road family exposed a separate decision defect while tracing the promised replan path.  A
known route loss correctly created a same-cell `RouteMaintenance`, and construction correctly
refused to bypass a viable repair; however that refusal terminally marked the pre-existing
patrol-confirmed bypass task `BLOCKED`.  If the exact loaded-world repair later conflicted, the
retained scar still prohibited a replacement forever.  The corrected route decision keeps the
same task pending and reschedules it at the retained construction cadence while an exact repair
is absent, `BUILDING` or `READY`.  A durable `CONFLICT` alone releases that repair's engineering
assignment and makes the already-confirmed task eligible for one bounded terrain-plan candidate;
the scar remains and the candidate must avoid it.  An observed successful repair instead makes
the retry close as no longer needed. Focused normal/deferred/conflict and restart-retry tests
cover the two exclusive outcomes and prove that waiting is persisted rather than a live-process
timer. This is deliberately not yet generic replan: the current candidate is a
road-family bounded catalogue, and transit, hive assembly and rail still need one shared
topology-disruption aggregate rather than copied policy.

This correction must not be misreported as
complete merely because the medical scene has typed port data. The flat provider
does not claim physical excavation or a non-flat entrance: its infirmary
support remains at the uniform datum. `TraversalPath` is a small immutable value
proving the spatial type and observed-arrival rule; it is not yet the persisted
capability topology required by all route, transit, assault and rail owners.
The generic closed-scene cleanup
does now run before every registered behavior, preventing historical lease tags
from leaking from the medical path into another scene family; the terminal
diagnostic reports such a released historical lease as `CLOSED`, not a false
duplicate-UUID conflict. The remaining exit gates above are mandatory.

The first T0.3 owner is the persisted `RouteTopology`: its declared supply
waypoints compile deterministically into one bounded `TraversalTopology` of
typed support nodes and directed pedestrian edges. Each edge retains its
ground/rail capability separation, exact grade and two-body clearance, named
route provenance, content revision and explicit availability. This compiler is
not an adapter pathfinder and never reads Minecraft. `OperationTravel` is now
the first retained cursor consumer: schema 95 persists the topology with exact
`BodyPosition` formation cells and one `TransportAnchor`; its WAL uses `0xfffe`.
Schema-91/`0xffff` operation formations, former bounded corridor bytes and
pre-current scene payloads are rejected: v3 recreates worlds rather than
migrating spatial meaning. Its HOT translation retains the same next-edge Y
delta for every formation and cargo position. Each `OperationAssembly` member
now retains its own typed pedestrian topology, not a historical support list;
no caller may claim a new rail route or runtime replan from this partial implementation.

Native route-return evidence exposed a semantic seam: the topology is a
support-surface graph while the prior operation/scene representation was raw
`BlockPosition`. Schema 94 corrects the active operation/scene boundary with
distinct body/transport values and exact loaded-column verification. The failed
2026-09-01 disposable route-return attempt is
retained as boundary evidence, not treated as a test-pilot fault.

The graded assembly regression now proves a retained one-block edge and distinct feet cell.
Fresh schema-98/persistence-v7 scene payloads retain body feet cells at every
capture/release/death boundary; no current scene codec reconstructs one from a generic
support position. `disposable_route_scene_return` evidence retains
the same members and cargo through normal unload/reload, graceful restart and
natural recovery to `HOT`; it proves retained cursor continuity, not edge
availability. Remaining correction before T0.4: loaded blocked-port/edge
evidence must alter availability, then HOT/COLD/restart recovery must prove the
same edge without a hidden sidestep. Only then may this route-return scenario
also become T0.4 evidence rather than a presentation of the old ambiguity.

The current raised-route provider closes a narrower but real physical-causality gap in
that foundation: destroying an owned `ROUTE_FOUNDATION` also makes its owned carpet
deck disappear through Vanilla survival rules. The observer now derives one bounded
atomic loss set from the provider palette before the initiating block mutates, persists
both semantic losses in the same command/transaction, and conflicts both desired-state
claims together. A restart retains both deltas and the blocked topology; it never
reprojects the carpet over a missing footing. A terminal patrol is correspondingly
historical evidence of the topology that it actually inspected, while only an
`EN_ROUTE` patrol must equal the current topology after a bypass cutover. The native
`disposable-stepped-route-restart` scenario proves ordinary player support break,
both physical-delta diagnostics and one graceful restart; its trace records the two
coordinates under one physical-observation command. This is a bounded provider rule,
not a coordinate exception, and does not close the remaining non-flat/bridge/rail and
HOT/COLD exit gates.

### V3-AUD-017 — route people are encoded as a historical pair, not an exact unit

The accepted human-capability contract distinguishes exact crew members from
their current duty and requires a patrol/scout group of two to four residents
and a separately retained cargo escort.  The current route models predate that
contract: patrols keep one `guardId`, while route operations infer the first
participant as a hauler and every later participant as escort, then hard-reject
anything except two people.  That prevents a real patrol pair, makes train crew
and escort inseparable, and leaves no durable leader/member composition to
degrade or recover.

Correction: keep `RoutePatrol` and `RouteOperation` as the only owners; embed
one shared immutable exact-unit manifest in each record.  The manifest has a
stable derived ID, exact ordered member IDs, one retained leader and explicit
member duties.  It is a value of its route owner, not a global unit registry or
an additional assignment ledger.  New patrol admission selects a security-capable
leader and one to three exact scouts.  New cargo admission names transport crew
separately and selects two to four exact escorts.  Assignment, actor positions,
combat/readiness and HOT materialization must derive from this same manifest.

Schema-82 is historical state.  A decoder may represent its one-person patrol
or one-guard escort only as an explicit understrength legacy manifest until the
already-running owner completes, fails or is interrupted.  Hydration may not
choose, create or equip a replacement resident.  No new process may create an
understrength manifest.

Exit evidence: new admission rejects a missing or conflicting exact member;
assignment compilation proves one owner per person and transport crew remains
distinct from escorts; normal traversal plus death/loss and snapshot/WAL
recovery retain the same surviving IDs and item custody; a native HOT scenario
shows the same named members before and after an ordinary unload/reload.

### V3-AUD-001 — scene behavior is not owned by one registry

The accepted contract requires one closed `SceneBehavior` registry. Current
scene admission, validation, ownership, state transition, diagnostics and
NeoForge execution independently ask whether the cause is logistics or a
settlement assault. `FrontierV3SceneExecutor` also invokes the assault executor
before running its logistics path. A third scene kind would therefore require
editing generic lifecycle code in many places and could accidentally call
`SceneLease.operationId()`, `cargoId()` or `logisticsCause()`, all of which
throw for a non-logistics cause.

Correction: introduce closed pure-domain and NeoForge behavior registries with
exactly one behavior per sealed cause. Generic lease code may know only common
identity, members, handoff, status and recovery. Cause-specific cargo,
engagement, assault and effect facts remain inside the registered behavior.
Sealed codec tags may retain an explicit exhaustive switch. Read-only
diagnostics consume a registered descriptor rather than recreating lifecycle
policy. No bomber, siege or third scene kind is admitted before this closes.

Exit evidence: duplicate/missing behavior registration fails at startup and in
a unit test; logistics and assault normal/negative/restart scenarios pass
through the same generic lifecycle; the debt ratchet has no scene branches
outside behavior registration, sealed codecs and explicitly allowlisted
read-only formatting.

### V3-AUD-002 — disposable fixtures are production-selectable

The nonce/property convention prevents accidental selection by the ordinary
scenario runner, but it is not a security or classpath boundary. Any normal JVM
can set the same two properties, and the fixture builders/configurations are in
production main sources. This contradicts the claim that fixtures are
unavailable to a live start and makes it possible to launch a plausible but
non-autonomous scripted world.

Correction: production `initialConfiguration` always creates the world
profile. Move scenario state builders, profile catalog and the selecting
bootstrap provider to a dedicated moddev/test source set that is absent from
the packaged JAR. The runner may select only a catalog entry provided by that
test classpath. A run ID remains correlation evidence, not authorization.

Exit evidence: packaged-JAR inspection rejects fixture classes, profile
properties and fixture configuration entry points; a production-start test
proves arbitrary JVM properties cannot select a fixture; every declared pilot
profile is validated from one test-only catalog.

### V3-AUD-003 — the runtime definition is a monolithic dispatcher

Explicit exhaustive dispatch is desirable, but one 947-line composition root
currently owns configuration variants, command policy, scheduled-kind routing,
event routing and many cross-domain validations. It is already close to the
repository's 1000-line hard limit. Adding domains here increases merge risk and
makes ownership and completeness hard to review.

Correction: split deterministic closed process modules. Each module declares
the command payloads, scheduled kinds, event payloads and reducers it owns. It
also declares the stable codec for every payload it can consume or emit.
Composition validates duplicate and missing registrations and retains stable
ordering. Unknown kinds still quarantine; dynamic plugins and ambient
classpath scanning are not introduced. The ordinary runtime configuration and
limits have one composition path; test fixtures inject only initial
facts/schedules.

Exit evidence: every registered payload/action has exactly one owner; omitted
or duplicate ownership fails a focused test; the composition root contains no
domain logic and is materially below its debt ceiling.

### V3-AUD-004 — physical executor order is implicit source order

Observation-before-effect, projection-before-observation and scene-last choices
change causality. Today those choices are 22 adjacent method calls. A new call
can be inserted at a visually convenient but causally incorrect location, and
there is no machine-readable dependency or per-stage pressure diagnostic.

Correction: use a closed staged physical-executor registry. Each executor
declares a stable ID, phase, dependencies, owned intent/observation kinds and a
bounded budget. Startup rejects duplicate IDs, cycles, missing dependencies or
multiple writers for an exclusive kind. The resulting topological order is
stable and exposed in read-only diagnostics.

Exit evidence: order/cycle/duplicate tests plus one negative causal test where
reversing an observation/effect dependency is rejected before world mutation.

### V3-AUD-005 — aggregate reconstruction is positional and scattered

The immutable aggregate is appropriate. The unsafe part is reconstructing its
22 components positionally from 18 files. Several components share the same
erased Java type, so a same-typed argument swap can compile. Adding a component
also requires editing unrelated process support classes, which makes omissions
likely.

Correction: `FrontierWorldState` owns a named copy/update boundary (builder,
copy spec or domain-owned `with...` methods). Process/support classes return
their owned sub-aggregate or call a named update; direct aggregate constructors
remain only in initial-state assembly and versioned hydration. Transition
validation consumes an explicit changed-domain set rather than inferring all
ownership from object identity.

Exit evidence: source guardrail rejects direct construction outside the
state/initial-state/codec owner; property tests mutate each component and prove
all unrelated components retain identity and value.

### V3-AUD-006 — persisted enum tags depend on source order

Bounds checks prevent an invalid array index, but they cannot detect a valid
tag whose meaning changed after enum insertion/reordering. Snapshot schema
versioning does not help if the source enum changes without an explicit tag
migration. This is a latent save/WAL compatibility defect.

Correction: every persisted enum has an explicit stable wire tag (integer or
string) and a total fail-closed decoder. Tags are never reused. Changing
meaning requires a schema migration; adding a value adds a new tag without
changing old bytes. Replace the existing 223 positional uses in bounded codec
families, with golden old-byte decoding tests before each schema bump.

Exit evidence: source guardrail permits no `ordinal()` or `values()[tag]` in v3
codecs; golden fixtures decode identically after enum declaration reordering;
unknown tags fail before state mutation.

### V3-AUD-007 — world rules are not versioned data

Capacity bounds and schema maxima correctly belong in code. Balance rules do
not: changing a patrol interval, assault step, infection gain or perception
radius currently changes the future of an existing world without recording
which rules produced its prior state. Development fixtures can also silently
drift from production composition.

Correction: add immutable `FrontierRuleset` data with a stable ID, schema and
content hash in the world manifest/snapshot. Processes receive it explicitly.
Keep safety maxima and algorithmic invariants in code. A ruleset change is an
explicit new-world choice or a versioned migration; recovery rejects a missing
or incompatible ruleset rather than using current constants silently.

Exit evidence: same state/seed/ruleset is deterministic; a changed ruleset has
a different manifest hash; recovery with an unavailable ruleset fails closed;
fixture composition reuses the production ruleset unless a scenario declares a
test-only override in its catalog.

Closure evidence: `FrontierRuleset` carries stable ID/schema plus canonical
SHA-256 across cadence, spatial and rate inputs. Version-80 state snapshots
write the selector immediately after world/seed and use the installed catalog
to require the exact ID/schema/hash before any mutable aggregate is read.
Versions 41–79 take one explicit named legacy anchor, never the current
production default. The bootstrap digest includes that selector; independent
same-seed/same-ruleset configurations produce equal state, schedules and state
bytes, while a changed rate changes both ruleset and manifest hashes. The
fixture catalog now requires a declared ruleset identity and verifies it against
the produced bootstrap. `frontier_v3_architecture_debt.py` permits zero
unhashed process cadence/radius/step/gain/lifetime/cost constants.

### V3-AUD-008 — pilot profile catalog is duplicated

Correction: one test-only declarative catalog owns profile ID, fixture
provider, allowed runner, required assertions and source profile. Gradle and
Node/Java parsers validate or derive their allowlists from it. Unknown,
duplicate and production-packaged entries fail the fast gate.

### V3-AUD-009 and V3-AUD-010 — bounded complete-load evidence

Immutable copies and complete validation buy valuable correctness, and the
ordered global queue buys determinism. This audit therefore does not prescribe
mutable shared state, parallel canonical writers or per-domain queues. First
instrument transaction planning/reduction/validation/allocation and each
physical stage. Then run the same-seed complete-domain pressure route with 12
settlements, simultaneous fronts and increasing exact body counts.

Optimization is authorized only from measured attribution. Acceptance records
schedule lag by kind/owner, queue depth, p50/p95/p99 transition and physical
stage cost, allocation/GC, TPS/MSPT and canonical hash. Any fairness mechanism
must retain one total deterministic order and WAL replay equivalence.

H0.6 closes the measured-risk finding at the currently declared limits, not at
an imagined future mob count. `frontierV3ScaleAudit` uses production seed 41
and performs one due action per tick through twelve ordinary simultaneous field
fronts. It runs twice, producing the exact same final checkpoint digest
`14849b68b9ca7d8192989d95bce48f9de24ab366c8016058ace89ea51455758b`; its
complete result is 434→435 exact bodies, 18,817 schedule plans, 302 queue
keys, maximum queue depth 86, maximum lag 30 ticks and exact retained-WAL
recovery. It neither creates cohorts nor substitutes a fixture population.

The separate disposable native route
`disposable-settlement-assault-scale-jfr` is intentionally narrower and
physical: one ordinary player naturally loads a current-limit 27-member
settlement assault, confirms its strike and captures a 120-second JFR. Its
seed-41 reservation-index evidence is stored under ignored build artifacts as
`build/profiles/frontier-v3-aud-h06-seed41-assault-reservation-index.jfr` with
the matching scenario manifest. The JFR records server-tick p50/p95/p99 of
2.099/5.912/16.773 ms (119 samples), no watchdog, out-of-memory or "Can't keep
up" line, and actual GC pauses no longer than 26.885 ms. It exposed a real
allocation source: ambient admission rebuilt every settlement's full structure
occupancy once per actor. The correction compiles exact actor reservations once
per immutable canonical snapshot and reuses the structural slot occupancy per
candidate. Sampled `FrontierGrayboxPlan.GrayboxCell` allocation falls from
12,412,453,088 to 15,890,872 bytes for this route; checkpoint-image allocation
does not appear in the final profile. The observer and caches are bounded,
read-only and absent from the canonical snapshot/WAL, while focused tests prove
they do not change the canonical result.

This is not evidence for twelve simultaneous materialized assaults. The
architecture keeps those fronts COLD until naturally loaded, and the
`frontier-v3-bounded-runtime` invariant requires a new same-seed JFR plus a
terminal causal scenario before raising either the active-body or physical
scene limit. That is an extension gate, not residual H0.6 debt.

### V3-AUD-014 — future-work queue had no retained cardinality bound

The previous per-tick `WorkBudget` bounded admission, not retention. A planner
could create more future actions than it consumed and make memory/canonical
snapshot size grow without a fail-closed boundary. This is a correctness defect,
not a performance tuning question.

Correction: `EngineLimits` owns an explicit `maxPendingSchedules` safety
maximum. Bootstrap and recovered schedules fail before canonical engine
installation when they exceed it; a transaction's copy-on-write schedule overlay proves its exact
post-commit size before validation/WAL, and an over-cap due action remains
unconsumed while the engine visibly quarantines. The architecture debt ratchet
must reject removal of the bootstrap, overlay or recovery protection.

Exit evidence: focused over-cap and oversized-recovery negative tests; the
12-settlement pressure route stays below the production maximum; H0.6's full
critical gate and real-server JFR evidence pass.

### V3-AUD-011 — declared package ownership has collapsed into `model`

The implementation plan defines `frontier.v3.model` for canonical aggregates,
`frontier.v3.process` for behavior and `frontier.v3.persistence` for schemas
and codecs. Current package structure has no `process` package; 33 process
classes and 23 state/payload codec classes sit beside aggregates in `model`.
This is not cosmetic: package-private access encourages processes to reconstruct
the world aggregate directly and prevents a clean dependency direction.

Correction: H0.3 moves process modules behind an explicit process-facing state
API; H0.4 moves wire schemas/codecs to persistence ownership without exposing
mutable model internals. Dependencies remain `process -> model/api` and
`persistence -> immutable model/api` with no reverse dependency on concrete
process classes. Package moves do not justify compatibility adapters in the
runtime.

Exit evidence: source isolation rejects `*Process`/`*Codec(s)` classes in
`model`; package dependency tests reject a model import of process or concrete
persistence implementations.

### V3-AUD-012 — payload-to-codec completeness is not enforced

Duplicate codec types fail correctly, but omission is discovered only when a
specific payload reaches WAL encoding. The former missing harvest-observation
codec proves this is not theoretical. An open `FrontierPayload` plus a manually
assembled codec list cannot demonstrate that every accepted command or emitted
event is durable before the runtime handles it.

Correction: each closed process module supplies one descriptor containing its
accepted command payloads, scheduled kinds, emitted/consumed event payloads and
their codecs. Composition rejects a payload without exactly one stable codec,
a codec without one declared owner, duplicate type strings and a planner that
emits an undeclared payload. Kernel schedule effects remain their own closed
module.

Exit evidence: a negative composition test omits one codec and fails before
engine start; every process descriptor round-trips one representative payload;
the full registered payload set round-trips through snapshot/WAL recovery.

Closure evidence: the installed process catalog now keeps a literal wire-type
contract per owner; the persistence-side catalog has the same finite owner
keys, and runtime rejects any mismatch with the actual codec registry before
engine construction. The representative corpus contains one payload for each
of the kernel, physical, ambient, logistics, population, economy, resource-site,
hive, infrastructure and strategy owners; it round-trips both its exact codec
and a combined versioned WAL transaction envelope. Existing snapshot/WAL
recovery remains covered by the deterministic engine/recovery suite. Focused
negative composition tests and the complete critical gate pass (248/248
GameTests, build and packaged-JAR verification).

### V3-AUD-013 — GameTest must not impersonate an off-template canonical world

The GameTest server deliberately places its templates at arbitrary far-away
coordinates. A v3 GameTest that creates a real canonical scene at its original
1024×1024 coordinates must therefore force-load an unrelated chunk. Conversely,
moving the physical actor to the template without a complete test-only coordinate
transform makes canonical position evidence invalid. Both approaches make an
apparently green test weaker than the real materialization boundary.

Correction: keep GameTests inside their naturally loaded template chunks for
local executor/ownership behavior. Put scenario fixtures and GameTest-only
helpers in `src/pilot`; do not package them. Prove cross-coordinate scene
materialization, player demand, restart and recovery through the native
disposable test-pilot against a real 1024×1024 v3 world. The architecture debt
validator rejects every v3 GameTest `getChunkAt` call.

Exit evidence: zero forced v3 GameTest chunk loads; packaged-JAR rejection of
the two moved test helpers; focused GameTest slice plus the named native scene
scenarios remain green.

### V3-AUD-043 — a physical scene can become the work owner

The shared lease lifecycle correctly prevents duplicate HOT/COLD custody, but
it does not by itself prove that the canonical process can begin and advance in
both modes. Resource harvest currently exposes the distinction: the retained
job and reservation can survive an unloaded release while the exact farmer no
longer has an admissible continuation on return. Earlier HOT and restart proofs
therefore establish a valid physical episode, not universal execution
continuity.

Correction: one process aggregate owns schedule, identities, resources,
topology, cursor, progress and outcome. A scene is only a versioned capability
grant to a registered HOT driver; its acquire, observed checkpoints and release
atomically update that same aggregate. A paired COLD driver advances the same
semantic steps whenever no physical lease exists. Player demand chooses the
executor and cannot create, start, reset or accelerate the process. Atomic
effects, environmental frontiers and ambient custody retain their distinct
intent/frontier/presence boundaries instead of being forced through a work
scene.

Here schedule ownership is semantic, not duplicate storage. The engine-owned
`ScheduledAction` inside the canonical checkpoint remains the sole durable
deadline/ordering fact. The process descriptor defines its meaning and cadence;
HOT lifecycle transactions bind to an exact typed view of that entry and the
engine validates state plus schedule effects atomically. `FrontierWorldState`,
`SceneLease` and the model behavior registry do not copy the queue. A release
without semantic progress preserves the current action exactly, while a HOT
cursor checkpoint advances cadence from that action under the same rule as
COLD. Missing, duplicate, stale or mismatched bindings fail closed rather than
being reconstructed from release time.

F0.V native-matrix evidence on 2026-09-04 exposed a separate pre-existing
integration defect that must not be concealed by choosing a different
fixture. `FrontierV3ResourceSiteHarvestSceneExecutor` obtains the globally
first eligible harvest candidate and only afterwards tests local observer
demand. A player demanding a later active field cannot therefore admit its
one HOT lease while an earlier field has no observer. The demanded field's
canonical cursor continues COLD but it has neither lease nor body. This is a
production scene-selection/fairness defect in the preserved F0.1 work, not a
test-pilot timeout. The failed disposable run and attributable bundle remain
at `build/frontier-v3-scenarios/f0v-resource-site-harvest-f0v-ecbc8707-72c4-4df8-bfa1-7f2e7debb782/`.
F0.V may continue to exercise independent SDK and runner gates, but it cannot
claim its native vertical exit until allowed F0.1 resumption corrects that
selection order without making demand create work.

Independent review of the later r58 F0.V candidate exposed a second neutral-
observer defect at the same boundary. At exact `SimInstant 24608`, the COLD and
HOT/COLD lanes retained the same worker, cursor 17, released lease, zero crop
progress and byte-equal scheduled continuation, but the harvest
`PhysicalIntent` was `PREPARED` in COLD and `RUNNING` after the HOT visit. The
declarative comparator omitted `result.intentStatus`. This is not physical
trajectory freedom: `RUNNING` is persisted canonical recovery state meaning a
non-replayable effect may have begun, and restart/conflict reducers treat it
differently. F0.V remains open until the traversal-only scene stops using the
intent lifecycle as readiness/admission state, the exact projection includes
kind/status/observation binding, and a mismatch fails before native execution.
The correction must not enable the deferred F0.2 crop effect.

Revision-5 WIP then exposed the inverse error: the HOT checkpoint submission was
guarded by the false irreversible-crop capability. Intent neutrality does not
mean traversal inactivity. Before F0.2, observed HOT arrival and ordinary COLD
execution advance the same retained traversal cursor/continuation while both
retain zero crop/output progress and `PREPARED` intent. This is V3-AUD-053 and
must be corrected without merging traversal and effect lifecycle again.

Fresh r70/r71 also classify a test-lifecycle defect rather than justify a
timeout increase. The isolated non-restart lane authenticated its terminal
assertion but never published/awaited an exact normal client disconnect before
RCON stop. r71 then remained in vanilla `ChunkMap.processUnloads` for more than
90 seconds and emitted no durable receipt; a focused r69 graceful restart
emitted both exact receipts, including the final one after 7.36 seconds. F0.V
revision 6 therefore makes terminal evidence immutable before a separately
attributed ordinary demand-loss release and durable cleanup. If that corrected
sequence still reproduces the stall, the result is runtime save/unload evidence,
not a reason to hide it with more time. This is V3-AUD-054.

Historically the F0.V runner's first measured same-host three-baseline/three-persistent-client
series failed the then-mandatory numeric floor: its median
improved by 19.57%, then by 23.54% after recovery stopped rerunning Gradle
world preparation, both below the required 25%. Both complete reports and their
bounded failure bundles remain under `build/frontier-v3-scenarios/`; no timeout,
terminal assertion or restart was weakened to turn either result green.

2026-09-05 user amendment supersedes that numeric blocker:25 percent is now
advisory and the later retained22.52-percent reference measurement is accepted
as sufficient optimization. Preserve the original failure reports unchanged;
do not relabel them as historical passes or rerun solely to reach25 percent.
Current-source correctness and actual recovery remain independently required.
The 2026-09-06 product decision makes feedback/provider ratios advisory:
retain the accepted5.065026x local result and measure later real work without a
standalone numeric certification campaign.

Final F0.1 acceptance combines the exact runtime/native evidence at
`3d46ec2634300614246bf6df4839748594539665` and run `34124239973` with the
proof-harness-only follow-up `8217f246079acc9220279ce66386b687c4a7441c`.
The latter changes no runtime, scenario contract, packaged-JAR or launch input;
its deterministic child-listener regression preserves the original causal
failure, captured graceful process/world evidence and cleanup failure in one
bundle after exact PID/port cleanup. Independent selected Node verification is
18/18 and `guardrails check` passes. This closes V3-AUD-043 and the paired-
driver gap only; irreversible crop/output effects and every V3-AUD-044+
guarantee remain open.

Exit evidence: a mechanical registry/composition check rejects a
duration-bearing process without both drivers; `MAT-001` passes never-loaded,
arrival-mid-progress, ordinary unload/return, intervention and graceful/abrupt
restart tests with one process cursor and exact farmer identity; the native
scenario shows later current state without replay, disappearance or a
demand-created start.

### V3-AUD-044 through V3-AUD-051 — seamless-world foundation correction

The scene-owner defect exposed a wider family of observer-dependent
assumptions: historical materialization can become permanent custody, physical
effects can become off-screen schedulers, permanent stack tags reject normal
Vanilla transformations, one scene is treated as a whole army, exact cell
motion is treated as final animation, restart uncertainty can wait for a future
visitor, and ordinary disruption can be mistaken for corruption. HOT/COLD
outcome equivalence is also not precise enough to prevent either scripted
physics or an observer advantage.

The normative diagnosis, target ownership records, ordered
`F0.0 -> F0.V -> F0.1 ... F0.6` migration, negative/recovery matrix and stop
conditions are in `docs/frontier-v3-seamless-foundation.md`. The mandatory
integration-feedback prerequisite and its measurable gate are in
`docs/frontier-v3-integration-feedback-foundation.md`. This audit owns finding
status; those documents own the complete implementation hand-off. New MAT
breadth is paused until all P0 foundation findings close. Existing tests remain
valid only for their proven boundary and must be rewritten when their expected
result encodes one of the rejected FND assumptions.

### F0.VA CI cold-cache preparation defect

Confirmed by source review on2026-09-05: native preparation, reusable workers
and sequential timing allocate private fresh GRADLE_USER_HOME directories but
their first native-environment Gradle invocation is offline. No earlier cache
provisioning exists in those jobs; the preparation artifact contains identity
and plan, not resolved dependencies. Warm local builds cannot establish this
clean-worker prerequisite.

Correction order `docs/work-orders/PM-F0VA-CI-BOOTSTRAP-01.md` requires explicit
online dependency resolution before the existing offline preparation, in each
job's own namespace and outside timing. Worker admission and immutable identity
verification remain mandatory. A scoped failing-before/passing-after workflow
contract guards ordering; actual clean-runner proof remains provider evidence.
No runtime/debt ceiling change is required. The unrelated Mineflayer pilot is
not an established dependency of the native Java execution path.

Local correction accepted: online setup now precedes unchanged offline
preparation in all three native job forms; worker admission remains first.
The workflow regression failed before the fix and passes after it; full Node
130/130 and guardrails check pass. Real provider bootstrap remains unproved.

### F0.VA CI composition defects

Source review on2026-09-05 identifies composition failures not covered
by the existing synthetic merge tests:

- Each shard calls run-f0v-matrix with one --variant/--lane. For a differential
  variant the runner nevertheless dereferences both cold and hot_cold results;
  the absent half fails. Current CI merge checks terminal projection presence
  but does not evaluate the declared relation. The correction must carry exact
  plan-bound evidence and compare both halves at the aggregate owner, never
  suppress the differential requirement.
- run-ci-matrix-sequential calls mergeFourWorkerMatrix after deliberately
  serial shard execution. That merger requires simultaneous overlap and a
  <=30-second start skew, so genuine sequential evidence fails the parallel
  policy. Separate shared correctness/completeness validation from explicit
  sequential versus parallel timing admission; never waive overlap for an
  actual parallel-speedup claim.
- merge-ci-matrix's sequential timing admission does not bind report status,
  plan/build/contract identities or exact lane count to the parallel evidence.
  Plausible timing numbers from a foreign or incomplete matrix must not prove
  the required speedup. Validate these at the final comparison boundary.

Before implementation, define one bounded CI-composition order and failing
deterministic orchestration tests. Also preserve the existing cross-lane
distinct-arrival-checkpoint assertion when distributing its two variants;
isolated per-lane successes alone cannot establish that relation. No native
matrix should be spent diagnosing these deterministic composition failures.
The bounded implementation specification is
`work-orders/PM-F0VA-CI-COMPOSITION-01.md`; local acceptance is recorded below.

Local correction ACCEPTED on 2026-09-05 under that order. Schema-2 evidence
retains and validates declared projections; complete aggregate comparison owns
HOT/COLD and distinct arrivals. Serial and parallel temporal policies are
separate, and final timing binds all identity fields and exact lane count.
Main full Node137/137 and critical gate290/290GameTests pass. Real provider
execution is still unproved; synthetic manifests are protocol evidence only.

### F0.VA persistent-client integration gap

Source review on 2026-09-05 distinguishes the current21 proof from the actual
CI worker path. The accepted F0.VA.1 requirement covers a worker's complete
assigned native matrix. run-f0va-persistent-matrix currently constructs a fixed
smoke-alpha / graceful-before / graceful-after / smoke-beta sequence.
run-ci-matrix-shard instead invokes run-f0v-matrix separately for every lane;
that invokes run-isolated-scenario with a new client lifecycle per lane (its
persistent mode only spans one graceful restart, and crash lanes use fresh
clients). Neither CI workflow calls the matrix-client owner.

Current21 proves the smoke/restart protocol, not adoption by the complete
assigned correctness workload. This is a remaining local F0.VA integration
requirement, not permission to repeat current21 or weaken crash assertions.
After CI-composition acceptance, scope a separate order to reuse the existing
persistent-client owner for contract-derived worker lanes, including declared
crash/recovery handling and bounded failure cleanup. First obtain a deterministic
orchestration regression; retain actual one-client/native proof as a separate
gate. PM-F0VA-CI-COMPOSITION-01 does not grant this lifecycle expansion.

Read-only design accepted under PM-F0VA-PERSISTENT-DESIGN-01. Additional source
evidence: actual worker-0 and worker-3 each own two worlds, while the existing
benchmark validator requires at least three. Separate generic exact-assignment
admission from that unchanged proof minimum. Implementation order is compiler
and real shard preflight (PM-F0VA-PERSISTENT-PLAN-01), authenticated expected
crash handling in existing owners, actual runner integration, then native
proof. No partial cut closes this integration finding.

PM-F0VA-PERSISTENT-PLAN-01 local compiler/preflight ACCEPTED: exact assignment
and trusted identity checks,1/2world admission without benchmark weakening,
all13lanes and5crash windows. Main review caught inherited crash metadata on
split executable scenarios (restart removed); arm is now only segment metadata
and every executable half must pass the existing scenario validator. Full
Node144/144 and critical290/290GameTests pass. Real runner/client lifecycle
integration is still pending; the compiled artifact is not lifecycle proof.

Further source-backed adoption checks,2026-09-05: the current matrix client
retains only terminal assertion/diagnostic counts in its result, whereas
ci-matrix.evidenceFromManifest consumes actual declared diagnostic projections.
The real integration must retain bounded per-lane diagnostic evidence, validate
all declared assertions and preserve terminal/differential/arrival extraction;
counts or a new status flag cannot substitute for the existing schema2 merge.
Keep original compiled scenario identity distinct from its exact serialized
runtime bytes and explicitly map the CI portable build hash to the verified
local prepared identity (the smoke owner currently hashes the full local build).
These are existing evidence-consumer contracts, not new gameplay scope.
Main inspected the actual generated13-lane plan: all current lanes have zero
frames, worker3 is exactly abrupt release-boundary plus graceful restart. Do not
add frame breadth to this adoption proof or claim that this proves visual/M3
acceptance. Crash/session protocol is ACCEPTED under PM-F0VA-PERSISTENT-CRASH-01:
focused Java12/fullNode148 and critical290GameTests/build/package pass. Actual
runner integration is specified by PM-F0VA-PERSISTENT-RUNNER-01, not yet proved.
Further confirmed defects to cover there: readiness PID is overwritten before
comparison to the retained spawn PID; diagnostics carry action step but lack
session/epoch/segment attribution and are bucketed by mutable current. Add
consumed deterministic regressions before native qualification. This finding
stays open until actual assigned-worker one-client evidence, not helper passes.

Revision2 scenario reconciliation: the shared abrupt harvest declaration keeps
the player beside the HOT worker, yet its release-boundary recovery requires
HARVESTING/complete:false. Source work/release conditions only permit partial
drain after lost demand and safe player distance; natural64crop completion
contradicts that terminal invariant. The runner order therefore admits one
ordinary departure copied from unload_return after action3 and changes the
declared restart offset to4, retaining all5windows and every recovery assertion.
Existing crash semantics are a non-replayed recovery half: a genuinely earlier
parked probe can interrupt the pre-half; unreached actions/assertions are not
claimed completed/passed. Conversely, exhaustion before a later probe keeps the
same client awaiting that actual causal input within its existing deadline,
not normal disconnect, premature terminal success or an unbounded sleep.
All13lane identities/four-worker memberships remain unchanged in a declaration-
only calculation; changed scenario hashes require fresh evidence. This finding
is OPEN pending deterministic integration and subsequent physical qualification;
no canonical scene/runtime change or product-promise weakening is authorized.

## Explicit non-defects

- Immutable canonical state and fail-closed complete validation are retained.
- A closed exhaustive reducer or codec switch is acceptable when it is the one
  owner and uses stable wire tags.
- Test fixtures are useful and remain supported; only their production
  packaging/selection is defective.
- A single canonical writer and one total scheduled order remain invariants.
- Current performance is not declared defective without a reproducible JFR or
  benchmark comparison.

## Remediation order

The implementation plan owns execution. The original hardening sequence through
V3-AUD-036 is complete. Execute the ordered F0 programme in
`docs/frontier-v3-seamless-foundation.md`: the completed F0.0 baseline is
followed by mandatory F0.V integration feedback before the preserved
V3-AUD-043/F0.1 process-ownership work resumes. V3-AUD-044 through V3-AUD-051
then correct replica custody, aftermath, resources, navigation/fronts,
recovery/failure policy and observer-neutrality. Only after F0 may the
materialization-completeness plan resume exact assigned movement, human
lifecycle, hive metabolism, distributed environment and decision comprehension.
Do not add another endpoint-only executor as a shortcut. Each correction lowers
the checked-in debt ratchet. Raising a baseline requires an accepted
architecture amendment with a new finding and removal plan; it is never an
ordinary implementation edit.
