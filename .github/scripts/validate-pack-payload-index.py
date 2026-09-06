#!/usr/bin/env python3
"""Reject repository-only inputs from the Packwiz payload index."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import tomllib


EXCLUDED_PREFIXES = ('.assembly-', '.git/', '.github/', '.work/', 'build/', 'crash-reports/', 'hosted/', 'logs/', 'pale-mirror/', 'run/')
EXCLUDED_PATHS = ('AGENTS.md', 'perf.data')
PACK_METADATA = ('pack.toml', 'index.toml', '.packwizignore', '.gitignore', '.gitattributes')
REQUIRED_PAYLOADS = (
    'config/structurify.json',
    'datapacks/pale-mirror-graybox/pack.mcmeta',
    'datapacks/far-frontier-spore-zones/data/spore/worldgen/structure_set/biomass_tower.json',
    'datapacks/idas-optional-integration-quarantine/data/idas/worldgen/structure_set/idas_common.json',
    'datapacks/integrated-villages-optional-integration-quarantine/data/integrated_villages/worldgen/template_pool/airship_village/irons_priest.json',
    'scripts/install-client.sh',
)
SHA256 = re.compile(r'^[0-9a-f]{64}$')


def validate_snapshot(root: Path, index_path: Path, pack_path: Path) -> None:
    try:
        index = tomllib.loads(index_path.read_text(encoding='utf-8'))
    except (OSError, tomllib.TOMLDecodeError) as error:
        raise SystemExit(f'pack payload index is unreadable: {error}') from error
    files = index.get('files')
    if not isinstance(files, list):
        raise SystemExit('pack payload index has no files array')
    paths: list[str] = []
    entries: list[tuple[dict, str]] = []
    for entry in files:
        if not isinstance(entry, dict) or not isinstance(entry.get('file'), str) or not SHA256.fullmatch(entry.get('hash', '')):
            raise SystemExit('pack payload index has a malformed file entry')
        path = canonical_payload_path(entry['file'])
        paths.append(path)
        entries.append((entry, path))
    if len(paths) != len(set(paths)):
        raise SystemExit('pack payload index has duplicate file entries')
    rejected = sorted(path for path in paths if path in EXCLUDED_PATHS or path.startswith(EXCLUDED_PREFIXES)
                      or (path.startswith('neoforge-') and path.endswith('-installer.jar.log')) or path.endswith('.jar'))
    if rejected:
        raise SystemExit(f'pack payload index includes repository-only input: {", ".join(rejected)}')
    for entry, path in entries:
        actual = root.joinpath(*path.split('/'))
        if not actual.is_file():
            raise SystemExit(f'pack payload index references missing payload: {path}')
        if hashlib.sha256(actual.read_bytes()).hexdigest() != entry['hash']:
            raise SystemExit(f'pack payload index hash does not match incoming payload: {path}')
    unindexed = sorted(payload_paths(root) - set(paths))
    if unindexed:
        raise SystemExit(f'pack payload index omits incoming payload: {", ".join(unindexed)}')
    missing = [path for path in REQUIRED_PAYLOADS if path not in paths]
    if missing:
        raise SystemExit(f'pack payload index omits required pack payload: {", ".join(missing)}')
    try:
        pack = tomllib.loads(pack_path.read_text(encoding='utf-8'))
    except (OSError, tomllib.TOMLDecodeError) as error:
        raise SystemExit(f'pack metadata is unreadable: {error}') from error
    declared = pack.get('index')
    if not isinstance(declared, dict) or declared.get('file') != 'index.toml' or declared.get('hash-format') != 'sha256' or not SHA256.fullmatch(declared.get('hash', '')):
        raise SystemExit('pack metadata has a malformed index declaration')
    actual_hash = hashlib.sha256(index_path.read_bytes()).hexdigest()
    if declared['hash'] != actual_hash:
        raise SystemExit('pack metadata index hash does not match incoming index.toml')


def canonical_payload_path(value: str) -> str:
    if not value or value.startswith('/') or '\\' in value:
        raise SystemExit(f'pack payload index has a non-canonical file path: {value!r}')
    parts = value.split('/')
    if any(not part or part in ('.', '..') for part in parts):
        raise SystemExit(f'pack payload index has a non-canonical file path: {value!r}')
    return value


def payload_paths(root: Path) -> set[str]:
    paths: set[str] = set()
    for candidate in root.rglob('*'):
        if not candidate.is_file():
            continue
        relative = candidate.relative_to(root).as_posix()
        if relative in PACK_METADATA or is_excluded(relative):
            continue
        paths.add(relative)
    return paths


def is_excluded(path: str) -> bool:
    return (path in EXCLUDED_PATHS or path.startswith(EXCLUDED_PREFIXES)
            or path.startswith('world') or (path.startswith('neoforge-') and path.endswith('-installer.jar.log'))
            or path.endswith('.jar'))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--index', type=Path)
    parser.add_argument('--pack', type=Path)
    arguments = parser.parse_args()
    root = arguments.root.resolve()
    index_path = (arguments.index if arguments.index is not None else root / 'index.toml').resolve()
    pack_path = (arguments.pack if arguments.pack is not None else root / 'pack.toml').resolve()
    validate_snapshot(root, index_path, pack_path)
    print(f'pack payload index passed: {len(tomllib.loads(index_path.read_text(encoding="utf-8"))["files"])} files, index sha256={hashlib.sha256(index_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
