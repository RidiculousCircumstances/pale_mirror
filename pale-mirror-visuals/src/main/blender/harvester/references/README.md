# Harvester supporting turntables

The user-supplied base images under `/home/rd/harvester_references/` remain the
sole likeness authority for every Harvester. Generated views never replace a
primary-view contour, resolve a primary mismatch, change primary likeness
scoring, or justify acceptance on their own.

`biomass_collector_turntable_v02.provenance.json` is a narrower reviewed
exception for the Collector: its four explicit source-local camera slots are
canonical hidden-side constraints for their own cameras and feed the
shape-only Hunyuan v05 trial. The literal source still controls its 0° trace;
the two remaining panels are Blender-only diagnostic evidence. Build the
normalized human-review contact sheet with:

```bash
python tools/harvester_turntable_contact_sheet.py \
  --manifest pale-mirror-visuals/src/main/blender/harvester/references/biomass_collector_turntable_v02.provenance.json \
  --references-root /home/rd/harvester_references \
  --output build/harvester-turntables/biomass_collector_turntable_v02.png
```

The checked-in `biomass_collector_turntable_v02_canonical_contact_sheet.png`
is the SHA-pinned result of that command. It is a Blender reference plane and
audit aid only; it is never cropped or supplied as a model-conditioning image.

| File | Input authority | Generated | SHA-256 | Retained use |
| --- | --- | --- | --- | --- |
| `biomass_collector_turntable_v01.png` | `01_harvester_biomass_collector.jpg` | 2026-08-22, built-in image generation | `7d62fda968ecb2dc8b2c7aa157f077f77e9cd16f7eb031e54dbb900e72ba168e` | Dorsal-sac depth, thick leg profiles, mantle volume, tail continuity. |
| `crusher_stalker_turntable_v01.png` | `02_crusher_stalker.jpg` | 2026-08-22, built-in image generation | `e5d94db0b2a7df0566c5e86f0c753370a6c75034af67e0ba109c2d2ac921fc15` | Crushing-arm depth, hunched shell continuity, compact head and rear-leg volume. |
| `scythe_stalker_turntable_v01.png` | `03_scythe_stalker.jpg` | 2026-08-22, built-in image generation | `6d84d51ae2197471f75ccca2056a22f9eb8a7465a1accf442cdd9df4be649a90` | Scythe thickness and inner hook, thorax width, six-leg clearance and rear-whip attachment. |

The three prompts were deliberately restrictive: each requested the same
creature in a four-view black-studio sheet and prohibited invented eyes,
weapons, limbs, text, scenery, logos and watermarks. They were derived one at a
time from the matching source image. Any apparent generated detail which is not
supported by that source remains non-authoritative and must not enter the mesh
as a likeness claim.
