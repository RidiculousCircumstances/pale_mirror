package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Production input can reside in a warehouse or in its explicitly installed machine input port. */
public final class ProductionInputAccess {
    public static ResourceStorageAccess at(FrontierWorldState state, SubjectId container) {
        var record = Objects.requireNonNull(state.inventory().containers().get(container), "production input container");
        if (record.storage().isPresent()) return ResourceStorageAccess.at(state, container);
        var station = record.productionStation().orElseThrow(() -> new IllegalArgumentException("input has no storage or station capability"));
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), record.ownerId()).structures().stream()
                .filter(s -> s.id().equals(station.facilityId())).findFirst().orElseThrow();
        var endpoint = new ShipmentEndpoint.ProductionPort(record.ownerId(), station, ActorContainerItemOrder.StationPort.INPUT,
                SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach());
        ShipmentEndpointComposition.validate(state, endpoint);
        return new ResourceStorageAccess(endpoint, List.of(station.workerStation()), ServiceAccessCoordinator.boundary(state, container));
    }
    public static boolean eligible(FrontierWorldState state, SubjectId container, SubjectId home,
            SubjectId facility, ProductionRecipe.Id recipeId) {
        var record = state.inventory().containers().get(container);
        if (record == null || !record.ownerId().equals(home) || ReferenceContainerCustody.blocksCanonicalUse(state, container)) return false;
        var recipe = state.bootstrap().ruleset().production().require(recipeId);
        if (record.storage().isPresent()) return record.storage().orElseThrow().role() == ResourceStorageRole.WAREHOUSE
                && recipe.ingredient().acceptedItems().stream().anyMatch(record.storage().orElseThrow().filter()::accepts);
        return record.productionStation().filter(s -> s.facilityId().equals(facility) && s.capability() == recipe.capability())
                .filter(s -> state.productionStations().machines().containsKey(s.id()))
                .filter(s -> state.productionStations().require(s.id()).operation().isEmpty()).isPresent();
    }
    private ProductionInputAccess() { }
}
