# Authored buildings — first Medieval Spruce bakery

Implementation: `18a52ee9b` in the Pale Mirror implementation worktree. Schema336,
fresh world. This vertical replaces the active bakery only. Other functional
buildings deliberately retain explicitly selected graybox templates.

## Ownership and delivered behavior

- `model/buildings` owns immutable content, explicit style/function/template/hash
  selection, local rooms/zones/passages/workplaces, and the sole placement transform.
  The populated style is Medieval Spruce; more styles are catalogue data.
- The native authoring compiler resolves actual Minecraft states/repair items,
  verifies all four rotations against native `BlockState.rotate`, matches native
  structure NBT to the neutral projection, and compiles the pristine collision
  certificate. NBT targets Minecraft1.21.1/DataVersion3955, with no entities,
  inventories or foreign block-entity payloads.
- Existing genesis and structural custody/aftermath own physical placement,
  destruction and recovery. Machine sockets remain separately owned production
  equipment. Authored appearance does not restore a broken machine or player edit.
- The registered common spatial composition supplies sparse building blocks and
  declared air volumes to existing 3D navigation. Native observations, physical
  deltas and pending-write fences override pristine knowledge. Public/staff
  passages, true non-flat floors and actor profiles use that same navigator.
- Existing station work, UAE, service access and storage remain gameplay owners.
  Storage zones project category filters/priority only when admitting a storage.
  Circulation annotations project no-parking/access constraints, never collision
  exceptions. A building is not a new inventory, actor controller or pathfinder.
- Operator `building export/set/point/validate/overlay` shares one strict bounded
  format. Drafts live under `debug/pale-mirror/buildings/`; validation never
  activates or pastes them. Vanilla debug markers are player-local, bounded and
  expire. See implementation `tools/buildings/README.md`.

Pinned MineColonies source and explicit vanilla adaptations are recorded in the
packaged asset's `bakery.provenance.json` and the asset provenance document. This
is existing-policy **private test content**; public redistribution permission is
not established. No MineColonies/Structurize/Domum runtime is introduced.

## Connected defects corrected

The larger real building exposed an actual group route loop: joining an approach
to an old route tail could revisit a departure before the formation checkpoint,
so the guide repeatedly selected the same arc while the donkey waited. The common
route join now uses the existing path-composition loop eraser before prefix
selection. Both real animal transport continuations pass; no cohesion bypass.

Coordinate-heavy immutable plans now use the existing regional map. Sparse building
knowledge does not allocate an entry per air cell. Placement projections have their
immutable structure's weak lifetime: a JVM histogram previously retained576096
fabric cells/816 old projections, later8473/12. The full NeoForge unit run then
completed920tests without the previous512MiB heap exhaustion. No test heap increase.

The retained 2D hive consumer pins the public floor but still validates current
missing support; it cannot reinterpret a bakery roof as its public route datum.
This is a compatibility consumer correction, not an expansion of hive behavior.

## Verification

- Guardrails, packaged-JAR verification and build pass. Relevant collision,
  observation/pending precedence, selection/transform and full entrance-to-workplace
  paths pass. The full Frontier run had2328cases, two stale expectations subsequently
  corrected and checked; focused219cases and the actual transport continuation
  checks cover the connected changes. The full NeoForge run had920cases/four stale
  fixture failures; those fixtures were corrected and the four affected classes
  rerun successfully. Retired enum-only material fixtures now use their own tagged
  wire identity. Existing resident-card tests use the current combat/skill rows.
- Native `building-authoring`:1/1. Actual NBT equality, native rotations,106 known
  registry shapes, both entrances/non-flat work approach and installation clearance.
- Native `local-navigation`:46/46. The content registration test drains the existing
  bounded producer instead of assuming a106-state palette fits one32-state turn.
- Checked-in `disposable-authored-bakery-production-restart.json`: PASS, native
  player inventory/menu packets,63wheat →21bread, explicit pause, graceful restart,
  HOT→COLD→HOT, resume and confirmed physical withdrawal. It uses the current r31
  generator/profile with no fabricated outcomes. The old flat fixture did not run
  current genesis and was unsuitable. Fast-forward waits for actual body-release
  queue completion, preserving production admission rules.
- Receipt: IMPL `build/buildings-native/production-restart.json`, native run
  `0f795430-5e44-4691-9b7d-ae451d7fdfe8`, persistent outer session
  `dcb21d60-d71a-4bbe-9389-16294b40d5ef`. Four linked frames were opened and reviewed:
  exterior, operating menu, recovered explicit pause and21confirmed bread.
  This isolated carrier is LITE, not a full-pack or autonomous-worker claim.
- Native authoring command acknowledged the actual registry/navigation validation
  through authenticated RCON. Draft geometry matches the packaged certificate.

Deployment/full-pack closure is recorded below. No human player state was used or mutated.

## Integration follow-up

Final code correction `0d7138e1c`: absolute placement anchors used the local
annotation parser (±256), so actual distant settlements rejected `overlay`/`point`.
World and local coordinate decoding are now explicit and independently bounded;
the actual−1496,64,−1346 counterexample is retained in the existing catalogue test.
The test client's clean→player capture transition separately now restores its
original HUD setting. This presentation error was not the cause of the command
failure. A native RCON probe exposed the parser rejection directly; no custom
network protocol or second debug renderer was introduced.

The live preflight was corrected to inspect an autonomous completed order, rather
than assume that a normally running station is empty. On the fresh live r31 world,
Finn Wood (`resident:1-15`) autonomously processed63wheat into21bread and delivered
it: exact task `task:settlement-1-settlement_produce_bread-1` reachedCOMPLETED.
Full-pack client `dfc6ea1e-47d9-424d-b4ff-ca4e5c8f8e86` confirmed the actual building,
bench access and current depot receipt with zero retained inventory/scene conflicts.
Its exterior/interior/menu frames were opened and reviewed. Its first marker
capture did not prove the overlay; the parser defect above was repaired before
final acceptance. The long player recipe/restart carrier remains dependency-valid;
only developer coordinate parsing, local capture presentation and the live
inspection declaration changed after it. The full920-case NeoForge gate passed
at18a52ee9b; the affected catalogue guard/coordinate checks and package gate passed
for0d7138e1c. Unchanged simulation/physical coverage is reused, not described as rerun.

## Delivered runtime

- Final source0d7138e1c450fca782edfb0ce997bd8ffb1838f3, clean detached release.
- Installed SHA512 `205ce351b63fd9433eda792f6a59063420b4b4990619e6056e9244061579efe91218c2b72de058ba2328c2abb3ea12da90d56f2269913af4ef283a6c5e4cb6aa`.
- `far-frontier-v3-live.service`, wrapper3253272/nativeJava22PID3253303,
  start1793861191. Fresh task world
  `authored-buildings-schema336-seed139-20261105`, seed139/r31. Initial deployment
  replaced the old335world; the developer-coordinate follow-up retained the new
  compatible336world after a confirmed native durable save (pending0).
- Final exact-code full-pack receipt IMPL `build/buildings-native/final-full-pack.json`,
  run `ea5a0187-4b6c-46ed-8976-33fb66fe3fd3`: PASS. Three linked frames opened/reviewed.
  The overlay is now visibly rendered with labelled storage, transit, operator,
  equipment and maintenance markers; HUD restored. The ordinary production bench
  and current depot receipt are confirmed alongside the completed autonomous task.
- Actual native `building point ... workplace.oven.input -1491 64 -1348` returned
  `7,1,3`; subsequent native `building validate` passed. Packaged and native draft
  collision certificates agree. This is actual distant-world tool evidence, not
  only a pure coordinate example.
- Final read-only summary at29606: green, requiredConflicts0, inventoryConflicts0,
  sceneConflicts0, replicaCustodyDiagnostics0. Deployment verifier passed after
  client cleanup. No continuous-solvency/performance claim is inferred from that.
- Task graphical client/privateXvfb exited, no task operators remain,0players online.
  Human `rd` state and unrelated operators were preserved. IMPL and RELEASE clean;
  PACK trackedclean with only the pre-existing `.f0v-baseline/` evidence directory.
  GOV unrelated WIP preserved; only task ledger/docs and exact architecture/provenance
  additions staged. No push/public asset distribution.

The current artifact exposes no automatic door-opening gameplay: the initial asset
uses open doors and a staff entrance; current physical obstructions remain real.
Other buildings remain explicitly selected graybox content. Future Create equipment
has declared installation/maintenance space, not a second production implementation.
