---
name: pm-automodel
description: Build, run, or review Pale Mirror image-to-3D, novel-view, reconstruction, texture, and auto-rig experiments without granting model output canonical art authority.
---

# PM Automodel Lab

Use this skill for automated creature modelling, multi-view synthesis, depth or
pose reconstruction, shape proposals, texture inference, rigging proposals,
or their model adapters. It is an offline R&D workflow, never a gameplay or
runtime path.

## Evidence before models

Read `CONTINUITY.md`, the relevant Harvester reference brief and
`docs/automodel-v2.md` before changing an Automodel run or adapter. For a
creature task, also use `pm-visual-audit`.

- The pinned supplied image and its source-pixel trace are the sole likeness
  authority. A generated view, inferred camera, depth map, point cloud or mesh
  is a proposal, even when several models agree.
- Preserve explicit evidence tiers. Only a human may promote a secondary view
  from unreviewed to reviewed; no score, model agreement or model-generated
  metadata can perform that promotion.
- A proposal may not read or write canonical `.blend` files, PMMesh exports,
  runtime resources, scenario state, or server state. It writes only a
  hash-pinned, ignored `build/automodel/...` run directory.
- Do not fuse meshes or point clouds by voting. Use model disagreement to mark
  uncertainty, then hand a selected proposal to the existing visual review.

## Model execution

Use only a registered adapter and its declared local hardware profile. Pin the
adapter source revision, checkpoint hash/licence, environment lock, inputs,
seed and hardware facts in the run manifest. Missing weights, inaccessible
models, unsupported hardware, incomplete provenance or failed output checks
must fail closed and leave no partially promoted candidate.

Current local work is limited to the registry entries enabled for the private
Windows GPU or Linux CPU. Deferred providers need a new preflight and explicit
approval before they can run. Never store credentials, weights or generated
outputs in Git.

## Review and handoff

Use synthetic known-scene benchmarks to qualify an adapter; its metrics
qualify only the adapter's permitted role, not likeness. A synthetic
turntable must pass identity review and pose-order checks before it can become
secondary depth evidence. The review package must expose source provenance,
primary overlay and uncertainty rather than a single winner score.

Only a separately approved visual review may create an editable Blender
candidate. The protected Collector Master remains untouched unless the user
explicitly reopens it.
