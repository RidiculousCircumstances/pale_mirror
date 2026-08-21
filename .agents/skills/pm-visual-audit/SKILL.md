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
   `review_briefs.json` completely. The base-reference contour is the first
   geometry constraint; the hi-fi source is only a construction guide.
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
anatomical mismatches. Use varied overlapping cuboids whose primary projection
follows the pinned reference pixels. Dense pixel/high-poly voxel tracing,
texture-concealed mass errors, and acceptance without the contour overlay are
hard failures.

## Re-capture and report

Capture the same views after the change and inspect them side by side. Report
what materially improved, remaining visual debt, the exact screenshot set, and
any product judgment that still needs a human playtest.
