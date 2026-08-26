#!/usr/bin/env python3
"""Focused contracts for the anatomy-aware Automodel still-ring laboratory."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import sys
from tempfile import TemporaryDirectory
import unittest

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError, validate_reference_bundle  # noqa: E402
from multiview_ring import (  # noqa: E402
    REQUIRED_AFFIRMATIONS,
    RING_YAWS,
    assemble_provisional_ring,
    build_ring_review_package,
    lint_rgba_still,
    prepare_external_still,
    prepare_geometry_input,
    record_ring_review,
    stage_candidate,
    validate_anatomy_sheet,
    validate_provisional_ring,
    validate_reviewed_view_set,
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class AutomodelMultiviewRingTest(unittest.TestCase):
    def _sheet(self, repository: Path) -> Path:
        path = repository / "build" / "automodel" / "biomass_collector" / "fixture" / "anatomy.json"
        path.parent.mkdir(parents=True)
        shutil.copyfile(ROOT / "tools" / "automodel" / "anatomy" / "biomass_collector.v1.json", path)
        return path

    def _subject(self, path: Path, *, debris: bool = False) -> None:
        image = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.ellipse((140, 270, 870, 760), fill=(80, 140, 75, 255))
        if debris:
            draw.rectangle((940, 940, 970, 970), fill=(255, 0, 0, 255))
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)

    def _reference_bundle(self, repository: Path, primary: Path) -> Path:
        trace = repository / "build" / "automodel" / "biomass_collector" / "fixture" / "trace.json"
        trace.write_text('{"trace": "fixture"}\n', encoding="utf-8")
        bundle = {
            "schema": "pale_mirror.automodel.reference_bundle.v1",
            "asset_id": "biomass_collector",
            "scope": "research_only_noncanonical",
            "primary": {
                "role": "sole_likeness_anchor",
                "tier": "PRIMARY_TRACE",
                "file": primary.relative_to(repository).as_posix(),
                "sha256": digest(primary),
            },
            "primary_trace": {"file": trace.relative_to(repository).as_posix(), "sha256": digest(trace)},
            "anchors": [],
            "model_inputs": [],
        }
        validate_reference_bundle(bundle)
        path = trace.with_name("reference_bundle.json")
        path.write_text(json.dumps(bundle), encoding="utf-8")
        return path

    def _prompt(self, repository: Path, yaw: int) -> Path:
        path = repository / "prompts" / f"{yaw:03d}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "schema": "pale_mirror.automodel.imagegen_prompt_record.v1",
                    "asset_id": "biomass_collector",
                    "yaw_degrees": yaw,
                    "text": f"canonical transparent turntable yaw {yaw}",
                }
            ),
            encoding="utf-8",
        )
        return path

    def _import(self, repository: Path, external: Path, yaw: int) -> tuple[Path, Path]:
        directory = repository / "build" / "automodel" / "biomass_collector" / "imports" / f"yaw_{yaw:03d}"
        prepare_external_still(external, directory, repository_root=repository)
        return directory / "still.png", directory / "external_still_import.json"

    def _complete_ring(self, repository: Path) -> tuple[Path, Path]:
        sheet = self._sheet(repository)
        validate_anatomy_sheet(json.loads(sheet.read_text(encoding="utf-8")))
        primary = repository / "build" / "automodel" / "biomass_collector" / "fixture" / "primary.png"
        self._subject(primary)
        reference = self._reference_bundle(repository, primary)
        run = repository / "build" / "automodel" / "biomass_collector" / "ring_run"
        selections: dict[int, Path] = {}
        for yaw in RING_YAWS[1:]:
            external = repository / "external" / f"{yaw:03d}.png"
            self._subject(external)
            prepared, imported = self._import(repository, external, yaw)
            stage_candidate(sheet, run, yaw, f"imagegen_{yaw:03d}", prepared, self._prompt(repository, yaw), imported, repository_root=repository)
            selections[yaw] = run / "candidates" / f"yaw_{yaw:03d}" / f"imagegen_{yaw:03d}" / "candidate_still.json"
        ring = run / "provisional_ring.json"
        assemble_provisional_ring(sheet, reference, selections, ring, repository_root=repository)
        return sheet, ring

    def test_complete_user_review_is_the_only_geometry_handoff(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-ring-") as temporary:
            repository = Path(temporary)
            _, ring_path = self._complete_ring(repository)
            ring = json.loads(ring_path.read_text(encoding="utf-8"))
            validate_provisional_ring(ring)
            package_dir = ring_path.with_name("review_package")
            build_ring_review_package(ring_path, package_dir, repository_root=repository)
            receipt = ring_path.with_name("ring_review_receipt.json")
            with self.assertRaisesRegex(AutomodelContractError, "every explicit anatomy"):
                record_ring_review(
                    package_dir / "ring_review_package.json",
                    receipt,
                    "user",
                    "accepted_complete_ring",
                    ["anatomy_cardinality"],
                    "looks coherent",
                    repository_root=repository,
                )
            accepted = record_ring_review(
                package_dir / "ring_review_package.json",
                receipt,
                "user",
                "accepted_complete_ring",
                REQUIRED_AFFIRMATIONS,
                "all eight views retain the reviewed anatomy and a continuous closed turntable",
                repository_root=repository,
            )
            reviewed = json.loads((repository / accepted["reviewed_view_set"]["file"]).read_text(encoding="utf-8"))
            validate_reviewed_view_set(reviewed)
            self.assertEqual("PRIMARY_TRACE", reviewed["frames"][0]["tier"])
            self.assertTrue(all(frame["tier"] == "REVIEWED_SECONDARY" for frame in reviewed["frames"][1:]))
            geometry = prepare_geometry_input(receipt, ring_path.with_name("geometry_input.json"), repository_root=repository)
            self.assertEqual(["da3_base", "vggt_official"], geometry["contract"]["permitted_adapters"])
            self.assertEqual(list(RING_YAWS), [frame["yaw_degrees"] for frame in geometry["frames"]])

    def test_detached_imagegen_debris_fails_before_candidate_is_written(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-ring-debris-") as temporary:
            repository = Path(temporary)
            sheet = self._sheet(repository)
            external = repository / "external" / "debris.png"
            self._subject(external, debris=True)
            with self.assertRaisesRegex(AutomodelContractError, "detached alpha debris"):
                prepare_external_still(external, repository / "build" / "automodel" / "biomass_collector" / "imports" / "debris", repository_root=repository)
            self.assertFalse((repository / "build" / "automodel" / "biomass_collector" / "run").exists())

    def test_lint_rejects_nontransparent_or_clipped_inputs(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-ring-lint-") as temporary:
            root = Path(temporary)
            opaque = root / "opaque.png"
            Image.new("RGB", (1024, 1024), (10, 10, 10)).save(opaque)
            with self.assertRaisesRegex(AutomodelContractError, "RGBA"):
                lint_rgba_still(opaque)
            clipped = root / "clipped.png"
            image = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
            ImageDraw.Draw(image).rectangle((0, 100, 900, 900), fill=(1, 2, 3, 255))
            image.save(clipped)
            with self.assertRaisesRegex(AutomodelContractError, "clips"):
                lint_rgba_still(clipped)

    def test_import_normalizes_only_a_safe_external_canvas(self) -> None:
        with TemporaryDirectory(prefix="pm-automodel-ring-import-") as temporary:
            repository = Path(temporary)
            raw = repository / "external" / "safe_1254.png"
            raw.parent.mkdir(parents=True)
            image = Image.new("RGBA", (1254, 1254), (0, 0, 0, 0))
            ImageDraw.Draw(image).ellipse((180, 250, 1020, 900), fill=(80, 140, 75, 255))
            image.save(raw)
            imported = prepare_external_still(raw, repository / "build" / "automodel" / "import", repository_root=repository)
            self.assertEqual([1024, 1024], imported["prepared"]["transform"]["output_size_px"])
            self.assertEqual("passed", imported["prepared"]["lint"]["status"])


if __name__ == "__main__":
    unittest.main()
