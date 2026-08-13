# Pale Mirror Visuals asset provenance

This private, non-distributed build embeds selected structure-template derivatives for the authored frontier grammar.
They are not a dependency or a claim of ownership, and they must be removed or relicensed before any distribution.

| Embedded family | Source | Exact source version | Original namespace | Use |
|---|---|---|---|---|
| Cabin/clockwork structures | Integrated Villages | `1.3.3+1.21.1-neoforge` | `integrated_villages` | Remapped private NBT modules for civic, housing, workshop and above-ground frontier-industrial MineSite slots |
| Ancient mine structures | Integrated Dungeons and Structures | `1.13.7+1.21.1-neoforge` | `idas` | Portal/hoist, gallery and staged machinery modules |
| Mining complex structures | When Dungeons Arise | `2.1.1-1.21.1` | `dungeons_arise` | Retained private underground reference modules; no dungeon shell is used as an above-ground building |
| Underground mining outposts | Terralith | `2.6.2-1.21.1-neoforge` | `terralith` | Entrance adit and crew support modules |
| Threat Heart texture | OpenAI image generation, project-directed | generated 2026-08-12 | n/a | GeckoLib entity texture |

Source archive checksum (SHA-512):

`50d119a972c8813b7c6d54db828a5130f342ff8ec2728bf3c73057d46b1d575d00f37ed76a20c5dbcc84cb74970961ebf31065818f16f49b6c7a297605fba161`

`idas`: `916b60a5843e38316f12f5f1c6a4e737364f12ad2afb642e0f43f4e98135858f82af87ce0f262cdbc97dcbc1e0a31822a54af73278747f417fc33ae35cfed18c`

`dungeons_arise`: `c33b1f575136494585ce3eed956f129841d4a012b004b5821e771d340a6a68996abb41ff4b16841c48ae222571100a04bdae8c643bf9ca92a7455a270c99f472`

`terralith`: `35298f1682567f63dc16658b04cee5498b30819f1c05f9712b4480d7f5eb17059db3b13ab14f81a05fe257149d11ced2cce2030d3727c1747edd8657c53e2a85`

Mine modules are copied only for this private pack. Their runtime compiler
ignores entities and block-entity NBT, removes spawners/explosives/structure
machinery and replaces inventories and ore/raw-resource cells with inert
materials. Per-module embedded SHA-256 values are enforced by
`AuthoredAssetCatalog`.

Schema v39 composes the visible mining campus from the already pinned
Integrated Villages workshop, residence, stable and depot modules, then adds
PM-authored headframe, chimney, loading-platform, local-foundation and path
geometry. Dungeons Arise, IDAS and Terralith modules are restricted to the
sealed underground adit, gallery and controller spaces.

The generated Threat Heart prompt requested a square Minecraft-style infected organic texture with a burgundy/crimson,
purple and sparse ember-orange palette, no text, logo, watermark or scene.
