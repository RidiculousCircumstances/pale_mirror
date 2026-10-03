# Continuity Ledger

Active scoped repair (2026-10-01, main alone/no subagents): user accepted
fundamental COLD/HOT placement + service occupancy separation, requested SOLID
implementation. Checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery`,
base9f979615; implementation WIP, no commit/push/deployment authority this turn.
Added epoch-bound `AmbientBodyConfirmed` (ADMISSION/SERVICE_OCCUPANCY), closed
ambient placement Strategies and typed service-point geometry; ambient admission
now confirms actual supported body atomically before HOT. Minecraft placement
checks bounded connected free candidates, support, physical collision and body
occupancy, with no force-load or HOT teleport. Meal-owned hook preserves claims
and reapproaches after relocated unbegun TAKE. Coordinator excludes PREPARED
phantom occupancy; denied arrivals can exit, actual HOT occupants arbitrate
ahead of COLD endpoints, idle actors clear the point through shared navigator.
Scene-reserved pre-lease confirmation holds rather than arms ambient motion.
Canonical execution/resource contracts and architecture map updated; scope is
resident depot access, not every scene family. Live server remains9f979615.
Focused39 Java checks previously passed; native new placement case passes after
using shared actual ground-contact observation. Local-navigation last27/28 had
old descending fixture still manually ticking retired actuator: corrected to
shared physical lifecycle, final run pending. Integration check exposed model
dependency/1000-line guardrails (corrected without raising ceilings), legacy
fixtures using now-rejected bare PREPARED→HOT commands (updating to explicit
unit/component receipts), and overly broad construction census treating a
dimensions query as entity creation (narrowed to compiled creation calls).
Final verification in progress; no new live/client/product acceptance claimed.

Live follow-up incident after9f979615 (2026-10-01): user reports brief movement,
security stalled at depot, farmer appearing beside entrance, then stationary
residents. Read-only RCON: runtime remains green, ticks448521→457504,
no required/inventory/custody conflicts. Clearwater resident:7-4 physically
occupies depot service body135/65/14 but retains canonical104/65/-30,
mealMOVE, serviceAvailable=false. Resident:7-11 retains135/65/14,
mealTAKE, serviceAvailable=true, ambientPREPARED and no physical body.
Exact source explains circular wait: ambient meal arrival stops7-4 before
checking hotArrivalHasServiceTurn; denied arrival is not recorded and leaves
it physically in the socket. Known meal navigation also returns singleton
start when already at service, without checking admission. Coordinator grants
the turn from canonical positions to7-11, whose physical admission rejects
the occupied body volume. Neither participant can advance. Admission UI
READY checks standing geometry but omits foreign-body occupancy, so obscures
the deferral. goalPositionY64 is SUPPORT, observed/bodyY65 is FEET: normal,
not evidence of a height defect. Farmer7-13 is PATROL/IDLE at135/65/13;
its reported appearance provenance and separate home-route stalls remain
unresolved. No code change/restart/reset in this diagnosis; preserve live
failure. Next repair must reconcile physical occupancy with service arbitration
and provide a legal yielding path, not relax arrival or allow overlapping bodies.

Latest live deployment (2026-10-01 14:00 local): user requested deployment of
the single HOT pedestrian-navigation correction. Source checkpoint
`9f97961558ff8165ed7a9635dd08b6a9feedc01f`, implementation clean; no push.
Clean detached build `/home/rd/proj/pm-single-navigation-release.Zk6J7D/source`
passed explicit graybox-catalog assemble/verifyPackagedJar; applicable focused
native/architecture/guardrails evidence retained below, no confidence reruns.
Installed SHA512:
`fba095ca2ab841bf9775844b0ab0e563656c6929eadaa61b0effb26e5000bd060e4b0aba8bc5125ccf818ffe85c61e9850b2fd0782a83756e01c32fcbfbb477c`.
Preflight passed, players0, save-all flush completed before graceful stop.
R9 world preserved, stopped-world backup
`/home/rd/far-frontier-server/.single-navigation-deploy-backup.qP3NmJ/`.
Checksum-pinned root installer archived the old JAR; no public publication,
client-required change or world reset. Service invocation
`2d3b2c2272644eb3b50ead37016a2822`, MainPID1441728, notBefore1790845246;
fresh ready14:00:57/runtime14:00:59 and deploy verifier pass. Live summary
revision367656/tick445919: OK/green, requiredConflicts0, inventoryConflicts0,
replicaCustodyDiagnostics0. This supersedes all not-deployed/WIP and33c3 live
statements below. Clearwater real-player passage/meal/farmer acceptance remains
open; healthy deployment is not HUMAN_CANDIDATE. Outer repo retains only
pre-existing `.f0v-baseline/`; governance historical WIP preserved.

Active movement incident/adoption (2026-10-01): user requires ALL target-directed
HOT movement to use the shared navigator, not parallel legacy actuators. Main
alone; no subagents. Read-only live sampling reproduced Clearwater security
resident:7-16 and logistician:7-18/7-12 repeatedly jumping against the same
edge near x135.3/z8-10 while their meal remains MOVE. This is not merely idle
presentation. Source-proven immediate cause: RouteNavigation installs LEGS,
then MinecraftGoalNavigation acquires a path via ControlledMobMotion.stop;
that recursively calls GoalNavigation.stop and deletes that very LEGS entry.
Every following pursue starts another leg/path and zeros in-flight velocity.
WIP separates legacy-actuator retirement from whole-navigation cancellation.
Also audited: path exhaustion drops physical control before confirmed station
arrival, stop does not clear jump state, and several scene/ambient families
still use direct movement. User authorizes connected migration of remaining
target-directed callers to the single navigator; stationary work gestures and
ordinary gravity are not route owners. No fix deployed yet; latest live bytes
remain33c3d1c3 below. Acceptance must exercise repeated production-cadence
refresh, real +1 ascent, and stopping/handoff, not only one acquisition or a
manual loop that omits the route wrapper.

Connected source correction is now implemented (uncommitted/not deployed).
Native acquisition retires only legacy directives, preserving LEGS and
in-flight velocity; exhausted native paths retain physical integration until
same-goal replanning/observed arrival; explicit cancellation clears jumping
and movement inputs. All active target-directed pedestrian callers now use
GoalNavigation: ambient directed travel, assemblies/hive return, production
and settlement service edges, engineering, medical, patrol, logistics and
assault. Stationary ambient actors no longer run a separate orbit actuator.
Native path stop calls are confined to navigation/legacy station-actuator
owners; the hive-return non-arrival observer no longer cancels its own path.
Existing COLD semantic checkpoints, custody and rail/cargo execution remain
unchanged. Focused architecture checks reject direct legacy travel and partial
path cancellation from production callers. Native local-navigation27/27 pass
at `/tmp/pm-single-navigation-native-r2.log`, including new repeated routed
refresh +1 ascent/landing and path-object stability. First25/27 had two old
production tests manually ticking only the retired actuator; updated them to
the shared physical lifecycle, preserving exact arrival/clearance checks.
Final compile, focused architecture tests, guardrails, assemble and packaged
JAR verification pass (`/tmp/pm-single-navigation-build.log`). No broad CI,
client campaign, commit, push or live deployment. Real Clearwater retest
remains open on a future deployed pinned artifact; live still33c3d1c3.

Latest live deployment (2026-10-01 13:21 local): user requested deployment of
the shared HOT navigation correction. Local source checkpoint
`33c3d1c3c5da89bf3b2355a70185911a2d774e23`, clean implementation checkout,
no push. Verified detached build `/home/rd/proj/pm-navigation-release.11vbbP/source`
with explicit graybox catalog; assemble/verifyPackagedJar and deployment
preflight passed. Installed JAR SHA512:
`4ca1a0b2be1da9c837e49fa8d615c4d279dfb55565ca6f820cbec3c861d1db4ca1a81aa64c295281d740dc551c4329ed5a7b8e4c5ec1d34a5a7c3dcccec439e9`.
Zero players confirmed; save-all flush succeeded before stop. R9 world
preserved; stopped-world backup
`/home/rd/far-frontier-server/.navigation-deploy-backup.zpeO5m/`.
Checksum-pinned pack installer archived the previous JAR. No world reset,
client-required change or hosted publication. Service invocation
`25fbae28a1944e82be666a974430a6b1`, MainPID1367313, notBefore1790842880;
fresh ready13:21:30/runtime13:21:32 records and deploy verifier pass.
Post-start revision329778/tick399330: OK/green, requiredConflicts0,
inventoryConflicts0, replicaCustodyDiagnostics0. No new native confidence run.
This supersedes the navigation WIP/not-deployed statements below. Exact live
medic/meal/farmer player-visible retest remains open; healthy startup is not
that acceptance. Outer repo still only pre-existing `.f0v-baseline/` dirty;
historical governance WIP preserved.

Active navigation correction (2026-10-01 13:02 local): user accepted the shared
HOT route/path-policy design and requested implementation with SOLID boundaries.
Main alone implemented in the same checkout over `f38a28a1`; source WIP is not
committed or deployed. `FrontierV3GoalNavigation` is the facade;
`FrontierV3RouteNavigation` owns discardable local legs of the unchanged final
order; explicit sealed `FrontierV3NavigationScope` Strategies distinguish true
restricted topology from observed-world bounds; `FrontierV3PhysicalPathPolicy`
owns common loaded/dry/bounded candidate acceptance. Accepted native paths
supply the ephemeral corridor; native path changes are revalidated. Meal,
service exit, bakery and farmer now use this shared routed-goal boundary;
their local stripe/waypoint implementations were removed. Farmer alternative
feasibility also uses the common policy. Existing explicit hard-envelope
navigation is preserved, not silently widened. Final arrival only can complete
the order; micro-waypoints cannot award work/meal/custody effects.
Known COLD routing, actor identity and checkpoint/fence ownership are unchanged;
HOT detours do not manufacture COLD knowledge or load chunks. Physical retries
back off, except changed grounded readiness permits a fresh attempt; accepted
path progress replaces straight-line goal-distance progress for legal detours.
Farmer search/path budget exhaustion has explicit stable reason tag11 and UI.
Canonical navigation/semantics/AGENTS/architecture documents amended together.
Verification:23 focused JUnit/architecture checks pass, guardrails, assemble and
verifyPackagedJar pass; canonical architecture validates. Final native
local-navigation slice:26/26 passed, log `/tmp/pm-hot-path-native-final.log`.
Two added native cases establish real detour beyond the old stripe without
false intermediate arrival, and same-order recovery after physical clearance
without reconnect. First slice25/26 exposed stale failed-query caching across
physical grounding changes in a multi-turn/same-game-tick field fixture; source
cache invalidation now accounts for grounding, then the complete slice passed.
No broad CI/confidence reruns, live-world mutation or product completion claim.
Exact live medic/meal/farmer retest remains open on a future deployed artifact.
Live server is still the deployment checkpoint below, NOT these WIP bytes.

Deployment checkpoint (2026-10-01 12:08 local): user requested deploy. Main
committed the verified execution-owner change as
`f38a28a1311ced383af88cf6186095aef55a4f69`; implementation checkout is clean,
no push. Clean detached release: `/home/rd/proj/pm-execution-release.eo30cX/source`.
Explicit graybox catalog assemble/verifyPackagedJar and deployment preflight
passed. Installed SHA512:
`062163f3376d0f051c43b73e2f54f4a20f90386c5243218228506b55ac39a93148ece4ce794160a36f06cbee28c813786117d6ff534e1da734b0d02c371f6ef8`.
Zero players verified; save-all flush completed before stop. Existing R9 world
preserved, stopped-world backup at
`/home/rd/far-frontier-server/.execution-deploy-backup.y9igDX/`.
Old JAR archived by checksum installer, no reset or client pack change.
Service invocation `3b1bb426cc5d418b988ebd66da578c48`, MainPID1236363,
notBefore1790838474. Fresh ready/runtime records and deployment verifier pass.
Live summary revision252716/tick311396: OK/green, requiredConflicts0,
inventoryConflicts0, replicaCustodyDiagnostics0. Two retained ambient HOT
leases became UNKNOWN pending loaded-world recovery at startup; this is not
proof of successful player-visible recovery. Farmer successive HOT cycles
still require retest; medic corridor defect remains unmodified. This supersedes
the source-WIP/not-deployed statements in the previous checkpoint below.
Outer deployment repository still has only pre-existing `.f0v-baseline/`;
governance historical WIP retained. No native/product completion claim.

Active implementation (2026-10-01): user authorized one fully integrated actor
execution coordinator; main alone, no subagents. Source checkout unchanged,
over diagnostic `c86538f3`; live server still that diagnostic build/R9.
`ActorExecutionCoordinator` WIP centralizes authority admission, common COLD
exclusion and actual atomic ambient-to-scene capture. Farmer/baker start,
meal/movement COLD guards and ResidentWorkYield are wired. The incorrect
historical-position equality is removed. User caught concrete bakery/harvest
checkpoint methods leaking into this generic owner; corrected to typed
`ActivityExecutionCapability` Strategies in a closed exhaustive registry,
family-owned support implementations and immutable-state/exact-assignment
checkpoint evidence. AGENTS architecture rule and execution semantics now
forbid that responsibility mixing and helper camouflage. Initial affected
domain tests/NeoForge compile pass, and3 coordinator regressions pass before
Strategy correction. Post-correction tests running; integration bypass audit,
negative Strategy evidence/architecture checks and final packaging were pending.
They are now verified:144 affected checks passed before the final common scene
preparation boundary addition; the final affected scene/owner/architecture run,
assemble/packaged-JAR/style/size pass after that addition. Guardrails pass;
canonical architecture validates. A read-only evaluation of the exact original
Clearwater snapshot185907 now returns AmbientTransfer(actor7-13,epoch8) and
the retained start action proposes ResourceSiteHarvestStarted rather than only
a retry; no live state was mutated. The actual transfer has codec-recovery and
duplicate/competing authority rejection coverage. Farmer/baker start reducers
also enforce the gate, all scene preparations and actual handoffs enter the
common owner, ambient retarget delegates to it, and meal/movement/harvest/bakery
COLD guards use it. Family-specific safe-stop checks stay in their owning
support implementations. Unsupported work families remain explicit safe holds,
not newly implemented suspension capabilities. No graphical/native completion
claim: live farmer succession and the earlier closed-body meal return still
require their own evidence. Changes remain source WIP, not committed/deployed.
No deployment, commit, push or completion claim for this WIP. Medic corridor
defect is separate and not changed by this execution-owner refactor.

User visit evidence (2026-10-01 ~11:15 local): rd visited Clearwater and used
fast-forward; left11:11:06.888. New path trace establishes medic7-17's exact
HOT rejection: Minecraft returns reachable=true,9 nodes, target support
(140,63,-8), but node3 feet(144,64,-5) is outside the local envelope. Feet are
dry; wheat there is not evidence of an impassable block. The provider rejects
a reachable detour because the retained 4-waypoint local leg plus radius2
does not cover it. Meal route/cache retries do not expand/replan after that
rejection. This is a confirmed overly restrictive HOT corridor, not no path.
No repair implemented. Existing service-wait trace also shows baker stopping
mid-approach for successive ordinary meal occupants, then resuming; it reaches
and executes bakery effects10:56:51,10:57:02,10:58:22/33 without reconnect.
Farmer7-13 consumes10:52:11 and10:59:54 and exits service normally. Previous
harvest epoch5 terminal receipt is confirmed. Exact pre-exit snapshot185907
(written11:11:04, tick243369) has field READY/no active work, farmer idle,
requestsYield=false and ambient HOT/PATROL: canonical body(150,65,-30), retained
handoffBody(135,65,13), goalBody(150,65,-30). Source
`ResourceSiteHarvestPlanning.retainsExactAmbientHandoff` requires canonical
body to equal BOTH handoffBody and goalBody. The historical depot handoff can
never equal the reached home; ordinary HOT custody makes the alternate
`FrontierSceneAdmission.available` gate false. Thus the next harvest is
self-blocked at job start, not field scene readiness. After departure the
ambient lease closes, snapshot186475/tick243969 has new job/candidate with
zero completed cells; snapshot187019/tick244569 has1, and later live query19.
This resolves the new-epoch farmer stall's exact source contradiction. The
earlier farmer TAKE/closed-body blockage remains a separate unresolved seam;
do not conflate it with this successfully witnessed feeding/return cycle.
Runtime remains OK/green/no required conflicts. Fast-forward reports13000
completed ticks; other requests rejected while a request or physical scene was
active. Do not classify every rejected command as a simulation failure.

Active diagnosis (2026-10-01 ~10:15 local): user asks for exact causes of
feeding/bakery stalls and permits additional logs. Main alone; no repair or
completion claim. Local diagnostic commit `c86538f3` in
`/home/rd/proj/pm-f06r3-facility-lane-recovery` preserves behavior and adds
bounded failed-path explanations (null/unreachable/first rejected envelope
node and physical blocks) plus change-only actor handoff/service-wait traces.
Compilation, one existing resident meal navigation test, guardrails/style/size,
assemble and packaged-JAR checks pass. No full client acceptance or push.
Clean detached release `/home/rd/proj/pm-wait-diagnostic-release.0pOKNK/source`
installed through the pack checksum-pinned helper after zero-player save/stop.
JAR SHA-512
`ccf8c9ad41926769b5cff6ba444b27efb63b44919850396c90e5231fc8b10a33fe5e347e495b90b16d094d678118ca4400dcf4d67ef644d27d07abd237e6d6c1`.
R9 world preserved; startup10:13:51, fresh deployment verification passes,
service invocation `39cdbeaab4a8429fb0a0ff4c247c4470`, no new quarantine.
Source/release clean; outer pack retains only pre-existing `.f0v-baseline/`;
governance historical WIP preserved. Lightweight ordinary observer was rejected
before spawn because this server requires genuine NeoForge21.1.248; no player
setup or teleport occurred. No observer/HTTP task remains. User was asked for
a short Clearwater visit; new diagnostic lines have not yet been produced.

Read-only immutable snapshots establish a specific service-blocking chain:
revision86579/tick131339 (before disconnect) has farmer7-13 meal TAKE at the
depot, admitted=true but ambient lease CLOSED/WORK, no active reservation and
no physical step. Depot physical custody is still operational, so COLD TAKE
cannot execute; HOT TAKE requires a HOT/MEAL carrier. This farmer occupies
the service boundary, blocking other meals and baker DEPOT_PICKUP. The bakery
executor stops even its approach when service is unavailable. At revision86765/
tick131539 (after disconnect), depot custody is no longer operational, farmer
meal is gone and a separate ServiceExit movement exists; bakery has a newly
PREPARED lease. Thus unloading removed the physical-custody barrier and allowed
the farmer meal to finish, explaining this reconnect-correlated queue recovery.
The lower-level reason why the retained farmer body did not acquire HOT/MEAL
ownership remains UNCONFIRMED; new ownership/wait logs target that seam.
Medic7-17 is a separate persistent Minecraft path rejection, not established
as the same blocker. Its exact rejected path/node awaits the new trace.

Reconnect correlation (2026-10-01 ~09:46 local, read-only): user reports
residents waking after reconnect again. Log confirms disconnect09:45:16.449,
bakery old lease draining/release09:45:16.569–.601, new lease prepared. After
join09:45:20.715 the new bakery lease becomes HOT at09:45:20.894; residents
7-1/7-20/7-19 consume at09:45:21/25/26 and bakery effects follow at09:45:28/39.
This supports reconnect-correlated recovery, NOT a successful fix or proof
that every actor has one cause. Medical7-17 still reports path-unavailable
after reconnect. Exact pre-reconnect blocker/reset remains unresolved; inspect
HOT navigation retention and service-access witnesses before implementing.
No code changes, restart or new test campaign.

Live follow-up (2026-10-01 ~09:40 local, read-only): user saw disappearing
crop cells before the farmer appeared a few seconds later, then hungry
residents apparently not eating. On deployed `c4d32fe1`/R9 the runtime is
OK/green with zero required conflicts. Settlement7 initially has19 nourished,
one hungry/one starving,34 bread and two active meals. Resident7-7 consumed
at09:37:40 and7-15 consumed at09:39:36 without reconnect, proving feeding is
not globally stopped; second census has7-15 nourished/no meal,33 bread/one claim.
Medical resident7-17 remains STARVING in HOT meal MOVE with available depot
access, retained overdue action23302 and physical pose near(144.95,64,-7.49).
At09:37:16 its navigator reported minecraft-path-unavailable; current actor
diagnostic still shows IDLE motion. A local meal-navigation stall is supported;
the exact terrain/path cause is not yet established. Farmer7-13 is alive,
SCENE_OWNED/HARVESTING at(143,64,-5); delayed visual appearance's cause remains
unproven. No code edits, restart or new test campaign from these reports.

Calendar deployed (2026-10-01 ~09:35 local), per explicit user request.
Main locally committed `c4d32fe1e71aa3e2be99ca697e40f8893f2ee946`, no push.
Clean detached release `/home/rd/proj/pm-calendar-release.ZfAsZg/source`
assembled/verified in 8s using trusted compilation cache. Installed JAR SHA-512
`0beba40c29c64796c4519d94cf51d0234a571c7e5bd025714e136e7d3b9d1b98720268898a9927aa25fb29db2ed890eb7ecba2b9f7c734add82465bcd5943bdd`.
Zero players online before normal save-all flush/service stop. Pack installer
archived prior JAR and preserved R9 `frontier-v3-body-observation-r9-20261001`,
seed `20260918065`: calendar adds no schema/physical-evidence format requiring
a reset. Preflight and fresh post-start verification pass (notBefore1790829296).
Live service active since09:34:56, main PID853984 / Java854008, port25565;
v3 recovery/startup09:35:08, no new quarantine. Two ambient leases retained
UNKNOWN pending ordinary loaded-world recovery. Read-only RCON status OK,
366 residents/12 sites. Bracketed live phase check: canonical120743→120744,
calendar743→744, actual graybox `time query daytime`=744. No canonical
advancement or world mutation was used for that diagnostic. First deploy verify
was too early (socket not bound yet); after ordinary startup it passed.
Temporary artifact HTTP18092 and installer HTTP18091 stopped. Source/release
clean; pack only pre-existing `.f0v-baseline/`; governance WIP preserved.
No client update needed. This is a diagnostic deployment, not graphical
acceptance or complete farmer-cycle/F0.6R3 closure.

Active task (2026-10-01): user accepted isolated canonical calendar/daylight
synchronization with explicit responsibilities. Main alone implements in the
same source checkout over `812db5a6`; no subagents. Pure SimulationCalendar and
schedule integration, dimension-scoped MinecraftCalendarPresentation with
coherent Level/ServerLevel read-only day-time aliases, independent host event
bridge and thin v3 binding are wired. Native gameTime/Overworld/unrelated data
remain untouched; actual reached canonical time is read without snapshot
encoding, including quarantine. Sleep and vanilla time writes cannot rewind
the calendar. Operator status exposes day index/phase and bounded lunar time.
Canonical invariant/execution semantics and work order
`docs/work-orders/PM-F06R3-CANONICAL-CALENDAR-01.md` updated. Implementation
verification complete: 14 focused domain/architecture checks pass; related
NeoForge run passed 58 unchanged checks, while the new diagnostic test initially
parsed the existing PMV3_DIAG prefix as JSON. Corrected to use the existing
prefix boundary; all three calendar runtime tests then pass. One native
calendar GameTest passes (08:39:09 local); server shuts down normally, no test
JVM remains. Final guardrails/style/size/debt/assemble/packaged-JAR gate passes
in 22s; canonical architecture also validates. Initial static checks caught
existing aggregate/test line ceilings; validation/assertions moved to their
support/focused owners without raising limits. Calendar is wired, not merely
a library. No commit/deploy/push; live still R9/`812db5a6`. Graphical client
sun/fast-forward acceptance and full farmer lifecycle remain OPEN. Outer pack
unchanged except pre-existing `.f0v-baseline/`; governance historical WIP
preserved. Next useful step is an authorized exact diagnostic deployment and
player-visible time/schedule check, not another reassurance matrix.

Body-observation deployment (2026-10-01 ~08:03 local): user requested validator
repair and deployment. Main updated source and governance validator copies to
accept a free-form nonempty brief, retaining 1000-line/archive checks; five
validator tests and both current ledgers pass. Guardrails pass. Source changes
checkpointed locally as `812db5a61f680c742556c60844f64e36b3c6dd98`, no push.
Clean detached release `/home/rd/proj/pm-body-observation-release.zFGslt/source`
assembled/verified JAR SHA-512
`e58b1b785c79f5c24b3bd0776d0ff20afc38b4b776e9832ccb02656e7d85b415f948ce9b70677fc0d4b56bae7ed3f91fdd9009af716ac85816f4626f6792d143`.
Zero players online; vanilla save-all flush acknowledged before normal service
stop. Installer archived previous JAR and selected fresh
`frontier-v3-body-observation-r9-20261001`, seed `20260918065`; R8 remains intact.
Preflight and fresh post-start deploy verification pass (notBefore1790823747),
service main PID536046 / Java536092, port25565, startup08:02:48 with no
quarantine. RCON instant426/revision378 reports status `ok`, 366 residents,
12 sites. Temporary artifact/pack HTTP18092/18091 stopped; no task test JVM.
Source/clean release clean; outer pack only pre-existing `.f0v-baseline/`;
governance historical WIP preserved. No client-required/network changes, no
world deletion. Diagnostic deployment is NOT full farmer lifecycle or human
candidate acceptance; field HOT→COLD→HOT/player evidence remains open.

Unified physical body observation (2026-10-01 ~07:50 local): user authorized
the common mechanism after read-only root-cause investigation; main alone
implemented WIP in `/home/rd/proj/pm-f06r3-facility-lane-recovery` over
`9064fbde`. R7 snapshots 122→123 retain the correct harvest/ambient handoff
feet `(137,64,-1)` but canonical `resident:7-13` becomes `(137,63,-1)`.
Ambient WORK release's independent `getBlockY()` floors fractional farmland
feet. `FrontierV3BodyObservation` now owns contact-based support normalization
and explicitly distinguishes unsupported poses. Ambient/scene captures,
handoffs, release policies, departure/rejoin, caches and semantic arrival share
it; airborne poses cannot confirm directed supported arrival. A vanilla
`saveWithoutId` hook saves a versioned observation bound to exact serialized
pose. Offline scene-departure evidence requires that witness; missing/stale
or unknown evidence is rejected, never reconstructed from floored Y. A future
deployment needs a fresh disposable world, not an old-NBT compatibility path.
Plan: `docs/work-orders/PM-F06R3-PHYSICAL-BODY-OBSERVATION-01.md`;
execution semantics and architecture invariant updated in canonical governance.
Verification: 54 focused NeoForge tests passed; final `harvest-support` native
slice 3/3 passed, including farmland/slab, airborne semantic rejection and
actual vanilla-save hook. Guardrails, final style/size, compile, assemble and
packaged-JAR verification passed. Final combined gate took 32s. The
source-checkout guardrails use their local ledger copy; separately invoking
the older ledger validator on canonical governance fails on its now-abolished
mandatory `## Goal (success criteria)` heading. This is an existing governance
validator mismatch, not a physical-code failure; do not invent a new workflow
gate or change instructions to satisfy it. Canonical architecture validates.
The initial
gate used a nonexistent size task; the corrected gate exposed the expanded
navigation test file's 1005-line limit, fixed by moving its existing capture
test into focused `FrontierV3BodyObservationGameTests` (no limit waiver).
Architecture recurrence check rejects independent pedestrian floor-Y body
construction. No commit/push/deploy/reset/subagents; source WIP retained,
outer pack untouched except pre-existing `.f0v-baseline/`, governance existing
WIP preserved. Test server exited normally. Live remains `9064fbde` / fresh R8;
full field lifecycle and HOT→COLD→HOT/player acceptance remain OPEN. Next:
review/checkpoint this coherent source correction, then exact diagnostic
deployment and focused full-cycle observation if authorized; no broad matrix.

Field-standing incident correction (2026-10-01 ~00:43 local): main alone
committed `9064fbde` in `/home/rd/proj/pm-f06r3-facility-lane-recovery`.
The live R7 farmer `resident:7-13` stayed at canonical feet `(137,63,-1)`
while that cell was farmland; physical admission was BLOCKED, but a PREPARED
harvest lease held its COLD continuation for >30,000 canonical ticks. The fix
rejects an obstructed body-free harvest admission and durably retires an
already PREPARED or restart-UNKNOWN lease only with exact, unstarted BODY
authority, leaving farmer identity/position and COLD route intact. Focused
domain tests, guardrails, package verification and two native Minecraft
harvest-support GameTests passed. The first same-world deployment correctly
quarantined because the new event changes the closed descriptor fingerprint;
the previous JAR was restored and its startup reverified. The v3 contract
forbids compatibility migration, so the new checksum-pinned JAR
`1b409901c93fbb59ee3c3ea59531555f92b3ae3b1523aade5a992c363442a043c6d78b57f8a033ccd2d1886ac47061d53502ff85a50d5b3026cf6fb09ee3f3bc`
was deployed from clean detached `9064fbde` into fresh world
`frontier-v3-field-standing-r8-20261001` (seed `20260918065`). Preflight,
post-start verify and RCON status passed: service active on port 25565, 12
sites, 366 residents, status `ok`. The R7 incident world remains intact on
disk. HOT/player field and HOT→COLD→HOT acceptance on the fresh world are
still OPEN; do not claim the complete farmer cycle or F0.6R3 closed. The
apparently nocturnal farmer after canonical fast-forward was a debug-clock
mismatch (canonical time advances, vanilla sun does not), not proven schedule
failure. No push or subagents; source checkout clean, outer pack retains only
pre-existing `.f0v-baseline/`.

Field-cadence diagnostic deployment (2026-09-30 ~23:49 local): player left
before the switch. Main alone committed `746c630d` in
`/home/rd/proj/pm-f06r3-facility-lane-recovery`: production ruleset R7
(schema 9) separates HOT field checkpoint cadence (1 tick) from COLD field
travel (20 ticks/edge); overdue HOT-held COLD actions rebase successor deadlines
to the actual current simulation tick instead of rapidly replaying elapsed
HOT time. Prior R6 selector/digest remains installed unchanged for preserved
worlds. This addresses source-proven 19-slot field jump across ~4 seconds of
HOT→COLD→HOT, not all field, bakery or meal liveness. Focused ruleset/harvest
tests, NeoForge compilation, style/size/debt checks, clean detached assemble
and packaged-JAR verification passed. New diagnostic JAR SHA-512
`268aa525ec3fff51a0623772f1dcf2db0adc4ff1202a0e19dc814393f4f85a45b8eaa308497d08bd6053ae09b143e212168a2da652ea1afde0b238a95638c40a`
from clean detached source `/home/rd/proj/pm-field-cadence-release.TN09cB/source`
was installed via the pack installer. Preflight and fresh post-start verify
passed. Service `far-frontier-v3-live.service` is active, PID 3584986,
port 25565, new world `frontier-v3-field-cadence-r7-20260930`, seed
`20260918065`. Read-only RCON status at instant 590: OK, 366 residents,
12 sites; the new snapshot contains `frontier-v3-production-r7`. Old world
`frontier-v3-meal-field-retest-20260930` remains on disk untouched. No
client/player HOT field or bakery acceptance yet. Ironmeadow baker's apparent
relog-dependent delivery remains unresolved: live traces showed several HOT
bakery effects before relog and `bakery_cold_hand_materialized` afterward,
but the pre-disconnect job phase was not captured; do not infer a fixed cause.
No push or subagents. Source checkout clean; outer pack still has only its
pre-existing `.f0v-baseline/` untracked. Next: one bounded manual field and
bakery observation on the exact new build; correlate exact job/instant before
any further correction, avoid a broad reassurance campaign.

Live meal follow-up (2026-09-30 ~23:24 local): the player reported bursts of
eating separated by pauses and apparent renewal after reconnect. Read-only
server trace for settlement 8 confirms eight HOT bread consumptions after the
last 23:22:40 login without another reconnect; at instant ~117,653 the census
was 37 nourished/two hungry with two active meals, and shortly afterward all
39 residents were nourished with no active meal. Runtime stayed OK/green with
zero required conflicts. Thus a permanent meal deadlock or reconnect-required
wakeup is not established in this interval. One post-meal movement logged an
`outside-retained-envelope` wait and later arrived; treat it as a separate
transient movement signal, not proof of the reported queue cause. No speculative
fix or added test from this observation; diagnose again against an exact
resident/instant if a prolonged pause occurs while residents remain hungry.
At ~23:27 a live capture found hungry security resident `8-4` in MEAL/MOVE,
HOT, with service access available and physical position `(355,65,11)` while
canonical body remained `(344,65,-30)`; this HOT projection lag alone is not
a stall. The *same resident* reached depot, consumed bread at 23:28:02, exited
and completed return movement at 23:28:19 without a player reconnect after
23:24:57. Earlier characterization of `8-4` as definitively stuck was too
strong. The player-visible multi-minute gap is real; whether a HOT route pause
or long side-pocket approach dominates remains unproven. Do not write a
speculative fix/test or claim reconnect is necessary for meal progress.

Current Clearwater/Ironmeadow diagnostic checkpoint (2026-09-30): on prior
`d5dbcecb`, the player saw a partially harvested field and missing farmer after
HOT→COLD→HOT, delayed completion after reconnect, depot residents waiting until
reconnect, and mature Ironmeadow field `no safe path`. The source-proven HOT
meal-route cache failed to replan from the waiting pocket to the service leg;
the source-proven field planner retained HOT `PATH_UNAVAILABLE` after scene
release and blocked COLD progress. Main alone committed narrow corrections
`718a1da4`: pocket arrival replans; after scene release COLD retries the next
known field step or retargets another reachable unprocessed cell, while a
still-impossible path remains held. Focused meal-navigation and harvest-process
regressions, style/size/debt checks, detached assemble and packaged-JAR verify
passed. An exploratory baseline geometry test was removed because it did not
model the live obstruction. The execution protocol now explicitly requires a
permanent test to exercise a reachable defect or contract boundary and change
a concrete decision; no broad reassurance campaign.

Exact JAR SHA-512 `dd765c5322a784dc74603d8ad61172b23ddeedbf2bb7398a309f18c67f19c5cc763dce00713a3951fcc4797db6ba862a946f23c7c87d944473910dee91beb47c`
was installed into preserved world `frontier-v3-meal-field-retest-20260930`;
preflight and post-start verify passed, service active at PID 3520616/port
25565. Post-start RCON summary at instant ~107,918 reported OK/green and zero
required conflicts. Ironmeadow is now GROWING after a terminal harvest job,
but that endpoint alone does not attribute recovery to the new JAR; the old
world advanced between prior observation and restart. Original HOT path-failure
cause, timely Clearwater projection, depot crowding and player-visible
Ironmeadow recovery remain unproven. Do not call F0.6R3 complete, push, or
reset the preserved world. Source checkout clean; outer pack retains only
pre-existing `.f0v-baseline/`; governance retains historical WIP.

Live meal/field incident (2026-09-30, main alone): player reported residents
stopped eating and farmer stopped after several cells on deployed `9d246294`
world `frontier-v3-queue-fresh-20260930`. Its 20:46:16 log and persisted WAL
showed a second HOT resident arrival rejected while the first held the shared
depot turn; that rejection globally quarantined the simulation. Source commit
`63948cb4` gates only the dynamic turn before HOT submission; focused meal,
harvest, bakery, style/debt and NeoForge compile checks passed. After exact
same-world deployment, status advanced again, but restart exposed a distinct
field conflict: a DRAINING farmer with bound wheat had a valid saved departure
and no loaded body; scene release required the loaded body anyway and recorded
`CARRIER_FENCE_UNRESOLVED`. Source commit `d5dbcecb` captures the exact offhand
at unload, compares it to the saved entity write, and permits release from that
saved witness. Focused departure/recovery tests and guardrails pass; no full
GameTest/client proof. The conflicted old world is preserved. Clean detached
`d5dbcecb` JAR SHA-512 `d90272b6acecf6a64055f0dcd8134e240e3d48437bf3004a9f8daa227d2035312b432fb124c263db250fb300f84e74afc85e441a40c1325d5103e463dab678f8`
was installed on separate fresh `frontier-v3-meal-field-retest-20260930`, PID
3355646, port 25565. Preflight and startup verification passed; first RCON
summary: status OK, diagnostic green, 0 required conflicts, 12 settlements,
366 residents. This is a diagnostic build, **not** proof of a full visible
feeding/harvest cycle or F0.6R3 completion. Next: one focused HOT player
visit plus COLD departure/restart to verify field and meal terminal results;
if it fails, use the exact trace. No push, subagents or deletion.

Live geometry/depot checkpoint (2026-09-30): main alone built clean detached
`a49ce4c0`, passed guardrails/assemble/package verification and deployed its exact
JAR to the preserved `frontier-v3-queue-fresh-20260930` world. The broader
`check` was intentionally stopped after the relevant modules had passed: its
unrelated hive-route fixture continued consuming CPU; do not report a full
suite pass. Live status stayed `ok`, but settlement 11 exposed a concrete
service deadlock: resident `11-12` held an uncompleted meal at the depot while
baker `11-3` held `DEPOT_SERVICE_WAIT` at the same boundary. The coordinator
rejected each because the other was present. Source commit `9d246294` gives
the already-present meal the turn consistently with the worker's existing
yield rule. A focused overlapping-occupant regression, resident meal,
bakery HOT/COLD, actor movement and common-route tests pass, as do style,
size, architecture-debt checks and NeoForge compilation. A clean detached
`9d246294` artifact (SHA-512
`d7100b234fc3066906f5124a4be9850d884a0c182c90e2abccc7d0fcd0381dd7f53691b7da50fd213acb1e37f0e820df68671121b066498d9acd3907c774e540`)
was installed after save/normal stop with no world reset; preflight and fresh
startup verification passed. At live instant ~249,574 status remained `ok`,
depot bread declined 34→32, baker moved off its prior boundary cell and
resident `11-12` had a new meal claim rather than its pre-fix claim. At
~252,387, active meals further fell 31→16, bread 32→16, `11-12` had finished
that meal and hunger deficit fell 4→3; status stayed `ok`/quarantine NORMAL.
The baker still reported `DEPOT_SERVICE_WAIT` while no longer at the depot,
so complete bakery liveness is not established. This is narrow queue-progress
evidence, not sustained throughput, complete depot/field success, HOT/player
acceptance or F0.6R3 closure. No push or subagents.

Unified pedestrian-geometry checkpoint (2026-09-30): user required reusable
geometry for future services. Main alone committed source `a49ce4c0` in
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`. One read-only
`KnownPedestrianRouteKnowledge` now supplies bounded COLD paths for meal,
post-meal exit, bakery and farmer. Callers declare exact canonical facility
and `EXTERIOR`/`PUBLIC_ACCESS` reach; the provider gets all four existing
facility kinds from the same typed port compiler as traversal topology. The
farmer adds its current canonical field-cycle overlay; no active task passes
raw obstacles to the low-level pathfinder. Static baseline and dynamic hive
organs, authored passages and changed-cell gaps are composed in one place.
Focused route/field/resident/bakery/movement and traversal-plan tests,
style/size/debt checks and NeoForge compilation pass; source working tree is
clean. This checkpoint is **not deployed**: the preserved live server still
runs `2b648ab4`. `PhysicalDelta` lacks final block state, so a changed cell is
treated as unavailable knowledge, not a confirmed solid wall. HOT/player,
restart and sustained TPS evidence for F0.6R3 remain open; do not claim
product completion, push or reset.

Field route-knowledge checkpoint (2026-09-30): main alone committed source
`a3e68e6e` after live candidate `2b648ab4`. The farmer's current work owner
still selects the typed CellId/depot goal, but no longer assembles blocked
cells or decides survey passability. `ResourceSiteHarvestKnownGeometry` now
owns the field/crop/soil overlay and authored depot passage, then invokes the
shared bounded pedestrian search; no movement or persistence semantics were
changed. Focused harvest and process-SDK tests, Java style/size/debt checks,
and NeoForge compilation pass. This is source-only and is **not** installed
on the live server, which still runs `2b648ab4`; do not claim the whole
navigation architecture is complete. The field overlay is still a separate
provider, and `PhysicalDelta` identifies changed cells without retaining
their final block state; conservative unknown-cell handling is not equivalent
to a confirmed solid obstacle. Read-only live diagnostic at instant ~164,932
still reported `status=ok`, runnable lag ~2 ticks and no scene leases, while
the server log had periodic 40–96 tick keep-up warnings. HOT/player and
sustained host-budget acceptance remain open. No push/reset/subagents.

Current movement/route checkpoint (2026-09-30): main alone committed source
`2b648ab4` in `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`.
Observed live settlement-11 service stall had a post-meal actor still inside
the depot boundary, no known exit route, and 31 waiting meals; prior route
callers classified every other COLD actor body as permanent terrain. The new
read-only `KnownSettlementPedestrianRoute` derives one settlement obstacle and
support view from plans/physical deltas and typed authored passages; meal,
post-meal exit and bakery routes use it, and short depot entry validates its
physical service cell. Crowd/changed-cell/process regressions plus focused
resident/bakery tests, Java style/debt and NeoForge compilation pass. Clean
detached diagnostic artifact in `/home/rd/proj/pm-f06r3-diagnostic.LgmwSV`
has SHA-512 `6d328e4d134de2c1dc5b43061aa4f4dac3dda5f80852601c6a9570706855babb9c94c898c6a4bf9c5d279538df569eb2a516d3fc3cea6cc002a2bc012518e44b`.
The preserved `frontier-v3-queue-fresh-20260930` world was saved and normally
restarted with that exact artifact; preflight and post-start verification
passed, service PID 3032122/port 25565. Settlement-11 bread stock dropped
59→54 with active meals 31→27, status `ok` and current runnable lag ~8 ticks,
showing the former blockade released. This is diagnostic evidence only: no
release-duration TPS, HOT/client, restart meal-life or complete F0.6R3 claim.
Later same-world checks reached instant ~150,638, remained `ok`, runnable
lag ~2 ticks, and the 194-second observed interval advanced ~19.8 canonical
ticks/second; queue depth includes future actions and is not by itself runnable
debt. Depot stock emptied and subsequently refilled to 61, and distinct
residents `11-1`, `11-5`, `11-20`, `11-32` held new meal phases; `11-32`
improved hunger deficit 4→1 and completed an intervening movement order.
An isolated native pilot was attempted: first launch rejected a collision
with live RCON port 25575 before Minecraft; corrected port 25591 started its
disposable server but rejected `DISPLAY=:95` because the runner requires the
physical `:0`, which the user disabled. Its server exited cleanly, pilot ports
25591/25592 and task-owned Xvfb :95 are stopped. This is method-unavailable,
not a HOT product failure; no client/HOT acceptance was obtained. User was
asked for one manual depot observation when convenient. Remaining field-specific
geometry and process-owned meal travel are not yet fully shared movement
architecture. No push/subagents/reset.

Current F0.6R3 resident-throughput checkpoint (2026-09-30): main alone
committed source `cd8be538` after movement split `064a2ad3` and addressed-wait commits `3979c2df` and
`4e62ed91` in the ledger-named checkout. The retained meal now ends on
confirmed bread consumption; a separate typed actor-movement owner holds the
post-service goal, timed COLD route, HOT navigation/observations and first
physical exit checkpoint. Schema 215 rejects old disposable-world snapshots.
Focused actor-movement/meal/depot/bakery/catalog tests, NeoForge compile,
large-file and Java-style checks pass. The new checkpoint excludes active
movement from work, scene, medical/migration and meal admission; a bakery
vertical proves the depot turn ends before movement arrival while work stays
blocked. The physical-delta engine regression
proves same-transaction route interruption; modeled HOT release and snapshot
tests prove narrower recovery paths. No native player-visible or TPS claim,
no server update, no push. Aggregate guardrails now pass after simplifying
the runtime composition overloads without raising the architecture ceiling.
Open: host-budget/queue/TPS calibration, clean fresh-world
HOT/COLD/restart/player evidence. Active order:
`docs/work-orders/PM-F06R3-RESIDENT-QUEUE-THROUGHPUT-01.md`.

Active sparse-travel increment (2026-09-30): user accepted one canonical
movement order with timed COLD route/as-of position, causal event boundaries
and HOT Minecraft navigation; service activities issue goals rather than own
locomotion. Normative clarification is in `docs/frontier-v3-execution-semantics.md`
and the active resident throughput order. Main alone checkpoint-committed source
`27cf781f` (not a release candidate)
in `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`: a bounded
`TimedKnownRoute` is persisted in schema 214 for active resident meals; their
COLD walk now writes a route-start and segment/arrival transition rather than
one WAL transaction per support. As-of demand and ambient lease preparation
checkpoint the same body; release requeues the interrupted meal from the
witnessed body. Distant residents travel to separate side pockets; a committed
short entrance holds the single-file service turn, and return travel checkpoints
at the first physical exit. Resident/meal/navigation/bakery and focused ambient
runtime/store tests pass; NeoForge compiles, `git diff --check` is clean. A
physical block observation intersecting the retained route now checkpoints
the as-of body and wakes that exact resident in one accepted engine transaction;
the engine test exposed and closed the missing process-emission declaration.
A two-resident test also proved that retiring the meal immediately on exit
without a successor COLD movement owner strands the first resident at the
depot and blocks the next visitor. That attempted partial change was reverted;
retain the working return movement until a shared successor order can take it
atomically. Side-pocket waiters now retain their due actions without recurring
no-op WAL retries, and unavailable activity/meal reviews use a one-tick due
fence instead of delaying 200 ticks after the source becomes available. The
predicate is still scanned, so this is not the final owner-addressed index.
This
is **not** a completed order, deployment or HOT visual proof. Remaining:
owner-addressed wakeups without due-queue scanning; complete known-route
invalidation on world edits and competing events; separate meal completion
from subsequent movement/service clearance; calibrated budget/queue/TPS;
fresh-world HOT/COLD/restart/player acceptance. Old schema worlds are not
silently migrated. No push, reset or subagents.

Active throughput correction (2026-09-30): follow
`docs/work-orders/PM-F06R3-RESIDENT-QUEUE-THROUGHPUT-01.md` in source checkout
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, main agent alone.
Read-only live diagnostics found 366 residents, roughly 1,055 queued actions,
oldest runnable debt roughly 62,556 ticks, and five Clearwater meals retained
in MOVE without COLD position progress over 2,000+ ticks; runtime was active.
First source increment is checkpoint-committed/undeployed: hold unavailable activity
and physically owned meal actions instead of writing routine retry WAL; use
current canonical instant on late wake; release depot access based on actual
body boundary rather than mere meal/work travel intent; test four independent
COLD meals under an ordinary four-action budget. Focused resident and bakery
tests (27) plus NeoForge compile pass, `git diff --check` clean. The raised
budget is provisional and has no live TPS evidence. Owner-addressed wakeup,
sparse COLD route progression, calibrated server budget/queue metrics and
fresh-world visual/restart acceptance remain open. Do not call the feature
complete or deploy this as accepted throughput. No push or subagents.

Current correction (2026-09-30): the player's depot crowd stopped because the
live `frontier-v3-parallel-meals-20260930` runtime quarantined at 09:06:58,
revision 58946. The adapter witnessed a resident leaving the depot access
boundary, but `ResidentMealProcess.hotMeal` still required the canonical RETURN
body to be at the service station or already outside. A read-only recovery of
the exact persisted pre-failure state proved resident `7-10` was correctly at
its HOT handoff `(134,65,14)`, one COLD return step beyond service
`(135,65,14)` yet still inside the boundary; the old reducer rejected the
witnessed exit. Main alone fixed only RETURN validation to accept that exact
retained HOT handoff, while TAKE/CONSUME remain service-station strict. A
permanent COLD→HOT mid-return/restart-codec regression passes, and the exact
persisted state accepts the formerly rejected transition under the new code.
Source checkpoint `fbd33624900a04ed28c63b8ed4c1a61b855fbe6d` is clean;
focused meal/navigation tests, guardrails, NeoForge compile/JAR verification
passed. No complete Java/GameTest or graphical claim. Clean detached release
tree `/home/rd/proj/pm-meal-return-release.OuH8eT/pale-mirror` built verified
JAR SHA-512 `3d9bb5d5b5fefe825ce2ce87c9b69655b81b992767f2f449e6a46df84cf84a7b4a079547a221d145851bf965e9df8cc031dd61b6a4627a92a4034d80497e5205`.
The same world was saved, stopped normally, preserved and restarted with that
JAR; preflight and post-start deploy verification passed (service PID 1407601,
port 25565). Runtime advanced revision 58946→59205, instant 77850→78086,
status OK/green, zero required/inventory conflicts. Some exact resident HOT
leases remain UNKNOWN_AFTER_RESTART without a naturally loaded actor chunk;
this is not evidence of successful HOT motion or feeding after restart. A
vanilla Mineflayer diagnostic client was rejected by NeoForge before spawn,
so it provides no player evidence. Next: one ordinary modded-client visit to
the depot, observe return/access release and multiple residents completing
meals; inspect new quarantine/trace if it fails. No push/subagents/world reset.

Active diagnostic deployment (2026-09-30): user reported serialized resident
travel to depot and whole-world failure after a medical worker's meal. Static
review confirmed the shared service turn was acquired at meal-trip start,
thereby serializing the entire approach. Main alone separated travel admission
from physical service occupancy, added deterministic waiting pockets and
HOT/COLD turn-change rerouting, and covered overlapping meal claims. The
actual preserved-world quarantine at 07:33:28 was a distinct resource-site
harvest successor-worker mismatch: planning reused the lineage worker while
validation selected the highest-ranked available worker. Both now use the
same successor choice, with a focused regression. Source commit
`bbd008afaf4285742a594df980e91a14cdd876ab` is clean; focused tests,
NeoForge compilation and packaged-JAR verification passed, not the full
Java/GameTest gate. Clean detached release tree is
`/home/rd/proj/pm-service-travel-release.W3F8hC/pale-mirror`, JAR SHA-512
`977a013cd8109a2ae4987121c7106b5b612018d4d702c6318f9a7bb63bf3082835f348e62cc6a58c98ae0bb96d4327d09fddcb7e753e3162f3b1b8a721ab0b5c`.
Same-world restart on `frontier-v3-service-access-fix-20260929` advanced
revision 76838→77062, instant 95717→95941, status OK/green and zero new
conflicts, proving recovery past that global crash. Its medical resident
`7-5` still has an old pending first-body creation/meal claim; the world was
preserved, not silently repaired. For human feeding observation a distinct
fresh world `frontier-v3-parallel-meals-20260930` was installed with the same
seed, preflight and post-start deployment verification passed; live service
PID 1264149, port 25565, initial summary status OK/green, 12 settlements,
366 residents, zero required/inventory conflicts. This is diagnostic
deployment, not confirmed multi-resident HOT visual acceptance. No push or
subagents. Next: one bounded player/client observation of simultaneous
approaches and service boundary release; preserve old world pending a
separate stopped-world absence-proof recovery decision.

Live quarantine correction (2026-09-30): on the preserved
`frontier-v3-service-access-fix-20260929` world, log at 23:36:51 showed
`bakery resource has no sole phase-correct account and claim` during a resident
meal action. Main alone proved the static cause with a failing local vertical:
after a confirmed 64-bread delivery, bakery phase `DELIVERED` retained the job
while its baker returned, but validation still demanded all 64 units at the
depot. The next resident's lawful one-bread take quarantined the whole runtime.
Source commit `d94eda26` releases job-exclusive output custody at delivery
for fungible and exact branches, retains input-claim/stranded-cargo checks,
and tests consumption before baker finalization plus snapshot roundtrip.
Focused bakery/resource tests 31/31, guardrails, NeoForge compile/JAR verify,
and clean detached artifact verify passed. An unrelated full Java `check`
was stopped after two minutes in heavy route-baseline compilation; it is not
green evidence. Clean detached release tree:
`/home/rd/proj/pm-bakery-delivery-release.gfwjYR`; JAR SHA-512
`e2166c78482f3bf81b8f1422ce6b848c4f6dcfcbcc6305828281f6669e603593eb269d510dca0b682aaa4030a9ace03e28997f24f67291326afb7bacd6d7eeb7`.
Same world was preserved and the test server restarted; deploy verification
passed with service PID 1167239, port 25565, no new quarantine, and read-only
summary `status=ok`, revision 51894→52256, instant 70917→71279, zero
required/inventory conflicts. This is a diagnostic deployment, not full
release or human acceptance. No push. Next: player-visible repeated meals and
bakery/field observation; avoid a redundant broad proof campaign.

Active correction (2026-09-29): user observed feeding in Clearwater, but
visitors apparently waited for the prior resident's whole return route and
the world stopped after about six meals. Live server log at 21:29:28 proves
whole-runtime quarantine on `schedule:resident-activity-7-3` with `invalid
hunger evaluation tick or metabolism`; the HOT consumption had advanced that
resident's need clock beyond a still-retained activity-review due tick. Main
alone has WIP in `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`:
activity review and the shared arbiter now evaluate no earlier than the last
confirmed need tick; the shared service boundary separates access release from
task completion. Resident meals, bakery delivery and outbound harvest work
release their depot turns on the first witnessed HOT/COLD physical boundary
exit, without requiring a precomputed route cell. Their independent journeys
continue after release; no second
queue or resource owner was added. The governance resident-life contract and
architecture map record the class-wide rule for later shared service points.
Focused activity/meal/bakery/harvest/catalog regressions pass, including overlapping
resident meals across snapshot recovery. Guardrails, NeoForge compile and
packaged-JAR verification pass. A subsequent NeoForge `build` invoked an
unrelated broad route-scene-return JUnit fixture and was stopped after about
two minutes; the final-source build aggregate is not green. One earlier full
429-GameTest pass had 428 green
and one `continuouspatroladvancesoneveryloadedservertick` failure on an
unrelated zero-delta second sample; a later scene slice passed all 153,
including that patrol. The concurrent full Java suite was stopped after its
GameTest failure and a >10-minute unrelated route-scene fixture; do not label
the complete release gate green. Source checkpoint `da3ab87d` is clean; no
push. A diagnostic JAR was built/verified in clean detached
`/home/rd/proj/pm-service-access-release.cEKJMa` and installed with SHA-512
`5f9e18342553c7ce323203bb159aaea62c7787447985375cc62b1399f40c90fbaa8ac871a65ae828ae4d8a7b2e49f4c87c008c7b67f7d054d273c7d9eb475728`.
Restart on the prior world correctly quarantined on incompatible process/scene
descriptor inventory, not on hunger; that world was preserved. Fresh disposable
world `frontier-v3-service-access-fix-20260929` was installed without deletion.
`frontier-v3-deploy-verify.sh` passed on live service PID 291361/port 25565,
with fresh runtime start at 22:38:09 and no new quarantine at verification.
This is diagnostic access, not RELEASE_CANDIDATE/HUMAN_CANDIDATE or proof of
six successive HOT meals. Next: player/client observation of shared-point
release and repeated meals, followed by proportionate formal acceptance.
No subagents, no push.

Current live correction (2026-09-29): main alone traced the Redwillow
`HARVESTING`/cleared-field and idle baker observations on
`frontier-v3-meal-opportunity-20260929` to two concrete failures. At first HOT
visibility the COLD-accounted farmer hand was incorrectly gated by the
field's separate, not-yet-ACTIVE block-projection claim; the worker scene
entered CONFLICT. Later a baker's physical bread delivery was rejected because
the depot receipt contained the pre-existing 59-bread stack as well as its
prepared 64-bread slot; the server quarantined. Source commit `0fe730d4`
separates cargo authority from the field writer, verifies the prepared bakery
slot within the complete depot layout, and keeps a rejected physical receipt
as a local ambiguous effect rather than quarantining unrelated work. Focused
regressions passed; source full gate passed 429/429 GameTests and Java checks;
final source and clean detached release NeoForge builds/tests/JAR verification
passed. Release checkout: `/home/rd/proj/pm-release-field-cargo-20260929`.
Pale Mirror JAR SHA-512:
`f43182f8fc5f9024e4b23aa841a391016595c7fb74b28ac14ad65a143e32fd5c6c0c02f9abeab9e831eee82f86ea6bc54f87da54a2b81db565b2aeba91dc910f`.

The first restart exposed a stale Visuals JAR from the root checkout still
requiring retired `villageroverhaul`; no new world was created by that failed
start. Visuals was rebuilt and verified from the same clean release commit,
SHA-512 `2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
Root pack commit `d0fca59` makes publication reject a Visuals JAR requiring
any retired mod before replacing hosted artifacts; stale rejection and good
publication were exercised, and `scripts/validate.sh` passed. Both repositories
are clean except the root's pre-existing `.f0v-baseline/`; governance retains
unrelated WIP. No push. The corrected pair is installed on the fresh test world
`frontier-v3-redwillow-claim-receipt-20260929` with original seed
`20260918065`; `far-frontier-v3-live.service` PID 4035591 passed preflight,
post-start artifact/port/log verification, and read-only summary advanced
revision 378→397, tick 740→1341 with `status=ok`, no required or inventory
conflicts. The quarantined old world is preserved. This is a healthy server
deployment, not completed HOT farmer/baker human acceptance; next inspect
Redwillow field-to-depot and bakery delivery in the actual client.

Latest shared-depot correction (2026-09-29): main alone traced the preserved
`frontier-v3-meal-opportunity-20260929` world quarantine to a canonical
reservation/projection mismatch. An active harvest reserved depot slot 1, while
COLD bakery bread output packed a second fungible stack into that same slot;
the resulting `active harvest output slot lacks exclusive container capacity`
invariant quarantined the runtime. Source commit `3dd834685967b302254f0cdb6e5a7bc81c988a89`
adds one reservation-aware inventory capacity/projection view shared by harvest,
production and supply, with no second persisted ledger. Focused tests and the
full critical gate passed, including 429/429 native GameTests; the clean
detached release build and packaged-JAR verification also passed. Release JAR
SHA-512: `e6cf31f06eb7e622617b74ac3e55cd4b5cf9a47a03da0e775858b263af99b702a18257205c86b91be95487ff5a8c2605b8119af8563fadd5e70569522108af4d`.
The user left the live server for restart. The artifact was published and
installed on the **same preserved world**; `far-frontier-v3-live.service`
restarted, fresh deploy verification passed at 18:23 local with matching JAR,
port 25565 and no new quarantine. Read-only RCON status advanced from the
pre-fix snapshot revision 105472/tick 123972 to revision 107967/tick 126466,
`status=ok`; summary was green with zero required/inventory conflicts. This
proves recovery and progress past the formerly failing boundary, not complete
manual farmer/bakery product acceptance. Source checkout is clean; no push or
world reset. Root pack retains its pre-existing untracked `.f0v-baseline/`;
governance repository retains unrelated pre-existing WIP. Next: user may
re-enter for manual observation of farmer/depot/baker behavior on this build.

Latest live correction (2026-09-29): main alone diagnosed the user's Ironmeadow
empty-field/`HARVESTING` observation and Clearwater farmer/depot stall on the
preserved `frontier-v3-meal-opportunity-20260929` world. The live runtime had
quarantined at canonical tick ~43000 because market clearing treated a baker's
valid FREE-window production-start reschedule as a missing terminal decision.
Commit `4f218e3c` translates that exact deferral into a market retry; a full
gate passed 429/429 GameTests and Java/build/package checks. The same-world
restart recovered Ironmeadow to GROWING epoch 2 with no active harvest.

Clearwater had completed 64/64 crop cells but retained 64 wheat with its
farmer because resident `7-21` held the shared depot turn while stuck in meal
MOVE. On an actual world snapshot, old `ResidentMealKnownNavigation.path` threw
`known pedestrian start has no clear support/body column` at the depot's
declared walkable side station `(134,64,13)`; resident `12-13` had the same
defect. Commit `6152f133` clears only declared depot access stations from known
structure occupancy. A focused regression and both snapshot routes passed;
the complete gate again passed 429/429 GameTests plus Java/build/package.
Clean detached artifact `/home/rd/proj/pm-release-depot-side-20260929` has
SHA-512 `62b88ac3690afce1a6358c399d76677088272dce3fa6781cc2309fe73392a8310a864bb8413258c9858c5b0c64bcb21c535662d6f89ca21233f1d9b1b8f3aac7`.
It is installed on the same world via `far-frontier-v3-live.service`, PID
3450228; preflight/post-start verify passed, no new quarantine. Live read-only
diagnostics then showed `7-21` reach/eat/depart the depot (bread 63→62,
`mealPhase=NONE`), the farmer walk toward it, and Clearwater transition to
GROWING with no active job. Its prior HOT-captured physical intent still has a
pending exact receipt, so depot wheat/physical stock is not yet confirmed.
The earlier player-visible HOT→COLD→HOT disappearance remains unproven fixed;
manual/normal-client visual retest is needed. No push, world reset or subagent.

Current correction (2026-09-29): the user reported that hunger with no food
prevented farming, only one resident ate, and other residents stopped visiting
the depot. The live `frontier-v3-service-access-20260929` world showed one
confirmed HOT bread consumption at 14:06:27, then quarantine at 14:06:47:
`ambient-meal-cleared` incorrectly emitted head-only `ScheduleEffect.Consumed`
for its own non-head meal continuation. Static review also found two independent
starvation gates on work admission. Main agent alone corrected both paths:
an executable meal now requires an available exact depot source/access;
otherwise a living hungry resident may continue or start ordinary work and
rechecks food on a bounded 200-tick wake. An asynchronous HOT meal return now
uses exact-ID `Cancelled`; confirmed physical consumption remains the eating
fact. Local source commit `b7cada6e063c47e99c4e70cc11687f3ed2ed22cf`.

Verification: focused regressions passed; the full source pass had 1005/1005
frontier tests and 428/428 GameTests. Its sole NeoForge test failure was an
obsolete no-food diagnostic expectation (`EAT` versus feasible `IDLE`),
corrected locally; all 37 tests in that diagnostic class plus build and JAR
verification then passed. Clean detached release worktree
`/home/rd/proj/pm-release-meal-opportunity-20260929/pale-mirror` passed
NeoForge build and packaged-JAR verification. JAR SHA-512:
`d6d57765ce296377b16f2858d0c1dfac7795aae101d550b8e54011c8eaeb140fc7cb779c1a6c4583c348c3141cc4418f5ab0be56adc643faa90213d58d8baa8e`.
It was published/installed on `far-frontier-v3-live.service` with fresh world
`frontier-v3-meal-opportunity-20260929`; preflight and post-start verification
passed, service PID 3275010, port 25565, no new quarantine. Old worlds remain.
Read-only RCON status advanced from revision 487/tick 2535 to revision
2430/tick 16773 with `status=ok`, 366 residents and 12 sites.
The isolated ordinary-client `disposable-resident-hot-meal` scenario passed:
one physical bread consumed, depot 64→63, meal claim cleared, resident
nourished, no quarantine. The older worker-meal scenario stopped at an obsolete
fixture precondition before its actions (`NOURISHED` expected while the worker
had already become hungry); it is not product evidence. The successful client
frame was visually inspected but occluded by a wall, so no graphical/full-pack
or human acceptance claim is made. No push; no subagents.

Current assignment (2026-09-29): main agent alone implemented the accepted
resident-metabolism variation and shared depot service-access mechanism,
without subagents. Per-resident baseline rates are deterministic and
ruleset-hashed (production r6/schema 8). Meal admission is serialized across
residents and depot work; an exact meal retains its turn until its body clears
the depot in HOT or COLD. Bakery delivery retains its job through physical
departure, releasing access on confirmed clearance. Field and bakery work
consult the same read-only coordinator. State schema 213 requires a fresh
disposable world. The contract and architecture map contain the policy.

The first full Java gate completed 1002 tests with six stale route-fixture ID
assertions; all six and added HOT depot-departure and varied-rate tests passed
after updating fixture identities. The single failing core GameTest was
`ownedfieldwritesallslotsandrecoversitspendingprovenance`: fixed its setup to
wait, with a finite 100-tick cap, for actual crop-survival light before
placing all field cells. The final full core gate passed 428/428 required
GameTests and packaged-JAR verification; a focused rerun after the last
test-only diagnostic edit passed. Clean detached release worktree
`/home/rd/proj/pm-release-service-access-20260929/pale-mirror` at local source
commit `7d735c3f3bfe78d6b4e2fab3447a022d3bea6429` passed NeoForge
build/check and packaged-JAR verification. JAR SHA-512:
`10f15a736d6637791043f80174fe2bbc537b6158ef4c3e9a7678e8a85310f4a8ee97ffafa82139ce4ba2b5e4c2ba9bc6104d0bd7212726a67d8448d855573bbd`.
No push.

At user request, the JAR was published through the root pack host and
installed on `far-frontier-v3-live.service` with Java 22 and fresh world
`frontier-v3-service-access-20260929`; the previous world was preserved.
Preflight and post-start verification passed: service active, port 25565,
new-world runtime start, installed JAR hash matching the detached artifact,
no new quarantine at verification. This is a healthy test-server deployment,
not graphical/player acceptance. Next: user or normal-client pilot should
inspect farmer field progression, staggered depot visits, physical bakery
delivery/departure, and whether residents resume work after eating; diagnose
actual reported symptoms before changing code.

Previous assignment (2026-09-29): main agent alone implemented, verified and
deployed the accepted resident-life and resource-accounting vertical in
`docs/frontier-v3-resident-life-resource-contract.md`, without Terra or other
subagents. Implementation checkout:
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`. The user cleared
the old system goal and explicitly requested deployment. Engineering acceptance
is recorded below, distinct from future human product acceptance.
Preserve all WIP/history. No push, deletion of old worlds or unrelated
publication was requested. The nested source
repository and root pack repository are separate. Historical chronology and
evidence are archived at
`docs/archive/continuity-2026-09-28-resident-life-wip.md`; consult only when a
specific old fact is needed.

Implemented in the source checkout: typed/versioned resident metabolism
and fractional per-person hunger; exact need/activity timers and settlement
WORK/FREE policy; retained worker assignment with safe yield; common navigation
for a resident meal; COLD and physical HOT MOVE/TAKE/CONSUME/RETURN with exact
ledger claim, actor hand, recovery and immediate activity hand-off; player
depot exit/gift witness with explicit affected-meal claim retirement; current
stock/reserve/birth/production policy and resident-life diagnostics. The old
settlement provision scheduler and reducers are unregistered/fail closed;
fresh worlds seed no provision counters. The new schema is 212; old disposable
worlds are intentionally incompatible. Unadapted work families retain an
explicit `OWNER_SAFETY_HOLD`, not silent progress or false bakery ownership.
Do not claim the old static provision source/model/codec are physically removed.

The frozen dirty-source candidate has source-content SHA-256
`b99df413d0f3f3f563b23d68b6c3391d013e5bcd0d332200fabb84488c649aad`
and packaged JAR SHA-256
`6942b6f1d25bab55a8a8bd3bce00c0d289be88182b6863e562df1a2458517a3c`.
Fresh normal-client disposable scenarios all passed on that exact identity:
worker hunger→physical meal→same farmer job→terminal field at
`build/frontier-v3-scenarios/disposable-resident-worker-meal-1790623832691.json`;
two-stack ordinary-player depot gift/withdrawal at
`build/frontier-v3-scenarios/disposable-depot-player-multistack-exit-1790623982929.json`;
claimed bread withdrawal/no phantom meal at
`build/frontier-v3-scenarios/disposable-resident-claimed-bread-exit-1790624056909.json`;
and post-TAKE COLD hand→graceful restart→single HOT consumption at
`build/frontier-v3-scenarios/disposable-resident-after-cold-take-restart-1790624128360.json`.
Product frames of farmer/field and depot chest were personally inspected.
All task-owned server/client processes and task-owned Xvfb :95 stopped;
pilot ports 25591/25592 are free. Live service and world were not modified.

Latest source corrections: starter 64-bread ledger lot in settlements 2–12
prevents a circular first-harvest hunger deadlock; settlement 1 retains wheat
for the bakery vertical. Scout first-patrol due time no longer depends on the
number of resident schedules. The physical-intent validator now recognizes
the settlement itself as a canonical cause/role subject. Obsolete provision
tests were replaced with a fresh-world retirement check, affected field/SDK
tests use the new WORK/FREE semantics, and schema tests use current versions.
Focused resident, player-stock, field, production, scout, engineering,
tactical, scene-SDK, migration and recovery checks pass. The final frontier
suite passed 999/999; full NeoForge `build`, packaged-JAR verification and
guardrails passed; 427/427 required physical GameTests passed. The earlier
40 frontier failures were obsolete timing/provision expectations or concrete
bugs now corrected. NeoForge birth fixtures needed current reserve stock,
and three old physical tests for the retired provision/manual-depot-mutation
path were removed; current ordinary-click and meal paths were checked natively.
`git diff --check` passed before this governance update.

Engineering implementation and disposable-client acceptance are complete.
Source commit `b664c3e157158349979ef1faa477513bf65748b8` contains the 125
task files; no push. A clean detached worktree at
`/home/rd/proj/pm-release-resident-life-b664c3e1` passed NeoForge build/check
and packaged-JAR verification. Its JAR SHA-512 is
`10d2d14e70edfb3214634708d9d41ccad966ef5565c2a6931cd5330470af70fa42610fd32d3c794f0c3bc8dcb61f06d2a981fef7657ee0ff7ee516394ee5bc58`.
The detached JAR differs from the earlier disposable-client JAR only in
`META-INF/pale-mirror-runtime-identity.properties` (`sourceTree` provenance);
all 3600 other archive entries have identical CRCs. The source-content
fingerprint changed on commit solely because the removed obsolete
`SettlementProvisionProcessTest.java` ceased being represented as a missing
tracked file. No compiled/runtime payload changed.

The first user-requested test-server deployment published this JAR through the
root pack host and installed it on `far-frontier-v3-live.service` (Java 22) in
world `frontier-v3-resident-life-20260929`, configured seed `20260918065`.
Previous world `frontier-v3-market-identity-retest-20260927` remains intact.
`frontier-v3-deploy-preflight.sh --require-world-absent` passed before start;
`frontier-v3-deploy-verify.sh` passed against process PID 2435942, port 25565,
fresh `Done` and Frontier v3 runtime start, and no new quarantine. Installed
server and hosted client JAR SHA-512 match the detached build. This is a
healthy deployment at that time, **not** HUMAN_CANDIDATE or manual player
acceptance. The subsequent live incident and replacement below supersede its
claim to be the current healthy world.

Live Clearwater incident, 2026-09-29: the user saw `HARVEST START PENDING`
with only one 64-bread stack in the depot. Read-only RCON inspection found the
field READY and due at canonical tick 24000, four living farmers, free depot
slots and no quarantine before that boundary. At tick 24000 all 21 local
residents entered their first hunger/meal interval, temporarily removing
farmers from new-work admission. An exact HOT meal consumption subsequently
advanced one resident's nutrition clock while its old scheduled need review
remained queued; the stale review threw `need review is not this resident's
exact threshold` and quarantined the runtime. Static review also found that
the field-start planner treated all temporarily eating farmers as a terminal
missing-worker condition; this second failure mode was not separately proven
in the quarantined world. One bread stack was not the blocker.

Correction commit `67541b6d04698412313e1d7783c097f388fc992a` atomically
reschedules the exact need review with both HOT and COLD confirmed bread
consumption, and keeps the same pending harvest task/start identity while a
living farmer is temporarily unavailable. A genuinely missing/dead farmer
still blocks. Focused need/meal/harvest regressions pass; `guardrails check`,
NeoForge build and packaged-JAR verification pass. The first post-correction
native pass failed one unrelated asynchronous cargo-cleanup GameTest after it
had passed in the earlier run; one evidence-driven retry passed all 427/427
required GameTests. No additional matrix reruns were made. This is engineering
evidence, not proof of the full manual farming loop.

A clean detached release worktree at
`/home/rd/proj/pm-release-harvest-meal-67541b6d` built and verified the JAR
with SHA-512
`acd6ad3cc5e28f76503a341bf8e333e18db379af22d5a259eab9be8bcc93e1bdc54f7b4e61b46bf0824d658855a8f1c268c443bfd2d4c9f6c0f16cc9e543196d`.
That build ran in `frontier-v3-resident-meal-clock-20260929` but was superseded
after the user's next observation: exactly three HOT bread consumptions preceded
runtime quarantine. The precise thrown action was the retained meal-progress
schedule at its old due tick; it evaluated nutrition backwards after a HOT
consumption had advanced the clock. The same world's field ledger retained
`site:7-wheat-field` initialization at 18/132 writes, explaining the visible
single row; the canonical READY board had been displayed before physical
completion. This world is preserved, not reused as a healthy release.

Correction commit `bae82133370e67d406d4caebb60b551fca6d37c6` atomically
reschedules meal progress after witnessed HOT consumption and displays FIELD
MATERIALIZING/CROPS UPDATING instead of READY while the physical field is not
current. Full `guardrails check`, 427 GameTests, NeoForge build and packaged-JAR
verification passed. A normal-client disposable scenario passed field ownership,
physical wheat at the last cell, one exact HOT meal and no quarantine at
`build/frontier-v3-scenarios/meal-field-first-visit-fix-visual-r2-result.json`;
its inspected player frame shows all field rows and GROWING 0/7 with young
crops. This does not yet prove multiple future live harvest/meal cycles.

Clean detached release worktree `/home/rd/proj/pm-release-meal-field-bae82133`
passed build/packaged-JAR verification. JAR SHA-512 is
`fa764c3b92c4cc702cd2eb9f946368f42b3464135adf541b7b36b12061a7cc97f57e243448415928ad1277748392ca6aea0f30c14993b187441b912ef0c3c00d`.
Root preflight with `--require-world-absent` and post-start deployment verify
passed. The live service now runs fresh world
`frontier-v3-meal-field-fix-20260929`, seed `20260918065`, port 25565,
MainPID 2685207. Fresh RCON status was `ok`, 366 residents, 12 sites, tick
445; no new quarantine. All old test worlds remain intact. User must refresh
the client pack and manually accept Clearwater; no push. Source/release
worktrees are clean. Governance retains pre-existing dirty WIP and this ledger
change; outer pack retains only `.f0v-baseline/` untracked.
