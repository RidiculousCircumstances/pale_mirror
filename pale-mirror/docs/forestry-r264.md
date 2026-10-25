# R264: finite forestry and whole-tree extraction

Canonical scope: `PM-FORESTRY-20261025` in
`/home/rd/proj/pm-governance/pale-mirror/docs/work-orders/`.
New worlds use `frontier-v3-forestry-graybox-r28`, state schema306. Old test
worlds are intentionally rejected rather than migrated.

## Responsibilities

| Component | Owns | Uses |
| --- | --- | --- |
| `model.forestry` | Exact tree identity/generation, compact log offsets, current source loss and known territory | Regional immutable views and shared source replica protocol |
| `ForestryWork` and its process module | Exact worker/tool/tree, labour, remaining consequence prefix, cargo and destination | UAE, staffing, schedule/needs, WorkCatalog and shared navigation |
| Common block extraction | An admitted bounded compound effect, native loot and exact child consequences | Minecraft operation journal, source witnesses and ordinary tool definitions |
| Common inventory/storage | Title, physical holder, exact equipment, fungible lots, capacity and dynamic warehouse selection | Existing resource/container journals and service access |
| World generation/growth adapter | Actual generation provenance and compact chunk attachment | One graybox natural plan; actual successful vanilla TreeFeature log positions |
| Native forestry adapter | Observed worker, axe gesture, bounded physical effects and receipts | The existing body controller, movement, inventory and operation protocols |

There is no forestry navigator, inventory ledger, body controller, save queue or
second physical-operation journal. Mining now shares cargo orders, actor cargo
custody, storage selection, block loot and source-replica helpers with forestry.
Exact inventory departure broadcasts facts to explicitly declared owners;
new forestry ownership is not inferred from an item name, ID or runtime class.

## Initial content

Oak forests are unevenly authored around Clearwater (town7), Dawnbridge (town3)
and Hearthvale (town6): two agrarian towns and one mining town. Initial trees
are placed during chunk generation, not replayed as runtime planting. Native
sapling growth registers its actual successful log set, with a new tree ID.
Overlapping leaves never merge identities. Buildings' logs are not trees.

Each forest town requests up to two workers and a reserve of256 oak logs;
new work resumes below192. Actual stored/held output and the unsettled source
suffix are counted once. Already-earned cargo is delivered even when new work
is no longer requested. There is no wood consumption, automated wood trade or
planting AI in this cut. Storage categories remain explicit membership in the
existing generic filter, not a separate WOOD currency or balance.

The iron axe uses CHOP, ordinary individual equipment manufacture/repair,
three ingots per new axe, one per repair, and real station heat. Its durability
is250. Work uses ordinary resident modifiers and WorkCatalog labour; each
confirmed extracted log consumes one wear. The production world grants no
initial axes. The separately named `forestry-equipped` native fixture supplies
only two INITIAL axes to isolate felling/handoff; it does not prove autonomous
iron procurement or manufacture.

## Physical and background work

A worker takes a tool, approaches a supported trunk station, labours, applies
an exact bounded whole-tree consequence and continues to another tree or a
warehouse. The axe's gesture/sound follows actual labour. Child effects remove
logs top down; ordinary Minecraft loot determines supported native output.
No duplicate item entities accompany canonical cargo credit. Vanilla owns
subsequent leaf decay; neighbouring trees may keep some leaves alive.

COLD uses the same source, labour, tool wear, cargo and consequence prefix.
Returning HOT presents current depletion and actor position, without replaying
missed swings. Physical source confirmation first retains positive current
block observations and waits for their journal fence. Once HOT demand ends,
release no longer depends on recreating virtual generation evidence from an
already unloaded chunk. Mining uses this same proof retention contract.

Player removal withdraws only that source entitlement. It grants no production
credit and does not let old projection restore a same-looking player block.
Foreign external worksite records are dispatched by their retained explicit
owner family before an adapter resolves its own geometry.

Equipment orders validate storage policy before preparation. A previous cargo
warehouse that accepts ore but refuses a pick/axe cannot receive a native tool
return intent while the scheduler is selecting a compatible destination. This
fix follows the actual old-world `work:extraction-79` failure, not a hypothetical
fallback; observed resource proofs remain strict.

## Cost and limits

Log positions use packed four-byte offsets plus a palette. Natural authored
geometry is read lazily per region; only changed/admitted sources and active
jobs enter canonical forest state. No per-log block entities or ticking tree
objects are added. Candidate and chunk work are bounded and unchanged regional
images keep their identity. Existing journals, coalescing and asynchronous
fences own disk writes.

Small supported oak felling fits the common64-item cargo and bounded batch.
Larger trees remain accounted but are not admitted as an oversized felling.
Chunk metadata is explicitly capped at256 owned trees/links; unsupported
species, oversized footprints and exhausted metadata capacity are reported,
not guessed or force-loaded. Vanilla's supplied trunk placement set includes
the dirt under the root: the adapter selects actual LOGS before profile checks.
Depletion facts remain necessary source state; completed work records are
removed rather than kept as an ever-growing job history.

## Verification receipts

- `build/forestry-final-build.log`: full `guardrails check`, native build and
  packaged-JAR verification passed; subsequent connected fixes have focused
  affected checks. The earlier whole GameTest campaign passed406/408; its
  first-visibility issue was corrected and the affected9/9 slice passed.
  The remaining old hive combat calibration also fails on clean ef0bf8892;
  it is not a forestry acceptance result or an all-green global GameTest gate.
- `build/forestry-retained-source-proof.log`: source proof retention, dispatch,
  forestry lifecycle and manufacture/repair checks passed,48 tests total plus
  architecture/guardrails and package checks.
- `build/forestry-storage-admission-final.log`:103 affected Frontier checks and
  all864 native unit checks passed, plus guardrails/build/package verification.
  This includes reserve hysteresis, counting actual cargo plus unsettled output
  once, and rejection of an incompatible tool-return destination before intent.
- `build/forestry-native/growth-owner-fixed.json`: actual sapling/bonemeal,
  registered new source, player break, no restoration and replacement log.
- `build/forestry-native/handoff-post-power.json`: actual HOT felling,
  two independent workers, normal demand loss,4k COLD interval, graceful restart
  and HOT return. Stored/held wood5->38; each actor/tool/cargo survives, source
  authority released. The power-interrupted preceding run is not accepted.

These are private Xvfb/native-client receipts, not human/M3 acceptance. Initial
chunk/renderer warm-up peaks are retained in performance diagnostics; this
report makes no blanket80ms worst-tick guarantee. Final scoped gate, native
candidate receipts, clean release identity and deployment are recorded on
completion in the canonical continuity ledger.

## R265 connected closure

Two additional native findings were repaired before delivery:

- An unused prepared forest effect could outlive normal shutdown while its live
  attempt permission disappeared. The forestry shutdown hook now withdraws that
  exact intent through the existing shared protocol before closing its source.
  Native `external-reserve-shutdown.log` records cancellation at22:34:37 and
  subsequent resumed extraction. Attempted/ambiguous effects still cannot replay
  from matching preimages; no journal proof or recovery constraint was weakened.
- Nominal cell-center reach could admit a station whose actual off-center body
  was outside4.5blocks. Actual failing feet388.5976307/64/-31.4279835 at support
  388/63/-32 were4.504blocks from target385/64/-35. Shared BlockInteractionReach
  now includes the support's lateral extent plus the explicitly declared body's
  half-width. All shared candidates/validators use it; native hit radius stays
  4500mm. This fixes the contract rather than nudging/teleporting a woodcutter.

Forge inspection also reports the declared recipe capability: Toolsmith,
Smelter or Baker. A generic station job is no longer automatically labelled Baker.

`forestry-final-consumers.log` passed47 affected Frontier checks; all864 native
unit checks passed with zero failures/errors/skips, and the final build/package
checks passed. Guardrails and the focused ledger/confirmation checks also pass.
A broad all-Frontier attempt was intentionally stopped in the unchanged expensive
ExpeditionSupplyFlowTest; it is not recorded as a full-suite pass.

`build/forestry-native/external-reserve-final-continuation.json` passed15 ordinary
client actions in the retained disposable world. Both forest jobs closed, real
warehouse stock259, no retained conflicts; the actual open-chest frame shows the
wood and returned tools. This is explicitly a continuation of the retained
`external-reserve-final` attempt, whose first-cut wall-time bound was too short;
that earlier attempt is not a fresh full pass. The primary declared cut bound
now reflects the measured path plus labour. Neither simulation speed nor work
policy was changed to pass a deadline.

This carrier starts with190logs, two explicitly supplied axes and food. The
player's original64logs are shift-clicked normally into the chest: external
stock254 alone cannot meet target256. Actual felling earned five more, and those
five had to be deposited before both jobs/tools could close. It proves ordinary
external-stock response, partial delivery and reserve cutoff, not autonomous
0->256production or manufacture from a fixture. Separately the normal live R264
world manufactured two axes and reached167earned logs by tick81800, with125
already stored and42carried. No fixture supplied those axes or logs.

Final deployment uses fresh R265/r28/schema306 because old work may retain the
former unsafe stations. Exact clean source/artifact/world identities are in the
canonical continuity ledger. Private Xvfb frames were reviewed; no human/M3
acceptance or blanket80ms worst-tick guarantee is claimed. The full-pack first
forest visit previously took4.5seconds including its1second settle; that is one
observed visit, not a universal loading guarantee.
