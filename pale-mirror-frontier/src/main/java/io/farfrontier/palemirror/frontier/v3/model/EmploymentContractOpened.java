package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Durable opening of a work agreement and its matching resident financial account. */
public record EmploymentContractOpened(EmploymentContract contract) implements FrontierPayload {
    public EmploymentContractOpened { Objects.requireNonNull(contract, "employment contract"); }
    @Override public String type() { return "frontier.employment_contract_opened"; }
}
