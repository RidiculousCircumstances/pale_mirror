#!/usr/bin/env python3
"""Focused contracts for anonymized Harvester pairwise visual review packages."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/harvester_blind_pairwise.py"
PROTOCOL = ROOT / "pale-mirror-visuals/src/main/blender/harvester/blind_review_protocol_v01.json"
spec = importlib.util.spec_from_file_location("harvester_blind_pairwise", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load harvester_blind_pairwise")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


def image(path: Path, colour: tuple[int, int, int, int]) -> None:
    Image.new("RGBA", (24, 16), colour).save(path)


def candidate(root: Path, name: str, colour: tuple[int, int, int, int]) -> module.Candidate:
    directory = root / name
    directory.mkdir()
    for view in module.VIEWS:
        image(directory / f"{view}.png", colour)
    return module.Candidate(name, directory)


with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    reference = root / "highly_identifying_reference_name.png"
    image(reference, (10, 20, 30, 255))
    alpha = candidate(root, "generated_history_must_not_leak", (255, 0, 0, 255))
    beta = candidate(root, "primitive_history_must_not_leak", (0, 0, 255, 255))
    result = module.build_package(
        protocol=PROTOCOL,
        review_id="trial_v01",
        reference=reference,
        candidates=(alpha, beta),
        output_root=root / "output",
    )
    public = result["public"]
    manifest = json.loads((public / "package_manifest.json").read_text(encoding="utf-8"))
    assert manifest["schema"] == module.SCHEMA
    assert set(manifest["candidates"]) == set(module.LABELS)
    assert (public / "reference.png").is_file()
    assert (public / "review_prompt.md").is_file()
    assert (public / "response_template.json").is_file()
    for label in module.LABELS:
        for view in module.VIEWS:
            assert (public / label / f"{view}.png").is_file()
    public_text = "\n".join(path.read_text(encoding="utf-8", errors="ignore") for path in public.rglob("*.json"))
    public_text += (public / "review_prompt.md").read_text(encoding="utf-8")
    for forbidden in ("generated_history_must_not_leak", "primitive_history_must_not_leak", "highly_identifying_reference_name", str(root)):
        assert forbidden not in public_text
    mapping = json.loads(result["mapping"].read_text(encoding="utf-8"))
    assert set(mapping["labels"]) == set(module.LABELS)
    assert {value["candidate_id"] for value in mapping["labels"].values()} == {alpha.identifier, beta.identifier}
    try:
        module.build_package(
            protocol=PROTOCOL,
            review_id="trial_v01",
            reference=reference,
            candidates=(alpha, beta),
            output_root=root / "output",
        )
    except ValueError as error:
        assert "refusing to overwrite" in str(error)
    else:
        raise AssertionError("existing review package was overwritten")

with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    first = candidate(root, "first", (1, 2, 3, 255))
    (first.directory / "side.png").unlink()
    try:
        module.required_views(first)
    except ValueError as error:
        assert "side.png" in str(error)
    else:
        raise AssertionError("missing required render view was accepted")

print("Harvester blind pairwise review contracts passed.")
