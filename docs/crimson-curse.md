# Crimson Curse integration

## Pinned feature

Crimson Curse: Infection is pinned to `1.4.3.1+mod` for Minecraft `1.21.1`, native
NeoForge, Modrinth version `RZ4LrIs5` (published 2026-05-15). The artifact is a
multi-loader JAR with an explicit `META-INF/neoforge.mods.toml` descriptor and no
required server-side NeoForge dependencies. It requires client rendering support:
Entity Model Features, Entity Texture Features, Sodium NeoForge and Polytone.

It adds a global infection with mass, points and phases; infected blocks/biomes,
creatures, nodes and major threats are exploration content. Better Combat is already
part of this pack and is recommended by the author, but no compatibility claim is
made until combat tests are complete.

## Director boundary

Pale Mirror is Far Frontier's authoritative world-encounter director. Crimson
Curse's automatic raids seek players and create new infected areas, so enabling them
by default would establish a competing global progression/event system.

`datapacks/crimson-curse-raids-disabled` disables only those automatic raids by
setting Crimson Curse's documented `Global Raids_Enabled` score to `0` on world
load. It deliberately leaves infection phases, terrain/ecology effects, entities,
nodes and player counterplay untouched.

Spore 2.2.0j is now a pinned core artifact. The former
crimson-curse-spore-compat-quarantine has been removed because it would replace
the exact optional Crimson resources that make this integration work. The combined
NeoForge server boot reaches Done with no missing spore tag or effect error.
Spore's own world impact is separately restricted in docs/spore-integration.md; it
does not gain authority over global progression.

The pinned upstream JAR still logs one non-fatal invalid-resource-path warning for
`advancement/technical/DISABLED_purified_rapier_player_killed_entity.json`. It is
ignored by Minecraft and the server reaches `Done`; no functional resource has been
replaced to conceal that upstream packaging defect.

## Required tests before promoting from WARNING

- Dedicated server boot with the Spore profile; ensure no missing objective,
  function-order, Spore-tag or Spore-effect error.
- Client boot with Crimson Curse's declared client asset dependencies.
- Fresh-world and existing-world infection generation, reload and 10 km travel.
- Confirm automatic Crimson raids remain disabled while PM-controlled threat actors work.
- Measure MSPT/heap during infection nodes, then with Create machinery and a
  Millénaire settlement loaded.
- Evaluate whether infection phase gains need a future documented cap; do not add
  command hacks or another global difficulty mod meanwhile.
