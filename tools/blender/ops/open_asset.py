"""Open the only canonical Blender source owned by the current allowlist."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ASSET_ID, SOURCE_PATH, asset_id, ensure_directories  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    ensure_directories()
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector source does not exist; run create_collector_base first.")
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))
    return {"asset_id": ASSET_ID, "source": SOURCE_PATH.as_posix(), "opened": True}
