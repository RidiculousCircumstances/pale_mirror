package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one exact local treatment before its physical supply is consumed. */
public record MedicalTreatmentStarted(MedicalEvacuationOperation operation,
                                      io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public MedicalTreatmentStarted {
        Objects.requireNonNull(operation, "medical treatment operation"); Objects.requireNonNull(executions, "medical participant executions");
        executions.requireDeclaration(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEDICAL_TREATMENT,
                operation.id(), MedicalExecutionAuthority.participants(operation));
    }
    @Override public String type() { return "frontier.medical_treatment_started"; }
}
