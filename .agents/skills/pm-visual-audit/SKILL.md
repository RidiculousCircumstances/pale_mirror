---
name: pm-visual-audit
description: Review and improve Pale Mirror settlements, mines, rail corridors, points of interest, terrain integration, architecture, streets, props, and atmosphere using reproducible client screenshots. Use for visual defects, supplied coordinates, screenshot capture, NBT art direction, settlement polish, mine appearance, or visual acceptance. Pair with pm-foundry-audit when geometry is unsupported, obstructed, floating, inaccessible, or otherwise structurally invalid.
---

# PM Visual Audit

Judge the generated world from the player's eye level as well as from above.
Logs and plans cannot prove visual quality.

## Prepare evidence

1. Read `CONTINUITY.md` and `docs/settlement-visual-audit.md` completely.
2. When changing asset selection or NBT modules, also read
   `docs/pale-mirror-visuals.md` and `docs/VISUAL_ASSET_PROVENANCE.md`.
3. Capture a deterministic before set with
   `scripts/capture-settlement-visuals.sh`. Include overview, entrances,
   streets, civic and industrial landmarks, perimeter, rail/depot, mine yard,
   and underground approach where relevant.
4. Open and inspect the actual images. Do not infer appearance from command
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

## Re-capture and report

Capture the same views after the change and inspect them side by side. Report
what materially improved, remaining visual debt, the exact screenshot set, and
any product judgment that still needs a human playtest.
