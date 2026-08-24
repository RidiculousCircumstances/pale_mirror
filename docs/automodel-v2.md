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
  transform of that primary and its trace, such as the transparent trace-masked
  RGBA image required by SV3D_p. It is not an additional view and cannot gain
  primary authority.

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
host. The registry therefore permits preflight only for SV3D_p, DA3-Base,
SPAR3D low-VRAM and the existing isolated Hunyuan shape runner. An enabled
preflight means only that a local experiment may be prepared; it does not mean
the result is accepted.

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
  -> optionally derives trace-masked conditioning with an exact source-size check

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
  -> copies only a human-reviewed SV3D sequence plus the pinned trace-masked
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
unreviewed/rejected orbit, reconstructs frame 0 from the pinned trace-masked
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
