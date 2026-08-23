#!/usr/bin/env python3
"""Prepare the primary-anchored Hunyuan3D-2mv Collector trial.

The supplied image is a real conditioning input in the closest canonical
near-side slot.  Its alpha comes only from the literal source-pixel trace.
The other three slots come from a separately reviewed image-generation
turntable and are explicitly secondary diagnostic evidence.  This can propose
unseen-side volume, never replace the primary likeness contract.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageFilter


ASSET_ID = "biomass_collector"
DEFAULT_TRIAL_ID = "hunyuan2mv_v02_primary_anchored"
PRIMARY_FILE = "01_harvester_biomass_collector.jpg"
PRIMARY_SHA256 = "788cfe68f11e82a4e280de837090d3ebd2209cb598afbb8016946b1ea33a05fa"
OUTPUT_SIZE = 768
ROOT = Path(__file__).resolve().parents[2]
SOURCE_TRACE_PATH = ROOT / "pale-mirror-visuals/src/main/blender/harvester/traces/biomass_collector_primary_v03.json"
TRACE_SHA256 = "e3150d88594a8c712c22dfe60395c43f2da038c6dea7bc53601bff8c69692cf7"

# Hunyuan's MV processor assigns exact view indices: front, then clockwise
# 90 degrees, back, then clockwise 270 degrees. Every trial below is fixed;
# callers choose a known calibration experiment rather than supplying views,
# paths or slot labels themselves. The rejected contact-sheet attempt is not a
# source: each secondary direction is independently generated and reviewed.
TRIALS = {
    "hunyuan2mv_v02_primary_anchored": {
        "bakeoff_id": "collector_multiview_bakeoff_v02_primary_anchored",
        "primary_slot": "left",
        "primary_role": "literal primary low front-left three-quarter, assigned to closest canonical near-side slot",
        "generated_views": {
            "front": {
                "file": "biomass_collector_hunyuan_v02_front_imagegen.png",
                "sha256": "84565b37e476eed984b5975175c461205d92305ac8c239f34e5ab97050208a6e",
                "role": "generated canonical front view",
            },
            "back": {
                "file": "biomass_collector_hunyuan_v02_back_imagegen.png",
                "sha256": "e1b4a53ff0c667a5b2103c7260f892e83898a5bcc6ce63f7961d3b34f9543dce",
                "role": "generated canonical rear view",
            },
            "right": {
                "file": "biomass_collector_hunyuan_v02_right_imagegen.png",
                "sha256": "d156c4e4c91bcc51fb64a3594f5fc610726f2161c13e3d5f1c22b6735619407b",
                "role": "generated canonical right view",
            },
        },
    },
    "hunyuan2mv_v03_primary_front": {
        "bakeoff_id": "collector_multiview_bakeoff_v03_primary_front",
        "primary_slot": "front",
        "primary_role": "literal supplied primary, assigned to Hunyuan's canonical front slot after upstream view-order verification",
        "generated_views": {
            "left": {
                "file": "biomass_collector_hunyuan_v03_left_imagegen.png",
                "sha256": "cf216423c2be3938b77231a2dbed182e431f28324c043fdf55b82496cd4667ea",
                "role": "generated 90-degree clockwise side view relative to the source anchor",
            },
            "back": {
                "file": "biomass_collector_hunyuan_v03_back_imagegen.png",
                "sha256": "a46e2404d5fba96af0387e8fc75850f7b2b50d4abfc425e266d0d83f04610d3a",
                "role": "generated 180-degree rear view relative to the source anchor",
            },
            "right": {
                "file": "biomass_collector_hunyuan_v03_right_imagegen.png",
                "sha256": "73846bf53027a4ce9ea76cdc07d38fa8a9b8e1a9978128740b247334e0f710f0",
                "role": "generated 270-degree clockwise side view relative to the source anchor",
            },
        },
    },
    "hunyuan2mv_v04_calibrated_secondary": {
        "bakeoff_id": "collector_multiview_bakeoff_v04_calibrated_secondary",
        "primary_slot": "front",
        "primary_role": "literal supplied primary, assigned to Hunyuan's canonical front slot after upstream view-order verification",
        "generated_views": {
            "left": {
                "file": "biomass_collector_hunyuan_v04_left_imagegen.png",
                "sha256": "7b8421df380a11679f711c0a5fd92e71dc5186bbb180a0fbd559f554baf571c9",
                "role": "generated 90-degree clockwise side view relative to the source anchor; reviewed as one subject on a neutral turntable background",
            },
            "back": {
                "file": "biomass_collector_hunyuan_v04_back_imagegen.png",
                "sha256": "bbbf0b97c9b4e2e168a80bd9aada29222c3ebe081075b3358c8c5548f6a8c5f1",
                "role": "generated 180-degree rear view relative to the source anchor; reviewed as one subject on a neutral turntable background",
            },
            "right": {
                "file": "biomass_collector_hunyuan_v04_right_imagegen.png",
                "sha256": "30a9338efaf71ce41696bd12faeb44a35674ac6f3e672ad0027850f2a513dd62",
                "role": "generated 270-degree clockwise side view relative to the source anchor; reviewed as one subject on a neutral turntable background",
            },
        },
    },
    # The reviewed v05 source-local cycle avoids the ambiguous decorative
    # `front/back` labels used by earlier ImageGen attempts.  The literal
    # source remains slot zero; generated slots are the quarter turn from its
    # own camera, opposite broadside and remaining end-on view respectively.
    "hunyuan2mv_v05_canonical_turntable": {
        "bakeoff_id": "collector_multiview_bakeoff_v05_canonical_turntable",
        "primary_slot": "front",
        "primary_role": "literal supplied source-local 0-degree broadside, assigned to Hunyuan's first camera slot",
        "generated_views": {
            "left": {
                "file": "biomass_collector_hunyuan_v05_tail_on_imagegen.png",
                "sha256": "15938880dac9b8aaa23a1fd5ec26180c34b8f54633aace74962f685056490691",
                "role": "source-local clockwise 90-degree tail-end-on canonical hidden-side constraint",
            },
            "back": {
                "file": "biomass_collector_hunyuan_v05_opposite_broadside_imagegen.png",
                "sha256": "1ef6a83a420c97ff62be5971637fa9f22335ae730c9b2a0b0ee0ce49e0888a98",
                "role": "source-local 180-degree opposite-broadside canonical hidden-side constraint",
            },
            "right": {
                "file": "biomass_collector_hunyuan_v05_head_on_imagegen.png",
                "sha256": "e3189881f441f0e1f1102f1e08ea2d6b935696b483994691580df76288ed0f56",
                "role": "source-local clockwise 270-degree head-end-on canonical hidden-side constraint",
            },
        },
    },
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _load_trace_mask(trace_path: Path) -> Image.Image:
    if sha256(trace_path) != TRACE_SHA256:
        raise ValueError("The locked Collector primary trace differs from the v02 pin.")
    trace = json.loads(trace_path.read_text(encoding="utf-8"))
    source = trace.get("source")
    sampling = trace.get("sampling")
    rows = trace.get("rows")
    if not isinstance(source, dict) or not isinstance(sampling, dict) or not isinstance(rows, list):
        raise ValueError("The locked Collector primary trace is malformed.")
    size = source.get("size")
    grid = sampling.get("grid_px")
    if not isinstance(size, list) or len(size) != 2 or not all(isinstance(value, int) for value in size):
        raise ValueError("The locked Collector primary trace has no pixel dimensions.")
    if not isinstance(grid, int) or grid < 1:
        raise ValueError("The locked Collector primary trace has no positive grid.")
    mask = Image.new("L", tuple(size), 0)
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("y"), int) or not isinstance(row.get("runs"), list):
            raise ValueError("The locked Collector primary trace has an invalid row.")
        for left, right in row["runs"]:
            if not isinstance(left, int) or not isinstance(right, int):
                raise ValueError("The locked Collector primary trace has an invalid run.")
            for y in range(row["y"], row["y"] + grid):
                for x in range(left, right):
                    if 0 <= x < mask.width and 0 <= y < mask.height:
                        mask.putpixel((x, y), 255)
    return mask


def subject_mask(panel: Image.Image) -> Image.Image:
    """Keep the red organism, excluding the clean white turntable background."""
    rgb = panel.convert("RGB")
    selected = [
        255 if red >= 18 and red >= green * 1.22 and red >= blue * 1.12 and red - green >= 6 else 0
        for red, green, blue in rgb.get_flattened_data()
    ]
    mask = Image.new("L", panel.size)
    mask.putdata(selected)
    return mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))


def centered_rgba(panel: Image.Image, mask: Image.Image) -> Image.Image:
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("A Hunyuan2mv conditioning panel has no retained subject pixels.")
    left, top, right, bottom = bounds
    side = max(right - left, bottom - top)
    margin = max(16, round(side * 0.08))
    left = max(0, left - margin)
    top = max(0, top - margin)
    right = min(panel.width, right + margin)
    bottom = min(panel.height, bottom + margin)
    crop = panel.crop((left, top, right, bottom)).convert("RGBA")
    crop.putalpha(mask.crop((left, top, right, bottom)))
    scale = (OUTPUT_SIZE * 0.86) / max(crop.width, crop.height)
    resized = crop.resize(
        (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.Resampling.LANCZOS
    )
    output = Image.new("RGBA", (OUTPUT_SIZE, OUTPUT_SIZE), (0, 0, 0, 0))
    output.alpha_composite(resized, ((OUTPUT_SIZE - resized.width) // 2, (OUTPUT_SIZE - resized.height) // 2))
    return output


def _entry(destination: Path, *, source: str, role: str) -> dict[str, object]:
    return {
        "file": destination.name,
        "sha256": sha256(destination),
        "source": source,
        "role_hypothesis": role,
        "source_panel": None,
        "size": list(Image.open(destination).size),
        "alpha_pixels": Image.open(destination).getchannel("A").histogram()[255],
    }


def prepare(primary: Path, generated_root: Path, trace_path: Path, output: Path, trial_id: str = DEFAULT_TRIAL_ID) -> dict[str, object]:
    if trial_id not in TRIALS:
        raise ValueError("The Hunyuan2mv preparation trial must use a fixed primary-anchored calibration.")
    trial = TRIALS[trial_id]
    if primary.name != PRIMARY_FILE or sha256(primary) != PRIMARY_SHA256:
        raise ValueError("The supplied primary reference does not match the pinned Collector authority.")
    generated_views = trial["generated_views"]
    generated_paths = {slot: generated_root / str(spec["file"]) for slot, spec in generated_views.items()}
    for slot, path in generated_paths.items():
        if not path.is_file() or sha256(path) != generated_views[slot]["sha256"]:
            raise ValueError(f"The reviewed v02 generated {slot} view does not match its pinned provenance.")
    trace_mask = _load_trace_mask(trace_path)
    with Image.open(primary) as image:
        primary_rgb = image.convert("RGB")
    if primary_rgb.size != trace_mask.size:
        raise ValueError("The primary trace dimensions do not match the pinned Collector reference.")
    output.mkdir(parents=True, exist_ok=True)
    views: dict[str, dict[str, object]] = {}
    for slot, generated_path in generated_paths.items():
        with Image.open(generated_path) as source:
            panel = source.convert("RGB")
        destination = output / f"{slot}.png"
        centered_rgba(panel, subject_mask(panel)).save(destination)
        views[slot] = _entry(
            destination,
            source="generated_single_view",
            role=str(generated_views[slot]["role"]),
        )
    primary_slot = str(trial["primary_slot"])
    primary_destination = output / f"{primary_slot}.png"
    centered_rgba(primary_rgb, trace_mask).save(primary_destination)
    views[primary_slot] = _entry(
        primary_destination,
        source="pinned_primary_reference_clipped_by_literal_trace",
        role=str(trial["primary_role"]),
    )
    ordered_views = {slot: views[slot] for slot in ("front", "left", "back", "right")}
    manifest = {
        "schema": "pale_mirror.hunyuan2mv_input.v1",
        "bakeoff_id": trial["bakeoff_id"],
        "candidate_id": trial_id,
        "asset_id": ASSET_ID,
        "authority": "The original primary remains likeness authority and is itself a conditioning input; generated views remain secondary volume evidence.",
        "inputs_are_calibrated_photos": False,
        "primary_is_model_conditioning": True,
        "primary_slot": primary_slot,
        "source": {
            "primary": {"file": PRIMARY_FILE, "sha256": PRIMARY_SHA256, "trace": {"file": trace_path.name, "sha256": TRACE_SHA256}},
            "diagnostic_views": {
                name: {"file": spec["file"], "sha256": spec["sha256"]} for name, spec in generated_views.items()
            },
            "generated_view_contract": {
                "generator": "OpenAI imagegen",
                "role": "secondary reviewed canonical-direction proxies only; each image contains one subject and no panel crop",
            },
        },
        "views": ordered_views,
        "model_contract": {"model": "tencent/Hunyuan3D-2mv", "subfolder": "hunyuan3d-dit-v2-mv", "shape_only": True},
        "runtime_export_forbidden": True,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--primary", type=Path, required=True)
    parser.add_argument("--generated-root", type=Path, required=True)
    parser.add_argument("--trial", choices=tuple(TRIALS), default=DEFAULT_TRIAL_ID)
    parser.add_argument(
        "--trace",
        type=Path,
        default=SOURCE_TRACE_PATH,
        help="Pinned literal primary trace; controlled Windows workspaces supply their synchronized copy.",
    )
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    print(
        json.dumps(
            prepare(arguments.primary, arguments.generated_root, arguments.trace, arguments.output, arguments.trial),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
