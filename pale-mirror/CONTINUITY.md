# Active continuity — 2026-11-09

Worldgen inventory verified2026-11-09 from current PACK manifests and actual LIVE
mods: Terralith/Tectonic/Lithostitched/Structurify are ABSENT from active pack/server,
explicitly listed in config/retired-v3-mod-prefixes.txt. Historical full-pack WORLD
stack was Terralith+Tectonic+Structurify with Lithostitched dependency; root
architecture/compatibility docs retain historical entries, not installed truth.
Fast Noise1.0.13 is installed (accelerator); C2ME is optional/defaultfalse and absent
from live mods. Quark is installed with world features. Northwatch datapack uses
PM agricultural_flat with three authored agricultural biomes, not Terralith terrain.
Upcoming natural-biome work must explicitly restore/select the worldgen stack and
integrate PM terrain/land knowledge; adding JARs alone does not replace this generator.

Current design direction (user2026-11-09): upcoming actual worldgen biomes must
produce natural agricultural territories, settlement specialization and substantial
fields sized to served population including exports. User likes the full Create
bread chain, with manual versus automated operation eventually determined by
settlement development; asks how to configure it NOW. No implementation requested
in this question. Proposed baseline: manual milling/kneading, fuelled baking,
approved3wheat->1bread and2bread/daily meal; one shared physical material chain
across later technology providers. Field area from daily grain demand * growth
period / yield, not decorative acreage. Illustrative30served:180grain/day,
4daygrowth/yield1=>720productive cells (paths/irrigation/fallow extra);90served
=>2160cells. These growth/yield/labour values are proposals, not approved or live.
Current CultivationRules maximumCells1024 is a settlement total (CultivationPlanning
subtracts existing area); exporting field scale will require explicit bounded
capacity/performance work. Existing field/batch scheduling should be reused;
no per-tick whole-field scans or permanent worker per plant. Prefer future
mechanization benefits in labour/throughput while preserving agreed material
policy; existing Create recipes still require explicit reconciliation.

Previous research order: crafting/resource costs, especially bread productivity.
RESEARCH COMPLETE; recipes, source and runtime unchanged. Main alone, no agents/push.
Report `docs/research/resource-cost-balance-20261108.md` plus adjacent JSON; evidence
IMPL/build/resource-cost-audit-20261108, frozen124384/t49000/r38.334alive,
1711stored bread across12towns,13hungry0starving; Northwatch23alive/115bread.
Actual NPC3wheat->1bread (64->21+1left),80work EACH loaf; no batch-roundup bug.
Life72ticks/unit,1000capacity, eatBelow668, bread1000 but333useful at threshold;
~1loaf/day/person. Nutrition500 makes no difference,200 gives2items/one meal/day.
Initial128bread PER TOWN masks shortages4–6days. Idealized worker1.17x+ironhoe
harvest1.5x gives20/40/80bread per farmer workday for yield1/2/4; excludes travel,
growth constraints, maintenance/logistics. Combined direct field+bake FTE for30:
1.67/0.92/0.54. Actual steady throughput NOT measured. Installed Create6.0.10
milling wheat->1flour+25%2extra, then1dough->1bread:64guaranteed/96expected from64
wheat versus NPC21. FD egg dough also more efficient. Need pack-wide tech parity
before merely increasing NPC wheat cost. Clothes are not a recurring cloth sink;
wood ratios vanilla; iron repairs restore HALF125durability, not full250.
2026-11-09 user ACCEPTED the direction: keep3wheat->1bread, approximately2bread
per daily meal,4x baking work, separately define Create mechanization advantage.
Research numerical candidate nutrition200/work320 is not implemented. User also
wants an intermediate milling/flour link and currently asks how Create does it.
Verified installed6.0.10 recipes: wheat Millstone1flour+25%2extra (+25%seed),
flour->dough via mixing with1000mBwater or fan washing,1dough->1bread via cooking.
Millstone needs rotation; processing speed depends on RPM. Direct crushing/wheat
recipe absent (do not infer crushing fallback). Proposed NPC chain3wheat->3flour
->1dough->1bread preserves approved total; shared Create flour would expose its
existing1flour->1bread path, so cross-provider recipe policy must be explicit.
No source/runtime/economic changes this turn. Future implementation uses a new
profile preserving r38; milling station/physical integration is not yet authored.


Previous restaurant order: make the restaurant's remaining chairs/benches functional.
TECHNICAL DELIVERY COMPLETE WITH LIMITS; verified, committed and deployed below.
Main alone; SA audit paused, Terra stopped, no subagents or push. AGENTS and
engineering-agent-protocol remain authoritative. No M3/HUMAN_CANDIDATE or universal
latency claim; unrelated product orders are not promoted by this delivery.

IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, Git root parent,
branch feat/baker-carry-orders-20260926, HEAD0375cad2594179b9489ce9d085ea11fc3a9ddb22.
Tracked clean; unrelated .serena retained. PACK `/home/rd/proj/minecraft`
HEAD909c26c, tracked clean; unrelated .f0v-baseline retained. GOV
`/home/rd/proj/pm-governance` has extensive unrelated WIP: preserve all. Own scope
is this ledger, ONE resident_furniture_boundary architecture line, and
`docs/verification/northwatch-restaurant-furniture-20261108.json`. Never stage
whole architecture.yml; stage own line against HEAD. No repository migration.

## Functional restaurant

New independent kitchen_furnished_5, style pale_mirror:medieval_spruce_furnished,
ruleset frontier-v3-furnished-restaurant-r38. R37 assets/profile remain immutable;
unit evidence proves other settlements unchanged. Actual original staircase
retained. Replaced reviewed ground/upper-floor/terrace fake stair furniture and
seven blank sign armrests (exact block and typed visual SNBT SHA256 preimages).
28 seats:16DINING+12REST, physically22chairs+6bench cells;16table sections.
Old kitchen had6DINING+2REST. No authored extra containers or fires introduced.

SeatTable is explicitly Optional in BuildingSeat/SeatPlace. Markup6 requires
both table=none/tableBlock=none for tableless REST; DINING requires a supported
exact table. Shared SeatState, activity owners, navigation, native provider and
non-replayable operation journal retain authority. No new executor or navigator.
SeatStateCodec persists table presence; snapshot schema352 rejects351 before
hydration; no heuristic conversion. New world was required and authorized.
Generic native authoring now checks connected clear approach/egress for each
seat. Initial terrace contacts and upper table neighbour states were corrected
through authored content, without carving architecture or runtime patches.

Evidence: IMPL/build/restaurant-furniture-20261108 and receipt above.
Targeted JVM49/49 PASS: layout, explicit none/dining rejection, optional-seat wire,
truncation, old schema rejection, ordinary/interrupted/recovered meals/leisure.
Guardrails and verifyPackagedJar PASS (/tmp/pm-restaurant-gates.log).
Native building-authoring11/11 PASS (/tmp/pm-restaurant-authoring5.log), actual
registry certificate and all4furniture rotations. Deterministic pinned import
PASS; forged sign payload rejected before any output. Initial concurrent Gradle
compilation invalidated loaded test classes; serial rerun passed. No whole-suite
claim: prior unrelated architecture/JVM/Node debt remains in previous receipt.

Checked-in disposable-northwatch-furnished-restaurant scenario PASS,
run931bd2fe-907a-4f48-84fd-ad75279599ef,27actions/5frames, graceful restart after5,
same fullpack Java22 client. Resident1-12 tableless bench furnished_10 and1-17
chair furnished_26 seat and leave through common owners; both ordinal1 CLOSED,
seat NONE, no movement; conflicts0. All5frames personally viewed; ground seated
legs persist through restart and exit coherently. Upper frame is close from
behind, not full pose aesthetic proof. First attempt inherited a camera inside
the staircase; corrected camera retry passed. Two representative native flows,
not an NPC replay on every seat or a dining-task acceptance for every new chair.

Fresh VibeCraft MCP before/after scans (12825blocks each) show all44furniture
blocks exactly match authored IDs/properties, zero mismatches; real stairs
retained. Before image and fresh central/window/upper/MCP after images personally
viewed. Final live layout inspection runbcdd6828-284e-4aef-8d5d-e6c2751037a2 PASS.
First MCP scan during receiving-level screen failed; one retry after compiled
chunks passed. No canonical, world-file or live-block edits used for evidence.
No owned native client/Xvfb/bridge/JFR remains active.

## Current runtime

LIVE `/home/rd/far-frontier-server`, far-frontier-v3-live.service ACTIVE.
Clean DETACHED source `/home/rd/proj/pm-restaurant-release-20261108/pale-mirror`
at0375cad25. Build/JAR package, preflight, publish/install, fresh postverify and
final postverify PASS. Metadata `/tmp/pm-restaurant-release.json`; operation logs
`/tmp/pm-restaurant-release-{build,preflight,publish,install,verify,final-verify}.log`.
SHA512 edb9cea4df66620af86fc0607d1d109546dabff0d2782f8e030dbbb733b10f404a518ab3e474aca353774b52d37e7b87d0d23a5645c39fff1a13e889ceb5aa9c.
Wrapper1438432/Java1438456; invocation3fe78a2407754eec8bf032d642156dac.
NEW world northwatch-restaurant-seed139-20261108, seed139/r38/schema352/network10.
Old northwatch-leisure-seed139-20261108 directory preserved; normal old Java1096584
stop proved all dimensions saved, exact process exit and port free. Required
managed graybox datapack installed before new genesis. Java22
`/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`,4–12G.
Preserve HTTP8092/8094/observatory. NotBefore1794154053. Final snapshot t4016:
319residents/12settlements, diagnostic green, required/scene/inventory conflicts0.
WIP verified candidate and clean deployed JAR have identical entries/content
except declared sourceTree provenance (clean7e3793cc40ae88fbff8e521f1c988994261937e0).

## Previous completed resident audit

Statistical resident combat/navigation/planning repair remains COMPLETE at
6391187f5 (ancestor); detailed causal evidence, commit list, old required incidents,
failed intermediates and baseline test debt are retained in
`docs/verification/northwatch-resident-execution-audit-20261108.json`.
Earlier combat receipt: `docs/verification/northwatch-combat-latency-20261108.json`.
Prior evidence IMPL/build/resident-execution-audit-20261108. Release17 SAME oldworld
final pair305103->308503(+3400ticks):347alive,341nourished6hungry0starving, zero old
meals/unclockedCOLDmoves/expiredroutes/unheldoverdue/RESTING+movement overlaps.
Historical required incident first14991,last1497409,occ5 remains in that preserved
old world; no claim it was repaired by erasure. Current newworld green is separate.
The prior fixes cover clinical contradiction, physical suffix starvation, melee
admission, bounded local access/service clearance, exact body departure/retired
passenger cleanup, deferred transition composition, fighting guard clearance and
returning group guide contact geometry. No new extension waives their boundaries.

Skills/manual already read: Serena; visual-audit, foundry-audit,
release-verification, test-server-ops+operations-map, prior worldgen-performance
and engine-source. Resume from retained facts; do not repeat whole historical
reference dumps. Relevant execution semantics remain read and binding.
