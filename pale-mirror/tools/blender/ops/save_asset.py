"""Save the active canonical source only to its fixed workspace path."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ASSET_ID, SOURCE_PATH, asset_id, ensure_directories  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    ensure_directories()
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    return {"asset_id": ASSET_ID, "source": SOURCE_PATH.as_posix(), "saved": True}
