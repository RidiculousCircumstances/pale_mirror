# Continuity Ledger

## Goal (success criteria)
- Implement Pale Mirror Frontier v3 from `docs/frontier-v3-contract.md` and `docs/frontier-v3-implementation-plan.md` as a greenfield Java event-driven simulation with production two-way Minecraft materialization.
- Deliver a finite autonomous 1024×1024 world with 12 settlements, one distributed hive rooted at two seed nests, exact people/bioforms/items, continuous HOT/COLD processes and unrestricted accounted physical consequences.
- Cut production over to v3 and remove the v2 runtime only after deterministic, full-scope, performance, restart/recovery, real-world and player-comprehension gates pass.

## Constraints/Assumptions
- Pale Mirror is the independent nested repository `pale-mirror/`; the outer repository owns pack/deployment artifacts. Inspect and commit them separately.
- Java 21, Minecraft 1.21.1 and NeoForge 21.1.248 remain the pinned development platform.
- `pale-mirror-frontier` will be a pure-Java module under `io.farfrontier.palemirror.frontier.v3` with no dependency on `pale-mirror-domain`, `frontier.reference`, Python, Minecraft or NeoForge.
- V3 is fresh-world-only. V2 remains frozen and isolated until the v3 cutover gate; there is no shared state, save migration or runtime fallback.
- One resident is one canonical person and one HOT Villager; one bioform is one canonical creature and one HOT graybox Zombie; one Minecraft item is one matching canonical item.
- No player, settlement, hive, ownership class or operation boundary is a safe zone from legitimate physical effects. Every actual consequence must be observed and reconciled.
- Desired-state materialization never silently overwrites an unknown/player change. Physical effects and post-impact accounting are a separate boundary.
- No simulation, materialization or audit path force-loads chunks. Start at most one visible client on `DISPLAY=:0` and run only relevant gates.

## Key decisions
- The v3 product promise is one seamless autonomous Minecraft world: foreground and background execution differ in location, not truth or fidelity.
- Canonical mutation is one ordered server-thread lane of typed commands, deterministic events and persisted due actions; there is no indivisible daily phase.
- One simulation unit is one running server tick. Zero-player server time advances; stopped-server time pauses; sleep advances through an explicit command; `/time` alone does not.
- Fixed-point arithmetic, checked rounding and keyed/counter RNG make v3 deterministic from its own input stream without Python numerical parity.
- HOT scenes use persisted atomic leases; Minecraft supplies actual movement, collision, combat, inventory and effect results while leased COLD actions are suspended.
- V3 persistence is checksummed snapshots plus ordered WAL. Player custody and non-replayable physical effects are durable before acknowledgement/execution and recover by postcondition inspection, never blind replay.
- Buildings/organs have stable IDs, semantic block parts and domain state. Infection uses a sparse 4×4 surface field; territory and actor positions use separate resolutions; baseline plus bounded sparse deltas retain physical aftermath.
- Human and hive AI use event-triggered utility selection, durable HTN-like processes and bounded HOT local goals. The two seed nests belong to one hive economy.
- Exact slot-owned resources, containers and cargo are the economy. There is no hidden aggregate stock; meaningful player supply may require tens or hundreds of stacks.
- Evidence levels remain separate: architecture, automated correctness, real-world continuity and unbriefed player comprehension.

## State

### Done
- The user accepted replacement of the Python/source-parity direction with greenfield Frontier v3 and approved the stable contract, implementation-wave structure and legacy-removal policy.
- V2 established useful exact-identity, HOT/COLD, physical-effect, reverse-causality, graybox readability and performance evidence; it is historical input, not a v3 runtime dependency.
- The complete pre-v3 ledger is archived at `docs/archive/CONTINUITY_2026-08-27_pre_frontier_v3.md`.
- `a1a6931` establishes the stable v3 contract, decision-complete implementation waves, valid version-3 architecture transition and explicit frozen-v2 notice.
- Wave 0 adds executable architecture validation and future `pale-mirror-frontier` source-boundary scans. Focused validator tests, `git diff --check` and `./gradlew guardrails check --no-daemon` pass; the full Gradle gate completed 56 tasks successfully.

### Now
- Wave 0 is complete at architecture/evidence level 1. No v3 runtime source exists yet, so no simulation, physical-world or player-product claim is made.
- Existing deployed v2 server/runtime state is unchanged and is not evidence for v3.
- The old durable `/goal` is paused and obsolete but not complete; it must be cleared rather than falsely marked achieved.

### Next
- Clear the obsolete paused goal with `/goal clear`, create the approved Frontier v3 durable goal, then begin Wave 1 with the isolated module and deterministic event kernel.

## Open questions
- Exact balance constants, infection/territory tuning and final HOT actor budgets remain profile calibration work; they do not block the architecture or Wave 1.
- Product validation remains unproven until real-display continuity audits, clean-room comprehension and cooperative-player gates are recorded for v3.

## Working set
- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `build.gradle`.
- `docs/frontier-v3-contract.md`, `docs/frontier-v3-implementation-plan.md`, `docs/frontier-ai-contract.md`, and the archived pre-v3 ledger.
- Current wave only: no `pale-mirror-frontier` source or settings entry exists yet.
