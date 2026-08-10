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
        state.routeContracts().stream().sorted(Comparator.comparing(RouteContract::id)).forEach(contract -> {
            SiteAffiliation origin = state.siteAffiliation(contract.originEndpoint(), SiteAffiliationRole.SUPPLIER).orElse(null);
            SiteAffiliation destination = state.siteAffiliation(contract.destinationEndpoint(), SiteAffiliationRole.RECIPIENT).orElse(null);
            if (origin == null || destination == null) return;
            int transferred = take(available, origin.objectId(), contract.resource(),
                    contract.transferableCapacity(state.simulationStep()));
            if (transferred > 0) deliveries.computeIfAbsent(destination.objectId(), ignored -> new EnumMap<>(ResourceKind.class))
                    .merge(contract.resource(), transferred, Integer::sum);
        });
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
}
