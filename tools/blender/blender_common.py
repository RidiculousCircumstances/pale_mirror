"""Local-only configuration and SSH transport for the Windows Blender host."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os
import socket
import subprocess
import time
import tomllib


class BlenderConfigurationError(RuntimeError):
    """Raised when the private Blender host is not configured safely."""


@dataclass(frozen=True)
class BlenderSettings:
    host: str
    user: str
    identity_file: Path
    known_hosts_file: Path
    local_port: int
    remote_port: int
    windows_workspace: str

    @property
    def target(self) -> str:
        return f"{self.user}@{self.host}"


def default_config_path() -> Path:
    override = os.environ.get("PALE_MIRROR_BLENDER_CONFIG")
    if override:
        return Path(override).expanduser()
    return Path.home() / ".config" / "pale-mirror" / "blender.toml"


def load_settings(path: Path | None = None) -> BlenderSettings:
    path = path or default_config_path()
    if not path.is_file():
        raise BlenderConfigurationError(
            f"Blender host configuration is missing: {path}. Copy blender.toml.example outside the repository."
        )
    with path.open("rb") as source:
        value = tomllib.load(source)
    connection = value.get("connection", {})
    windows = value.get("windows", {})
    host = str(connection.get("host", "")).strip()
    user = str(connection.get("user", "")).strip()
    identity = Path(str(connection.get("identity_file", ""))).expanduser()
    known_hosts = Path(str(connection.get("known_hosts_file", ""))).expanduser()
    workspace = str(windows.get("workspace", "")).strip()
    if not host or host == "CHANGE_ME" or not user or user == "CHANGE_ME":
        raise BlenderConfigurationError("Blender host and user must be set in the private configuration.")
    if not identity.is_file():
        raise BlenderConfigurationError(f"SSH identity file is missing: {identity}")
    if not known_hosts.is_file():
        raise BlenderConfigurationError(
            f"Pinned SSH known-hosts file is missing: {known_hosts}. Do not use accept-new for the Blender host."
        )
    if not workspace or not _safe_windows_path(workspace):
        raise BlenderConfigurationError("windows.workspace must be an absolute, shell-safe Windows path.")
    return BlenderSettings(
        host=host,
        user=user,
        identity_file=identity,
        known_hosts_file=known_hosts,
        local_port=_port(connection.get("local_port", 19876), "local_port"),
        remote_port=_port(connection.get("remote_port", 9876), "remote_port"),
        windows_workspace=workspace,
    )


def ssh_arguments(settings: BlenderSettings) -> list[str]:
    return [
        "ssh",
        "-F", "/dev/null",
        "-i", str(settings.identity_file),
        "-o", "BatchMode=yes",
        "-o", "PasswordAuthentication=no",
        "-o", "KbdInteractiveAuthentication=no",
        "-o", "StrictHostKeyChecking=yes",
        "-o", f"UserKnownHostsFile={settings.known_hosts_file}",
        "-o", "GlobalKnownHostsFile=/dev/null",
        "-o", "ConnectTimeout=8",
    ]


def ensure_loopback_tunnel(settings: BlenderSettings) -> None:
    """Open one authenticated local forward if no process already owns it."""
    if _port_open(settings.local_port):
        return
    process = subprocess.Popen(
        ssh_arguments(settings)
        + [
            "-o", "ExitOnForwardFailure=yes",
            "-N",
            "-L", f"127.0.0.1:{settings.local_port}:127.0.0.1:{settings.remote_port}",
            settings.target,
        ],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
        start_new_session=True,
    )
    deadline = time.monotonic() + 8.0
    while time.monotonic() < deadline:
        if _port_open(settings.local_port):
            return
        if process.poll() is not None:
            detail = process.stderr.read().strip() if process.stderr else ""
            raise BlenderConfigurationError(f"Could not start Blender SSH tunnel: {detail or process.returncode}")
        time.sleep(0.1)
    process.terminate()
    raise BlenderConfigurationError("Blender SSH tunnel did not bind its local loopback port in time.")


def _port(value: object, label: str) -> int:
    try:
        port = int(value)
    except (TypeError, ValueError) as error:
        raise BlenderConfigurationError(f"{label} must be an integer port.") from error
    if not 1024 <= port <= 65535:
        raise BlenderConfigurationError(f"{label} must be between 1024 and 65535.")
    return port


def _port_open(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.25):
            return True
    except OSError:
        return False


def _safe_windows_path(value: str) -> bool:
    if len(value) < 4 or value[1:3] != ":\\":
        return False
    return not any(character in value for character in "&|<>^\"'`\n\r")
