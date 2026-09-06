# Automodel v2: evidence-bounded creature research

Automodel v2 automates repeatable research operations around creature
modelling. It does not automate artistic acceptance. The source primary image
and its source-pixel trace remain the sole authority for likeness; no model
agreement can make a synthetic rear view or mesh factual.

## Artifact flow

```text
ReferenceBundle (literal primary + trace + optional conditioning-only input)
    -> RunManifest (source/checkpoint/environment/seed hashes)
    -> execution receipt + ViewSet (literal primary plus labelled secondary frames)
    -> GeometryEvidence (pose/depth/normal proposals) / ShapeProposal
    -> disagreement maps
    -> review-only Blender candidate
    -> explicit human decision
    -> existing canonical Blender / PMMesh protocol
```

All pre-Blender artifacts are versioned JSON manifests and ignored run output
below `build/automodel/`. They include input hashes, adapter revision,
checkpoint/environment hashes, licence, seed and local hardware profile.
Models never receive canonical `.blend`, PMMesh, runtime resource, world or
server paths.

An official checkpoint is represented by an immutable `official_checkpoint`
receipt. It pins a registry-approved Hugging Face repository/file pair, byte
count and SHA-256, but never copies weights or a private host path into Git.
The registered `facebook/VGGT-Omega/vggt_omega_1b_512.pt` and
`stabilityai/sv3d/sv3d_p.safetensors` pairs are distinct from every quarantined
third-party file, even where model names or tensor layouts appear compatible.

`ReferenceBundle` has two deliberately different input classes:

- `primary` is a literal byte-for-byte staged copy of the supplied source
  image and is the only likeness anchor;
- an optional `conditioning_only` input is an explicitly declared deterministic
  transform of that primary and its trace. `isolated_subject_v1` is the current
  SV3D_p input: a transparent RGBA isolation with a separately pinned binary
  mask, cleanup receipt and exact upstream-equivalent white `576²` review
  frame. It removes disconnected trace dust and tiny enclosed alpha holes,
  but cannot paint, extend or invent source pixels. It is not an additional
  view and cannot gain
  primary authority.
- `generated_cutout_r01` is a separate explicitly `MODEL_DERIVED` transparent
  background-extraction image, accepted only as conditioning for a bounded
  SV3D visual-preview experiment. It may never enter VGGT/geometry, canonical
  Blender, PMMesh or runtime work; the literal primary and trace remain the
  only likeness authority.

Every collector refuses to overwrite an artifact. A failed or incomplete
local run is therefore preserved as a separate, non-promoted run directory;
retrying means preparing a new run id.

## Evidence tiers

| Tier | Meaning | May constrain canonical likeness? |
| --- | --- | --- |
| `PRIMARY_TRACE` | Supplied image plus pinned source-pixel trace | Yes |
| `REVIEWED_SECONDARY` | A human accepted a stable secondary view | Volume aid only |
| `MODEL_DERIVED` | Inferred pose/depth/mask/shape/material/rig | No |
| `UNTRUSTED` | Incomplete or failed experimental output | No |

Only an explicit human review receipt may promote a `MODEL_DERIVED` frame to
`REVIEWED_SECONDARY`. Geometry stays model-derived even when it was produced
from reviewed views. Agreement creates an uncertainty map, never a fused mesh
or automatic promotion.

## Local model policy

The current profile has only the private Windows 8 GiB GPU and a Linux CPU
host. The registry therefore permits preflight only for SV3D_p, bounded
Edit360 dual-anchor visual preview, DA3-Base, SPAR3D low-VRAM and the existing
isolated Hunyuan shape runner. An enabled preflight means only that a local
experiment may be prepared; it does not mean the result is accepted.

SEVA/MV-Adapter, SAM3D, TRELLIS.2, Hunyuan Paint and SkinTokens are recorded
as deferred. A future executor requires an explicit decision, upstream
revision/checkpoint/terms, a hardware preflight and known-geometry benchmark.
PBR maps may be stored as diagnostics, but PMMesh v1 carries a single albedo
texture; Automodel v2 does not alter the renderer or asset schema.

## Implemented local hand-offs

The lab has no generic `run this command` facility. Its fixed operations are:

```text
stage_reference_bundle.py
  -> copies literal primary/trace into build/automodel/<asset>/<run>/input
  -> optionally derives the reviewable isolated_subject_v1 conditioning input

checkpoint_receipt.py
  -> records a private-host SHA-256 receipt for one registered official model file

download_official_checkpoint.py
  -> downloads only a registry-approved Hugging Face repo/file pair to the private inference host

environment_lock.py
  -> records a redacted immutable interpreter/package/CUDA environment receipt for a run manifest

orchestrator.py prepare-run
  -> pins model source revision, checkpoint hash, environment hash and seed

registered local runner
  -> writes files only below that prepared run directory

orchestrator.py collect-views / collect-geometry / collect-shape
  -> validates format/location/hash, then writes evidence and immutable receipt

orchestrator.py review-turntable / record-turntable-review
  -> produces an inspectable 21-position contact sheet, then records a human
     eligibility decision without changing any frame's evidence tier

prepare_vggt_pose_depth_input.py
  -> copies only a human-reviewed SV3D sequence plus the pinned isolated-subject
     primary into a fresh legacy VGGT pose/depth input directory
```

`tools/sv3d_orbit/run_windows_sv3d_p.ps1` is the first concrete runner. It is
restricted to the private Windows GPU host and the pinned upstream SV3D_p
checkout. It checks revision, private checkpoint location/hash, environment
lock, manifest input hashes, a matching official-checkpoint receipt and CUDA imports before it allows inference. It
does **not** claim that a view-synthesis model has passed a geometry benchmark:
the only preflight outcome is permission to emit unreviewed `MODEL_DERIVED`
views. It invokes exactly the upstream
SV3D_p sampler at 50 steps and extracts the twenty non-primary frames from its
21-position trajectory. The literal primary replaces upstream's final
re-encoded 0-degree frame. Its separately registered `fast_fp16_20` profile is
a synthetic-qualification-only experiment: the same official FP32 checkpoint
is CPU-cast so the denoiser runs in FP16 while upstream VAE codec boundaries
remain FP32, sampled at exactly 20 steps under CUDA autocast, and remains a
distinct `MODEL_DERIVED` adapter. It may not
silently replace the full profile or authorize creature output.

`tools/edit360_orbit/` is a separate dual-anchor visual experiment. Its
trace-derived or separately reviewed generated white front and explicitly
`MODEL_DERIVED` verified opposite-broadside or actual rear-right-three-quarter
anchor are hash-pinned in the reference bundle. The anchor's nominal 180-degree
setting is not a camera calibration. The upstream `sv3d_u` sampler
does not expose calibrated azimuths in dual mode, so its twenty generated
images are recorded only as an ordered visual sequence. The direct FP32
profile is deferred because the official checkpoint is 9.36 GB before CUDA
activations. The distinct local 8 GiB profile uses a CPU-cast FP16 denoiser,
explicit FP32 codec boundaries and conditioner/safety-filter CPU offload at
exactly 20 steps. Neither profile creates a ViewSet, declared camera pose,
VGGT input, geometry evidence or canonical asset.

Before either Edit360 profile can preflight, its exact staged front and
opposite-anchor pixels must have a separate immutable visual-input review.
This is an operator gate, not an automatic likeness score: it records that the
effective canvases were opened and checked for background residue, holes,
detached fragments, clipping, legible subject scale and genuine rear anatomy
rather than a mirrored same-side view. The runner rejects a
missing, stale or mismatched review. A clean `MODEL_DERIVED` generated front
may be used only for this same visual-preview role when trace isolation damages
the textured subject; it cannot become geometry, a ViewSet, Blender, PMMesh or
runtime evidence.

The SV3D wrapper may produce views only after its official checkpoint,
environment lock and import preflight match the prepared manifest. It still
does not qualify a geometry provider: an orbit has to pass explicit visual
continuity review and a later independent pose-order check before a bounded
VGGT pose/depth experiment may inspect it.

`prepare_synthetic_qualification.py` and
`validate_synthetic_qualification.py` are the separate executor qualification
path for `fast_fp16_20`: they use an asymmetric synthetic input and accept only
the exact 21-frame 576² video plus twenty uniquely hash-pinned 18°…360° frame
files. The resulting `technical_execution_passed` receipt verifies that the
bounded runner can finish; it says nothing about creature likeness, generated
camera accuracy, pose ordering, visual quality or any promotion.

`prepare_synthetic_multiview_benchmark.py` then supplies the actual quality
gate for the fast profile: three held-back, deterministic 3D scenes with
asymmetry, thin forks and a suspended hook. Its evaluator measures the
generated orbit's declared-yaw silhouette, best apparent heading, exposed
critical-detail recall and local excess. The evaluator records the one global
SV3D-to-synthetic camera sign conversion (`synthetic_yaw = -declared_yaw mod
360`) when comparing headings; it may never estimate a different transform
per image. It rejects any scene sequence that fails those limits or strict yaw
progress. This only qualifies a bounded
`MODEL_DERIVED` pose/depth experiment; it remains independent from human
identity review and never establishes creature likeness or a real camera.

## Qualification and review

Pose/depth providers are first measured with three deterministic known
silhouettes and a 21-position camera ring. Pose ordering, yaw error, mask IoU
and scale-aligned depth error decide whether that *pose/depth role* is usable.
They do not rank creatures or replace the mandatory primary overlay and blind
visual comparison. A view-synthesis runner may emit unreviewed model-derived
frames after a technical preflight, but never declares them geometry-qualified;
identity continuity and a later pose/depth measurement are separate gates.

For a synthetic turntable, retain the literal primary outside the generated
sequence, reject duplicate synthetic zero degrees, inspect identity continuity
frame by frame and require a monotonic inferred orbit before using any frames
as secondary depth input. A failed run remains reproducible evidence but is
not eligible for fitting.

The review receipt intentionally has the narrower decision
`eligible_for_pose_depth_only`: it does not promote any `MODEL_DERIVED` frame
to `REVIEWED_SECONDARY`, nor does it authorize Blender, PMMesh, resource,
server or world mutation. `prepare_vggt_pose_depth_input.py` refuses any
unreviewed/rejected orbit, reconstructs frame 0 from the pinned isolated-subject
conditioning input, and records the ViewSet, SV3D manifest and review receipt
hashes. The legacy VGGT runner remains a point-cloud/pose-only pilot and must
still reject non-monotonic inferred poses.

The qualification receipt only enables the provider role to emit
`MODEL_DERIVED` evidence. It does not authorize a model to score, rank, fuse
or promote a creature result. View identity/continuity, literal-primary
overlay and blind visual review stay independent gates.

The protected Collector Master is out of scope. A selected proposal enters a
separate review-only Blender scene only after the normal audit gate; moving it
into an editable canonical source requires a separate user decision.

## Automodel v3: reviewed still ring and independent diagnostics

The rejected SV3D/Edit360 motion paths are not a prerequisite for the current
experiment.  Automodel v3 uses a deliberately small eight-view ring for the
Collector and gives the human, rather than a model score, the one promotion
decision:

```text
literal 0° primary + trace
  -> versioned AnatomySheet (six sacs; five bilateral leg pairs; mantle; tail)
  -> externally generated 45°...315° transparent still candidates
  -> technical isolation lint
  -> one selected provisional 8-view ring
  -> complete-ring human accept/reject
  -> DA3-Base and official VGGT-Omega diagnostics
  -> disagreement map for inspection
```

The still generator is an external staging provider, represented as
`builtin_imagegen_staged`; Automodel contains neither its credentials nor a
remote executor.  A staged candidate is always `MODEL_DERIVED`, even where a
prompt happens to repeat the source reference.  It must be a transparent
1024x1024 RGBA PNG with one connected, padded subject.  Built-in ImageGen may
return a different square raster size, so `prepare-external-still` first
hash-pins its raw PNG, rejects a clipped/opaque/debris subject *before* any
processing, then mechanically crops, uniformly scales and centers only a safe
subject into that 1024px transparent canvas.  It never inpaints, deletes or
repairs pixels.  The lint rejects an empty, clipped, opaque or detached-debris
image; it does **not** pretend to understand anatomy.

The canonical Anatomy Sheet for the Collector is
`tools/automodel/anatomy/biomass_collector.v1.json`.  It has an exact count:
one body core, six dorsal sacs, one front mantle, five *bilateral* leg pairs
(ten legs), and one tail.  It declares all yaws `0,45,...315`; it does not
make a generated rear factual.  The rear remains a labelled artistic
hypothesis for the human to accept or reject.

Only a **single complete-ring human receipt** can create a
`REVIEWED_SECONDARY` ViewSet.  Acceptance requires all four explicit
affirmations: anatomy cardinality, adjacent-ring continuity, transparent
isolation and knowledge that the hidden side is a hypothesis.  Declining the
ring creates no reviewed ViewSet.  A partial approval, an ImageGen result, an
adapter metric and cross-model agreement cannot promote a frame.

Use the fixed, local-only hand-off tools in this order (all paths below are
ignored `build/automodel/...` artifacts):

```bash
# Stage one visually inspected ImageGen still and its immutable prompt record.
python tools/automodel/multiview_ring.py prepare-external-still --help
python tools/automodel/multiview_ring.py stage-candidate --help

# Select exactly one non-primary candidate per yaw, package it for review,
# and record the user's whole-ring decision.
python tools/automodel/multiview_ring.py assemble-ring --help
python tools/automodel/multiview_ring.py build-review-package --help
python tools/automodel/multiview_ring.py record-review --help
python tools/automodel/multiview_ring.py prepare-geometry --help

# A pose/depth adapter must first pass the known synthetic 8-view role gate.
python tools/automodel/ring_benchmark.py build-fixture --help
python tools/automodel/ring_benchmark.py write-qualification --help

# Prepare exactly one fresh, qualified DA3 or official VGGT diagnostic run.
python tools/automodel/pose_depth_ring.py prepare-run --help
```

`DA3-Base` and official `VGGT-Omega` are independent pose/depth observers,
not shape authorities.  Both runners recheck their pinned source revision,
official checkpoint receipt, environment lock, accepted geometry input and
successful synthetic eight-view qualification.  They may write only camera
pose, relative depth, confidence, normalized depth diagnostics and a bounded
point cloud under the prepared run directory:

```bash
python tools/da3/run_da3_ring.py --help
python tools/vggt_omega/run_reviewed_ring.py --help
python tools/automodel/pose_depth_ring.py disagreement --help
```

The disagreement heat map is an uncertainty annotation at matching yaw; it
does not choose, combine or fuse a mesh/point cloud.  No v3 command can write
Collector Master, a canonical `.blend`, PMMesh, runtime resources, a server or
a world.  The GeometryEvidence package remains a review-only input to the
existing visual pipeline.
