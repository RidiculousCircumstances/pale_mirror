package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Contains the accepted decision: replay validates its envelope and never runs a solver. */
public record SettlementEconomicPlanPublished(SettlementEconomicPlan plan) implements FrontierPayload {
    public SettlementEconomicPlanPublished { Objects.requireNonNull(plan); }
    @Override public String type() { return "frontier.settlement_economic_plan_published"; }
}
