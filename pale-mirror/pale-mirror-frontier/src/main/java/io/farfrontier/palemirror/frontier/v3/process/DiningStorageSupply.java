package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Placement requests for misplaced food; ordinary shipment execution owns every transfer. */
final class DiningStorageSupply {
    private DiningStorageSupply() { }
    static List<InternalHaulRequest> requests(FrontierWorldState state, SubjectId home) {
        var requests = new ArrayList<InternalHaulRequest>();
        var inventory = state.inventory();
        for (var sourceId : ResourceStorageSelection.declaredContainers(inventory, home, Set.of(ResourceStorageRole.WAREHOUSE))
                .stream().sorted().toList()) {
            if (ResidentDiningPlaces.pantry(state, home, sourceId)) continue;
            var source = inventory.containers().get(sourceId).storage().orElseThrow().endpoint();
            for (var food : state.bootstrap().ruleset().residentLife().foods().foods().keySet().stream().sorted().toList()) {
                var account = FungibleResourceCustodySupport.accountAtContainer(state, sourceId).orElse(null);
                if (account == null || inventory.fungibleResources().unclaimedQuantity(account.id(), home, food) == 0) continue;
                for (var destination : ResourceStorageSelection.candidates(state, home, food, source.station(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                    if (!ResidentDiningPlaces.pantry(state, home, destination.containerId())) continue;
                    InternalHaulRequest.select(state, home, source, destination, food, 64).ifPresent(requests::add);
                }
            }
        }
        return List.copyOf(requests);
    }
}
