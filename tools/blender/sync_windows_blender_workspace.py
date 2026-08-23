"""Synchronize only versioned PM Blender operations and explicit references.

The destination is intentionally append/replace-only. It never mirrors or
deletes arbitrary Windows files and finishes by writing a SHA-256 manifest.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

from blender_common import BlenderSettings, load_settings, ssh_arguments


ROOT = Path(__file__).resolve().parents[2]
SYNC_PATHS = (
    Path("tools/blender/ops"),
    Path("tools/blender/pmmesh_schema_v1.json"),
    Path("tools/hunyuan3d_2mv"),
)
REFERENCE_FILES = ("01_harvester_biomass_collector.jpg",)
SECONDARY_REFERENCE_FILES = (
    "biomass_collector_turntable_v01.png",
    "biomass_collector_hunyuan_v02_front_imagegen.png",
    "biomass_collector_hunyuan_v02_back_imagegen.png",
    "biomass_collector_hunyuan_v02_right_imagegen.png",
    "biomass_collector_hunyuan_v03_left_imagegen.png",
    "biomass_collector_hunyuan_v03_back_imagegen.png",
    "biomass_collector_hunyuan_v03_right_imagegen.png",
    "biomass_collector_hunyuan_v04_left_imagegen.png",
    "biomass_collector_hunyuan_v04_back_imagegen.png",
    "biomass_collector_hunyuan_v04_right_imagegen.png",
    "biomass_collector_hunyuan_v05_tail_on_imagegen.png",
    "biomass_collector_hunyuan_v05_opposite_broadside_imagegen.png",
    "biomass_collector_hunyuan_v05_head_on_imagegen.png",
    "biomass_collector_turntable_v02_elevated_near_dorsal_imagegen.png",
    "biomass_collector_turntable_v02_low_opposite_front_imagegen.png",
    "biomass_collector_turntable_v02_canonical_contact_sheet.png",
    "biomass_collector_turntable_v02.provenance.json",
)
SECONDARY_REFERENCE_ROOT = Path("pale-mirror-visuals/src/main/blender/harvester/references")
TRACE_FILES = (
    "biomass_collector_primary_v03.json",
    "biomass_collector_semantic_cage_v01.json",
)
TRACE_ROOT = Path("pale-mirror-visuals/src/main/blender/harvester/traces")
SCULPT_PROTOCOL_FILES = (
    "collector_v05_sculpt_v01.json",
    "collector_v05_sculpt_v02.json",
    "collector_v05_sculpt_v03_composition.json",
)
SCULPT_PROTOCOL_ROOT = Path("pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True, help="Directory containing pinned user reference images")
    arguments = parser.parse_args()
    settings = load_settings()
    files = _source_files(arguments.reference_root)
    _mkdirs(settings, {path.parent for path in files.values()})
    for source, destination in files.items():
        _copy(settings, source, destination)
    manifest = {
        "schema": "pale_mirror.blender_sync.v1",
        "files": {destination.as_posix(): _sha256(source) for source, destination in sorted(files.items())},
    }
    with tempfile.TemporaryDirectory(prefix="pm-blender-sync-") as temporary:
        manifest_file = Path(temporary) / "manifest.json"
        manifest_file.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        _copy(settings, manifest_file, Path("manifest.json"))
    print(json.dumps({"destination": settings.windows_workspace, "files": len(files), "manifest": manifest}, indent=2))


def _source_files(reference_root: Path) -> dict[Path, Path]:
    result: dict[Path, Path] = {}
    for entry in SYNC_PATHS:
        source = ROOT / entry
        if source.is_dir():
            for child in source.rglob("*"):
                if child.is_file() and "__pycache__" not in child.parts:
                    result[child] = child.relative_to(ROOT)
        elif source.is_file():
            result[source] = entry
        else:
            raise FileNotFoundError(f"Required Blender source is missing: {source}")
    for name in REFERENCE_FILES:
        source = reference_root / name
        if not source.is_file():
            raise FileNotFoundError(f"Pinned reference is missing: {source}")
        result[source] = Path("references") / name
    for name in SECONDARY_REFERENCE_FILES:
        source = ROOT / SECONDARY_REFERENCE_ROOT / name
        if not source.is_file():
            raise FileNotFoundError(f"Secondary diagnostic reference is missing: {source}")
        result[source] = Path("references") / "secondary" / name
    for name in TRACE_FILES:
        source = ROOT / TRACE_ROOT / name
        if not source.is_file():
            raise FileNotFoundError(f"Primary trace is missing: {source}")
        result[source] = Path("traces") / name
    for name in SCULPT_PROTOCOL_FILES:
        source = ROOT / SCULPT_PROTOCOL_ROOT / name
        if not source.is_file():
            raise FileNotFoundError(f"Collector sculpt protocol is missing: {source}")
        result[source] = Path("sculpt_protocol") / name
    return result


def _mkdirs(settings: BlenderSettings, directories: set[Path]) -> None:
    # Use EncodedCommand so the Windows SSH command shell cannot reinterpret
    # a drive path before PowerShell sees it. One call also avoids an SSH
    # handshake per operation file.
    script = "; ".join(
        f"New-Item -ItemType Directory -Force -Path '{_windows_command_path(settings, directory)}' | Out-Null"
        for directory in sorted(directories)
    )
    encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
    command = f"powershell.exe -NoProfile -NonInteractive -EncodedCommand {encoded}"
    subprocess.run(ssh_arguments(settings) + [settings.target, command], check=True)


def _copy(settings: BlenderSettings, source: Path, destination: Path) -> None:
    target = _windows_path(settings, destination)
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
            str(source),
            f"{settings.target}:{target}",
        ],
        check=True,
    )


def _windows_path(settings: BlenderSettings, relative: Path) -> str:
    return settings.windows_workspace.replace("\\", "/").rstrip("/") + "/" + relative.as_posix()


def _windows_command_path(settings: BlenderSettings, relative: Path) -> str:
    return settings.windows_workspace.rstrip("\\") + "\\" + str(relative).replace("/", "\\")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    main()
