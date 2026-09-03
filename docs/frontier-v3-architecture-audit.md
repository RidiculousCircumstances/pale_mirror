# Frontier v3 simulation architecture audit

Status: accepted defect register and remediation input.

Audit date: 2026-08-31. Audited revision: `5ca5291`. Scope: the pure v3
kernel/model, persistence codecs, NeoForge lifecycle and physical execution
boundary. This is a static architecture audit; it does not replace the annual,
JFR, restart or player-visible gates in the implementation plan.

## Classification

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
terrain replan, bridge/rail grades or a completed assault departure.

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

The implementation plan owns execution. In summary: stop adding scene/domain
breadth; close scene behavior and fixture isolation first; then split domain
and physical dispatch; then replace positional state updates and unstable wire
tags; introduce the versioned ruleset; finally measure and, only if necessary,
optimize complete-domain pressure. Each correction lowers the checked-in debt
ratchet. Raising a baseline requires an accepted architecture amendment with a
new finding and removal plan; it is never an ordinary implementation edit.
