#!/usr/bin/env python3
"""Focused contract for the non-authoring VGGT point-cloud reviewer."""

from __future__ import annotations

import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/vggt_omega/render_point_cloud_review.py"
spec = importlib.util.spec_from_file_location("render_point_cloud_review", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load point-cloud review tool")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

if importlib.util.find_spec("numpy") is None:
    source = TOOL.read_text(encoding="utf-8")
    assert "does_not_construct_a_mesh" in source
    assert "requires_locked_primary_trace_review" in source
    print("VGGT point-cloud review static contracts passed (NumPy runtime smoke is Windows-only).")
    raise SystemExit(0)

with TemporaryDirectory(prefix="pm-vggt-point-cloud-test-") as temporary:
    root = Path(temporary)
    ply = root / "input.ply"
    vertices = [
        f"{x} {y} {z} {50 + x * 20} {80 + y * 20} {110 + z * 20}"
        for x in range(5)
        for y in range(5)
        for z in range(5)
    ]
    ply.write_text(
        "ply\nformat ascii 1.0\nelement vertex 125\nproperty float x\nproperty float y\nproperty float z\n"
        "property uchar red\nproperty uchar green\nproperty uchar blue\nend_header\n"
        + "\n".join(vertices)
        + "\n",
        encoding="ascii",
    )
    pilot_manifest = root / "pilot.json"
    pilot_manifest.write_text(
        '{"input":{"frames":[{"yaw_degrees":0},{"yaw_degrees":180}]},'
        '"estimated_camera_poses":[[[1,0,0,0],[0,1,0,0],[0,0,1,0]],'
        '[[1,0,0,0],[0,1,0,0],[0,0,1,0]]]}',
        encoding="utf-8",
    )
    report = module.run(ply, root / "review", 96, 72, 1, pilot_manifest)
    assert report["input"]["point_count"] == 125
    assert report["contract"]["does_not_construct_a_mesh"] is True
    assert report["render"]["views"][:2] == ["source_yaw_000_primary", "source_yaw_180_front"]
    assert (root / "review" / "point_cloud_contact_sheet.png").is_file()

print("VGGT point-cloud review contracts passed.")
