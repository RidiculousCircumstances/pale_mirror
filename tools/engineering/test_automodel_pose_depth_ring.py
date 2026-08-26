#!/usr/bin/env python3
"""Contracts for the independent DA3/VGGT reviewed-ring hand-off."""

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

from contracts import AutomodelContractError  # noqa: E402
from pose_depth_ring import (  # noqa: E402
    collect_pose_depth_evidence,
    load_accepted_geometry_input,
    prepare_pose_depth_run,
    write_disagreement_report,
)
from ring_benchmark import build_fixture, write_qualification  # noqa: E402


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PoseDepthRingTest(unittest.TestCase):
    def _geometry_input(self, repository: Path) -> Path:
        directory = repository / "build" / "automodel" / "biomass_collector" / "fixture"
        directory.mkdir(parents=True)
        frames = []
        reviewed_frames = []
        for yaw in range(0, 360, 45):
            image = directory / f"{yaw:03d}.png"
            Image.new("RGBA", (8, 8), (yaw % 256, 20, 40, 255)).save(image)
            frame = {
                "id": "primary_000" if yaw == 0 else f"secondary_{yaw:03d}",
                "yaw_degrees": yaw,
                "tier": "PRIMARY_TRACE" if yaw == 0 else "REVIEWED_SECONDARY",
                "role": "sole_likeness_anchor" if yaw == 0 else "provisional_secondary",
                "file": image.relative_to(repository).as_posix(),
                "sha256": digest(image),
            }
            if yaw:
                frame["review"] = {"status": "accepted_in_complete_ring", "reviewer": "user"}
                frame["candidate"] = {"candidate_id": f"fixture_{yaw:03d}"}
            frames.append(dict(frame))
            reviewed_frames.append(frame)
        reviewed = {
            "schema": "pale_mirror.automodel.reviewed_view_set.v1",
            "asset_id": "biomass_collector",
            "scope": "research_only_noncanonical",
            "promotion_prohibited": True,
            "frames": reviewed_frames,
            "ring_review": {"decision": "accepted_complete_ring", "reviewer": "user"},
        }
        reviewed_path = directory / "reviewed_view_set.json"
        reviewed_path.write_text(json.dumps(reviewed), encoding="utf-8")
        geometry = {
            "schema": "pale_mirror.automodel.geometry_input.v1",
            "asset_id": "biomass_collector",
            "scope": "research_only_noncanonical",
            "promotion_prohibited": True,
            "reviewed_view_set": {"file": reviewed_path.relative_to(repository).as_posix(), "sha256": digest(reviewed_path)},
            "frames": frames,
            "contract": {"permitted_adapters": ["da3_base", "vggt_official"]},
        }
        path = directory / "geometry_input.json"
        path.write_text(json.dumps(geometry), encoding="utf-8")
        return path

    def _records(self, repository: Path) -> tuple[Path, Path]:
        directory = repository / "build" / "automodel" / "biomass_collector" / "fixture"
        checkpoint = directory / "checkpoint_receipt.json"
        environment = directory / "environment_lock.json"
        checkpoint.write_text('{"checkpoint":"fixture"}\n', encoding="utf-8")
        environment.write_text('{"environment":"fixture"}\n', encoding="utf-8")
        return checkpoint, environment

    def _qualification(self, repository: Path, adapter_id: str, revision: str) -> Path:
        directory = repository / "build" / "automodel" / "synthetic_qualification" / adapter_id
        build_fixture(directory, repository_root=repository)
        observations = {
            "schema": "pale_mirror.automodel.known_8_view_observations.v1",
            "adapter_id": adapter_id,
            "observations": [
                {
                    "expected_yaw_degrees": yaw,
                    "reported_yaw_degrees": yaw,
                    "mask_iou": 0.97,
                    "relative_depth_error": 0.04,
                    "dorsal_sac_count": 6,
                    "leg_pair_count": 5,
                }
                for yaw in range(0, 360, 45)
            ],
        }
        observations_path = directory / "observations.json"
        observations_path.write_text(json.dumps(observations), encoding="utf-8")
        receipt_path = directory / "qualification.json"
        write_qualification(adapter_id, revision, directory / "fixture.json", observations_path, receipt_path, repository_root=repository)
        return receipt_path

    def test_prepared_run_accepts_only_reviewed_ring_and_diagnostic_outputs(self) -> None:
        with TemporaryDirectory(prefix="pm-pose-depth-ring-") as temporary:
            repository = Path(temporary)
            geometry_input = self._geometry_input(repository)
            self.assertEqual("biomass_collector", load_accepted_geometry_input(geometry_input, repository_root=repository)["asset_id"])
            checkpoint, environment = self._records(repository)
            revision = "a" * 40
            qualification = self._qualification(repository, "vggt_official", revision)
            run_directory = repository / "build" / "automodel" / "biomass_collector" / "vggt_run"
            manifest = prepare_pose_depth_run(
                geometry_input,
                "vggt_official",
                run_directory,
                revision,
                checkpoint,
                environment,
                qualification,
                available_vram_gib=8,
                repository_root=repository,
            )
            self.assertEqual("prepared_not_executed", manifest["execution"]["status"])
            pose = run_directory / "outputs" / "pose.json"
            depth = run_directory / "outputs" / "depth.png"
            confidence = run_directory / "outputs" / "confidence.png"
            cloud = run_directory / "outputs" / "cloud.ply"
            pose.parent.mkdir(parents=True)
            pose.write_text('{"poses": []}\n', encoding="utf-8")
            Image.new("L", (2, 2), 120).save(depth)
            Image.new("L", (2, 2), 255).save(confidence)
            cloud.write_text("ply\n", encoding="ascii")
            evidence = collect_pose_depth_evidence(
                run_directory / "pose_depth_run_manifest.json",
                {"camera_pose": pose, "depth": depth, "confidence": confidence, "point_cloud": cloud},
                run_directory / "pose_depth_evidence.json",
                repository_root=repository,
            )
            self.assertEqual("vggt_official", evidence["adapter_id"])
            with self.assertRaisesRegex(AutomodelContractError, "fresh and immutable"):
                prepare_pose_depth_run(
                    geometry_input,
                    "vggt_official",
                    run_directory,
                    revision,
                    checkpoint,
                    environment,
                    qualification,
                    available_vram_gib=8,
                    repository_root=repository,
                )

    def test_disagreement_is_an_uncertainty_visual_not_a_mesh(self) -> None:
        with TemporaryDirectory(prefix="pm-pose-depth-disagreement-") as temporary:
            repository = Path(temporary)
            inputs = repository / "build" / "automodel" / "fixture"
            inputs.mkdir(parents=True)
            da3, vggt = inputs / "da3.png", inputs / "vggt.png"
            first = Image.new("L", (2, 2))
            first.putdata([0, 80, 170, 255])
            first.save(da3)
            second = Image.new("L", (2, 2))
            second.putdata([255, 170, 80, 0])
            second.save(vggt)
            report = write_disagreement_report(da3, vggt, inputs / "report", repository_root=repository)
            self.assertTrue(report["contract"]["does_not_select_or_fuse_a_mesh"])
            self.assertTrue((inputs / "report" / "depth_disagreement.png").is_file())


if __name__ == "__main__":
    unittest.main()
