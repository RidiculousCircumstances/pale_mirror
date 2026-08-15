# Pale Mirror Visuals asset provenance

This private, non-distributed build embeds selected structure-template derivatives for the authored frontier grammar.
They are not a dependency or a claim of ownership, and they must be removed or relicensed before any distribution.

| Embedded family | Source | Exact source version | Original namespace | Use |
|---|---|---|---|---|
| Cabin/clockwork/marketstead structures | Integrated Villages | `1.3.3+1.21.1-neoforge` | `integrated_villages` | Remapped private NBT modules for civic, housing, trade, health, food, freight, industry and above-ground MineSite slots |
| PM frontier-industrial context kit | Pale Mirror | schema v40 generator | `pale_mirror_visuals` | Reproducible foundations, roads, yards, portal frame, loading context and the first dormant Red Valley foundation stage |
| Ancient mine structures | Integrated Dungeons and Structures | `1.13.7+1.21.1-neoforge` | `idas` | Curated underground gallery and controller machinery references |
| Mining complex structures | When Dungeons Arise | `2.1.1-1.21.1` | `dungeons_arise` | Curated underground controller chamber reference |
| Underground mining outposts | Terralith | `2.6.2-1.21.1-neoforge` | `terralith` | Curated underground entrance-adit reference |
| Threat Heart texture | OpenAI image generation, project-directed | generated 2026-08-12 | n/a | GeckoLib entity texture |

Source archive checksum (SHA-512):

`50d119a972c8813b7c6d54db828a5130f342ff8ec2728bf3c73057d46b1d575d00f37ed76a20c5dbcc84cb74970961ebf31065818f16f49b6c7a297605fba161`

`idas`: `916b60a5843e38316f12f5f1c6a4e737364f12ad2afb642e0f43f4e98135858f82af87ce0f262cdbc97dcbc1e0a31822a54af73278747f417fc33ae35cfed18c`

`dungeons_arise`: `c33b1f575136494585ce3eed956f129841d4a012b004b5821e771d340a6a68996abb41ff4b16841c48ae222571100a04bdae8c643bf9ca92a7455a270c99f472`

`terralith`: `35298f1682567f63dc16658b04cee5498b30819f1c05f9712b4480d7f5eb17059db3b13ab14f81a05fe257149d11ced2cce2030d3727c1747edd8657c53e2a85`

Mine modules are copied only for this private pack. Their runtime compiler
ignores imported entity lists, removes spawners, explosives, structure
machinery, creative power and unscoped external controllers, and fails closed
on invalid states or missing dependencies unless a specific curator-approved
substitution exists. Architectural materials, local Create machinery, finite
container contents and block-entity NBT are preserved as one-shot authored
state; coordinates are rewritten and source-world links are stripped. The
module is not periodically rebuilt or refilled after first materialization.
Per-module embedded SHA-256 values are enforced by `AuthoredAssetCatalog`.

The curated-frontier revision replaces the five generated surface boxes with
private Integrated Villages `clockwork_village` and `marketstead_village`
buildings. Exact source entries are: `clockwork_village_toolsmith` for the
portal/hoist works; `marketstead_village_botanist` for the crew office;
`marketstead_village_mason` for processing; `marketstead_village_engineer` for
power; and `marketstead_village_stables` for loading. The same coherent source
kit supplies the staged Red Valley shell, machinery and commissioning forms.
PM still authors terrain integration, foundations, circulation, semantic
ports, portal frame, yards and safety details. The pinned import is reproducible
through `scripts/import-curated-frontier-assets.sh`; the old deterministic
generator is restricted to the dormant Red Valley context foundation and can
no longer overwrite active surface buildings. The underground adit, gallery
and controller modules pass through the same sanitizer.

The generated Threat Heart prompt requested a square Minecraft-style infected organic texture with a burgundy/crimson,
purple and sparse ember-orange palette, no text, logo, watermark or scene.
