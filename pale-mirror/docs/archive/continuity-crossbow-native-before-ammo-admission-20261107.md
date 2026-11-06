# Active continuity — 2026-11-07

## Current assignment
Main alone; no subagents or push. Implement user-requested crossbow production,
forge arrowheads (multiple per ingot), cloth-fletched arrows, settlement-planned
crossbow guards with real ammunition and coordinated ranged combat supporting
existing swordsmen. Reuse common production, storage/custody, UAE, navigation,
security contacts and durable physical operation protocol. No COLD combat yet.
Commit coherent verified changes and deploy on completion. Previous navigation
repair delivered and goal completed; receipt below remains valid for that ref.

Implementation sequence: (1) catalog/recipes/items and explicit guard combat-role
mix; (2) shared tactical-method boundary, actor ammunition acquisition/consumption
and durable shot lifecycle; (3) native crossbow/reload/projectile, line-of-fire,
range/retreat/support positioning through shared navigator; (4) focused ordinary,
negative and recovery checks plus native scenario, clean-ref build/deploy.
CURRENT WIP / unresolved native failure (do not claim complete): source commits
1b4a7477b feature +cf67f19c3 pilot-selector parity. IMPL tracked clean, .serena/
untracked local. Detached RELEASE1b4a7477b build/guardrails/package PASS; live was
installed as r33/schema344 fresh crossbow-guards-seed139-20261107, seed139,
core5602e03547c1cf43af24fdcea7f57e3ed1bc04b38bf3f25d4d71013f326acb4baf2565d2ad2258095c9a2b0e3c034142f9a4a3b2dc64b90dd9edbce765afb279.
LIVE IS NOW STOPPED/QUARANTINED after actual player deposit/guard issue:
IllegalStateException confirmed equipment transfer could not retain its storage
disposition, EquipmentServicePort19 <-EquipmentIssueExecutor.confirm176.
Keep this world and latest.log for diagnosis. It saved all dimensions normally,
quiesced unused0/pending25. No task client or Xvfb remains (2273204 stopped).
Evidence /tmp/pm-crossbow-equipment-client-2.log and build/crossbow-native/;
first attempt rejected Java pilot whitelist, fixedcf67 with focused parser +guardrails.
New real findings:
1. Catalog mistakenlydeclared crossbow durability326, MC pinned Items.CROSSBOW is465
(ModLens Items1871; real dependency source). Correct catalog, repairhalf232, unit
fixtures and native registry-conformance assertion before next native run.
2. Equipment issue/return confirmation submits domain transition, then separately
checkpoints whole-storage image. Pending durability/predecessor is treated as
false/fatal; even accepted first transition can release work before second closure.
Need atomic common physical-intent confirmation + native whole-storage image
receipt command, using existing lifecycle capability dispatch and reference
mutation closure/unresolved protocol. Do not just swallow wait or canonical-close
foreign physical contents. Suggested generic PhysicalIntentStorageConfirmed
contains complete expected PhysicalIntent, effect receipt and exact provider/
container/epoch/native image/provenance; existing physical command owner delegates
family transition, previews its proof-bound successor, appends storage closure or
isolated mismatch in same canonical command. No new family-inspecting coordinator.
3. Full settlement diagnostic exceeds8192byte budget once new commodities +work
appear (response_limit at instant~4800), hidingguard facts. Add bounded separate
settlement_security view for client checks, no arbitrary byte-limit increase.
WIP implemented: PhysicalIntentStorageConfirmed carries complete expectedintent,
actualstoragelease and observedwholeimage; existingphysicalowner dispatchesexact
lifecycle, previews proof-bound consequence, appendsclosedreference orlocal
OBSERVATION_MISMATCH in samecanonicalcommand. Nativeissue/return nowusesit, no
secondstoragecommand. Guardparameterizedunit tests clean/mismatchedstorage,
staleepoch, codec, replay andsnapshot PASS;Rangeddurabilitynow465/repair232;
newnativecatalog-vs-MCregistry assertion PASS within56localnavtests.
Newbounded settlement_security diagnostic added; bothpilots migrated, Java/Node
parsers agree. Relevantfinal checks:116canonical PASS,48Java pilotparser tests PASS,Node
scenario tests PASS;56native localnav PASS incl465registryconformance;5native
reference-conflict-restart PASS inside mistaken reference-all23run (8known
reference/adoption failures, notgreenwholegate). Negativeprovider/missingintent
check nowrunning /tmp/pm-crossbow-atomic-negative.log. Need recommitclean-ref
build/freshworld (r33 ruleshash
will change with correctdurability), deploy and execute both checked-in crossbow
carriers plus restart; then scopedGOVcommit. No push, no subagents.
Already valid checks:115 canonical including8ranged+3realmanufacture, allPASS in
/tmp/pm-crossbow-boundary-gates.log;56 local-nav native including4newranged PASS
in /tmp/pm-crossbow-targeted-final.log. Full native460 completed,18failures in
oldpatrol/reference/adoption/production fixtures, not all classified; no newranged
failure. Unfiltered check interrupted afteroldfailures; priorcleance872 reproduces
6failures/19cases in /tmp/pm-crossbow-baseline-fixtures.log. Nofullsuite/release/M3
claim. Actor emission/ammo/wearjournalled; later projectile isvanillatransient,
neverrespawn/refundonabsence. No COLD combat.

Canonical AGENTS.md, execution protocol and execution semantics apply. Previous
receipt: docs/frontier-v3-navigation-passage-cellar-repair-20261107.md.

## Working set and boundaries
IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, Git root parent,
branch feat/baker-carry-orders-20260926, HEAD1b4a7477b; coherent crossbow cut committed; native delivery checks ongoing.
RELEASE `/home/rd/proj/pm-r242-economic-release/pale-mirror`, CLEAN detached1b4a7477b;
/tmp/pm-crossbow-detached-build.log guardrails/JAR/package PASS. Previous receipt:
build/navigation-delivery-release.log guardrails/JAR/package PASS.
PACK `/home/rd/proj/minecraft` HEAD98a6701 tracked clean, unrelated `.f0v-baseline/`
untracked retained. GOV extensive unrelated WIP remains; only this ledger, new
receipt/archive and three isolated architecture keys belong to current closeout.
Never stage whole architecture.yml or unrelated GOV files. No push.
Java21 build `/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`.
Live Java22 `/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`.

## Delivered source
- d69f6e085: shared HOT navigation retains semantic destination across bounded
  legs. Missing segments use existing cooperative planner through explicit
  registered owner knowledge. WAITING differs from completed KNOWN_NO_PATH;
  intermediate arrival/native local failure cannot complete or retarget work.
  Proven known refusal still permits field-owned target selection. Native partial
  prefixes continue; same-turn clearance query reused; body permission references weak.
- Same commit: vanilla gate facing reversal included in native input declaration;
  axis rotation remains structural. Exact enclosed source underground AIR retained
  and surveyed/cleared through site preparation. Hall140 lost air cells restored;
  wet/unknown cells refused, no component-box or outdoor-air excavation.
- Earlier a609/9aff already own porch-carving, ordinary door/linked-leaf packet
  continuation, public waiting/dining drift and damage/function separation.
  Current delivery preserves/tests those integrations, not duplicate owners.
-54023348f: live guard issue crashed on a player sword in a bread-only kitchen
  barrel. Shared exact withdrawal now differs from receiving/return filters and
  validates actual owner/custody. Selection, physical issue and field acquisition
  agree. Foreign/missing/conflicting source remains refused.
-4e9cec1a2: both native equipment directions settle the whole storage image through
  existing reference custody before releasing work witness; actual foreign slots
  stay preserved UNRESOLVED. No permissive overwrite or another storage protocol.
-694d3c5de: decrementing fast-forward counter missed time published by WAL between
  slices, overshooting21000 to21002 and failing correct receipt validation. One
  immutable admitted interval ceiling now derives remaining from canonical time,
  for relative and absolute requests. Same world recovered, then held EXACT21200.
-ce8722295: retained meal admission/proof is local to exact actor/claim/life and
  suspension/safety facts; it no longer collects global work/storage dependencies.
  Meal absence/death/suspension/safety releases it. No wake or priority suppressed.

## Verification and limits
Evidence IMPL build/navigation-delay-20261107/:34 selected JVM plus52 native
local-navigation and8 native building-authoring PASS;23equipment JVM,5native
reference-conflict/restart,10runtime/calendar/safety and23activity/meal/guard checks
PASS. Relevant guardrails/JAR/package gates PASS. Native equipment unit covers
actual take/place/common closure/serialization/foreign contents, not whole guard
lookup. Existing exact-four-storage-address test updated to own critical addresses
and exclusion of foreign stores. No test skipped/deleted. Independent baseline
resource-adoption/reference-projection failures remain classified in earlier archive;
no whole-suite-green claim.

Checked-in live-authored-bakery-gates-and-cellar.json PASS34actions/5frames,
run3c5ba587-54d1-4754-9519-052aef199a2c. Gates used from both sides, exact states,
INTACT/0damage; real cellar room inspected. Later ignored exploratory fullpack
run e7376520-f681-419e-8638-06f61e273171 PASS23actions/6frames, final-hot-meals/manifest.json.
Textile/hall/smeltery/kitchen frames personally viewed. HOT OwenVale1-14 moved to
kitchen, CONSUME23434, confirmed nutrition23462, NOURISHED/NONE23786 and cleared
outside service by24391. All23Northwatch residents nourished by30912. Private Xvfb,
not human/M3 or formal promotion of ignored scenario.

Two45s normal HOT traces on694d3c5de:19.932TPS/debt1→9, then23.799TPS catching
170→1. Graph/dependency links closed, no JFR loss; unexplained2.068%/1.366% means
causal closure incomplete. A peak-free server/control speedup ratio is not proved.
Second trace exposed1.959s activity admission; final local-proof correction has
functional/source coverage, no fresh controlled performance ratio. Worker slice
wall times are not whole-route or server-tick latency. COLD tick-query0.2ms does
not establish HOT CPU health. See receipt for measured scope/method failures.

## Live delivery
Runtime `/home/rd/far-frontier-server`, transient far-frontier-v3-live.service ACTIVE.
World navigation-closure-seed139-20261107,seed139,r32/schema343. Same world recovered
through clock fix, not reset to hide it. Previous navigation-passages-cellars world
retained with guard crash/conflict data; older storage worlds retained as archived.
Final source ce8722295,core SHA512
3bde58730492f083c63e7a75ab62959f1f60515a9b86caef0f35d4f97e53f37b6d2adb49376aef8e5ab2fb428bf32b3de2bb3ddc30911f575fd8e2d2936ece82.
Visuals unchanged2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Before replacement all dimensions saved,quiesce unused0/pending0. Pack index170
verified; delivery-preflight.log/delivery-verify.log PASS. Invocation
acfdbb57670b497f86ed01741ac7e98d,wrapper1587866,notBefore1793993732.
Postrestart34801:0required/inventory/scene conflicts,0preparing; Northwatch23nourished,
0hungry/0active meals,120available bread. Later35500 debt2/no fastforward. Normal
native tickrate20; initial startup samples excluded from performance claims.
No task client/display running. PMBakeryAudit fullpack uses railway-client mods
with EMF/ETF and20FPS; server mod folder is an invalid client pack. Human processes
untouched. RCON helper IMPL build/forestry-native/query-runtime.mjs; never print
server.properties credentials. Close goal and report delivered state after GOV commit.
