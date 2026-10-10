package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;

/** Registered physical effect owners contribute exact held slots; storage does not inspect jobs. */
final class ContainerPhysicalReservations {
    private static final ImmutableInputView<List<ContainerSlotClaim>> CLAIMS = new ImmutableInputView<>();
    private static final ImmutableInputView<List<ContainerInboundCapacity.Demand>> DEMANDS = new ImmutableInputView<>();
    private record DemandKey(SubjectId owner, SubjectId container, String kind) { }
    private record Owners(ResourceSiteState sites, Map<SubjectId, ProductionJob> production, ShipmentState shipments,
            io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState extraction) { }
    private static final List<Function<Owners, List<ContainerSlotClaim>>> PORTS = List.of(
            owners -> HarvestContainerReservations.claims(owners.sites()),
            owners -> ShipmentPhysicalAuthority.slotClaims(owners.shipments()),
            owners -> BakeryPhysicalAuthority.slotClaims(owners.production()),
            owners -> ExtractionPhysicalAuthority.slotClaims(owners.extraction()));
    private static List<ContainerSlotClaim> claims(ExactInventory inventory, Owners owners) {
        return CLAIMS.get(List.of(inventory.containers(), owners.sites(), owners.production(), owners.shipments(), owners.extraction()), () -> {
            var claims = PORTS.stream().flatMap(port -> port.apply(owners).stream()).toList();
            requireExclusive(inventory, claims);
            return claims;
        });
    }
    static List<ContainerSlotClaim> claims(FrontierWorldState state) {
        return claims(state.inventory(), new Owners(state.resourceSites(), state.productionJobs(), state.shipments(), state.extractionSites()));
    }
    static void validate(ExactInventory inventory, ResourceSiteState sites, Map<SubjectId, ProductionJob> jobs,
                         ShipmentState shipments, io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState extraction) {
        claims(inventory, new Owners(sites, jobs, shipments, extraction));
    }
    private static void requireExclusive(ExactInventory inventory, List<ContainerSlotClaim> claims) {
        var held = new HashMap<InventoryCustody.ContainerSlot, ContainerSlotClaim.Owner>();
        for (var claim : claims) {
            var container = inventory.containers().get(claim.slot().containerId());
            if (container == null || claim.slot().slot() >= container.slotCount())
                throw new IllegalArgumentException("physical slot claim has no current container slot");
            var prior = held.putIfAbsent(claim.slot(), claim.owner());
            if (prior != null) throw new IllegalArgumentException("competing physical slot claims: "
                    + claim.slot() + " owners=" + prior + "," + claim.owner());
        }
    }
    static Set<Integer> slots(FrontierWorldState state, SubjectId container) {
        return slots(state, container, Optional.empty());
    }
    static Set<Integer> slots(FrontierWorldState state, SubjectId container, Optional<SubjectId> completing) {
        return claims(state).stream().filter(claim -> claim.slot().containerId().equals(container)
                && completing.filter(claim.owner().id()::equals).isEmpty())
                .map(claim -> claim.slot().slot()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    static List<ContainerInboundCapacity.Demand> uncoveredDemands(FrontierWorldState state) {
        var physicalClaims = claims(state);
        var demands = ContainerStorageDemandSources.demands(state);
        return DEMANDS.get(List.of(physicalClaims, demands), () -> {
            var covered = new HashMap<DemandKey, Integer>();
            physicalClaims.stream().flatMap(claim -> claim.coveredDemand().stream()).forEach(physical ->
                    covered.merge(new DemandKey(physical.owner(), physical.container(), physical.itemKind()), physical.quantity(), Math::addExact));
            return demands.stream().map(demand -> {
            int remaining = Math.subtractExact(demand.quantity(), covered.getOrDefault(
                    new DemandKey(demand.owner(), demand.container(), demand.itemKind()), 0));
            if (remaining < 0) throw new IllegalArgumentException("physical claims exceed inbound demand");
            return remaining == 0 ? null : new ContainerInboundCapacity.Demand(demand.owner(), demand.container(), demand.itemKind(), remaining);
            }).filter(Objects::nonNull).toList();
        });
    }
    private ContainerPhysicalReservations() { }
}
