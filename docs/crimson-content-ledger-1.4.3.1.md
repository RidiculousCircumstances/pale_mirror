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
| `decayed_human` | zombie | `decayed_mob` zombie branch | vanilla AI, local stats/loot | GameTest |
| `decayed_villager` | zombie villager | `decayed_mob` villager branch | vanilla AI, local stats/loot | GameTest |
| `decayed_husk` | husk | `decayed_mob` husk branch | vanilla AI, local stats/loot | GameTest |
| `decayed_skeleton` | skeleton | `decayed_mob` skeleton branch | vanilla AI, local stats/loot | GameTest |
| `decayed_drowned` | drowned | `decayed_drowned_spawn` non-trident branch | vanilla AI, local stats/loot | GameTest |
| `decayed_bogged` | bogged | `decayed_mob` bogged branch | vanilla AI, local stats/loot | GameTest |
| `decayed_wither_skeleton` | wither skeleton | `decayed_mob` wither-skeleton branch | vanilla AI, local stats/loot | GameTest |
| `rusher` | ravager | `rusher_summon` | PM-owned 7–11 block dash; local stats/loot | GameTest |
| `raptor` | zombie | `raptor_summon` | PM-owned local invisibility and target aura; no door breaking | GameTest |
| `juggernaut` | zombie | `juggernaut` local form | vanilla melee, no door breaking; PM site leash | siege GameTest |
| `knight` | zombie | `knight` local form | vanilla melee, no door breaking; PM site leash | siege GameTest |
| `mangler` | ravager | `mangler` local form | no AI; PM bounded dash, never terrain griefing | siege GameTest |
| `pummeler` | ghast | `pummeler` local form | no AI; PM local non-griefing pulse | siege GameTest |
| `kraken` | phantom | `kraken` local form | no AI; PM local close-range grasp | siege GameTest |
| `osiris` | zombie | `osiris` local form | PM local health phase; no upstream brain/raid | siege GameTest |
| `bloodlink_i` | wither skeleton | Bloodlink I | no AI; registered PM gate and local aura | siege GameTest |
| `bloodlink_ii` | wither skeleton | Bloodlink II | no AI; registered PM gate and local aura | siege GameTest |
| `bloodlink_iii` | wither skeleton | Bloodlink III | no AI; registered PM gate and local aura | siege GameTest |

Every initializer is confined to `pale_mirror:crimson/v1431/*` and must omit:

- `Global` scores, `Mass`, `Points`, phases and raids;
- Crimson teams and unrestricted entity scans;
- infection spread, arbitrary block conversion, explosions and worldgen;
- calls into `crimson_curse:tick`, actor passive functions or player functions.

## Visual asset boundary

For this private integration, visual data remains in the installed Crimson
client JAR. PM does not package copied models or textures. It supplies the
audited CEM-selecting names, model-carrier equipment, scale and Pummeler's
`item_display` book carrier (`custom_model_data = 5450230`) only. The pinned
resource contract is checked in Gradle and the Pummeler carrier is checked in
a real NeoForge GameTest.

PM additionally supplies Raptor's limb/body frames, Bloodlink's five approved
stage frames, and an invulnerable no-AI Osiris Brain visual passenger. Rusher
and Mangler receive a short-lived PM marker passenger only while dashing so
their CEM `is_ridden` pose is active. These presentation children carry PM
visual provenance and are always discarded when their registered parent is
removed or dies. They cannot receive Crimson tags, teams, scoreboards or AI.

## PM siege constraints

Nodes are not derived Crimson blocks: they are PM-owned Sea Lanterns constrained
to the four template mutable cells and restored through the normal provenance
cleanup path. Boss and Bloodlink UUIDs are saved only as PM `SiegePartRef`
records. The supported sequence is fixed to Nodes → one deterministic boss →
Bloodlink I/II/III; no upstream Bloodlink graph, raid, global score, world scan
or terrain operation is imported.
