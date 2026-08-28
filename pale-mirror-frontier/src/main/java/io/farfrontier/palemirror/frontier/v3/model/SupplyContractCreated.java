package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;
public record SupplyContractCreated(SupplyContract contract) implements FrontierPayload {
    public SupplyContractCreated { Objects.requireNonNull(contract); if (contract.status() != ContractStatus.ORDERED) throw new IllegalArgumentException("new supply contract must be ordered"); }
    @Override public String type() { return "frontier.supply_contract_created"; }
}
