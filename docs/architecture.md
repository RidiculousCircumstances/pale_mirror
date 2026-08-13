# Architecture

Far Frontier is deliberately layered so a failure is diagnosable without replacing
unrelated gameplay.

| Layer | Components | Boundary |
|---|---|---|
| WORLD | Terralith + Tectonic + Structurify | Tectonic creates macro relief; Terralith supplies biome variety; Structurify controls non-IDA structure density. |
| RENDER | Distant Horizons | Client rendering plus server LOD cache/sync from pre-existing chunks; it never generates authoritative terrain. |
| ENGINEERING | Create + Sable + Aeronautics | Transport and machines; tested on a dedicated server and across saves. |
| QUESTS | FTB Quests (adopted; pin/boot gate pending) | Campaign journal and operation briefings; never authoritative world or boss state. |
| EXPEDITIONS | Far Frontier expedition core (planned) | Persistent distant contracts, dormant chapter arenas and non-substitutable chapter artifacts. |
| EXPLORATION | IDA + IDAS + YUNG + Cataclysm + Alex's Caves | WDA is excluded; IDA uses its own documented structure-set overrides. |
| ECOLOGY | Naturalist | Ambient fauna, audited against vanilla caps. |
| THREATS | vanilla + Hostile Tactics + Born in Chaos + Mowzie's | Vanilla stays common; modded threats are increasingly rare and distinct. |
| INFECTION | Crimson Curse + Spore | Crimson is global strategic pressure; Spore is local contamination-site ecology. Automatic encounter escalation remains Ravents-owned. |
| COMBAT | Better Combat + Simply Swords | No blanket HP/damage escalation. |
| DIRECTOR | Ravents + Enhanced Celestials 2 | Ravents owns progression; EC2 adds occasional environmental timing. |
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

The planned expedition core does not replace Ravents. Ravents directs ordinary
world pressure; expedition state owns only named destination operations and their
authored boss lifecycle. FTB Quests is the readable campaign layer, not a second
director. See docs/expedition-system.md and docs/quest-system.md.

## Operational constraints

The identical Distant Horizons build runs on both sides. The dedicated server is
permanently cache-only: the background importer is disabled, normal chunk events
populate ready LODs, and `PRE_EXISTING_ONLY` remains only as a dormant fail-safe.
Alex's Caves port and unofficial Citadel are paired Packwiz options. Caliber
is excluded from the default manifest and belongs only in a separate evaluation profile. Performance mods are intentionally deferred
until functional tests establish a baseline.
