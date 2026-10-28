package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Dated market choice. The exact phase rederives only that candidate against
 * current consent, stock, money, workforce and geometry before admission. */
final class GoodsParticipantOpportunityAdvice {
    private GoodsParticipantOpportunityAdvice() { }
    static ScheduledAdvisory<FrontierWorldState> capture(FrontierWorldState state, ScheduledAction action, SimInstant at) {
        var owner = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()));
        var party = owner.party(); var bootstrap = state.bootstrap();
        var book = state.companies().goodsTrade().orders();
        var navigation = state.navigationGeometry();
        long input = state.companies().goodsTrade().participants().economicState(party.id()).inputRevision();
        return new ScheduledAdvisory<>() {
            private java.util.function.Supplier<GoodsParticipantProcess.PreparedDecision> calculation;
            @Override public Completion<FrontierWorldState> prepare() {
                if (calculation == null) calculation = GoodsParticipantProcess.prepareDecisionCalculation(state, action, at,
                        new SettlementEconomicPlanPreparation.Review(at.ticks(), owner.prices(), Optional.empty()), Optional.empty());
                var choice = calculation.get().choice();
                return new ScheduledAdvisory.Completion<FrontierWorldState>() {
                    private String rejection = "NONE";
                    public String rejectionReason() { return rejection; }
                    private Optional<ScheduledPreparation> reject(String reason) { rejection = reason; return Optional.empty(); }
                    public Optional<ScheduledPreparation> revalidate(FrontierWorldState current, SimInstant now) {
                        var participant = current.companies().goodsTrade().participants().participants().get(party.id());
                        if (current.bootstrap() != bootstrap) return reject("BOOTSTRAP");
                        if (participant == null || !participant.party().equals(party)) return reject("OWNER_IDENTITY");
                        if (now.compareTo(at) < 0) return reject("EXECUTION_TIME");
                        if (now.ticks() - at.ticks() >= bootstrap.ruleset().goodsTrade().reviewInterval()) return reject("EXPIRED");
                        // A resumed pending query must not keep asking its captured unknown
                        // terrain after the observation owner has supplied newer knowledge.
                        if (choice == GoodsTripPlanning.Disposition.AWAITING_NAVIGATION
                                && current.navigationGeometry() != navigation) return reject("GEOMETRY");
                        // An old absence must not erase a newly published opportunity.
                        // A selected dated quote still faces all current admission checks.
                        if (choice == GoodsTripPlanning.Disposition.NO_PURCHASE && (book != current.companies().goodsTrade().orders()
                                || input != current.companies().goodsTrade().participants().economicState(party.id()).inputRevision()))
                            return reject("CHANGED_EMPTY_MARKET");
                        rejection = "NONE";
                        var fresh = new SettlementEconomicPlanPreparation.Review(now.ticks(), participant.prices(), Optional.empty());
                        return Optional.of(() -> GoodsParticipantProcess.prepareDecision(current, action, now, fresh, Optional.of(choice)).completion());
                    }
                };
            }
        };
    }
}
