# Harvester blind pairwise review

This protocol selects between two visual candidates. It exists because a raw
pixel overlap and a numerical score can be useful local diagnostics yet still
mis-rank a generated organic volume against a trace-led candidate. The supplied
primary image remains the sole likeness authority.

The machine-readable contract is
`pale-mirror-visuals/src/main/blender/harvester/blind_review_protocol_v01.json`.
It is mandatory whenever a modelling decision compares two Harvester geometry
candidates. It does not replace the direct primary-contour gate or normal
animation/material review.

## Create a package

Both candidate directories must be produced by the same audit-camera contract
and contain `primary.png`, `front.png`, `side.png`, `opposite.png` and
`elevated_rear.png`.

```bash
python3 tools/harvester_blind_pairwise.py \
  --protocol pale-mirror-visuals/src/main/blender/harvester/blind_review_protocol_v01.json \
  --review-id collector-example-v01 \
  --reference /home/rd/harvester_references/01_harvester_biomass_collector.jpg \
  --candidate operator_a=build/blender-audits/candidate-a \
  --candidate operator_b=build/blender-audits/candidate-b \
  --output-root build/harvester-blind-reviews
```

The tool refuses to overwrite a prior package. It decodes and rewrites every
image as a PNG, removing source names and metadata. The resulting
`public/` directory contains only `reference.png`, `amber/`, `cobalt/`, the
review prompt and its response template. Candidate identity, paths, provenance,
history, scores, overlay and metric data are retained only in the neighbouring
operator mapping.

## Review order

Give each independent reviewer a fresh context and only the absolute path to
`public/`. The reviewer first chooses `amber`, `cobalt`, or
`indistinguishable` from the primary images, then checks diagnostic views and
records a final ordinal preference. It must not assign a scalar score or inspect
the parent directory.

Use at least two reviewers. A winner exists only when both choose the same
label after diagnostic views; disagreement is an inconclusive result, not a
tiebreak opportunity for an automated metric. Reveal `operator_mapping.json`
only after the records are fixed.

Only then inspect the direct locked-camera trace overlay. A material contour
mismatch blocks acceptance, but cannot retrospectively turn a human ordinal
preference into a numerical likeness rank. A blind winner still needs every
ordinary acceptance gate: literal trace, normal-view defect review,
animation/material evidence and the explicit export/runtime gate.
