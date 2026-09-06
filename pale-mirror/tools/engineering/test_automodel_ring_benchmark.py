#!/usr/bin/env python3
"""Contract tests for the non-artistic synthetic eight-view adapter gate."""

from __future__ import annotations

import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError  # noqa: E402
from ring_benchmark import build_fixture, write_qualification  # noqa: E402


class RingBenchmarkTest(unittest.TestCase):
    def _observations(self, adapter_id: str, *, sac_count: int = 6) -> dict[str, object]:
        return {
            "schema": "pale_mirror.automodel.known_8_view_observations.v1",
            "adapter_id": adapter_id,
            "observations": [
                {
                    "expected_yaw_degrees": yaw,
                    "reported_yaw_degrees": yaw,
                    "mask_iou": 0.97,
                    "relative_depth_error": 0.04,
                    "dorsal_sac_count": sac_count,
                    "leg_pair_count": 5,
                }
                for yaw in range(0, 360, 45)
            ],
        }

    def test_known_fixture_qualifies_only_pose_depth_role(self) -> None:
        with TemporaryDirectory(prefix="pm-ring-benchmark-") as temporary:
            repository = Path(temporary)
            fixture_directory = repository / "build" / "automodel" / "fixture"
            fixture = build_fixture(fixture_directory, repository_root=repository)
            self.assertEqual(8, len(fixture["frames"]))
            observations = fixture_directory / "observations.json"
            observations.write_text(json.dumps(self._observations("da3_base")), encoding="utf-8")
            receipt = write_qualification(
                "da3_base",
                "a" * 40,
                fixture_directory / "fixture.json",
                observations,
                fixture_directory / "qualification.json",
                repository_root=repository,
            )
            self.assertEqual("adapter_role_qualified", receipt["conclusion"])
            self.assertTrue(receipt["contract"]["does_not_promote_views_or_meshes"])

    def test_missing_anatomy_or_reused_output_is_rejected(self) -> None:
        with TemporaryDirectory(prefix="pm-ring-benchmark-negative-") as temporary:
            repository = Path(temporary)
            fixture_directory = repository / "build" / "automodel" / "fixture"
            build_fixture(fixture_directory, repository_root=repository)
            observations = fixture_directory / "bad_observations.json"
            observations.write_text(json.dumps(self._observations("vggt_official", sac_count=5)), encoding="utf-8")
            with self.assertRaisesRegex(AutomodelContractError, "six sacs"):
                write_qualification(
                    "vggt_official",
                    "b" * 40,
                    fixture_directory / "fixture.json",
                    observations,
                    fixture_directory / "qualification.json",
                    repository_root=repository,
                )


if __name__ == "__main__":
    unittest.main()
