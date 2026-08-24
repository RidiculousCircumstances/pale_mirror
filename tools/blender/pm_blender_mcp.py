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
def blender_create_collector_direct_session(asset_id: str) -> dict[str, Any]:
    """Create the protected non-exportable working copy from pinned raw v05."""
    return operation("create_collector_direct_session", {"asset_id": _asset(asset_id)})


@SERVER.tool()
def blender_continue_collector_direct_session(
    asset_id: str,
    from_session: str = "collector_v05_direct_mesh_v01",
    to_session: str = "collector_v05_direct_mesh_v02",
) -> dict[str, Any]:
    """Fork one checked-in reviewed direct session into its declared successor."""
    return operation(
        "continue_collector_direct_session",
        {"asset_id": _asset(asset_id), "from_session": _direct_session(from_session), "to_session": _direct_session(to_session)},
    )


@SERVER.tool()
def blender_capture_collector_direct_baseline(asset_id: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Capture raw-v05 working-copy evidence before any direct edit."""
    return operation("capture_collector_direct_baseline", {"asset_id": _asset(asset_id), "session_id": _direct_session(session_id)})


@SERVER.tool()
def blender_begin_collector_direct_mesh_pass(asset_id: str, pass_id: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Open one sequential direct-retopology pass with an exact backup."""
    return operation("begin_collector_direct_mesh_pass", {"asset_id": _asset(asset_id), "pass_id": _direct_pass(pass_id), "session_id": _direct_session(session_id)})


@SERVER.tool()
def blender_inspect_collector_artist_mantle_topology(asset_id: str) -> dict[str, Any]:
    """Read-only topology report for the declared v03 leading-mantle artist region."""
    return operation("inspect_collector_artist_mantle_topology", {"asset_id": _asset(asset_id), "session_id": "collector_v05_direct_mesh_v03"})


@SERVER.tool()
def blender_prepare_collector_artist_leading_mantle(asset_id: str) -> dict[str, Any]:
    """Create only v03 artist vertex groups and locked guides; it never deforms geometry."""
    return operation("prepare_collector_artist_leading_mantle", {"asset_id": _asset(asset_id), "session_id": "collector_v05_direct_mesh_v03"})


@SERVER.tool()
def blender_prepare_collector_master_artist_session(asset_id: str) -> dict[str, Any]:
    """Prepare only the protected Master form pass for visible Blender sculpting."""
    return operation("prepare_collector_master_artist_session", {"asset_id": _asset(asset_id), "session_id": "collector_v05_production_master_v01"})


@SERVER.tool()
def blender_inspect_collector_master_sculpt_assets(asset_id: str) -> dict[str, Any]:
    """Inspect only the isolated Blender profile's sculpt capabilities."""
    return operation("inspect_collector_master_sculpt_assets", {"asset_id": _asset(asset_id), "session_id": "collector_v05_production_master_v01"})


@SERVER.tool()
def blender_complete_collector_direct_mesh_pass(asset_id: str, pass_id: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Seal an edited direct pass and produce its mandatory audit package."""
    return operation("complete_collector_direct_mesh_pass", {"asset_id": _asset(asset_id), "pass_id": _direct_pass(pass_id), "session_id": _direct_session(session_id)})


@SERVER.tool()
def blender_record_collector_direct_review(asset_id: str, pass_id: str, decision: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Record the independent visual continue/rollback decision for one pass."""
    if decision not in {"continue", "rollback"}:
        raise ValueError("decision must be continue or rollback")
    return operation("record_collector_direct_review", {"asset_id": _asset(asset_id), "pass_id": _direct_pass(pass_id), "decision": decision, "session_id": _direct_session(session_id)})


@SERVER.tool()
def blender_rollback_collector_direct_mesh_pass(asset_id: str, pass_id: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Restore a rejected pass from its exact protected full-mesh backup."""
    return operation("rollback_collector_direct_mesh_pass", {"asset_id": _asset(asset_id), "pass_id": _direct_pass(pass_id), "session_id": _direct_session(session_id)})


@SERVER.tool()
def blender_render_collector_direct_audit(asset_id: str, label: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Render the seven-view neutral-clay audit set for active direct-v05 work."""
    if not label or len(label) > 80 or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in label):
        raise ValueError("label must use 1-80 lowercase letters, digits, underscores or dashes")
    return operation("render_collector_direct_audit", {"asset_id": _asset(asset_id), "label": label, "session_id": _direct_session(session_id)})


@SERVER.tool()
def blender_validate_collector_direct_session(asset_id: str, session_id: str = "collector_v05_direct_mesh_v01") -> dict[str, Any]:
    """Validate raw-v05 protection, direct working ownership and non-exportability."""
    return operation("validate_collector_direct_session", {"asset_id": _asset(asset_id), "session_id": _direct_session(session_id)})


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


def _direct_pass(value: str) -> str:
    if value not in {"primary_composition_v01", "diagnostic_solidity_v01", "tail_cleanup_v01", "front_mantle_grounding_v02", "dorsal_cascade_v02", "support_hierarchy_v02", "tail_fan_v02", "surface_coherence_v02", "leading_mantle_artist_v03", "production_master_form_v01", "production_master_surface_v01"}:
        raise ValueError("pass_id must name a declared Collector direct-mesh pass")
    return value


def _direct_session(value: str) -> str:
    if value not in {"collector_v05_direct_mesh_v01", "collector_v05_direct_mesh_v02", "collector_v05_direct_mesh_v03", "collector_v05_production_master_v01"}:
        raise ValueError("session_id must name a checked-in Collector direct-mesh session")
    return value


def main() -> None:
    SERVER.run()


if __name__ == "__main__":
    main()
