#!/usr/bin/env python3
"""Safely launch the pinned async-profiler against one explicit owned JVM."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import time
from pathlib import Path
from typing import Any

VERSION = "4.5"
ARCHIVE_SHA256 = "89546fbb9ee0fc5496c7edd4099b0709489bc78b0d8057ccbb4b801f6b032b62"
LAUNCHER_SHA256 = "bd479cae0c2eb4c94af9327b8ab274ca9bc704d1d7b27d03d57ee188d3d11cbe"
LIBRARY_SHA256 = "3c03ce90c8c34332480d14cb0a5b9eb380c0dbc0da915486b3348bcfaf026d61"
DEFAULT_HOME = Path.home() / ".local/share/pale-mirror-tools" / f"async-profiler-{VERSION}-linux-x64"
DEFAULT_OUTPUT_ROOT = Path.home() / ".local/state/pale-mirror/profiles"
EVENTS = {"cpu", "wall", "alloc", "lock"}
FORMATS = {"jfr", "html"}


class ProfileError(RuntimeError):
    pass


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def installation(home: Path) -> tuple[Path, dict[str, Any]]:
    manifest_path = home / "pale-mirror-install.json"
    launcher = home / "bin/asprof"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ProfileError("pinned async-profiler manifest is unavailable") from exc
    expected = {"version": VERSION, "archive_sha256": ARCHIVE_SHA256, "asset": f"async-profiler-{VERSION}-linux-x64.tar.gz"}
    if any(manifest.get(key) != value for key, value in expected.items()):
        raise ProfileError("async-profiler manifest does not match the project pin")
    if not launcher.is_file() or not os.access(launcher, os.X_OK):
        raise ProfileError("async-profiler launcher is unavailable")
    library = home / "lib/libasyncProfiler.so"
    if digest(launcher) != LAUNCHER_SHA256 or not library.is_file() or digest(library) != LIBRARY_SHA256:
        raise ProfileError("async-profiler installed files failed attestation")
    return launcher, manifest


def java_process(pid: int) -> dict[str, Any]:
    if pid <= 1:
        raise ProfileError("an explicit non-system PID is required")
    proc = Path("/proc") / str(pid)
    try:
        status = (proc / "status").read_text(encoding="utf-8")
        cmdline = (proc / "cmdline").read_bytes().replace(b"\0", b" ").decode("utf-8", errors="replace").strip()
    except OSError as exc:
        raise ProfileError("target PID is not readable") from exc
    uid_match = next((line for line in status.splitlines() if line.startswith("Uid:")), None)
    if not uid_match or int(uid_match.split()[1]) != os.geteuid():
        raise ProfileError("target PID is not owned by the current user")
    executable = (proc / "exe").resolve(strict=True).name.lower()
    if "java" not in executable or not cmdline:
        raise ProfileError("target PID is not a Java process")
    return {"pid": pid, "executable": executable, "cmdline": cmdline[:2048]}


def safe_output(root: Path, name: str, output_format: str) -> Path:
    if not name or name in {".", ".."} or "/" in name or "\\" in name or len(name) > 120:
        raise ProfileError("output name must be one safe basename")
    root = root.expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(root, 0o700)
    suffix = ".jfr" if output_format == "jfr" else ".html"
    path = (root / f"{name}{suffix}").resolve()
    if path.parent != root or path.exists():
        raise ProfileError("profile output must be a new file under the private profile root")
    return path


def command(launcher: Path, pid: int, event: str, duration: int, output_format: str, output: Path) -> list[str]:
    if event not in EVENTS or output_format not in FORMATS or not 1 <= duration <= 120:
        raise ProfileError("profile event, format or duration is outside the bounded policy")
    return [str(launcher), "-d", str(duration), "-e", event, "-o", output_format, "-f", str(output), str(pid)]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pid", type=int)
    parser.add_argument("--event", choices=sorted(EVENTS), default="cpu")
    parser.add_argument("--duration", type=int, default=30)
    parser.add_argument("--format", choices=sorted(FORMATS), default="jfr")
    parser.add_argument("--name", required=True)
    parser.add_argument("--profiler-home", type=Path, default=Path(os.environ.get("PM_ASYNC_PROFILER_HOME", DEFAULT_HOME)))
    parser.add_argument("--output-root", type=Path, default=Path(os.environ.get("PM_PROFILE_OUTPUT_ROOT", DEFAULT_OUTPUT_ROOT)))
    parser.add_argument("--run", action="store_true", help="attach; without this flag only validate and print the command")
    return parser


def main() -> int:
    arguments = build_parser().parse_args()
    try:
        launcher, manifest = installation(arguments.profiler_home.expanduser().resolve())
        target = java_process(arguments.pid)
        output = safe_output(arguments.output_root, arguments.name, arguments.format)
        invocation = command(launcher, arguments.pid, arguments.event, arguments.duration, arguments.format, output)
        receipt: dict[str, Any] = {
            "schema_version": 1,
            "mode": "run" if arguments.run else "dry-run",
            "target": target,
            "event": arguments.event,
            "duration_seconds": arguments.duration,
            "format": arguments.format,
            "output": str(output),
            "profiler": {"version": manifest["version"], "archive_sha256": manifest["archive_sha256"]},
            "command": invocation,
        }
        if arguments.run:
            started = time.monotonic()
            completed = subprocess.run(invocation, check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=arguments.duration + 30)
            receipt.update({"exit_code": completed.returncode, "elapsed_seconds": round(time.monotonic() - started, 3), "stdout": completed.stdout[-8000:], "stderr": completed.stderr[-8000:]})
            receipt_path = output.with_suffix(output.suffix + ".receipt.json")
            receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            os.chmod(receipt_path, 0o600)
            if completed.returncode != 0:
                raise ProfileError(f"async-profiler exited {completed.returncode}; see {receipt_path}")
        print(json.dumps(receipt, indent=2, sort_keys=True))
        return 0
    except (ProfileError, subprocess.TimeoutExpired) as exc:
        print(json.dumps({"error": str(exc), "state": "invalid"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
