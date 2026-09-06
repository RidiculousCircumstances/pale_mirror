package io.farfrontier.palemirror.frontier.reference;

/**
 * Immutable source-equivalent row recorded by {@code World._record_history}.
 * Nullable ledger values mean the organ was created after that day's ecology
 * pass, matching Python's intentionally sparse diagnostic dictionary.
 */
public record ReferenceHiveEconomySnapshot(
        int day,
        int organId,
        Double openingBiomass,
        Double substrateIn,
        Double biomassIncome,
        Double samplesIn,
        Double maintenance,
        double biomass,
        double samples,
        double vitality
) { }
