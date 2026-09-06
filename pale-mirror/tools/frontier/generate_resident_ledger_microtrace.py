#!/usr/bin/env python3
"""Generate the source-pinned micro-trace for ``simulation.population``."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from random import Random
import sys
from typing import Any

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def roster(ledger: Any) -> list[dict[str, object]]:
    return [
        {
            "id": resident.id,
            "home_settlement_id": resident.home_settlement_id,
            "occupation": resident.occupation,
            "economic_class": resident.economic_class,
            "employer_company_id": resident.employer_company_id,
            "location": resident.location.value,
            "location_ref": resident.location_ref,
            "condition": resident.condition.value,
            "deployment_role": resident.deployment_role,
        }
        for resident_id in ledger.living_ids()
        for resident in (ledger.residents[resident_id],)
    ]


def ledger_state(ledger: Any) -> dict[str, object]:
    return {
        "revision": ledger.revision,
        "next_ordinal": ledger.next_ordinal,
        "living_ids": list(ledger.living_ids()),
        "roster": roster(ledger),
    }


def cases(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        from simulation.population import ResidentLedger, ResidentLocation

        lifecycle = ResidentLedger(3, 8)
        lifecycle_initial = ledger_state(lifecycle)
        created = lifecycle.create(2)
        lifecycle.assign_employment(19, ("resident:3:1", "resident:3:2"))
        lifecycle.deploy(
            ("resident:3:1", "resident:3:3"),
            17,
            {"resident:3:1": "guard", "resident:3:3": "scout"},
        )
        wounded = lifecycle.wound_expected_from(
            lifecycle.ids_at(ResidentLocation.OPERATION), 1.0, Random(7)
        )
        recovered = lifecycle.recover_expected(1.0, Random(11))
        killed = lifecycle.kill_expected_from(
            ("resident:3:2", "resident:3:3", "resident:3:7"), 1.7, Random(11)
        )

        field = ResidentLedger(10, 3)
        field.assign_field_post(("resident:10:1",), 4)
        field.deploy_from_field_post(
            ("resident:10:1",), 4, 6, {"resident:10:1": "scout"}
        )
        field.return_home(("resident:10:1", "resident:10:missing"))
        wounded_patient = field.wound_expected_from(("resident:10:2",), 1.0, Random(0))
        field.assign_field_post(("resident:10:2",), 9)
        field.transfer_wounded_from_field_post(("resident:10:2",), 9, 12)

        origin = ResidentLedger(1, 5)
        destination = ResidentLedger(2, 1)
        travellers = origin.depart_expected(2.5, Random(2))
        accepted = destination.accept_transferred(travellers)

        classes = ResidentLedger(6, 20)
        return {
            "lifecycle": {
                "initial": lifecycle_initial,
                "created": list(created),
                "wounded": list(wounded),
                "recovered": list(recovered),
                "killed": list(killed),
                "final": ledger_state(lifecycle),
            },
            "field_custody": {
                "wounded_patient": list(wounded_patient),
                "final": ledger_state(field),
            },
            "migration": {
                "traveller_ids": [resident.id for resident in travellers],
                "accepted": list(accepted),
                "origin": ledger_state(origin),
                "destination": ledger_state(destination),
            },
            "class_cycle": {
                "owner": classes.residents["resident:6:12"].economic_class,
                "dependent": classes.residents["resident:6:13"].economic_class,
                "last_occupation": classes.residents["resident:6:12"].occupation,
                "next_occupation": classes.residents["resident:6:13"].occupation,
            },
        }
    finally:
        sys.path.remove(str(root))
        for name in tuple(sys.modules):
            if name == "simulation" or name.startswith("simulation."):
                del sys.modules[name]


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    return canonical_bytes(
        {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "population_sha256": files["simulation/population.py"]},
            "cases": cases(root),
        }
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    source = args.reference_root.resolve()
    result = payload(source)
    output = args.output.resolve()
    if args.check:
        if not output.is_file() or output.read_bytes() != result:
            raise SystemExit("resident-ledger micro-trace differs from the active Python reference")
        print("Frontier resident-ledger micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned resident-ledger micro-trace to {output}")


if __name__ == "__main__":
    main()
