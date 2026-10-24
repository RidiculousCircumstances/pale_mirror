package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Pure fixtures use the same calculation and publication owners as scheduled reviews. */
public final class AcceptedEconomyFixture {
    public static FrontierWorldState publish(FrontierWorldState state, SubjectId home, long at) {
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var domestic = SettlementEconomicEvaluation.domestic(state, participant, at);
        if (domestic.view().supplyPlan().isEmpty()) return state;
        return SettlementEconomicPublication.apply(state, home, SettlementEconomicPlanPreparation.prepare(state, participant, domestic, at), at);
    }
    private AcceptedEconomyFixture() { }
}
