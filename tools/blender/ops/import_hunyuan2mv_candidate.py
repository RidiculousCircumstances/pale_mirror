"""Import one fixed Hunyuan3D-2mv Collector proposal into a review scene.

The allowlist deliberately names every candidate. Callers choose an ID, never
a path, and no candidate can open the canonical Collector source or export PMMesh.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import hashlib
import json
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    ASSET_ID,
    TRACE_GUIDE_COLLECTION,
    TRACE_GUIDE_NAME,
    asset_id,
    collection,
    ensure_audit_stage,
    ensure_primary_reference_plane,
    fresh_scene,
    load_primary_trace,
    trace_profile_mesh,
)

import bpy


@dataclass(frozen=True)
class CandidateSpec:
    candidate_id: str
    stage: str

    @property
    def root(self) -> Path:
        return WORKSPACE / "candidates" / ASSET_ID / self.candidate_id

    @property
    def review_source(self) -> Path:
        return self.root / f"collector_{self.candidate_id}_review.blend"

    @property
    def proposal_collection(self) -> str:
        return f"PM_COLLECTOR_{self.candidate_id.upper()}_PROPOSAL"

    @property
    def proposal_root(self) -> str:
        return f"PM_COLLECTOR_{self.candidate_id.upper()}_ROOT"


WORKSPACE = Path(__file__).resolve().parents[3]
DEFAULT_CANDIDATE_ID = "hunyuan2mv_v03_primary_front"
SPECS = {
    "hunyuan2mv_v01": CandidateSpec("hunyuan2mv_v01", "volume-proposal-unaccepted"),
    "hunyuan2mv_v02_primary_anchored": CandidateSpec(
        "hunyuan2mv_v02_primary_anchored", "volume-proposal-unaccepted"
    ),
    "hunyuan2mv_v03_primary_front": CandidateSpec("hunyuan2mv_v03_primary_front", "volume-proposal-unaccepted"),
    "hunyuan2mv_v04_calibrated_secondary": CandidateSpec(
        "hunyuan2mv_v04_calibrated_secondary", "volume-proposal-unaccepted"
    ),
    "hunyuan2mv_v05_canonical_turntable": CandidateSpec(
        "hunyuan2mv_v05_canonical_turntable", "volume-proposal-unaccepted"
    ),
}


def candidate_spec(payload: dict[str, object]) -> CandidateSpec:
    value = payload.get("candidate_id", DEFAULT_CANDIDATE_ID)
    if not isinstance(value, str) or value not in SPECS:
        raise ValueError("Only a fixed Hunyuan2mv proposal may enter this review scene.")
    return SPECS[value]


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    spec = candidate_spec(payload)
    manifest = _candidate_manifest(spec)
    trace, trace_hash = load_primary_trace()
    fresh_scene()
    _reset_review_only_scene_state(bpy.context.scene)
    guide = collection(TRACE_GUIDE_COLLECTION)
    guide_profile = trace_profile_mesh(guide, TRACE_GUIDE_NAME, trace, _guide_material(), depth=-0.08)
    guide_profile.hide_render = True
    guide_profile.hide_set(True)
    guide_profile["pm_trace_role"] = "locked_primary_overlay_guide"
    ensure_primary_reference_plane(trace)

    proposal = collection(spec.proposal_collection)
    before = set(bpy.data.objects)
    candidate_path = spec.root / "candidate.glb"
    bpy.ops.import_scene.gltf(filepath=str(candidate_path))
    imported = [object_ for object_ in bpy.data.objects if object_ not in before and object_.type == "MESH"]
    if not imported:
        raise ValueError("Pinned Hunyuan2mv candidate imported no mesh objects.")
    for object_ in imported:
        for current in list(object_.users_collection):
            current.objects.unlink(object_)
        proposal.objects.link(object_)
        object_["pm_export_exclude"] = True
        object_["pm_candidate_id"] = spec.candidate_id
        object_["pm_candidate_stage"] = spec.stage

    raw_bounds = _bounds(imported)
    trace_bounds = _trace_bounds(trace)
    root = bpy.data.objects.new(spec.proposal_root, None)
    proposal.objects.link(root)
    for object_ in imported:
        object_.parent = root
    # One uniform width scale and ground-datum normalization only. This is not
    # silhouette fitting: literal primary overlay remains the acceptance gate.
    _normalise_primary_extent(root, raw_bounds, trace_bounds)
    bpy.context.view_layer.update()
    normalized_bounds = _bounds(imported)
    ensure_audit_stage()

    scene = bpy.context.scene
    scene["pm_asset_id"] = ASSET_ID
    scene["pm_candidate_id"] = spec.candidate_id
    scene["pm_candidate_stage"] = spec.stage
    scene["pm_candidate_sha256"] = manifest["output"]["sha256"]
    scene["pm_candidate_input_manifest_sha256"] = manifest["input_manifest_sha256"]
    scene["pm_primary_trace_id"] = trace["trace_id"]
    scene["pm_primary_trace_sha256"] = trace_hash
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_runtime_export_forbidden"] = True
    scene["pm_review_scope"] = "Hunyuan2mv diagnostic volume proposal only; primary trace remains sole likeness authority"
    spec.review_source.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(spec.review_source))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": spec.candidate_id,
        "stage": spec.stage,
        "review_source": spec.review_source.as_posix(),
        "candidate_sha256": manifest["output"]["sha256"],
        "raw_bounds": raw_bounds,
        "normalized_bounds": normalized_bounds,
        "trace_bounds": trace_bounds,
        "runtime_export_forbidden": True,
    }


def _candidate_manifest(spec: CandidateSpec) -> dict[str, object]:
    candidate_path = spec.root / "candidate.glb"
    candidate_manifest = spec.root / "manifest.json"
    if not candidate_path.is_file() or not candidate_manifest.is_file():
        raise FileNotFoundError("Pinned Hunyuan2mv candidate/provenance is unavailable in the controlled workspace.")
    manifest = json.loads(candidate_manifest.read_text(encoding="utf-8-sig"))
    if (
        manifest.get("schema") != "pale_mirror.hunyuan2mv_volume_candidate.v1"
        or manifest.get("asset_id") != ASSET_ID
        or manifest.get("candidate_id") != spec.candidate_id
        or manifest.get("stage") != spec.stage
        or manifest.get("runtime_export_forbidden") is not True
    ):
        raise ValueError("Hunyuan2mv candidate provenance does not match the fixed proposal contract.")
    output = manifest.get("output")
    if not isinstance(output, dict) or output.get("file") != "candidate.glb" or not isinstance(output.get("sha256"), str):
        raise ValueError("Hunyuan2mv provenance does not pin candidate.glb.")
    if _sha256(candidate_path) != output["sha256"]:
        raise ValueError("Hunyuan2mv candidate differs from its immutable provenance hash.")
    return manifest


def _reset_review_only_scene_state(scene: bpy.types.Scene) -> None:
    """Forget derived-proxy markers when restoring the immutable GLB review.

    ``fresh_scene`` intentionally preserves Blender scene custom properties:
    canonical authoring operations replace them deliberately.  A Hunyuan
    review import, by contrast, is a reset to its immutable candidate.  Its
    prior experimental proxy markers must therefore not survive and block or
    mislabel a fresh fixed operation.
    """
    for key in ("pm_trace_conformal_proxy", "pm_trace_depth_hull_proxy"):
        if key in scene:
            del scene[key]


def _normalise_primary_extent(root, raw: dict[str, list[float]], target: dict[str, list[float]]) -> None:
    source_width = raw["max"][0] - raw["min"][0]
    target_width = target["max"][0] - target["min"][0]
    if source_width <= 0.0 or target_width <= 0.0:
        raise ValueError("Hunyuan2mv candidate or primary trace has a degenerate width.")
    scale = target_width / source_width
    root.scale = (scale, scale, scale)
    root.location.x = (target["min"][0] + target["max"][0]) * 0.5 - (raw["min"][0] + raw["max"][0]) * 0.5 * scale
    root.location.y = -0.12 - raw["max"][1] * scale
    root.location.z = target["min"][2] - raw["min"][2] * scale


def _trace_bounds(trace: dict[str, object]) -> dict[str, list[float]]:
    mapping = trace["primary_camera_mapping"]
    units = float(mapping["world_units_per_pixel"])
    origin_x, origin_y = (int(value) for value in mapping["origin_pixel"])
    grid = int(trace["sampling"]["grid_px"])
    pixels = [(left, int(row["y"])) for row in trace["rows"] for left, _right in row["runs"]]
    pixels.extend((right, int(row["y"]) + grid) for row in trace["rows"] for _left, right in row["runs"])
    xs = [(x - origin_x) * units for x, _y in pixels]
    zs = [(origin_y - y) * units for _x, y in pixels]
    return {"min": [min(xs), 0.0, min(zs)], "max": [max(xs), 0.0, max(zs)]}


def _bounds(objects) -> dict[str, list[float]]:
    vertices = [object_.matrix_world @ vertex.co for object_ in objects for vertex in object_.data.vertices]
    if not vertices:
        raise ValueError("Hunyuan2mv candidate has no vertices.")
    return {
        "min": [min(vertex[index] for vertex in vertices) for index in range(3)],
        "max": [max(vertex[index] for vertex in vertices) for index in range(3)],
    }


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _guide_material():
    material = bpy.data.materials.get("PM_HUNYUAN2MV_TRACE_GUIDE")
    if material is not None:
        return material
    material = bpy.data.materials.new("PM_HUNYUAN2MV_TRACE_GUIDE")
    material.diffuse_color = (1.0, 1.0, 1.0, 1.0)
    return material
