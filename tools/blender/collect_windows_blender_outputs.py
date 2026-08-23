"""Collect only verified canonical Blender outputs from the private Windows host."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from blender_common import BlenderSettings, load_settings, ssh_arguments


ROOT = Path(__file__).resolve().parents[2]
ASSET = "biomass_collector"
_LABEL = re.compile(r"^[a-z0-9_-]{1,80}$")
_CANDIDATES = (
    "sf3d_v01",
    "hunyuan2mv_v01",
    "hunyuan2mv_v02_primary_anchored",
    "hunyuan2mv_v03_primary_front",
    "hunyuan2mv_v04_calibrated_secondary",
    "hunyuan2mv_v05_canonical_turntable",
)
_SCULPT_SESSIONS = (
    "collector_v05_sculpt_v01",
    "collector_v05_sculpt_v02",
    "collector_v05_sculpt_v03_composition",
)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True, help="The allowlisted Blender audit label to collect")
    parser.add_argument("--candidate", choices=_CANDIDATES, help="Collect only the named non-canonical candidate audit")
    parser.add_argument("--sculpt-session", choices=_SCULPT_SESSIONS, help="Collect one non-exportable direct-sculpt session audit")
    parser.add_argument("--reference-root", type=Path, help="Pinned supplied-reference directory; required for a sculpt-session contour overlay")
    parser.add_argument("--source", action="store_true", help="Collect the canonical .blend source after audit review")
    parser.add_argument("--include-export", action="store_true", help="Collect PMMesh only after the source is explicitly export-ready")
    arguments = parser.parse_args()
    if not _LABEL.fullmatch(arguments.label):
        raise SystemExit("label must use 1-80 lowercase letters, digits, underscores or dashes")
    settings = load_settings()
    if arguments.candidate and arguments.sculpt_session:
        raise SystemExit("candidate and sculpt-session are mutually exclusive")
    if arguments.sculpt_session and arguments.reference_root is None:
        raise SystemExit("sculpt-session collection requires --reference-root so the direct primary overlay cannot be skipped")
    if arguments.sculpt_session:
        audit_local = ROOT / "build" / "blender-audits" / f"{arguments.label}-{ASSET}-{arguments.sculpt_session}"
        audit_remote = f"sculpt_sessions/{arguments.sculpt_session}/audits/{arguments.label}"
    elif arguments.candidate:
        audit_local = ROOT / "build" / "blender-audits" / f"{arguments.label}-{ASSET}-{arguments.candidate}"
        audit_remote = f"candidates/{ASSET}/{arguments.candidate}/audits/{arguments.label}"
    else:
        audit_local = ROOT / "build" / "blender-audits" / f"{arguments.label}-{ASSET}"
        audit_remote = f"audits/{ASSET}/{arguments.label}"
    outputs: dict[str, str] = {}
    views = ("primary", "opposite", "front", "side", "elevated_rear", "silhouette_primary")
    if arguments.sculpt_session or not arguments.candidate or arguments.candidate.startswith("hunyuan2mv_"):
        views += ("primary_trace",)
    for view in views:
        relative = f"{audit_remote}/{view}.png"
        target = audit_local / f"{view}.png"
        _copy_verified(settings, relative, target)
        outputs[view] = str(target.relative_to(ROOT))
    if arguments.sculpt_session:
        reference = arguments.reference_root / "01_harvester_biomass_collector.jpg"
        overlay = audit_local / "primary_model_overlay.png"
        metadata = audit_local / "primary_model_overlay.json"
        subprocess.run(
            [
                "python3",
                str(ROOT / "tools" / "harvester_primary_model_overlay.py"),
                "--reference",
                str(reference),
                "--trace-render",
                str(audit_local / "primary_trace.png"),
                "--silhouette",
                str(audit_local / "silhouette_primary.png"),
                "--output",
                str(overlay),
                "--metadata",
                str(metadata),
            ],
            check=True,
        )
        outputs["primary_model_overlay"] = str(overlay.relative_to(ROOT))
        outputs["primary_model_overlay_metadata"] = str(metadata.relative_to(ROOT))
    if (arguments.candidate or arguments.sculpt_session) and (arguments.include_export or arguments.source):
        raise SystemExit("Non-canonical proposal and sculpt-session audits cannot collect canonical source or PMMesh export")
    if arguments.include_export:
        export_target = audit_local / f"{ASSET}.pmmesh.json"
        _copy_verified(settings, f"exports/{ASSET}.pmmesh.json", export_target)
        outputs["export"] = str(export_target.relative_to(ROOT))
    if arguments.source:
        source_target = ROOT / "pale-mirror-visuals" / "src" / "main" / "blender" / "harvester" / f"{ASSET}.blend"
        _copy_verified(settings, f"assets/harvester/{ASSET}.blend", source_target)
        outputs["source"] = str(source_target.relative_to(ROOT))
    manifest = {
        "schema": "pale_mirror.blender_collect.v1",
        "asset_id": ASSET,
        "candidate_id": arguments.candidate,
        "sculpt_session": arguments.sculpt_session,
        "label": arguments.label,
        "files": {name: _sha256(ROOT / relative) for name, relative in sorted(outputs.items())},
    }
    manifest_path = audit_local / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"audit": str(audit_local.relative_to(ROOT)), "files": outputs}, indent=2, sort_keys=True))


def _copy_verified(settings: BlenderSettings, relative: str, target: Path) -> None:
    remote_hash = _remote_sha256(settings, relative)
    target.parent.mkdir(parents=True, exist_ok=True)
    remote = _windows_path(settings, relative)
    subprocess.run(
        [
            "scp",
            "-F", "/dev/null",
            "-i", str(settings.identity_file),
            "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=yes",
            "-o", f"UserKnownHostsFile={settings.known_hosts_file}",
            "-o", "GlobalKnownHostsFile=/dev/null",
            "-p",
            f"{settings.target}:{remote}",
            str(target),
        ],
        check=True,
    )
    local_hash = _sha256(target)
    if local_hash != remote_hash:
        raise RuntimeError(f"SHA-256 mismatch collecting {relative}: remote {remote_hash}, local {local_hash}")


def _remote_sha256(settings: BlenderSettings, relative: str) -> str:
    path = _windows_command_path(settings, relative)
    script = f"(Get-FileHash -Algorithm SHA256 -LiteralPath '{path}').Hash.ToLowerInvariant()"
    encoded = __import__("base64").b64encode(script.encode("utf-16le")).decode("ascii")
    completed = subprocess.run(
        ssh_arguments(settings) + [settings.target, f"powershell.exe -NoProfile -NonInteractive -EncodedCommand {encoded}"],
        check=True,
        capture_output=True,
        text=True,
        errors="replace",
    )
    value = completed.stdout.strip().splitlines()[-1].strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise RuntimeError(f"Windows host returned an invalid SHA-256 for {relative}: {completed.stdout!r}")
    return value


def _windows_path(settings: BlenderSettings, relative: str) -> str:
    return settings.windows_workspace.replace("\\", "/").rstrip("/") + "/" + relative


def _windows_command_path(settings: BlenderSettings, relative: str) -> str:
    return settings.windows_workspace.rstrip("\\") + "\\" + relative.replace("/", "\\")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    main()
