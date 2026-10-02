package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Closed read-model composition; operation owners declare their occupied facilities. */
public final class SettlementCommitmentComposition {
    public static final SettlementCommitmentAdmission ADMISSION = new SettlementCommitmentAdmission(List.of(
            state -> ProductionFacilityReservations.committed(state.productionJobs())));
    private SettlementCommitmentComposition() { }
}
