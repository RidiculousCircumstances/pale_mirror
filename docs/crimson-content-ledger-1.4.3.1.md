# Crimson content ledger — internal prototype

Target: `mr_crimson_curse 1.4.3.1`, SHA-512 recorded in
[`crimson-audit-1.4.3.1.md`](crimson-audit-1.4.3.1.md).

Crimson is marked **ARR** by its publisher. The files and values below are an
internal compatibility prototype, not a redistribution grant. A public Pale
Mirror release containing derived Crimson functions, models, textures, or
tables is blocked until the author grants written permission.

## Approved local forms

| PM profile | Vanilla base | Upstream reference | PM execution policy | Test |
| --- | --- | --- | --- | --- |
| `crimsonified_human` | zombie | `crimsonified_mob` zombie branch | vanilla AI, local stats/loot | GameTest |
| `crimsonified_villager` | zombie villager | `crimsonified_mob` villager branch | vanilla AI, local stats/loot | GameTest |
| `crimsonified_husk` | husk | `crimsonified_mob` husk branch | vanilla AI, local stats/loot | GameTest |
| `crimsonified_skeleton` | skeleton | `crimsonified_mob` skeleton branch | vanilla AI, local stats/loot | GameTest |
| `crimsonified_drowned` | drowned | `crimsonified_drowned_spawn` non-trident branch | vanilla AI, local stats/loot | GameTest |
| `crimsonified_bogged` | bogged | `crimsonified_mob` bogged branch | vanilla AI, local stats/loot | GameTest |
| `crimsonified_wither_skeleton` | wither skeleton | `crimsonified_mob` wither-skeleton branch | vanilla AI, local stats/loot | GameTest |

Every initializer is confined to `pale_mirror:crimson/v1431/*` and must omit:

- `Global` scores, `Mass`, `Points`, phases and raids;
- Crimson teams and unrestricted entity scans;
- infection spread, arbitrary block conversion, explosions and worldgen;
- calls into `crimson_curse:tick`, actor passive functions or player functions.

## Next audit queue

Decayed variants, elite combat families, Bloodlinks/Nodes and boss objects
remain unapproved. Each requires its own row with local dependency graph,
destructive-operation policy, persistent identity, cleanup path, GameTest and
restart test before becoming available to a PM encounter profile.
