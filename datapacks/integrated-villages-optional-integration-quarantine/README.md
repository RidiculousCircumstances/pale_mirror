# Integrated Villages optional-integration quarantine

Integrated Villages 1.3.3 ships template pools that reference entities from
optional mods even when those mods are absent. This pack replaces only the
affected Guard Villagers and Iron's Spells NPC pools with empty pools.

The village archetypes, buildings and vanilla villagers remain enabled. This is
an explicit compatibility policy, not a log filter: it prevents invalid entity
NBT from being selected during chunk generation.

Pinned targets:

- Minecraft 1.21.1 (`pack_format` 48)
- Integrated Villages 1.3.3
- Guard Villagers absent
- Iron's Spells 'n Spellbooks absent

Install this folder into the world's `datapacks/` directory before generating
new terrain. Re-audit these overrides whenever Integrated Villages is upgraded.
