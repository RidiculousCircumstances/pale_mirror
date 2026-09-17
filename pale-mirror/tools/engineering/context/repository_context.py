#!/usr/bin/env python3
"""Read-only, repository-confined context and Codebase Memory facade for Pale Mirror.

The graph is advisory navigation only.  Canonical governance is read from the
separate governance checkout on every request; code is read only from the one
explicit implementation checkout below.  No operation accepts an arbitrary
repository root, writes source, starts a watcher, or contacts a network service.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import select
import shutil
import stat
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

import yaml

IMPLEMENTATION = Path("/home/rd/proj/pm-f06r3-human-ingress-repair/pale-mirror").resolve()
GOVERNANCE = Path("/home/rd/proj/pm-governance/pale-mirror").resolve()
DATA_ROOT = Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local/share"))) / "pale-mirror-tools"
STATE_ROOT = Path(os.environ.get("XDG_STATE_HOME", str(Path.home() / ".local/state"))) / "pale-mirror" / "repository-context"
RELEASE = "v0.8.1"
BINARY_SHA256 = "48e1f5b086dc2dff4a320085e400464630583e1d8eaaf143568041ca6d992cd9"
ASSET_SHA256 = "6ab87a6c05d049dde57700803ca0ab4199fcf25973a0606618af0fcee73f5abd"
SOURCE_COMMIT = "f0c9be19c5d74b84f418d807bfdce7b5d6a261ff"
VENDOR = "DeusData/codebase-memory-mcp"
PROTOCOL = "2024-11-05"
SERVER_NAME = "codebase-memory-mcp"
SERVER_VERSION = "0.10.0"
MAX_OUTPUT_BYTES = 180_000
MAX_VENDOR_MESSAGE_BYTES = 1_000_000
MAX_QUERY_CHARS = 512
MAX_RESULTS = 24
TIMEOUT_SECONDS = 90
READ_ONLY_TOOLS = ("pm_context_status", "pm_context_index", "pm_context_task", "pm_context_path", "pm_context_change", "pm_context_trace", "pm_context_impact", "pm_context_search")
CROSS_LINKS = Path(__file__).with_name("cross_links.yml")


class ContextError(RuntimeError):
    pass


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1_048_576), b""):
            value.update(block)
    return value.hexdigest()


def private_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    metadata = path.lstat()
    if path.is_symlink() or not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != os.geteuid():
        raise ContextError("private context state is unsafe")
    os.chmod(path, 0o700)


def vendor_directory() -> Path:
    return DATA_ROOT / f"codebase-memory-mcp-{RELEASE}"


def binary() -> Path:
    return vendor_directory() / "codebase-memory-mcp"


def manifest() -> Path:
    return vendor_directory() / "vendor-manifest.json"


def expected_manifest() -> dict[str, Any]:
    return {"schema_version": 1, "vendor": VENDOR, "source_repository": "https://github.com/DeusData/codebase-memory-mcp.git",
            "source_commit": SOURCE_COMMIT, "release_tag": RELEASE, "binary_version": "0.8.1",
            "asset": "codebase-memory-mcp-linux-amd64-portable.tar.gz", "asset_sha256": ASSET_SHA256,
            "binary_sha256": BINARY_SHA256}


def validate_installation() -> None:
    directory = vendor_directory()
    if directory.is_symlink() or binary().is_symlink() or manifest().is_symlink() or not binary().is_file() or not os.access(binary(), os.X_OK):
        raise ContextError("pinned Codebase Memory installation is unavailable")
    try:
        installed = json.loads(manifest().read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ContextError("pinned Codebase Memory manifest is invalid") from exc
    if installed != expected_manifest() or digest(binary()) != BINARY_SHA256:
        raise ContextError("pinned Codebase Memory installation failed attestation")


def project_id() -> str:
    raw = str(IMPLEMENTATION).replace("\\", "/")
    compact = "".join(char if char.isascii() and (char.isalnum() or char in "._-") else "-" for char in raw)
    while "--" in compact:
        compact = compact.replace("--", "-")
    return compact.strip("-.") or "root"


def cache_directory() -> Path:
    return STATE_ROOT / f"vendor-{RELEASE}-{BINARY_SHA256[:12]}" / project_id()


def git(arguments: list[str], root: Path) -> str:
    result = subprocess.run(["git", *arguments], cwd=root, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return result.stdout


def git_root(root: Path | None = None) -> Path:
    """Return the actual Git root containing one fixed declared code scope.

    Git status paths are relative to this root, not necessarily to the declared
    implementation directory.  Keeping that distinction explicit prevents a
    nested implementation scope from silently resolving top-level Git paths
    against the wrong directory.
    """
    declared = IMPLEMENTATION if root is None else Path(root)
    if declared.is_symlink() or not declared.is_dir():
        raise ContextError("declared implementation root is unsafe")
    try:
        candidate = Path(git(["rev-parse", "--show-toplevel"], declared).strip())
    except (OSError, ValueError) as exc:
        raise ContextError("declared implementation root has no Git top level") from exc
    if not candidate.is_absolute() or candidate.is_symlink() or not candidate.is_dir():
        raise ContextError("Git top level is unsafe")
    actual = candidate.resolve()
    try:
        declared.resolve().relative_to(actual)
    except ValueError as exc:
        raise ContextError("Git top level does not contain declared implementation root") from exc
    return actual


def identity(root: Path) -> dict[str, str]:
    repository = git_root(root)
    return {"root": str(repository), "head": git(["rev-parse", "HEAD"], repository).strip(),
            "tree": git(["rev-parse", "HEAD^{tree}"], repository).strip()}


def implementation_scope() -> dict[str, str]:
    repository = git_root()
    return {"declared_root": str(IMPLEMENTATION), "git_root": str(repository),
            "relative_to_git_root": IMPLEMENTATION.relative_to(repository).as_posix() or "."}


def safe_repository_path(repository: Path, relative: str) -> Path:
    candidate = Path(relative)
    if candidate.is_absolute() or not relative or ".." in candidate.parts:
        raise ContextError("worktree fingerprint found an unsafe Git path")
    target = repository / candidate
    try:
        target.relative_to(repository)
    except ValueError as exc:
        raise ContextError("worktree fingerprint path escapes Git root") from exc
    probe = repository
    for component in candidate.parts:
        probe /= component
        if probe.is_symlink():
            raise ContextError("worktree fingerprint rejects symbolic links")
    return target


def worktree_fingerprint() -> str:
    # Porcelain status alone says only that an untracked directory exists and that a tracked
    # file is modified; it does not change when bytes inside either change.  Bind readiness to
    # the current dirty content so a structural query cannot silently reuse a graph from an
    # earlier WIP edit.
    repository = git_root()
    result = hashlib.sha256(identity(repository)["head"].encode())
    status = git(["status", "--porcelain=v1", "-z"], repository)
    result.update(status.encode())
    changed = git(["diff", "--name-only", "-z", "HEAD"], repository).split("\0")
    untracked = git(["ls-files", "--others", "--exclude-standard", "-z"], repository).split("\0")
    for relative in sorted({path for path in [*changed, *untracked] if path}):
        path = safe_repository_path(repository, relative)
        result.update(relative.encode() + b"\0")
        if path.is_file(): result.update(digest(path).encode())
        elif not path.exists(): result.update(b"deleted")
        else: raise ContextError("worktree fingerprint found an unsupported entry")
    return result.hexdigest()


def index_state_path() -> Path:
    return cache_directory() / "pale-mirror-index-state.json"


def cache_usage(path: Path) -> dict[str, int | bool]:
    if not path.exists():
        return {"exists": False, "entries": 0, "bytes": 0}
    total = entries = 0
    for item in path.rglob("*"):
        if item.is_symlink():
            raise ContextError("context cache must not contain symlinks")
        entries += 1
        if entries > 512:
            raise ContextError("context cache entry limit exceeded")
        if item.is_file():
            total += item.stat().st_size
            if total > 1_073_741_824:
                raise ContextError("context cache byte limit exceeded")
    return {"exists": True, "entries": entries, "bytes": total}


def state() -> dict[str, Any]:
    current = worktree_fingerprint()
    path = index_state_path()
    recorded: dict[str, Any] | None = None
    if path.exists():
        try:
            recorded = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {"state": "partial", "reason": "invalid_index_receipt", "current_fingerprint": current,
                    "implementation": implementation_scope(), "repository": identity(IMPLEMENTATION), "governance": identity(GOVERNANCE)}
    try:
        validate_installation()
        vendor_error = None
    except ContextError as error:
        vendor_error = str(error)
    readiness = "unavailable" if vendor_error else "ready" if recorded and recorded.get("worktree_fingerprint") == current else "stale" if recorded else "unavailable"
    result = {"state": readiness, "implementation": implementation_scope(), "repository": identity(IMPLEMENTATION), "governance": identity(GOVERNANCE),
            "worktree_fingerprint": current, "indexed_fingerprint": recorded.get("worktree_fingerprint") if recorded else None,
            "cache": {"path": str(cache_directory()), **cache_usage(cache_directory())},
            "vendor": {"release": RELEASE, "binary_sha256": BINARY_SHA256, "protocol": PROTOCOL,
                       "server": f"{SERVER_NAME} {SERVER_VERSION}"}}
    if vendor_error:
        result["reason"] = vendor_error
    return result


class VendorSession:
    def __init__(self) -> None:
        self._initialize_sent = False

    def __enter__(self) -> "VendorSession":
        validate_installation(); private_directory(cache_directory())
        env = {key: os.environ[key] for key in ("HOME", "PATH", "LANG", "LC_ALL") if key in os.environ}
        env.update({"CBM_CACHE_DIR": str(cache_directory()), "CBM_WORKERS": "1", "CBM_SQLITE_MMAP_SIZE": str(64 * 1024 * 1024), "CODEBASE_MEMORY_UI": "false"})
        # The portable vendor initializes its Java/LSP graph arena before it can service the
        # first full repository request.  Two GiB produces a deterministic SIGSEGV on the
        # current Java/Gradle worktree; keep a finite, single-process 8 GiB ceiling instead.
        self.process = subprocess.Popen(["/usr/bin/prlimit", "--as=8589934592", "--cpu=60", str(binary())], cwd=IMPLEMENTATION,
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env)
        initialized = self.initialize_once()
        result = initialized.get("result", {})
        if result.get("protocolVersion") != PROTOCOL or result.get("serverInfo") != {"name": SERVER_NAME, "version": SERVER_VERSION}:
            raise ContextError("Codebase Memory MCP handshake does not match pin")
        self.notify({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}})
        tools = self.request({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}).get("result", {}).get("tools", [])
        self.tools = {item.get("name") for item in tools if isinstance(item, dict)}
        if not {"index_repository", "index_status", "search_graph", "search_code", "trace_path"}.issubset(self.tools):
            raise ContextError("Codebase Memory MCP tool surface is incomplete")
        self.next_id = 3
        return self

    def initialize_once(self) -> dict[str, Any]:
        if self._initialize_sent:
            raise ContextError("Codebase Memory MCP initialize was already sent")
        self._initialize_sent = True
        return self.request({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": PROTOCOL, "capabilities": {}, "clientInfo": {"name": "pale-mirror-context", "version": "1.0.0"}}})

    def request(self, request: dict[str, Any]) -> dict[str, Any]:
        encoded = json.dumps(request, separators=(",", ":"))
        if len(encoded) > 20_000:
            raise ContextError("context request exceeds bound")
        assert self.process.stdin and self.process.stdout
        self.process.stdin.write(encoded + "\n"); self.process.stdin.flush()
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not select.select([self.process.stdout], [], [], remaining)[0]:
                raise ContextError("Codebase Memory MCP timed out")
            line = self.process.stdout.readline(MAX_VENDOR_MESSAGE_BYTES + 1)
            if not line or len(line.encode()) > MAX_VENDOR_MESSAGE_BYTES:
                raise ContextError("Codebase Memory MCP returned invalid response")
            # The pinned binary currently emits one bounded startup log line on stdout before
            # its JSON-RPC initialize reply.  Ignore only non-JSON diagnostics; every actual
            # response still has to correlate to this exact request id.
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if payload.get("id") != request.get("id") or "error" in payload:
                raise ContextError("Codebase Memory MCP rejected graph request")
            return payload

    def notify(self, request: dict[str, Any]) -> None:
        assert self.process.stdin
        self.process.stdin.write(json.dumps(request, separators=(",", ":")) + "\n"); self.process.stdin.flush()

    def call(self, name: str, arguments: dict[str, Any]) -> Any:
        if name not in {"index_repository", "index_status", "search_graph", "search_code", "trace_path"}:
            raise ContextError("graph operation is not in Pale Mirror read-only allowlist")
        response = self.request({"jsonrpc": "2.0", "id": self.next_id, "method": "tools/call", "params": {"name": name, "arguments": arguments}})
        self.next_id += 1
        content = response.get("result", {}).get("content", [])
        if not isinstance(content, list) or len(content) != 1 or not isinstance(content[0], dict) or not isinstance(content[0].get("text"), str):
            raise ContextError("Codebase Memory MCP graph payload is malformed")
        text = content[0]["text"]
        if len(text.encode()) > MAX_OUTPUT_BYTES:
            raise ContextError("graph response exceeds bound")
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return {"text": text[:20_000], "partial": len(text) > 20_000}

    def __exit__(self, *_unused: object) -> None:
        if self.process.poll() is None:
            self.process.terminate()
            try: self.process.wait(timeout=5)
            except subprocess.TimeoutExpired: self.process.kill()


def index() -> dict[str, Any]:
    private_directory(cache_directory())
    with VendorSession() as vendor:
        result = vendor.call("index_repository", {"repo_path": str(IMPLEMENTATION), "mode": "moderate", "persistence": False})
        status = vendor.call("index_status", {"project": project_id()})
    receipt = {"worktree_fingerprint": worktree_fingerprint(), "indexed_at": int(time.time()), "vendor_result": result, "vendor_status": status}
    index_state_path().write_text(json.dumps(receipt, sort_keys=True), encoding="utf-8")
    os.chmod(index_state_path(), 0o600)
    return {"state": "ready", "index": result, "vendor_status": status, **state()}


def architecture() -> dict[str, Any]:
    return yaml.safe_load((GOVERNANCE / "architecture.yml").read_text(encoding="utf-8"))


def cross_links() -> dict[str, Any]:
    try:
        index = yaml.safe_load(CROSS_LINKS.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        raise ContextError("cross-link index is unavailable") from exc
    if not isinstance(index, dict) or index.get("schema_version") != 1 or not isinstance(index.get("contracts"), list) or not isinstance(index.get("active_tasks"), dict):
        raise ContextError("cross-link index has an invalid shape")
    component_names = {component.get("name") for component in architecture().get("components", [])}
    covered = set()
    for contract in index["contracts"]:
        required = {"id", "owner", "paths", "canonical_refs", "critical_flows", "verification_families"}
        if not isinstance(contract, dict) or set(contract) != required or contract["owner"] not in component_names:
            raise ContextError("cross-link contract is invalid")
        if not all(isinstance(contract[field], list) and contract[field] for field in ("paths", "canonical_refs", "verification_families")) or not isinstance(contract["critical_flows"], list):
            raise ContextError("cross-link contract has incomplete routing")
        if not all(isinstance(item, str) and item for field in ("paths", "canonical_refs", "critical_flows", "verification_families") for item in contract[field]):
            raise ContextError("cross-link contract has invalid routing values")
        for path in contract["paths"]:
            if not isinstance(path, str) or Path(path).is_absolute() or ".." in Path(path).parts:
                raise ContextError("cross-link path is unsafe")
        for reference in contract["canonical_refs"]:
            if not isinstance(reference, str) or not (GOVERNANCE / reference).is_file():
                raise ContextError("cross-link canonical reference is unavailable")
        if str(contract["id"]).startswith("component-"): covered.add(contract["owner"])
    if covered != component_names:
        raise ContextError("cross-link index does not cover every architecture component")
    for task in index["active_tasks"].values():
        if not isinstance(task, dict) or set(task) != {"order", "canonical_refs", "contract_ids"}:
            raise ContextError("cross-link active task is invalid")
        if not isinstance(task["order"], str) or not (GOVERNANCE / task["order"]).is_file():
            raise ContextError("cross-link active task order is unavailable")
        if not all(isinstance(reference, str) and (GOVERNANCE / reference).is_file() for reference in task["canonical_refs"]):
            raise ContextError("cross-link active task reference is unavailable")
        if not all(isinstance(contract_id, str) for contract_id in task["contract_ids"]):
            raise ContextError("cross-link active task contract is invalid")
    return index


def checked_path(value: str) -> str:
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts or not value or len(value) > 512:
        raise ContextError("path is outside the selected implementation checkout")
    normalized = candidate.as_posix()
    if not (IMPLEMENTATION / normalized).exists():
        raise ContextError("path is not present in the selected implementation checkout")
    return normalized


def unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def contracts_for_path(path: str, index: dict[str, Any]) -> list[dict[str, Any]]:
    def matches(prefix: str) -> bool:
        normalized = prefix.rstrip("/")
        return path == normalized or path.startswith(normalized + "/")
    return [contract for contract in index["contracts"] if any(matches(prefix) for prefix in contract["paths"])]


def context_for_path(value: str) -> dict[str, Any]:
    path = checked_path(value); index = cross_links(); matched = contracts_for_path(path, index); runtime = state()
    if not matched:
        return {"state": "unmapped", "path": path, "graph_state": runtime["state"], "reason": "no_governed_owner_or_flow_mapping"}
    owners = unique([contract["owner"] for contract in matched])
    return {"state": runtime["state"] if runtime["state"] != "ready" else "ready", "path": path, "owners": owners,
            "contract_ids": [contract["id"] for contract in matched],
            "canonical_refs": unique([reference for contract in matched for reference in contract["canonical_refs"]]),
            "critical_flows": unique([flow for contract in matched for flow in contract["critical_flows"]]),
            "verification_families": unique([family for contract in matched for family in contract["verification_families"]]),
            "graph_state": runtime["state"], "partial": runtime["state"] != "ready"}


def context_for_change(values: list[str]) -> dict[str, Any]:
    if not isinstance(values, list) or not values or len(values) > MAX_RESULTS:
        raise ContextError("change context requires 1..24 repository paths")
    paths = [context_for_path(value) for value in values]
    unmapped = [path["path"] for path in paths if path["state"] == "unmapped"]
    fields = ("owners", "contract_ids", "canonical_refs", "critical_flows", "verification_families")
    summary = {field: unique([item for path in paths if path["state"] != "unmapped" for item in path[field]]) for field in fields}
    graph_states = unique([path["graph_state"] for path in paths])
    return {"state": "unmapped" if len(unmapped) == len(paths) else "partial" if unmapped or graph_states != ["ready"] else "ready",
            "paths": paths, "unmapped_paths": unmapped, "graph_states": graph_states, **summary}


def task_context(query: str) -> dict[str, Any]:
    if not query or len(query) > MAX_QUERY_CHARS:
        raise ContextError("task query is invalid")
    index = cross_links(); task = index["active_tasks"].get("f06r3")
    if not isinstance(task, dict) or not isinstance(task.get("contract_ids"), list):
        raise ContextError("active F0.6R3 cross-link task is unavailable")
    contracts = {contract["id"]: contract for contract in index["contracts"]}
    selected = [contracts.get(contract_id) for contract_id in task["contract_ids"]]
    if any(contract is None for contract in selected): raise ContextError("active F0.6R3 cross-link task references an unknown contract")
    anchors = unique([path for contract in selected for path in contract["paths"]])
    governed = context_for_change(anchors)
    if governed["state"] == "unmapped": raise ContextError("active F0.6R3 task has an unmapped anchor")
    flow_ids = unique([flow for contract in selected for flow in contract["critical_flows"]])
    flows = [flow for flow in architecture().get("critical_flows", []) if flow.get("id") in flow_ids]
    return {"state": governed["state"], "query": query, "active_order": str(GOVERNANCE / task["order"]),
            "contracts": [str(GOVERNANCE / path) for path in task["canonical_refs"]], "critical_flows": flows,
            "anchors": governed["paths"], "all_components": [component["name"] for component in architecture().get("components", [])],
            "governed": governed,
            "advisory": "Verify graph suggestions in these source anchors and canonical documents."}


def bounded_graph(operation: str, value: str | list[str]) -> dict[str, Any]:
    current = state()
    governed = context_for_change(value) if operation == "impact" and isinstance(value, list) else None
    if current["state"] != "ready":
        return {"state": current["state"], "reason": "graph_index_not_current", "context": current,
                **({"governed": governed, "graph": {"state": current["state"], "partial": True}} if governed else {})}
    with VendorSession() as vendor:
        if operation == "trace":
            if not isinstance(value, str) or not value or len(value) > MAX_QUERY_CHARS: raise ContextError("trace symbol is invalid")
            graph = vendor.call("trace_path", {"project": project_id(), "function_name": value, "direction": "both", "depth": 4, "mode": "calls", "include_tests": True})
        elif operation == "impact":
            assert isinstance(value, list)
            paths = [checked_path(path) for path in value[:MAX_RESULTS]]
            # Vendor search treats a pipe literally unless its unexposed regex switch is set.
            # Query each bounded declared path separately instead of silently returning an
            # empty synthetic union; consumers can see which member was partial/no-match.
            graph = {path: vendor.call("search_code", {"project": project_id(), "pattern": Path(path).stem,
                                                        "mode": "files", "limit": MAX_RESULTS}) for path in paths}
            graph = {"results": graph,
                "partial_paths": [path for path, result in graph.items() if result.get("total_results", 0) == 0]}
        else:
            assert isinstance(value, str)
            graph = vendor.call("search_code", {"project": project_id(), "pattern": value[:MAX_QUERY_CHARS], "mode": "compact", "limit": MAX_RESULTS, "context": 2})
    return {"state": "ready", "operation": operation, "graph": graph, "context": current,
            **({"governed": governed} if governed else {})}


def mcp_response(message: dict[str, Any]) -> dict[str, Any] | None:
    method = message.get("method"); request_id = message.get("id")
    if method == "notifications/initialized": return None
    if method == "initialize": return {"jsonrpc": "2.0", "id": request_id, "result": {"protocolVersion": PROTOCOL, "serverInfo": {"name": "pale-mirror-context", "version": "1.0.0"}, "capabilities": {"tools": {}}}}
    if method == "tools/list":
        descriptions = {"pm_context_status": "Current pinned graph and dirty-worktree freshness.", "pm_context_index": "Index only the fixed Pale Mirror implementation checkout.", "pm_context_task": "Bounded active F0.6R3 governance and source-anchor context.", "pm_context_path": "Governed owner/contract/flow routing for one fixed-repository path.", "pm_context_change": "Governed union routing for fixed-repository changed paths.", "pm_context_trace": "Bounded read-only code trace.", "pm_context_impact": "Bounded graph hints joined to governed changed-path consequences.", "pm_context_search": "Bounded read-only code search."}
        return {"jsonrpc": "2.0", "id": request_id, "result": {"tools": [{"name": name, "description": descriptions[name], "inputSchema": {"type": "object", "additionalProperties": False, "properties": ({"query": {"type": "string", "maxLength": MAX_QUERY_CHARS}} if name in {"pm_context_task", "pm_context_search"} else {"path": {"type": "string", "maxLength": 512}} if name == "pm_context_path" else {"symbol": {"type": "string", "maxLength": MAX_QUERY_CHARS}} if name == "pm_context_trace" else {"paths": {"type": "array", "maxItems": MAX_RESULTS, "items": {"type": "string", "maxLength": 512}}} if name in {"pm_context_change", "pm_context_impact"} else {})}} for name in READ_ONLY_TOOLS]}}
    if method != "tools/call" or not isinstance(message.get("params"), dict): raise ContextError("unsupported MCP operation")
    name = message["params"].get("name"); arguments = message["params"].get("arguments", {})
    if not isinstance(arguments, dict) or name not in READ_ONLY_TOOLS: raise ContextError("MCP operation is not in Pale Mirror read-only allowlist")
    if name == "pm_context_status": result = state()
    elif name == "pm_context_index": result = index()
    elif name == "pm_context_task": result = task_context(str(arguments.get("query", "")))
    elif name == "pm_context_path": result = context_for_path(str(arguments.get("path", "")))
    elif name == "pm_context_change": result = context_for_change(arguments.get("paths", []))
    elif name == "pm_context_trace": result = bounded_graph("trace", str(arguments.get("symbol", "")))
    elif name == "pm_context_impact": result = bounded_graph("impact", arguments.get("paths", []))
    else: result = bounded_graph("search", str(arguments.get("query", "")))
    return {"jsonrpc": "2.0", "id": request_id, "result": {"content": [{"type": "text", "text": json.dumps(result, ensure_ascii=False) }]}}


def serve() -> int:
    for line in sys.stdin:
        try:
            if len(line.encode()) > 25_000: raise ContextError("MCP request exceeds bound")
            response = mcp_response(json.loads(line))
            if response is not None: print(json.dumps(response, ensure_ascii=False), flush=True)
        except (ContextError, json.JSONDecodeError) as error:
            print(json.dumps({"jsonrpc": "2.0", "id": None, "error": {"code": -32602, "message": str(error)}}), flush=True)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Pale Mirror local repository context")
    parser.add_argument("command", choices=("status", "index", "task", "path", "change", "trace", "impact", "search", "mcp"))
    parser.add_argument("values", nargs="*")
    args = parser.parse_args()
    try:
        if args.command == "status": result = state()
        elif args.command == "index": result = index()
        elif args.command == "task": result = task_context(" ".join(args.values))
        elif args.command == "path": result = context_for_path(" ".join(args.values))
        elif args.command == "change": result = context_for_change(args.values)
        elif args.command == "trace": result = bounded_graph("trace", " ".join(args.values))
        elif args.command == "impact": result = bounded_graph("impact", args.values)
        elif args.command == "search": result = bounded_graph("search", " ".join(args.values))
        else: return serve()
        print(json.dumps(result, ensure_ascii=False, sort_keys=True)); return 0
    except (ContextError, subprocess.CalledProcessError) as error:
        print(json.dumps({"state": "unavailable", "reason": str(error)}), file=sys.stderr); return 2


if __name__ == "__main__": raise SystemExit(main())
