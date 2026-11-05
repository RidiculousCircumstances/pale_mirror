# Retained settlement layouts and Medieval Spruce rollout

Source candidate `c9f20719415b893a89c3cb261524f6005c411aa7` (implementation
`dae577e04`, diagnostics import `7e9f6e439`, integration fixes `1a9b2612c`,
regional treatment geometry `23e8b984c`, integration/ranking closure `c9f207194`).
Implementation checkout is named in CONTINUITY.md. Private supplied-reference
assets retain upstream ARR provenance; no public push is authorized.

## Ownership and future expansion

| Owner | Responsibility | Relationships |
| --- | --- | --- |
| Building content/catalog | Fabric, parcel, oriented entrances, machine mounts, service anchors, exact bed halves | One Medieval Spruce style; stable content references and native collision certificates |
| SettlementLayout | Plot/building identities, parcel bounds, public paving, extension fronts and revision | `extend` appends plots/paving, rejects replacement of occupied IDs; existing placements remain exact |
| SettlementLayoutPlanner | Bounded initial allocation from real parcels and ports | Three deterministic street schemes; not a live construction or economic planner |
| SettlementLayoutValidation / Format | Shared geometry/terrain/identity validation and bounded interchange | Persistence and developer tools consume the same plan, not a parallel placement format |
| SettlementBeds | Declared physical bed capacity and aftermath | Actual head/foot pairs; housing capacity counts houses, not a global allowance |
| Existing navigation/storage/execution | Worker journeys, physical custody and interactions | Receives declared facilities/contacts and exact unit profiles; no building-specific navigator |
| Developer authoring tools | Export/move/street/connect/validate/SVG/overlay/selected-building preview | Draft only; cannot paste into the live world or overwrite player changes |

Persistence schema339 stores exact manifests. Recovery restores them and does not
rerun the allocator. A future expansion/construction owner must publish an explicit
new revision through the existing physical-operation lifecycle; it must not regenerate
occupied plots from population. Automatic expansion, construction, bed assignment,
indoor home occupancy and sleep remain outside this pass.

## Content and physical integration

Hall, depot, infirmary, bakery, smeltery, forge, woodshop, farm and four house variants
use the pinned Medieval Spruce set. Houses expose1/2/3/5 beds, including upper floors.
Initial villages contain14..18 buildings and25..43 beds for21..35 residents at seed139.
Public stone-brick paving replaces the raised concrete strips. Fields/quarries use
shared exterior land proposals and do not occupy the buildings' private plots.

All11 new imports reproduce44 checked-in asset/provenance files exactly; pinned source
`a9b03edfbbd5359b148c85284ea038066d689866`, per-blueprint SHA256 in
`tools/buildings/settlement_sources.json`. Source blueprints stay ignored under build/.
All12 templates passed the native authoring asset gate after final adaptations.

## Connected defects resolved

- An obstacle-aware final formation could put a member behind the nominal slot.
  A member already at its own final station held the donkey indefinitely. Spacing
  arbitration now excludes settled peers; normal in-flight spacing/pace remain,
  and only the existing movement owner can confirm arrival or release cargo.
- The enclosing group profile incorrectly granted door operation when any member
  had it. Capabilities are intersected; a donkey receives no human door operation.
- Production/migration planned with the actor profile but validated/rejoined with
  a generic profile. The same exact actor profile now owns both sides, including doors.
- Depot and metal-store sockets retained old material/geometry-owner assumptions.
  Endpoint strategies expose their producer-declared supporting facility; metal
  storage declares it explicitly. Economic ownership does not imply geometry ownership.
- A directly available requested mineral tied the overburden of another source
  at equal urgency. Shared extraction selection now prefers direct fulfillment
  before equally urgent access preparation; no resource-specific ranking was added.
- Repeated whole-house map reconstruction for one socket query now uses the shared
  authored block index. A captured native server stack also identified quadratic
  immutable flat-set construction in treatment geometry; that query now reuses the
  existing regional index under exact immutable bootstrap identities.

Fixtures now use actual bed deficits, declared Hall staging, open streets, network-owned
road cells and native body shapes. Raw block absence is not a clearance oracle for
operable doors. Terminal/recovery, wrong-identity and physical-loss assertions remain.
The local legacy Hive parent-return fixture targets its nest-adjacent settlement;
cross-frontier Hive macro-waypoint navigation through other villages is not claimed.

## Evidence and delivery

- `build/settlement-layout-authoring.log`: all12 assets, native registry geometry.
- `build/settlement-layout-review/reimport.log`: exact44-file reproducibility.
- `build/settlement-layout-review/`: properties/SVG plans for all12 villages.
- `build/settlement-layout-focused-repair3.log`: ordinary food/storage/production,
  population and consolidated cargo round trips with in-flight recovery (both human
  and finite animal cargo). Its two obsolete Hive envelope assertions were subsequently
  corrected; that aggregate itself is retained as red, not reported green.
- Release `build/settlement-layout-release-final.log`: all443 Core GameTests passed
  at1a9b2612c. One non-deterministic test fault-injection fixture failed separately.
- Release `build/settlement-layout-release-patch-verification.log`: guardrails,
  treatment/overlap negatives, corrected admission fixture,6 affected native GameTests,
  build and packaged-JAR verification passed at23e8b984c.
- The unchanged legacy Visuals lanes attempted earlier are not green: their GameTest
  run lacks a test-only template and their separate natural-world genesis harness
  found0 eligible sites. No Visuals source/assets were changed. These failures are
  distinct from the active Core-owned v3 graybox layout and its graphical preflight.

Pure integration was completed by class partitions rather than repeating successful
expensive checks. The first partition retains254 complete Frontier classes/1470 tests
and all920 NeoForge tests; the remainder's XML is retained separately, with all13
failed classes subsequently corrected and focused receipts reporting zero failures.
`build/settlement-layout-review/integration-partition/` retains these reports.
`build/settlement-layout-final-fixture-repair*.log` and
`build/settlement-layout-extraction-ranking-final.log` retain the final class receipts.
Four fixed-grid/raised-apron tests were retired with that removed implementation;
current layout tests cover exact identity, extension, damaged beds, terrain rejection
and recovery. Other fixture updates preserve current lifecycle/negative assertions.

`build/settlement-layout-release-complete.log` passes final guardrails, the three
last-edited test classes, build and packaged-JAR validation at c9f207194.
`build/settlement-layout-extraction-native.log` in IMPL passes all4 affected native
extraction GameTests on the final production bytes. No live TPS/latency gain is
claimed from static representation/caching changes alone.

## Deployment identity

The checksum-pinned package is published and installed through PACK scripts.
Source c9f207194; Core SHA-512
`b69b909dba00df102bc534c92706d7e8f3b4d931355cf9dcce2a322f85d5ac2b8083130730675ebe042895c939ca375e40043fcb39983fa015a11c227730ae04`.
Visuals unchanged SHA-512
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
Selected runtime /home/rd/far-frontier-server, new disposable world
`settlement-layout-schema339-seed139-20261105`, seed139. Old world stopped normally
with all-dimensions save. Preflight, install and fresh ready/no-quarantine receipts
are in RELEASE `build/settlement-layout-deploy-*.log`.
Full-pack layout scenario passed with Create/GeckoLib/Visuals/Supplementaries runtime
inventory. Its four actual frames were inspected: overview shows a coherent Spruce
village/flush paving; house fabric is present but that first exterior camera was
too near a corner, so the recovery camera was moved outside the parcel; the ordinary
spectator menu opens the native depot inventory. The initial camera trial failed
because the scenario added an extra cell to an already body-converted route anchor;
this was a harness coordinate error, not delayed terrain. No product timeout or
arrival predicate was relaxed. Native scenario corrections are source f603f3391;
production/package remain exact c9f207194 (identical functional code/assets).

First graphical receipt: RELEASE `build/settlement-layout-review/live-layout.json`,
run `2c87b88b-b188-44b6-b81c-80b77bbba33e`; four linked PNGs in that manifest.
No scene/inventory conflicts at terminal snapshot. This is a bounded layout/menu
flow, not complete live economy or work-progress acceptance.

Graceful restart saved all dimensions. Post-restart developer export/validate passes;
original/recovered `settlement.properties` SHA-256 both
`2e9f6e580eb3a3bb5e4296309bf47c6698cad776a3b385ba348471b970c2be79`.
Layout is byte-identical including all retained plot positions, not merely the same
revision or bed count. Restart verifier passes; wrapper PID2757086, start22:41:04+05,
seed139/schema339. Human rd connected before the authorized restart; no account
position/items were edited. Graphical recovery scenario PASS, run190a8727-4e09-4be7-a681-69508aa2b3db,
RELEASE `build/settlement-layout-review/live-layout-recovery.json`. Its three real
frames were opened and reviewed. The improved street-eye frame shows several complete
Spruce facades and rooflines with ground-level paving; no suspended/buried bases are
visible. Native depot menu opens after recovery; terminal inventory/scene conflicts0.
This private automated display is not human M3 acceptance.

`build/settlement-layout-review/delivery.json` joins exact production/scenario identities,
world, reviewed frames, restart and claim limits. IMPL final f603f3391 is clean;
RELEASE remains clean detached c9f207194, the deployed production ref. The later
commits change only checked-in native scenario cameras/configuration. PACK tracked
tree remains888ed90d5 unchanged; ignored checksum-pinned artifacts were atomically
published and installed. Unrelated `.f0v-baseline/` remains. Test client disconnected
and its private Xvfb was closed after each pass. The server remains running.
