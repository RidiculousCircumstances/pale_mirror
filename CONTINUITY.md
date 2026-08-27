# Continuity Ledger

## Goal (success criteria)
- Close Pale Mirror 0.3 as a player-legible Living Frontier: an unbriefed player discovers a real crisis, chooses a response, sees the physical aftermath and understands the next causal story.
- Keep `/home/rd/proj/pale_mirror_ai/simulation/` as the pinned semantic oracle while Java is the deterministic canonical runtime; replace the diagnostic projector with a persistent HOT/COLD physical executor for source-parity `graybox_1_40`.
- The graybox is a finite 1024×1024 Minecraft-scale, two-way autonomous world: 12 settlements, 2 infection seeds, exact people and bioforms, live operations/effects/logistics/infection, typed reverse causality and seamless unload/reload/restart continuation.

## Constraints/Assumptions
- Pale Mirror is the independent nested repository `pale-mirror/`; the outer repository owns the pack, installers, hosted artifacts and deployment.
- Read both workspace guardrails plus this ledger at the start of each relevant turn; keep this ledger factual and at most 120 lines.
- Java 21 bytecode/toolchain, Minecraft 1.21.1 and NeoForge 21.1.248; the disposable private server runs Java 22.
- `pale-mirror-domain` is pure Java and owns canonical state. Minecraft blocks/entities/labels and visual adapters are observed/materialized representations only.
- Core schema v41 and Visuals schema v40 intentionally reject older worlds. The dedicated test world is disposable unless the user says otherwise.
- One source resident is one managed Villager and one source bioform is one managed Zombie; no cohorts or hidden population multiplier. Human stock/capacity/credit use the accepted 1:40 scale.
- No player or PM ownership class is a safe zone. Real PM physical effects are observed and reconciled; unknown/player changes remain explicit conflicts and are never silently overwritten.
- User permits starting a client without a new approval, but only one Minecraft client may run at a time; run only relevant tests.
- Private visual/creature asset provenance and their existing acceptance rules remain unchanged; unrelated Harvester work is not part of the graybox implementation slice.

## Key decisions
- The world plays itself: simulation creates production, demand, trade, rationing, investment, migration, recovery and decline; narration exposes rather than creates or freezes those processes.
- Python `source_v2` is the semantic oracle. Java follows its phase order and determinism contracts, with bounded acceptable numerical drift only where explicitly calibrated.
- Canonical daily state is indivisible: GAMEPLAY is 24,000 Minecraft ticks/source day; physical actors tick at 20 TPS, brains at 10 ticks, local combat at 5 ticks, operation reconciliation at 20 ticks and infection/work at 100 ticks. FAST_GRAYBOX is explicitly 1,200 ticks/day plus full-day fast-forward.
- Source records own territory, infection, ecology, settlements, residents, economy, companies/contracts/credit, sites, posts/links, operations, campaigns and engagements. `SourceGrayboxSavedData` atomically persists source document plus bounded execution/effect/container/scar ledgers.
- HOT/COLD is demand-driven from real non-spectator player chunks plus an already-loaded apron; no actor or audit code force-loads chunks. An exact actor has at most one lease/body and collision recovery fails visibly to COLD rather than duplicating or killing it.
- Physical deaths, declared interaction-slot breaks and exact warehouse/carrier deltas become deduplicated, revision-validated source observations. Physical consequences are durable non-replayable receipts with post-impact reconciliation.
- Infection grows through source-owned independent 3×3 provenance clumps. A blocked clump is a visible scar, not permission to erase a foreign block; the visual adapter never invents infection or alters its value.
- Graybox uses rectangular color-coded structures, normal villagers by role colour, zombie bioforms by kind colour, continuous visible routes and distinct hive silhouettes. One object gets one local information board; geometry must still communicate before text.
- Labels are local player aids, not a global sky HUD: landmarks are larger/farther, object details and interaction prompts remain local; explicit three-row source briefings must not be paragraph-wrapped.
- Foundry remains read-only over immutable plans and loaded chunks. Generated-world performance, loading/materialization, networking and client rendering must be measured separately before optimization.

## State

### Done
- Java Wave 0 has CPython RNG/profile/config parity; the pure seven-phase reference engine, named resident ledger and source-pinned checkpoints cover seeds 42/7/17/41/73. The annual `graybox_1_40` envelope gates 29 metrics across seeds 7/17/41/73.
- The 64×44/16-block source profile, 12 settlements and 2 seeds are canonical and bounded. Actor custody, field posts/links/campaigns, warehouse stock, cargo hand-offs, physical scars, infection tissue and source-role local movement/combat exist with negative/recovery coverage.
- Schema v28 actor admission is collision-safe and migration-tested. A live v27→v28 migration plus a normal-display COLD→HOT audit showed safe Villager head/feet clearance without duplicate body or obstruction error.
- Static materialization is cached and only republishes changed source frames/COLD preparation/natural graybox chunk loads; HOT positions persist independently. A warm idle JFR showed no full snapshot work and low PM CPU after the client left.
- Snapshot-only graybox post/campaign/operation scene silhouettes, exact typed interaction slots, physical source cargo carriers, real TNT/scar reconciliation and conflict retention are implemented; `graybox-10` is non-authoritative legacy scaffold.
- The current presentation cut preserves exact explicit three-line briefings, uses larger full-bright native boards with a tested landmark/detail range hierarchy, and keeps CustomName as an unrendered recovery mirror.
- The same cut maps severe infection to purple tissue and permits only one existing pink signal clump per infected cell; no source rule, quantity, geometry or provenance changed.
- Full critical verification of the cut passed: focused label/plan JUnit tests, `:pale-mirror-neoforge:runGameTestServer` 139/139, and `guardrails check :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
- Entry JFR identified repeated `PresentationLedger.claims()` copies and full footprint scans for every label candidate. `SourceGrayboxLabelHeightIndex` now builds one bounded, immutable 1024×704 source-height index per presentation and preserves the former highest-roof rule; focused negative/equivalence tests and the final critical gate pass, including 139/139 GameTests.
- The Village observer GameTest fixture now places its vertical-only landmark directly above the focus, rather than relying on asynchronous POI indexing outside the adapter's intentional horizontal fallback radius; it still proves the documented vertical search contract.
- A matched COLD re-entry route at settlement 11 (`-56,89,72`, then four points at Y=73/radius 12) recorded no `SourceGrayboxLabelPositions` samples, versus 291 in the prior JFR, and 6 `claims()/valuesToArray` samples versus 270. One-second server-tick mean/max was 1.830/5.671 ms versus 3.194/59.360 ms; neither repeat emitted `Can't keep up`.

### Now
- Verified commit `d5ac1ef` artifact SHA-512 `91ad611e300fb6f88e71f4efe4c2787b8bfea6a66a3ab949ae8cb7b62a6ad1513dfdc516b009f6501393ad8a669f4641b65048496d43c499853a2bc4fd3e6914` is hosted, checksum-pinned installed and running in the disposable server PID 2400614 on port 25565; `frontier-graybox-10-y63-r3-no-structures` was retained.
- Real-client visual evidence is at `build/visual-audits/20260827T010309Z-8_65_8/` and the close settlement-11 pass `build/visual-audits/20260827T010942Z--56_65_72/`. The close pass confirms local boards, color-coded routes and distinguishable purple tissue/pink signal accents; distant board clutter and raw graybox forms remain diagnostic presentation, not a product-art acceptance.
- The current performance evidence is `build/profiles/graybox-entry-20260827T011858Z.jfr` and the matched repeat `build/profiles/graybox-entry-matched-after-20260827T013855Z.jfr`; the post-change screenshots are `build/visual-audits/20260827T013855Z--56_65_72/`. The verified short-range-safe index is committed and deployed; no audit client is running.
- The first high observation-deck top frame was invalid for visual judgment; the reduced-height top frame correctly sees the ground. Future graybox audit cameras need semantic eye-level views, not only generic anchor aerials.
- The presentation refinement is committed as `08d0a29`. The outer pack repository has no tracked change.

### Next
- If a distinct entry stall recurs, capture the same COLD route first and separate chunk load/deserialization, static projection, entity admission, save/lighting, network and client work before another performance change.
- Add semantic graybox audit cameras for a settlement, hive organ, route/intersection, active operation and field post; capture player-eye views after each material visual change.
- Continue the HOT/COLD executor proof: reload/restart and return to an evolving active scene must retain exact bodies, effects, cargo and player-caused scars without force-loading or repair.
- Complete remaining source-parity domains and negative/recovery tests against the Python oracle; do not substitute legacy `graybox-10` mechanics.
- Validate active operations, exact physical transport and tactical effects as continuous player scenes, including kills/destruction/item flows returning to source state.
- Product gates remain open: live natural discovery, infection/recovery, logistics/evacuation/refusal, cooperative authority and clean-room player comprehension; the accurate status is not product-validated.

## Open questions
- UNCONFIRMED: exact source-parity breadth still needs an auditable domain-by-domain checklist and calibration evidence for the complete Python oracle.
- UNCONFIRMED: optimal player-facing label range/interaction affordance after semantic eye-level cameras replace anchor-only aerials.
- UNCONFIRMED: physical operation transport needs a dedicated canonical transit state before it can be generalized beyond the current carrier hand-off.
- UNCONFIRMED: 0.3 human product gates and the separate Harvester contour acceptance remain open.

## Working set
- `../AGENTS.md`, `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/frontier-ai-contract.md`, active Python `simulation/` and `tests/`.
- `pale-mirror-domain/.../frontier/reference/`, `pale-mirror-neoforge/.../internal/world/SourceGraybox*`, GameTests and source-graybox audit/deployment scripts.
- Current visual evidence directories above; server runtime `/home/rd/far-frontier-server`; outer pack scripts only for verified artifact publication/install.
