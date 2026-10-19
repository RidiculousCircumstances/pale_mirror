package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Quote cost/timing advice, never arrival coverage or route authority. No asynchronous cache dependency. */
final class ProcurementTravelForecast {
    record Estimate(long deliveryTicks, long labourTicks, long journeyTicks) { }
    static Optional<Estimate> read(FrontierWorldState state, GoodsTradeOrder offer, GoodsParticipant buyer) {
        return read(state, offer.containerId(), offer.itemKind(), buyer);
    }
    static Optional<Estimate> read(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId source,
                                   String item, GoodsParticipant buyer) {
        var destinations = GoodsParticipantStorage.containers(state, buyer, item);
        for (var mission : state.shipments().missions().values().stream().sorted(Comparator.comparing(TransportMission::id)).toList()) {
            if (!mission.sender().containerId().equals(source)
                    || !destinations.contains(mission.receiver().containerId()) || mission.supplies().isEmpty()
                    || mission.supplies().orElseThrow().needsReplan() || !state.unitGroups().groups().containsKey(mission.groupId())
                    || !GoodsTradeStateSupport.receivingCapacity(state, mission.receiver().containerId(), item, 1)) continue;
            var proof = mission.supplies().orElseThrow().journey();
            // This retained trip length is cost advice, not a fresh traversability
            // proof. Rewalking every physical edge here multiplies route validation
            // by every economic/worker query. Admission and movement still validate
            // their own current geometry; no quote becomes promised delivery here.
            var rules = state.bootstrap().ruleset().expedition();
            long duration = rules.plannedDuration(proof.outward().edges(), proof.returning().edges());
            long labour = Math.addExact(Math.multiplyExact(Math.addExact(proof.outward().edges(), proof.returning().edges()),
                    rules.ticksPerRouteEdge()), rules.destinationWorkTicks());
            return Optional.of(new Estimate(Math.addExact(duration, state.humanPopulation().schedule(buyer.settlementId()).dayTicks()), labour, duration));
        }
        // A first quote still needs a cost estimate. Endpoint distance is explicitly
        // provisional: this may rank a BUY intention, never create incoming stock,
        // accept a route or fund deadline-backed production. Matching must acquire
        // the real itinerary and supplies before admitting a contract/expedition.
        var origin = state.inventory().surfaces().get(source).position(state);
        long distance = destinations.stream()
                .filter(id -> GoodsTradeStateSupport.receivingCapacity(state, id, item, 1))
                .map(id -> state.inventory().surfaces().get(id).position(state))
                .mapToLong(target -> Math.abs((long) target.x() - origin.x()) + Math.abs((long) target.y() - origin.y())
                        + Math.abs((long) target.z() - origin.z())).min().orElse(-1);
        if (distance < 0) return Optional.empty();
        var rules = state.bootstrap().ruleset().expedition();
        long edges = Math.max(1, Math.ceilDiv(Math.multiplyExact(distance,
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().orElseThrow().provisionalDistancePermille()), 1000));
        long duration = rules.plannedDuration(edges, edges);
        long labour = Math.addExact(Math.multiplyExact(Math.multiplyExact(edges, 2), rules.ticksPerRouteEdge()), rules.destinationWorkTicks());
        return Optional.of(new Estimate(Math.addExact(duration, state.humanPopulation().schedule(buyer.settlementId()).dayTicks()), labour, duration));
    }
    private ProcurementTravelForecast() { }
}
