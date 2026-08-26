#!/usr/bin/env python3
"""Static boundary checks for the fresh reviewed-ring VGGT runner."""

from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "vggt_omega" / "run_reviewed_ring.py"


class VggtReviewedRingContractTest(unittest.TestCase):
    def test_runner_is_pinned_to_reviewed_ring_and_diagnostics(self) -> None:
        source = RUNNER.read_text(encoding="utf-8")
        self.assertIn('manifest["adapter_id"] != "vggt_official"', source)
        self.assertIn("load_accepted_geometry_input", source)
        self.assertIn("collect_pose_depth_evidence", source)
        self.assertIn("load_official_checkpoint_receipt", source)
        self.assertIn("point_cloud_is_not_a_mesh", source)
        self.assertIn("no_glb_or_mesh_export", source)
        self.assertIn("_source_revision(source_root)", source)
        self.assertIn("_white_background_copy", source)
        for forbidden in (".blend", ".pmmesh", "src/main/resources", "mesh export", "trimesh"):
            self.assertNotIn(forbidden, source.lower())


if __name__ == "__main__":
    unittest.main()
