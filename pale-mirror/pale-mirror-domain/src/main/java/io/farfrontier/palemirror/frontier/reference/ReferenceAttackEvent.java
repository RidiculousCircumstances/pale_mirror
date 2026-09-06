package io.farfrontier.palemirror.frontier.reference;

import java.util.Map;

/** A source-port attack arrival; the settlement/field resolver owns consequences. */
public record ReferenceAttackEvent(int swarmId, int settlementId, double power, Integer sourceOrganId,
                                   ReferenceBioformKind kind, Map<ReferenceBioformKind, Double> composition,
                                   ReferenceFormationPhase phase) {
    public ReferenceAttackEvent {
        composition = Map.copyOf(composition);
    }
}
