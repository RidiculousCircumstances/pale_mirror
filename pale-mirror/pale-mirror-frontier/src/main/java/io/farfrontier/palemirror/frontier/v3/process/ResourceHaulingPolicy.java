package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Placement policy only; shipment/UAE own transport and source adapters own pickup. */
final class ResourceHaulingPolicy {
    static final String REVIEW = "frontier.resource_hauling.review";
    static ScheduledAction review(SubjectId home, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:resource-hauling-" + java.util.UUID.nameUUIDFromBytes(
                home.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))), new SimInstant(tick), 12, home, REVIEW, 1);
    }
    static Optional<InternalShipmentDispatched> offer(FrontierWorldState state, ResidentProfile worker, long tick) {
        var stationSupply = ProductionStationSupply.offer(state, worker, tick);
        if (stationSupply.isPresent()) return stationSupply;
        var resources = state.inventory().fungibleResources();
        for (var source : ResourceSourceDiscovery.localHaulSources(state, worker.settlementId())) {
            if (source.container().filter(id -> ReferenceContainerCustody.blocksCanonicalUse(state, id)).isPresent()) continue;
            var account = resources.accounts().values().stream().filter(value -> value.custody().equals(source.custody())).findFirst().orElse(null);
            if (account == null) continue;
            var kinds = account.lotQuantities().keySet().stream().map(id -> resources.lots().get(id).itemKind()).distinct().sorted().toList();
            for (var kind : kinds) {
                int quantity = Math.min(64, resources.unclaimedQuantity(account.id(), worker.settlementId(), kind));
                if (quantity == 0) continue;
                var placement = ResourceStorageSelection.destinationUpTo(state, worker.settlementId(), kind, quantity, source.station(),
                        Set.of(ResourceStorageRole.WAREHOUSE), candidate -> source.container().filter(candidate.endpoint().containerId()::equals).isEmpty()
                                && InternalShipmentPlanning.prepare(state, worker, source, candidate.endpoint(), kind, candidate.quantity(), tick).isPresent());
                if (placement.isPresent()) return InternalShipmentPlanning.prepare(state, worker, source, placement.orElseThrow().endpoint(),
                        kind, placement.orElseThrow().quantity(), tick);
            }
        }
        return Optional.empty();
    }
    static boolean available(FrontierWorldState state, ResidentProfile worker, long tick) {
        try { return offer(state, worker, tick).isPresent(); }
        catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) { return false; }
    }
    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
        if (!REVIEW.equals(action.kind()) || !review(action.subject(), action.dueAt().ticks()).equals(action))
            throw new IllegalArgumentException("resource hauling review has foreign identity");
        long now = Math.max(instant.ticks(), action.dueAt().ticks());
        var events = new ArrayList<ProposedEvent>(); var projected = state;
        for (var worker : ResidentWorkComposition.SELECTION.eligible(state, action.subject(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, now)) {
            Optional<InternalShipmentDispatched> offer;
            try { offer = offer(projected, worker, now); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) { break; }
            if (offer.isEmpty()) continue;
            var dispatched = offer.orElseThrow(); projected = InternalShipmentStateSupport.dispatch(projected, action.subject(), dispatched);
            events.add(new ProposedEvent(action.subject(), dispatched));
            events.add(new ProposedEvent(dispatched.shipment().id(), new ScheduleEffect.Created(ShipmentProcess.progress(dispatched.shipment().id(), Math.addExact(now, 1)))));
        }
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                review(action.subject(), Math.addExact(now, state.bootstrap().ruleset().extraction().reviewTicks())))));
        return List.copyOf(events);
    }
    private ResourceHaulingPolicy() { }
}
