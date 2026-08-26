#!/usr/bin/env python3
"""Static boundary tests for the narrow Windows Edit360 adapter."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "edit360_orbit" / "run_windows_edit360.ps1"
CAPTURE = ROOT / "tools" / "edit360_orbit" / "run_windows_edit360_capture.ps1"
PREPARER = ROOT / "tools" / "edit360_orbit" / "prepare_opposite_anchor.py"
GENERATED_FRONT_PREPARER = ROOT / "tools" / "edit360_orbit" / "prepare_generated_front.py"
PROBE = ROOT / "tools" / "edit360_orbit" / "preflight_imports.py"
LOW_VRAM = ROOT / "tools" / "edit360_orbit" / "low_vram_fp16_sample_two.py"
SEQUENCE_REVIEW = ROOT / "tools" / "edit360_orbit" / "review_edit360_sequence.py"
INPUT_REVIEW = ROOT / "tools" / "edit360_orbit" / "record_input_visual_review.py"

runner = RUNNER.read_text(encoding="utf-8")
for required in (
    "edit360_dual_anchor_orbit",
    "prepared_not_executed",
    "promotion_prohibited",
    "conditioning:edit360_front_white_r01",
    "anchor:edit360_opposite_broadside_r01",
    "checkpoints\\sv3d_u.safetensors",
    "stabilityai/sv3d",
    "sv3d_u.safetensors",
    "'--mode', 'two'",
    "'--anchor-view-angle', '180'",
    "bounded_fp16_20",
    "upstream_fp32_50",
    "'--num-steps', \"$($profile.sampling_steps)\"",
    "'--decoding-t', \"$DecodingT\"",
    "review_edit360_sequence.py",
    "output_authority = 'MODEL_DERIVED'",
    "quality_qualification",
    "InputVisualReview",
    "$inputVisualReviewRecord",
    "function Get-RequiredJsonProperty",
    "missing required property",
    "edit360_generated_front_white_r01",
    "requires a matching explicit visual review",
    "function Invoke-PinnedPython",
    "function Resolve-RunManifest",
    "Run manifest must remain below build/automodel.",
    "verified_opposite_broadside",
    "actual_rear_right_three_quarter",
    "$ErrorActionPreference = 'Continue'",
):
    assert required in runner, required
for forbidden in (".blend", ".pmmesh", "src\\main\\resources", "server", "invoke-expression", "start-process", "collect-views"):
    assert forbidden not in runner.lower(), forbidden

capture = CAPTURE.read_text(encoding="utf-8")
for required in ("Capture run manifest must remain below build/automodel.", "function Resolve-RunManifest", "edit360_inference.stdout.log", "execution_capture_receipt.json", "promotion_prohibited", "InputVisualReview"):
    assert required in capture, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "invoke-expression", "start-process"):
    assert forbidden not in capture.lower(), forbidden

low_vram = LOW_VRAM.read_text(encoding="utf-8")
for required in ("original_sample_two", "model = upstream.instantiate_from_config(config.model).to(device=\"cpu\", dtype=torch.float16)", "first_stage.float()", "embedder.to(\"cpu\")", "torch.autocast(device_type=\"cuda\", dtype=torch.float16)", "--input-path-f", "--input-path-b", "upstream.sample_two"):
    assert required in low_vram, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "subprocess", "start-process"):
    assert forbidden not in low_vram.lower(), forbidden

sequence_review = SEQUENCE_REVIEW.read_text(encoding="utf-8")
for required in ("MODEL_SEQUENCE_COUNT = 20", "_manifest_input_any", "edit360_generated_front_white_r01", "camera_pose_or_yaw", "not_exposed_by_edit360_upstream", "view_set_prohibited", "vggt_or_geometry_use_prohibited"):
    assert required in sequence_review, required
for forbidden in ("declared_yaw_degrees\": float", ".blend", ".pmmesh", "src/main/resources", "server", "subprocess", "start-process"):
    assert forbidden not in sequence_review.lower(), forbidden

preparer = PREPARER.read_text(encoding="utf-8")
for required in ("boundary checkerboard", "CHECKERBOARD_MIN_BRIGHTNESS", "MODEL_DERIVED", "geometry_or_canonical_use_prohibited", "edit360_opposite_broadside_r01_alpha.png"):
    assert required in preparer, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "subprocess", "start-process"):
    assert forbidden not in preparer.lower(), forbidden

generated_front_preparer = GENERATED_FRONT_PREPARER.read_text(encoding="utf-8")
for required in (
    "boundary_connected_neutral_checkerboard_only",
    "low front-left three-quarter",
    "MODEL_DERIVED",
    "geometry_or_canonical_use_prohibited",
    "input_alpha_was_opaque",
):
    assert required in generated_front_preparer, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "subprocess", "start-process"):
    assert forbidden not in generated_front_preparer.lower(), forbidden

probe = PROBE.read_text(encoding="utf-8")
for required in ("scripts.sampling.simple_video_sample", "torch.cuda.is_available()", "tyro", "rembg", "xformers"):
    assert required in probe, required

input_review = INPUT_REVIEW.read_text(encoding="utf-8")
for required in ("accepted_for_noncanonical_visual_preview_only", "operator_visual_inspection_of_exact_staged_pixels", "non_white_border_pixels", "edit360_generated_front_white_r01", "verified_opposite_broadside", "actual_rear_right_three_quarter", "view_set_vggt_geometry_canonical_use_prohibited"):
    assert required in input_review, required
for forbidden in (".blend", ".pmmesh", "src/main/resources", "server", "subprocess", "start-process"):
    assert forbidden not in input_review.lower(), forbidden
