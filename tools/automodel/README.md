# Automodel v2 laboratory

`tools/automodel` is a reproducible, offline laboratory for image-to-3D
experiments. It is intentionally outside Minecraft, the canonical Blender
source, and PMMesh export.

The baseline source authority is a pinned literal primary image and its trace.
Model outputs remain non-canonical hypotheses. `model_registry.v1.json` is the
sole catalogue of allowed providers; it records hardware, licence and execution
state. A provider must pass its known-geometry benchmark before it may provide
model-derived pose/depth or shape evidence.

Every run is prepared under `build/automodel/<asset>/<run>/` and requires a
hash-pinned `ReferenceBundle`. The orchestrator only prepares a manifest: it
does not download weights, run arbitrary commands, access a `.blend`, export
PMMesh, or contact a remote executor.

The current execution policy is local-only. `SV3D_p`, `DA3-Base`, SPAR3D and
the existing isolated Hunyuan shape runner are preflight candidates; all other
providers are explicitly deferred. See `docs/automodel-v2.md` for the
evidence model and handoff rules.

Useful fixed commands (all artifacts stay ignored below `build/automodel/`):

```bash
# Stage immutable inputs from a supplied source image and trace.
python tools/automodel/stage_reference_bundle.py --help

# Pin one non-executable local experiment.
python tools/automodel/orchestrator.py prepare-run --help

# Normalise already-produced local outputs; neither command runs a model.
python tools/automodel/orchestrator.py collect-views --help
python tools/automodel/orchestrator.py collect-geometry --help
python tools/automodel/orchestrator.py collect-shape --help

# Inspect a completed 21-position SV3D orbit and record its narrow human gate.
python tools/automodel/orchestrator.py review-turntable --help
python tools/automodel/orchestrator.py record-turntable-review --help

# Prepare the only permitted SV3D-to-VGGT hand-off.
python tools/automodel/prepare_vggt_pose_depth_input.py --help
```

The first model-specific wrapper is
[`tools/sv3d_orbit/`](../sv3d_orbit/README.md). It is blocked until its
Windows preflight passes; its output collector is already covered by the
Automodel contract test.
