"""Small CLI for the same whitelisted Blender operations exposed through MCP."""

from __future__ import annotations

import argparse
import json

from blender_operation_client import canonical_asset, operation


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "operation",
        choices=("create_collector_direct_session", "continue_collector_direct_session", "capture_collector_direct_baseline", "begin_collector_direct_mesh_pass", "inspect_collector_artist_mantle_topology", "prepare_collector_artist_leading_mantle", "prepare_collector_master_artist_session", "inspect_collector_master_sculpt_assets", "complete_collector_direct_mesh_pass", "record_collector_direct_review", "rollback_collector_direct_mesh_pass", "render_collector_direct_audit", "validate_collector_direct_session", "open_asset", "inspect_asset", "save_asset", "render_audit", "validate_asset", "export_pmmesh"),
    )
    parser.add_argument("--asset", default="biomass_collector")
    parser.add_argument("--label", default="manual")
    parser.add_argument(
        "--direct-pass",
        choices=("primary_composition_v01", "diagnostic_solidity_v01", "tail_cleanup_v01", "front_mantle_grounding_v02", "dorsal_cascade_v02", "support_hierarchy_v02", "tail_fan_v02", "surface_coherence_v02", "leading_mantle_artist_v03", "production_master_form_v01", "production_master_surface_v01"),
        default="primary_composition_v01",
    )
    parser.add_argument("--direct-session", choices=("collector_v05_direct_mesh_v01", "collector_v05_direct_mesh_v02", "collector_v05_direct_mesh_v03", "collector_v05_production_master_v01"), default="collector_v05_direct_mesh_v01")
    parser.add_argument("--from-direct-session", choices=("collector_v05_direct_mesh_v01", "collector_v05_direct_mesh_v02"), default="collector_v05_direct_mesh_v01")
    parser.add_argument("--to-direct-session", choices=("collector_v05_direct_mesh_v02", "collector_v05_direct_mesh_v03", "collector_v05_production_master_v01"), default="collector_v05_direct_mesh_v02")
    parser.add_argument("--review-decision", choices=("continue", "rollback"))
    arguments = parser.parse_args()
    payload: dict[str, str] = {"asset_id": canonical_asset(arguments.asset)}
    if arguments.operation == "continue_collector_direct_session":
        payload["from_session"] = arguments.from_direct_session
        payload["to_session"] = arguments.to_direct_session
    if arguments.operation in {"capture_collector_direct_baseline", "begin_collector_direct_mesh_pass", "inspect_collector_artist_mantle_topology", "prepare_collector_artist_leading_mantle", "prepare_collector_master_artist_session", "inspect_collector_master_sculpt_assets", "complete_collector_direct_mesh_pass", "record_collector_direct_review", "rollback_collector_direct_mesh_pass", "render_collector_direct_audit", "validate_collector_direct_session"}:
        payload["session_id"] = arguments.direct_session
    if arguments.operation in {"render_audit", "render_collector_direct_audit"}:
        payload["label"] = arguments.label
    if arguments.operation in {"begin_collector_direct_mesh_pass", "complete_collector_direct_mesh_pass", "record_collector_direct_review", "rollback_collector_direct_mesh_pass"}:
        payload["pass_id"] = arguments.direct_pass
    if arguments.operation == "record_collector_direct_review":
        payload["decision"] = arguments.review_decision or "rollback"
    print(json.dumps(operation(arguments.operation, payload), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
