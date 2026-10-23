package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** External removal of a maintenance target cancels its operation without refunding spent heat or work. */
public final class StationExactItemDeparture {
    public static boolean committed(FrontierWorldState state, SubjectId item) {
        return state.productionStations().machines().values().stream().flatMap(machine -> machine.operation().stream())
                .anyMatch(operation -> !operation.transformed() && operation.inputCustody().maintenanceTarget()
                        .filter(target -> target.id().equals(item)).isPresent());
    }
    public static FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source, ExactInventory inventory) {
        var operation = state.productionStations().machines().values().stream().flatMap(machine -> machine.operation().stream())
                .filter(value -> !value.transformed() && value.inputCustody().maintenanceTarget()
                        .filter(target -> target.id().equals(item)).isPresent()).reduce((left, right) -> {
                            throw new IllegalArgumentException("maintenance target has competing operations");
                        }).orElseThrow();
        var target = operation.inputCustody().maintenanceTarget().orElseThrow();
        if (!target.custody().equals(source) || ContainerPhysicalAuthorityComposition.pending(state,
                ((InventoryCustody.ContainerSlot) source).containerId()))
            throw new IllegalArgumentException("maintenance departure overlaps a prepared effect or foreign source");
        var abandoned = ProductionStationProcessing.abandon(state, operation);
        var transaction = FungibleForfeitureSettlement.from(state, inventory.withFungibleResources(abandoned.inventory().fungibleResources()))
                .withStations(abandoned.machines());
        transaction = ProductionOperationCompletionComposition.inputLost(state, operation, transaction);
        return state.withChanges(transaction.contribution());
    }
    private StationExactItemDeparture() { }
}
