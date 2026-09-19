package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.Objects;
import java.util.Optional;

/** Durable state change after executor admission, postcondition observation or restart inspection. */
public record PhysicalIntentTransition(PhysicalIntentId intentId, PhysicalIntentStatus status,
                                       Optional<PhysicalEffectObservation> observation,
                                       Optional<PhysicalIntentRetirementProof> retirementProof,
                                       Optional<DiagnosticTuple> diagnostic) implements FrontierPayload {
    public PhysicalIntentTransition {
        Objects.requireNonNull(intentId, "physical intent id");
        Objects.requireNonNull(status, "physical intent status");
        observation = Objects.requireNonNull(observation, "physical observation");
        retirementProof = Objects.requireNonNull(retirementProof, "physical retirement proof");
        diagnostic = Objects.requireNonNull(diagnostic, "physical transition diagnostic");
        if (status == PhysicalIntentStatus.PREPARED) throw new IllegalArgumentException("physical intent cannot transition to prepared");
        if (status == PhysicalIntentStatus.CONFIRMED != observation.isPresent()) throw new IllegalArgumentException("only confirmed transition has observation evidence");
        if (status == PhysicalIntentStatus.RUNNING && retirementProof.isPresent()) throw new IllegalArgumentException("nonterminal physical transition cannot carry retirement proof");
        if (status != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && diagnostic.isPresent()) throw new IllegalArgumentException("only recovery-unknown transition may carry a diagnostic tuple");
    }
    public PhysicalIntentTransition(PhysicalIntentId intentId, PhysicalIntentStatus status,
                                    Optional<PhysicalEffectObservation> observation) {
        this(intentId, status, observation, Optional.empty(), Optional.empty());
    }
    public PhysicalIntentTransition withRetirementProof(PhysicalIntentRetirementProof proof) {
        return new PhysicalIntentTransition(intentId, status, observation, Optional.of(Objects.requireNonNull(proof, "retirement proof")), diagnostic);
    }
    public PhysicalIntentTransition withRecoveryDiagnostic(DiagnosticTuple stampedDiagnostic) {
        if (status != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) throw new IllegalArgumentException("only recovery-unknown transition accepts a diagnostic tuple");
        return new PhysicalIntentTransition(intentId, status, observation, retirementProof, Optional.of(Objects.requireNonNull(stampedDiagnostic, "recovery diagnostic")));
    }
    @Override public String type() { return "frontier.physical_intent_transition"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
