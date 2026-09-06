#!/usr/bin/env python3
"""Generate the source-pinned terrain, tissue and synapse foundation trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.infection import InfectionModel, OrganKind

        model = InfectionModel(7, 5, seed=17)
        model.seed_infection(2, 2, level=0.85, radius=2)
        synapse = model.create_nest(3, 2, biomass=30.0, samples=2.0, parent_nest_id=1, kind=OrganKind.SYNAPSE)
        isolated = model.create_nest(6, 4, biomass=44.0, samples=1.0, kind=OrganKind.BROOD_SAC)
        model.genome["synaptic_redundancy"] = 0.5
        components, cells = model.network_components()
        profile = model.organ_network_profile(synapse)
        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "biomes": [[biome.value for biome in row] for row in model.biomes],
            "ecosystem": {"organic": model.ecosystem.total_organic(), "scar": model.ecosystem.total_scar()},
            "tissue": [[value for value in row] for row in model.level],
            "organs": [{"id": organ.id, "x": organ.x, "y": organ.y, "kind": organ.kind.value,
                        "biomass": organ.biomass, "samples": organ.samples, "parent": organ.parent_nest_id,
                        "feral": organ.feral} for organ in model.nests.values()],
            "pressure": {"center": model.pressure_at(2, 2), "corner": model.pressure_at(0, 0)},
            "route": model.route_infection(0, 0, 6, 4),
            "components": {"by_cell": {f"{x},{y}": identifier for (x, y), identifier in components.items()},
                           "cells": [[list(position) for position in cell] for cell in cells]},
            "profile": profile,
            "signal": {"core": model.signal_at(2, 2), "synapse": model.signal_at(3, 2),
                       "isolated": model.signal_at(6, 4), "map": model.signal_map(), "feral_fraction": model.feral_fraction()},
            "adaptation": model.adaptation_multiplier("signal_multiplier"),
        }
        model.nests[1].vitality = 0.0
        model._refresh_feral_status()
        result["damaged_core"] = {"signal": model.signal_at(3, 2), "core_feral": model.nests[1].feral,
                                  "synapse_feral": model.nests[2].feral, "isolated_feral": isolated.feral}
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
            raise SystemExit("infection foundation micro-trace differs from the active Python reference")
        print("Frontier infection foundation micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection foundation micro-trace to {output}")


if __name__ == "__main__":
    main()
