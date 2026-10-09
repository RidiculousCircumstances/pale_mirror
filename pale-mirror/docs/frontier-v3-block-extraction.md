# Shared block-resource extraction

Accepted2026-10-09. Implemented in the ledger-named Java checkout; no subagents.
The boundary is reusable for resource-producing block work. The original R78
consumer was farming. The accepted finite-quarry cut now adopts the same port;
its connected implementation and acceptance limits are recorded below.

## Finite-worksite adoption — 2026-10-10, connected native evidence

The opt-in `frontier-v3-quarry-graybox-r1` content declares six exterior deposits,
finite source cells, ordinary ramps, real starter tools and site containers.
`ExtractionSiteState` owns authored geometry, current source/depletion revisions
and exact work. It does not own another inventory, body or pathfinding system.

- Mining admission/labour select a source and retain its worker/tool/operation.
  UAE owns current execution and interruption. Shared navigation moves the body.
- `BlockExtractionPort` retains native loot once. The mining effect owner pairs
  source and hand observations before the existing resource ledger accepts output.
  A causal block-half witness in the common BLOCKS journal survives split recovery.
- Chunk-sized `ExtractionRegion` boundaries use the existing replica-custody
  registry. Projection PREPARING is not proof of a complete physical observation.
  One cell's mining receipt leaves the successor region PREPARING until every
  current cell is checked. Normal departure releases only exact resolved custody.
- Shared worksite projection/handoff dispatch to registered family owners through
  immutable point/chunk indices; they do not inspect concrete mining jobs.
  Declared infrastructure has bounded current geometry observations. External
  removals/replacements are acknowledged, not overwritten or treated as output.
- Site storage delegates actual chest/inventory effects to the existing container
  authority. Service arbitration and HOT waiting use the closed service-point
  registry, not an assumption that every container is a settlement depot.
- A separate eligible logistics worker requests INTERNAL_SHIPMENT through the
  existing shipment/carrying/UAE protocol. Receipt keeps the economic owner and
  does not invent a sale. Public cobblestone trade uses the ordinary goods catalog.

Canonical mining/hauling and focused source/geometry/split-witness recovery checks
pass. Native run8 reached HOT extraction, natural COLD departure and a separate
carrier's accepted64-cobblestone home delivery at instant17827. Run10's retained
world passed exact COLD departure and a graceful restart; its initial delivery
wait ended while carriers were interrupted by normal meals, not at a terminal
delivery. The checked-in existing-world follow-up completed home delivery at
instant40020, returned to the same depleted quarry and observed AIR at the first
extracted cell. The reviewed frame shows the excavated pit, remaining stone,
ramp and sheltered chest. This is joined same-world evidence, not a claim that
run10's original aggregate passed or every promised intervention was filmed.
Affected integration checks cover1258 Frontier cases (11 stale registry/profile/
descriptor fixtures corrected in a subsequent66-case green run),689 native unit
cases (one existing skip),16 native field-turns cases, guardrails and packaged JAR.
Ordinary catalog matching proves a funded partial stone contract, not yet a
native importer delivery. Live remains R80 until the verified R81 deployment.
Unobserved abrupt-recovery custody remains fenced until positive recovery evidence;
absence of a loaded chunk must never manufacture confirmation or release.
Tool manufacture/wear, subterranean planning, extra minerals and construction
consumption are not promised by this cut. Old R78 evidence below remains scoped
to its original farming integration and isolated adapter checks.

## Responsibilities and dependency direction

- The work family chooses an exact source block, tool/loot profile and intended
  successor. It owns target selection, work time, interruption and continuation.
- Pure `BlockExtraction` records a complete prepared effect: operation identity,
  exact actor execution, target, before/after states and bounded resolved outputs.
  `BlockExtractionPort` is the dependency-inverted physical boundary.
- `FrontierV3MinecraftBlockExtraction` implements that port. It reads Minecraft
  loot tables using actual block, worker and tool context, including NeoForge
  loot modifiers; validates tool suitability and captured body/execution authority;
  and applies only the declared block mutation in an already loaded chunk.
  It knows no farm, bakery, settlement, route or resource account.
- The consumer's existing durable witness retains the preparation before any
  effect. Field ownership remains in the common journaled field store; there is
  no extraction-specific persistence service or second inventory ledger.
- Existing `FungibleResourceLedger` accounts confirmed output quantities in exact
  actor custody. It handles resource kinds and quantities, not farming policy.
  Output allocation, inventory presentation and delivery belong to their current
  owners, not the block adapter. A native block mutation alone never issues stock.
- Field biology/sowing remains separate. Removing a crop is not permission for
  the general extraction adapter to till, replant, grow or reset a field.

Dependency chain: work-family policy -> pure preparation/port <- Minecraft
adapter; durable family witness fences effects; accepted receipts -> existing
resource accounting. Actor/body actuation comes from UAE's existing owner.

## Effect and recovery protocol

1. Validate the exact current source, actual tool and execution/body authority.
   Prepare native loot without changing blocks or inventory.
2. Retain the complete preparation in the consumer witness and acknowledge its
   existing durable disk fence.
3. Apply the declared source-to-successor change with the same captured command
   authority. Recheck tool/source before the first destructive write.
4. Observe and retain the physical result. A present successor is only an observed
   postcondition, not proof of who changed the block and not permission to mint
   resources. The family's exact paired witness/receipt still decides acceptance.
5. For farming, retain/recover the existing separate replant and actor-hand
   effects, then accept their exact canonical receipt. Recovery reads prepared
   output; it neither rerolls loot nor replays completed resource accounting.

Changed source/tool, missing authority or unloaded terrain cannot authorize a
replacement write. Invalid preparation, unsupported loot/components or malformed
recovery evidence fail explicitly; they never fall back to fabricated output.
The field witness validates both cause and exact FIELD_HARVEST execution.
Existing crop-intervention, hand, custody, epoch and retirement checks remain.

## HOT/COLD rule and current limits

A versioned definition explicitly declares the COLD output from authoritative
known block state. HOT evaluates its declared Minecraft loot table and requires
exact agreement with that rule. COLD never loads terrain or calls Minecraft.
This first version supports deterministic, positive, component-free fungible
outputs on stateless source/successor blocks: at most32 distinct kinds, at most64
units per kind. Block entities are explicitly rejected at prepare/apply; their
contents or other state cannot be destroyed through an unretained block write.
It does not silently
approximate arbitrary random loot, enchantment-dependent yields or tile inventory.
Such profiles need a deliberate shared deterministic rule/extended destination
before being enabled. An output can be accounted in a hand or personal pocket
through existing actor-slot bindings; the family decides the destination.

Farming declares `pale_mirror:grain_harvest_v1`: actual wheat age7 -> air,
empty main hand, one wheat via a PM-authored standard block loot table. Its
current replant remains separate and does not consume seeds. This preserves
current production balance; it is not a claim of complete Vanilla wheat/seed
economy or player-break equivalence. Players retain ordinary Minecraft harvesting.
Tool wear, experience and player-only break hooks are not introduced by this
bare-hand farming change; a future mining consumer must define those effects
and retain them at its equipment/recovery boundary.

Native stone coverage uses the same adapter with an iron pickaxe and Minecraft's
actual `minecraft:blocks/stone` table, without any stone branch in production
adapter/accounting code. That isolated check is not an in-game quarry feature.

## Integration and persistence

`ResourceSiteHarvestProcess` derives exact quantity from the family definition
in both HOT and COLD, not from cursor delta. NeoForge field work prepares through
the common port and stores the resolved plan in its paired crop/hand witness.
Field scene actuation is shared with movement; no duplicate authority policy.
Native field witness format9, field store format17; other schemas unchanged.
Old disposable worlds are not migrated. Deployment requires a fresh test world.

## Verification

Affected domain and native witness/codec tests, real Minecraft adapter GameTest,
architecture/guardrails and packaged-JAR gates are the technical checks.
Tests retain actual recovery and source/tool/authority negatives. No speculative
quarry, seed economy, UI or whole-world visual acceptance claim follows from them.
The server remains unchanged until an explicitly authorized deployment.
