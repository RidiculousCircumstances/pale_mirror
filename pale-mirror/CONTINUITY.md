# Active continuity — 2026-11-06

## Assignment and outcome

Main alone; no subagents, new goal or push. User authorized the connected repair:
confirmed block damage vs attempted hits; reusable integrity/function separation;
Northwatch feeding/access; town-hall doors; terrain-aware placement; faithful native
imports and actual client review. Implementation and disposable deployment are now
complete. Autonomous resident repair/construction remains deferred; repair-ready
contracts are implemented. No human M3 or whole-repository green claim.

Canonical task plans and evidence:
- docs/frontier-v3-structural-integrity-and-repair-20261106.md
- docs/frontier-v3-placement-and-northwatch-food-20261106.md
- docs/archive/continuity-20261106-structural-wip-start.md (initial state)

## Repositories / delivery identity

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror; Git root parent;
branch feat/baker-carry-orders-20260926. HEAD6a343e01146f2db19ecd8a70c10618000537ad4a,
clean. Main implementation d2369cdf2, later semantic/test closure and final site fix
included in HEAD. RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror clean,
detached at that same exact commit. Only verified detached artifacts were published.
PACK /home/rd/proj/minecraft HEAD98a6701: tracked unchanged,170index files valid;
unrelated untracked .f0v-baseline/ preserved. GOV /home/rd/proj/pm-governance has
extensive unrelated WIP: stage/commit only owned task plans, ledger, initial archive
and the two owned architecture boundary lines. Do not absorb other changes.

LIVE /home/rd/far-frontier-server; far-frontier-v3-live.service. Final world
buildings-schema342-site2-seed139-20261106, seed139, frontier-v3-textiles-terrain-r32,
schema342/graybox ledger7/Network7. Wrapper2559129, native Java2559153, TCP25565;
start notBefore1793956640, invocation61b2050bc0254c44b8adb526b7546a38.
Core SHA512:
b3e8e8df57b20df8042fc9cbaf62718642ed36774968df6b83a0a2293723f9b3b1d66f7d83758100ecf827fb43d57150d9510c6130577f5b0c13648e01f00ec7
Visuals unchanged, SHA512:
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601

Both prior worlds were stopped through authenticated native RCON; all dimensions
saved and native JVM/port exited before install. Final preflight and deploy verification
passed. No previous world was silently recovered under new content. Latest after-client
summary at tick4912/revision5684: ok/green retained-conflict scope,12settlements,
319residents,0inventory/scene/required/replica conflicts. This is a bounded check,
not proof of continuous economic solvency or every actor's progress.

Build Java21: /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2.
Live Java22: /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
RCON helper: IMPL build/forestry-native/query-runtime.mjs; do not print secrets.
All task native test/pilot clients are closed; full-pack client was limited to20FPS.
The live service remains running. Do not leave a graphical client idle between checks.

## Root causes and implemented ownership

Old Northwatch had23residents/15hungry despite77unclaimed usable pantry bread.
START_DESTROY incorrectly recorded losses for two unchanged kitchen cells; these
semantic losses also masked collision as UNKNOWN. Removing only those two false
facts in an offline discriminator recovered the exact10-anchor pantry exit.
Roof-heavy actor-hash candidate ordering amplified waits; some guard holds were
independent. The old full diagnostic is in the placement plan, not a universal
claim that every hungry actor had one cause.

StructureIntegrity alone projects exact lost/restored cells and repair evidence
(ABSENT/REPLACED/UNKNOWN). StructureCellObserved carries the precise owner/part,
actual collision and original-material result atomically. Native-equivalent visual
serialization and operating properties are not material damage. FacilityFunctionAvailability
consumes owner-declared requirements; ProductionFacilityAvailability owns exact
job/station/workplace relationships. Navigation owns physical access; UNKNOWN stays
a wait. Exact bed head/foot facts determine capacity; farmhouse damage does not own
crop biology. Surviving authored fabric remains real despite aggregate DESTROYED,
including the shared infection-contact geometry index.

STRUCTURE uses existing ManagedBlockWrites, paged graybox journal and confirmed
operation ledger. START admits a hit; abort/no-op creates no loss. A bounded ordinary
player-input continuation revalidates player/hand/world/reach/predecessors and invokes
the whole native game-mode call once. Unattempted work expires/cancels; it is never
replayed after restart. Hall cards no longer intercept interactive door/container clicks.

KnownLocalAccess supplies a bounded positive nearest proof (2048visits,radius16,
cache64); miss falls back to ordinary cooperative navigation, never global NO_PATH.
Meal approaches use shared proofs/batched goals and service-grade ordering. Geometry
retains exact observed collision after semantic damage; loss-only facts invalidate
stale observations until actual shape arrives. No alternate mover or body shrink.

BuildingSiteSurvey/Planner/Plan own bounded survey, grade and explicit cut/fill;
markup4 separates outdoor ground, entrance supports and intended excavation. Source
AIR below grade is not excavation authority. Hall uses pinned original townhall5;
other explicit selections and native materials/NBT remain faithful.

First full-pack review rejected the initially deployed depot datum2: it buried the
original courtyard furniture. Final importer accepts explicit bounded 3D entrance
supports/threshold independently of yard grade. Depot now retains original loading
platform/path at sourceY2 and original outdoor gradeY1. No extra apron, architectural
substitution, carved aisle or relaxed donkey profile was required. Kernel personal
and donkey mixed trips still load/deliver/return/recover through the ordinary owners.

## Verification and limits

Receipts under IMPL build/northwatch-dining-placement:
- native-v9/manifest.json PASS: real hall open/close, aborted hit/no damage, actual
  destruction, SAMEworld graceful restart, one ordinary restoration. Floor frame opened.
- dining-native8/manifest.json PASS: physically HOT resident, actual192->191pantry
  bread, NOURISHED/NONE,0claim. Frame is doorway, not visible eating-animation proof.
- Native core448/448 and NeoForge unit921/921 passed before final content-only changes;
  reuse only for their unchanged protocols. All native test JVMs closed.
- depot-site-native-authoring.log: final original native/NBT/collision checks5/5 PASS.
- depot-grade-animal-closure.log: personal and donkey kernel histories PASS1m6sec.
- semantic-cohort-closure.log: distinct residents actually fed under SAME WorkBudget4,
  driven by retained due boundaries rather than the obsolete3000tick stopwatch.
- Final RELEASE build/structural-release342/site2-release-gates.log: affected catalog,
  layout and graybox42/42, guardrails, jar and verifyPackagedJar PASS48sec. Prior
  scoped-closure-gates2.log and final-delivery-gates.log cover unchanged integrity,
  meals, production, readability, guard/bed/reserve/restoration and codec boundaries.
- live-fullpack342-site2/manifest.json PASS23actions/11frames, loaded-mod inventoryPASS,
  run e37c0ce2-32e5-44df-a888-820e70e593e6. Opened final depot original frame and full
  11-facade contact sheet: courtyard/furniture at street grade, raised loading intact,
  original hall stonework/materials visible. This is street-level automated review.
- live-site2-delivery.json binds exact source/artifact/world/process and review limits.

Whole historical Frontier suite is NOT green. Attempt33b43 reported2287tests and
84assertion failures plus2harness errors;71exact failures reproduced on clean OLD1af.
Its13new differences were closed by scoped checks (actual cells vs aggregate labels,
positive geometry after restoration, real journey time, explicit loading fixtures).
No test was silently disabled/deleted. The broad runner also exhausted a4GiB worker
and a legacy construction CPU loop was explicitly stopped after9minutes. Keep this
separate from the scoped delivery; do not claim all historical tests or all unsupported
families were repaired. Retained controls: IMPL build/northwatch-dining-placement/
baseline-control/comparison.json and logs. Read-only baseline checkout remains
/home/rd/proj/pm-structural342-baseline at1af1feca7, no JVM running.

## Remaining handoff

Implementation, deployment and native review are complete. This record and the
owned plans form the task's governance closure; only the concise user delivery
report remains. No further implementation or test campaign is needed absent new evidence. Preserve
all unrelated GOV/pack WIP. Autonomous resident repair, arbitrary-world terrain adapter,
indoor animal transshipment and human M3 are not delivered in this cut.
