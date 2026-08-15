# Compatibility matrix — Minecraft 1.21.1 / NeoForge

Last verified: 2026-08-08.  The target loader is **NeoForge 21.1.248** (the current
`21.1.x` release line at verification time); Create: Aeronautics itself requires at
least NeoForge `21.1.228`. Every file listed below is a specific 1.21.1 NeoForge
artifact, not an inferred cross-loader match.  `CONFIRMED` means that an official
artifact and its dependency graph were verified; it does **not** yet claim that the
whole pack has completed a runtime smoke test.

## Decisions that affect the pack shape

1. **Integrated Dungeons Arise replaces original When Dungeons Arise.** The IDA
   author explicitly says that original WDA is mostly incompatible, not recommended,
   and that Structurify must not control IDA's generation frequency.  Therefore WDA
   is excluded from the core profile. IDA gets its own versioned vanilla datapack
   overrides for its `structure_set` files; Structurify controls every other
   structure set. This is the user-approved resolution of an otherwise blocking
   conflict.
2. **Enhanced Celestials is excluded.** Live play showed that independent Blood
   Moon timing adds unexplained global pressure beside Pale Mirror. Both EC2
   modules and their orphan libraries are absent from the default manifest.
3. **Chunk optimisation is server-profiled.** Lithium `0.15.4`, ModernFix `5.27.2`,
   Fast Noise `1.0.13` and FerriteCore `7.0.3` form the stable candidate. ScalableLux `0.1.0.1` is
   excluded because Sable `2.0.3` declares it incompatible. Exact
   Distant Horizons `3.2.0-b` runs on both sides under permanent PM cache-only control. C2ME
   `0.3.0-alpha.0.93` is disabled by default, uses eight workers when selected, and requires Java 22 plus the full
   worldgen/Create/Sable/restart gate.
4. **Alex's Caves and its Citadel are community ports.** They are feature-flagged
   (`alex-caves` packwiz option, enabled by default) and can be removed together
   without changing the rest of the pack. They must pass the dedicated terrain,
   save/reload and performance suite before being called release-ready.
5. **Create: Caliber is an evaluated candidate and is not distributed by the default pack.** Its only 1.21.1 build is
   a two-day-old beta. It is not part of the core progression or combat balance.
6. **Crimson Curse is bounded by Pale Mirror.** Crimson Curse `1.4.3.1` is a native
   NeoForge multi-loader artifact, but it has its own infection phases and automatic
   player-seeking raids. The versioned `crimson-curse-raids-disabled` datapack keeps
   infection/exploration content while disabling its automatic raids; PM remains
   the sole authoritative encounter director.
7. **Spore is local, not a second director.** Native NeoForge Spore `2.2.0j` is
   included with explicit structure-set and safety controls. It supplies rare
   contamination sites while Pale Mirror owns canonical threat progression.
8. **FTB Quests is the adopted campaign presentation layer.** The official
   NeoForge 1.21.1 `2101.1.30` artifact is selected for chapters, briefings and
   visible progress; it does not replace the planned authoritative expedition core.
   Its Packwiz materialisation is pending a resolver run that also pins FTB Library;
   no unverified hash or manually copied JAR is admitted.

## Core matrix

| Mod | Official page; pinned candidate | Release / MC / loader / side | Required dependencies (optional) | Origin; configs/datapacks; compatibility notes | Status |
|---|---|---|---|---|---|
| [Terralith](https://modrinth.com/mod/terralith) | `2.6.2`, [artifact](https://modrinth.com/mod/terralith/version/IY93YaEe) | 2026-06-09; 1.21.1; NeoForge; server required, client optional | Lithostitched | Official. `config/terralith.json`; cannot be removed from an existing world. Officially fully compatible with the **mod** build of Tectonic. | CONFIRMED |
| [Tectonic](https://modrinth.com/mod/tectonic) | `3.0.26-neoforge-21.1`, [artifact](https://modrinth.com/mod/tectonic/version/vNrkxC3z) | 2026-07-02; 1.21.1; NeoForge; server required, client optional | Lithostitched | Official. Config screen / config file. Use the mod builds together—do not mix Terralith datapack with normal Tectonic; standalone datapack users need Terratonic. It changes large-scale terrain, deep oceans and mountains, so structure tests are mandatory. | CONFIRMED |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | `3.2.0-b-1.21.1`, [artifact](https://modrinth.com/mod/distanthorizons/version/ZpKb4kZp) | 2026-07-07; 1.21.1; NeoForge; both | None | Official beta. Identical server/client build; PM disables the background importer and client generation requests. Normal chunk events populate/synchronise the server LOD cache; `PRE_EXISTING_ONLY` remains a dormant fail-safe. `SURFACE`, `FEATURES`, `INTERNAL_SERVER` and N-sized generation remain off. | WARNING |
| [Lithium](https://modrinth.com/mod/lithium/version/DDUrRVCA) + [ModernFix](https://modrinth.com/mod/modernfix/version/iYwIkyV5) | `0.15.4` + `5.27.2` | 1.21.1; NeoForge; server profile | None | Stable candidate for tick/chunk-access and memory/runtime overhead. Installed server-side only to avoid adding client mixins. | WARNING |
| [Fast Noise](https://modrinth.com/mod/zfastnoise/version/9vSFkDAr) | `1.0.13` | 1.21.1; NeoForge; server profile | None | Stable worldgen-noise candidate. Must preserve PM/Terralith/Tectonic/IDA structures across seed and restart gates. | WARNING |
| [FerriteCore](https://modrinth.com/mod/ferrite-core/version/x7kQWVju) | `7.0.3` | 1.21.1; NeoForge; both | None | Reduces memory retained by the large block-state/data-component registry; exact release also fixes its ModernFix dynamic-resources interaction. | WARNING |
| [ScalableLux](https://modrinth.com/mod/scalablelux/version/j10HNoNf) | `0.1.0.1` | 1.21.1; NeoForge; server | None | Rejected by actual Java 22 full-profile boot: Sable 2.0.3 declares all ScalableLux versions incompatible and NeoForge fails before world access. | BLOCKED |
| [C2ME NeoForge](https://modrinth.com/mod/c2me-neoforge/version/KmfiVd28) | `0.3.0-alpha.0.93` | 1.21.1; NeoForge; server-only; Java 22 runtime | None | Alpha experimental profile, disabled by default and pinned to eight workers when selected. Promote only after ≥25% throughput or ≥20% p95 gain and every correctness gate; otherwise retain the stable profile. | WARNING |
| [Create](https://modrinth.com/mod/create) | `6.0.10+mc1.21.1`, [artifact](https://modrinth.com/mod/create/version/UjX6dr61) | 2026-04-21; 1.21.1; NeoForge; server required, client optional | None | Official. Server/client configs and datapack recipes. Foundation for IDA, Integrated Villages, Sable/Aeronautics and optional Caliber. | CONFIRMED |
| [Sable](https://modrinth.com/mod/sable) | `2.0.3+mc1.21.1`, [artifact](https://modrinth.com/mod/sable/version/1L6XJqnY) | 2026-06-17; 1.21.1; NeoForge; both | Embedded Veil and Sable Companion (Create, ImGuiMC optional) | Official. Physics config. New physics/rendering layer: test isolated first, then with DH; do not add shader or optimisation mods until its flight/restart tests pass. | WARNING |
| [Create Aeronautics](https://modrinth.com/mod/create-aeronautics) | `1.3.0+mc1.21.1`, [artifact](https://modrinth.com/mod/create-aeronautics/version/w7zlLnea) | 2026-06-13; 1.21.1; NeoForge; both | Create; Sable | Official. Configs; bundled artifact. Requires NeoForge >= `21.1.228`. Officially reports Iris shader visual issues. The pack must validate assembly, chunk edges, save/reload and dedicated server before balance changes. | WARNING |
| [When Dungeons Arise](https://modrinth.com/mod/when-dungeons-arise) | `2.1.68` | 2025-10-26; 1.21.1; NeoForge; both | None | Official, but excluded. It conflicts conceptually and by the author's own FAQ with IDA; keeping both would duplicate/rework the same structures. | BLOCKED |
| [Integrated Dungeons Arise](https://modrinth.com/mod/integrated-dungeons-arise) | `2.1.1`, [artifact](https://modrinth.com/mod/integrated-dungeons-arise/version/mUchb4lO) | 2026-06-20; 1.21.1; NeoForge; server required, client optional | Create; Farmer's Delight; Amendments; Integrated API; Quark; Supplementaries (Better Archeology, BetterEnd, Cataclysm, Mowzie's, Guard Villagers and others optional) | Official. Datapack-style `structure_set`, processor and loot configuration. Replaces original WDA in this pack. `datapacks/ida-legacy-loot-fix` preserves its five affected tables while updating only obsolete nested loot-table fields; boot retest passes. **Do not let Structurify edit IDA sets**; maintain explicit IDA overrides instead. | CONFIRMED |
| [YUNG's Better Dungeons](https://modrinth.com/mod/yungs-better-dungeons) | `5.1.4`, [artifact](https://modrinth.com/mod/yungs-better-dungeons/version/D6aZn0Em) | 2024-12-01; 1.21.1; NeoForge; server | YUNG's API | Official. JSON/server config. Structurify has enhanced YUNG compatibility; structure-set IDs are dumped before tuning. | CONFIRMED |
| [YUNG's Better Strongholds](https://modrinth.com/mod/yungs-better-strongholds) | `5.1.3`, [artifact](https://modrinth.com/mod/yungs-better-strongholds/version/8U0dIfSM) | 2025-03-06; 1.21.1; NeoForge; server | YUNG's API | Official. Configurable worldgen. It replaces vanilla strongholds; locate and End-progression tests are required. | CONFIRMED |
| [Integrated Dungeons and Structures](https://modrinth.com/mod/idas) | `1.13.7+1.21.1-neoforge`, [artifact](https://modrinth.com/mod/idas/version/bpMwZSKf) | 2026-06-11; 1.21.1; NeoForge; both | Create; Integrated API; Quark; Supplementaries | Official. Optional Terralith integration is advertised. `datapacks/idas-optional-integration-quarantine` disables its three unavailable Ice and Fire / Ars Nouveau placements and provides valid neutral data for their unused tables/spawners. Retested boot has no IDAS missing-registry errors. Integrated API map-decoration logs and separate legacy `dungeons_arise` loot-format errors remain gates. | WARNING |
| [L_Ender's Cataclysm](https://modrinth.com/mod/l_enders-cataclysm) | `3.32`, [artifact](https://modrinth.com/mod/l_enders-cataclysm/version/695vQRhD) | 2026-07-03; 1.21.1; NeoForge; both | Curios API; Lionfish API | Official. Server/common configs. Major encounters only: spacing, biome/flatness and overlap checks are mandatory; no global HP multipliers are added. | CONFIRMED |
| [Structurify](https://modrinth.com/mod/structurify) | `2.0.30+mc1.21.1`, [artifact](https://modrinth.com/mod/structurify/version/aV5lNYVM) | 2026-07-31; 1.21.1; NeoForge; both | YACL | Official. `config/structurify.json`; `/structurify dump` discovers exact structures and sets. Controls spacing, separation, salt, frequency, biome and flatness checks. It controls all non-IDA sets. | CONFIRMED |
| [Alex's Caves — Unofficial Port](https://modrinth.com/mod/alexs-caves-(unofficial-port)) | `2.0.10`, [artifact](https://modrinth.com/mod/alexs-caves-(unofficial-port)/version/pC1MYhqR) | 2026-04-25; 1.21.1; NeoForge; both | Citadel — Unofficial Port | **Unofficial**, community maintained by Raguto. Configurable cave generation. No native 1.21.1 Tectonic compatibility patch is published; historical and current community reports identify problematic cave terrain, notably Abyssal Chasm. Remove together with Citadel if the feature flag is off. | WARNING |
| [Naturalist](https://modrinth.com/mod/naturalist) | `2.0.2+1.21.1-neoforge`, [artifact](https://modrinth.com/mod/naturalist/version/5VOUtmLM) | 2026-07-28; 1.21.1; NeoForge; both | None | Official. Spawn configs/datapack hooks where exposed. Latest stable release is recent; audit spawn weights against vanilla during ecology testing. | WARNING |
| [Mowzie's Mobs](https://modrinth.com/mod/mowzies-mobs) | `1.8.2`, [artifact](https://modrinth.com/mod/mowzies-mobs/version/xgAXTl17) | 2026-03-15; 1.21.1; NeoForge; both | GeckoLib | Official. Configs. Treat as rare special encounters; test Better Combat animations, shields and boss scripts. | CONFIRMED |
| [Born in Chaos](https://modrinth.com/mod/borninchaos) | `1.7.6`, [artifact](https://modrinth.com/mod/borninchaos/version/ttcWWp3r) | 2026-06-17; 1.21.1; NeoForge; both | GeckoLib (JEI and Better Combat optional) | Official. Spawn configs. Better Combat integration exists; cap its spawn weights so it remains uncommon compared with vanilla hostile mobs. | CONFIRMED |
| [Enhanced Celestials 2: Core](https://modrinth.com/mod/enhanced-celestials-2-core) + [Default Lunar Events](https://modrinth.com/mod/enhanced-celestials-2-default-lunar-events) | both `2.0.0.0` | CorgiLib; Data Anchor | Removed with its dependencies: Blood Moon timing is not causally owned by PM. | DISABLED |
| [Ravents](https://modrinth.com/mod/ravents) | `3.0`, [artifact](https://modrinth.com/mod/ravents/version/nTTlZTlJ) | 2026-06-08; 1.21.1; NeoForge; both | Architectury API (Pehkui optional) | Retained as a dormant future PM adapter. Default config contains two inert generated templates, zero raids and zero events. | CONFIRMED |
| [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | `2101.1.30`, [official file](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/8589821) | 2026-08-06; 1.21.1; NeoForge; both | FTB Library (FTB XMod Compat only if later KubeJS/JEI/stages integration is deliberately adopted) | Official. Chosen solely as the campaign journal and editable quest graph; its chapter UI may show core-authoritative contracts but must not control boss lifecycle, selected locations or unique artifacts. The file is verified on the official page, but Packwiz metadata, FTB Library closure and full-profile boot have not yet been materialised; no quest content is enabled. | WARNING |
| [Crimson Curse: Infection](https://modrinth.com/datapack/crimson_curse) | `1.4.3.1+mod`, [artifact](https://modrinth.com/datapack/crimson_curse/version/RZ4LrIs5) | 2026-05-15; 1.21/1.21.1; NeoForge; both | Entity Model Features, Entity Texture Features, Sodium NeoForge and Polytone (client) | Official multi-loader JAR; its NeoForge descriptor has no required server dependency. Adds infection mass/points/phases, infection biomes, entities and its own raids. `crimson-curse-raids-disabled` preserves Ravents as director. Its optional Spore resources are active with the pinned Spore artifact. Dedicated server boot passes, but the upstream JAR retains one ignored invalid disabled-advancement-path log. Its custom-block infection, phase progression, DH/Sable renderer stack and server performance remain test gates. | WARNING |
| [Fungal Infection: Spore](https://modrinth.com/mod/fungal-infectionspore) | `2.2.0j`, [artifact](https://modrinth.com/mod/fungal-infectionspore/version/DwE3w8IX) | 2026-06-29; 1.21.1; NeoForge; both | NeoForge `>=21.1.212` only | Official. `config/spore-startup.toml` and `datapacks/far-frontier-spore-zones` restrict it to rare local contamination sites; Crimson's obsolete no-Spore quarantine is removed so its optional Spore resources load. The full dedicated profile boots, but actual density, hivemind lifecycle, combat, DH/Sable client rendering and MSPT remain gates. | WARNING |
| [Better Combat](https://modrinth.com/mod/better-combat) | `2.4.0+1.21.1-neoforge`, [artifact](https://modrinth.com/mod/better-combat/version/VhIOvcXP) | 2026-07-14; 1.21.1; NeoForge; both | playerAnimator; Cloth Config API | Official. Config and per-weapon attributes. Validate animations/reach/shields with Simply Swords, Cataclysm, Mowzie and Born in Chaos; no blanket damage changes. | CONFIRMED |
| [Simply Swords](https://modrinth.com/mod/simply-swords) | `1.63.0-1.21.1`, [artifact](https://modrinth.com/mod/simply-swords/version/eREhvVpQ) | 2026-03-20; 1.21.1; NeoForge; both | Architectury API; Fzzy Config; Simply Tooltips (Better Combat optional) | Official. Config/datapack settings. Keep high-tier weapons exploration/loot-gated where its native configuration permits; otherwise document unchanged recipes rather than silently patching them. | CONFIRMED |
| [Hostile Tactics](https://modrinth.com/mod/hostile-tactics) | Modrinth label `0.9.1`, file `0.10.1`, [artifact](https://modrinth.com/mod/hostile-tactics/version/p6MKUmpy) | 2026-07-10; 1.21.1; NeoForge; **server only** | None | Official. Server config and debug commands. Its declared intent is tactical AI without artificial HP/damage increases. The project/version label conflict must be resolved by artifact hash and in-game manifest during boot. Disable or tightly constrain any griefing setting. | WARNING |
| [Villager Overhaul](https://modrinth.com/mod/villager-overhaul) | `3.10.17.16`, [artifact](https://modrinth.com/mod/villager-overhaul/version/Z9nioxrI) | 2026-06-12; 1.21.1; NeoForge; both | EZ Emerald Pouch; Timeline; EZ Actions | Official. Hot-reloadable server config. Operates on vanilla villagers; test guards, farming, professions and pathing in PM-authored settlements. Do not attempt to modify Millénaire NPCs. | CONFIRMED |
| [Integrated Villages](https://modrinth.com/mod/integrated-villages) | `1.3.3+1.21.1-neoforge`, [artifact](https://modrinth.com/mod/integrated-villages/version/B5BcNY1z) | 2026-05-28; 1.21.1; NeoForge; server required, client optional | Integrated API; Create; Supplementaries (Farmer's Delight, Guard Villagers, Quark and many others optional/recommended) | Installed as a private asset/dependency source. Structurify explicitly disables both `regular_villages` and `air_villages`; PM-authored settlements are the only generated living settlements. Villager Overhaul and unrelated standalone POIs remain enabled. | WARNING |
| [Millénaire](https://www.millenaire.org/downloads) | `9.0.0-beta.2`, [official download artifact](https://www.millenaire.org/api/downloads/9.0.0-beta.2~millenaire-9.0.0-beta.2.jar) | 2026-07-25; 1.21.1; NeoForge; both | None published | Compatibility target only. Removed from the default client/server manifest after village chunk loading produced a 32-second server-thread stall and client timeout. Re-enable only in an isolated profile with an explicit performance gate. | DISABLED |
| [Create: Caliber](https://modrinth.com/mod/create-caliber) | `0.2.0`, [artifact](https://modrinth.com/mod/create-caliber/version/uEGLkjec) | 2026-07-10; 1.21.1; NeoForge; both | Create | Official, MIT, explicitly beta/WIP. It advertises kinetic firearms, automated ammunition and Create components, but no mature compatibility/balance evidence exists. Excluded from the default manifest; evaluate it only in a separate profile before any promotion. | WARNING |

## Pinned dependency closure

These are functional dependencies, not unreviewed gameplay additions. Their role is recorded
here because IDA/IDAS/Integrated Villages necessarily bring parts of the Integrated ecosystem.
Each is pinned in `mods/` during the implementation stage and validated as part of the same
resolver graph.

| Dependency | Candidate version / date | Needed by | Notes |
|---|---|---|---|
| [Lithostitched](https://modrinth.com/mod/lithostitched) | `1.7.13-neoforge-21.1`, 2026-07-06 | Terralith, Tectonic | Required worldgen library. |
| [YACL](https://modrinth.com/mod/yacl) | `3.8.2+1.21.1-neoforge`, 2026-01-09 | Structurify | Config GUI/library. |
| [YUNG's API](https://modrinth.com/mod/yungs-api) | `5.1.6`, 2025-07-01 | YUNG's mods | Worldgen library. |
| [Integrated API](https://modrinth.com/mod/integrated-api) | `1.7.3+1.21.1-neoforge`, 2026-04-08 | IDA, IDAS, Integrated Villages | Required data/worldgen integration layer. |
| [Supplementaries](https://modrinth.com/mod/supplementaries) + [Moonlight](https://modrinth.com/mod/moonlight) | `3.8.8`, 2026-08-05 + `3.3.2`, 2026-08-04 | IDA, IDAS, Integrated Villages, Amendments | Required blocks/decor used by generated structures; Moonlight is its library. |
| [Quark](https://modrinth.com/mod/quark) + [Zeta](https://modrinth.com/mod/zeta) | `4.1-482`, 2026-07-12 + `1.1-40`, 2026-04-24 | IDA, IDAS; IV recommended | Required blocks/logic used by structures; audit module toggles without removing referenced blocks. |
| [Farmer's Delight](https://modrinth.com/mod/farmers-delight) + [Amendments](https://modrinth.com/mod/amendments) | `1.3.2`, 2026-05-13 + `2.1.7`, 2026-07-11 | IDA | IDA-required structural content; Amendments uses Moonlight. |
| [Curios](https://modrinth.com/mod/curios) + [Lionfish API](https://modrinth.com/mod/lionfish-api) | `9.5.1`, 2025-05-14 + `3.1`, 2026-06-30 | Cataclysm | Cataclysm API dependencies. |
| [GeckoLib](https://modrinth.com/mod/geckolib) | `4.9.2`, 2026-07-01 | Mowzie's, Born in Chaos | Entity animation library. |
| [Architectury](https://modrinth.com/mod/architectury-api) | `13.0.11`, 2026-07-23 | Ravents, Simply Swords | API. |
| [playerAnimator](https://modrinth.com/mod/playeranimator) + [Cloth Config](https://modrinth.com/mod/cloth-config) | `2.0.4` beta, 2025-12-28 + `15.0.140`, 2024-09-16 | Better Combat | Animation/config dependencies; playerAnimator beta is a test focus. |
| [Fzzy Config](https://modrinth.com/mod/fzzy-config) + [Simply Tooltips](https://modrinth.com/mod/simply-tooltips) | `0.7.6`, 2026-02-03 + `0.1.3`, 2026-03-06 | Simply Swords | Configuration and client tooltip dependency. |
| [EZ Emerald Pouch](https://modrinth.com/mod/ez-emerald-pouch), [Timeline](https://modrinth.com/mod/rpg-timeline), [EZ Actions](https://modrinth.com/mod/ez-actions) | `1.0.3.3`, 2026-04-24; `2.0.4.1`, 2026-02-25; `2.0.3.5`, 2026-04-22 | Villager Overhaul | Required by the selected newest Villager Overhaul artifact. |
| [Citadel — Unofficial Port](https://modrinth.com/mod/citadel-(1.21.1-port)) | `2.7.6`, 2026-04-25 | Alex's Caves port | Must exactly match the Alex's Caves port release line; feature-flagged with it. |
| [Entity Model Features](https://modrinth.com/mod/entity-model-features) + [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) | `3.2.4-neoforge-1.21`, 2026-05-09 + `7.1-neoforge-1.21`, 2026-04-15 | Crimson Curse client assets | Required client entity-model/texture support for the pinned Crimson Curse artifact. |
| [Iris](https://modrinth.com/mod/iris) + [Sodium NeoForge](https://modrinth.com/mod/sodium) + [Polytone](https://modrinth.com/mod/polytone) | `1.8.14-beta.1+mc1.21.1`, 2026-06-13 + `0.8.13-beta.2+mc1.21.1`, 2026-08-07 + `3.11.1-neoforge`, 2026-08-04 | Shader support; Crimson Curse client assets | Iris 1.8.14 is the Sodium 0.8 backport. Iris 1.8.12 must not be combined with Sodium 0.8 because its mixins target the removed `SodiumGameOptions` API. The beta renderer pair must be tested explicitly with Distant Horizons, Sable, Aeronautics and Veil. |
| [Pehkui](https://modrinth.com/mod/pehkui) and [JEI](https://modrinth.com/mod/jei) | `3.8.3`, 2024-06-19; `19.43.0.393` beta, 2026-08-03 | Ravents / Born in Chaos optional | Not in the minimal server core. JEI may be added as a separately documented client UX profile only after boot; Pehkui only if Ravents scaling is deliberately used. |

## Confirmed risks and gates

| Risk | Evidence / impact | Required gate or fallback |
|---|---|---|
| IDA vs original WDA vs Structurify | IDA's official FAQ rejects original WDA and Structurify frequency control. | Core excludes WDA; IDA spacing is an explicit datapack; Structurify controls all other sets. |
| Millénaire 9 beta | Fresh rewrite; official site asks for a fresh world and backup. | Keep in core only after dedicated multi-seed, economy, chunk-load and reload pass. If it corrupts or destabilises the server, ship a documented `no-millenaire` server profile rather than silently replacing it. |
| Alex's Caves port + Tectonic | Unofficial port; no 1.21.1 native compatibility patch; known cave/ocean terrain concern. | Test each cave biome over several seeds, 10+ km pregeneration and restart. Fallback is the `alex-caves` option off (also removes unofficial Citadel). |
| DH + Sable/Aeronautics | DH is beta; public reports include freezes with Sable and Aeronautics has shader visual issues. | Baseline without shaders/performance mods; test LO/MD/HI LOD profiles and flight separately. No shader stack is a release requirement. |
| Independent event directors | Ravents raid waves and EC2 lunar events looked like PM consequences without participating in PM state. | EC2 is excluded and Ravents has no automatic raids/events. Any future adapter call must originate from PM. |
| Hostile Tactics release label | Modrinth version label and JAR filename disagree. | Pin Modrinth hash, record in-game manifest, boot server-only first, and disable griefing by default. |
| Integrated ecosystem breadth | IDA/IDAS/IV require Create/Quark/Supplementaries and, for IDA, FD/Amendments. | These are justified dependencies, not elective expansion. Audit their optional modules and prevent recipes/loot from creating a second uncontrolled progression. |
| IDAS optional-integration data | Initial core boot parsed 10 IDAS loot tables and two spawner pools with absent Ice and Fire / Ars Nouveau content. | Resolved in `idas-optional-integration-quarantine`; 2026-08-08 retest auto-enabled it and eliminated these missing-registry errors. Do not add either unrelated mod. |
| IDA legacy loot data | Five `dungeons_arise` tables used obsolete nested `minecraft:loot_table.name` entries. | Resolved in `ida-legacy-loot-fix`; it copies upstream tables verbatim except `name` → `value`, and the 2026-08-08 boot retest has no remaining IDA loot parse error. |
| Caliber | `0.2.0` is beta/WIP and no balance history exists. | Excluded from the default manifest; preliminary decision **EVALUATION ONLY**, never core until measured ammo-chain and boss-TTK tests pass. |
| Crimson Curse + Spore + PM | Crimson and Spore both have upstream autonomous escalation that could compete with PM. | Crimson automatic raids remain disabled. Spore is capped, natural-spawned only in mushroom fields, has no conversion/spread/chunk-loading/raids, and its structure sets use an explicit sparse datapack. |

## Sources and verification method

Primary metadata came from each linked official Modrinth/CurseForge/project page and its
version artifact. Key compatibility assertions are from the official
[Terralith page](https://modrinth.com/mod/terralith), official
[Tectonic page](https://modrinth.com/mod/tectonic), official
[IDA page](https://modrinth.com/mod/integrated-dungeons-arise), official
[Structurify page](https://modrinth.com/mod/structurify), official
[Ravents page](https://modrinth.com/mod/ravents), and the
[Millénaire downloads page](https://www.millenaire.org/downloads).
The lockfile records resolver hashes (SHA-512 from Modrinth/CurseForge, SHA-256 for
the directly published Millénaire artifact); no manually copied JAR is accepted.
