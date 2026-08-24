#!/usr/bin/env python3
"""Focused contracts for the bounded VGGT Collector pilot runner."""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/vggt_omega/run_collector_pilot.py"
spec = importlib.util.spec_from_file_location("run_collector_pilot", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load run_collector_pilot")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

source = TOOL.read_text(encoding="utf-8")
assert "weights_only=True" in source
assert "non-tensor state entry" in source
assert "official-checkpoint-receipt" in source
assert "turntable-review-receipt" in source


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


with TemporaryDirectory(prefix="pm-vggt-pilot-test-") as temporary:
    root = Path(temporary)
    frames = []
    for index in range(12):
        path = root / f"frame_{index}.png"
        path.write_bytes(f"frame-{index}".encode("ascii"))
        frames.append(
            {
                "index": index,
                "yaw_degrees": index * 30,
                "file": path.name,
                "sha256": digest(path),
                "source": "literal_primary_trace_masked" if index == 0 else "generated_turntable_panel_unreviewed",
                "authority": "sole_likeness_anchor" if index == 0 else "secondary_depth_suggestion_only",
            }
        )
    manifest = {
        "schema": module.EXPECTED_SCHEMA,
        "asset_id": "biomass_collector",
        "frame_count": 12,
        "frames": frames,
        "acceptance_contract": {"vggt_output_is_noncanonical_nonexportable": True},
    }
    manifest_path = root / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    parsed, selected = module.load_pilot_inputs(manifest_path, 10, module.REVIEW_ACKNOWLEDGEMENT)
    assert parsed["asset_id"] == "biomass_collector"
    assert selected[0]["index"] == 0 and selected[-1]["index"] == 11
    assert len({item["index"] for item in selected}) == 10
    try:
        module.load_pilot_inputs(manifest_path, 10, "wrong")
    except ValueError as error:
        assert "acknowledgement" in str(error)
    else:
        raise AssertionError("VGGT runner must require a manual-review acknowledgement")

    turntable_receipt = root / "turntable_review_receipt.json"
    turntable_receipt.write_text(
        json.dumps(
            {
                "schema": module.TURN_TABLE_REVIEW_SCHEMA,
                "scope": "research_only_noncanonical",
                "promotion_prohibited": True,
                "decision": "eligible_for_pose_depth_only",
            }
        ),
        encoding="utf-8",
    )
    manifest["automodel_provenance"] = {
        "turntable_review_receipt": {"sha256": digest(turntable_receipt)}
    }
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    try:
        module.load_pilot_inputs(manifest_path, 10, module.REVIEW_ACKNOWLEDGEMENT)
    except ValueError as error:
        assert "turntable-review receipt" in str(error)
    else:
        raise AssertionError("Automodel VGGT input must require its reviewed-orbit receipt")
    _, reviewed_selected = module.load_pilot_inputs(
        manifest_path,
        10,
        module.REVIEW_ACKNOWLEDGEMENT,
        turntable_receipt,
    )
    assert len(reviewed_selected) == 10

    checkpoint = root / "private-vggt.pt"
    checkpoint.write_bytes(b"official-vggt-tensors")
    receipt_path = root / "build" / "automodel" / "biomass_collector" / "official" / "checkpoint_receipt.json"
    receipt_path.parent.mkdir(parents=True)
    receipt_path.write_text(
        json.dumps(
            {
                "schema": module.OFFICIAL_CHECKPOINT_SCHEMA,
                "model_id": "vggt_official",
                "scope": "research_only_noncanonical",
                "promotion_prohibited": True,
                "official_origin": {"repository": module.OFFICIAL_REPOSITORY, "filename": module.OFFICIAL_FILENAME},
                "checkpoint": {"sha256": digest(checkpoint), "byte_size": checkpoint.stat().st_size, "storage": "private_local_host"},
                "contract": {"weights_not_copied_to_repository": True, "canonical_promotion_forbidden": True},
            }
        ),
        encoding="utf-8",
    )
    pinned = module.load_official_checkpoint_receipt(receipt_path, checkpoint, repository_root=root)
    assert pinned["official_origin"]["repository"] == module.OFFICIAL_REPOSITORY
    receipt_path.write_text(receipt_path.read_text(encoding="utf-8").replace("private_local_host", "quarantine"), encoding="utf-8")
    try:
        module.load_official_checkpoint_receipt(receipt_path, checkpoint, repository_root=root)
    except ValueError as error:
        assert "registered official" in str(error)
    else:
        raise AssertionError("VGGT runner must reject a non-official checkpoint receipt")

print("VGGT Collector pilot contracts passed.")
