#!/usr/bin/env python3
"""Static contract tests for the constrained Windows SV3D_p runner."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "sv3d_orbit" / "run_windows_sv3d_p.ps1"
EXTRACTOR = ROOT / "tools" / "sv3d_orbit" / "extract_sv3d_orbit_frames.py"
IMPORT_PROBE = ROOT / "tools" / "sv3d_orbit" / "preflight_imports.py"
SYNTHETIC_PROBE = ROOT / "tools" / "sv3d_orbit" / "prepare_synthetic_qualification.py"
CAPTURE_RUNNER = ROOT / "tools" / "sv3d_orbit" / "run_windows_sv3d_capture.ps1"
SYNTHETIC_VALIDATOR = ROOT / "tools" / "sv3d_orbit" / "validate_synthetic_qualification.py"
MULTIVIEW_PREPARER = ROOT / "tools" / "sv3d_orbit" / "prepare_synthetic_multiview_benchmark.py"
MULTIVIEW_EVALUATOR = ROOT / "tools" / "sv3d_orbit" / "evaluate_synthetic_multiview_benchmark.py"


runner = RUNNER.read_text(encoding="utf-8")
extractor = EXTRACTOR.read_text(encoding="utf-8")
probe = IMPORT_PROBE.read_text(encoding="utf-8")

for required in (
    "[ValidateSet('Preflight', 'Infer')]",
    "full_fp32_50",
    "fast_fp16_20",
    "sv3d_p_orbit_fast_fp16_20",
    "prepared_not_executed",
    "promotion_prohibited",
    "ConditioningId",
    "isolated_subject_v1",
    "generated_cutout_r01",
    "$conditioningRole",
    "conditioning_id",
    "CheckpointReceipt",
    "official_checkpoint_receipt.v1",
    "stabilityai/sv3d",
    "checkpoints\\sv3d_p.safetensors",
    "sampling_steps = 50",
    "sampling_steps = 20",
    '"--num_steps=$($profile.sampling_steps)"',
    "--azimuths_deg=[$azimuths]",
    "collect-views",
    "quality_qualification",
    "MODEL_DERIVED",
    "^[A-Za-z]:",
    "low_vram_conditioner_cpu_offload",
    "low_vram_fp16_conditioner_cpu_offload",
    "memory_profile_runner_sha256",
    "execution_profile",
    "precision",
    "function Invoke-PinnedPython",
    "$script:pinnedPythonExitCode",
    "2>&1",
    "$ErrorActionPreference = 'Continue'",
    "$ErrorActionPreference = $previousErrorAction",
    "UTF8Encoding]::new($false)",
):
    assert required in runner, required

for forbidden in (".blend", ".pmmesh", "src\\main\\resources", "server", "invoke-expression", "start-process"):
    assert forbidden not in runner.lower(), forbidden

assert "adapter_qualification" not in runner
assert "preflight_imports.py" in runner
for required in ("'--source-root', $source", "'--memory-profile', $MemoryProfile", "'--runner-root', $scriptRoot"):
    assert required in runner, required
for required in ("import cv2", "import einops", "import fire", "import imageio", "import omegaconf", "import rembg", "import torch", "import xformers", "torch.cuda.is_available()"):
    assert required in probe, required
assert "import scripts.sampling.simple_video_sample" in probe
assert "low_vram_conditioner_cpu_offload" in probe
low_vram = ROOT / "tools" / "sv3d_orbit" / "low_vram_sample.py"
low_vram_source = low_vram.read_text(encoding="utf-8")
for required in (
    "DeepFloydDataFiltering",
    'filter_kwargs["device"] = "cpu"',
    "model.conditioner.embedders",
    "torch.cuda.empty_cache()",
    "does not split the 21-frame temporal batch",
):
    assert required in low_vram_source, required

fp16_low_vram = ROOT / "tools" / "sv3d_orbit" / "low_vram_fp16_sample.py"
fp16_source = fp16_low_vram.read_text(encoding="utf-8")
for required in (
    "dtype=torch.float16",
    'to(device="cpu", dtype=torch.float16)',
    "torch.autocast(device_type=\"cuda\", dtype=torch.float16)",
    "torch.inference_mode()",
    "torch.cuda.get_device_capability(0)",
    "model.conditioner.embedders",
    "model.first_stage_model",
    "encoder.float()",
    "first_stage.float()",
    "denoiser remains FP16",
    'filter_kwargs["device"] = "cpu"',
    "avoids a second FP32 GPU",
):
    assert required in fp16_source, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "start-process"):
    assert forbidden not in fp16_source.lower(), forbidden

assert "low_vram_fp16_conditioner_cpu_offload" in probe
assert "low_vram_fp16_sample.py" in probe

synthetic = SYNTHETIC_PROBE.read_text(encoding="utf-8")
for required in (
    "synthetic_sv3d_signal_tower",
    "research_only_noncanonical",
    "promotion_prohibited",
    "technical FP16/20-step SV3D execution qualification only",
    "requires_exactly_21_frame_video",
    "does_not_qualify_creature_likeness",
    "stage_reference_bundle",
):
    assert required in synthetic, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "start-process"):
    assert forbidden not in synthetic.lower(), forbidden

validator = SYNTHETIC_VALIDATOR.read_text(encoding="utf-8")
for required in (
    "synthetic_sv3d_signal_tower",
    "sv3d_p_orbit_fast_fp16_20",
    "technical_execution_passed",
    "exactly 21 frames",
    "EXPECTED_YAWS",
    "does_not_qualify_creature_likeness",
    "does_not_establish_camera_truth",
    "refusing to overwrite immutable qualification receipt",
    "utf-8-sig",
    "_load_existing_capture_receipt",
):
    assert required in validator, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "start-process"):
    assert forbidden not in validator.lower(), forbidden

multiview_preparer = MULTIVIEW_PREPARER.read_text(encoding="utf-8")
for required in (
    "signal_tower",
    "needle_fork",
    "gantry_hook",
    "ground_truth_frames",
    "known-geometry SV3D benchmark",
    "research_only_noncanonical",
    "promotion_prohibited",
    "ELEVATION_DEGREES = 10.0",
):
    assert required in multiview_preparer, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "start-process"):
    assert forbidden not in multiview_preparer.lower(), forbidden

multiview_evaluator = MULTIVIEW_EVALUATOR.read_text(encoding="utf-8")
for required in (
    "minimum_mean_silhouette_iou",
    "exposed_critical_recall",
    "critical_local_excess",
    "strict_monotonic_predicted_yaw",
    "adapter_quality_gate_passed",
    "adapter_quality_gate_rejected",
    "does_not_qualify_creature_likeness",
):
    assert required in multiview_evaluator, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "start-process"):
    assert forbidden not in multiview_evaluator.lower(), forbidden

capture = CAPTURE_RUNNER.read_text(encoding="utf-8")
for required in (
    "Capture run manifest must remain below build/automodel.",
    "Refusing to overwrite ${Label}",
    "sv3d_inference.stdout.log",
    "sv3d_inference.stderr.log",
    "execution_capture_receipt.json",
    "research_only_noncanonical",
    "promotion_prohibited",
    "-Phase Infer",
):
    assert required in capture, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "invoke-expression", "start-process"):
    assert forbidden not in capture.lower(), forbidden

assert "FRAME_COUNT = 21" in extractor
assert "NON_PRIMARY_FRAME_COUNT = 20" in extractor
assert "if index == NON_PRIMARY_FRAME_COUNT" in extractor
assert "frame_{yaw:03d}.png" in extractor
