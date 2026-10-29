package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;

/** Registered physical effect owners contribute exact held slots; storage does not inspect jobs. */
final class ContainerPhysicalReservations {
    private static final ImmutableInputView<List<ContainerSlotClaim>> CLAIMS = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SubjectId, List<ContainerSlotClaim>>> BY_CONTAINER = new ImmutableInputView<>();
    private static final ImmutableKeyedInputView<SubjectId, List<ContainerInboundCapacity.Demand>> DEMANDS = new ImmutableKeyedInputView<>(512);
    private static final ImmutableInputView<Map<SubjectId, BackingView>> LOCAL_BACKING = new ImmutableInputView<>();
    private static final ThreadLocal<List<ContainerSlotClaim>> previousDeclared = ThreadLocal.withInitial(List::of);
    /** Local capacity facts only. A claim elsewhere cannot force this container's
     * sorted reservation admission to run again; player edits still change its budget. */
    private static final class BackingView {
        private List<ContainerSlotClaim> declared;
        private ContainerSlotBudget budget;
        private List<ContainerSlotClaim> result;
        List<ContainerSlotClaim> get(ExactInventory inventory, List<ContainerSlotClaim> next, ContainerSlotBudget current) {
            if (result == null || !declared.equals(next) || !budget.equals(current)) {
                var checked = backed(inventory, next);
                declared = next; budget = current; result = checked;
            }
            return result;
        }
    }
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
            // Complete immutable claim values cut off downstream invalidation when
            // only the producer's progress changes; native permission is separate.
            return view.getPublished(List.of(source), () -> List.copyOf(read.apply(source)));
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
            var previous = previousDeclared.get();
            if (previous.equals(claims)) return previous;
            previousDeclared.set(claims); return claims;
        });
    }
    private static List<ContainerSlotClaim> declared(FrontierWorldState state) {
        return claims(state.inventory(), new Owners(state.resourceSites().sites(), state.productionJobs(), state.shipments(), state.extractionSites(), state.forest(), state.physicalIntents()));
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
    private static Map<SubjectId, List<ContainerSlotClaim>> byContainer(List<ContainerSlotClaim> claims) {
        return BY_CONTAINER.get(List.of(claims), () -> {
            var result = new HashMap<SubjectId, List<ContainerSlotClaim>>();
            for (var claim : claims) result.computeIfAbsent(claim.slot().containerId(), ignored -> new ArrayList<>()).add(claim);
            result.replaceAll((id, values) -> List.copyOf(values));
            return Collections.unmodifiableMap(result);
        });
    }
    /** Discover this container before assessing capacity, never after a world-wide
     * backing pass. Views are bounded by the current declared container registry. */
    private static List<ContainerSlotClaim> atContainer(FrontierWorldState state, SubjectId container) {
        return backingAt(state, container, byContainer(declared(state)).getOrDefault(container, List.of()));
    }
    private static List<ContainerSlotClaim> backingAt(FrontierWorldState state, SubjectId container, List<ContainerSlotClaim> claims) {
        if (claims.isEmpty()) return List.of();
        var inventory = state.inventory();
        var budget = ContainerStockIndex.budget(inventory, container);
        var views = LOCAL_BACKING.get(List.of(inventory.containers()), HashMap::new);
        return views.computeIfAbsent(container, ignored -> new BackingView()).get(inventory, claims, budget);
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
    static List<ContainerInboundCapacity.Demand> uncoveredDemands(FrontierWorldState state, SubjectId container) {
        // Capacity belongs to the queried receiver. Backing every other store's
        // reservations here turns each local admission into a world stock scan.
        var physicalClaims = atContainer(state, container);
        var demands = ContainerStorageDemandSources.demands(state, container);
        return DEMANDS.get(state.bootstrap(), container, List.of(physicalClaims, demands), () -> {
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
