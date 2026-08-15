# Spore integration

## Role boundary

Fungal Infection: Spore 2.2.0j is a native NeoForge 1.21.1 artifact, pinned to
Modrinth version DwE3w8IX (2026-06-29). It has no required mod dependency beyond
NeoForge >=21.1.212; Far Frontier's 21.1.248 satisfies that range.

It is not a second world director:

| System | Far Frontier role |
|---|---|
| Crimson Curse | PM-controlled physical infection provider; its automatic raids remain disabled. |
| Pale Mirror | Canonical threat state, activation, escalation and story ownership. |
| Ravents | Dormant future materialization adapter with no configured raids/events. |
| Spore | Local biological contamination sites: infected ecology, hivemind encounters and structure-specific expeditions. |

Crimson Curse's now-present optional Spore resources are no longer quarantined.
The combined dedicated-server profile reaches Done; this confirms resource parsing
and registry compatibility, not combat or performance balance.

## Conservative policy

The Spore startup configuration deliberately changes only global-safety and
placement controls:

- natural Spore spawns are limited to minecraft:mushroom_fields, with an
  independent cap of 16;
- vanilla-to-infected conversion and infection on player death are disabled;
- hives cannot dispatch Vigil raids, force-load chunks, build a wall, spread
  madness, or advance the global Proto World Modifier;
- mound block/foliage spread, scents, Scamper mound creation, tendril targeting
  and the two configured explosive block-destruction paths are disabled;
- block-breaking hardnesses are zero and higher block-breaking AI is disabled;
- per-area infected caps are much lower than upstream defaults.

Thus an existing location can remain dangerous and biologically distinct, but it
cannot silently become a base-destroying or world-wide progression system.

The Far Frontier Spore zones datapack retains Spore's thirteen authored structures,
but changes each stock structure set from 3–48 chunk spacing to a 4096-chunk
random-spread grid with 3072 chunk separation. The combined expected density is
roughly one candidate per 18 km before biome filtering, not a POI every few
hundred blocks. Structurify is not used for these IDs: the explicit datapack is the
single owner of Spore structure placement.

## What is intentionally not promised

There is no verified public hook that can enable a vanilla worldgen structure set
from mutable PM story state. Therefore the current profile makes sites
geographically rare from the first generated chunk; it does not claim a false
tier-gate.

The future Tier 2 proposal is to gate activation/access to discovered sites by a
documented integration only if a stable API or minimal server-side implementation
is available. It must not use brittle repeating command hacks. Until then, Spore
remains a rare exploration layer and Pale Mirror remains the progression authority.

## Remaining gates

- Fresh-world 10+ km searches across several seeds: verify actual site density and
  terrain/structure placement with Tectonic and Terralith.
- Hivemind formation, save/reload and unload/reload: confirm no chunk ticket,
  long-range raid or block-spread regression.
- Player combat and Better Combat/shields; infected, elite and calamity encounters
  must be measured rather than balanced through global HP multipliers.
- Client boot and rendering with Sodium, Polytone, EMF/ETF, Distant Horizons and
  Sable.
- MSPT/heap measurement at an active Spore structure with Create and Millénaire.
