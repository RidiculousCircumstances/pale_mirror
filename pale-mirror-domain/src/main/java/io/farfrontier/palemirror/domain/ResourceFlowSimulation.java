package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic transfer and consumption of coarse strategic resources. */
public final class ResourceFlowSimulation {
    private final DomainEventFactory events;

    ResourceFlowSimulation(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        Map<WorldObjectId, Map<ResourceKind, Integer>> available = availableProduction(state);
        Map<WorldObjectId, Map<ResourceKind, Integer>> deliveries = new LinkedHashMap<>();
        state.routes().stream().sorted(Comparator.comparing(RouteState::id)).forEach(route -> {
            int transferred = take(available, route.origin(), route.resource(), route.transferableCapacity());
            if (transferred > 0) deliveries.computeIfAbsent(route.destination(), ignored -> new EnumMap<>(ResourceKind.class))
                    .merge(route.resource(), transferred, Integer::sum);
        });
        List<DomainEvent> produced = new ArrayList<>();
        state.settlements().stream().sorted(Comparator.comparing(SettlementState::id)).forEach(settlement -> {
            SettlementStatus before = settlement.status();
            int beforeDefense = settlement.currentDefense();
            settlement.advanceResources(deliveries.getOrDefault(settlement.id(), Map.of()));
            if (before != SettlementStatus.SHORTAGE && settlement.status() == SettlementStatus.SHORTAGE) {
                produced.add(events.create(state, DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED, settlement.id(), null));
            } else if (before == SettlementStatus.SHORTAGE && settlement.status() == SettlementStatus.STABLE) {
                produced.add(events.create(state, DomainEventType.SETTLEMENT_SUPPLY_RESTORED, settlement.id(), null));
            }
            if (settlement.currentDefense() < beforeDefense) {
                produced.add(events.create(state, DomainEventType.SETTLEMENT_DEFENCE_DECLINED, settlement.id(), null));
            }
            if (before != SettlementStatus.DECLINING && settlement.status() == SettlementStatus.DECLINING) {
                produced.add(events.create(state, DomainEventType.SETTLEMENT_DECLINING, settlement.id(), null));
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
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
