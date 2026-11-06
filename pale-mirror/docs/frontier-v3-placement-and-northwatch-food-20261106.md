# Building placement and Northwatch food — diagnosis and proposal, 2026-11-06

Status: live diagnosis completed; placement design proposed, not implemented.
No Java, live blocks, player inventories, artifact or world were changed.

## Bound evidence

Live source 1af1feca73d6d6c73b450ffa605cfd68ce6f66c9; service
far-frontier-v3-live.service PID75296; world textiles-schema341-seed139-20261106,
seed139/r32/schema341. IMPL docs/carrier HEAD e4ec16b87 unchanged.
Read-only receipts are in IMPL build/northwatch-dining-placement.

Initial resident sample at ticks342993..343097: 23 living residents,8 nourished,
3 hungry,12 starving. Kitchen pantry container:pantry-340ff79b-f6fd-3b36-ad0f-cf41d396ae46
held77 usable bread,0 claimed. Container diagnostic: ACTIVE, OBSERVED_CURRENT,
ACQUIRED epoch14, no conflict. Not food sold away or absent from the kitchen.
Some residents continue eating; bread later74. This is selective starvation,
not total stoppage. Resident1-3 remains STARVING/no meal at tick347225 and reports
TARGET_COMPONENT_NEEDS_GEOMETRY; later feeding by other residents does not close it.

Planning diagnostic gives exact missing geometry for kitchen-clearance requests:
(-1458,63,-1315) and(-1459,64,-1313), observation KNOWN. Both still have persisted
PLAYER/KNOWN_SEMANTIC_LOSS. Live execute-if-block confirmed original spruce_planks
and domum_ornamentum:double_crossed respectively. These are exactly the two aborted
hit cells identified in the integrity diagnosis. KnownPedestrianGround.restricted
and the common obstacle index mask physicalDeltas as unknown; ordinary observation
has not reconciled these false loss declarations.

On a copied normal snapshot revision1473488/tick344800, the actual shared route
from pantry station(-1452,63,-1313) to floor(-1457,63,-1317) throws
TARGET_COMPONENT_NEEDS_GEOMETRY. Reconstructing ONLY the offline immutable state
without those two false deltas makes the same query return a10-anchor route.
This is a causal discriminator, not a live repair or native acceptance test.

Separate amplifier: ServiceWaitingTargets enumerates every clear surface in its
XZ buffer, including roofs; ServiceClearanceTargets picks an actor-hash offset and
throws at the first unfinished candidate. At this snapshot the pantry has87 waiting
candidates,49 atY67..74 against serviceY63. Resident1-3's first candidate isY73,
resident1-22'sY70. Live continuation requests also target these roof levels.
An unfinished destination candidate must not become an endless strict-prefix
wait before an independently provable reachable same-floor alternative. This is not evidence that every roof
is universally inaccessible or that all15 hungry residents have identical holds;
guards also retain owner safety checkpoints and need exact end-to-end verification.

Scoped SETTLED kitchen ingress audit passed2814runtimechecks,0pending/unverified/
mismatch,2openports. This does not prove all interior navigation or absence of false
canonical damage. The symptom is before meal admission, not food consumption being
conditioned on returning home.

## Placement contradiction

Screens supplied by the user show Northwatch: buried lower hall, sunken farm/depot
courtyards, recessed textile entrance and isolated ground remnants around furniture.
This matches actual imported geometry and the existing terrain datum policy.

SettlementLayoutPlanner anchors the declared entrance support at terrain.supportYAt.
The importer separately copies ALL source AIR at/below that entrance floor inside
its extended horizontal extent. FrontierGrayboxPlan writes these as real AIR.
There is no semantic distinction between intentional cellar/clearance and empty
source space that should retain terrain. In this layout the farm declares235 AIR
cells at street-supportY63, depot41, hall72, textile80 plus27 atY62. Preserving
original materials therefore did not by itself produce a correct site fit.

The current pad gate mainly demands equal natural terrain height under relevant
foundation columns. The road check proves only the selected frontage support and
public network. It does not plan courtyard grade or general terrain transitions.
Non-flat settlement sites are predominantly rejected; there is no general cut/fill,
retaining-wall or foundation adaptation planner. Existing terrain fixtures outside
settlement pads are not proof of uneven-site building placement support.

## Proposed responsibility boundary

Retain BuildingPlacement as the sole local/world transform. Do not make the runtime
navigator, importer or individual work owners choose a building's height.

1. Building content declares site requirements independently of its original blocks:
   outdoor ground datum, entrance thresholds, foundation footprint, true cellar/
   excavation and keep-clear volumes, courtyard surfaces and connection ports.
   Source AIR is not automatically an excavation instruction. Preserve original
   building material/shape; classify functional adaptations explicitly.
2. Shared geometry supplies an immutable bounded terrain/obstruction survey using
   the same multi-level collision facts as navigation. No second height-map-only
   collision model or diagnostic chunk force-loading.
3. A pure bounded site-placement planner evaluates position/orientation/elevation.
   Its result is either a typed immutable site plan or an explicit refusal/wait:
   building transform, exact cut/fill/foundation/support works, intentional voids,
   yard grades, steps/ramps and street/port connections, plus survey dependencies.
   Uneven sites may retain terraces; flattening the entire settlement is not a rule.
   Unreasonable earthwork, unsupported geometry or unsafe access rejects a candidate.
4. Settlement layout owns plot selection/reservation and street composition. It
   compares feasible site plans and their preparation cost; it does not duplicate
   excavation geometry or adjust source blocks ad hoc.
5. Existing common physical-operation/materialization executors apply the admitted
   site plan. Genesis prepares it during world generation; future construction
   performs the same explicit terrain works through ordinary resource-backed jobs.
   No silently erasing player edits or replaying the whole building to repair one cell.
6. Shared admission/Foundry checks the composed terrain + building + connection
   geometry: support, intentional open space, body clearance, courtyard connectivity,
   entrances and actual permitted stairs/slopes. Runtime navigation consumes the
   resulting geometry and handles doors; it does not invent the site fit.

Keep source grade/level selection separate from this mechanism. Fixing placement
alone does not change level2 buildings into the gallery's maximum-level variants.

## Implementation order if adopted

First close the already agreed player-break admission/result and reconciliation
boundary, including unchanged blocks and restored/replaced cells. Then correct
common service-clearance candidate selection, preserving UNKNOWN as a real wait
while allowing other proven reachable candidates. Verify affected hungry residents
reach confirmed consumption, with no manual state reset or free food.

Implement site semantics and the common placement plan next. Validate representative
flat, sloped and intentionally lowered/cellar sites with two competing neighbouring
plots, blocked connections and player edits. Review actual street-level frames for
hall/farm/depot/textile rather than accepting only block-count equality or an
entrance-only audit. No implementation/deployment claim is made in this document.
