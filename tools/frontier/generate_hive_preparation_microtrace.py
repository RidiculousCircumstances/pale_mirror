#!/usr/bin/env python3
"""Generate a source-pinned micro-trace for scheduled hive preparation."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def organ_summary(organ: object) -> dict[str, object]:
    return {
        "id": organ.id, "biomass": organ.biomass, "samples": organ.samples,
        "feral": organ.feral, "last_project_day": organ.last_project_day,
    }


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.infection import InfectionModel, OrganKind

        adapted = InfectionModel(7, 5, seed=17)
        adapted.seed_infection(2, 2)
        adapted.nests[1].samples = 20.0
        synapse = adapted.create_nest(3, 2, biomass=100.0, samples=6.0, parent_nest_id=1, kind=OrganKind.SYNAPSE)
        adapted.damage_memory = {"combat": 5.0, "scorch": 8.0}
        adapted.prepare_hive_orders(10)

        insufficient = InfectionModel(7, 5, seed=17)
        insufficient.seed_infection(2, 2)
        insufficient.nests[1].samples = 11.99
        insufficient.damage_memory = {"combat": 8.0}
        insufficient.prepare_hive_orders(10)

        result = {
            "schema": SCHEMA,
            "source": {
                "tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"],
                "balance_sha256": files["simulation/balance.py"],
            },
            "adapted": {
                "genome": adapted.genome,
                "organs": [organ_summary(item) for item in adapted.nests.values()],
                "history": adapted.project_history,
                "damage_memory": adapted.damage_memory,
                "synapse_id": synapse.id,
            },
            "insufficient": {
                "genome": insufficient.genome,
                "samples": insufficient.nests[1].samples,
                "history": insufficient.project_history,
            },
        }
        return canonical_bytes(result)
    finally:
        sys.path.remove(str(root))
        for name in tuple(sys.modules):
            if name == "simulation" or name.startswith("simulation."):
                del sys.modules[name]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = payload(args.reference_root.resolve())
    output = args.output.resolve()
    if args.check:
        if not output.is_file() or output.read_bytes() != result:
            raise SystemExit("hive preparation micro-trace differs from the active Python reference")
        print("Frontier hive preparation micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned hive preparation micro-trace to {output}")


if __name__ == "__main__":
    main()
