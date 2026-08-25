#!/usr/bin/env python3
"""Generate source-pinned combat, demography and named-casualty traces."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def settlement_state(settlement: object) -> dict[str, object]:
    fields = ("population", "integrity", "alive", "wounded_personnel", "illness_burden")
    return {field: getattr(settlement, field) for field in fields}


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.economy import Resource
        from simulation.profiles import GRAYBOX_1_40, SOURCE_V2
        from simulation.settlement import Facilities, NaturalPotential, Settlement

        defended = Settlement(1, "s", 0, 0, 1000.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
        defended.add(Resource.WEAPONS, 100.0)
        defended.add(Resource.AMMO, 100.0)
        attack = defended.resolve_swarm_attack(1000.0)

        demography = Settlement(2, "d", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(clinic=2.0), profile=SOURCE_V2)
        demography.wound_people(5.0, cause="test")
        demography.end_of_day_demography()

        graybox = Settlement(3, "g", 0, 0, 200.0, 0.0, NaturalPotential(), Facilities(), profile=GRAYBOX_1_40)
        casualties = graybox.apply_exposed_casualties(graybox.residents.living_ids(), 1.5, 1.5, cause="test")
        recovered = graybox.recover_exposed_wounded_people(casualties[1], 5.0, cause="treatment")
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "settlement_sha256": files["simulation/settlement.py"]},
            "combat": {"defence": 134.0, "combat_defence": 203.0, "outcome": attack, "state": settlement_state(defended)},
            "demography": settlement_state(demography),
            "discrete_casualties": {
                "killed": list(casualties[0]),
                "wounded": list(casualties[1]),
                "recovered": list(recovered),
                "living_ids": list(graybox.residents.living_ids()),
                "state": settlement_state(graybox),
            },
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
            raise SystemExit("settlement micro-trace differs from the active Python reference")
        print("Frontier settlement micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned settlement micro-trace to {output}")


if __name__ == "__main__":
    main()
