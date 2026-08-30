package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable terminal outcome for an ordered contract that never acquired cargo or a physical effect. */
public record SupplyContractAbandoned(SubjectId contractId) implements FrontierPayload {
    public SupplyContractAbandoned { Objects.requireNonNull(contractId, "contract id"); }
    @Override public String type() { return "frontier.supply_contract_abandoned"; }
}
