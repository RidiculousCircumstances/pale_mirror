# Continuity Ledger
Updated2026-10-09. Main alone; Terra stopped, subagents prohibited.
Canonical governance is here; sole workflow docs/engineering-agent-protocol.md.

## Current assignment
Current2026-10-09 follow-up: reusable block-resource extraction integrated with
farmer, after user approved a common Minecraft mining/harvest boundary for future
quarries. Main alone. Active design docs/frontier-v3-block-extraction.md;
order docs/work-orders/PM-BLOCK-EXTRACTION-20261009.md. No new goal/commit/push
requested. Prior resident-card WIP remains owned and preserved.
Implementation adds pure prepared-effect/port, generic native loot/block adapter,
generic codec inside the existing paired field witness, one family grain profile,
and exact-quantity accrual through existing fungible custody. Sowing/biology,
navigation/UAE and stock ownership remain separate. HOT reads Minecraft loot;
COLD uses the same declared deterministic output. Wheat remains grain-only1;
seed economy, quarry, random/component-bearing loot and equipment wear are not
claimed. Native field witness9/store17 require a fresh disposable deployment.
Focused harvest/custody/witness/codec checks PASS28s. Isolated real native adapter
check PASS (wheat and vanilla stone loot, source/tool/authority negatives).
Final NeoForge stateless suite PASS684 tests/0fail/1skip; real held farmer pilot
PASS12 actions, one last-cell harvest/replant/delivery after a hunger interruption.
General core native gate originally9/352 failures; user requested retirement of
legacy tests and repair of current coverage. None of those nine semantic
obligations is retired. Removed the unused synchronous scene setup overload and
cleanup helper; migrated every retained caller from synchronous materialization
to actual asynchronous readiness and ordinary observation. Fixed fixture bounds
(Minecraft empty template is1x1x1), complete body declaration, and semantic HOT
intervention. GameTest has unpaced ticks, so native I/O uses a real no-progress
watchdog plus finite outer test bound, not a40/80/100-tick readiness assumption.
`build/native-regressions-final.log`: all42 native tests PASS, including all9
original failures and8 strike consumers; all16 combat calibration seeds PASS.
Final retained Frontier/NeoForge test compilation and guardrails PASS10s
(`build/native-regressions-static-final.log`); source diff whitespace checks clean.
Task-owned native server stopped normally; no task client/server remains.
Full aggregate
release is not claimed: the earlier broad Frontier JVM was invalidated by a
concurrent dependency-JAR rewrite, recorded in the order. All subsequent builds
were serialized. No player-facing acceptance beyond that last-cell pilot.
R77 remains the live server; no server/world/hosted artifact changes in this task.

Previous completed follow-up:
Current follow-up: remove ResidentProfession end-to-end; add persisted resident
names and compact read-only resident cards (settlement, task/role, skills,
characteristics). Town Hall card must show settlement information including
treasury finances. Main alone; no subagents. Caravan pace/spacing change cancelled
before edits. Source starts clean63a7c011; user subsequently authorized deployment.
Active order docs/work-orders/PM-RESIDENT-CARDS-20261009.md.
Task WIP removes profession consumers/type, persists bounded genesis/birth names
under snapshot263/birth RBO3, and provides read-only resident/TownHall cards.
Town Hall has ordinary empty-hand block inspection with exact physical claim
validation; resident inspection validates its retained body. First broad Java
run found only seven obsolete profession-selection/nameplate assertions;
corrected affected cases and new identity/finance checks are green: focused
Frontier59/NeoForge9; terminal medical/name checks13 plus architecture7 and
NeoForge5. Guardrails, packaged JAR and pilot compilation PASS. Node53 tests and
all16 affected scenario declarations PASS. Main inspected actual client frames:
resident details wrap rather than disappear; Town Hall finances are readable.
Native cards terminal PASS64s, build/resident-cards-native-final-reviewed.json.
Geometry-only failed attempts and the initial loading-screen/clipped frames are
not acceptance evidence. Exact reviewed frames/limits are in the active order.
Resident name selectors now consume explicit delivered diagnostic references,
not removed profession labels. Medical patient role is distinct from care team;
final pure projection change verified after native card capture (no care UI claim).
R77 deployment COMPLETE; matching hosted client, new schema263 world and card-v2.
Full-pack ordinary live card inspection PASS71s; main reviewed both readable
frames, final summary green/conflicts0. Fixed stale full-pack --fullscreen after
an exact GLFW0x0 startup failure; no simulation change or extra matrix.
Delivery docs/frontier-v3-resident-cards-r77-20261009.md binds source/JAR/runtime.
No active goal/subagents/client/display/profiler or further test campaign.
macOS updater follow-up: user already downloaded/verified R77, then retirement
failed on mapfile. Outer installer now uses Bash3.2-compatible streamed prefixes
and tr instead of mapfile/${name,,}; bootstrap guards empty forwarded arguments.
Focused actual cleanup regression PASS (mixed-case retired JAR, preserved user
and hosted JARs, empty catalog); syntax/diff checks and hosted bootstrap pins
PASS on Linux. No macOS execution claim. Packwiz index/pack pins refreshed;
same updater can be rerun. No server/source/JAR/world changes for this fix.
Read-only live follow-up: user reports one Ironmeadow resident stuck by donkey.
Initial settlement8 cut33425 confirmed hungry1/activeMeal1/availableBread74;
by33914 all39 nourished, meals0, bread74. Last late nutrition update belongs to
Ada Vale/resident:8-1 at33562; her body had departed HOT at330,65,-25.
She subsequently ate in COLD; this is not proof the reported HOT obstruction
is fixed. Exact donkey/blocking cause unconfirmed; no source/server mutation.

Previous completed assignment:
User accepts connecting all eight remaining useful physical registries and
deleting the tenth obsolete field blast registry with related legacy. Also
explicitly requires delta field/click writes, durable before-effect witnesses,
legacy runtime pre-construction gate and common all-store recovery/drain.
No active goal. User now explicitly requests commit and server update; no push.
Receipt: docs/work-orders/PM-SHARED-STORAGE-20261009.md.

## Implementation and actual state
Implementation checkout /home/rd/proj/pm-f06r3-facility-lane-recovery,
Gradle root pale-mirror, branch feat/baker-carry-orders-20260926,
base e3377dd2fad28a6cb166f96512daabeb9a09a317.
Connected storage source committed63a7c011297216b58aac68686df0fc9932db40e8;
active checkout has current resident-card task WIP, branch HEAD unchanged63a7c011.
Private local release snapshots do not advance this branch/index; no remote push.
Current clean detached release /home/rd/proj/pm-resident-cards-r77-release-20261009,
source2b4b0e4600ebfcab155f9997d4bca1fe0f3d77e8/tree8b2a10a0cd8d8a8efd5cbab39ac67406d2ca9d6b.
Prior clean detached release /home/rd/proj/pm-shared-storage-r76-release-20261009.
Guardrails/JAR/package PASS11s there; exact artifact matches verified WIP SHA512.

All eight current non-actor families now use common journaled SavedData:
fields17 (prepared extraction witness9; previously16), graybox5, depot clicks1, boards1, hoppers1, observations3,
managed effects5, infection6. Actor11 custom codec remains on the same core
and joins common registration/flush/drain. Explicit world/dimension/kind/table
identity, fixed wire tags, exact owner replacement, complete checkpoint+tail
live/fallback/read-only recovery. Actor bounds4096rows/32MiB; other physical
streams131072rows/64MiB. Existing frame/queue/tail limits unchanged.
Field cells and effect queues have immutable256-record shards and small
progress headers. Forced append acknowledges dirty state; before-effect
blast/click/infection fences are explicit. Infection replacements retain the
exact predecessor; managed evidence retires after canonical confirmation.
Canonical transaction WAL and native storage remain separate current owners.

Legacy field stage claims/fences/receipts, writer/admission API and old field
blast ledger/executor/registrations/detail-only tests retired. Current cell
ownership/observation/player/world changes, hands and deliveries retained.
Selected v3 launch rejects old PaleMirrorRuntime before SavedData hydration;
broad callbacks/network/items guarded. Shared DH has an independent lifecycle.
Canonical and source architecture maps updated with shared storage invariant.
Performance contract/order record bounds, dispositions and evidence limits.

## Verification and delivery
Connected technical implementation verified. Full guardrails/check/build/JAR
gate PASS11m5s; unchanged Frontier1238tests/0failures, changed NeoForge gate
also green. Final focused physical adapter/journal/profile/launch/slice checks:
34tests/0failures,7s, build/shared-storage-final-focused.log. All eight real
adapters recover nonempty checkpoint+tail deltas/tombstones; clean no-op,
failed disk fence, wrong-world, field bucket and effect cursor cases covered.
Infection replacement preserves exact predecessor and rejects missing baseline.

Corrected native shared-storage slice36requiredPASS/0failed,52s:
body-lifetime20/field-turn15/shared composition1. Explicit graceful release
fixed the two component owner-replacement fixtures; competing owner rejection
retained. All dimensions saved11:26:50 and GameTest server exited.
One8096ms concurrent fixture keep-up warning is not live TPS evidence.
Final removal of unused duplicate file-name constants is behavior-neutral;
guardrails/compilePilot/JAR/package PASS11s afterward. Complete commands,
retired-test dispositions and limits are in the active order receipt.
Canonical architecture/continuity validators and source/governance diff hygiene
PASS. No task Gradle/GameTest/client/display remains. A simultaneous early
Gradle/native output race is discarded; do not share output trees concurrently.

Deployed JAR SHA512:
66cc549ad298857a7bcb491728254f7a6bd1d2430c95c342d6caa3017f4596bb6cfc552f04b2c6e4f266ab4ec4a13356645d19f7ff83c2da3835ed879f69ffe7.
Source63task-owned paths committed; no push. Pack validation/hosted publication,
pinned installation and clean-ref fresh-world preflight PASS. Ordinary full-pack
boundary and actual actor admission scenarios PASS, followed by same-world
save/stop/restart and fresh deployment verification PASS. Scope and evidence:
docs/frontier-v3-shared-storage-r76-20261009.md. No full-feature/HUMAN_CANDIDATE
acceptance or whole-path TPS speedup claim. No further test campaign is active.

## Live server — R77
Runtime /home/rd/far-frontier-server, far-frontier-v3-live.service.
Disposable world frontier-v3-resident-cards-r77-20261009, seed20260918065.
Production2b4b0e46; Java22, existing trade-playtest-r3 ruleset, view8/simulation6,
heap4–12GiB. Current invocation0193cdc915124847b39c0f34bfb160ae/start1791534455,
wrapper3982723/Java3982751. SHA5127618ce26dcb69efca602fc2d19e4c09e288611d03c84e7ab79ad94168c59c2a44d469719c983323bfc8ea0e2e0b29e8c570b3385e713d6aab1b94302cfe5135b.
After ordinary card inspection/disconnect: instant6325/revision2581,366 residents,
12 settlements, verdict green, required/scene/inventory/custody conflicts0.
Ordinary advance hold released for players. Capped20FPS task client exited;
server remains active. R76 saved/stopped normally and its exact45MiB world moved
to desktop trash, recoverable; old JAR archived by installer. No migration.
Old R75 disposition and shared-journal coverage remain in the R76 receipt.

New shared-ledger schemas required this fresh disposable world.
No manual conflict/history repair or automatic old-world migration.
Prior ~2s settlement tick peaks are not proven completely fixed by this work.
Prior R75 actor-only A/B7.674x does not measure the other eight streams;
historical receipt docs/frontier-v3-carrier-journal-r75-20261009.md remains valid.

## Repository boundaries and retained evidence
Outer pack /home/rd/proj/minecraft: unrelated untracked .f0v-baseline/.
Task WIP: scripts/install-client.sh, bootstrap-client.sh, retirement policy test,
index.toml and pack.toml for the macOS installer compatibility fix; no commit/push.
Original nested /home/rd/proj/minecraft/pale-mirror:23 unrelated WIP paths.
Unrelated WIP untouched. Canonical governance has mixed unrelated WIP; no blanket stage.
Inspect/report these separately. No source history migration or deletion.

Prior chronology and receipts preserved in:
- docs/archive/continuity-2026-10-09-before-shared-storage-delivery.md
- docs/archive/continuity-2026-10-08-r72-before-compaction.md
- docs/frontier-v3-caravan-bakery-r72-20261008.md
Historical assignments there are not current authority.
