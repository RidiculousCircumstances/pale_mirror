# Harvester organic reconstruction bake-off

## Purpose

Replace the rejected Collector `primary-relief` experiments with one smooth,
anatomically coherent organic mesh while retaining literal agreement with the
pinned user reference in the locked primary camera.  This is authoring R&D,
not a runtime or infection change: no candidate is exportable, registered or
able to alter canonical threat state.

The supplied reference image and `biomass_collector_primary_v03` are the sole
authority for primary-view likeness.  AI-generated geometry and generated
turntables may propose only unseen-side volume, never alter a traced source
pixel, score a candidate, or silently become the canonical model.

## Reproducible inputs and provenance

The active camera-corrected trial is `collector_volume_bakeoff_v02`. The prior
`v01` artefacts remain immutable evidence: SF3D rendered its trace-cut input,
but the generated geometry was rejected after the real five-view audit.

| Input | Authority | Required record |
| --- | --- | --- |
| `01_harvester_biomass_collector.jpg` | primary likeness | SHA-256, dimensions |
| `biomass_collector_primary_v03.json` | primary contour and camera | SHA-256, trace id |
| `collector_primary_input.png` | source image cut by the literal trace mask | SHA-256, mask provenance |
| model checkpoint | hidden-side volume proposal only | repository/revision, checkpoint hash, licence, access status |
| model output | unaccepted volume candidate | file hashes, device, exact command/config, tool revision |

Credentials, Hugging Face tokens and model weights never enter this repository
or its manifests.  A gated model that cannot be obtained through the author's
own account is recorded as unavailable; it is not replaced by an unrecorded
download.

## Candidate order

1. **Stable Fast 3D (SF3D)** is the first candidate.  Its published default
   requirement fits the Windows RTX 4070 Laptop's 8 GiB VRAM budget.  Run it
   only against the trace-cut primary input and request its native mesh/UV
   output; it is a volume draft, not a final textured asset.
2. **Hunyuan3D-2mv** is the calibrated multi-image comparison.  It receives
   four fixed `front/left/back/right` slots simultaneously; in the active v03
   calibration the literal source-trace image occupies `front`, whose index
   is verified from the pinned upstream processor, while three independently
   generated single-subject views supply only clockwise hidden-volume hints.
   SF3D cannot perform that fusion.  The secondary panels are generated
   diagnostic evidence rather than calibrated photographs, so its result
   remains a non-authoritative volume proposal.  It runs shape-only with
   official CPU offload because the Windows RTX 4070 has less VRAM than the
   published shape recommendation; an out-of-memory result is an expected,
   recorded result rather than a reason to destabilise the authoring host.
3. Other methods may be added only as named, versioned candidates with the
   same provenance.  Generated multi-view images remain diagnostic evidence,
   never primary contour authority.

The Windows workstation performs CUDA inference and Blender review.  The
64-GiB Linux workstation may prepare masks, validate manifests and run
CPU-heavy mesh analysis, but host RAM is not treated as substitute VRAM.

## Stages and gates

### A. Controlled input

`tools/harvester_volume_bakeoff.py prepare` verifies the pinned reference and
trace, compiles the literal trace mask and writes a transparent RGBA image plus
a manifest.  A changed source image, trace or camera mapping fails closed.

### B. Volume proposal bake-off

Each model produces a private GLB/mesh candidate.  The candidate is rendered
through the existing typed Blender operation with the locked primary camera and
the five diagnostic views.  It passes this stage only when it is an actual
closed, smooth volume with no obvious card, raster shell, disconnected legs or
primary-plane leak.  It does **not** pass visual acceptance at this stage.

### C. Contour-constrained cage fit

The selected proposal is controlled by a low-dimensional cage rather than
moving every generated vertex.  The optimisation may move depth and internal
shape, but it must preserve:

- the exact source-pixel silhouette at the locked primary camera;
- pinned ground contacts, six dorsal-sac centres/arcs, front mantle, major
  support arches and tail/drape landmarks;
- positive visible thickness in diagnostic views;
- smoothness, edge-length and normal-consistency regularisers.

The fitter must not flatten the mesh into the source plane or use a fitted
screen-space mask as geometry.  A failed fit retains the candidate as evidence
and returns to a controlled manual cage/anatomy pass.

### D. Anatomy, retopology and rigging

Blender authoring turns the accepted cage volume into one coherent body:
separate six dorsal sacs, rooted articulated supports, a hanging leading
mantle and intentional tail/drape fibres.  Voxel remesh is allowed only for a
disposable fusion proxy; the animation-ready mesh requires clean semantic
regions and a PM-owned rig.  Automatic retopology and UniRig can propose
topology/skeletons but never replace the required visual and motion review.

### E. Visual acceptance

Run `tools/harvester_visual_audit.mjs --capture`, collect the Blender frames,
inspect the real primary overlay plus every mandated diagnostic/action frame,
and obtain an independent review.  The 85/100 hard gate in the Harvester brief
still applies.  Only after this evidence is accepted may the canonical `.blend`
be collected, PMMesh v1 exported and a separate renderer/debug-entity task
begin.

## Operational boundaries

- Windows Blender remains behind the pinned SSH tunnel and versioned typed
  operations; no caller gets arbitrary `bpy` execution.
- AI inference has its own named CLI/environment under `E:\PaleMirror`; it
  cannot overwrite the Blender source, canonical assets or model catalog.
- Every output lives under a label-specific bake-off directory and is
  hash-recorded before Blender imports it.
- The current `collector_volume_v08` and v09 remain rejected/unaccepted
  evidence.  They are not inputs for a new candidate beyond comparison.

## Exit conditions for this trial

The trial succeeds when SF3D (or a recorded fallback) produces a hash-pinned
closed volume that imports through a named Blender operation, renders in the
locked audit set, and is explicitly classified `volume-stage-only` by visual
review.  It fails cleanly if the model cannot be licensed/downloaded, exceeds
the hardware budget, or produces an unsuitable volume; the source-pixel trace
and existing Blender scene remain untouched in every failure case.
