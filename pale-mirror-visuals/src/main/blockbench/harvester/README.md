# Harvester creature source assets

These are editable Blockbench Bedrock-model projects whose exported GeckoLib
resources are bundled under `src/main/resources/assets/pale_mirror_visuals`.
They intentionally include no entity, renderer or runtime registration.

| Creature | Geometry | Animation IDs |
| --- | --- | --- |
| Biomass Collector | `geo/harvester/biomass_collector.geo.json` | `animation.biomass_collector.idle`, `animation.biomass_collector.harvest` |
| Crusher Stalker | `geo/harvester/crusher_stalker.geo.json` | `animation.crusher_stalker.walk`, `animation.crusher_stalker.crush` |
| Scythe Stalker | `geo/harvester/scythe_stalker.geo.json` | `animation.scythe_stalker.walk`, `animation.scythe_stalker.slash` |

For visual authoring, open the matching `*_reference_hifi.bbmodel` project.
These are project-owned high-polygon working sources built around the same named
skeleton and animation clips. They are useful construction guides, but are not
the acceptance target: the supplied base reference image for that creature is
the only visual authority. The plain `<creature>.bbmodel` files are the
256×256, cube-only GeckoLib counterparts that are already export-compatible
with the Java/NeoForge asset paths above. Meshes are deliberately retained as
editor sources only: standard GeckoLib runtime geometry remains cube-based
until a custom mesh renderer is added.

When editing a `.bbmodel`, export the matching geometry and animation files to
the resource paths above. Keep the texture at
`textures/entity/harvester/<creature>.png`; the same texture is copied next to
each `.bbmodel` so Blockbench opens it without a missing-texture prompt.

`material_studies/` retains the project-bound 1254×1254 organic-material
sources used by the hi-fi Blockbench projects. The reproducible 256×256
Minecraft sheets are made from them by `tools/build_harvester_texture_sheets.py`.

## Reference-first review protocol

Every modelling iteration is reviewed in normal Blockbench display before a
score is assigned. Capture the neutral pose in these five views at a fixed
orthographic scale and with the grid/selection outlines hidden:

1. The primary three-quarter view, matched as closely as possible to the base
   reference camera. This is the only direct likeness comparison.
2. The opposite three-quarter view.
3. Front and side views.
4. A slightly elevated rear view.

The non-reference views do not earn likeness points. They expose accidental
flatness, intersecting limbs, hidden missing forms, and shapes that work only
from the reference camera. Review the idle pose and three frames of the action
clip: anticipation, strongest pose, and recovery. Store the before/after image
sets with the iteration notes and inspect them side by side; a render command,
element count, valid JSON, or a successful animation export is never visual
evidence.

Score the runtime cube model out of 100, against the base reference only:

| Criterion | Points | What is judged |
| --- | ---: | --- |
| Primary-view silhouette and proportions | 35 | Envelope, stance, front/rear weight, and the recognisable outline at the reference camera. |
| Primary-view anatomical landmarks | 30 | The creature-specific large forms: Collector shell sacs and draped head, Crusher's humped crushing forequarters, or Scythe's dorsal arch and hooked blades. |
| Volume from the four diagnostic views | 15 | Cohesive masses rather than thin planes, hidden gaps, collisions, or a form that only works from one angle. |
| Limb hierarchy and joints | 10 | Clear major segments, taper and purposeful overlapping joints; not a noisy equal-cell surface. |
| Material and animation readability | 10 | Continuous material hierarchy plus readable idle/action poses without joints detaching or collapsing. |

Two fail gates override the numerical total:

- A uniform surface lattice of similarly sized cubes is a failed result. It
  obscures the large anatomical masses and scores **0** for landmark likeness
  until removed.
- A model cannot be called acceptable below 85/100, nor called 10/10 unless
  there are no material mismatches in the primary comparison and no critical
  defect in any diagnostic or animation frame.

Do not award points for cuboid count, technical validity, texture resolution,
or resemblance to a hi-fi working source. Record the per-criterion score,
three largest visible mismatches, and the exact next modelling change after
each iteration. The existing surface-sampling approach below is retained only
as a rejected experiment; it is not a valid final modelling method.

## Reproducible LLM visual pipeline

`review_briefs.json` turns the workflow into data: it pins the base-reference
filename, primary camera intent, creature-specific landmarks, action frames and
the immutable score weights. The editable runtime `.bbmodel` remains the one
asset under review. It has eight deliberately separate steps:

1. Read the pinned base reference and its landmark list.
2. Plan the next anatomical change in a varied-cuboid model; do not sample a
   hi-fi surface or use texture to conceal a bad mass.
3. Prove the untextured large masses and silhouette before adding secondary
   detail.
4. Add only landmark, limb, joint or material-separation detail that is visible
   from the reference camera.
5. Capture the ten prescribed frames from normal Blockbench display, including
   the texture-free solid silhouette before its textured views.
6. Invoke an independent vision-capable review subagent to score the images
   against the base reference, explicitly marking the hard defect gates. The
   user is not required to fill a scorecard.
7. Change only the three largest visible mismatches, then compare the next
   audit directory with this one.
8. Finalise the review; a score below 85 or any hard gate blocks acceptance.

Create an audit package with real screenshots after every modelling change:

```bash
node tools/harvester_visual_audit.mjs \
  --creature scythe_stalker \
  --iteration anatomical-masses-v01 \
  --references-root /home/rd/harvester_references \
  --capture
```

The command opens the ordinary desktop Blockbench application, pins its camera
views and writes `manifest.json`, `scorecard.md`, `review.json`, and the image
set under `build/harvester-visual-audits/`. It intentionally leaves Blockbench
open so the reviewer can inspect the same normal display. Its static
uniform-cuboid warning is only a prompt: visual rejection still requires an
image review. On a headless shell connected to the desktop's XWayland session,
pass `--display :0` explicitly.

After the independent visual subagent has looked at the images, the primary
agent fills its findings into `review.json` and finalises it:

```bash
node tools/harvester_visual_audit.mjs \
  --finalize build/harvester-visual-audits/<audit-directory>
```

Finalisation fails closed if evidence, scores, defect decisions, three visible
mismatches, or one precise next modelling change are absent. The CLI does not
claim that a static automated score can replace the independent visual review.

## Rejected cube-model rebuild

`tools/rebuild_harvester_srp_style.mjs` sampled each source surface into
roughly one thousand small, overlapping cuboids per creature. That procedure
produces an unwanted voxel lattice and is retained solely as a reproducible
failed experiment. Do not use it for a final asset. The replacement runtime
model must be assembled from a smaller number of deliberately varied,
overlapping anatomical cuboids and pass the review protocol above.

The SRP JAR was inspected solely as a technical reference for this cuboid-chain
approach. No SRP model, texture, code or other asset is included here.
