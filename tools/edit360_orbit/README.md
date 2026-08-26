# Edit360 dual-anchor orbit experiment

This directory contains one bounded Windows-only adapter around upstream
Edit360 revision `fe1f386f7dff9ff3e48b23d67e7c218848f34541`.  It uses the
official `stabilityai/sv3d` `sv3d_u.safetensors` checkpoint and an immutable
Automodel manifest; it is not a generic model runner.

The experiment takes exactly two noncanonical inputs: one inspected white
front conditioning and one inspected, `MODEL_DERIVED` opposite anchor:
either a verified opposite broadside or an actual rear-right three-quarter.
The anchor's declared relative 180-degree relation is only a conditioning
hint, never a recovered camera pose. A reviewer must establish that its
head/tail arrangement is opposite to the primary; a horizontal flip, mirror
or same-side broadside is rejected. The front is normally the trace-derived white
frame, but may instead be a separately reviewed clean `MODEL_DERIVED` cutout
when a literal trace mask visibly damages textured subject pixels.  That
exception remains visual-preview-only: it cannot enter a `ViewSet`, VGGT,
geometry, Blender, PMMesh, runtime or canonical-art workflow.  The literal
primary remains the sole likeness anchor.  Upstream `sv3d_u` dual mode does **not**
expose a commanded azimuth path: its `1.png` is a re-encoded conditioning end
frame, while `2.png` through `21.png` are only ordered model sequence frames.
The adapter therefore writes an indexed visual review package, never a
`ViewSet`, declared yaw, VGGT input, pose/depth evidence or geometry proposal.

`prepare_opposite_anchor.py` and `prepare_generated_front.py` are intentionally
restricted to the baked neutral-checkerboard form returned by the image
generator. They derive transparent pixels only by boundary-connected neutral
checkerboard removal, drop tiny unconnected debris and write a white
576-square input. Their result remains `MODEL_DERIVED` and cannot enter VGGT,
canonical Blender, PMMesh, resources, server or world state.

The unmodified upstream profile uses the 9.36 GB FP32 `sv3d_u` checkpoint and
is deferred pending a separate >=16 GiB preflight.  The only local Windows
profile is instead `edit360_dual_anchor_orbit_fast_fp16_20`: it loads on CPU,
casts the denoiser-bearing model to FP16 before CUDA, keeps image-codec
boundaries FP32, moves the safety filter and conditioner off CUDA after both
conditions are prepared, and runs exactly 20 steps with `decoding_t=1`.
An OOM, import failure, hash mismatch or malformed 21-frame output fails
closed and leaves the prepared run as reproducible negative evidence. A
completed sequence is visual-only and can never be upgraded to geometry by
review alone.
