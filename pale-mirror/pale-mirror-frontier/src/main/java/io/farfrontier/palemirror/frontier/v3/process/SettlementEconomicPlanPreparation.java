package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** One economic owner captures its decision and bases before publication. */
final class SettlementEconomicPlanPreparation {
    record Review(long atTick, Map<String, MarketPricePolicy.Quote> prices, Optional<SettlementEconomicPlan> publication) {
        Review { prices = Map.copyOf(prices); Objects.requireNonNull(publication); }
    }
    /** A dated domestic forecast is independent of another buyer's changing consent.
     * Rejected conditional exports may reuse this immutable part; complete() still
     * reads the newly captured peers and admission still checks current authority. */
    private record DomesticReview(long atTick, Map<String, MarketPricePolicy.Quote> prices,
            GoodsParticipantAssessment.Assessment evaluation) { }
    private record DomesticKey(ScheduledAction action, GoodsParticipant participant,
            SettlementEconomicPlanningState input, FrontierBootstrap bootstrap, Object navigation,
            Object physical, Map<SubjectId, GoodsParticipantPeers.Declaration> peers, long window) {
        @Override public boolean equals(Object other) {
            return other instanceof DomesticKey key && action.equals(key.action)
                    && participant.equals(key.participant) && input.sameNonMarketInputs(key.input)
                    && bootstrap == key.bootstrap && navigation == key.navigation && physical == key.physical
                    && peers.equals(key.peers) && window == key.window;
        }
        @Override public int hashCode() {
            // Equality checks complete declarations; hashing must not walk every
            // peer's known-counterparty list or the owner's price history.
            return Objects.hash(action, participant.party().id(), System.identityHashCode(bootstrap),
                    System.identityHashCode(navigation), System.identityHashCode(physical), window);
        }
    }
    private static final BoundedValueMemo<DomesticKey, DomesticReview> DOMESTIC = BoundedValueMemo.readMostly(128);

    static Review review(FrontierWorldState state, ScheduledAction action, SimInstant at) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()), "economic forecast owner");
        long interval = state.bootstrap().ruleset().goodsTrade().reviewInterval();
        var key = new DomesticKey(action, participant,
                state.companies().goodsTrade().participants().economicState(action.subject()), state.bootstrap(),
                state.navigationGeometry(), state.physicalDeltas(), GoodsParticipantPeers.read(state, participant),
                Math.floorDiv(at.ticks(), interval));
        var captured = state;
        var draft = DOMESTIC.get(key, () -> domestic(captured, action, at));
        // Cache residency grants no time validity, including historical branch reads.
        if (at.ticks() < draft.atTick() || at.ticks() >= Math.addExact(draft.atTick(), interval))
            draft = domestic(state, action, at);
        if (!draft.prices().equals(participant.prices())) {
            state = GoodsParticipantPricing.applyCommitted(state, participant.party().id(),
                    new GoodsParticipantPricesReviewed(participant.reviewRevision(), draft.atTick(), draft.prices()), draft.atTick());
            participant = state.companies().goodsTrade().participants().participants().get(action.subject());
        }
        var publication = draft.evaluation().view().supplyPlan().isPresent()
                ? Optional.of(prepare(state, participant, draft.evaluation(), draft.atTick(), at.ticks())) : Optional.<SettlementEconomicPlan>empty();
        return new Review(draft.atTick(), draft.prices(), publication);
    }
    private static DomesticReview domestic(FrontierWorldState state, ScheduledAction action, SimInstant at) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()));
        var evaluation = SettlementEconomicEvaluation.domestic(state, participant, at.ticks());
        var prices = GoodsParticipantPricing.review(state, participant, at.ticks(), evaluation);
        if (!prices.equals(participant.prices())) {
            state = GoodsParticipantPricing.applyCommitted(state, participant.party().id(),
                    new GoodsParticipantPricesReviewed(participant.reviewRevision(), at.ticks(), prices), at.ticks());
            participant = state.companies().goodsTrade().participants().participants().get(action.subject());
            evaluation = SettlementEconomicEvaluation.domestic(state, participant, at.ticks());
        }
        return new DomesticReview(at.ticks(), prices, evaluation);
    }

    static SettlementEconomicPlan prepare(FrontierWorldState state, GoodsParticipant participant,
            GoodsParticipantAssessment.Assessment domestic, long now) {
        return prepare(state, participant, domestic, now, now);
    }
    private static SettlementEconomicPlan prepare(FrontierWorldState state, GoodsParticipant participant,
            GoodsParticipantAssessment.Assessment domestic, long now, long marketAt) {
        var registry = state.companies().goodsTrade().participants();
        var input = registry.economicState(participant.party().id());
        long next = Math.addExact(now, state.bootstrap().ruleset().goodsTrade().reviewInterval());
        for (var order : state.companies().goodsTrade().orders().values()) {
            if (order.party().equals(participant.party()) && order.expiresAtTick() > marketAt)
                next = Math.min(next, order.expiresAtTick());
        }
        long revision = Math.incrementExact(input.accepted().map(SettlementEconomicPlan::revision).orElse(0L));
        var completion = SettlementEconomicEvaluation.complete(state, domestic, input.inputRevision(), revision, Math.addExact(now, Math.multiplyExact(2,
                state.bootstrap().ruleset().goodsTrade().reviewInterval())), marketAt);
        var complete = completion.assessment().view().supplyPlan().orElseThrow();
        var ceilings = new TreeMap<String, Long>();
        for (var item : complete.demands().keySet()) {
            long additional = complete.economy().map(plan -> plan.additionalProduction().get(item))
                    .orElseGet(() -> complete.require(item).production());
            ceilings.put(item, Math.addExact(ProductionStockDemand.coverage(state, participant.party().id(), item), additional));
        }
        var affected = new TreeMap<String, Set<String>>();
        for (var item : complete.demands().keySet()) affected.put(item,
                io.farfrontier.palemirror.frontier.v3.model.supply.ProductionReachability.consumers(
                        domestic.facts().orElseThrow().capacities(), item));
        var buyers = new TreeMap<SubjectId, Long>();
        for (var quote : completion.quotes()) {
            // Only an offered conditional quantity depends on this exact buyer
            // envelope. Unused alternatives remain dated forecast observations.
            buyers.put(quote.recipient().id(), registry.economicState(quote.recipient().id()).accepted()
                    .map(SettlementEconomicPlan::revision).orElse(0L));
        }
        return new SettlementEconomicPlan(revision,
                input.inputRevision(), now, next, domestic.view().supplyPlan().orElseThrow(), complete, ceilings, buyers, affected,
                CultivationEconomicEvaluation.evaluate(state, participant.party().id(), now), domestic.facts().orElseThrow().basis().needs(), completion.quotes());
    }
    private SettlementEconomicPlanPreparation() { }
}
