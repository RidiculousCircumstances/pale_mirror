#!/usr/bin/env python3
"""Focused contracts for the noncanonical Automodel v2 laboratory."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from adapters import collect_geometry_evidence, collect_shape_proposal, collect_view_synthesis  # noqa: E402
from benchmark import FRAME_COUNT, build_fixture_corpus, evaluate_report, write_adapter_qualification  # noqa: E402
from contracts import (  # noqa: E402
    AutomodelContractError,
    EvidenceTier,
    validate_geometry_evidence,
    validate_reference_bundle,
    validate_run_manifest,
    validate_shape_proposal,
    validate_view_set,
)
from orchestrator import prepare_run  # noqa: E402
from registry import load_registry, require_local_preflight  # noqa: E402
from review import (  # noqa: E402
    build_review_package,
    build_turntable_review_package,
    promote_secondary_frame,
    record_turntable_review,
    write_depth_disagreement,
)
from prepare_vggt_pose_depth_input import prepare as prepare_vggt_pose_depth_input  # noqa: E402
from stage_reference_bundle import stage_reference_bundle  # noqa: E402
from checkpoint_receipt import write_official_checkpoint_receipt  # noqa: E402
from download_official_checkpoint import download  # noqa: E402
from environment_lock import write_environment_lock  # noqa: E402


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


HASH = "a" * 64


class AutomodelContractsTest(unittest.TestCase):
    def test_registry_keeps_heavy_models_deferred(self) -> None:
        registry = load_registry()
        self.assertTrue(registry["sv3d_p_orbit"].local_preflight)
        self.assertTrue(registry["sv3d_p_orbit_fast_fp16_20"].local_preflight)
        self.assertEqual("windows_gpu", registry["sv3d_p_orbit_fast_fp16_20"].executor)
        self.assertEqual("deferred_hardware", registry["edit360_dual_anchor_orbit"].status)
        self.assertTrue(registry["edit360_dual_anchor_orbit_fast_fp16_20"].local_preflight)
        self.assertEqual("windows_gpu", registry["edit360_dual_anchor_orbit_fast_fp16_20"].executor)
        self.assertTrue(registry["vggt_official"].local_preflight)
        self.assertEqual("deferred_hardware", registry["trellis2"].status)
        self.assertEqual("deferred_runtime", registry["hunyuan_paint"].status)
        with self.assertRaisesRegex(AutomodelContractError, "requires 8 GiB"):
            require_local_preflight(registry, "da3_base", 7)
        with self.assertRaisesRegex(AutomodelContractError, "not enabled"):
            require_local_preflight(registry, "trellis2", 64)

    def test_reference_requires_one_primary_authority(self) -> None:
        bundle = {
            "schema": "pale_mirror.automodel.reference_bundle.v1",
            "asset_id": "biomass_collector",
            "scope": "research_only_noncanonical",
            "primary": {"role": "sole_likeness_anchor", "tier": "PRIMARY_TRACE", "file": "build/automodel/input/primary.png", "sha256": HASH},
            "primary_trace": {"file": "build/automodel/input/trace.json", "sha256": HASH},
            "anchors": [{"id": "generated_018", "tier": "MODEL_DERIVED", "file": "build/automodel/input/018.png", "sha256": HASH}],
        }
        validate_reference_bundle(bundle)
        bundle["anchors"][0]["tier"] = "PRIMARY_TRACE"
        with self.assertRaisesRegex(AutomodelContractError, "only primary"):
            validate_reference_bundle(bundle)

    def test_official_checkpoint_receipt_pins_only_registered_origin(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-checkpoint-") as temporary:
            root = Path(temporary)
            checkpoint = root / "private" / "vggt_omega_1b_512.pt"
            checkpoint.parent.mkdir(parents=True)
            checkpoint.write_bytes(b"official-model-bytes")
            output = root / "build" / "automodel" / "collector" / "official_vggt" / "checkpoint_receipt.json"
            receipt = write_official_checkpoint_receipt(
                "vggt_official",
                checkpoint,
                output,
                repository_root=root,
            )
            self.assertEqual("facebook/VGGT-Omega", receipt["official_origin"]["repository"])
            self.assertEqual("private_local_host", receipt["checkpoint"]["storage"])
            self.assertNotIn(str(checkpoint), json.dumps(receipt))
            with self.assertRaisesRegex(AutomodelContractError, "overwrite"):
                write_official_checkpoint_receipt(
                    "vggt_official",
                    checkpoint,
                    output,
                    repository_root=root,
                )

    def test_official_download_rejects_unknown_registry_entry_before_network(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-download-") as temporary:
            with self.assertRaisesRegex(ValueError, "no official checkpoint contract"):
                download("not_a_model", Path(temporary))

    def test_environment_lock_is_redacted_immutable_build_artifact(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-environment-") as temporary:
            root = Path(temporary)
            output = root / "build" / "automodel" / "environment" / "lock.json"
            lock = write_environment_lock(output, repository_root=root)
            self.assertEqual("research_only_noncanonical", lock["scope"])
            self.assertTrue(lock["contract"]["contains_no_credentials"])
            self.assertTrue(lock["contract"]["contains_no_private_paths"])
            self.assertGreater(len(lock["distributions"]), 0)
            with self.assertRaisesRegex(AutomodelContractError, "overwrite"):
                write_environment_lock(output, repository_root=root)

    def test_human_receipt_is_required_for_secondary_promotion(self) -> None:
        view_set = {
            "schema": "pale_mirror.automodel.view_set.v1",
            "asset_id": "biomass_collector",
            "frames": [
                {"id": "literal_000", "tier": "PRIMARY_TRACE", "file": "build/automodel/test/literal.png", "sha256": HASH},
                {"id": "generated_018", "tier": "MODEL_DERIVED", "file": "build/automodel/test/018.png", "sha256": HASH},
            ],
        }
        validate_view_set(view_set)
        promoted = promote_secondary_frame(view_set, "generated_018", "reviewer_a", "accept_identity_and_continuity")
        self.assertEqual(EvidenceTier.REVIEWED_SECONDARY.value, promoted["frames"][1]["tier"])
        self.assertEqual("accepted_by_human", promoted["frames"][1]["review"]["status"])
        with self.assertRaisesRegex(AutomodelContractError, "positive identity"):
            promote_secondary_frame(view_set, "generated_018", "reviewer_a", "looks_reasonable")

    def test_staging_preserves_literal_primary_and_marks_conditioning_as_non_authority(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-stage-") as temporary:
            repository = Path(temporary)
            artist_input = repository / "artist_primary.png"
            artist_trace = repository / "artist_trace.json"
            Image.new("RGB", (8, 8), (120, 30, 20)).save(artist_input)
            artist_trace.write_text(
                json.dumps(
                    {
                        "source": {"size": [8, 8]},
                        "sampling": {"grid_px": 1},
                        "rows": [{"y": 1, "runs": [[1, 7]]}],
                    }
                ),
                encoding="utf-8",
            )
            run_directory = repository / "build" / "automodel" / "biomass_collector" / "staged_input"
            bundle = stage_reference_bundle(
                "biomass_collector",
                artist_input,
                artist_trace,
                run_directory,
                isolated_subject_conditioning=True,
                repository_root=repository,
            )
            validate_reference_bundle(bundle)
            self.assertEqual("sole_likeness_anchor", bundle["primary"]["role"])
            self.assertEqual("isolated_subject_v1", bundle["model_inputs"][0]["id"])
            self.assertEqual("conditioning_only", bundle["model_inputs"][0]["role"])
            self.assertTrue((run_directory / "input" / "isolated_subject_conditioning_v1.png").is_file())
            self.assertTrue((run_directory / "input" / "isolated_subject_mask_v1.png").is_file())
            self.assertTrue((run_directory / "input" / "isolated_subject_conditioning_v1.json").is_file())
            with Image.open(run_directory / "input" / "isolated_subject_sv3d_effective_v1.png") as review:
                self.assertEqual((576, 576), review.size)
                self.assertEqual("RGB", review.mode)
                self.assertEqual((255, 255, 255), review.getpixel((0, 0)))

    def test_isolated_subject_conditioning_removes_trace_dust_and_fills_tiny_holes(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-isolated-subject-") as temporary:
            repository = Path(temporary)
            artist_input = repository / "artist_primary.png"
            artist_trace = repository / "artist_trace.json"
            Image.new("RGB", (12, 12), (120, 30, 20)).save(artist_input)
            artist_trace.write_text(
                json.dumps(
                    {
                        "source": {"size": [12, 12]},
                        "sampling": {"grid_px": 1},
                        "rows": [
                            {"y": 3, "runs": [[3, 7]]},
                            {"y": 4, "runs": [[3, 4], [5, 7]]},
                            {"y": 5, "runs": [[3, 7]]},
                            {"y": 6, "runs": [[3, 7]]},
                            {"y": 10, "runs": [[10, 11]]},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            run_directory = repository / "build" / "automodel" / "biomass_collector" / "isolated_input"
            bundle = stage_reference_bundle(
                "biomass_collector",
                artist_input,
                artist_trace,
                run_directory,
                isolated_subject_conditioning=True,
                repository_root=repository,
            )
            preparation = bundle["model_inputs"][0]["preparation"]
            receipt = json.loads((repository / preparation["receipt_file"]).read_text(encoding="utf-8"))
            self.assertEqual(1, receipt["cleanup"]["removed_components"])
            self.assertEqual(1, receipt["cleanup"]["filled_holes"])
            with Image.open(repository / preparation["mask_file"]) as mask:
                self.assertEqual(255, mask.getpixel((4, 4)))
                self.assertEqual(0, mask.getpixel((10, 10)))

    def test_generated_cutout_is_model_derived_preview_only(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-generated-cutout-") as temporary:
            repository = Path(temporary)
            artist_input = repository / "artist_primary.png"
            artist_trace = repository / "artist_trace.json"
            generated_cutout = repository / "generated_cutout.png"
            Image.new("RGB", (12, 8), (120, 30, 20)).save(artist_input)
            artist_trace.write_text(
                json.dumps({"source": {"size": [12, 8]}, "sampling": {"grid_px": 1}, "rows": [{"y": 1, "runs": [[1, 11]]}]}),
                encoding="utf-8",
            )
            generated = Image.new("RGBA", (12, 8), (0, 0, 0, 0))
            for y in range(2, 6):
                for x in range(3, 9):
                    generated.putpixel((x, y), (20, 100, 160, 255))
            generated.save(generated_cutout)
            run_directory = repository / "build" / "automodel" / "biomass_collector" / "generated_input"
            bundle = stage_reference_bundle(
                "biomass_collector",
                artist_input,
                artist_trace,
                run_directory,
                isolated_subject_conditioning=False,
                generated_conditioning_source=generated_cutout,
                repository_root=repository,
            )
            validate_reference_bundle(bundle)
            conditioning = bundle["model_inputs"][0]
            self.assertEqual("generated_cutout_r01", conditioning["id"])
            self.assertEqual(EvidenceTier.MODEL_DERIVED.value, conditioning["tier"])
            self.assertEqual(["primary"], conditioning["derives_from"])
            self.assertTrue(conditioning["preparation"]["geometry_or_canonical_use_prohibited"])
            self.assertTrue((run_directory / "input" / "generated_cutout_r01_sv3d_effective.png").is_file())

    def test_generated_cutout_requires_real_transparency(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-opaque-cutout-") as temporary:
            repository = Path(temporary)
            artist_input = repository / "artist_primary.png"
            artist_trace = repository / "artist_trace.json"
            generated_cutout = repository / "opaque.png"
            Image.new("RGB", (8, 8), (120, 30, 20)).save(artist_input)
            artist_trace.write_text(
                json.dumps({"source": {"size": [8, 8]}, "sampling": {"grid_px": 1}, "rows": [{"y": 1, "runs": [[1, 7]]}]}),
                encoding="utf-8",
            )
            Image.new("RGBA", (8, 8), (20, 100, 160, 255)).save(generated_cutout)
            with self.assertRaisesRegex(AutomodelContractError, "transparent background"):
                stage_reference_bundle(
                    "biomass_collector",
                    artist_input,
                    artist_trace,
                    repository / "build" / "automodel" / "biomass_collector" / "opaque_input",
                    isolated_subject_conditioning=False,
                    generated_conditioning_source=generated_cutout,
                    repository_root=repository,
                )

    def test_run_and_review_remain_inside_ignored_build_root(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-test-") as temporary:
            repository = Path(temporary)
            inputs = repository / "build" / "automodel" / "input"
            inputs.mkdir(parents=True)
            # Source authority is frequently supplied as JPEG.  Collection
            # must preserve that literal byte-for-byte primary rather than
            # requiring a lossy format conversion after inference.
            primary = inputs / "primary.jpg"
            Image.new("RGB", (8, 8), (30, 20, 10)).save(primary)
            trace = inputs / "trace.json"
            trace.write_text("{}\n", encoding="utf-8")
            conditioning = inputs / "isolated_subject_v1.png"
            Image.new("RGBA", (8, 8), (30, 20, 10, 255)).save(conditioning)
            bundle = {
                "schema": "pale_mirror.automodel.reference_bundle.v1",
                "asset_id": "biomass_collector",
                "scope": "research_only_noncanonical",
                "primary": {"role": "sole_likeness_anchor", "tier": "PRIMARY_TRACE", "file": "build/automodel/input/primary.jpg", "sha256": digest(primary)},
                "primary_trace": {"file": "build/automodel/input/trace.json", "sha256": digest(trace)},
                "anchors": [],
                "model_inputs": [
                    {
                        "id": "isolated_subject_v1",
                        "role": "conditioning_only",
                        "file": "build/automodel/input/isolated_subject_v1.png",
                        "sha256": digest(conditioning),
                        "derives_from": ["primary", "primary_trace"],
                        "preparation": {
                            "profile": "isolated_subject_v1",
                            "mask_file": "build/automodel/input/isolated_subject_mask.png",
                            "mask_sha256": HASH,
                            "receipt_file": "build/automodel/input/isolated_subject_receipt.json",
                            "receipt_sha256": HASH,
                            "effective_sv3d_review_file": "build/automodel/input/isolated_subject_sv3d_effective.png",
                            "effective_sv3d_review_sha256": HASH,
                        },
                    }
                ],
            }
            bundle_path = inputs / "bundle.json"
            bundle_path.write_text(json.dumps(bundle), encoding="utf-8")
            run_directory = repository / "build" / "automodel" / "biomass_collector" / "sv3d_seed_01"
            manifest = prepare_run(bundle_path, "sv3d_p_orbit", run_directory, available_vram_gib=8, adapter_revision=HASH, checkpoint_sha256=HASH, environment_lock_sha256=HASH, seed=7, repository_root=repository)
            validate_run_manifest(manifest)
            self.assertTrue((run_directory / "run_manifest.json").is_file())

            orbit = run_directory / "outputs" / "nonprimary_orbit"
            orbit.mkdir(parents=True)
            generated = []
            for index in range(1, 21):
                frame = orbit / f"frame_{index:03d}.png"
                Image.new("RGB", (8, 8), (40 + index, 20, 10)).save(frame)
                generated.append(frame)
            view_path = run_directory / "view_set.json"
            view_set = collect_view_synthesis(
                run_directory / "run_manifest.json",
                primary,
                generated,
                view_path,
                repository_root=repository,
            )
            validate_view_set(view_set)
            self.assertTrue((run_directory / "execution_receipt_view_set.json").is_file())
            turntable_package = build_turntable_review_package(
                view_path,
                run_directory / "turntable_review",
                repository,
            )
            self.assertTrue((run_directory / "turntable_review" / "turntable_contact_sheet.png").is_file())
            self.assertTrue(turntable_package["promotion_prohibited"])
            turntable_receipt_path = run_directory / "turntable_review" / "turntable_review_receipt.json"
            turntable_receipt = record_turntable_review(
                run_directory / "turntable_review" / "turntable_review_package.json",
                turntable_receipt_path,
                "visual_reviewer",
                "eligible_for_pose_depth_only",
                "All declared headings are present; use only as uncalibrated pose/depth input.",
                repository_root=repository,
            )
            self.assertTrue(turntable_receipt["promotion_prohibited"])
            self.assertEqual(EvidenceTier.MODEL_DERIVED.value, view_set["frames"][1]["tier"])
            vggt_input = prepare_vggt_pose_depth_input(
                view_path,
                turntable_receipt_path,
                run_directory / "vggt_pose_depth_input",
                repository_root=repository,
            )
            self.assertEqual(21, vggt_input["frame_count"])
            self.assertEqual("literal_primary_isolated_subject", vggt_input["frames"][0]["source"])
            with self.assertRaisesRegex(AutomodelContractError, "overwrite"):
                prepare_vggt_pose_depth_input(
                    view_path,
                    turntable_receipt_path,
                    run_directory / "vggt_pose_depth_input",
                    repository_root=repository,
                )

            geometry_run = repository / "build" / "automodel" / "biomass_collector" / "da3_seed_01"
            prepare_run(bundle_path, "da3_base", geometry_run, available_vram_gib=8, adapter_revision=HASH, checkpoint_sha256=HASH, environment_lock_sha256=HASH, seed=7, repository_root=repository)
            depth_a = geometry_run / "outputs" / "depth.png"
            depth_b = geometry_run / "outputs" / "depth_comparison.png"
            pose = geometry_run / "outputs" / "poses.json"
            depth_a.parent.mkdir(parents=True)
            Image.new("L", (8, 8), 10).save(depth_a)
            Image.new("L", (8, 8), 35).save(depth_b)
            pose.write_text("{}\n", encoding="utf-8")
            geometry_path = geometry_run / "geometry_evidence.json"
            geometry = collect_geometry_evidence(
                geometry_run / "run_manifest.json",
                [("depth", depth_a), ("camera_pose", pose)],
                geometry_path,
                repository_root=repository,
            )
            validate_geometry_evidence(geometry)
            self.assertTrue((geometry_run / "execution_receipt_geometry.json").is_file())
            review = build_review_package(view_path, geometry_path, run_directory / "review", repository)
            self.assertTrue((run_directory / "review" / "review_package.json").is_file())
            self.assertTrue(review["promotion_prohibited"])
            heat = write_depth_disagreement(depth_a, depth_b, geometry_run / "depth_disagreement.png")
            self.assertEqual(25.0, heat["mean_absolute_difference"])

            shape_run = repository / "build" / "automodel" / "biomass_collector" / "hunyuan_seed_01"
            prepare_run(bundle_path, "hunyuan_shape", shape_run, available_vram_gib=8, adapter_revision=HASH, checkpoint_sha256=HASH, environment_lock_sha256=HASH, seed=7, repository_root=repository)
            shape = shape_run / "outputs" / "candidate.glb"
            shape.parent.mkdir(parents=True)
            shape.write_bytes(b"glTF\x02\x00\x00\x00")
            shape_path = shape_run / "shape_proposal.json"
            proposal = collect_shape_proposal(shape_run / "run_manifest.json", [shape], shape_path, repository_root=repository)
            validate_shape_proposal(proposal)
            self.assertTrue((shape_run / "execution_receipt_shape.json").is_file())

    def test_benchmark_catches_pose_order_failure(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-corpus-") as temporary:
            repository = Path(temporary)
            corpus_directory = repository / "build" / "automodel" / "fixtures"
            corpus = build_fixture_corpus(corpus_directory)
            self.assertEqual(3, len(corpus["objects"]))
            good = {"observations": [{"yaw_degrees": index * 18.0, "mask_iou": 0.99, "depth_error": 0.02} for index in range(FRAME_COUNT)]}
            self.assertTrue(evaluate_report(good)["qualified_for_model_derived_geometry"])
            receipt = write_adapter_qualification("da3_base", corpus_directory / "corpus.json", good, corpus_directory / "da3_qualification.json", repository_root=repository)
            self.assertEqual("adapter_role_qualified", receipt["conclusion"])
            bad = {"observations": [{"yaw_degrees": 0.0, "mask_iou": 0.99, "depth_error": 0.02} for _ in range(FRAME_COUNT)]}
            self.assertFalse(evaluate_report(bad)["qualified_for_model_derived_geometry"])


if __name__ == "__main__":
    unittest.main()
