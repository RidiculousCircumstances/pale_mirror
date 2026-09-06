package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable end of an exact resident's legal work authority. */
public record EmploymentContractTerminated(SubjectId contractId, SubjectId residentId,
                                    EmploymentTerminationReason reason) implements FrontierPayload {
    public EmploymentContractTerminated {
        Objects.requireNonNull(contractId, "employment contract");
        Objects.requireNonNull(residentId, "employment resident");
        Objects.requireNonNull(reason, "employment termination reason");
    }
    @Override public String type() { return "frontier.employment_contract_terminated"; }
}
