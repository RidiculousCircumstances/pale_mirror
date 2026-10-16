# R172 — demand-derived resource reserves

Accepted scope: all active public-settlement commodities (food, ingredients,
fuel and extraction outputs). No new construction, wear, medicine or company
simulation is activated. Their future owners can supply the same typed demands.

## Responsibility and calculation

Nutrition supplies consumption and immediate shortage. Production owners supply
explicit recipe flows; replenishment owners supply timing estimates. A pure
resource-neutral reserve planner propagates demand through an acyclic recipe
graph and calculates coverage and uncertainty buffers. It neither creates jobs
nor reads concrete resident, bakery, harvest, mining or caravan stages.

Public production, mining priority, market quotes/pricing and protected stock
must use the same resulting targets. Fixed commodity quantities are not public
reserve authority. No consumer means zero speculative reserve. A commercial
opportunity is not a contract; existing bounded opportunity advice remains
separate from owned stock and reservations.

Each resource's buffer covers its consumption over replenishment lead time,
review interval and policy safety margin. Missing timing is visibly an estimate,
never a guaranteed arrival. Existing logistics receipts alone may fund timed
production. Recipe rates are propagated once; reserved production dependencies
and computed buffers must not produce duplicate replenishment orders. Resources
shared by recipes aggregate; alternatives must not all receive full demand.

The active content remains bread/wheat/coal/cobblestone. No stone consumption is
invented to keep quarries busy. Physical capacity, actual stock claims and money
remain admission constraints with their current owners. Forecasts never create
resources, credit, stock custody or delivery guarantees.

## Verification

Check increasing population/lead time, shared and alternative recipe inputs,
zero-consumer resources, commitment coverage and invalid/cyclic declarations.
Check active public production, mining, sales protection and pricing against the
same forecast; include codec/recovery and diagnostics. Source-first verification,
then focused integration; no repeated client campaign to prove arithmetic.

## Implemented selection and limits

Ruleset `frontier-v3-quarry-graybox-r14` enables dynamic advice; old ruleset
digests are unchanged and do not silently adopt it. The new disposable world
uses the new selection explicitly. No snapshot-format change is required.

The buffer is consumption × (replenishment lead + 50% lead safety + review
cadence), plus current nutrition shortage. The lower reorder point excludes
the review cadence; protected stock covers safety plus immediate shortage.
Recipe rates use exact rational arithmetic and round only final item counts.
Shared inputs aggregate before expansion; one selected ingredient/fuel
alternative supplies each recipe flow. Existing production dependencies and
buffer replenishment overlap, rather than add duplicate purchase quantities.

Family providers report a full crop cycle, a work-calendar processing estimate,
present extraction materials, and accepted complete caravan-trip lengths.
Transient machine occupancy or a baker eating does not remove long-run demand.
Destroyed stations remove their recipe demand, not residents' food demand.
Without a source estimate, the explicit ruleset initial estimate remains visible
as `INITIAL_ESTIMATE`; this is neither guaranteed supply nor geology depletion
prediction. Existing exact delivery forecasts alone can back timed production.
This iteration does not learn supplier reliability or predict undiscovered ore.

`reserves` in participant diagnostics exposes quantity, reorder point, protection,
coverage horizon, exact consumption rate, replenishment ticks and timing basis.
Initial-state example:39 residents yield40 bread,111 wheat,1 coal,0 cobblestone.
It is intentionally possible to hold less coal than the old fixed8: one coal
fuels20 bread and measured demand is relatively low. A larger population or
longer replenishment increases the buffer. No claim of guaranteed starvation-free
economy is made when production, transport, money or finite geology are insufficient.

## Delivery verification, 2026-10-16

Source commit `1a49ff83d768a3907689a69defcc31117dd028cf`,25files,493additions.
42 affected Frontier tests pass; NeoForge741cases,740passed/1skipped; guardrails,
module build and packaged-JAR verification pass.375 required native GameTests
pass. Final focused receipt `build/r172-final-economy.log` (2m53s), native receipt
`build/r172-integration.log`. The broad aggregate is NOT green: known historical
ExpeditionSupplyFlowTest line172 fails; the remaining long Frontier test executor
was stopped, not waived as passing. Its failure was already reproduced on the
prior clean baseline. No acceptance of the complete historical suite is claimed.

Clean detached artifact build: `/home/rd/proj/pm-release-r172.DmEI2n/source/pale-mirror`.
Build/package/guardrails pass14s; identical final module tests reused explicitly
with `-x :pale-mirror-neoforge:test`, not reported as a fresh second test run.
SHA512 `2e0f96f7801282957351e457941b713c30a21ae2b1a17959173dd21aa9d38f7cd468b1dd5608f58bd44e687e7915c2f46418b5c2b238d99482421a9b2a71f6fc`.

Deployed fresh `frontier-v3-reserves-r172-20261016`, explicit r14 JVM selector,
schema290. Previous world retained, no world deleted. Old JAR recoverably
archived by checksum installer. RCON graceful stop confirmed all dimensions
saved. Preflight/install/post-start verification pass; service
`far-frontier-v3-live.service`, PID3462632, start1792145785,
invocation0205f3a2da594d4da026951e2f4974a4, port25565.
Fresh tick1037: statusok,366residents/12settlements, required/scene/inventory
conflicts0. Tick251 readyqueue0/lag0, navigationwaiters0; no fast-forward.
These are startup/COLD checks, not prolonged HOT or economic-balance acceptance.
No test client launched; no client update required. No push requested/performed.

Live saved-state `build/r172-live-snapshot.bin` independently confirms dynamic
targets; extracted read-only receipt `live-reserves.log` in the release directory.
Known diagnostic limitation: `inspect process settlement:6` returns
`response_limit` because the existing full economic report exceeds its response
budget. Embedded reserve JSON exists but this large report is not a reliable
standalone viewing surface; a bounded/paginated economic report remains follow-up.
Source clean; pack tracked clean with existing `.f0v-baseline/` preserved.
Prior unrelated governance WIP retained. Temporary artifact HTTP process stopped.
