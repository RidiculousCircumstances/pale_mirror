package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic abstract flow. Route adapters validate capability; they never deliver canonical stock directly. */
public final class ResourceFlowSimulation {
    private final DomainEventFactory events;

    ResourceFlowSimulation(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        refreshPaleMirrorContracts(state);
        Map<WorldObjectId, Map<ResourceKind, Integer>> available = availableProduction(state);
        Map<WorldObjectId, Map<ResourceKind, Integer>> deliveries = new LinkedHashMap<>();
        allocateByDemand(state, available, deliveries);
        List<DomainEvent> produced = new ArrayList<>();
        state.economies().stream().sorted(Comparator.comparing(SettlementEconomy::communityId)).forEach(economy -> {
            SettlementCommunity community = state.community(economy.communityId()).orElseThrow();
            for (ResourceKind kind : ResourceKind.values()) {
                ResourceAccount account = economy.accounts().get(kind);
                if (account == null) continue;
                ResourceAvailability before = account.availability();
                account.advance(deliveries.getOrDefault(economy.communityId(), Map.of()).getOrDefault(kind, 0),
                        community.rationing());
                if (before != account.availability()) {
                    produced.add(events.create(state, account.availability() == ResourceAvailability.AVAILABLE
                            ? DomainEventType.SETTLEMENT_SUPPLY_RESTORED : DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED,
                            community.id(), "resource:" + kind.name().toLowerCase()));
                }
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }

    private static void allocateByDemand(WorldState state,
                                         Map<WorldObjectId, Map<ResourceKind, Integer>> available,
                                         Map<WorldObjectId, Map<ResourceKind, Integer>> deliveries) {
        Map<SupplyKey, List<FlowRequest>> grouped = new LinkedHashMap<>();
        state.routeContracts().stream().sorted(Comparator.comparing(RouteContract::id)).forEach(contract -> {
            SiteAffiliation origin = state.siteAffiliation(contract.originEndpoint(), SiteAffiliationRole.SUPPLIER).orElse(null);
            SiteAffiliation destination = state.siteAffiliation(contract.destinationEndpoint(), SiteAffiliationRole.RECIPIENT).orElse(null);
            SettlementEconomy economy = destination == null ? null : state.economy(destination.objectId()).orElse(null);
            ResourceAccount account = economy == null ? null : economy.accounts().get(contract.resource());
            SettlementCommunity community = destination == null ? null : state.community(destination.objectId()).orElse(null);
            int capacity = contract.transferableCapacity(state.simulationStep());
            if (origin == null || destination == null || account == null || community == null || capacity <= 0) return;
            int consumption = community.rationing() ? account.rationedConsumption() : account.baseConsumption();
            int demand = Math.max(0, account.capacity() - account.stock() + consumption - account.production());
            if (demand <= 0) return;
            grouped.computeIfAbsent(new SupplyKey(origin.objectId(), contract.resource()), ignored -> new ArrayList<>())
                    .add(new FlowRequest(contract, destination.objectId(), Math.min(capacity, demand)));
        });
        grouped.forEach((supply, requests) -> {
            int remainingSupply = available.getOrDefault(supply.origin(), Map.of()).getOrDefault(supply.resource(), 0);
            Map<DemandKey, Integer> remainingDemand = new LinkedHashMap<>();
            requests.forEach(request -> remainingDemand.merge(new DemandKey(request.destination(), supply.resource()),
                    request.demand(), Math::max));
            Map<FlowRequest, Integer> allocations = new LinkedHashMap<>();
            int roundsRemaining = requests.size() + 1;
            while (remainingSupply > 0 && roundsRemaining-- > 0) {
                List<FlowRequest> eligible = requests.stream().filter(request -> {
                    int allocated = allocations.getOrDefault(request, 0);
                    return allocated < request.demand()
                            && remainingDemand.getOrDefault(new DemandKey(request.destination(), supply.resource()), 0) > 0;
                }).toList();
                if (eligible.isEmpty()) break;
                double totalScore = eligible.stream().mapToDouble(request -> (double) Math.max(1,
                        request.demand() - allocations.getOrDefault(request, 0))
                        * request.contract().allocationWeight()).sum();
                int before = remainingSupply;
                Map<FlowRequest, Double> remainders = new LinkedHashMap<>();
                for (FlowRequest request : eligible) {
                    int routeRemaining = request.demand() - allocations.getOrDefault(request, 0);
                    DemandKey demandKey = new DemandKey(request.destination(), supply.resource());
                    int destinationRemaining = remainingDemand.getOrDefault(demandKey, 0);
                    double exact = before * ((double) routeRemaining * request.contract().allocationWeight()) / totalScore;
                    int proportional = (int) Math.min(Integer.MAX_VALUE, Math.floor(exact));
                    remainders.put(request, exact - proportional);
                    int assigned = Math.min(Math.min(routeRemaining, destinationRemaining),
                            Math.min(remainingSupply, proportional));
                    if (assigned <= 0) continue;
                    allocations.merge(request, assigned, Integer::sum);
                    remainingDemand.put(demandKey, destinationRemaining - assigned);
                    remainingSupply -= assigned;
                }
                if (remainingSupply > 0) {
                    List<FlowRequest> remainderOrder = eligible.stream().sorted(Comparator
                            .comparingDouble((FlowRequest request) -> remainders.getOrDefault(request, 0D)).reversed()
                            .thenComparing(request -> request.contract().id())).toList();
                    for (FlowRequest request : remainderOrder) {
                        if (remainingSupply <= 0) break;
                        int allocated = allocations.getOrDefault(request, 0);
                        DemandKey demandKey = new DemandKey(request.destination(), supply.resource());
                        int destinationRemaining = remainingDemand.getOrDefault(demandKey, 0);
                        if (allocated >= request.demand() || destinationRemaining <= 0) continue;
                        allocations.merge(request, 1, Integer::sum);
                        remainingDemand.put(demandKey, destinationRemaining - 1);
                        remainingSupply--;
                    }
                }
                if (remainingSupply == before) break;
            }
            int transferred = allocations.values().stream().mapToInt(Integer::intValue).sum();
            take(available, supply.origin(), supply.resource(), transferred);
            allocations.forEach((request, amount) -> deliveries
                    .computeIfAbsent(request.destination(), ignored -> new EnumMap<>(ResourceKind.class))
                    .merge(supply.resource(), amount, Integer::sum));
        });
    }

    private static void refreshPaleMirrorContracts(WorldState state) {
        state.routeContracts().stream().filter(contract -> contract.provider() == RouteProvider.PALE_MIRROR)
                .sorted(Comparator.comparing(RouteContract::id)).forEach(contract -> {
                    SiteAffiliation origin = state.siteAffiliation(contract.originEndpoint(), SiteAffiliationRole.SUPPLIER).orElse(null);
                    WorldSite originSite = state.site(contract.originEndpoint()).orElse(null);
                    WorldSite destinationSite = state.site(contract.destinationEndpoint()).orElse(null);
                    FacilityState facility = origin == null ? null : state.facility(origin.objectId()).orElse(null);
                    if (facility != null && facility.status() == FacilityStatus.OPERATIONAL
                            && originSite != null && originSite.operationalState() != OperationalState.OFFLINE
                            && destinationSite != null && destinationSite.operationalState() != OperationalState.OFFLINE) {
                        contract.validate(contract.nominalCapacity(), state.simulationStep(),
                                "pm:" + contract.id().value() + ":" + state.simulationStep());
                    }
                });
    }

    private static Map<WorldObjectId, Map<ResourceKind, Integer>> availableProduction(WorldState state) {
        Map<WorldObjectId, Map<ResourceKind, Integer>> available = new LinkedHashMap<>();
        state.facilities().stream().sorted(Comparator.comparing(FacilityState::id)).forEach(facility -> {
            Map<ResourceKind, Integer> output = new EnumMap<>(ResourceKind.class);
            output.put(ResourceKind.IRON, facility.currentProduction());
            available.put(facility.id(), output);
        });
        return available;
    }

    private static int take(Map<WorldObjectId, Map<ResourceKind, Integer>> available, WorldObjectId origin,
                            ResourceKind resource, int requested) {
        if (requested <= 0) return 0;
        Map<ResourceKind, Integer> resources = available.get(origin);
        if (resources == null) return 0;
        int current = resources.getOrDefault(resource, 0);
        int transferred = Math.min(current, requested);
        resources.put(resource, current - transferred);
        return transferred;
    }

    private record SupplyKey(WorldObjectId origin, ResourceKind resource) { }
    private record DemandKey(WorldObjectId destination, ResourceKind resource) { }
    private record FlowRequest(RouteContract contract, WorldObjectId destination, int demand) { }
}
