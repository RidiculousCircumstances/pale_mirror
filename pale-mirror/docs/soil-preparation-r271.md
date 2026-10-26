# R271 — data-driven soil preparation

## Implemented ownership

`soil-preparation.properties` in the Frontier module declares a bounded immutable
catalog of soil identities/roles and preparation steps. Each step names its
predecessor, successor, work operation, effort multiplier, tool ability/grade/
wear and native tool-modification ability. These are mod definitions, loaded
once; their deterministic canonical text participates in every ruleset hash.
They are not editable instructions for live jobs. A definition change requires
a matching build and fresh ruleset state, never silent retuning of a saved world.

`SoilPreparationCatalog` owns definition validation and path lookup. Missing,
duplicate, ambiguous, cyclic and incomplete paths fail closed. Stable declared
wire tags replace per-codec soil switches. Soil values entering authoritative
cell state must be the exact registered definitions. Existing core constants
are nominal identities, not the list of all supported soils; adding a soil
using an existing supported action changes data, not worker branches.

`ResourceFieldCycle` owns the actual cell state/generation. One operation advances
one declared preparation step. Initial content is:

* grass -> farmland;
* dirt -> farmland;
* coarse dirt -> dirt -> farmland.

Planting is a separate operation. Intermediate dirt is retained through
interruption/recovery; no crop, harvested yield or finished farmland is invented.
`ResourceSiteHarvestLabour`, `FieldEquipment`, common WorkProgress and ToolUse
retain their owners. Every preparation step uses the configured operation,
effort and exact tool wear. `FieldWorkForecast` sums the complete path and shares
identical effort/tool assessments instead of repeating inventory scans per cell.

`FrontierV3SoilPreparation` is a read-only Minecraft/NeoForge provider. It previews
the declared block-only transformation through `getToolModifiedState(..., true)`
with the real item ability and compares its result with the declared successor.
Unknown blocks, changed predecessors, wrong tools and obstructed crop space do
not become write permission. `FrontierV3FieldWorkOperation` alone connects that
proof to the existing common durable Minecraft operation, tool effect and witness.
Native observation, foreign edits, projection, snapshot/WAL and field witness
recovery consume the same definitions. There is no new physical writer or COLD
process. New kinds of effects, including tool actions with extra drops, need an
appropriate effect provider; they are not silently treated as block-only steps.

Canonical schema307 and field-witness format11 explicitly reject old soil-tag
grammars. The old R267 world is preserved for diagnosis; use a fresh disposable
world. This is not a compatibility migration or proof that the economy is balanced.

## Discontinuous cultivable patches

The exposed coarse ground revealed a second reproducible limit: treating every
soil island as a separate field exhausted the four-worksite policy. A pure
repeat-admission probe stopped at112cells despite remaining reachable candidates.
A field is now a work area with an arbitrary explicit cell set, including gaps.
The existing reachable-area survey proves access across intervening walkable
land. Admission consumes an opaque survey bound to the exact immutable world,
declared settlement and source fields; a selected subset cannot add unsurveyed
cells, and foreign/stale evidence fails before mutation.

One admission extends an idle safe work area or declares one new work area for
its selected cells. Active jobs, effects and receipts still prevent changing
that area's geometry. Maximum fields, total cells, per-turn development and
search radius remain unchanged. No speculative route, coordinate-based owner
inference, soil teleportation or arbitrary rise of limits was introduced.

## Verification and limits

Affected Java checks include config-only extra soil/action data, missing and
ambiguous definitions, complete multi-step cell/codec recovery, actual rocky
land admission, repeated separated-patch development and stale/foreign survey
rejection, plus existing harvesting, cultivation, economy and persistence cases.
The focused native field-turn slice includes real Minecraft hoe transformations,
wrong-tool/changed-ground/obstruction rejection and intermediate witness recovery
with old-format rejection. Runtime/deployment evidence is retained under ignored
`build/soil-preparation-r271/` and canonical continuity.

These checks establish the preparation and admission mechanisms. They do not
establish indefinite economic equilibrium, maximal feasible field size under live
staffing, or player comprehension. Actual production/trade recovery remains a
separate observation, without free resources, forced prices or waived provisioning.
