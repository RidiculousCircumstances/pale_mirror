# SV3D_p local orbit runner

`run_windows_sv3d_p.ps1` is the only current SV3D execution path. It is a
specific, non-generic wrapper around the pinned Stability AI upstream
`simple_video_sample.py`; it does not run arbitrary commands and it never
receives a canonical Blender, PMMesh, resource, world or server path.

The run is staged first with:

```text
literal source primary + source-pixel trace
  -> tools/automodel/stage_reference_bundle.py --trace-masked-conditioning
  -> tools/automodel/orchestrator.py prepare-run
  -> Windows Preflight
  -> Windows Infer
  -> 20 model-derived orbit frames + literal primary ViewSet
```

The literal input stays the only `PRIMARY_TRACE` view. The upstream model is
called with 21 positions: generated `18°..360°`, then a final literal `0°`
position which upstream overwrites with its source input. The extractor drops
that final re-encoded duplicate and keeps only the twenty generated frames.

The Windows preflight requires all of the following before inference:

- source revision exactly equals `RunManifest.provenance.adapter_revision`;
- `checkpoints/sv3d_p.safetensors` exists under that isolated source checkout
  and its SHA-256 equals the manifest;
- the environment-lock file SHA-256 equals the manifest;
- CUDA plus `cv2`, `einops`, `fire`, `imageio`, `omegaconf`, `rembg`, `torch` and
  `xformers` import successfully;
- the actual pinned upstream sampler imports successfully without executing a
  model, so immediate transitive dependencies cannot surprise an immutable run;
- the literal and trace-derived conditioning inputs still match their hashes.

Preflight permits only `MODEL_DERIVED` view synthesis. It does not pretend a
view generator has passed a geometry benchmark. An orbit must be inspected as
a complete sequence, receive an explicit `eligible_for_pose_depth_only` human
receipt, and subsequently pass the independent VGGT pose-order gate before it
can be used as secondary pose/depth evidence.

After inference the wrapper invokes the Automodel collector. The collector
writes a hash-pinned `ViewSet` and execution receipt under the same ignored
run directory. A later human review is still required before *any* secondary
frame can become `REVIEWED_SECONDARY`; the narrow turntable eligibility receipt
does not do that promotion.

`full_fp32_50` with `low_vram_conditioner_cpu_offload` retains the upstream
21-frame, 576-square, 50-step sampler and its camera path, but moves the
completed conditioner and post-generation safety CLIP to CPU before denoising.

`fast_fp16_20` is a separately registered qualification profile, never an
implicit fallback.  It requires `low_vram_fp16_conditioner_cpu_offload`, loads
the official FP32 checkpoint on CPU, converts it to FP16 before CUDA transfer,
runs the fixed 21-frame sampler at exactly 20 steps under CUDA FP16 autocast,
and decodes at a separately pinned batch size.  It must first pass a synthetic
technical qualification; any creature output remains `MODEL_DERIVED` and never
bypasses the visual-review gate.

The separate `prepare_synthetic_multiview_benchmark.py` and
`evaluate_synthetic_multiview_benchmark.py` path is stricter than the
technical smoke. It builds three deterministic, known 3D scenes—an asymmetric
tower, thin fork and gantry hook—then holds their 21 exact camera renders and
semantic masks back from SV3D. The evaluator measures silhouette alignment,
apparent yaw order and exposed thin-detail recall/excess after a completed
orbit. A pass only permits the bounded pose/depth research role; it cannot
prove creature likeness, real camera truth or promote generated frames.
