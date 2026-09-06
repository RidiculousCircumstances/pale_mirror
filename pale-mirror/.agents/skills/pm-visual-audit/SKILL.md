---
name: pm-visual-audit
description: Review and improve Pale Mirror settlements, mines, rail corridors, points of interest, terrain integration, architecture, streets, props, atmosphere, and Blockbench creature assets using reproducible screenshots. Use for visual defects, supplied coordinates, screenshot capture, NBT art direction, settlement polish, mine appearance, creature modelling, or visual acceptance. Pair with pm-foundry-audit when world geometry is unsupported, obstructed, floating, inaccessible, or otherwise structurally invalid.
---

# PM Visual Audit

Judge the generated world from the player's eye level as well as from above.
Logs and plans cannot prove visual quality.

## Prepare evidence

1. Read `CONTINUITY.md` and `docs/settlement-visual-audit.md` completely.
2. When changing asset selection or NBT modules, also read
   `docs/pale-mirror-visuals.md` and `docs/VISUAL_ASSET_PROVENANCE.md`.
3. For Harvester creature geometry, textures, animation or acceptance, read
   `pale-mirror-visuals/src/main/blockbench/harvester/README.md` and
   `review_briefs.json` completely. The base-reference image is the likeness
   authority; the image-faithful mesh named by the brief is the candidate under
   review, not an optional construction guide.
4. Capture a deterministic before set with
   `scripts/capture-settlement-visuals.sh`. Include overview, entrances,
   streets, civic and industrial landmarks, perimeter, rail/depot, mine yard,
   and underground approach where relevant.
5. For a Harvester creature, use `tools/harvester_visual_audit.mjs --capture`
   instead. Inspect its mandatory reference/model contour overlay plus every
   prescribed neutral and animation frame; never infer likeness from element
   counts or successful export.
6. Open and inspect the actual images. Do not infer appearance from command
   success, logs, block counts, or a top-down map alone.

## Diagnose before editing

Classify each defect as authored module, layout grammar, terrain/surface plan,
route compiler, furniture/decoration pass, perimeter grammar, or late world
writer. Use `references/review-checklist.md` for the acceptance pass.

If a defect includes floating blocks, buried doors, blocked paths, broken
support, bad clearance, fluid intrusion, or disconnected rails, invoke
`pm-foundry-audit` and use its structural evidence before polishing aesthetics.

## Implement

Fix the reusable art grammar or module rather than the reported coordinate.
Preserve semantic roles and future functionality. Prefer coherent composition,
walkable transitions, terrain-native foundations, and intentional repetition
over random detail. Validate all affected climate/archetype variants.

For Harvester modelling, change the smallest set of dominant contour or
anatomical mismatches. Reconstruct the visible geometry directly from the
pinned reference pixels with an image-faithful mesh; do not simplify it to
Minecraft-like cubes. Background/shadow geometry, texture-concealed mass
errors and acceptance without the contour overlay are hard failures.

### Primary-image trace gate

Before authoring or replacing a Harvester's large masses, create a locked
primary camera and image plane using the pinned supplied reference. Trace the
visible silhouette and major anatomical boundaries into a versioned
source-pixel trace; show the working mesh over that same image before any
diagnostic-view polish begins. The trace constrains construction and decides
acceptance; it does not require creating the mesh from trace rails.

Do **not** begin with generic spheres, tubes, primitives, noise or a previous
failed primitive/cage candidate. Collector starts from the protected editable
copy of hash-pinned raw v05 geometry and may change that existing surface by
direct deformation and local retopology. It may not create a replacement body,
disconnected anatomy or global two-dimensional fitting. A generated turntable
never changes the primary trace, silhouette score or acceptance authority. If
no trace-overlay evidence exists, stop rather than treating an audit render as
evidence of likeness.

## Re-capture and report

Capture the same views after the change and inspect them side by side. Report
what materially improved, remaining visual debt, the exact screenshot set, and
any product judgment that still needs a human playtest.

### Mandatory blind comparison for Harvester candidate selection

When the work chooses between two Harvester geometry candidates, create a
package with `tools/harvester_blind_pairwise.py` and the versioned
`blind_review_protocol_v01.json`. Give at least two independent fresh-context
reviewers only that package's `public/` directory. They must first make an
ordinal primary-image decision (`amber`, `cobalt` or `indistinguishable`) and
then inspect diagnostic views; do not disclose IDs, prior reviews, provenance,
scores, overlays or expected result before their records are fixed.

Reveal the operator mapping and inspect the direct contour overlay only after
those decisions. Automated counts are exact-trace diagnostics, not cross-class
likeness scores; they cannot break a blind tie or override the visual verdict.
The blind winner is still unaccepted until the literal trace, normal-view,
animation/material and runtime/export gates all pass.
