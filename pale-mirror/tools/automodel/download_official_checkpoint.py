#!/usr/bin/env python3
"""Download one registered official Automodel checkpoint to a private host.

The source is intentionally a tiny fixed allowlist, rather than a generic
URL/command wrapper. It copies no weight into the repository and leaves the
hash/provenance record to ``checkpoint_receipt.py`` after a completed download.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
from typing import Any

from checkpoint_receipt import OFFICIAL_CHECKPOINTS


HTTP_RANGE_CHUNK_BYTES = 64 * 1024 * 1024


def _download_hub(record: dict[str, str], destination: Path) -> Path:
    """Use Hugging Face's native client for a registered file."""

    try:
        from huggingface_hub import hf_hub_download
    except ImportError as exc:
        raise RuntimeError("huggingface_hub is required for the local official-checkpoint download") from exc
    downloaded = Path(
        hf_hub_download(
            repo_id=record["repository"],
            filename=record["filename"],
            local_dir=destination.parent,
        )
    ).resolve()
    if downloaded != destination.resolve() or not downloaded.is_file() or downloaded.stat().st_size <= 0:
        raise RuntimeError("official checkpoint download did not produce the expected non-empty local file")
    return downloaded


def _http_resume_partial(local_dir: Path, filename: str) -> Path:
    """Return this transport's own resumable file, never a Hub/Xet partial.

    Hub/Xet backends may create sparse or chunk-addressed ``.incomplete``
    files whose logical length is not a verified contiguous HTTP prefix.  An
    HTTP Range request may resume only a file written by this exact streaming
    implementation.  The stale Hub partial is deliberately preserved for
    diagnosis and never trusted as input bytes.
    """

    return local_dir / f".{filename}.pale_mirror_http.incomplete"


def _http_resume_state(partial: Path) -> Path:
    return partial.with_suffix(partial.suffix + ".json")


def _header_lfs_sha(response: Any) -> str:
    """Require the official LFS SHA advertised by Hugging Face's redirect."""

    for candidate in (*response.history, response):
        for name in ("x-linked-etag", "etag"):
            value = candidate.headers.get(name, "").strip().strip('"')
            if re.fullmatch(r"[0-9a-f]{64}", value):
                return value
    raise RuntimeError("official download response did not expose a 64-character LFS SHA-256")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _download_http_stream(record: dict[str, str], destination: Path) -> Path:
    """Resume one official Hugging Face LFS blob with an authenticated Range GET.

    This is only a fallback for a stalled native Hub/Xet backend.  The URL is
    constructed from the existing fixed allowlist, the bearer token is read
    locally by ``huggingface_hub`` and never printed, and a final LFS hash is
    required before the file becomes the destination.
    """

    try:
        import requests
        from huggingface_hub import get_token, hf_hub_url
    except ImportError as exc:
        raise RuntimeError("requests and huggingface_hub are required for the HTTP streaming fallback") from exc
    token = get_token()
    if not token:
        raise RuntimeError("HTTP streaming fallback requires a local Hugging Face read token")
    partial = _http_resume_partial(destination.parent, record["filename"])
    state_path = _http_resume_state(partial)
    existing_bytes = partial.stat().st_size if partial.is_file() else 0
    state: dict[str, Any] | None = None
    if existing_bytes:
        try:
            state = json.loads(state_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise RuntimeError("HTTP resume partial lacks a readable Pale Mirror resume state") from exc
        if (
            state.get("repository") != record["repository"]
            or state.get("filename") != record["filename"]
            or not isinstance(state.get("lfs_sha256"), str)
            or not isinstance(state.get("total_bytes"), int)
        ):
            raise RuntimeError("HTTP resume state does not identify this registered official checkpoint")
    url = hf_hub_url(record["repository"], record["filename"])
    partial.parent.mkdir(parents=True, exist_ok=True)
    expected_sha = state["lfs_sha256"] if state else None
    total_bytes = state["total_bytes"] if state else None
    while total_bytes is None or existing_bytes < total_bytes:
        range_end = existing_bytes + HTTP_RANGE_CHUNK_BYTES - 1
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept-Encoding": "identity",
            "Range": f"bytes={existing_bytes}-{range_end}",
        }
        with requests.get(url, headers=headers, stream=True, timeout=(30, 120), allow_redirects=True) as response:
            if response.status_code != 206:
                raise RuntimeError(f"official HTTP server refused bounded Range request with status {response.status_code}")
            response.raise_for_status()
            content_range = response.headers.get("content-range", "")
            matched_range = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+)", content_range)
            if matched_range is None:
                raise RuntimeError("official HTTP response omitted a valid Content-Range")
            range_start, response_end, response_total = (int(value) for value in matched_range.groups())
            if range_start != existing_bytes or response_end < range_start:
                raise RuntimeError("official HTTP Range response does not match the requested contiguous offset")
            response_sha = _header_lfs_sha(response)
            if expected_sha is not None and response_sha != expected_sha:
                raise RuntimeError("official HTTP LFS SHA changed while resuming; refusing mixed checkpoint bytes")
            if total_bytes is not None and response_total != total_bytes:
                raise RuntimeError("official HTTP total byte size changed while resuming")
            expected_sha = response_sha
            total_bytes = response_total
            state_path.write_text(
                json.dumps(
                    {
                        "repository": record["repository"],
                        "filename": record["filename"],
                        "lfs_sha256": expected_sha,
                        "total_bytes": total_bytes,
                        "range_chunk_bytes": HTTP_RANGE_CHUNK_BYTES,
                    },
                    indent=2,
                    sort_keys=True,
                )
                + "\n",
                encoding="utf-8",
            )
            expected_length = response_end - range_start + 1
            written = 0
            with partial.open("ab") as target:
                for chunk in response.iter_content(chunk_size=8 * 1024 * 1024):
                    if chunk:
                        target.write(chunk)
                        written += len(chunk)
                target.flush()
            if written != expected_length:
                raise RuntimeError(f"official HTTP Range body length mismatch: expected {expected_length}, got {written}")
            existing_bytes += written
    if expected_sha is None or total_bytes is None or existing_bytes != total_bytes:
        raise RuntimeError("official HTTP checkpoint stream did not finish at its advertised byte size")
    actual_sha = _sha256(partial)
    if actual_sha != expected_sha:
        raise RuntimeError(f"official HTTP download SHA-256 mismatch: expected {expected_sha}, got {actual_sha}")
    partial.replace(destination)
    state_path.unlink()
    return destination


def download(model_id: str, local_dir: Path, *, transport: str = "hub") -> Path:
    if model_id not in OFFICIAL_CHECKPOINTS:
        raise ValueError(f"no official checkpoint contract is registered for {model_id}")
    record = OFFICIAL_CHECKPOINTS[model_id]
    local_dir = local_dir.resolve()
    local_dir.mkdir(parents=True, exist_ok=True)
    destination = local_dir / record["filename"]
    if destination.is_file():
        return destination
    if transport == "hub":
        return _download_hub(record, destination)
    if transport == "http_stream":
        return _download_http_stream(record, destination)
    raise ValueError(f"unsupported official-checkpoint transport: {transport}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, choices=sorted(OFFICIAL_CHECKPOINTS))
    parser.add_argument("--local-dir", required=True, type=Path)
    parser.add_argument("--transport", choices=("hub", "http_stream"), default="hub")
    arguments = parser.parse_args()
    result = download(arguments.model, arguments.local_dir, transport=arguments.transport)
    print(json.dumps({"model": arguments.model, "checkpoint": str(result), "status": "downloaded_or_present"}, sort_keys=True))


if __name__ == "__main__":
    main()
