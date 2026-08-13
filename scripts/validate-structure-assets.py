#!/usr/bin/env python3
"""Validate reachable worldgen structure graphs without starting Minecraft.

Mod JAR resources are overlaid with the managed datapacks, then only roots still
present in active structure sets are traversed. This makes quarantine explicit:
an unreachable broken third-party root is accepted, while a new reachable bad
item stack, missing template or missing jigsaw pool fails installation.
"""

from __future__ import annotations

import argparse
import gzip
import json
import re
import struct
import sys
import zipfile
from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable


class NbtError(ValueError):
    pass


class NbtReader:
    def __init__(self, raw: bytes) -> None:
        if raw[:2] == b"\x1f\x8b":
            raw = gzip.decompress(raw)
        self.raw = memoryview(raw)
        self.offset = 0

    def take(self, size: int) -> bytes:
        end = self.offset + size
        if size < 0 or end > len(self.raw):
            raise NbtError("truncated NBT")
        value = bytes(self.raw[self.offset:end])
        self.offset = end
        return value

    def number(self, fmt: str) -> int | float:
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self) -> str:
        size = int(self.number("H"))
        return self.take(size).decode("utf-8", errors="replace")

    def payload(self, kind: int, depth: int = 0) -> Any:
        if depth > 128:
            raise NbtError("NBT nesting exceeds 128")
        if kind == 1:
            return self.number("b")
        if kind == 2:
            return self.number("h")
        if kind == 3:
            return self.number("i")
        if kind == 4:
            return self.number("q")
        if kind == 5:
            return self.number("f")
        if kind == 6:
            return self.number("d")
        if kind == 7:
            size = int(self.number("i"))
            self.take(size)
            return None
        if kind == 8:
            return self.string()
        if kind == 9:
            child = int(self.number("B"))
            size = int(self.number("i"))
            if size < 0 or size > 50_000_000:
                raise NbtError(f"invalid list size {size} for child tag {child} at byte {self.offset}")
            return [self.payload(child, depth + 1) for _ in range(size)]
        if kind == 10:
            result: dict[str, Any] = {}
            while True:
                child = int(self.number("B"))
                if child == 0:
                    return result
                name = self.string()
                result[name] = self.payload(child, depth + 1)
        if kind in (11, 12):
            size = int(self.number("i"))
            width = 4 if kind == 11 else 8
            self.take(size * width)
            return None
        raise NbtError(f"unknown NBT tag {kind}")

    def root(self) -> Any:
        kind = int(self.number("B"))
        if kind == 0:
            raise NbtError("empty root tag")
        self.string()
        value = self.payload(kind)
        if self.offset != len(self.raw):
            raise NbtError(f"trailing NBT bytes: {len(self.raw) - self.offset}")
        return value


@dataclass(frozen=True)
class Resource:
    source: str
    read: Callable[[], bytes]


def resource_key(name: str, marker: str, suffix: str) -> str | None:
    parts = name.split("/")
    if len(parts) < 4 or parts[0] != "data" or not name.endswith(suffix):
        return None
    prefix = f"data/{parts[1]}/{marker}/"
    if not name.startswith(prefix):
        return None
    path = name[len(prefix): -len(suffix)]
    return f"{parts[1]}:{path}"


def load_zip(path: Path, stores: dict[str, dict[str, Resource]]) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
    for name in names:
        for kind, marker, suffix in (
            ("sets", "worldgen/structure_set", ".json"),
            ("set_tags", "tags/worldgen/structure_set", ".json"),
            ("structures", "worldgen/structure", ".json"),
            ("pools", "worldgen/template_pool", ".json"),
            ("templates", "structure", ".nbt"),
            ("templates", "structures", ".nbt"),
        ):
            key = resource_key(name, marker, suffix)
            if key is not None:
                stores[kind][key] = Resource(
                    f"{path.name}!/{name}",
                    lambda p=path, n=name: read_zip_member(p, n),
                )


def read_zip_member(path: Path, name: str) -> bytes:
    with zipfile.ZipFile(path) as archive:
        return archive.read(name)


def load_directory(root: Path, stores: dict[str, dict[str, Resource]]) -> None:
    data = root / "data"
    if not data.is_dir():
        return
    metadata = root / "pack.mcmeta"
    if metadata.is_file():
        try:
            filters = json.loads(metadata.read_text(encoding="utf-8")).get("filter", {}).get("block", [])
            prefixes = {
                "sets": "worldgen/structure_set/",
                "set_tags": "tags/worldgen/structure_set/",
                "structures": "worldgen/structure/",
                "pools": "worldgen/template_pool/",
                "templates": "structure/",
            }
            for rule in filters:
                if not isinstance(rule, dict):
                    continue
                namespace = re.compile(str(rule.get("namespace", ".*")))
                path_pattern = re.compile(str(rule.get("path", ".*")))
                for kind, resources in stores.items():
                    prefix = prefixes[kind]
                    for key in list(resources):
                        resource_namespace, _, resource_path = key.partition(":")
                        if namespace.fullmatch(resource_namespace) and path_pattern.fullmatch(prefix + resource_path):
                            del resources[key]
        except (OSError, ValueError, re.error) as exception:
            raise ValueError(f"{metadata}: invalid pack filter: {exception}") from exception
    for path in sorted(data.rglob("*")):
        if not path.is_file():
            continue
        name = path.relative_to(root).as_posix()
        for kind, marker, suffix in (
            ("sets", "worldgen/structure_set", ".json"),
            ("set_tags", "tags/worldgen/structure_set", ".json"),
            ("structures", "worldgen/structure", ".json"),
            ("pools", "worldgen/template_pool", ".json"),
            ("templates", "structure", ".nbt"),
            ("templates", "structures", ".nbt"),
        ):
            key = resource_key(name, marker, suffix)
            if key is not None:
                stores[kind][key] = Resource(str(path), path.read_bytes)


def read_json(resource: Resource) -> dict[str, Any]:
    try:
        value = json.loads(resource.read())
    except Exception as exception:
        raise ValueError(f"{resource.source}: invalid JSON: {exception}") from exception
    if not isinstance(value, dict):
        raise ValueError(f"{resource.source}: root must be an object")
    return value


def pool_elements(value: Any) -> Iterable[str]:
    if not isinstance(value, dict):
        return
    element = value.get("element", value)
    if not isinstance(element, dict):
        return
    location = element.get("location")
    if isinstance(location, str):
        yield location
    nested = element.get("elements")
    if isinstance(nested, list):
        for child in nested:
            yield from pool_elements(child)


def inspect_nbt(value: Any, path: str, issues: list[str], pools: set[str]) -> None:
    if isinstance(value, dict):
        identifier = value.get("id")
        item_markers = {"count", "Count", "Slot", "components", "tag"}
        if identifier == "minecraft:air" and item_markers.intersection(value):
            issues.append(f"invalid item stack minecraft:air at {path}")
        pool = value.get("pool")
        if isinstance(pool, str) and ":" in pool and pool != "minecraft:empty":
            pools.add(pool)
        for key, child in value.items():
            inspect_nbt(child, f"{path}.{key}", issues, pools)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            inspect_nbt(child, f"{path}[{index}]", issues, pools)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods-dir", type=Path, required=True)
    parser.add_argument("--datapacks", type=Path, default=Path(__file__).resolve().parent.parent / "datapacks")
    parser.add_argument("--resources", type=Path, action="append", default=[])
    parser.add_argument(
        "--structurify",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "config" / "structurify.json",
        help="Structurify policy whose disabled structure sets are excluded",
    )
    parser.add_argument(
        "--namespace",
        action="append",
        default=[],
        help="active root namespace to audit; defaults to the pinned integration packs",
    )
    parser.add_argument("--all-namespaces", action="store_true")
    arguments = parser.parse_args()
    if not arguments.mods_dir.is_dir():
        parser.error(f"mods directory does not exist: {arguments.mods_dir}")

    stores: dict[str, dict[str, Resource]] = {
        "sets": {}, "set_tags": {}, "structures": {}, "pools": {}, "templates": {}
    }
    for jar in sorted(arguments.mods_dir.glob("*.jar")):
        try:
            load_zip(jar, stores)
        except zipfile.BadZipFile as exception:
            print(f"invalid mod JAR {jar}: {exception}", file=sys.stderr)
            return 1
    if arguments.datapacks.is_dir():
        for pack in sorted(arguments.datapacks.iterdir()):
            if pack.is_dir():
                load_directory(pack, stores)
    for resources in arguments.resources:
        load_directory(resources, stores)

    errors: list[str] = []
    active_roots: set[str] = set()
    namespaces = set(arguments.namespace or ("dungeons_arise", "idas", "integrated_villages"))
    disabled_sets: set[str] = set()
    if arguments.structurify.is_file():
        try:
            structurify = json.loads(arguments.structurify.read_text(encoding="utf-8"))
            disabled_sets = {
                entry["name"] for entry in structurify.get("structure_sets", [])
                if isinstance(entry, dict) and entry.get("is_disabled") is True and isinstance(entry.get("name"), str)
            }
        except (OSError, ValueError) as exception:
            print(f"invalid Structurify policy {arguments.structurify}: {exception}", file=sys.stderr)
            return 1
    for key, resource in stores["set_tags"].items():
        namespace = key.partition(":")[0]
        if not (arguments.all_namespaces or namespace in namespaces):
            continue
        try:
            tag = read_json(resource)
        except ValueError as exception:
            errors.append(str(exception))
            continue
        values = tag.get("values", [])
        if not isinstance(values, list):
            errors.append(f"{resource.source}: values must be a list")
            continue
        for entry in values:
            required = True
            reference = entry
            if isinstance(entry, dict):
                reference = entry.get("id")
                required = entry.get("required", True) is not False
            if not required or not isinstance(reference, str):
                continue
            is_tag = reference.startswith("#")
            target = reference[1:] if is_tag else reference
            if target.partition(":")[0] != namespace:
                continue  # Cross-namespace registries are not all present in the mod directory.
            target_store = stores["set_tags"] if is_tag else stores["sets"]
            if target not in target_store:
                kind = "structure-set tag" if is_tag else "structure set"
                errors.append(f"{resource.source}: required {kind} {target} is missing")
    for key, resource in stores["sets"].items():
        if key in disabled_sets:
            continue
        try:
            structure_set = read_json(resource)
        except ValueError as exception:
            errors.append(str(exception))
            continue
        entries = structure_set.get("structures", [])
        if not isinstance(entries, list):
            errors.append(f"{resource.source}: structures must be a list")
            continue
        for entry in entries:
            root = entry.get("structure") if isinstance(entry, dict) else entry
            if (isinstance(root, str) and not root.startswith("#")
                    and (arguments.all_namespaces or root.partition(":")[0] in namespaces)):
                active_roots.add(root)

    queue: deque[tuple[str, str, str]] = deque()
    visited_pools: set[tuple[str, str]] = set()
    template_roots: dict[str, set[str]] = defaultdict(set)
    template_chains: dict[tuple[str, str], str] = {}
    for root in sorted(active_roots):
        resource = stores["structures"].get(root)
        if resource is None:
            continue  # Code-defined and vanilla roots need not have data JSON.
        try:
            structure = read_json(resource)
        except ValueError as exception:
            errors.append(str(exception))
            continue
        pool = structure.get("start_pool")
        if isinstance(pool, str):
            queue.append((pool, root, root))

    nbt_cache: dict[str, tuple[list[str], set[str]]] = {}
    while queue:
        pool_key, root, chain = queue.popleft()
        if pool_key == "minecraft:empty" or (pool_key, root) in visited_pools:
            continue
        visited_pools.add((pool_key, root))
        resource = stores["pools"].get(pool_key)
        if resource is None:
            errors.append(f"root {root}: missing pool {pool_key} via {chain}")
            continue
        try:
            pool = read_json(resource)
        except ValueError as exception:
            errors.append(f"root {root}: {exception}")
            continue
        fallback = pool.get("fallback")
        if isinstance(fallback, str) and fallback != "minecraft:empty":
            queue.append((fallback, root, f"{chain} -> {pool_key}"))
        elements = pool.get("elements", [])
        if not isinstance(elements, list):
            errors.append(f"root {root}: {resource.source}: elements must be a list")
            continue
        for entry in elements:
            for template in pool_elements(entry):
                template_roots[template].add(root)
                template_chains.setdefault((template, root), f"{chain} -> {pool_key} -> {template}")
                template_resource = stores["templates"].get(template)
                if template_resource is None:
                    errors.append(f"root {root}: missing template {template} via {template_chains[(template, root)]}")
                    continue
                if template not in nbt_cache:
                    template_issues: list[str] = []
                    referenced_pools: set[str] = set()
                    try:
                        nbt = NbtReader(template_resource.read()).root()
                        inspect_nbt(nbt, "root", template_issues, referenced_pools)
                    except Exception as exception:
                        template_issues.append(f"invalid NBT: {exception}")
                    nbt_cache[template] = (template_issues, referenced_pools)
                template_issues, referenced_pools = nbt_cache[template]
                for issue in template_issues:
                    errors.append(
                        f"root {root}: {issue} in {template_resource.source} via {template_chains[(template, root)]}"
                    )
                for referenced_pool in referenced_pools:
                    queue.append((referenced_pool, root, template_chains[(template, root)]))

    if errors:
        unique_errors = sorted(set(errors))
        grouped: dict[str, list[str]] = defaultdict(list)
        for error in unique_errors:
            match = re.match(r"^(root [^:]+:[^:]+): (.*)$", error)
            if match:
                grouped[match.group(1)].append(match.group(2))
            else:
                grouped["resource graph"].append(error)
        print(f"structure validation failed with {len(unique_errors)} reachable issue(s):", file=sys.stderr)
        for root, root_errors in grouped.items():
            print(f"- {root}: {len(root_errors)} issue(s)", file=sys.stderr)
            for error in root_errors[:5]:
                print(f"    {error}", file=sys.stderr)
            if len(root_errors) > 5:
                print(f"    ... {len(root_errors) - 5} more", file=sys.stderr)
        return 1
    print(
        "structure validation passed: "
        f"{len(active_roots)} active roots, {len(visited_pools)} root/pool edges, "
        f"{len(nbt_cache)} reachable templates"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
