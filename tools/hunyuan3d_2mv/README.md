# Private Hunyuan3D-2mv trial

This is a controlled, shape-only four-view volume-proposal trial for the
Biomass Collector.  It is separate from Blender, gameplay, and canonical
assets.  The user-supplied primary image remains the sole likeness authority;
the generated turntable only conditions an unaccepted diagnostic volume.

The pinned trial uses the official `Tencent-Hunyuan/Hunyuan3D-2` repository at
`f8db63096c8282cb27354314d896feba5ba6ff8a` and the official
`tencent/Hunyuan3D-2mv` shape checkpoint.  It uses CPU offload, runs no texture
generation, and never accepts a candidate without the normal primary overlay
and diagnostic-frame review.

After the controlled Blender workspace has been synchronized, run on the
private Windows host. The bootstrap reuses only the existing verified Python
binary to create a separate venv; it never adds a system-wide Python install:

```powershell
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\install_hunyuan2mv.ps1 -Phase Bootstrap
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\install_hunyuan2mv.ps1 -Phase Requirements
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\install_hunyuan2mv.ps1 -Phase Validate
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview.ps1 -Phase Prepare
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview.ps1 -Phase Inference
```

The host has less GPU memory than the official shape-only recommendation, so
out-of-memory is a valid recorded result.  Do not reduce the source image,
invent additional views, enable texture generation or replace the fixed
candidate label to conceal such a result.

## Network transfer

The fixed runner sets `HF_HUB_DISABLE_XET=1` before it downloads the public
shape checkpoint. On the private Windows host this avoids Xet's aggressive
parallel transfer collapse; it changes only the transport, never inputs,
model revision, seed or inference parameters.

It stages only `config.yaml` and `model.fp16.safetensors` in its private
checkpoint directory. The official convenience loader otherwise requests the
mutually exclusive legacy `.ckpt` too; that duplicate is neither needed nor
permitted by this safetensors-only trial.

The official pipeline is constructed on CPU and only then gets its documented
sequential CPU-offload hooks for CUDA. Constructing it directly on the 8 GiB
GPU would require every component to fit before offload is enabled.

The private Windows host has 16 GiB RAM. The runner therefore consumes the
official safetensor state one component at a time and releases each source
group after it is loaded into fp16 CPU modules. This avoids the upstream
loader's short-lived double copy, but neither changes nor retrains the model.

## Linux CPU fallback

When the Windows host cannot construct the fixed 4.9 GiB shape model inside
its physical RAM budget, use the isolated Linux CPU fallback. It runs the
identical four pinned views, checkpoint, seed, resolution and shape-only
contract, but explicitly selects CPU fp32 because CPU fp16 kernels are not a
safe assumption for this upstream stack. It is intentionally slow and remains
an unaccepted review artifact only.

```bash
export PALE_MIRROR_HARVESTER_REFERENCES=/private/path/to/harvester_references
tools/hunyuan3d_2mv/install_hunyuan2mv_cpu.sh Bootstrap
tools/hunyuan3d_2mv/install_hunyuan2mv_cpu.sh Requirements
tools/hunyuan3d_2mv/install_hunyuan2mv_cpu.sh Validate
tools/hunyuan3d_2mv/run_collector_multiview_cpu.sh
```

## Upstream liveness probe

Before interpreting a Collector proposal, run the same low-VRAM loader against
the pinned upstream example views.  This determines whether a bad candidate is
caused by our Windows CPU-offload integration or by the Collector's
conditioning images.  The probe uses the upstream demonstration parameters
(50 diffusion steps, octree 380, 20,000 extraction chunks), writes only an
ignored diagnostic artifact, and can never become a Pale Mirror asset:

```powershell
python E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_hunyuan2mv_upstream_probe.py `
  --views-dir E:\PaleMirror\workspace\pale-mirror\build\inference\hunyuan3d-2mv\vendor\Hunyuan3D-2\assets\example_mv_images\1 `
  --candidate E:\PaleMirror\workspace\pale-mirror\build\inference\hunyuan3d-2mv\probes\upstream_example_1\candidate.glb `
  --model-repository E:\PaleMirror\workspace\pale-mirror\build\inference\hunyuan3d-2mv\vendor\Hunyuan3D-2 `
  --checkpoint-root E:\PaleMirror\models\hunyuan3d-2mv `
  --device cuda
```

## Primary-anchored Collector trials

`hunyuan2mv_v01` is rejected: it conditioned only on uncalibrated generated
turntable crops and omitted the supplied authority image. `v02` proved that
the full loader and shape model can make one coherent volume, but its primary
image was incorrectly assigned to the `left` slot. The pinned upstream
`MVImageProcessorV2` defines the order as `front`, clockwise 90°, `back`,
clockwise 270°. Therefore `v03` assigns the literal trace-cut primary image to
`front` and uses three independently reviewed, single-subject ImageGen views
only for the remaining clockwise volume hypotheses. Every trial is diagnostic
only, uses the ordinary 50-step schedule and remains runtime-export forbidden:

```powershell
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview_primary_anchored.ps1 -Trial hunyuan2mv_v03_primary_front -Phase Prepare
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview_primary_anchored.ps1 -Trial hunyuan2mv_v03_primary_front -Phase Inference
```

`hunyuan2mv_v04_calibrated_secondary` is one bounded comparison using a new
neutral-background secondary turntable. It retains the identical literal front
input, trace, model revision, seed and 50-step schedule, so any difference is
attributable only to the secondary hidden-side hypotheses. It is not a new
likeness source and may neither overwrite nor promote v03:

```powershell
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview_primary_anchored.ps1 -Trial hunyuan2mv_v04_calibrated_secondary -Phase Prepare
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview_primary_anchored.ps1 -Trial hunyuan2mv_v04_calibrated_secondary -Phase Inference
```

`hunyuan2mv_v05_canonical_turntable` replaces v04's independently generated
secondary images with the human-reviewed source-local cycle pinned in
`biomass_collector_turntable_v02.provenance.json`: literal source-local 0°,
tail end-on 90°, opposite broadside 180°, then head end-on 270°. The two
remaining generated views are Blender-only diagnostics and are deliberately
not passed to Hunyuan. As with every other trial, output is a shape-only,
non-exportable volume hypothesis; the literal source trace remains the
primary-likeness authority.

```powershell
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview_primary_anchored.ps1 -Trial hunyuan2mv_v05_canonical_turntable -Phase Prepare
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\hunyuan3d_2mv\run_collector_multiview_primary_anchored.ps1 -Trial hunyuan2mv_v05_canonical_turntable -Phase Inference
```
