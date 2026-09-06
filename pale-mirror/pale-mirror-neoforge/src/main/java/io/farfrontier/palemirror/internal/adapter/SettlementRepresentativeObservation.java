package io.farfrontier.palemirror.internal.adapter;

import java.util.Objects;

import io.farfrontier.palemirror.domain.SettlementCohort;

public record SettlementRepresentativeObservation(String nativeId, SettlementCohort cohort) {
    public SettlementRepresentativeObservation {
        if (nativeId == null || nativeId.isBlank()) throw new IllegalArgumentException("Representative identity is required");
        Objects.requireNonNull(cohort, "cohort");
    }
}
