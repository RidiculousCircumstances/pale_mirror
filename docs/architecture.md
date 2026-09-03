# Architecture

Far Frontier is deliberately layered so a failure is diagnosable without replacing
unrelated gameplay.

| Layer | Components | Boundary |
|---|---|---|
| WORLD | Terralith + Tectonic + Structurify | Tectonic creates macro relief; Terralith supplies biome variety; Structurify controls non-IDA structure density. |
| RENDER | Vanilla Minecraft renderer | The supported v3 profile avoids the unstable third-party renderer stack. |
| ENGINEERING | Create | Transport and machines; tested on a dedicated server and across saves. |
| QUESTS | FTB Quests (adopted; pin/boot gate pending) | Campaign journal and operation briefings; never authoritative world or boss state. |
| EXPEDITIONS | Far Frontier expedition core (planned) | Persistent distant contracts, dormant chapter arenas and non-substitutable chapter artifacts. |
| EXPLORATION | IDA + IDAS + YUNG + Cataclysm | WDA is excluded; IDA uses its own documented structure-set overrides. |
| ECOLOGY | Naturalist | Ambient fauna, audited against vanilla caps. |
| THREATS | vanilla + Hostile Tactics + Born in Chaos + Mowzie's | Vanilla stays common; modded threats are increasingly rare and distinct. |
| INFECTION | Crimson Curse + Spore | Pale Mirror owns strategic threat state and activation; both providers are constrained physical projections. |
| COMBAT | Better Combat + Simply Swords | No blanket HP/damage escalation. |
| DIRECTOR | Pale Mirror | PM owns progression and causal stories. Ravents is installed without raids/events as a dormant future adapter; Enhanced Celestials is excluded. |
| CIVILIZATION | PM-authored settlements + Villager Overhaul | Integrated Villages remains an installed private asset source with settlement generation disabled. Millénaire is an optional compatibility target and is disabled in the current profile. |

## Worldgen ownership

Terralith and Tectonic use their official NeoForge mod artifacts together. Structure
spacing is never tuned from intuition: the final server exports Structurify's actual
`structure_set` IDs first. Structurify controls all non-IDA sets. The upstream IDA
author advises against Structurify frequency control, therefore any IDA spacing change
is a small, explicit vanilla datapack override with its source JSON recorded.

No extra structure or mob mod is admitted merely to fill a perceived gap. A proposed
addition must document the uncovered function, native NeoForge 1.21.1 artifact,
dependency cost, and benchmark delta.

Spore structure placement is an explicit versioned datapack, not Structurify:
the upstream sets are too dense and its containment policy couples placement with
runtime ecology. See docs/spore-integration.md.

Pale Mirror owns named operations, regional pressure and their authored lifecycle.
Ravents must not independently start encounters in the default profile. FTB Quests
is the readable campaign layer, not a second director. See docs/expedition-system.md
and docs/quest-system.md.

## Operational constraints

Alex's Caves and Citadel are excluded from every supported profile, as are the
optional Distant Horizons/Iris/Sable/Aeronautics renderer and flight stack. Caliber is
excluded from the default manifest and belongs only in a separate evaluation
profile. Performance mods are intentionally deferred until functional tests
establish a baseline.
