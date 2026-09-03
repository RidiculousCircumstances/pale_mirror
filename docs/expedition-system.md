# Expedition and chapter-boss system

## Status

**DESIGN FROZEN; NOT ENABLED IN THE LIVE PROFILE.** This document fixes the
intended architecture. No boss spawning, Cataclysm worldgen, recipe or Ravents
behaviour changes until the stated adapter tests have passed.

## Core loop

    intelligence → preparation → long journey → field objective
    → player-controlled arena activation → authored boss encounter
    → extraction → permanent chapter unlock

The system is deliberately a destination layer for rail logistics, not a quest
list or a global difficulty multiplier. Walking is viable for the first hard
expedition; Create trains serve repeatable known routes and bulk logistics rather
than first discovery.

## Authority boundaries

| System | Authority |
|---|---|
| Far Frontier expedition core | Persistent contracts, target selection, completion, unique artifacts and chapter state. |
| FTB Quests (adopted; pin/boot gate pending) | Campaign journal, operation briefings and visible player/team progress. It is never authoritative for a selected target, boss state or chapter artifact. |
| Ravents | Automatic world encounter/progression director. It may unlock new operation pools after a chapter, but it does not own a contract's coordinates or completion state. |
| Cataclysm / Mowzie's / Spore / Crimson Curse | Authored boss and encounter content. Their native AI, arenas, drops and mechanics are preserved. |
| Create rail logistics | Preparation, repeatable transport and cargo infrastructure. |

The proposed implementation is a minimal native NeoForge 1.21.1 integration
module named far_frontier_core, with content definitions in a versioned datapack.
KubeJS and repeating command functions are not the baseline: they do not provide
the required persistent, restart-safe, per-location boss lifecycle.

FTB Quests is deliberately reused for its mature book UI and editable campaign
graph rather than recreated in the core. Its exact boundary and its first delivery
contract gate are defined in `docs/quest-system.md`.

## Contract state

Each expedition has server-saved state:

    id, chapter, selected target, owner/group, discovered/active/cleared state,
    activation prerequisites, completion evidence, issued reward and retry state

The core issues a signed chart/compass-like item after it selects a qualifying
location beyond the chapter's minimum distance. Early intelligence names a region
and bearing rather than exact GPS coordinates. On arrival, the chart resolves to
the final objective. State survives server restart, player death and chunk unload.

Only one active chapter operation per target may hold a temporary chunk ticket;
that ticket is released after victory, abandonment timeout or safe recovery. No
Expedition feature keeps remote chunks loaded permanently.

## Boss expedition rules

Every chapter boss has four layers:

1. **Approach.** Remote terrain, a limited outer encounter and a meaningful
   landing/camp decision.
2. **Field objective.** Restore a beacon, destroy anchors, collect signals or
   otherwise make the arena ready. This creates mechanics instead of extra HP.
3. **Player-controlled activation.** A prepared player starts the encounter.
   Entering an unloaded or newly discovered chunk never immediately starts a boss.
4. **Extraction.** Native loot plus the chapter artifact must be recovered or
   deposited to receive the permanent unlock.

A boss adapter never reimplements third-party AI with commands. It either verifies
and tracks a native encounter at its authored arena, or the boss type is rejected
from the expedition catalog.

## Cataclysm policy

Cataclysm has two explicitly different roles:

| Role | Availability | Reward |
|---|---|---|
| Incidental Cataclysm location | Ordinary rare world exploration, if retained after structure-density testing | Native loot only; no chapter artifact. |
| Chapter-boss arena | Not an ordinary available fight. It stays dormant until a valid expedition contract has completed its field objective and activates it. | Native loot plus exactly one expedition artifact and a chapter unlock. |

Therefore a player must not be able to casually find, kill and thereby bypass a
chapter boss. The initial implementation requirement is stricter than a loot
check: the selected Cataclysm boss must not become an ordinary active encounter
before activation.

Before any Cataclysm boss is admitted, its exact structure, spawn lifecycle,
arena prerequisites, save/reload behaviour and configuration controls must be
extracted from the pinned artifact and tested. The adapter then chooses one of
these safe paths:

1. Prefer a documented Cataclysm configuration/data control that suppresses
   ordinary generation/spawn of that chapter arena, while the expedition core
   activates only its selected target.
2. If native data permits a safe dormant state, retain the authored arena and
   activate its own intended mechanism only after contract validation.
3. If neither is safe, reject that boss from chapter use. It remains incidental
   content or its structure is disabled; no spawn-cancellation hack is accepted.

The exact Cataclysm boss roster is intentionally unchosen until this audit. This
avoids claiming that all Cataclysm arenas have identical lifecycle semantics.

## Unique-resource policy

There are two resource classes:

| Class | Rule |
|---|---|
| Chapter artifact | No survival alternative. It has no recipe, trade, generic loot-table source, tag substitute or normal mob-farm source. It is issued only by confirmed completion of its named expedition. |
| Consumable / native material | May have controlled alternatives. Food, repair, cargo supplies and ordinary mod materials must never hard-lock a player because of death, loss or an unlucky seed. |

The chapter artifact is deposited into permanent research/advancement state before
being consumed by an unlock recipe. If it is lost before deposit, the completed
contract can issue only a replacement for that same artifact; this is recovery,
not an alternate progression route.

The implementation must run a source-closure audit for every chapter artifact:
all recipes, loot tables, trades, entity drops, tags, datapack additions and
integration code paths are scanned against an explicit allowlist. Exact item IDs,
not broad tags, are used in unlock recipes.

Native Spore materials are not eligible as a sole chapter gate because local mobs
can be farmed. A sealed expedition sample granted after a confirmed site objective
is eligible. The same distinction applies to ordinary Cataclysm drops.

## Initial vertical slice

The first implementation is one complete route, not all chapters:

    Tier 2 intelligence
    → remote mountain/ruin operation
    → native, audited chapter boss
    → Aero Survey Core
    → unlock of the full Air Age recipe branch
    → distant Spore, ocean and civilization operations become practical

The final boss and structure are selected only after the adapter audit. The
vertical slice must prove that a player cannot obtain the Aero Survey Core by any
ordinary route and that the aircraft unlock has no viable bypass.

## Acceptance tests

- Contract selection respects distance, biome/structure constraints and never
  picks an already-cleared target.
- Death, restart, unload/reload and player disconnect preserve state correctly.
- The arena remains dormant before activation and starts once, only in the
  selected target.
- Native boss AI, arena mechanics and drops work without duplicate spawns.
- Exactly one chapter artifact is awarded; source-closure audit finds no bypass.
- The artifact unlock survives loss/restart after deposit.
- No temporary chunk ticket remains after resolution.
- The route can be completed by walking, but aircraft reduces a measured
  expedition turnaround substantially without trivialising the encounter.
