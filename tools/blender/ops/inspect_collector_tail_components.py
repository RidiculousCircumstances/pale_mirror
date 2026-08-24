"""Read-only component inventory for the final direct-v05 cleanup pass."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from apply_collector_tail_cleanup import _component_projects_into_trace, _trace_cells  # noqa: E402
from collector_direct_common import ASSET_ID, connected_face_components, require_session_scene  # noqa: E402
from common import load_primary_trace  # noqa: E402


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    _protocol, _protocol_hash, working = require_session_scene(payload)
    trace, _trace_hash = load_primary_trace()
    cells = _trace_cells(trace)
    rows = []
    for ordinal, component in enumerate(connected_face_components(working.data)):
        rows.append({
            "ordinal": ordinal,
            "faces": len(component),
            "projects_into_primary_trace": _component_projects_into_trace(working, component, trace, cells),
        })
    return {"asset_id": ASSET_ID, "components": rows, "read_only": True}


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
