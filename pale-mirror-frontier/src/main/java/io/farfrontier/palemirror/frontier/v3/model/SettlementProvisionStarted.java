package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

public record SettlementProvisionStarted(SettlementProvision provision) implements FrontierPayload {
    public SettlementProvisionStarted { Objects.requireNonNull(provision, "settlement provision"); }
    @Override public String type() { return "frontier.settlement_provision_started_v2"; }
}
