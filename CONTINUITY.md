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
- Wave 1 foundation adds the independent pure-Java `pale-mirror-frontier` module, typed IDs and simulation values, checked fixed-point arithmetic, keyed RNG golden vectors, deterministic bounded scheduling and versioned kernel codecs. Focused tests and the critical Gradle gate, including 168 existing NeoForge GameTests, pass.
- `eea89c1` records the Wave 1 module/primitives foundation.
- `84d3136` adds immutable command/event envelopes, typed rejection, one-thread atomic in-memory transactions, bounded receipt/transaction retention, reducer quarantine, non-destructive due-action admission and a reproducible pure-kernel benchmark fixture.
- Explicit kernel schedule-created, schedule-cancelled, schedule-rescheduled and schedule-consumed events now mutate the due-action index only inside the same successful transaction; failed due work remains scheduled and visibly quarantines the engine.
- Wave 1 now has explicit payload registries, versioned command/event/transaction codecs, built-in schedule-effect codecs and fail-closed deterministic transaction replay. Focused replay tests and the critical Gradle gate, including 168 GameTests, pass.
- The Wave 1 review tightened event-time causality: a command is accepted only at the engine's current `SimInstant`, so retained history cannot acquire a backdated event that replay would reject.
- A fresh-JVM fixture now serializes the same command/schedule transcript twice and proves byte-identical event and checkpoint output across independent Java processes.
- Wave 2 defines the v3-only `FrontierStore` port, typed durability/append/snapshot/compaction receipts and `RecoveryImage`; foreign world checkpoint/WAL inputs fail before replay.
- `9c28c1f` records the cross-JVM deterministic replay proof. Wave 1 is complete at automated kernel-evidence level; it is not gameplay, persistence-host or product validation evidence.
- Wave 1 focused negative/recovery tests and the full critical Gradle gate pass: architecture checks, build/package validation and 168 NeoForge GameTests. The benchmark is documented at `docs/benchmarks/frontier-v3-wave1-baseline.md`; repeated runs retain the same checkpoint input hash `00002710`.

### Now
- The obsolete goal was cleared and the approved Frontier v3 durable goal is active.
- Wave 2 is starting with a v3-only persistence contract: recovery image, WAL transaction records, snapshot integrity and a NeoForge storage host, before fresh-world gameplay aggregates.
- Existing deployed v2 server/runtime state is unchanged and is not evidence for v3.

### Next
- Implement checksummed pure snapshot/WAL schemas and fault-injection recovery tests, then add the v3-only NeoForge storage host and exact bootstrap ownership.

## Open questions
- Exact balance constants, infection/territory tuning and final HOT actor budgets remain profile calibration work; they do not block the architecture or Wave 1.
- Product validation remains unproven until real-display continuity audits, clean-room comprehension and cooperative-player gates are recorded for v3.

## Working set
- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `build.gradle`.
- `docs/frontier-v3-contract.md`, `docs/frontier-v3-implementation-plan.md`, `docs/frontier-ai-contract.md`, and the archived pre-v3 ledger.
- Current wave: `pale-mirror-frontier/` kernel sources/tests, `settings.gradle` and architecture lifecycle validation.
