#!/usr/bin/env python3
"""Prepare and verify trace-authoritative one-image volume-bake-off inputs.

This tool has no model dependency.  It compiles the already reviewed literal
Collector source-pixel trace into a transparent input image and a hash-pinned
manifest.  Remote inference tools consume that immutable input; they do not
receive authority to re-segment, crop or redraw the primary creature.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
from typing import Any

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
BAKEOFF_PATH = ROOT / "pale-mirror-visuals/src/main/blender/harvester/bakeoff/collector_volume_bakeoff_v02.json"
TRACE_PATH = ROOT / "pale-mirror-visuals/src/main/blender/harvester/traces/biomass_collector_primary_v03.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def mask_from_trace(trace: dict[str, Any]) -> Image.Image:
    source = trace.get("source")
    sampling = trace.get("sampling")
    rows = trace.get("rows")
    if not isinstance(source, dict) or not isinstance(sampling, dict) or not isinstance(rows, list):
        raise ValueError("primary trace is malformed")
    dimensions = source.get("size")
    grid = sampling.get("grid_px")
    if not isinstance(dimensions, list) or len(dimensions) != 2 or not all(isinstance(value, int) for value in dimensions):
        raise ValueError("primary trace lacks integer source dimensions")
    if not isinstance(grid, int) or grid <= 0:
        raise ValueError("primary trace lacks a positive sampling grid")
    mask = Image.new("L", tuple(dimensions), 0)
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("y"), int) or not isinstance(row.get("runs"), list):
            raise ValueError("primary trace row is malformed")
        y = row["y"]
        for run in row["runs"]:
            if not isinstance(run, list) or len(run) != 2 or not all(isinstance(value, int) for value in run):
                raise ValueError("primary trace run is malformed")
            for pixel_y in range(y, y + grid):
                for pixel_x in range(run[0], run[1]):
                    if 0 <= pixel_x < mask.width and 0 <= pixel_y < mask.height:
                        mask.putpixel((pixel_x, pixel_y), 255)
    return mask


def prepare(reference: Path, output: Path) -> dict[str, Any]:
    bakeoff = load_json(BAKEOFF_PATH)
    trace = load_json(TRACE_PATH)
    primary = bakeoff.get("primary_input")
    if not isinstance(primary, dict):
        raise ValueError("bake-off config lacks primary_input")
    if sha256(TRACE_PATH) != primary.get("trace_sha256"):
        raise ValueError("checked-in primary trace differs from bake-off pin")
    if reference.name != primary.get("reference_file") or sha256(reference) != primary.get("reference_sha256"):
        raise ValueError("reference differs from the pinned user-supplied Collector image")
    with Image.open(reference) as image:
        rgb = image.convert("RGB")
    size = primary.get("reference_size")
    if not isinstance(size, list) or tuple(size) != rgb.size:
        raise ValueError("reference dimensions differ from the bake-off pin")
    mask = mask_from_trace(trace)
    if mask.size != rgb.size:
        raise ValueError("trace mask dimensions differ from the pinned reference")
    output.mkdir(parents=True, exist_ok=True)
    mask_path = output / "collector_primary_mask.png"
    input_path = output / "collector_primary_input.png"
    manifest_path = output / "manifest.json"
    mask.save(mask_path)
    rgba = rgb.convert("RGBA")
    rgba.putalpha(mask)
    rgba.save(input_path)
    manifest = {
        "schema": "pale_mirror_visuals.harvester_volume_bakeoff_input.v1",
        "bakeoff_id": bakeoff["bakeoff_id"],
        "asset_id": bakeoff["asset_id"],
        "source": {
            "reference": {"file": reference.name, "sha256": sha256(reference), "size": list(rgb.size)},
            "trace": {"file": TRACE_PATH.name, "sha256": sha256(TRACE_PATH), "trace_id": trace["trace_id"]},
        },
        "outputs": {
            "mask": {"file": mask_path.name, "sha256": sha256(mask_path)},
            "rgba_input": {"file": input_path.name, "sha256": sha256(input_path)},
        },
        "contract": "RGBA is the supplied image clipped by the literal source-pixel trace; inference may propose only unseen-side volume.",
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = subcommands.add_parser("prepare")
    prepare_parser.add_argument("--reference", required=True, type=Path)
    prepare_parser.add_argument("--output", required=True, type=Path)
    check_parser = subcommands.add_parser("check")
    check_parser.add_argument("--reference", required=True, type=Path)
    check_parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    if arguments.command == "prepare":
        print(json.dumps(prepare(arguments.reference, arguments.output), indent=2, sort_keys=True))
        return
    with tempfile.TemporaryDirectory(prefix="pm-harvester-bakeoff-check-") as temporary:
        expected = prepare(arguments.reference, Path(temporary))
    actual = load_json(arguments.output / "manifest.json")
    if actual != expected:
        raise SystemExit("bake-off input manifest differs from reproducible output")
    for output in actual["outputs"].values():
        file = arguments.output / output["file"]
        if not file.is_file() or sha256(file) != output["sha256"]:
            raise SystemExit(f"bake-off output is missing or differs: {file}")


if __name__ == "__main__":
    main()
