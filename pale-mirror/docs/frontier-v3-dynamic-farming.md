# Dynamic market-responsive farming

Status: accepted implementation order, 2026-10-18. Main alone implements in
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`. Not acceptance evidence.

## Product contract

Settlements adjust future sowing to domestic needs, executable purchases and
conservative expected exports. Existing plants survive a reduction; prepared
land remains reserve. Restore reserve before clearing adjacent land or admitting
a separate nearby field. Multiple arbitrary-shaped fields share the settlement's
existing agriculture workforce, navigation, resource, activity and work systems.
Zero sowing is allowed only when stock and confirmed supply cover consumption,
reserve and the lead time to restart local production. Quotes are not supply.

No irrigation in this cut: water is neither required nor produced and does not
alter managed crop growth. Fertility and growth period remain territory-owned.
Seeds cost nothing initially, but sowing is a separate timed physical operation
with an explicit empty input requirement. Ordinary unmanaged player farms keep
Minecraft behaviour. No new crop, metal, seasons, roads, earthworks or general
construction feature. Fresh test world; no legacy-world migration.

## Responsibility and implementation sequence

1. Canonical resource-site registry owns explicit identity, settlement, servicing
   facility, kind and current geometry. Bootstrap creates initial declarations,
   never supplies missing runtime identity. Preserve stable CellIds on change.
2. Separate prepared territory, sowing intent, current plant biology and pending
   work. Economic contraction disables future sowing; it does not erase cells,
   crops, resource history or already possibly applied effects.
3. Agriculture owns independent TILL, SOW and HARVEST operations. Harvest leaves
   bare farmland; sowing requires current permission and creates a new plant.
   Labour, goal navigation, body authority, claims, interruptions, carry and
   physical receipts remain with their existing shared owners. No whole-field
   prepare/harvest barrier. Growth never waits for delivery.
4. Market owns bounded demand observations, separate from price samples. Quotes
   of the same buyer across warehouses/suppliers are not additive demand; reading
   a quote again is not a sale. Unknown routing is not lack of market demand.
5. Economy evaluates domestic and export production plus executable imports,
   pricing labour, storage, logistics and bakery/fuel constraints. Conditional
   revenue is not spendable money. Capacity options, admitted plans and dated
   expected output are distinct. Do not count unchosen land as promised wheat.
6. Agronomy maps selected output to cells using known geometry, fertility and
   shared spatial exclusions. Prefer planted -> reserve -> adjacent -> new field.
   No block writes, worker admission or stock ledger in either planner.
7. Integrate supply/reserve forecasts, diagnostics, persistence, physical indexes,
   current-state projection and recovery; retire active fixed-bootstrap and
   automatic harvest/replant bypasses. Commit and deploy only verified code.

## Initial tuning and operating limits

Ruleset owns two complete simulation days of persistent export evidence before
expansion, a 10-percent area deadband (minimum one cell), and at most64 newly
ordered sowing cells per decision, counting outstanding preparation. Domestic
food-risk reconsideration bypasses export persistence delay. Search within64
blocks of the farm, at most4fields/1024cells per settlement; these graybox limits
do not restrict the shape contract. Search is bounded/incremental with no forced
chunks or per-tick global scans. Supply forecasts include travel, preparation,
sowing, growth and harvesting. Shared labour capacity is not duplicated per field.

## Interference and physical truth

Full storage defers useful work, never awards a crop receipt. Player crop removal
produces no settlement yield; obstructions and removed soil exclude exact cells
without freezing other work. No automatic rebuilding of foreign blocks or holes.
Plan replacement finishes only an already prepared atomic effect, then rechecks
permission. HOT and COLD share plant/work semantics and resource accounting;
HOT workers perform visible timed work, ingress projects current state instead
of replaying history. Exact pending effects and identities survive restart.

## Acceptance

Static trace first, then focused owner/recovery tests and one relevant native
story. Verify sustained funded demand -> expansion -> real harvest/trade;
temporary/unfunded/duplicated demand -> no false expansion; decline -> reduced
next sowing without plant destruction; recovery -> reserve reuse; safe zero
sowing and restart; multiple fields/workers; no-water HOT/COLD parity; player
damage/obstruction, full storage, food interruptions and restart conservation.
Reuse the current pilot, real engine and accelerated time; no invented outcomes
or second test harness. Inspect runtime planning cost, queue lag and chunk
presentation. Display target output, planted/prepared/reserve areas, expected
yield/time and actual decision/hold reason. A finite soak is not proof of eternal
economic equilibrium.

## Implementation boundaries (R192)

- `MarketDemandHistory` retains bounded continuous funded interest, not invented
  sales. `SettlementEconomicPlanning` applies persistence before export expansion;
  `ProductionChainAllocation` dates upstream output and shares labour and inputs.
- `CultivationPlanning` composes economic requirements, restart coverage and land
  options. The pure `CultivationAreaPlanner` chooses sowing area; it cannot write
  blocks, award resources or assign residents.
- `FieldDevelopmentLand` uses common reachable-surface search and
  `DevelopmentGroundKnowledge`/`LandUseExclusions`. `FieldLandAdmission` alone
  declares surveyed land or extends an idle field while preserving existing IDs.
  The content-declared `TerrainSurfacePalette` is shared by COLD ground facts
  and the native biome surface generator. Rocky land cannot be silently surveyed
  as grass; clearing stone/coarse soil remains outside this no-earthworks cut.
- `ResourceSiteState` owns declarations; `ResourceFieldCycle` owns actual biology
  and a separately versioned `FieldSowingPlan`. A declaration never grants a
  prepared field or a crop. Pending physical operations retain exact authority
  across a plan change; subsequent work checks the new permission.
- Existing field workers execute independently timed `TILL`, `SOW`, `HARVEST`
  through shared work, navigation, custody and UAE. Multiple fields have distinct
  strategic slots but share the settlement workforce. Unavailable sites do not
  reserve workers. The existing full-storage gate also defers soil preparation.
- `FieldWorkForecast` supplies agronomic costs; the independent
  `RenewableWorkForecast` schedules shared labour and plant growth.
  `DatedStockCoverage` rejects a restart plan with an uncovered interval even if
  later deliveries would make its final stock positive.
- Native witnesses distinguish authored and surveyed initial ground. Closing an
  older exact physical projection does not make it the newest plant state; the
  latest canonical state is projected separately. Player changes are still
  classified before writes. Context cards and diagnostics expose the decision.
  External-cell observations retain two distinct predecessors: canonical biology
  and the last committed physical condition. A delayed projection does not make
  a real obstruction a global invariant failure or authorize overwriting it.
- Navigation observations wait for initial structural projection, including
  contextual shape dependencies. A loaded but unfinished replica's air is not
  evidence that a declared support was removed. The request remains queued;
  later real player changes remain authoritative. This fixes the observed first
  HOT visit abort at the settlement11 service floor `(134,64,355)`.

Deployment requires ruleset `frontier-v3-quarry-graybox-r21`, state schema294 and
a fresh disposable world. Live acceptance receipts belong in `CONTINUITY.md`;
this description is not a claim that every market outcome has been observed.
