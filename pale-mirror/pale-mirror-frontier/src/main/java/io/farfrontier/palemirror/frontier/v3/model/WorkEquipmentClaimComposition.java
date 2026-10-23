package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/** Read-only composition of owner-retained reservations, not another mutable equipment registry. */
public final class WorkEquipmentClaimComposition {
    private static final List<Function<FrontierWorldState, Stream<SubjectId>>> OWNERS = List.of(
            state -> state.extractionSites().work().values().stream().filter(job -> !job.terminal()).map(job -> job.toolId()),
            state -> state.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                    .filter(job -> !job.equipment().settled()).map(job -> job.equipment().itemId()),
            StationWorkItemDeparture::reserved,
            state -> state.shipments().shipments().values().stream().filter(s -> !s.terminal() || s.reception().isPresent())
                    .map(Shipment::cargo).filter(ShipmentCargo.Individual.class::isInstance)
                    .map(ShipmentCargo.Individual.class::cast).map(ShipmentCargo.Individual::itemId),
            state -> state.companies().goodsTrade().contracts().values().stream()
                    .flatMap(contract -> contract.individualAllocations().values().stream()).map(IndividualResourceAllocation::itemId));
    private static final ImmutableInputView<Set<SubjectId>> CLAIMS = new ImmutableInputView<>();
    public static Set<SubjectId> reserved(FrontierWorldState state) {
        // These are the complete inputs read by the registered claim producers.
        // A new view of the same claims must not invalidate downstream forecasts.
        return CLAIMS.get(List.of(state.bootstrap(), state.extractionSites().work(),
                state.resourceSites().sites(), state.productionJobs(), state.shipments().shipments(),
                state.companies().goodsTrade().contracts()), () -> OWNERS.stream()
                    .flatMap(owner -> owner.apply(state)).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    private WorkEquipmentClaimComposition() { }
}
