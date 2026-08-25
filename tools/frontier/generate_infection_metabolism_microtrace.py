#!/usr/bin/env python3
"""Generate a source-pinned InfectionModel tissue/metabolism micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
from types import SimpleNamespace

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.infection import InfectionModel, OrganKind

        model = InfectionModel(7, 5, seed=17)
        model.seed_infection(2, 2, level=0.85, radius=2)
        model.create_nest(3, 2, biomass=30.0, samples=2.0, parent_nest_id=1, kind=OrganKind.SYNAPSE)
        model.create_nest(1, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.DIGESTIVE_POOL)
        model.create_nest(4, 2, biomass=5.0, samples=0.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        model.genome["rapid_digestion"] = 0.5
        model._spread()
        model._digest_ecology(SimpleNamespace(day=11))
        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "tissue": model.level,
            "organs": [
                {"id": organ.id, "kind": organ.kind.value, "biomass": organ.biomass, "samples": organ.samples}
                for organ in model.nests.values()
            ],
            "flows": [
                {"day": flow.day, "source_kind": flow.source_kind, "source_id": flow.source_id,
                 "source_x": flow.source_x, "source_y": flow.source_y, "nest_id": flow.nest_id,
                 "amount": flow.amount, "retained": flow.retained, "loss": flow.loss, "distance": flow.distance}
                for flow in model.network_flows
            ],
            "ledger": {str(identifier): values for identifier, values in model.nest_economy.items()},
            "harvested": {"biomass": model.harvested_biomass, "genetic_material": model.harvested_genetic_material},
            "ecosystem": {"organic": model.ecosystem.total_organic(), "scar": model.ecosystem.total_scar()},
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
            raise SystemExit("infection metabolism micro-trace differs from the active Python reference")
        print("Frontier infection metabolism micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection metabolism micro-trace to {output}")


if __name__ == "__main__":
    main()
