package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;

/** Exact CARGO_HANDOFF invariants kept separate from aggregate canonical state ownership. */
final class FrontierCargoValidation {
    private FrontierCargoValidation() { }

    static SubjectId receiverStore(FrontierBootstrap bootstrap, RouteOperation operation) {
        BlockPosition destination = operation.route().getLast();
        HiveNest nest = bootstrap.hive().seedNests().stream().filter(value -> value.anchor().equals(destination)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route destination is not a hive nest"));
        return bootstrap.hive().organs().stream().filter(organ -> organ.nestId().equals(nest.id()) && organ.kind() == HiveOrganKind.STORE)
                .findFirst().flatMap(HiveOrgan::containerId).orElseThrow(() -> new IllegalArgumentException("hive nest has no exact store receiver"));
    }

    static void validateObservation(FrontierBootstrap bootstrap, Map<SubjectId, RouteOperation> operations,
                                    Map<SubjectId, SupplyContract> contracts, ExactInventory inventory,
                                    PhysicalIntent intent, CargoHandoffObservation observation) {
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF || !intent.id().equals(observation.intentId())) {
            throw new IllegalArgumentException("unsupported or mismatched physical observation kind");
        }
        RouteOperation operation = operations.get(intent.causeSubjectId());
        if (operation == null || !operation.cargoId().equals(observation.cargoId())) throw new IllegalArgumentException("physical observation does not match its route operation");
        SupplyContract contract = contracts.values().stream().filter(value -> value.cargoId().equals(observation.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("physical observation cargo has no contract"));
        if (contract.status() != ContractStatus.DELIVERED || inventory.cargo().containsKey(observation.cargoId())) {
            throw new IllegalArgumentException("physical observation requires delivered cargo without a retained batch");
        }
        SubjectId receiver = receiverStore(bootstrap, operation);
        int observedCount = 0;
        for (CargoHandoffPlacement placement : observation.placements()) {
            ExactItemStack item = inventory.items().get(placement.itemId());
            if (item == null || !item.itemKind().equals(contract.itemKind()) || !item.custody().equals(placement.receiverSlot())
                    || !receiver.equals(placement.receiverSlot().containerId())) throw new IllegalArgumentException("physical observation placement does not match exact delivered inventory");
            observedCount = Math.addExact(observedCount, item.count());
        }
        if (observedCount != contract.itemCount()) throw new IllegalArgumentException("physical observation delivered count does not match contract");
    }
}
