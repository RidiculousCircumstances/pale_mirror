package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming;

/** Quote cost/timing advice, never arrival coverage or route authority. No asynchronous cache dependency. */
public final class ProcurementTravelForecast {
    private record Query(SubjectId source, String item, TransportOrganization.Itinerary itinerary) { }
    private record Endpoints(SubjectId source, SubjectId receiver, TransportOrganization.Itinerary itinerary) { }
    private record Positions(BlockPosition source, BlockPosition receiver) { }
    private record JourneyInput(Endpoints endpoints, Optional<TransportJourneyForecastFacts.Journey> retained,
                                Optional<Positions> positions) { }
    private static final ImmutableInputView<ImmutableResultView<Query, Optional<Estimate>>> QUOTES = new ImmutableInputView<>();
    private static final ImmutableInputView<BoundedValueMemo<JourneyInput, Estimate>> JOURNEYS = new ImmutableInputView<>();
    public record Estimate(long deliveryTicks, long labourTicks, long journeyTicks) { }
    static Optional<Estimate> read(FrontierWorldState state, GoodsTradeOrder offer, GoodsParticipant buyer) {
        return read(state, offer.containerId(), offer.itemKind(), buyer);
    }
    static Optional<Estimate> read(FrontierWorldState state, SubjectId source, String item, GoodsParticipant buyer) {
        return read(state, source, item, buyer, TransportOrganization.Itinerary.SOURCE_DELIVERY);
    }
    static Optional<Estimate> read(FrontierWorldState state, SubjectId source, String item, GoodsParticipant buyer,
                                   TransportOrganization.Itinerary itinerary) {
        // Keep identical-draft quotation hits cheap; only journey cost facts
        // cross state boundaries. Receiving permission always uses the current draft.
        return QUOTES.get(List.of(state), () -> new ImmutableResultView<Query, Optional<Estimate>>(256))
                .get(List.of(buyer), new Query(source, item, itinerary), () -> select(state, source, item, buyer, itinerary));
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
        var retained = TransportJourneyForecastFacts.between(state, source, receiver);
        Optional<Positions> positions = Optional.empty();
        if (retained.isEmpty()) {
            var current = ContainerPositionFacts.all(state);
            positions = Optional.of(new Positions(Objects.requireNonNull(current.get(source), "forecast source position"),
                    Objects.requireNonNull(current.get(receiver), "forecast receiver position")));
        }
        var input = new JourneyInput(new Endpoints(source, receiver, itinerary), retained, positions);
        return JOURNEYS.get(List.of(state.bootstrap()), () -> new BoundedValueMemo<>(256))
                .get(input, () -> calculate(state, input));
    }
    private static Estimate calculate(FrontierWorldState state, JourneyInput input) {
        var itinerary = input.endpoints().itinerary();
        if (input.retained().isPresent()) {
            var proof = input.retained().orElseThrow();
            // Retained length is cost advice, not fresh traversability evidence.
            boolean sameDirection = proof.itinerary() == itinerary;
            return estimate(state, sameDirection ? proof.outward() : proof.returning(),
                    sameDirection ? proof.returning() : proof.outward(), itinerary);
        }
        var origin = input.positions().orElseThrow().source();
        var target = input.positions().orElseThrow().receiver();
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
