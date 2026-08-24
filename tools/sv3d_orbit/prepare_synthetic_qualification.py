#!/usr/bin/env python3
"""Create one immutable synthetic input for an SV3D technical qualification.

The artifact deliberately has no creature likeness and lives only below
``build/automodel``.  It makes a transparent, asymmetric signal-tower prop and
an exact raster trace so the normal ReferenceBundle/RunManifest gates are
exercised without opening a Harvester source image or Blender file.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError, sha256, write_object  # noqa: E402
from stage_reference_bundle import stage_reference_bundle  # noqa: E402


CANVAS = 576
ASSET_ID = "synthetic_sv3d_signal_tower"


def _draw_signal_tower() -> tuple[Image.Image, Image.Image]:
    image = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    mask = Image.new("L", image.size, 0)
    colour = ImageDraw.Draw(image)
    silhouette = ImageDraw.Draw(mask)

    def polygon(points: list[tuple[int, int]], fill: tuple[int, int, int, int]) -> None:
        colour.polygon(points, fill=fill)
        silhouette.polygon(points, fill=255)

    def ellipse(bounds: tuple[int, int, int, int], fill: tuple[int, int, int, int]) -> None:
        colour.ellipse(bounds, fill=fill)
        silhouette.ellipse(bounds, fill=255)

    # A deliberately asymmetric opaque prop: broad base, tilted antenna and a
    # side-mounted wheel give the view generator identity cues at every yaw.
    polygon([(151, 447), (430, 447), (393, 488), (177, 488)], (38, 83, 94, 255))
    polygon([(206, 238), (366, 238), (401, 447), (172, 447)], (58, 132, 145, 255))
    polygon([(242, 132), (339, 132), (366, 248), (206, 248)], (68, 163, 169, 255))
    polygon([(280, 68), (326, 68), (338, 144), (263, 144)], (236, 174, 62, 255))
    polygon([(326, 275), (445, 306), (405, 359), (339, 339)], (202, 95, 57, 255))
    ellipse((111, 329, 240, 458), (126, 193, 101, 255))
    ellipse((144, 362, 206, 424), (31, 55, 64, 255))
    polygon([(369, 160), (415, 193), (390, 298), (348, 273)], (108, 69, 158, 255))
    return image, mask


def _trace(mask: Image.Image) -> dict[str, object]:
    rows: list[dict[str, object]] = []
    pixels = mask.load()
    for y in range(mask.height):
        runs: list[list[int]] = []
        x = 0
        while x < mask.width:
            while x < mask.width and pixels[x, y] == 0:
                x += 1
            left = x
            while x < mask.width and pixels[x, y] != 0:
                x += 1
            if left != x:
                runs.append([left, x])
        if runs:
            rows.append({"y": y, "runs": runs})
    return {
        "schema": "pale_mirror.automodel.synthetic_trace.v1",
        "source": {"size": [mask.width, mask.height]},
        "sampling": {"grid_px": 1},
        "rows": rows,
        "purpose": "technical SV3D qualification only; not creature likeness evidence",
    }


def prepare(output_directory: Path, *, repository_root: Path = ROOT) -> dict[str, object]:
    output_directory = output_directory.resolve()
    try:
        relative = output_directory.relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("synthetic qualification must remain inside the repository") from exc
    if not relative.startswith("build/automodel/"):
        raise AutomodelContractError("synthetic qualification must remain under build/automodel")
    if output_directory.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable synthetic qualification: {output_directory}")
    source = output_directory / "source"
    source.mkdir(parents=True)
    primary, mask = _draw_signal_tower()
    primary_path = source / "signal_tower_primary.png"
    trace_path = source / "signal_tower_trace.json"
    primary.save(primary_path)
    write_object(trace_path, _trace(mask))
    bundle = stage_reference_bundle(
        ASSET_ID,
        primary_path,
        trace_path,
        output_directory,
        trace_masked_conditioning=True,
        repository_root=repository_root,
    )
    receipt = {
        "schema": "pale_mirror.automodel.synthetic_sv3d_qualification.v1",
        "asset_id": ASSET_ID,
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "purpose": "technical FP16/20-step SV3D execution qualification only",
        "primary": {"file": "source/signal_tower_primary.png", "sha256": sha256(primary_path)},
        "trace": {"file": "source/signal_tower_trace.json", "sha256": sha256(trace_path)},
        "reference_bundle": {"file": "reference_bundle.json", "sha256": sha256(output_directory / "reference_bundle.json")},
        "acceptance": {
            "requires_exactly_21_frame_video": True,
            "requires_twenty_extracted_model_derived_frames": True,
            "requires_no_nan_or_nonfinite_frame_pixels": True,
            "does_not_qualify_creature_likeness": True,
            "does_not_promote_generated_views": True,
        },
    }
    write_object(output_directory / "synthetic_qualification_receipt.json", receipt)
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-directory", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(prepare(arguments.output_directory), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
