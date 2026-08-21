# Continuity Ledger

## Goal (success criteria)
- Close Pale Mirror 0.3 as a player-legible Living Frontier: an unbriefed player discovers the mine-supply-defence crisis, chooses a real response, sees its physical aftermath and understands the next causal story.
- Finish the current 0.3 combat/materialization correction without regressing deterministic authored settlements, Mine17, baseline freight, evacuation, persistence or multi-audience authority.
- Define 0.4 as an autonomous reactive regional economy rather than a scripted quest chain: mining and agrarian settlements continue to trade, adapt, develop or decline without requiring the player.

## Constraints/Assumptions
- Pale Mirror is the primary project under `/home/rd/proj/minecraft`; its independent repository is `pale-mirror/`, while the outer repository owns pack, installer, hosted-artifact and deployment files.
- Root `AGENTS.md` requires this ledger and `pale-mirror/AGENTS.md` to be read at the start of every workspace turn.
- Java 21 bytecode/toolchain; Minecraft 1.21.1; NeoForge 21.1.248. The private server runs Java 22; clients and artifacts remain Java 21 compatible.
- `pale-mirror-domain` remains pure Java and owns canonical simulation; Minecraft, adapters and visuals are observed/materialized representations.
- Core schema v41 and Visuals schema v40 intentionally reject older worlds. The test server and worlds are disposable; verified incompatible cuts reset them without backup unless the user requests otherwise.
- Private Integrated Villages/ARR assets may be copied and remixed with origin/version/hash provenance; public redistribution remains out of scope pending a licence audit.
- Crimson 1.4.3.1 is the default infection source; Spore is optional/test-only; Millénaire is absent from the active pack.
- Historical detail preceding this compact ledger is archived at `docs/archive/CONTINUITY_2026-08-21_pre_protocol.md`.

## Key decisions
- The world plays itself: settlement simulation creates production, demand, trade, rationing, investment, migration, recovery and decline; Narrator selects legible stories but never creates or freezes those processes.
- 0.4 introduces FOOD and an agrarian settlement alongside the existing IRON economy. Local infection remains a stressor; global territorial infection economy is a later milestone, followed by organized military settlements/expeditions.
- Economic resolution uses deterministic snapshot -> intentions -> market/route clearing -> simultaneous commit -> consequences/events. Iteration order must not change outcomes.
- Inter-settlement money uses one mutual-credit accounting unit: every purchase creates equal buyer debt and seller credit, the network net balance remains zero, and bounded credit limits prevent unlimited imports.
- Currency does not replace physical logistics, safety reserves or the resolver. Prices choose among feasible trades; route capacity, stock, production and survival policy remain authoritative constraints.
- Player economic identity is a StoryAudience account. Earning credits creates a settlement obligation and spending transfers that claim; initial negative player credit, exact price formation and credit reputation remain unapproved design details.
- Region, economy, facilities, routes, population and physical incident outcome are global. Knowledge, access and scenario response are private per StoryAudience; physical help is shared and the first canonical outcome closes linked scenarios once.
- Authored fresh-world geometry comes from deterministic immutable chunk-local plans. Post-gen change uses persisted jobs, never force-loads prospective chunks and never overwrites unknown player changes by default.
- `TOWNSHIP` is the only materialized settlement stage in 0.3. The 12/24/72 compositions and reserved expansion parcels are validated plans, not active growth transitions.
- Settlement/mine structures use curated NBT with semantic entrances, bounded terrain integration, connected streets/adits, typed parcels and Foundry fail-closed validation; no generic-box fallback or global terrace is allowed.
- Baseline rail is a persisted dry-preferring route graph with loaded-chunk materialization, bridge limits and topology-based health; Red Valley/Create capability remains blocked until physically validated.
- Population movement is a persisted off-screen `WorldJourney`; nearby residents materialize under stable identity leases. Shelter selection prefers the player choice and falls back only after the fair grace window.
- Harvester creature authoring is contour-first: the pinned base-reference pixels are the sole likeness authority, the primary solid projection must follow their envelope through deliberately varied anatomical cuboids, and dense pixel/high-poly voxel tracing is forbidden. A generated reference/model contour overlay plus independent visual review is mandatory; automation never assigns likeness.

## State

### Done
- Authored multi-climate settlements, Mine17/Red Valley campuses, terrain-led streets/perimeters, vanilla baseline rail, residents, shelter/evacuation, damage/reconstruction and Foundry P0-P1 are implemented on fresh-world schemas.
- Canonical state, commands, persistence, bounded history/jobs, materialization ownership, route allocation, multi-audience authority and restart/package/client verification foundations are implemented.
- Worldgen was moved to deterministic precompiled chunk-local slices with bounded parallel planning; DH is client/cache-only by default and runtime work is budgeted.
- Resource-only Harvester Blockbench sources, reviewed runtime geometry, animations, textures and reproducible asset/audit tooling are versioned without registering unfinished entities in gameplay.
- Harvester review briefs now pin normalized subject bounds, alignment and contour landmarks for all three creatures; the visual audit captures a clipped solid viewport, builds a deterministic three-panel contour comparison and fails finalization without an explicit contour decision. Three synthetic overlay tests run in `guardrails`.
- The workspace now contains the independent Pale Mirror repository beneath the root pack repository; paths, publishing defaults and mandatory governance routing were updated without losing dirty work.
- The `stream_miner` continuity protocol is enforced: every turn reads the ledger, the previous ledger is archived, the active brief is compact, and Python contract tests plus Gradle guardrails validate structure, order, size and archive references.

### Now
- Commit `dde35f0` fixes shared physical encounter activation, deterministic Crimson/Spore actor spawning, widened Mine17 portal clearance and evacuation/camp reconciliation; commit `7ae3b3b` adds the authored Harvester source-asset cut.
- This exact candidate passed 166/166 JUnit, 52/52 Core GameTests, 31/31 Visuals GameTests, Core packaged-JAR verification/restart, Crimson and Spore integration restart harnesses, and the Visuals packaged-JAR two-start harness on 2026-08-21.
- Clean test seed `9031746258841137206` compiled three regions with zero Foundry blockers/errors. Settlements: `312,67,2824`, `5816,70,-14856`, `14312,67,296`; Mine17 portals: `504,69,2728`, `5656,66,-14888`, `14472,74,168`.
- A real Blockbench Scythe audit at `build/harvester-visual-audits/20260821T181005Z-scythe_stalker-contour-contract-v01` proves the overlay pipeline. It exposes a current 2.260817 model aspect ratio against the pinned 1.651625 subject box; the asset remains unaccepted pending contour-led correction.

### Next
- Verify Heart plus living actors by re-entering an informed Mine17 footprint, then exercise two independent audiences, evacuation residents, retained Create machinery, baseline freight, positive aftermath and delayed causal continuation.
- Complete the external 0.3 player-comprehension gates or record remaining P0 blockers before declaring release completion.
- Convert the accepted 0.4 autonomous-economy direction into a decision-complete design: needs/actions, order matching, prices, credit limits, default/recovery and player account boundaries.
- Continue Scythe from the contour overlay, then capture first contour-contract audits for Biomass Collector and Crusher Stalker; no Harvester entity may be registered before its model passes the 85/100 and hard-gate review.

## Open questions
- UNCONFIRMED: cold-taiga and dry-arid art-family quality plus live Villager Overhaul patrol/combat quality still need equivalent client playtesting.
- UNCONFIRMED: whether players may borrow in 0.4, how market prices clear and how creditor/debtor default is resolved.
- UNCONFIRMED: 0.3 positive aftermath, delayed consequence and full clean-room comprehension have not yet passed external human gates.
- UNCONFIRMED: Biomass Collector and Crusher Stalker have source assets but have not yet passed the new contour-contract audit.

## Working set
- Workspace `../AGENTS.md`; `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/living-frontier-0.3-release-audit.md`; Harvester `README.md`, `review_briefs.json`, `tools/harvester_visual_audit.mjs`, `tools/harvester_contour_overlay.py`; next work is live 0.3 validation, contour-led creature correction and the 0.4 economy design.
