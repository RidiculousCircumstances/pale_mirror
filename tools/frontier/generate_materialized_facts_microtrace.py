#!/usr/bin/env python3
"""Pin source-owned consequences of typed non-entity graybox facts."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def outcome(value: object) -> dict[str, object]:
    return {"accepted": value.accepted, "reason": value.reason, "applied_weight": value.applied_weight}


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.economy import Resource
        from simulation.field import CampaignKind, FieldPostKind
        from simulation.infection import OrganKind
        from simulation.operations import AgentKind, AgentRef, Operation, OperationKind, TargetKind, TargetRef
        from simulation.perturbations import MaterializedFact, MaterializedFactKind
        from simulation.profiles import SimulationProfileName
        from simulation.world import World, WorldConfig

        world = World(WorldConfig(seed=281, settlement_count=2, infection_seeds=0,
                                  profile=SimulationProfileName.GRAYBOX_1_40))
        settlement = world.settlements[1]
        site = next(item for item in world.resource_sites.values() if item.owner_id == settlement.id)
        route = next(item for item in world.trade.routes if settlement.id in item.key())
        facility_weight = settlement.facilities.workshop / 2.0
        facility = world.apply_materialized_fact(MaterializedFact(
            "trace:facility", MaterializedFactKind.FACILITY_DAMAGED,
            f"settlement:{settlement.id}:facility:workshop", facility_weight,
        ))
        site_outcome = world.apply_materialized_fact(MaterializedFact(
            "trace:site", MaterializedFactKind.SITE_DAMAGED, f"site:{site.id}", 0.25,
        ))
        route_weight = route.capacity / 3.0
        route_outcome = world.apply_materialized_fact(MaterializedFact(
            "trace:route", MaterializedFactKind.ROUTE_DAMAGED,
            f"route:{route.key()[0]}:{route.key()[1]}", route_weight,
        ))
        organ = world.infection.create_nest(12, 11, biomass=40.0, kind=OrganKind.CORE)
        organ_outcome = world.apply_materialized_fact(MaterializedFact(
            "trace:organ", MaterializedFactKind.ORGAN_DAMAGED, f"organ:{organ.id}", organ.vitality,
        ))
        operation = Operation(
            id=991, side=AgentKind.SETTLEMENT, kind=OperationKind.RECON,
            owner=AgentRef(AgentKind.SETTLEMENT, settlement.id),
            target=TargetRef(TargetKind.CELL, x=settlement.x, y=settlement.y),
            started_day=world.day, x=float(settlement.x), y=float(settlement.y),
            origin_x=float(settlement.x), origin_y=float(settlement.y),
            cargo={Resource.FOOD: 8.0, Resource.MEDICINE: 2.0},
        )
        world.operations.active.append(operation)
        cargo = world.apply_materialized_fact(MaterializedFact(
            "trace:cargo", MaterializedFactKind.OPERATION_CARGO_LOST,
            "operation:991:cargo:food", 3.0,
        ))
        campaign = world.field.create_campaign(
            world, CampaignKind.CONTAINMENT, settlement.id, set(),
            target_kind="cell", target_id=None, target_x=10, target_y=10, reason="trace",
        )
        if campaign is None:
            raise RuntimeError("materialized-fact trace could not create field campaign")
        post = world.field.start_post(
            world, campaign.id, FieldPostKind.CHECKPOINT, 10, 10, settlement.id, {settlement.id},
            {settlement.id: 1.0}, {Resource.FOOD: 8.0, Resource.MEDICINE: 2.0},
        )
        if post is None:
            raise RuntimeError("materialized-fact trace could not create field post")
        field_post_food_weight = post.stock[Resource.FOOD] / 2.0
        field_post_cargo = world.apply_materialized_fact(MaterializedFact(
            "trace:field-post-cargo", MaterializedFactKind.FIELD_POST_CARGO_LOST,
            f"field_post:{post.id}:cargo:food", field_post_food_weight,
        ))
        field_post = world.apply_materialized_fact(MaterializedFact(
            "trace:field-post", MaterializedFactKind.FIELD_POST_DAMAGED, f"field_post:{post.id}", post.integrity,
        ))
        unsupported = world.apply_materialized_fact(MaterializedFact(
            "trace:housing", MaterializedFactKind.FACILITY_DAMAGED,
            "settlement:1:facility:housing", 1.0,
        ))
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "perturbations_sha256": files["simulation/perturbations.py"]},
            "outcomes": {
                "facility": outcome(facility), "site": outcome(site_outcome), "route": outcome(route_outcome),
                "organ": outcome(organ_outcome), "cargo": outcome(cargo), "field_post_cargo": outcome(field_post_cargo),
                "field_post": outcome(field_post),
                "unsupported": outcome(unsupported),
            },
            "state": {
                "workshop": settlement.facilities.workshop,
                "site_condition": site.condition,
                "route_capacity": route.capacity,
                "organ_present": organ.id in world.infection.nests,
                "combat_damage_memory": world.infection.damage_memory["combat"],
                "cargo": {resource.value: amount for resource, amount in sorted(operation.cargo.items(), key=lambda item: item[0].value)},
                "field_post_cargo": {resource.value: amount for resource, amount in sorted(post.stock.items(), key=lambda item: item[0].value)},
                "field_post": {"integrity": post.integrity, "status": post.status.value, "garrison": post.garrison},
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
            raise SystemExit("materialized facts micro-trace differs from the active Python reference")
        print("Frontier materialized facts micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned materialized facts micro-trace to {output}")


if __name__ == "__main__":
    main()
