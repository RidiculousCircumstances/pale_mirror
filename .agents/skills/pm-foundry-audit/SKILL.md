---
name: pm-foundry-audit
description: Audit and develop Pale Mirror physical materialization with Foundry reports, rules, ownership, recovery, and world-to-plan reconciliation. Use for settlements, mines, railways, routes, NBT modules, unsupported or obstructed structures, height/cut-fill maps, physical drift, materialization defects, Foundry commands, or new audit rules. Do not use for purely artistic review without a structural defect; use pm-visual-audit instead.
---

# PM Foundry Audit

Treat canonical simulation state and immutable materialization plans as the
source of truth. Treat loaded Minecraft chunks as an observed representation,
never as permission to force-load or silently repair the world.

## Prepare

1. Read `CONTINUITY.md`, the relevant sections of `architecture.yml`, and
   `docs/pale-mirror-foundry.md` completely.
2. Identify the owning layer and the exact immutable plan, catalog object, or
   physical job that owns the affected geometry.
3. Select the report phase deliberately:
   - `PLAN` or `COMPILED` for geometry that can be proven without a world.
   - `MATERIALIZED` immediately after placement.
   - `SETTLED` after neighbor updates, falling blocks, fluids, and late writes.
   - `RELOADED` after save, unload, and restart.
4. Prefer the latest settled or reloaded report when assessing a live defect.

## Investigate

1. Use `/pale_mirror debug foundry ...` or the existing export path. Do not
   force-load chunks, mutate the plan, or repair blocks while collecting data.
2. Summarize a report with `scripts/summarize_report.py`. Compare before and
   after evidence with `scripts/compare_reports.py`.
3. Classify the failure before editing:
   - authored module/NBT defect;
   - compiled layout or semantic-slot defect;
   - terrain/surface-plan defect;
   - route/rail graph defect;
   - execution-order or late-writer defect;
   - player/world conflict;
   - audit false positive.
4. Fix the reusable compiler, rule, module, or ownership contract. Never add a
   coordinate-specific patch for a generated-world symptom.

## Add or change a rule

Follow `references/rule-authoring.md`. Give every rule and finding a stable
identifier. Keep scans bounded and findings locatable. Make severity reflect
playability and data risk, not aesthetic preference.

For critical materialization changes, add a negative or recovery GameTest and
run the critical verification gates from `AGENTS.md`. Preserve idempotency,
provenance, and player-conflict semantics.

## Report

State the phase, catalog hash, region/site identity, metrics, failing rule IDs,
representative coordinates, source layer, fix, and verification. Distinguish a
clean compiled plan from a clean settled or reloaded world.
