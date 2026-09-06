"""Transport for the versioned Blender operation allowlist.

This module is deliberately independent of the MCP SDK so command-line smoke
tests use the exact same boundary without importing or starting an MCP server.
"""

from __future__ import annotations

import base64
import json
from pathlib import PureWindowsPath
import socket
import threading
from typing import Any

from blender_common import BlenderConfigurationError, BlenderSettings, ensure_loopback_tunnel, load_settings


ASSETS = {"biomass_collector"}
OPERATIONS = {
    "create_collector_direct_session": "create_collector_direct_session.py",
    "continue_collector_direct_session": "continue_collector_direct_session.py",
    "capture_collector_direct_baseline": "capture_collector_direct_baseline.py",
    "begin_collector_direct_mesh_pass": "begin_collector_direct_mesh_pass.py",
    "apply_collector_primary_composition": "apply_collector_primary_composition.py",
    "repair_collector_direct_backup_metadata": "repair_collector_direct_backup_metadata.py",
    "apply_collector_diagnostic_solidity": "apply_collector_diagnostic_solidity.py",
    "apply_collector_tail_cleanup": "apply_collector_tail_cleanup.py",
    "apply_collector_front_mantle_grounding": "apply_collector_front_mantle_grounding.py",
    "inspect_collector_artist_mantle_topology": "inspect_collector_artist_mantle_topology.py",
    "prepare_collector_artist_leading_mantle": "prepare_collector_artist_leading_mantle.py",
    "prepare_collector_master_artist_session": "prepare_collector_master_artist_session.py",
    "inspect_collector_master_sculpt_assets": "inspect_collector_master_sculpt_assets.py",
    "inspect_collector_tail_components": "inspect_collector_tail_components.py",
    "complete_collector_direct_mesh_pass": "complete_collector_direct_mesh_pass.py",
    "record_collector_direct_review": "record_collector_direct_review.py",
    "rollback_collector_direct_mesh_pass": "rollback_collector_direct_mesh_pass.py",
    "render_collector_direct_audit": "render_collector_direct_audit.py",
    "validate_collector_direct_session": "validate_collector_direct_session.py",
    "open_asset": "open_asset.py",
    "inspect_asset": "inspect_asset.py",
    "save_asset": "save_asset.py",
    "render_audit": "render_audit.py",
    "validate_asset": "validate_asset.py",
    "export_pmmesh": "export_pmmesh.py",
}
_REQUEST_LOCK = threading.Lock()


def canonical_asset(value: str) -> str:
    if value not in ASSETS:
        raise ValueError(f"Unsupported canonical asset: {value}")
    return value


def operation(operation_id: str, arguments: dict[str, Any]) -> dict[str, Any]:
    if operation_id not in OPERATIONS:
        raise ValueError(f"Unknown project script: {operation_id}")
    if not isinstance(arguments, dict):
        raise ValueError("arguments must be an object")
    settings = load_settings()
    try:
        encoded_arguments = base64.b64encode(json.dumps(arguments, sort_keys=True).encode("utf-8")).decode("ascii")
    except (TypeError, ValueError) as error:
        raise ValueError("arguments must be JSON-serializable") from error
    if len(encoded_arguments) > 16_384:
        raise ValueError("arguments exceed the controlled-operation limit")
    workspace = PureWindowsPath(settings.windows_workspace)
    operation_path = workspace / "tools" / "blender" / "ops" / OPERATIONS[operation_id]
    operations_root = operation_path.parent
    code = (
        "import base64, importlib, json, pathlib, runpy, sys\n"
        "importlib.invalidate_caches()\n"
        f"operations_root = pathlib.Path({str(operations_root)!r}).resolve()\n"
        "for name, module in tuple(sys.modules.items()):\n"
        "    source = getattr(module, '__file__', None)\n"
        "    if source and pathlib.Path(source).resolve().parent == operations_root:\n"
        "        sys.modules.pop(name, None)\n"
        f"operation = runpy.run_path({str(operation_path)!r})\n"
        f"payload = json.loads(base64.b64decode({encoded_arguments!r}).decode('utf-8'))\n"
        "result = operation['main'](payload)\n"
    )
    return execute(settings, code)


def status() -> dict[str, Any]:
    settings = load_settings()
    return execute(
        settings,
        (
            "import bpy\n"
            "result = {'blender_version': bpy.app.version_string, 'background': bpy.app.background, "
            "'file': bpy.data.filepath, 'objects': len(bpy.context.scene.objects)}"
        ),
    )


def execute(settings: BlenderSettings, code: str) -> dict[str, Any]:
    ensure_loopback_tunnel(settings)
    request = json.dumps({"type": "execute", "code": code, "strict_json": True}, separators=(",", ":")).encode("utf-8") + b"\0"
    with _REQUEST_LOCK, socket.create_connection(("127.0.0.1", settings.local_port), timeout=15.0) as connection:
        connection.settimeout(90.0)
        connection.sendall(request)
        response = _read_message(connection)
    try:
        parsed = json.loads(response)
    except json.JSONDecodeError as error:
        raise BlenderConfigurationError(f"Official Blender MCP returned invalid JSON: {response[:500]}") from error
    if parsed.get("status") != "ok":
        raise BlenderConfigurationError(f"Blender operation failed: {parsed}")
    result = parsed.get("result")
    if not isinstance(result, dict):
        raise BlenderConfigurationError("Blender operation did not return an object result.")
    return result


def _read_message(connection: socket.socket) -> str:
    data = bytearray()
    while len(data) <= 2_000_000:
        block = connection.recv(65_536)
        if not block:
            raise BlenderConfigurationError("Official Blender MCP closed the response stream unexpectedly.")
        delimiter = block.find(b"\0")
        if delimiter >= 0:
            data.extend(block[:delimiter])
            return data.decode("utf-8")
        data.extend(block)
    raise BlenderConfigurationError("Official Blender MCP response exceeded the 2 MiB controlled-operation limit.")
