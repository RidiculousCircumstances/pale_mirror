#!/usr/bin/env python3
"""Verify a generated Python trace against the tracked source contract."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--trace-dir", type=Path, required=True)
    args = parser.parse_args()

    repository = Path(__file__).resolve().parents[2]
    contract = json.loads((repository / "docs" / "frontier-reference-source.json").read_text(encoding="utf-8"))
    trace_dir = args.trace_dir.resolve()
    manifest_path = trace_dir / "manifest.json"
    if not manifest_path.is_file():
        raise SystemExit(f"missing trace manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    files, tree_digest = source_manifest(args.reference_root.resolve())
    expected_source = contract["source"]
    if tree_digest != expected_source["tree_sha256"]:
        raise SystemExit(f"source tree hash changed: {tree_digest}; refresh contract deliberately")
    for name, expected in expected_source["required_files"].items():
        if files.get(name) != expected:
            raise SystemExit(f"source file hash changed: {name}")
    if manifest.get("schema") != contract["schema"]:
        raise SystemExit("trace schema does not match contract")
    if manifest.get("source", {}).get("tree_sha256") != tree_digest:
        raise SystemExit("trace was generated from another source tree")
    profile = dict(contract["profile"])
    profile.pop("name", None)
    actual_profile = dict(manifest.get("profile", {}))
    actual_profile.pop("name", None)
    if actual_profile != profile:
        raise SystemExit(f"trace profile mismatch: {actual_profile!r}")

    by_day = {str(item["day"]): item for item in manifest.get("snapshots", [])}
    for day, expected in contract["checkpoints"].items():
        actual = by_day.get(day)
        if actual is None:
            raise SystemExit(f"trace has no day {day}")
        if actual["sha256"] != expected["sha256"] or actual["bytes"] != expected["bytes"]:
            raise SystemExit(f"manifest checkpoint mismatch on day {day}")
        payload = trace_dir / actual["file"]
        if not payload.is_file() or digest(payload) != expected["sha256"] or payload.stat().st_size != expected["bytes"]:
            raise SystemExit(f"snapshot payload mismatch on day {day}")

    if canonical_bytes(manifest) != manifest_path.read_bytes():
        raise SystemExit("trace manifest is not canonical JSON")
    print("Frontier full-source trace matches the pinned Python reference")


if __name__ == "__main__":
    main()
