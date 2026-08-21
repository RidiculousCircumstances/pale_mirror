import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw


SCRIPT = Path(__file__).parents[1] / "harvester_contour_overlay.py"
SPEC = importlib.util.spec_from_file_location("harvester_contour_overlay", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class HarvesterContourOverlayTest(unittest.TestCase):
    def test_builds_three_panel_comparison_and_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = root / "reference.png"
            silhouette = root / "silhouette.png"
            output = root / "comparison.png"
            metadata = root / "metadata.json"

            Image.new("RGB", (100, 60), (28, 12, 16)).save(reference)
            viewport = Image.new("RGB", (80, 60), (18, 20, 22))
            draw = ImageDraw.Draw(viewport)
            draw.rectangle((20, 12, 58, 50), fill=(190, 190, 190))
            viewport.save(silhouette)

            result = MODULE.build_comparison(
                reference,
                silhouette,
                (0.1, 0.1, 0.9, 0.9),
                "bottom_center",
                output,
                metadata,
            )

            self.assertEqual([100, 60], result["reference_size"])
            self.assertIsNone(result["automated_score"])
            with Image.open(output) as comparison:
                self.assertEqual((300, 94), comparison.size)
            stored = json.loads(metadata.read_text(encoding="utf-8"))
            self.assertEqual("bottom_center", stored["alignment"])
            self.assertEqual(3, len(stored["comparison_panels"]))

    def test_rejects_empty_projection(self):
        mask = Image.new("L", (20, 20), 0)
        with self.assertRaisesRegex(ValueError, "contains no light model pixels"):
            MODULE.align_mask(mask, (100, 100), (10, 10, 90, 90), "center")

    def test_rejects_unknown_alignment(self):
        mask = Image.new("L", (20, 20), 0)
        ImageDraw.Draw(mask).rectangle((5, 5, 15, 15), fill=255)
        with self.assertRaisesRegex(ValueError, "unsupported contour alignment"):
            MODULE.align_mask(mask, (100, 100), (10, 10, 90, 90), "top_left")


if __name__ == "__main__":
    unittest.main()
