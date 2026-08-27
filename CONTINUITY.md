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
- User permits starting a client without a new approval, but only one Minecraft client may run at a time; run only relevant tests. Manual game audits use the real `DISPLAY=:0` so the user can observe them; automated GameTests remain headless.
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
- The pending readability cut makes boards genuinely local (landmarks 128 blocks, object facts 29–35), reserves horizontal eye-level space, bounds lateral slot search at 4,096 O(1) spiral positions, and keeps one visible board for each player-facing conflicted object while retaining every exact background conflict in the ledger/scar. Replayed interaction events win the board priority and have a dedicated GameTest.
- The same cut maps severe infection to purple tissue and permits only one existing pink signal clump per infected cell; no source rule, quantity, geometry or provenance changed.
- Full critical verification of the cut passed: focused label/plan JUnit tests, `:pale-mirror-neoforge:runGameTestServer` 139/139, and `guardrails check :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
- Entry JFR identified repeated `PresentationLedger.claims()` copies and full footprint scans for every label candidate. `SourceGrayboxLabelHeightIndex` now builds one bounded, immutable 1024×704 source-height index per presentation and preserves the former highest-roof rule; focused negative/equivalence tests and the final critical gate pass, including 139/139 GameTests.
- The Village observer GameTest fixture now places its vertical-only landmark directly above the focus, rather than relying on asynchronous POI indexing outside the adapter's intentional horizontal fallback radius; it still proves the documented vertical search contract.
- A matched COLD re-entry route at settlement 11 (`-56,89,72`, then four points at Y=73/radius 12) recorded no `SourceGrayboxLabelPositions` samples, versus 291 in the prior JFR, and 6 `claims()/valuesToArray` samples versus 270. One-second server-tick mean/max was 1.830/5.671 ms versus 3.194/59.360 ms; neither repeat emitted `Can't keep up`.
- Source routes now compile to a bounded three-block physical corridor: one bright light-blue canonical centre, two neutral gray shoulders and shoulder-mounted waypoints. Only the centre retains the exact typed-damage slots; shoulders have stable presentation claims but no interaction weight. Focused plan coverage, an independent physical GameTest and the final critical gate passed (145/145 GameTests).
- The route cut was published and checked in the real full-screen PMAudit client at `graybox/route/route:4:6/corridor`; the clean player-eye evidence is `build/visual-audits/20260827T0618Z-route-corridor-after/route-4-6-after-clean.png`. It proves the updated local corridor, not product-art acceptance.
- An isolated full-screen audit run proved that missing settlement bodies were not caused by third-party gameplay mods: a loaded exact HOT actor could retain its reservation after the physical body vanished. The executor now distinguishes a genuinely absent exact UUID from a present-but-invalid body, settles only the former COLD, releases its claim, and permits exactly one fresh later lease; a typed death remains a separate source observation. Focused domain coverage and the critical gate passed, including 146/146 GameTests.
- The resident one-frame flicker had a vanilla cause rather than an AI/executor cause: the deployment profile set `spawn-npcs=false`, and `ServerLevel` consequently discarded an already-added PM Villager on its next tick. The profile now leaves all three global spawn flags enabled and relies on the already-defined source-graybox admission boundary for ambient custody. The final gate passed with 149/149 GameTests; the packaged SHA-512 was deployed and a real-display audit retained `farmer | settlement` across successive observations without an unexpected HOT departure.

### Now
- The retained disposable `frontier-graybox-10-y63-r3-no-structures` server is running on port 25565 with the deployed Pale Mirror JAR SHA-512 `cdd650cf…e29f4`; exactly one PMAudit client is connected at 3840×2400 on real `DISPLAY=:0`.
- The active runtime has `spawn-animals=true`, `spawn-monsters=true`, and `spawn-npcs=true`. This is intentional: vanilla's global NPC switch would otherwise remove canonical Villagers. The separate verified commits are Pale Mirror `914dbcc` (HOT actor recovery) and outer-pack `e0d6fc2` (runtime profile).
- Evidence is `build/visual-audits/20260827T1408Z-disappearance-trace/final-npc-lifetime*.png`, `admission-probe-exact-immediate.png`, and `/home/rd/far-frontier-server/logs/graybox-final-npc-lifetime-20260827T0957Z.log`. The first proves continuous source-resident lifetime; the latter clean probe reports `No entity was found` for an explicitly summoned cow within four blocks of the real graybox player, proving the EntityJoin custody boundary rather than relying on the vanilla success message.

### Next
- If a distinct entry stall recurs, capture the same COLD route first and separate chunk load/deserialization, static projection, entity admission, save/lighting, network and client work before another performance change.
- Improve settlement and hive infrastructure beyond the route corridor against semantic player-eye evidence; do not fake missing operation/post scenes for coverage.
- Exercise `capture-settlement-visuals.sh --source-graybox` with its isolated audit client and retain the reproducible contact sheet; its recovery path must return the operator to the captured original dimension.
- Extend the deployed reload/restart proof to a naturally evolving live scene with a player-visible return on `DISPLAY=:0`.
- Keep exercising the deployed HOT/COLD recovery in a naturally evolving live scene; retain the exact resident identity, relevant server log interval, and one-client evidence rather than trusting a one-frame observation.
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
