#!/usr/bin/env python3
"""Generate a source-pinned scalar ecology transition trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def cell_state(cell: object) -> dict[str, float]:
    return {"flora": cell.flora, "fauna": cell.fauna, "detritus": cell.detritus,
            "nutrients": cell.nutrients, "moisture": cell.moisture, "scar": cell.scar}


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from random import Random
        from simulation.ecology import Biome, Ecosystem

        ecosystem = Ecosystem([[Biome.PLAINS, Biome.FOREST], [Biome.WETLAND, Biome.BARREN]], Random(77))
        initial = {f"{x},{y}": cell_state(ecosystem.cell(x, y)) for y in range(2) for x in range(2)}
        consumed, genetic = ecosystem.consume(0, 0, 20.0)
        forest_factor = ecosystem.human_output_factor("forest", 1, 0)
        ecosystem.human_extract("forest", 1, 0, 15.0)
        ecosystem.add_detritus(1, 1, 4.0)
        ecosystem.restore(0, 0, 0.6)
        ecosystem.scorch(1, 0, 0.4)
        ecosystem.regenerate()
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "ecology_sha256": files["simulation/ecology.py"]},
            "initial": initial,
            "consume": {"mass": consumed, "genetic": genetic, "forest_factor": forest_factor},
            "final": {f"{x},{y}": cell_state(ecosystem.cell(x, y)) for y in range(2) for x in range(2)},
            "totals": {"organic": ecosystem.total_organic(), "scar": ecosystem.total_scar()},
            "summary": ecosystem.summary_at(1, 0),
        })
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
            raise SystemExit("ecology micro-trace differs from the active Python reference")
        print("Frontier ecology micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned ecology micro-trace to {output}")


if __name__ == "__main__":
    main()
