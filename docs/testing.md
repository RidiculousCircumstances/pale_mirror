# Test matrix and evidence

`PASS` means an observed test artifact, not an inference from a mod page. `PENDING`
means it has not been run in this environment.

| Area | Test | Status |
|---|---|---|
| BOOT | Packwiz metadata resolves all pinned core artifacts | PASS — 2026-08-12 materialisation; current profile excludes Millénaire. |
| BOOT | Dedicated server discovers all core mod manifests | PASS — 2026-08-08; Hostile Tactics manifest reports 0.10.1. |
| BOOT | ScalableLux with Sable 2.0.3 | BLOCKED — 2026-08-13 Java 22 full-profile boot; Sable declares ScalableLux incompatible, so the candidate was removed rather than bypassing the loader gate. |
| BOOT | Dedicated server reaches `Done` | PASS — 2026-08-13 clean full-pack stable profile on pinned Temurin 22 reached `Done (15.746s)`; spawn preparation took 4.479 s and shutdown was clean. |
| BOOT | Cache-only server-side Distant Horizons | RETEST — 2026-08-13 profiling showed that `PRE_EXISTING_ONLY` still ran DH's background importer. PM now disables that importer and client generation requests outright while retaining the mode as a dormant fail-safe. Ready client LOD sync and absence of `DH-World Gen Thread` work require a graphical/JFR retest. |
| BOOT | Experimental C2ME profile compatibility | PASS — 2026-08-13 full-pack Java 22 restart reached `Done (3.331s)`, created exactly eight C2ME workers and DH selected its C2ME pre-existing-chunk accessor. C2ME remains an active playtest candidate pending the repeated flight and correctness gates. |
| INSTALL | packwiz client bootstrap materialises the client-side profile | PASS — 2026-08-08 isolated bootstrap test; client-only artifacts downloaded and server-only artifacts correctly skipped. The current default manifest excludes Caliber instead of relying on headless optional selection. |
| INSTALL | Terralith client dependency is present | PASS — 2026-08-08; Terralith's own NeoForge descriptor declares Lithostitched `side = "BOTH"`. Pack metadata was corrected from an erroneous server-only classification to `both`. |
| INSTALL | Linux client installer materialises a clean instance without Caliber | PASS — historical 2026-08-08 run disabled the downloaded optional JAR; the current default manifest excludes Caliber entirely so every platform receives the same profile. |
| INSTALL | Linux server installer materialises a clean dedicated server | PASS — 2026-08-13 clean materialisation installed Java-22-compatible stable mods, DH on both sides, 8/6 distances, synchronous writes, managed 4–12 GiB heap and a disabled C2ME artifact. |
| INSTALL | Windows 10 PowerShell client installer end-to-end | PENDING — PowerShell is unavailable in this environment; requires a real Windows 10 NeoForge-client test. |
| BOOT | Dedicated server accepts connection | PASS — repeated live NeoForge client sessions through 2026-08-13. |
| CLIENT | Client starts and connects | PASS — 2026-08-13 graphical Windows client joined and performed the profiled creative-flight run. |
| QUESTS | Materialise pinned FTB Quests + FTB Library through Packwiz | BLOCKED — FTB Quests `2101.1.30` is selected, but this environment cannot reach the CurseForge resolver API; no unverified lockfile entry was created. |
| QUESTS | Full-profile FTB Quests server/client boot, key binding and restart persistence | BLOCKED — follows reproducible materialisation. |
| QUESTS | One settlement-to-forpost sealed-cargo vertical slice | BLOCKED — requires the planned `far_frontier_core`; no generic daily quests or chapter rewards are enabled. |
| WORLDGEN | Multi-seed terrain/biome/cave inspection | PENDING |
| WORLDGEN | Tens-of-km pregeneration/restart | N/A — the accepted policy deliberately optimises live generation and does not pregenerate the open world. |
| STRUCTURES | Dump IDs, apply conservative spacing, inspect overlaps | PENDING |
| CREATE | Basic machine, train, contraption | PENDING |
| AERONAUTICS | Assemble, fly, land, restart, chunk-edge test | PENDING |
| COMBAT | Vanilla groups, shields, all modded encounter tiers | PENDING |
| EVENTS | Dormant Ravents profile parses on dedicated server | PASS — 2026-08-15 live server reports `Loaded 2 mobs, 0 raids, 0 events`; the former Tier 0 wave raid was removed. |
| EVENTS | Enhanced Celestials / Blood Moon | DISABLED — 2026-08-15 boot contains neither EC2 module nor its orphan dependencies. |
| CRIMSON + SPORE | Native NeoForge dedicated-server boot with active native Spore integration; automatic Crimson raids guard | PASS — 2026-08-08; pinned Crimson `1.4.3.1` + Spore `2.2.0j` reaches `Done` on NeoForge 21.1.248 with the obsolete no-Spore quarantine absent. `far-frontier-spore-zones` is auto-enabled and no missing `spore:*` resource error occurs. `Global/Raids_Enabled=0` remains the Crimson policy. One upstream Crimson disabled-advancement invalid-path log remains non-fatal. |
| SPORE | Conservative config and sparse structure-set datapack parse in full server profile | PASS — 2026-08-08; 13 Spore `structure_set` overrides at 4096/3072 chunks load on the dedicated server. |
| SPORE | Multi-seed density, hivemind lifecycle, local containment and combat | PENDING |
| CRIMSON CURSE | Client profile materialisation through packwiz | PASS — 2026-08-08; live `:8091` source produced Crimson Curse plus ETF, EMF, Sodium NeoForge and Polytone in a clean client profile; Caliber was renamed `.jar.disabled`. |
| CRIMSON CURSE | Client boot with Sodium/Polytone/EMF/ETF + Distant Horizons + Sable | PENDING |
| CRIMSON CURSE | Infection generation, phase progression, 10 km travel and performance | PENDING |
| CIVILIZATION | PM-authored settlements + Villager Overhaul; no random vanilla/Integrated Villages settlements | PENDING |
| MILLÉNAIRE | Separate compatibility profile | DISABLED — removed from the default client/server pack after a 32-second village-chunk stall caused a client timeout. |
| ALEX'S CAVES | Six cave biomes, reload, spawning, pregen | PENDING |
| PERFORMANCE | C2ME combined live-flight server profile | CANDIDATE — 2026-08-13 cache-only rerun, 402 s JFR: mean 5.24 ms, p50 5.27 ms, p95 10.23 ms, p99 14.88 ms rolling MSPT; JVM CPU averaged 18.70% over the whole recording and 22.76% while the player was connected. AVX2 and DFC remained active; the DH background worldgen thread disappeared. One 2.548 s `Can't keep up` warning was not a single main-thread stall: JFR showed ordinary sub-48 ms tick waits and a 57.4 ms maximum rolling tick value. G1 paused 4.42 s total (max 172.5 ms), RSS grew from 5.3 to 12.1 GiB under approximately 478 GiB of temporary allocations, and the bounded event-fed DH LOD builder reported queue overload twice. User-observed worldgen speed improved substantially. Exact blocks/s and a longer post-flight heap/LOD-drain check remain before final promotion. |
| EXPEDITIONS | Contract persistence, dormant native chapter arena, unique artifact closure and post-boss unlock | BLOCKED — planned far_frontier_core vertical slice; no Cataclysm boss is promoted to a chapter gate until its native spawn/arena adapter passes. |

## Known boot findings

- The core mod manifests load on NeoForge `21.1.248`; Aeronautics' bundled module is
  discovered correctly.
- `datapacks/idas-optional-integration-quarantine` was boot-tested on 2026-08-08.
  Minecraft automatically enabled `file/idas-optional-integration-quarantine`; the
  prior 10 IDAS Ice and Fire / Ars Nouveau loot errors and two invalid spawner pools
  no longer appear. It removes only Dread Citadel, Archmage's Tower and Siren's Cove
  from IDAS sets. This is a PASS for the optional-integration quarantine.
- `datapacks/ida-legacy-loot-fix` was boot-tested on 2026-08-08. Minecraft
  automatically enabled it alongside the IDAS quarantine pack; the five prior IDA
  `dungeons_arise` legacy loot errors no longer appear. It is a verbatim upstream
  copy except for the required nested loot-table `name` → `value` conversion.
- Integrated API still logs missing map-decoration keys. This is independent of the
  loot fixes and remains a structure/worldgen investigation item.
- Quark logs creature/monster-category mismatches for stoneling/toretoise in many
  Terralith biomes. This is an upstream spawn-cap risk to quantify in ecology tests.
- Historical Millénaire findings remain relevant only to a future isolated compatibility
  profile. Its JAR and generated configuration are not part of the active pack.
- Ravents has no configured raids or events. Any future use must originate in a
  canonical Pale Mirror decision and must not present independent ambient waves.

Raw smoke logs and disposable worlds stay outside git; commands and pass criteria are
kept in `scripts/`.
