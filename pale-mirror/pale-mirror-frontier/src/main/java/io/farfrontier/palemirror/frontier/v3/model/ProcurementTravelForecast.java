package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming;

/** Quote cost/timing advice, never arrival coverage or route authority. No asynchronous cache dependency. */
public final class ProcurementTravelForecast {
    private record Query(SubjectId source, String item, GoodsParticipant buyer, TransportOrganization.Itinerary itinerary) { }
    private record Endpoints(SubjectId source, SubjectId receiver, TransportOrganization.Itinerary itinerary) { }
    private static final ImmutableInputView<BoundedValueMemo<Query, Optional<Estimate>>> QUOTES = new ImmutableInputView<>();
    private static final ImmutableInputView<BoundedValueMemo<Endpoints, Estimate>> JOURNEYS = new ImmutableInputView<>();
    private static final ImmutableInputView<List<TransportMission>> ORDERED = new ImmutableInputView<>();
    public record Estimate(long deliveryTicks, long labourTicks, long journeyTicks) { }
    static Optional<Estimate> read(FrontierWorldState state, GoodsTradeOrder offer, GoodsParticipant buyer) {
        return read(state, offer.containerId(), offer.itemKind(), buyer);
    }
    static Optional<Estimate> read(FrontierWorldState state, SubjectId source, String item, GoodsParticipant buyer) {
        return read(state, source, item, buyer, TransportOrganization.Itinerary.SOURCE_DELIVERY);
    }
    static Optional<Estimate> read(FrontierWorldState state, SubjectId source, String item, GoodsParticipant buyer,
                                   TransportOrganization.Itinerary itinerary) {
        return QUOTES.get(List.of(state), () -> new BoundedValueMemo<Query, Optional<Estimate>>(256))
                .get(new Query(source, item, buyer, itinerary), () -> select(state, source, item, buyer, itinerary));
    }
    private static Optional<Estimate> select(FrontierWorldState state, SubjectId source, String item, GoodsParticipant buyer,
            TransportOrganization.Itinerary itinerary) {
        return GoodsParticipantStorage.containers(state, buyer, item).stream()
                .filter(id -> GoodsTradeStateSupport.receivingCapacity(state, id, item, 1))
                .map(id -> between(state, source, id, itinerary))
                .min(Comparator.comparingLong(Estimate::journeyTicks));
    }
    /** Exact endpoints, provisional journey. Comparison must not acquire detailed
     * routes for every quotation; selected transport admission owns that proof. */
    public static Estimate between(FrontierWorldState state, SubjectId source, SubjectId receiver,
                                   TransportOrganization.Itinerary itinerary) {
        // Reservations and quote drafts change stock/funding, not journey length.
        // Mobile surfaces still invalidate on the exact actor locations; retained
        // mission proof remains conditional on its live group and immutable rules.
        return JOURNEYS.get(List.of(state.bootstrap(), state.shipments().missions(), state.unitGroups().groups(),
                        state.inventory().surfaces(), state.actorLocations()), () -> new BoundedValueMemo<Endpoints, Estimate>(256))
                .get(new Endpoints(source, receiver, itinerary), () -> calculate(state, source, receiver, itinerary));
    }
    private static Estimate calculate(FrontierWorldState state, SubjectId source, SubjectId receiver,
            TransportOrganization.Itinerary itinerary) {
        var ordered = ORDERED.get(List.of(state.shipments().missions()), () -> state.shipments().missions().values().stream()
                .sorted(Comparator.comparing(TransportMission::id)).toList());
        for (var mission : ordered) {
            if (!mission.sender().containerId().equals(source) || !mission.receiver().containerId().equals(receiver)
                    || mission.supplies().isEmpty() || mission.supplies().orElseThrow().needsReplan()
                    || !state.unitGroups().groups().containsKey(mission.groupId())) continue;
            var proof = mission.supplies().orElseThrow().journey();
            // Retained length is cost advice, not fresh traversability evidence.
            boolean sameDirection = mission.organization().itinerary() == itinerary;
            return estimate(state, sameDirection ? proof.outward().edges() : proof.returning().edges(),
                    sameDirection ? proof.returning().edges() : proof.outward().edges(), itinerary);
        }
        var origin = state.inventory().surfaces().get(source).position(state);
        var target = state.inventory().surfaces().get(receiver).position(state);
        long distance = Math.abs((long) target.x() - origin.x()) + Math.abs((long) target.y() - origin.y())
                + Math.abs((long) target.z() - origin.z());
        long edges = Math.max(1, Math.ceilDiv(Math.multiplyExact(distance,
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().map(rules -> rules.provisionalDistancePermille()).orElse(1000)), 1000));
        return estimate(state, edges, edges, itinerary);
    }
    private static Estimate estimate(FrontierWorldState state, long outward, long returning,
                                     TransportOrganization.Itinerary itinerary) {
        var rules = state.bootstrap().ruleset().expedition();
        var timing = ExpeditionTiming.assess(rules, outward, returning, itinerary);
        long labour = Math.addExact(Math.multiplyExact(Math.addExact(outward, returning), rules.ticksPerRouteEdge()), rules.destinationWorkTicks());
        return new Estimate(timing.deliveryTicks(), labour, timing.durationTicks());
    }
    private ProcurementTravelForecast() { }
}
