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
    static Review review(FrontierWorldState state, ScheduledAction action, SimInstant at) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()), "economic forecast owner");
        var evaluation = SettlementEconomicEvaluation.domestic(state, participant, at.ticks());
        var prices = GoodsParticipantPricing.review(state, participant, at.ticks(), evaluation);
        if (!prices.equals(participant.prices())) {
            state = GoodsParticipantPricing.applyCommitted(state, participant.party().id(),
                    new GoodsParticipantPricesReviewed(participant.reviewRevision(), at.ticks(), prices), at.ticks());
            participant = state.companies().goodsTrade().participants().participants().get(action.subject());
            evaluation = SettlementEconomicEvaluation.domestic(state, participant, at.ticks());
        }
        var publication = evaluation.view().supplyPlan().isPresent()
                ? Optional.of(SettlementEconomicPlanPreparation.prepare(state, participant, evaluation, at.ticks())) : Optional.<SettlementEconomicPlan>empty();
        return new Review(at.ticks(), prices, publication);
    }

    static SettlementEconomicPlan prepare(FrontierWorldState state, GoodsParticipant participant,
            GoodsParticipantAssessment.Assessment domestic, long now) {
        var registry = state.companies().goodsTrade().participants();
        var input = registry.economicState(participant.party().id());
        long next = Math.addExact(now, state.bootstrap().ruleset().goodsTrade().reviewInterval());
        for (var order : state.companies().goodsTrade().orders().values()) {
            if ((order.party().equals(participant.party()) || order.counterparty().equals(participant.party())) && order.expiresAtTick() > now)
                next = Math.min(next, order.expiresAtTick());
        }
        long revision = Math.incrementExact(input.accepted().map(SettlementEconomicPlan::revision).orElse(0L));
        var completion = SettlementEconomicEvaluation.complete(state, domestic, input.inputRevision(), revision, Math.addExact(now, Math.multiplyExact(2,
                state.bootstrap().ruleset().goodsTrade().reviewInterval())));
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
