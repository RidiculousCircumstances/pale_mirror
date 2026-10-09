# Shared block-resource extraction

Accepted2026-10-09. Implemented in the ledger-named Java checkout; no subagents.
The boundary is reusable for resource-producing block work. The current consumer
is farming; this does not introduce a quarry, seed economy or new player action.

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
