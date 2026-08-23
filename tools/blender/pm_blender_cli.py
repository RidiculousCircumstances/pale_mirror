"""Small CLI for the same whitelisted Blender operations exposed through MCP."""

from __future__ import annotations

import argparse
import json

from blender_operation_client import canonical_asset, operation


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "operation",
        choices=("create_collector_base", "build_collector_volume", "build_collector_trace_cage", "build_collector_semantic_cage", "create_collector_sculpt_branch", "capture_collector_sculpt_baseline", "apply_collector_sculpt_pass", "repair_collector_sculpt_branch_metadata", "render_collector_sculpt_audit", "validate_collector_sculpt_branch", "open_asset", "inspect_asset", "save_asset", "render_audit", "validate_asset", "export_pmmesh", "import_sf3d_candidate", "render_sf3d_candidate_audit", "import_hunyuan2mv_candidate", "fit_hunyuan2mv_v03_trace_proxy", "build_hunyuan2mv_v03_trace_hull_proxy", "smooth_hunyuan2mv_v03_trace_hull_proxy", "render_hunyuan2mv_candidate_audit"),
    )
    parser.add_argument("--asset", default="biomass_collector")
    parser.add_argument("--label", default="manual")
    parser.add_argument(
        "--sculpt-pass",
        choices=("front_mantle_v03", "dorsal_rhythm_v03", "supports_v03"),
        default="front_mantle_v03",
    )
    parser.add_argument(
        "--candidate",
        choices=(
            "hunyuan2mv_v01",
            "hunyuan2mv_v02_primary_anchored",
            "hunyuan2mv_v03_primary_front",
            "hunyuan2mv_v04_calibrated_secondary",
            "hunyuan2mv_v05_canonical_turntable",
        ),
        help="Fixed non-canonical Hunyuan2mv proposal to review.",
    )
    arguments = parser.parse_args()
    payload: dict[str, str] = {"asset_id": canonical_asset(arguments.asset)}
    if arguments.operation in {"render_audit", "render_sf3d_candidate_audit", "render_hunyuan2mv_candidate_audit", "render_collector_sculpt_audit"}:
        payload["label"] = arguments.label
    if arguments.operation == "apply_collector_sculpt_pass":
        payload["pass_id"] = arguments.sculpt_pass
    if arguments.operation in {"import_hunyuan2mv_candidate", "fit_hunyuan2mv_v03_trace_proxy", "build_hunyuan2mv_v03_trace_hull_proxy", "smooth_hunyuan2mv_v03_trace_hull_proxy", "render_hunyuan2mv_candidate_audit"}:
        payload["candidate_id"] = arguments.candidate or "hunyuan2mv_v03_primary_front"
    print(json.dumps(operation(arguments.operation, payload), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
