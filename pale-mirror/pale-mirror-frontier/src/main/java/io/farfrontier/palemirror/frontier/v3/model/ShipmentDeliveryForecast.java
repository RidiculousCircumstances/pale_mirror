package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.Optional;
import static io.farfrontier.palemirror.frontier.v3.model.DeliveryEstimate.Reason.*;

/** Logistics owns timing. This view neither plans routes nor reserves inventory or completes transport. */
public final class ShipmentDeliveryForecast {
    private ShipmentDeliveryForecast() { }
    public static DeliveryEstimate read(FrontierWorldState state, Shipment shipment) {
        if (!shipment.equals(state.shipments().shipments().get(shipment.id())))
            throw new IllegalArgumentException("delivery estimate requires the current exact shipment");
        if (shipment.reception().isPresent()) return DeliveryEstimate.unknown(AWAITING_ACCEPTANCE);
        if (shipment.terminal()) return DeliveryEstimate.unknown(TERMINAL);
        if (shipment.status() != Shipment.Status.CARRYING) return DeliveryEstimate.unknown(LOADING);
        if (!ContainerStorageAdmission.receive(state, shipment.receiver().containerId(), shipment.itemKind(), shipment.quantity(),
                ShipmentAuthorizationComposition.capacityCompletionOwner(shipment))) return DeliveryEstimate.unknown(RECEIVER_UNAVAILABLE);
        if (shipment.transportMissionId().isEmpty()) return single(state, shipment);
        var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
        if (mission.stage() == TransportMission.Stage.UNLOADING) return DeliveryEstimate.unknown(UNLOADING);
        if (mission.stage() != TransportMission.Stage.OUTBOUND) return DeliveryEstimate.unknown(NOT_DISPATCHED);
        var group = state.unitGroups().groups().get(mission.groupId());
        var port = UnitGroupMissionPorts.require(group);
        // Forecast facts must not invoke live movement admission: buying food
        // can itself require this economic forecast. An unresolved supply need
        // has no reliable ETA, whether the mission will refill here or continue.
        if (port.requestsJourneyStop(state, group) || mission.replenishment().isPresent()
                || io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority
                    .requiresPersonalStock(state, mission))
            return DeliveryEstimate.unknown(GROUP_HELD);
        if (group.phase() != UnitGroup.Phase.TRAVELLING || group.goalOrdinal() != mission.deliveryOrdinal())
            return DeliveryEstimate.unknown(MOVEMENT_PENDING);
        if (mission.supplies().isEmpty()) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
        var proof = mission.supplies().orElseThrow().journey();
        var path = mission.organization().itinerary() == TransportOrganization.Itinerary.SOURCE_DELIVERY
                ? proof.outward() : proof.returning();
        if (!path.destination().equals(mission.deliveryRendezvous())) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
        long remaining = 0, ticksPerEdge = port.travelPolicy(state, group).ticksPerEdge();
        for (var member : group.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor.condition().status() != ActorLifeStatus.ALIVE) return DeliveryEstimate.unknown(GROUP_HELD);
            if (state.humanPopulation().meals().containsKey(member.actorId())) return DeliveryEstimate.unknown(GROUP_HELD);
            // Persisted HOT poses are handoff/checkpoint evidence, not a live velocity or
            // proof that the native navigator is making progress. Never fabricate that evidence.
            if (ActorBodyAuthority.retainsPhysicalCustody(state, member.actorId()))
                return DeliveryEstimate.unknown(PHYSICAL_PROGRESS_REQUIRED);
            var position = actor.supportingSurface();
            var movement = state.actorMovements().get(member.actorId());
            if (movement == null && !position.equals(group.journey().orElseThrow().stations().get(member.actorId())))
                return DeliveryEstimate.unknown(MOVEMENT_PENDING);
            if (movement != null && (!(movement.context() instanceof ActorMovementContext.GroupLeg leg)
                    || !leg.groupId().equals(group.id()))) return DeliveryEstimate.unknown(GROUP_HELD);
            if (movement != null && movement.coldTravel().isEmpty()) return DeliveryEstimate.unknown(MOVEMENT_PENDING);
            long segmentEdges = 0;
            if (movement != null) {
                var travel = movement.coldTravel().orElseThrow();
                if (ActorMovementProjection.firstKnownBarrier(state, travel) >= 0) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
                ticksPerEdge = Math.max(ticksPerEdge, travel.ticksPerEdge());
                // Include the accepted local detour, then the retained itinerary tail.
                // With no as-of clock input, retain the whole active segment conservatively.
                segmentEdges = travel.route().size() - 1L;
                position = travel.route().getLast();
            }
            var distance = RouteRemainingDistance.edges(path, position);
            if (distance.isEmpty()) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
            remaining = Math.max(remaining, Math.addExact(segmentEdges, distance.orElseThrow()));
        }
        return timing(state, Math.multiplyExact(remaining, ticksPerEdge));
    }
    private static DeliveryEstimate single(FrontierWorldState state, Shipment shipment) {
        if (ActorBodyAuthority.retainsPhysicalCustody(state, shipment.execution().actorId()))
            return DeliveryEstimate.unknown(PHYSICAL_PROGRESS_REQUIRED);
        var movement = state.actorMovements().get(shipment.execution().actorId());
        if (movement == null || !movement.executionId().equals(shipment.execution())) return DeliveryEstimate.unknown(MOVEMENT_PENDING);
        // A bounded intermediate leg is not an itinerary to the receiver.
        if (movement.coldTravel().isEmpty()) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
        var travel = movement.coldTravel().orElseThrow();
        if (ActorMovementProjection.firstKnownBarrier(state, travel) >= 0) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
        if (!travel.route().getLast().equals(shipment.receiver().station())) return DeliveryEstimate.unknown(ROUTE_UNKNOWN);
        return timing(state, Math.multiplyExact(travel.route().size() - 1L, travel.ticksPerEdge()));
    }
    private static DeliveryEstimate timing(FrontierWorldState state, long travelTicks) {
        var rules = state.bootstrap().ruleset().expedition();
        long handling = Math.addExact(rules.destinationWorkTicks(), state.bootstrap().ruleset().cadence().transportReviewInterval());
        long margin = Math.ceilDiv(Math.multiplyExact(Math.addExact(travelTicks, handling), rules.durationMarginPermille()), 1000L);
        return new DeliveryEstimate(ROUTED, Optional.of(new DeliveryEstimate.Timing(travelTicks, handling, margin)));
    }
}
