# Ravents progression design

Ravents `3.0` is the sole world-progression director. Its generated JSON model has
been verified to be per-player and supports durable counters/conditions such as
dimension, biome, structure/POI, items and block actions. It does not publish a
dependable advancement trigger, global scheduler, probability pool, lunar hook or
settlement-context API. Tier 0 below is an enabled, schema-validated baseline;
tiers 1–3 remain a design contract until their exact trigger and encounter tests are
validated.

| Tier | Durable trigger to validate | Event posture |
|---|---|---|
| 0 — Frontier | New player / no milestone | Vanilla hostile mobs + tactical AI; rare Born in Chaos; weak, infrequent events; no mass invasion. |
| 1 — Exploration / Nether | First Nether visit or equivalent native Ravents dimension condition | More varied groups and small raids; EC2 can be more noticeable, not guaranteed. |
| 2 — Industrial / Air Age | A documented placed/obtained Create or Aeronautics component, only if Ravents can detect it reliably; otherwise a stable exploration milestone | Elite mixes and distant expedition pressure; no day-count escalation. |
| 3 — Endgame | End visit / Dragon-related durable condition / verified Cataclysm milestone | Rare dangerous mixed events and possible heavy Blood Moon conditions, never continuous spam. |

## Enabled Tier 0 — Frontier

`config/Ravents/raids/frontier_first_contact.json` defines one deliberately small
encounter, **Frontier First Contact**:

- Its trigger is per player: `2` total Ravents-counted mob kills and `100` blocks
  walked. `mobKillsSpecific` is empty, so the current configuration does not limit
  kills to a particular mob type; it has no elapsed-day condition.
- `singleUse=true` records completion in Ravents saved data, so it cannot become a
  recurring ambient raid.
- Wave 1 is two `frontier_raider` zombies; wave 2 is one `frontier_scout` skeleton.
  They spawn at `24` blocks, have normal vanilla health, and award neither custom
  drops nor experience. This is a tactical composition test, not numerical scaling.
- The profile has no files under `config/Ravents/events/`. Ravents therefore has no
  automatic vanilla-spawn replacement or cloning enabled.

Ravents itself creates `division_zombie.json` and `division_skeleton.json` when they
are absent. They are committed unchanged so first boot is reproducible. They are
only mob templates: no enabled raid/event references either ID, so they cannot spawn
in this pack. A valid baseline therefore reports **4 mobs, 1 raid, 0 events**.

The next test is to execute this raid with a real client, including shields and
Better Combat, then inspect the saved completion state and a server restart.

## Event constraints

- No global HP or damage multiplier.
- Events require cooldowns/probability only where Ravents' actual schema provides
  them; otherwise they remain disabled rather than simulated with command hacks.
- A Blood Moon is a separate EC2 event. Combined Ravents events are manual test
  prototypes until a supported integration point exists.
- Village, Millénaire, caravan and siege context is not assumed. It is enabled only
  after reliable settlement detection exists; ordinary villages must survive normal
  nights.

Future tiers must name every JSON file, counter, threshold, cooldown and test result
here before they are enabled.
