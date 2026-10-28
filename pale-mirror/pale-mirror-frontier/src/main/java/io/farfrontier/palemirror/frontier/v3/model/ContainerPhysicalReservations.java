package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;

/** Registered physical effect owners contribute exact held slots; storage does not inspect jobs. */
final class ContainerPhysicalReservations {
    private static final ImmutableInputView<List<ContainerSlotClaim>> CLAIMS = new ImmutableInputView<>();
    private static final ImmutableInputView<List<ContainerSlotClaim>> BACKED = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SubjectId, List<ContainerSlotClaim>>> BY_CONTAINER = new ImmutableInputView<>();
    private static final ImmutableInputView<List<ContainerInboundCapacity.Demand>> DEMANDS = new ImmutableInputView<>();
    private record DemandKey(SubjectId owner, SubjectId container, ContainerSectionId section, String kind, ContainerInboundCapacity.Representation representation) { }
    private record Owners(Map<SubjectId, ResourceSiteLifecycle> sites, Map<SubjectId, ProductionJob> production, ShipmentState shipments,
            io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState extraction,
            io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState forest,
            Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> equipment) { }
    private static <I> Function<Owners, List<ContainerSlotClaim>> port(
            Function<Owners, I> inputs, Function<I, List<ContainerSlotClaim>> read) {
        var view = new ImmutableInputView<List<ContainerSlotClaim>>();
        return owners -> {
            var source = inputs.apply(owners);
            return view.get(List.of(source), () -> List.copyOf(read.apply(source)));
        };
    }
    private static final List<Function<Owners, List<ContainerSlotClaim>>> PORTS = List.of(
            port(Owners::sites, HarvestContainerReservations::claims),
            port(Owners::shipments, ShipmentPhysicalAuthority::slotClaims),
            port(Owners::production, StationWorkPhysicalAuthority::slotClaims),
            port(Owners::extraction, ExtractionPhysicalAuthority::slotClaims),
            port(Owners::forest, ForestryPhysicalAuthority::slotClaims),
            port(Owners::equipment, EquipmentPhysicalAuthority::slotClaims));
    private static List<ContainerSlotClaim> claims(ExactInventory inventory, Owners owners) {
        var contributions = PORTS.stream().map(port -> port.apply(owners)).toList();
        var inputs = new ArrayList<Object>(contributions); inputs.add(inventory.containers());
        return CLAIMS.get(inputs, () -> {
            var claims = contributions.stream().flatMap(List::stream).toList();
            requireExclusive(inventory, claims);
            return claims;
        });
    }
    static List<ContainerSlotClaim> claims(FrontierWorldState state) {
        var declared = claims(state.inventory(), new Owners(state.resourceSites().sites(), state.productionJobs(), state.shipments(), state.extractionSites(), state.forest(), state.physicalIntents()));
        return BACKED.get(List.of(state.inventory(), declared), () -> backed(state.inventory(), declared));
    }
    /** Prospective destinations reserve only still available capacity. External
     * edits may displace them; in-flight physical operations are separately fenced
     * by ContainerPhysicalAuthorityComposition, never by these planning hints. */
    private static List<ContainerSlotClaim> backed(ExactInventory inventory, List<ContainerSlotClaim> declared) {
        var occupied = new HashMap<SubjectId, Set<Integer>>();
        var result = new ArrayList<ContainerSlotClaim>();
        for (var claim : declared.stream().sorted(Comparator
                .comparing((ContainerSlotClaim c) -> c.slot().containerId())
                .thenComparingInt(c -> c.slot().slot())).toList()) {
            var slots = new HashSet<>(occupied.getOrDefault(claim.slot().containerId(), Set.of()));
            slots.add(claim.slot().slot());
            if (!inventory.canReserveSlots(claim.slot().containerId(), slots)) continue;
            occupied.put(claim.slot().containerId(), slots);
            result.add(claim);
        }
        return List.copyOf(result);
    }
    static void validate(ExactInventory inventory, ResourceSiteState sites, Map<SubjectId, ProductionJob> jobs,
                         ShipmentState shipments, io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState extraction,
                         io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState forest,
                         Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> equipment) {
        claims(inventory, new Owners(sites.sites(), jobs, shipments, extraction, forest, equipment));
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
    /** Select only this exact storage's already validated/backed claims. The list
     * identity includes all current owner and inventory backing dependencies. */
    private static List<ContainerSlotClaim> atContainer(FrontierWorldState state, SubjectId container) {
        var claims = claims(state);
        return BY_CONTAINER.get(List.of(claims), () -> {
            var result = new HashMap<SubjectId, List<ContainerSlotClaim>>();
            for (var claim : claims) result.computeIfAbsent(claim.slot().containerId(), ignored -> new ArrayList<>()).add(claim);
            result.replaceAll((id, values) -> List.copyOf(values));
            return Collections.unmodifiableMap(result);
        }).getOrDefault(container, List.of());
    }
    static Set<Integer> slots(FrontierWorldState state, SubjectId container) {
        return slots(state, container, Optional.empty());
    }
    static Set<Integer> slots(FrontierWorldState state, SubjectId container, Optional<SubjectId> completing) {
        var claims = atContainer(state, container);
        if (claims.isEmpty()) return Set.of();
        return claims.stream().filter(claim -> completing.filter(claim.owner().id()::equals).isEmpty())
                .map(claim -> claim.slot().slot()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    static Set<Integer> slotsExceptClaim(FrontierWorldState state, SubjectId container, ContainerSlotClaim.Owner current) {
        var claims = atContainer(state, container);
        if (claims.isEmpty()) return Set.of();
        return claims.stream().filter(claim -> !claim.owner().equals(current))
                .map(claim -> claim.slot().slot()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    static List<ContainerInboundCapacity.Demand> uncoveredDemands(FrontierWorldState state) {
        var physicalClaims = claims(state);
        var demands = ContainerStorageDemandSources.demands(state);
        return DEMANDS.get(List.of(physicalClaims, demands), () -> {
            var covered = new HashMap<DemandKey, Integer>();
            physicalClaims.stream().flatMap(claim -> claim.coveredDemand().stream()).forEach(physical ->
                    covered.merge(new DemandKey(physical.owner(), physical.container(), physical.section(), physical.itemKind(), physical.representation()), physical.quantity(), Math::addExact));
            return demands.stream().map(demand -> {
            int remaining = Math.subtractExact(demand.quantity(), covered.getOrDefault(
                    new DemandKey(demand.owner(), demand.container(), demand.section(), demand.itemKind(), demand.representation()), 0));
            if (remaining < 0) throw new IllegalArgumentException("physical claims exceed inbound demand");
            return remaining == 0 ? null : new ContainerInboundCapacity.Demand(demand.owner(), demand.container(), demand.itemKind(), remaining, demand.representation(), demand.section());
            }).filter(Objects::nonNull).toList();
        });
    }
    private ContainerPhysicalReservations() { }
}
