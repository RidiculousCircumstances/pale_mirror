#!/usr/bin/env python3
"""Focused contracts for the known-geometry SV3D multiview benchmark."""

from __future__ import annotations

import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "sv3d_orbit"))

from evaluate_synthetic_multiview_benchmark import _expected_synthetic_yaw, _foreground, _read_ground_truth, score_generated_frame  # noqa: E402
from prepare_synthetic_multiview_benchmark import CANVAS, SCENES, YAWS, prepare  # noqa: E402


class SyntheticMultiviewBenchmarkTests(unittest.TestCase):
    def test_prepare_writes_three_immutable_known_geometry_scenes(self) -> None:
        with TemporaryDirectory(prefix="pm-sv3d-multiview-") as temporary:
            repository = Path(temporary)
            suite_root = repository / "build" / "automodel" / "suite"
            suite = prepare(suite_root, repository_root=repository)
            self.assertEqual("sv3d_p_orbit_fast_fp16_20", suite["adapter_id"])
            self.assertEqual(3, len(suite["scenes"]))
            self.assertEqual({"signal_tower", "needle_fork", "gantry_hook"}, {item["scene_id"] for item in suite["scenes"]})
            for record in suite["scenes"]:
                scene_path = suite_root / record["synthetic_scene"]["file"]
                scene = json.loads(scene_path.read_text(encoding="utf-8"))
                self.assertEqual(list(YAWS), scene["camera"]["yaw_degrees"])
                self.assertEqual(len(YAWS), len(scene["ground_truth_frames"]))
                self.assertTrue((scene_path.parent / "input" / "trace_masked_conditioning.png").is_file())

    def test_perfect_known_render_scores_as_its_declared_pose(self) -> None:
        with TemporaryDirectory(prefix="pm-sv3d-multiview-") as temporary:
            repository = Path(temporary)
            suite_root = repository / "build" / "automodel" / "suite"
            suite = prepare(suite_root, repository_root=repository)
            record = suite["scenes"][1]
            run = repository / record["run_directory"]
            scene = json.loads((suite_root / record["synthetic_scene"]["file"]).read_text(encoding="utf-8"))
            ground_truth = _read_ground_truth(run, scene)
            frame = run / "ground_truth" / "colour" / "frame_270.png"
            score = score_generated_frame(_foreground(frame), 90, ground_truth)
            self.assertEqual(270, score["best_matching_ground_truth_yaw_degrees"])
            self.assertEqual(270, score["expected_synthetic_yaw_degrees"])
            self.assertGreater(float(score["silhouette_iou_at_declared_yaw"]), 0.98)
            self.assertGreater(float(score["exposed_critical_recall"]), 0.98)
            self.assertLess(float(score["critical_local_excess"]), 0.05)

    def test_fully_occluded_critical_detail_is_excluded_not_scored_as_missing(self) -> None:
        with TemporaryDirectory(prefix="pm-sv3d-multiview-") as temporary:
            repository = Path(temporary)
            suite_root = repository / "build" / "automodel" / "suite"
            suite = prepare(suite_root, repository_root=repository)
            record = suite["scenes"][0]
            run = repository / record["run_directory"]
            scene = json.loads((suite_root / record["synthetic_scene"]["file"]).read_text(encoding="utf-8"))
            ground_truth = _read_ground_truth(run, scene)
            hidden_declared_yaw = next(
                declared_yaw
                for declared_yaw in range(18, 361, 18)
                if score_generated_frame(
                    _foreground(run / "ground_truth" / "colour" / f"frame_{_expected_synthetic_yaw(declared_yaw):03d}.png"),
                    declared_yaw,
                    ground_truth,
                )["critical_detail_exposed"] is False
            )
            score = score_generated_frame(
                _foreground(run / "ground_truth" / "colour" / f"frame_{_expected_synthetic_yaw(hidden_declared_yaw):03d}.png"),
                hidden_declared_yaw,
                ground_truth,
            )
            self.assertFalse(score["critical_detail_exposed"])
            self.assertIsNone(score["exposed_critical_recall"])
            self.assertIsNone(score["critical_local_excess"])

    def test_fixture_dimensions_are_the_runner_contract_dimensions(self) -> None:
        self.assertEqual(576, CANVAS)
        self.assertEqual(21, len(YAWS))
        self.assertEqual(3, len(SCENES))

    def test_sv3d_camera_sign_is_one_global_calibration(self) -> None:
        self.assertEqual(342, _expected_synthetic_yaw(18))
        self.assertEqual(270, _expected_synthetic_yaw(90))
        self.assertEqual(180, _expected_synthetic_yaw(180))
        self.assertEqual(0, _expected_synthetic_yaw(360))


if __name__ == "__main__":
    unittest.main()
