# Harvester creature source assets

These are editable Blockbench Bedrock-model projects whose exported GeckoLib
resources are bundled under `src/main/resources/assets/pale_mirror_visuals`.
They intentionally include no entity, renderer or runtime registration.

| Creature | Geometry | Animation IDs |
| --- | --- | --- |
| Biomass Collector | `geo/harvester/biomass_collector.geo.json` | `animation.biomass_collector.idle`, `animation.biomass_collector.harvest` |
| Crusher Stalker | `geo/harvester/crusher_stalker.geo.json` | `animation.crusher_stalker.walk`, `animation.crusher_stalker.crush` |
| Scythe Stalker | `geo/harvester/scythe_stalker.geo.json` | `animation.scythe_stalker.walk`, `animation.scythe_stalker.slash` |

For visual authoring and acceptance, open the matching
`*_reference_hifi.bbmodel` project. Despite the historical filename, it is the
canonical image-faithful model: its silhouette and dominant visible volumes
are reconstructed directly from the supplied reference image, with no
Minecraft-style simplification. The image remains the sole likeness authority;
the mesh is the editable three-dimensional realization of that authority.

The plain `<creature>.bbmodel` and `geo/` files are a **legacy GeckoLib
compatibility export**, not an acceptable creature model and not an input to
visual acceptance. They remain only because standard GeckoLib geometry is
cube-based. No future entity renderer may silently select them in preference to
the image-faithful mesh. A runtime custom-mesh renderer is required before a
Harvester is registered in gameplay; until then these are intentionally
resource-only assets.

When editing a `.bbmodel`, export the matching geometry and animation files to
the resource paths above. Keep the texture at
`textures/entity/harvester/<creature>.png`; the same texture is copied next to
each `.bbmodel` so Blockbench opens it without a missing-texture prompt.

`material_studies/` retains the project-bound 1254×1254 organic-material
sources used by the hi-fi Blockbench projects. The reproducible 256×256
Minecraft sheets are made from them by `tools/build_harvester_texture_sheets.py`.

## Legacy compatibility export

`tools/rebuild_harvester_anatomical_models.mjs` is the reproducible source for
the committed legacy cube compatibility export. It preserves the named bones,
hierarchy, texture and GeckoLib animation IDs for the future mesh renderer's
animation bridge. It is not a likeness generator. Do not hand-patch only one
of its outputs or re-run the rejected surface-sampling experiments.

```bash
node tools/rebuild_harvester_anatomical_models.mjs
node tools/rebuild_harvester_anatomical_models.mjs --check
```

The second command is non-mutating and verifies only compatibility output.

The image-faithful source itself is reproducible separately:

```bash
node tools/build_harvester_hifi_blockbench_models.mjs
node tools/build_harvester_hifi_blockbench_models.mjs --check
```

## Image-faithful construction contract

The outer contour in the pinned base-reference pixels is a literal geometry
constraint for the canonical mesh. Before adding unseen-side volume, project
the untextured mesh through the pinned primary camera and trace the actual
visible subject envelope: aspect ratio, ground line, dorsal sac arcs,
front/rear mass balance, limb arches, negative spaces and trailing forms. Do
not redraw proportions from memory, from an earlier Blockbench model or from a
generic "Minecraft creature" convention.

High-density mesh reconstruction is required wherever it materially improves
this match. It may trace the visible silhouette and major anatomical boundaries
at the reference image's pixel precision, but must not turn the photograph's
background, cast shadows or noise into geometry. Diagnostic views own the
unseen-side volume, joint separation and animation-safe depth; exact likeness
in the primary image must never be achieved with a flat cut-out.

### Non-negotiable primary trace workflow

Every new or replacement canonical mesh begins from a versioned primary-image
trace, not from an adjustable collection of generic primitives. The authoring
scene must contain the pinned base image as a locked primary-camera plane, and
the trace must retain literal source-pixel coordinates for the outer contour
and the major visible anatomical boundaries. Before any volume, texture or
diagnostic polish is accepted, the corresponding untextured mesh is rendered
over that same primary image and its projected contour must agree with the
trace.

Spheres, tubes, procedural noise, previous failed candidates and generated
turntables are forbidden as a primary-shape starting point. They may only add
unseen-side depth or a local secondary form after the traced primary silhouette
has passed review. The generated turntable is never a source of primary
proportions, contour edits, scoring or acceptance.

Each creature's `reference_subject_bounds_normalized`, alignment anchor and
`contour_landmarks` are pinned in `review_briefs.json`. After every capture,
`tools/harvester_contour_overlay.py` extracts the solid Blockbench projection,
fits it uniformly into those reference bounds and creates a three-panel
reference / model mask / overlay image. `tools/harvester_visual_audit.mjs` runs
that step automatically. The overlay is mandatory evidence and a material
primary-contour mismatch is a hard rejection gate, but the tool deliberately
does not award a similarity score: a vision-capable reviewer decides whether
the projected contour follows the actual creature rather than image noise.

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

For an individual candidate, record the following evidence against the base
reference:

| Criterion | Points | What is judged |
| --- | ---: | --- |
| Primary-view silhouette and proportions | 35 | Envelope, stance, front/rear weight, and the recognisable outline at the reference camera. |
| Primary-view anatomical landmarks | 30 | The creature-specific large forms: Collector shell sacs and draped head, Crusher's humped crushing forequarters, or Scythe's dorsal arch and hooked blades. |
| Volume from the four diagnostic views | 15 | Cohesive masses rather than thin planes, hidden gaps, collisions, or a form that only works from one angle. |
| Limb hierarchy and joints | 10 | Clear major segments, taper and purposeful overlapping joints; not a noisy equal-cell surface. |
| Material and animation readability | 10 | Continuous material hierarchy plus readable idle/action poses without joints detaching or collapsing. |

Two fail gates override the numerical total:

- A generic low-detail cuboid substitute, or a mesh whose screen-space contour
  is visibly hand-waved rather than traced from the image, is a failed result.
  Technical compatibility geometry earns no likeness credit.
- A model cannot be called acceptable below 85/100, nor called 10/10 unless
  there are no material mismatches in the primary comparison and no critical
  defect in any diagnostic or animation frame.

Do not award points for polygon count, technical validity or texture
resolution. Record the per-criterion evidence, three largest visible
mismatches, and the exact next modelling change after each iteration. A scalar
score may describe one reviewed candidate only; it is not calibrated across
generated, trace-led or primitive candidate classes and cannot choose between
them.

Whenever a choice exists between two geometry candidates, run the mandatory
blind pairwise protocol in
`../../blender/harvester/blind_review_protocol_v01.json` first. Two fresh
reviewers see only anonymized `amber`/`cobalt` images and make an ordinal
primary-plus-diagnostic verdict before the operator reveals IDs or a contour
overlay. The direct overlay remains a hard technical acceptance gate, but it
does not become an automated likeness score or override a blind visual verdict.
See `docs/harvester-blind-review.md` for the exact command and disclosure
order.

## Reproducible LLM visual pipeline

`review_briefs.json` turns the workflow into data: it pins the base-reference
filename, canonical mesh project, primary camera intent, creature-specific
landmarks, action frames and immutable score weights. It has eight deliberately
separate steps:

1. Read the pinned base reference and its landmark list.
2. Read the pinned subject bounds and contour landmarks, then make the next
   image-derived mesh change directly against that reference contour. Trace
   visible geometry precisely; do not use texture to conceal a bad mass.
3. Prove the untextured large masses and silhouette before adding the smaller
   reference-derived anatomy.
4. Add only landmark, limb, joint or material-separation detail that is visible
   from the reference camera.
5. Capture the ten prescribed frames from normal Blockbench display, including
   the texture-free solid silhouette before its textured views, and generate
   the mandatory reference/model contour overlay.
6. For a comparative selection, invoke two independent fresh-context visual
   reviewers through the blind pairwise package before showing them any
   candidate history, score or overlay. For a single-candidate review, invoke
   one independent vision-capable reviewer against the base reference and mark
   the hard defect gates. The user is not required to fill a scorecard.
7. Change only the three largest visible mismatches, then compare the next
   audit directory with this one.
8. Finalise the review; any hard gate blocks acceptance. An 85-point scalar
   threshold applies only within a comparable audit class and never resolves a
   cross-class comparison.

Create an audit package with real screenshots after every modelling change:

```bash
node tools/harvester_visual_audit.mjs \
  --creature scythe_stalker \
  --iteration anatomical-masses-v01 \
  --references-root /home/rd/harvester_references \
  --capture
```

The command opens the canonical mesh project selected by the brief in ordinary
desktop Blockbench, pins its camera views and writes `manifest.json`,
`scorecard.md`, `review.json`, and the image set under
`build/harvester-visual-audits/`. It intentionally leaves Blockbench open so
the reviewer can inspect the same normal display. On a headless shell
connected to the desktop's XWayland session, pass `--display :0` explicitly.

After the independent visual subagent has looked at the images, the primary
agent fills its findings into `review.json` and finalises it:

```bash
node tools/harvester_visual_audit.mjs \
  --finalize build/harvester-visual-audits/<audit-directory>
```

Finalisation fails closed if evidence, scores, defect decisions, three visible
mismatches, or one precise next modelling change are absent. The CLI does not
claim that a static automated score can replace the independent visual review.

## Rejected modelling shortcuts

`tools/rebuild_harvester_srp_style.mjs` sampled an earlier source surface into
roughly one thousand equal, overlapping cuboids per creature. That procedure
produces a noisy voxel lattice and is retained solely as a reproducible failed
experiment. Do not use it for a final asset. The approved replacement is the
reference-driven organic mesh above, evaluated by the image-review protocol.

The SRP JAR was inspected solely as a technical reference for this cuboid-chain
approach. No SRP model, texture, code or other asset is included here.
