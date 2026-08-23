"""Whitelisted MCP facade over the official Blender Lab local TCP server.

The official add-on deliberately executes Python.  This process never exposes
that primitive: all requests are reduced to a versioned operation identifier
whose source has already been synced into the private Windows workspace.
"""

from __future__ import annotations

from typing import Any

from mcp.server.fastmcp import FastMCP

from blender_operation_client import OPERATIONS, canonical_asset, operation, status


SERVER = FastMCP(
    "Pale Mirror Blender",
    instructions=(
        "Controlled Pale Mirror Blender authoring only. Use named assets and named project operations; "
        "arbitrary Blender Python is intentionally unavailable."
    ),
)
@SERVER.tool()
def blender_status() -> dict[str, Any]:
    """Return the current private Blender scene and MCP connection state."""
    return status()


@SERVER.tool()
def blender_open(asset_id: str) -> dict[str, Any]:
    """Open a canonical PM Blender asset in the active Blender session."""
    return operation("open_asset", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_create_base(asset_id: str) -> dict[str, Any]:
    """Create the approved baseline source scene for a canonical PM asset."""
    return operation("create_collector_base", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_build_trace_cage(asset_id: str) -> dict[str, Any]:
    """Build the named trace-authoritative three-dimensional Collector cage."""
    return operation("build_collector_trace_cage", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_build_semantic_cage(asset_id: str) -> dict[str, Any]:
    """Build the primary-rail, primitive-free Collector semantic cage."""
    return operation("build_collector_semantic_cage", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_create_collector_sculpt_branch(asset_id: str) -> dict[str, Any]:
    """Create a separate, non-exportable editable branch from locked Hunyuan v05."""
    return operation("create_collector_sculpt_branch", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_capture_collector_sculpt_baseline(asset_id: str) -> dict[str, Any]:
    """Capture the unchanged raw-v05 direct-sculpt baseline evidence."""
    return operation("capture_collector_sculpt_baseline", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_apply_collector_front_mantle_v03(asset_id: str) -> dict[str, Any]:
    """Apply the v03 literal-stroke front-drape pass after baseline evidence."""
    return operation("apply_collector_sculpt_pass", {"asset_id": _asset(asset_id), "pass_id": "front_mantle_v03"})


@SERVER.tool()
def blender_apply_collector_dorsal_rhythm_v03(asset_id: str) -> dict[str, Any]:
    """Apply the independent v03 six-crest dorsal rhythm pass."""
    return operation("apply_collector_sculpt_pass", {"asset_id": _asset(asset_id), "pass_id": "dorsal_rhythm_v03"})


@SERVER.tool()
def blender_apply_collector_supports_v03(asset_id: str) -> dict[str, Any]:
    """Apply the independent v03 six-support grounding pass."""
    return operation("apply_collector_sculpt_pass", {"asset_id": _asset(asset_id), "pass_id": "supports_v03"})


@SERVER.tool()
def blender_repair_collector_sculpt_branch_metadata(asset_id: str) -> dict[str, Any]:
    """Repair only the known inherited working-tag defect in the v05 backup."""
    return operation("repair_collector_sculpt_branch_metadata", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_render_collector_sculpt_audit(asset_id: str, label: str) -> dict[str, Any]:
    """Render the seven-view neutral-clay audit set for the active sculpt branch."""
    if not label or len(label) > 80 or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in label):
        raise ValueError("label must use 1-80 lowercase letters, digits, underscores or dashes")
    return operation("render_collector_sculpt_audit", {"asset_id": _asset(asset_id), "label": label})


@SERVER.tool()
def blender_validate_collector_sculpt_branch(asset_id: str) -> dict[str, Any]:
    """Validate raw-v05 protection and non-exportable v03 sculpt-branch structure."""
    return operation("validate_collector_sculpt_branch", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_save(asset_id: str) -> dict[str, Any]:
    """Save a canonical PM Blender asset to its fixed workspace path."""
    return operation("save_asset", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_render_audit(asset_id: str, label: str = "manual") -> dict[str, Any]:
    """Render the deterministic reference/audit camera set for a canonical asset."""
    if not label or len(label) > 80 or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in label):
        raise ValueError("label must use 1-80 lowercase letters, digits, underscores or dashes")
    return operation("render_audit", {"asset_id": _asset(asset_id), "label": label})


@SERVER.tool()
def blender_import_sf3d_candidate(asset_id: str) -> dict[str, Any]:
    """Import only the pinned, non-exportable Collector SF3D volume proposal."""
    return operation("import_sf3d_candidate", {"asset_id": _asset(asset_id), "candidate_id": "sf3d_v01"})


@SERVER.tool()
def blender_render_sf3d_candidate_audit(asset_id: str, label: str = "sf3d_v01") -> dict[str, Any]:
    """Render the fixed SF3D proposal's review-only camera set."""
    if not label or len(label) > 80 or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in label):
        raise ValueError("label must use 1-80 lowercase letters, digits, underscores or dashes")
    return operation(
        "render_sf3d_candidate_audit",
        {"asset_id": _asset(asset_id), "candidate_id": "sf3d_v01", "label": label},
    )


@SERVER.tool()
def blender_import_hunyuan2mv_candidate(
    asset_id: str, candidate_id: str = "hunyuan2mv_v03_primary_front"
) -> dict[str, Any]:
    """Import one fixed, non-exportable Collector Hunyuan2mv proposal."""
    return operation("import_hunyuan2mv_candidate", {"asset_id": _asset(asset_id), "candidate_id": _hunyuan_candidate(candidate_id)})


@SERVER.tool()
def blender_render_hunyuan2mv_candidate_audit(
    asset_id: str, candidate_id: str = "hunyuan2mv_v03_primary_front", label: str = "hunyuan2mv_v03_primary_front"
) -> dict[str, Any]:
    """Render one fixed Hunyuan2mv proposal's review-only camera set."""
    if not label or len(label) > 80 or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in label):
        raise ValueError("label must use 1-80 lowercase letters, digits, underscores or dashes")
    return operation(
        "render_hunyuan2mv_candidate_audit",
        {"asset_id": _asset(asset_id), "candidate_id": _hunyuan_candidate(candidate_id), "label": label},
    )


@SERVER.tool()
def blender_validate(asset_id: str) -> dict[str, Any]:
    """Validate canonical naming, geometry budget, rig and material contracts."""
    return operation("validate_asset", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_export(asset_id: str) -> dict[str, Any]:
    """Export a canonical asset into the PMMesh v1 staging directory."""
    return operation("export_pmmesh", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_run_project_script(script_id: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
    """Run one explicitly versioned PM operation with JSON-only arguments."""
    if script_id not in OPERATIONS:
        raise ValueError(f"Unknown project script: {script_id}")
    if arguments is None:
        arguments = {}
    if not isinstance(arguments, dict):
        raise ValueError("arguments must be an object")
    return operation(script_id, arguments)


def _asset(value: str) -> str:
    return canonical_asset(value)


def _hunyuan_candidate(value: str) -> str:
    if value not in {
        "hunyuan2mv_v01",
        "hunyuan2mv_v02_primary_anchored",
        "hunyuan2mv_v03_primary_front",
        "hunyuan2mv_v04_calibrated_secondary",
        "hunyuan2mv_v05_canonical_turntable",
    }:
        raise ValueError("candidate_id must name a fixed Hunyuan2mv proposal")
    return value


def main() -> None:
    SERVER.run()


if __name__ == "__main__":
    main()
