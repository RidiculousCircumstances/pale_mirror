# Active continuity — 2026-10-25, R266 startup cleanup and crash repair delivered

## Assignment and authority

Forestry goal is COMPLETE. Player map delivered. Current user requested removal
of obsolete starter kits and reported a live server crash. Both scoped fixes
are committed/deployed and verified; no new system goal was requested.
Main alone; no Terra/subagents/push. Ordinary commit/deploy and disposable-world
reset authorized. Preserve all unrelated histories/WIP.

Canonical references: AGENTS.md, architecture.yml, engineering-agent-protocol,
docs/work-orders/PM-FORESTRY-20261025.md, frontier-v3-forestry-design.md and
source docs/forestry-r264.md. Previously read relevant repository-context,
engine-source, worldgen-performance, client-investigation, release-verification
and server-ops references. Do not repeat unchanged reading/checks by ritual.

## Exact repositories and delivery

Implementation /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror
(Git root parent), branch feat/baker-carry-orders-20260926, CLEAN.
Feature70b670821ec3b2b5d4abfe304bcbb11d6a02cbc0, preflight8631f679f;
Forestry HEAD248be0f83 includes shutdown/reach closure; player-map HEADa9d5b36ef; current HEAD76c64ab13
removes scenario grants and fixes service access/quarantined shutdown. Source clean.
Release /home/rd/proj/pm-r242-economic-release/pale-mirror, CLEAN detached76c64ab13.
Pack /home/rd/proj/minecraft, HEAD33fa84b, tracked CLEAN; retain unrelated
untracked .f0v-baseline/. No push. Governance retains extensive unrelated WIP;
only task-owned ledger/archive/order/design and forestry-only architecture/
materialization sections are staged. Never stage-all governance.

Runtime /home/rd/far-frontier-server, far-frontier-v3-live.service ACTIVE.
Fresh world frontier-v3-r266-starter-cleanup-20261025,
UUID30f840b8-2963-4ffb-8552-c6203bce854b, seed126/r28/schema306.
PID595324 at verification, startup epoch1792953177. Artifact SHA512:
bdf54d975a1c1721aa996900fc77bfaecd1cf937e3a6f70762a19060914867b0271fe50aec560c95193ab17cd4a80f48ac9dcfce657241c263c5c1ca727005e9.
Exact root publish/install/preflight/start/verify succeeded; source build
legacy-startup-audit/release-build.log PASS7sec; deployment build/r266-startup-*.log in IMPL.
R264 retained as normal production evidence, not migrated: old jobs can contain
unsafe former workstations. Production world grants no initial axes or logs.
Client updater required from pre-forestry builds; current pack selects r28.

## Implemented boundaries

Compact UUID/generation trees, packed log offsets/palette, sparse source losses,
lazy immutable authored regional geometry and bounded chunk growth provenance.
Forests near7Clearwater,3Dawnbridge,6Hearthvale: two agrarian and one mining.
Reserve256/resume<192, two workers,64cargo. Oak only; no wood consumption/trade
or planting AI. No per-log entities, TreeChop, runtime planting or force-loading.
Successful TreeFeature capture filters actual LOGS; its set also contains dirt.
Vanilla owns leaf decay; neighbours may sustain leaves after a trunk is cut.

Ironaxe uses ordinary forge manufacture3ingots/heat200, repair1ingot/heat100,
250wear. Forestry retains exact worker/tool/tree/labour/effect prefix/cargo;
UAE, navigation, staffing/needs, tool definitions, fungible custody, dynamic
warehouses, journals and physical confirmations remain shared authorities.
Whole-tree native children run top down, with native loot and once-only wear/
output. External removal withdraws entitlement without output or old-block
restoration. New generations cannot inherit unsettled old entitlement.
Completed work is removed; sparse depletion is required durable source state.
Limits remain explicit:4096descriptor logs,64item felling envelope,256owned
chunk records/links. Unsupported contexts are reported, never guessed.

Connected fixes: explicit foreign worksite dispatch BEFORE own geometry lookup;
shared source witnesses retained before native confirmation so ordinary demand
loss can release after unload; equipment destinations validated BEFORE physical
preparation (actual old extraction79 tried returning a pick to ore-only storage).
Normal forestry shutdown now withdraws exact unused native intent via common
PreparedOperationWithdrawal BEFORE its live permission disappears; attempted/
ambiguous effects never gain replay permission from matching preimages.
Shared BlockInteractionReach covers lateral support extent plus half declared
body width, with unchanged4500mm hit radius. Actual old support388/63/-32
admitted an eye4.504blocks from target385/64/-35. No tree-only alternative list,
teleport, wider hit radius or journal relaxation. Forge label follows recipe
capability (Toolsmith/Smelter/Baker), not stationWork presence.

## Evidence and practical limits

Retained full guardrails/check/native package PASS12m23. Later connected103Frontier
+864native checks and source/recovery slices pass. Final geometry/owner consumers:
47tests PASS48sec (forestry-final-consumers.log); ALL864native unit tests passed
0fail/error/skip, including package/build. Focused ledger/confirmation19 and
forestry/architecture checks pass. Full Frontier repeat intentionally stopped
in unchanged expensive ExpeditionSupplyFlowTest after jcmd attribution; do not
claim it passed. Old complete GameTests406/408: first-visibility fixed and9/9
slice passes; unrelated hive combat timeout also fails clean ef0bf8892. No hive
repair or global all-green claim belongs to this goal.

Native receipts in IMPL/build/forestry-native:
- growth-final-candidate.json OK17actions: real sapling/bonemeal/new source,
  player removal/no resurrection, replacement retained.
- handoff-post-power.json OK: two workers, HOT5logs/5wear, normal demand loss,
 4kCOLD to38logs, graceful restart/HOT return, same actors/tools/cargo, scopes0.
  Its custody/recovery logic is unchanged by final admission-envelope correction.
- external-reserve-shutdown.log22:34:37: exact unused effect cancelled on normal
  shutdown, followed by real resumed extraction. This caught/fixed a real gap.
- external-reserve-final-continuation.json OK15actions in retained private world:
  initial190logs/twoaxes/food, real HOT five earned logs, ordinary player's64log
  shift-click (external stock254 alone below256), actual final warehouse259,
  0jobs, both tools returned,0conflicts. Worker/chest frames reviewed. This is a
  CONTINUATION, not a new full primary-carrier pass. Earlier attempts interrupted
  by power or inadequate bounds are not accepted. No autonomous0->256claim.
  Separately NORMAL live R264 made2axes/167logs at81800,125stored/42carried,
  without fixture supplies, proving ordinary manufacture and background delivery.
- live-r265-full-pack-preflight.json OK6actions, same final source/actual15mod
  client, two forest frames reviewed. Client STOPPED. First visit4.5sec including
 1sec settle; recent64ticks mean3.54ms/max8.86ms; first-load peak285ms retained.
  Fast-forward0, durability queue empty at sample, latest summary1909/conflicts0.
  No universal<5sec/80ms or human/M3 claim. No more native campaigns are required
  merely to repeat these unchanged facts.

All private native servers/clients STOPPED; live service remains running.
JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Ignored RCON query helper build/forestry-native/query-runtime.mjs takes properties
path+command and never prints credentials. Use exact current properties/world;
never decode rotating snapshots in place. No concurrent Gradle mutations in one
output tree; freeze source/tests/scenarios during native evidence.

## Host incident

Latest user asked whether host switched off. Previous boot journal ends abruptly
18:00:23+05; new boot21:08:10. No orderly shutdown, OOM, kernel panic or thermal
trip evidence retained; exact cause UNKNOWN. EXT4 normal orphan cleanup; changed
source no NUL, artifact CRC/Git checks good. Old R263 recovered normally before
fresh deployment. Temporary systemd live unit and /tmp helpers disappeared at
boot; server restored. Do not attribute host power loss to the native feature
bug. Historical detail: docs/archive/continuity-20261025-r264-before-forestry-close.md.

## Player-map delivery

Source a9d5b36ef and pack33fa84b committed, no push. Java read-only players
observation returns x/y/z/yaw/dimension plus cached analytical UUID, cap32;
no scans, canonical mutations or save reads. Separate in-memory2sec collector
and /api/players, shared bounded RCON with performance; browser Map refresh
retains pan/zoom. Blue named direction markers, player selector and centering,
layer toggle. Missing/stale/failed/wrong-world observations show no marker;
other dimensions counted separately. No economic player history or client update.

IMPL/build/player-map-check.log: guardrails, scoped performance/economy tests,
JAR+verify PASS14sec. Python8tests PASS; JS and installer syntax pass. Local
Chrome/Playwright fixture browser11checks pass: coords/focus/refresh/toggle,
wrong-world/stale/error/disconnect; frame UI-only ui-fixture.png reviewed.
MCP Chrome binary missing; used installed /usr/bin/google-chrome, no MCP changes.
Clean detached release package PASS7sec. Pack validate PASS, installer tests8PASS.
Same-world deployment normal save/preflight/publish/install/start/verify PASS:
build/player-map-deploy-*.log and player-map/deployment.log. Analytics installed
in /home/rd/far-frontier-economy; service running, database preserved. Native
inspect players and /api/players agree on actual worldUUID, empty before client.
Actual full-pack PMAudit preflight PASS6actions, run9b3569d8-45e2-4143-a1ad-0be98ceac7b8.
Live browser on8093 with no mocked responses displayed real PMAudit at
561.5/64/34.5 yaw-135 in graybox; same analytical UUID, no browser errors.
live-map.png reviewed. Browser subsequently observed marker disappear after
actual client disconnect, native inspect confirms empty list. Receipts
build/player-map/live-*.{log,json,png}. Test client/browser/static8094 STOPPED;
live MC/economy services ACTIVE. No new functional world scenario or goal.
No simulation defect inferred from this short telemetry check. Other governance
WIP unrelated. Native/source and pack tracked clean; preserve .f0v-baseline/.

## R266 starter cleanup and live incident repair — complete

Source76c64ab13, source doc docs/startup-equipment-and-service-access-r266.md.
Normal genesis no longer grants four hardcoded engineering iron picks per town;
field-tool placement uses actual container capacity. Explicit engineering test
fixtures alone supply kits; generic custody/destruction tests use actual field
tools. Farming/mining equipment, finite food and configured public stocks remain
intentional startup supply. No economic rebalance, migration or inactive feature
removal. Fresh R266 removes old issued kits; R265 retained for incident evidence.

Original R265 failed23:23:40: native movement requireDeclared, then quarantine
forestry shutdown Optional.orElseThrow masked original failure and left RCON-only
JVM. All dimensions saved; residual service stopped. Actual copied save recovers
revision131378/tick37781 and exactly one missing access: resident:6-6, shipment
internal/19b8d17c-5d09-3191-b0bf-6f5d18cc2215, raw_iron1 from depot6 to smeltery6
INPUT. Logical capacity allowed the item, physical destination unavailable;
transient transfer readiness erased the declaration despite valid current job.

Common ephemeral ServiceAccessDemand now separates readyToEnter from identity.
Unready declared approaches wait outside; actual occupied footprints remain
blocking until clearance. Transfers retain exact workflow/phase checks. Draft
meal-source feasibility remains permitted before admission, while committed
movement still requires its current declared owner. Missing declaration includes
identity in error trace. Forestry shutdown issues no cancellation after quarantine
revokes command authority; journals and primary failure remain retained.

Evidence in IMPL/build/legacy-startup-audit:
- service-cause.log reproduces original missing declaration; service-cause-fixed.log
  recovers SAME revision: zero missing, exact old courier has declaration/allowed
  false with absent physical destination. Read-only isolated store copy, no world
  edits. New tests exercise station-full/unready/capacity release across codec,
  normal startup and quarantine shutdown. No caught/ignored invariant.
- final-check.log guardrails+63Frontier tests+package PASS44sec; unaffected
 36NeoForge tests incl shutdown/diagnostics passed in check.log. Prior failures
 were removed fixture assumptions; kits moved into explicit testFixtures, one
 draft-meal check preserved. No full unrelated expedition suite repeated.
- clean detached package PASS7sec; root validate/preflight/publish/install/fresh
 world/start/verify PASS. Root tracked clean at33fa84b, no pack edits/commit;
 .f0v-baseline/ remains unrelated. Both histories preserved. Analytics database
 preserved, latest API now reports R266 UUID; server-only change, no client update.
- live-preflight.json OK6actions, actual full-pack20FPS client,
 run7997991d-dd8d-46a7-82d7-59e21fa9bfa8; current forest frame reviewed.
 live-removed-kit.log reports old Clearwater pick not_found. After-client
 summary1647green/conflicts0. Test client STOPPED; MC/economy ACTIVE.
 Netty Connection reset by peer coincides exactly with test-client closure;
 no new runtime quarantine. This short preflight is not long-run economy or
 full engineering acceptance. No normal-shutdown/native campaign needs repetition
 solely for already-covered unchanged forestry effects.
