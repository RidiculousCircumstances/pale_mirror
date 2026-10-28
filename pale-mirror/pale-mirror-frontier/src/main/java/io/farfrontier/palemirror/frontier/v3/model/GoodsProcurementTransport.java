package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionCrewAdmission;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportAsset;
import io.farfrontier.palemirror.frontier.v3.model.market.FundedCargoLot;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import io.farfrontier.palemirror.frontier.v3.model.supply.LotTransportCost;
import java.util.*;

/** Shared material/funding forecast for both delivery modes. It never grants
 * workers, a route or stock: exact logistics admission rechecks those owners. */
public final class GoodsProcurementTransport {
    public record Quote(int quantity, long expense, long cashFreight, long deliveryTicks, long journeyTicks,
                        GoodsParticipant organizer, GoodsTradeFulfillment.Mode mode, LotTransportCost transport, long workerTicks) {
        public long perItem() { return Math.ceilDiv(expense, quantity); }
        public long economicPerItem() { return Math.ceilDiv(transport.quote(quantity).economic(), quantity); }
    }
    private record Query(SubjectId source, String item, int requested, long price, long budget, long now, long deadline) { }
    private record Crew(SubjectId home, int count, Optional<TransportAsset> asset, long duration, long now) { }
    /** Snapshot-local facts shared by every hypothetical parcel size, not a crew reservation. */
    private record Context(GoodsTradeFulfillment.Mode mode, GoodsParticipant organizer,
            ProcurementTravelForecast.Estimate forecast, List<SubjectId> people, long crewAt) { }
    private static final ImmutableInputView<ImmutableResultView<Query, Optional<Quote>>> QUOTES = new ImmutableInputView<>();
    private static final ImmutableInputView<BoundedValueMemo<Crew, Boolean>> CREWS = new ImmutableInputView<>();
    private GoodsProcurementTransport() { }
    public static Optional<Quote> best(FrontierWorldState state, GoodsParticipant seller, SubjectId source, GoodsParticipant buyer,
            String item, int requested, long price, long budget, long now) {
        return best(state, seller, source, buyer, item, requested, price, budget, now, Long.MAX_VALUE);
    }
    public static Optional<Quote> best(FrontierWorldState state, GoodsParticipant seller, SubjectId source, GoodsParticipant buyer,
            String item, int requested, long price, long budget, long now, long deadline) {
        if (requested < 1 || price < 1 || budget < 0 || now < 0 || deadline < 0) throw new IllegalArgumentException("invalid procurement envelope");
        return QUOTES.get(List.of(state), () -> new ImmutableResultView<Query, Optional<Quote>>(512))
                .get(List.of(seller, buyer), new Query(source, item, requested, price, budget, now, deadline),
                        () -> calculate(state, seller, source, buyer, item, requested, price, budget, now, deadline));
    }
    private static boolean preferred(FrontierWorldState state, Quote candidate, Quote incumbent,
            String item, int requested, long price, long deadline) {
        var candidateId = new SubjectId("advice:candidate"); var incumbentId = new SubjectId("advice:incumbent");
        var a = new ProcurementSelection.Offer(candidateId, Map.of(item, new ProcurementSelection.Line(candidate.quantity(), new FixedScalar(price))),
                new FixedScalar(candidate.expense()), candidate.deliveryTicks(), candidate.workerTicks(), ProcurementSelection.Departure.READY);
        var b = new ProcurementSelection.Offer(incumbentId, Map.of(item, new ProcurementSelection.Line(incumbent.quantity(), new FixedScalar(price))),
                new FixedScalar(incumbent.expense()), incumbent.deliveryTicks(), incumbent.workerTicks(), ProcurementSelection.Departure.READY);
        long labour = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing()
                .map(io.farfrontier.palemirror.frontier.v3.model.supply.SourcingRules::labourValuePerTick).orElse(0L);
        return new ProcurementSelection().preferred(new ProcurementSelection.Requirement(item, requested, deadline), List.of(a, b), labour)
                .orElseThrow().id().equals(candidateId);
    }
    private static Optional<Quote> calculate(FrontierWorldState state, GoodsParticipant seller, SubjectId source, GoodsParticipant buyer,
            String item, int requested, long price, long budget, long now, long deadline) {
        Quote best = null;
        int size = GoodsTransportOptions.parcelSize(state, item);
        int funded = FundedCargoLot.quantity(Math.min(requested, GoodsTransportOptions.maximumQuantity(state, item)), price, 0, budget);
        if (funded == 0 || !GoodsParticipantStorage.eligible(state, seller, source, item)) return Optional.empty();
        var contexts = contexts(state, seller, source, buyer, item, now);
        // Every nominal size above the goods-only funding cap produced the same
        // funded load. Keep that load once and all lower parcel breakpoints.
        // Candidate quantities never exceed either requirement, so ranking is unchanged.
        for (int quantity = funded; quantity > 0; quantity = ((quantity - 1) / size) * size) {
            var candidate = calculateQuantity(state, contexts, item, quantity, price, budget, now, deadline);
            if (candidate.isPresent() && (best == null || preferred(state, candidate.orElseThrow(), best, item, requested, price, deadline)))
                best = candidate.orElseThrow();
        }
        return Optional.ofNullable(best);
    }
    private static List<Context> contexts(FrontierWorldState state, GoodsParticipant seller, SubjectId source,
            GoodsParticipant buyer, String item, long now) {
        var contexts = new ArrayList<Context>();
        for (var mode : GoodsTradeFulfillment.Mode.values()) {
            var organizer = mode == GoodsTradeFulfillment.Mode.BUYER_COLLECTION ? buyer : seller;
            var itinerary = mode == GoodsTradeFulfillment.Mode.BUYER_COLLECTION
                    ? TransportOrganization.Itinerary.DESTINATION_COLLECTION : TransportOrganization.Itinerary.SOURCE_DELIVERY;
            var timing = ProcurementTravelForecast.read(state, source, item, buyer, itinerary);
            if (timing.isEmpty()) continue;
            var forecast = timing.orElseThrow();
            var people = state.humanPopulation().residentsIn(organizer.settlementId()).stream()
                    .filter(r -> state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE)
                    .map(ResidentProfile::id).sorted().toList();
            // Undated reserve projections can ask about an older planning instant.
            // This quote uses retained current people, never rewinds their nutrition;
            // exact departure admission still requires its actual canonical instant.
            long crewAt = Math.max(now, people.stream().mapToLong(id -> state.humanPopulation().nutrition(id).lastEvaluatedTick()).max().orElse(now));
            contexts.add(new Context(mode, organizer, forecast, people, crewAt));
        }
        return List.copyOf(contexts);
    }
    private static Optional<Quote> calculateQuantity(FrontierWorldState state, List<Context> contexts,
            String item, int requested, long price, long budget, long now, long deadline) {
        Quote best = null;
        for (var context : contexts) {
            var mode = context.mode(); var organizer = context.organizer(); var forecast = context.forecast();
            var people = context.people(); long crewAt = context.crewAt();
            int size = GoodsTransportOptions.parcelSize(state, item);
            int quantity = requested;
            // Shrinking an affordable lot can reduce its crew. Each iteration
            // strictly reduces the load within the declared maximum cargo size.
            while (quantity > 0) {
                int parcels = Math.ceilDiv(quantity, size); int smaller = 0;
                for (var option : GoodsTransportOptions.forLoad(state, organizer, forecast.journeyTicks(), parcels, people.size(), now)) {
                    long cost = option.cost().resourceExpense().raw();
                    long cash = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? cost : 0;
                    int affordable = FundedCargoLot.quantity(quantity, price, cash, budget);
                    if (affordable < quantity) { smaller = Math.max(smaller, affordable); continue; }
                    var crew = new Crew(organizer.settlementId(), option.workers(), option.animal(), forecast.journeyTicks(), crewAt);
                    if (!CREWS.get(List.of(state), () -> new BoundedValueMemo<Crew, Boolean>(512)).get(crew,
                            () -> !ExpeditionCrewAdmission.select(state, crew.home(), people, crew.count(), crew.asset(),
                                    crew.duration(), crew.now(), ids -> true).isEmpty())) continue;
                    long labour = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing()
                            .map(io.farfrontier.palemirror.frontier.v3.model.supply.SourcingRules::labourValuePerTick).orElse(0L);
                    var curve = new LotTransportCost(List.of(new LotTransportCost.Option(quantity, quantity,
                            option.economicCost(labour), cash)));
                    var quote = new Quote(quantity, cost, cash, forecast.deliveryTicks(), forecast.journeyTicks(), organizer, mode, curve, option.cost().workerTicks());
                    if (best == null || preferred(state, quote, best, item, requested, price, deadline)) best = quote;
                }
                if (smaller == 0) break;
                quantity = smaller;
            }
        }
        return Optional.ofNullable(best);
    }
}
