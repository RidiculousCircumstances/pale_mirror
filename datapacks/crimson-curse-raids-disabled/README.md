# Crimson Curse raid policy

Crimson Curse `1.4.3.1` includes its own phase/mass progression and automatic raids.
Far Frontier keeps its infection, biomes, mobs and exploration content, but leaves
world-level encounter direction to Ravents. This datapack disables Crimson Curse
automatic raids every time a world is loaded.

The one-tick schedule ensures Crimson Curse has created its scoreboard objectives
before the setting is applied. It does not tick continuously and does not alter
infection mass, phases, blocks, entities, loot or recipes.

An administrator can experiment during a running session with:

```mcfunction
/function cc_config:enable_raids
```

That is a temporary, unsupported experiment: the next server reload/restart reapplies
the pack policy. Remove this datapack only after a documented Ravents/Crimson Curse
combined-event balance and performance test.
