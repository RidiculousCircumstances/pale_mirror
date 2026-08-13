# Quest presentation and operation journal

## Decision

**FTB Quests is the adopted client/server quest-journal layer for Far Frontier.**
It presents campaign chapters, operation briefings, durable player/team progress and
non-authoritative rewards. It is not the world director and does not replace the
planned `far_frontier_core` integration module.

The selected candidate is the official **FTB Quests 2101.1.30** NeoForge artifact
for Minecraft 1.21.1, released 2026-08-06. It is pinned only after Packwiz can
retrieve the CurseForge resolver metadata and its required FTB Library artifact;
no unverifiable hand-written download URL or hash is allowed. FTB XMod Compat and
KubeJS are deliberately *not* part of this decision.

## Authority boundary

| Component | Owns | Must not own |
|---|---|---|
| FTB Quests | Campaign UI, readable briefings, chapter graph, task display, ordinary turn-ins and visible player/team progress | Boss lifecycle, target coordinates, world safety, chunk loading, authoritative chapter artifacts or event spawning |
| `far_frontier_core` (planned) | Contract state, selected destination, activation validation, server-safe rewards, retry/recovery and world consequences | A second quest-book UI or a duplicate editable campaign tree |
| Ravents | Automatic ambient encounters and configured raid composition | Named contracts or their completion state |
| Third-party content mods | Their native mobs, structures, boss mechanics and drops | The global sequence of Far Frontier chapters |

The quest book may display a completed operation only after the core emits its
authoritative completion signal. Conversely, completing a generic FTB Quests task
must never create, awaken or clear a chapter boss.

## Campaign shape

The initial book has three kinds of content. It intentionally does **not** include
daily tasks, random kill quotas or a checklist for every installed mod.

1. **Chapters** — Frontier, Exploration, Industrial / Air Age, World Fronts and
   Endgame. These explain the intended order and reveal the next strategic goal.
2. **Operations** — rare named expeditions. A briefing may include a clue, cargo
   requirement, risk and expected extraction, while the core keeps the exact target
   and validation state.
3. **Field manual** — optional, non-rewarding guidance for Create logistics,
   Aeronautics, infection containment, civilization layers and encounter safety.

## NPC and settlement contracts

NPCs supply context; they do not require a fragile direct dialogue integration.

- A vanilla or Integrated Village can create a sealed cargo, recovery or protection
  contract at a nearby contract board / expedition table.
- Millénaire remains an independent simulation. Its settlement may be a source of
  trade, rumours or a manually accepted cultural contract, but its NPC AI and
  internal quest lifecycle are never patched through Villager Overhaul or FTB
  Quests.
- The FTB Quests page is the journal view. The physical board/table and the planned
  core validate issuance, delivery and failure conditions.

This makes Create trains useful for repeatable bulk routes and Aeronautics useful
for urgent, high-value or remote operations without turning settlements into MMO
quest hubs.

## First vertical slice

Before authoring the whole campaign, implement and test exactly one contract:

    settlement briefing in FTB Quests
    → sealed physical cargo
    → distant safe delivery target selected by the core
    → successful hand-off validated server-side
    → FTB Quests completion and one non-critical unlock

It must survive death, restart, disconnect and team ownership changes without
duplicating cargo or reward. A Cataclysm chapter boss is intentionally not part of
this first FTB Quests test; that requires the separate boss adapter described in
`docs/expedition-system.md`.

## Admission and test gates

1. Materialise the exact FTB Quests and FTB Library metadata through Packwiz.
2. Boot the full dedicated profile and a real NeoForge client with the pinned files.
3. Confirm individual and team progress persistence over restart.
4. Confirm the quest GUI does not collide with configured key bindings.
5. Run the vertical-slice delivery contract before enabling any chapter rewards.

No FTB quest-barrier teleportation, stage gating, currency system, KubeJS hook or
FTB XMod Compat integration is enabled by default. Each would require a separate
function, compatibility and performance justification.
