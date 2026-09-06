#!/usr/bin/env python3
"""Focused checked-in regressions for incoming Packwiz snapshot validation."""

from __future__ import annotations

import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest


MODULE_PATH = Path(__file__).with_name('validate-pack-payload-index.py')
SPEC = importlib.util.spec_from_file_location('validate_pack_payload_index', MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class PackPayloadIndexTest(unittest.TestCase):
    def test_synchronized_snapshot_is_repeatable(self) -> None:
        with fixture() as root:
            validate(root)
            validate(root)

    def test_stale_payload_hash_addition_and_removal_fail_before_refresh(self) -> None:
        with fixture() as root:
            index = root / 'index.toml'
            index.write_text(index.read_text(encoding='utf-8').replace(hash_of(root / 'scripts/install-client.sh'), '0' * 64, 1), encoding='utf-8')
            refresh_pack_hash(root)
            with self.assertRaisesRegex(SystemExit, 'hash does not match incoming payload'):
                validate(root)
        with fixture() as root:
            (root / 'payload-added.txt').write_text('new payload\n', encoding='utf-8')
            with self.assertRaisesRegex(SystemExit, 'omits incoming payload'):
                validate(root)
        with fixture() as root:
            (root / 'extra.txt').unlink()
            with self.assertRaisesRegex(SystemExit, 'references missing payload'):
                validate(root)

    def test_pack_hash_and_index_structure_fail_closed(self) -> None:
        with fixture() as root:
            (root / 'pack.toml').write_text('[index]\nfile = "index.toml"\nhash-format = "sha256"\nhash = "' + 'f' * 64 + '"\n', encoding='utf-8')
            with self.assertRaisesRegex(SystemExit, 'does not match incoming index'):
                validate(root)
        with fixture() as root:
            (root / 'index.toml').write_text('[[files]]\nfile = "scripts/install-client.sh"\n', encoding='utf-8')
            refresh_pack_hash(root)
            with self.assertRaisesRegex(SystemExit, 'malformed file entry'):
                validate(root)
        for bad_path, expected in [
            ('./.github/workflows/build.yml', 'non-canonical'),
            ('pale-mirror/../pale-mirror/build.gradle', 'non-canonical'),
            ('.github/workflows/build.yml', 'repository-only'),
            ('nested/forbidden.jar', 'repository-only'),
        ]:
            with self.subTest(bad_path=bad_path), fixture() as root:
                append(root, bad_path, 'bad\n')
                refresh_pack_hash(root)
                with self.assertRaisesRegex(SystemExit, expected):
                    validate(root)
        with fixture() as root:
            duplicate = '[[files]]\nfile = "scripts/install-client.sh"\nhash = "' + hash_of(root / 'scripts/install-client.sh') + '"\n'
            (root / 'index.toml').write_text((root / 'index.toml').read_text(encoding='utf-8') + duplicate, encoding='utf-8')
            refresh_pack_hash(root)
            with self.assertRaisesRegex(SystemExit, 'duplicate'):
                validate(root)


def fixture():
    directory = tempfile.TemporaryDirectory(prefix='pack-index-')
    root = Path(directory.name)
    for relative in VALIDATOR.REQUIRED_PAYLOADS + ('extra.txt',):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f'{relative}\n', encoding='utf-8')
    write_index(root)
    refresh_pack_hash(root)
    class Context:
        def __enter__(self): return root
        def __exit__(self, *_): directory.cleanup()
    return Context()


def write_index(root: Path) -> None:
    entries = []
    for path in sorted(VALIDATOR.REQUIRED_PAYLOADS + ('extra.txt',)):
        entries.append(f'[[files]]\nfile = "{path}"\nhash = "{hash_of(root / path)}"\n')
    (root / 'index.toml').write_text(''.join(entries), encoding='utf-8')


def append(root: Path, path: str, content: str) -> None:
    (root / 'index.toml').write_text((root / 'index.toml').read_text(encoding='utf-8')
        + f'[[files]]\nfile = "{path}"\nhash = "{hashlib.sha256(content.encode()).hexdigest()}"\n', encoding='utf-8')


def refresh_pack_hash(root: Path) -> None:
    digest = hash_of(root / 'index.toml')
    (root / 'pack.toml').write_text(f'[index]\nfile = "index.toml"\nhash-format = "sha256"\nhash = "{digest}"\n', encoding='utf-8')


def validate(root: Path) -> None:
    VALIDATOR.validate_snapshot(root, root / 'index.toml', root / 'pack.toml')


def hash_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == '__main__':
    unittest.main()
