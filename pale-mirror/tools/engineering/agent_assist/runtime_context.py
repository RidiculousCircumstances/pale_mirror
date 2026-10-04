#!/usr/bin/env python3
"""Bounded read-only runtime-evidence index and MCP facade for Pale Mirror."""
from __future__ import annotations

import argparse
from collections import deque
import hashlib
import json
import os
import re
import sqlite3
import stat
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, Iterable, Iterator

from verification_selector import SelectionError, select as select_verification

SCHEMA_VERSION = 1
INDEX_FORMAT_VERSION = 2
SERVER_NAME = "pale-mirror-runtime-context"
SERVER_VERSION = "1.0.0"
PROTOCOL_VERSION = "2024-11-05"
ALLOWED_SUFFIXES = {".json", ".jsonl", ".ndjson", ".log", ".txt"}
MAX_ROOTS = 8
MAX_FILES = 4096
MAX_CANDIDATES = 50_000
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024
MAX_RECORDS = 300_000
MAX_RECORDS_PER_FILE = 20_000
MAX_LINE_BYTES = 256 * 1024
MAX_PAYLOAD_CHARS = 4_000
MAX_QUERY_CHARS = 512
MAX_RESULTS = 40
MAX_DB_BYTES = 768 * 1024 * 1024

FIELD_KEYS = {
    "timestamp": ("timestamp", "time", "at", "recordedAt", "recorded_at"),
    "trace_id": ("traceId", "trace_id", "correlation", "correlationId", "correlation_id", "causeChainId", "cause_chain_id"),
    "subject_id": ("subjectId", "subject_id", "siteId", "site_id", "operationId", "operation_id", "intentId", "intent_id"),
    "actor_id": ("actorId", "actor_id", "residentId", "resident_id", "entityUuid", "entity_uuid", "uuid"),
    "run_id": ("runId", "run_id", "attemptId", "attempt_id", "worldId", "world_id"),
    "reason_code": ("reasonCode", "reason_code", "reason", "failureCode", "failure_code"),
    "category": ("category", "classification", "kind", "type", "eventType", "event_type"),
}
SECRET_PATTERNS = (
    re.compile(r"(?i)(authorization\s*[:=]\s*bearer\s+)[^\s,;]+"),
    re.compile(r"(?i)((?:token|password|secret|api[_-]?key)\s*[:=]\s*)[^\s,;]+"),
    re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b"),
)


class RuntimeContextError(RuntimeError):
    pass


def _redact(value: str) -> str:
    result = value
    for pattern in SECRET_PATTERNS:
        if pattern.groups:
            result = pattern.sub(r"\1[REDACTED]", result)
        else:
            result = pattern.sub("[REDACTED]", result)
    return result[:MAX_PAYLOAD_CHARS]


def _private_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    metadata = path.lstat()
    if path.is_symlink() or not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != os.geteuid():
        raise RuntimeContextError(f"unsafe private state directory: {path}")
    os.chmod(path, 0o700)


def _fixed_directory(raw: str | Path, label: str) -> Path:
    path = Path(raw).expanduser().resolve(strict=True)
    metadata = path.lstat()
    if path.is_symlink() or not stat.S_ISDIR(metadata.st_mode):
        raise RuntimeContextError(f"{label} must be a real directory")
    return path


def _flatten(value: Any, *, depth: int = 0, result: dict[str, str] | None = None) -> dict[str, str]:
    result = {} if result is None else result
    if depth > 6 or len(result) > 512:
        return result
    if isinstance(value, dict):
        for key, item in value.items():
            if not isinstance(key, str):
                continue
            if isinstance(item, (str, int, float, bool)) or item is None:
                result.setdefault(key, "" if item is None else str(item))
            else:
                _flatten(item, depth=depth + 1, result=result)
    elif isinstance(value, list):
        for item in value[:128]:
            _flatten(item, depth=depth + 1, result=result)
    return result


def _field(flat: dict[str, str], name: str) -> str | None:
    for key in FIELD_KEYS[name]:
        value = flat.get(key)
        if value:
            return value[:1024]
    return None


def _text_field(text: str, name: str) -> str | None:
    alternatives = "|".join(re.escape(item) for item in FIELD_KEYS[name])
    match = re.search(rf"(?i)(?:{alternatives})[\"']?\s*[:=]\s*[\"']?([^\s,;\"'}}]+)", text)
    return match.group(1)[:1024] if match else None


def _record(payload: Any, raw: str) -> tuple[str | None, ...]:
    flat = _flatten(payload) if payload is not None else {}
    values = []
    for name in FIELD_KEYS:
        values.append(_field(flat, name) or _text_field(raw, name))
    return (*values, _redact(raw))


def _json_from_line(line: str) -> Any | None:
    candidates = [line]
    brace = line.find("{")
    if brace > 0:
        candidates.append(line[brace:])
    for candidate in candidates:
        try:
            return json.loads(candidate)
        except (json.JSONDecodeError, ValueError):
            pass
    return None


class RuntimeContext:
    def __init__(self, implementation: str | Path, evidence_roots: Iterable[str | Path], state_root: str | Path | None = None):
        self.implementation = _fixed_directory(implementation, "implementation")
        roots = [_fixed_directory(root, "evidence root") for root in evidence_roots]
        if not roots or len(roots) > MAX_ROOTS or len(set(roots)) != len(roots):
            raise RuntimeContextError(f"expected 1..{MAX_ROOTS} distinct evidence roots")
        self.roots = tuple(roots)
        base = Path(state_root).expanduser() if state_root else Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local/state")) / "pale-mirror" / "runtime-context"
        identity = hashlib.sha256((str(self.implementation) + "\0" + "\0".join(map(str, self.roots))).encode()).hexdigest()[:20]
        self.state_root = base / identity
        _private_directory(self.state_root)
        self.database = self.state_root / "runtime-evidence.sqlite3"

    def _files(self) -> tuple[list[tuple[int, Path, str, int, int]], bool, str | None]:
        candidates: list[tuple[int, Path, str, int, int]] = []
        files: list[tuple[int, Path, str, int, int]] = []
        total = 0
        partial = False
        reason = None
        for root_index, root in enumerate(self.roots):
            for path in root.rglob("*"):
                if len(candidates) >= MAX_CANDIDATES:
                    partial, reason = True, "candidate_limit"
                    break
                if path.is_symlink() or not path.is_file() or path.suffix.lower() not in ALLOWED_SUFFIXES:
                    continue
                metadata = path.stat()
                if metadata.st_size > MAX_FILE_BYTES:
                    partial, reason = True, "oversize_file_skipped"
                    continue
                candidates.append((root_index, path, path.relative_to(root).as_posix(), metadata.st_size, metadata.st_mtime_ns))
            if reason == "candidate_limit":
                break

        def priority(item: tuple[int, Path, str, int, int]) -> tuple[int, int, str]:
            _root_index, path, relative, _size, mtime_ns = item
            lowered = relative.lower()
            score = 4 if path.suffix.lower() in {".jsonl", ".ndjson"} else 1
            if any(word in lowered for word in ("incident", "failure", "manifest", "diagnostic", "latest.log", "runner.log")):
                score += 3
            return score, mtime_ns, relative

        for candidate in sorted(candidates, key=priority, reverse=True):
            if len(files) >= MAX_FILES:
                partial, reason = True, reason or "bounded_recent_file_window"
                break
            if total + candidate[3] > MAX_TOTAL_BYTES:
                partial, reason = True, reason or "bounded_recent_byte_window"
                continue
            total += candidate[3]
            files.append(candidate)
        return files, partial, reason

    def _inventory(self) -> tuple[str, list[tuple[int, Path, str, int, int]], bool, str | None]:
        files, partial, reason = self._files()
        digest = hashlib.sha256()
        digest.update(f"index-format:{INDEX_FORMAT_VERSION}\n".encode())
        for root_index, _path, relative, size, mtime_ns in files:
            digest.update(f"{root_index}\0{relative}\0{size}\0{mtime_ns}\n".encode())
        return digest.hexdigest(), files, partial, reason

    def status(self) -> dict[str, Any]:
        fingerprint, files, scan_partial, scan_reason = self._inventory()
        if not self.database.exists():
            state, metadata = "unavailable", {}
        elif self.database.stat().st_size > MAX_DB_BYTES:
            state, metadata = "partial", {"reason": "database_byte_limit"}
        else:
            try:
                with sqlite3.connect(f"file:{self.database}?mode=ro", uri=True) as connection:
                    metadata = dict(connection.execute("SELECT key, value FROM metadata"))
                    record_count = connection.execute("SELECT COUNT(*) FROM records").fetchone()[0]
                state = "ready" if metadata.get("fingerprint") == fingerprint else "stale"
                if metadata.get("partial") == "true" and state == "ready":
                    state = "partial"
                metadata["records"] = record_count
            except (sqlite3.Error, OSError):
                state, metadata = "partial", {"reason": "invalid_database"}
        if scan_partial and state == "ready":
            state = "partial"
        return {
            "schema_version": SCHEMA_VERSION,
            "state": state,
            "scan_partial": scan_partial,
            "scan_reason": scan_reason,
            "implementation": str(self.implementation),
            "evidence_roots": [str(root) for root in self.roots],
            "files": len(files),
            "fingerprint": fingerprint,
            "indexed_fingerprint": metadata.get("fingerprint"),
            "records": int(metadata.get("records", 0)),
            "database_bytes": self.database.stat().st_size if self.database.exists() else 0,
        }

    def _iter_file_records(self, path: Path) -> Iterator[tuple[int, Any | None, str]]:
        first: list[tuple[int, Any | None, str]] = []
        tail: deque[tuple[int, Any | None, str]] = deque(maxlen=MAX_RECORDS_PER_FILE - 2_000)
        with path.open("rb") as source:
            for line_number, encoded in enumerate(source, 1):
                if len(encoded) > MAX_LINE_BYTES:
                    encoded = encoded[:MAX_LINE_BYTES]
                line = encoded.decode("utf-8", errors="replace").rstrip("\r\n")
                if line:
                    item = (line_number, _json_from_line(line), line)
                    if len(first) < 2_000:
                        first.append(item)
                    else:
                        tail.append(item)
        yield from first
        yield from tail

    def index(self) -> dict[str, Any]:
        fingerprint, files, partial, reason = self._inventory()
        fd, temporary_name = tempfile.mkstemp(prefix="runtime-context-", suffix=".sqlite3", dir=self.state_root)
        os.close(fd)
        temporary = Path(temporary_name)
        records = 0
        try:
            with sqlite3.connect(temporary) as connection:
                connection.executescript("""
                    PRAGMA journal_mode=DELETE;
                    PRAGMA synchronous=FULL;
                    CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
                    CREATE TABLE sources (
                        source_id INTEGER PRIMARY KEY,
                        root_index INTEGER NOT NULL,
                        relative_path TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        mtime_ns INTEGER NOT NULL,
                        UNIQUE(root_index, relative_path)
                    );
                    CREATE TABLE records (
                        record_id INTEGER PRIMARY KEY,
                        source_id INTEGER NOT NULL,
                        line_number INTEGER NOT NULL,
                        timestamp TEXT,
                        trace_id TEXT,
                        subject_id TEXT,
                        actor_id TEXT,
                        run_id TEXT,
                        reason_code TEXT,
                        category TEXT,
                        payload TEXT NOT NULL,
                        FOREIGN KEY(source_id) REFERENCES sources(source_id)
                    );
                    CREATE INDEX records_trace ON records(trace_id);
                    CREATE INDEX records_subject ON records(subject_id);
                    CREATE INDEX records_actor ON records(actor_id);
                    CREATE INDEX records_run ON records(run_id);
                    CREATE INDEX records_reason ON records(reason_code);
                """)
                for root_index, path, relative, size, mtime_ns in files:
                    cursor = connection.execute(
                        "INSERT INTO sources(root_index, relative_path, size_bytes, mtime_ns) VALUES (?, ?, ?, ?)",
                        (root_index, relative, size, mtime_ns),
                    )
                    source_id = cursor.lastrowid
                    for line_number, payload, raw in self._iter_file_records(path):
                        if records >= MAX_RECORDS:
                            partial, reason = True, "record_limit"
                            break
                        connection.execute(
                            "INSERT INTO records(source_id, line_number, timestamp, trace_id, subject_id, actor_id, run_id, reason_code, category, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            (source_id, line_number, *_record(payload, raw)),
                        )
                        records += 1
                    if reason == "record_limit":
                        break
                metadata = {
                    "schema_version": str(SCHEMA_VERSION),
                    "index_format_version": str(INDEX_FORMAT_VERSION),
                    "fingerprint": fingerprint,
                    "indexed_at_unix": str(int(time.time())),
                    "partial": "true" if partial else "false",
                    "partial_reason": reason or "",
                    "records": str(records),
                }
                connection.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata.items())
                connection.commit()
            if temporary.stat().st_size > MAX_DB_BYTES:
                raise RuntimeContextError("runtime context database exceeds byte limit")
            os.chmod(temporary, 0o600)
            os.replace(temporary, self.database)
        finally:
            temporary.unlink(missing_ok=True)
        result = self.status()
        result.update({"indexed": True, "partial": partial, "partial_reason": reason})
        return result

    def query(self, identifier: str, *, field: str = "any", limit: int = 20) -> dict[str, Any]:
        if not identifier or len(identifier) > MAX_QUERY_CHARS or "\x00" in identifier:
            raise RuntimeContextError("identifier is invalid")
        if field not in {"any", "trace", "subject", "actor", "run", "reason"}:
            raise RuntimeContextError("query field is invalid")
        limit = max(1, min(int(limit), MAX_RESULTS))
        status = self.status()
        if status["state"] == "unavailable":
            return {**status, "matches": [], "query": identifier}
        columns = {
            "trace": "r.trace_id",
            "subject": "r.subject_id",
            "actor": "r.actor_id",
            "run": "r.run_id",
            "reason": "r.reason_code",
        }
        if field == "any":
            escaped = identifier.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            predicate = " OR ".join(f"{column} = ?" for column in columns.values()) + " OR r.payload LIKE ? ESCAPE '\\' OR s.relative_path LIKE ? ESCAPE '\\'"
            parameters: list[Any] = [identifier] * len(columns) + [f"%{escaped}%", f"%{escaped}%", limit]
        else:
            predicate = f"{columns[field]} = ?"
            parameters = [identifier, limit]
        sql = f"""
            SELECT r.record_id, s.root_index, s.relative_path, r.line_number, r.timestamp,
                   r.trace_id, r.subject_id, r.actor_id, r.run_id, r.reason_code, r.category, r.payload
              FROM records r JOIN sources s ON s.source_id = r.source_id
             WHERE {predicate}
             ORDER BY s.mtime_ns DESC, r.line_number DESC, r.record_id DESC LIMIT ?
        """
        try:
            with sqlite3.connect(f"file:{self.database}?mode=ro", uri=True) as connection:
                rows = connection.execute(sql, parameters).fetchall()
        except sqlite3.Error as exc:
            raise RuntimeContextError(f"runtime context query failed: {exc}") from exc
        keys = ("record_id", "root_index", "source", "line", "timestamp", "trace_id", "subject_id", "actor_id", "run_id", "reason_code", "category", "payload")
        return {**status, "query": identifier, "field": field, "matches": [dict(zip(keys, row)) for row in reversed(rows)]}

    def why(self, subject: str, *, limit: int = 20) -> dict[str, Any]:
        result = self.query(subject, field="any", limit=limit)
        matches = result["matches"]
        explanation = {
            "state": "not_found" if not matches else "trace_incomplete" if result["state"] != "ready" else "found",
            "last_reason": next((item["reason_code"] for item in reversed(matches) if item["reason_code"]), None),
            "last_category": next((item["category"] for item in reversed(matches) if item["category"]), None),
            "events": len(matches),
        }
        return {**result, "why": explanation}


def _jsonrpc_result(identifier: Any, result: Any) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": identifier, "result": result}


def _jsonrpc_error(identifier: Any, code: int, message: str) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": identifier, "error": {"code": code, "message": message}}


def serve_mcp(context: RuntimeContext) -> int:
    tools = [
        {"name": "pm_runtime_status", "description": "Report runtime evidence index roots, freshness and bounds.", "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False}},
        {"name": "pm_runtime_index", "description": "Refresh the bounded local read-only evidence index.", "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False}},
        {"name": "pm_runtime_timeline", "description": "Find a bounded causal timeline by exact trace, subject, actor, run or reason identifier.", "inputSchema": {"type": "object", "required": ["identifier"], "properties": {"identifier": {"type": "string", "maxLength": MAX_QUERY_CHARS}, "field": {"enum": ["any", "trace", "subject", "actor", "run", "reason"]}, "limit": {"type": "integer", "minimum": 1, "maximum": MAX_RESULTS}}, "additionalProperties": False}},
        {"name": "pm_runtime_why", "description": "Return the latest bounded evidence and reason available for one subject.", "inputSchema": {"type": "object", "required": ["subject"], "properties": {"subject": {"type": "string", "maxLength": MAX_QUERY_CHARS}, "limit": {"type": "integer", "minimum": 1, "maximum": MAX_RESULTS}}, "additionalProperties": False}},
        {"name": "pm_verify_change", "description": "Build but never execute the smallest declarative verification plan for changed repository paths.", "inputSchema": {"type": "object", "required": ["paths"], "properties": {"paths": {"type": "array", "minItems": 1, "maxItems": 256, "items": {"type": "string", "maxLength": 512}}, "milestone": {"type": "boolean"}}, "additionalProperties": False}},
    ]
    for line in sys.stdin:
        identifier = None
        try:
            request = json.loads(line)
            identifier = request.get("id")
            method = request.get("method")
            if method == "initialize":
                response = _jsonrpc_result(identifier, {"protocolVersion": PROTOCOL_VERSION, "capabilities": {"tools": {}}, "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION}})
            elif method == "tools/list":
                response = _jsonrpc_result(identifier, {"tools": tools})
            elif method == "tools/call":
                params = request.get("params") or {}
                name = params.get("name")
                arguments = params.get("arguments") or {}
                if name == "pm_runtime_status":
                    result = context.status()
                elif name == "pm_runtime_index":
                    result = context.index()
                elif name == "pm_runtime_timeline":
                    result = context.query(arguments.get("identifier", ""), field=arguments.get("field", "any"), limit=arguments.get("limit", 20))
                elif name == "pm_runtime_why":
                    result = context.why(arguments.get("subject", ""), limit=arguments.get("limit", 20))
                elif name == "pm_verify_change":
                    result = select_verification(arguments.get("paths", []),
                                                 milestone=bool(arguments.get("milestone", False)),
                                                 root=context.implementation)
                else:
                    raise RuntimeContextError("unknown tool")
                content = json.dumps(result, sort_keys=True)
                response = _jsonrpc_result(identifier, {"content": [{"type": "text", "text": content}], "isError": False})
            elif method and method.startswith("notifications/"):
                continue
            else:
                response = _jsonrpc_error(identifier, -32601, "method not found")
        except (RuntimeContextError, SelectionError, ValueError, TypeError, json.JSONDecodeError) as exc:
            response = _jsonrpc_error(identifier, -32602, str(exc))
        sys.stdout.write(json.dumps(response, separators=(",", ":")) + "\n")
        sys.stdout.flush()
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--implementation", default=os.environ.get("PM_IMPLEMENTATION_ROOT", os.getcwd()))
    parser.add_argument("--evidence-root", action="append", dest="evidence_roots")
    parser.add_argument("--state-root", default=os.environ.get("PM_RUNTIME_CONTEXT_STATE"))
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status")
    commands.add_parser("index")
    query = commands.add_parser("timeline")
    query.add_argument("identifier")
    query.add_argument("--field", choices=("any", "trace", "subject", "actor", "run", "reason"), default="any")
    query.add_argument("--limit", type=int, default=20)
    why = commands.add_parser("why")
    why.add_argument("subject")
    why.add_argument("--limit", type=int, default=20)
    commands.add_parser("mcp")
    return parser


def main() -> int:
    arguments = build_parser().parse_args()
    roots = arguments.evidence_roots or [str(Path(arguments.implementation) / "build")]
    try:
        context = RuntimeContext(arguments.implementation, roots, arguments.state_root)
        if arguments.command == "status":
            result = context.status()
        elif arguments.command == "index":
            result = context.index()
        elif arguments.command == "timeline":
            result = context.query(arguments.identifier, field=arguments.field, limit=arguments.limit)
        elif arguments.command == "why":
            result = context.why(arguments.subject, limit=arguments.limit)
        else:
            return serve_mcp(context)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (RuntimeContextError, SelectionError) as exc:
        print(json.dumps({"error": str(exc), "state": "invalid"}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
