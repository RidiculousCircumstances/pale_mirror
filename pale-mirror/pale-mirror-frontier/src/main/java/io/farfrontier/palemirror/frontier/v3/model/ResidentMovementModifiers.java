package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** Resident-owned base and source-addressed movement factors; activity chooses only a gait. */
public record ResidentMovementModifiers(int basePermille, Map<SubjectId, Integer> factors) {
    public ResidentMovementModifiers {
        if (basePermille < 250 || basePermille > 4000 || factors.size() > ResidentCharacteristics.MAX_MODIFIERS)
            throw new IllegalArgumentException("invalid resident movement modifiers");
        factors = ImmutableLookupMap.copyOf(factors);
        factors.forEach((source, factor) -> {
            Objects.requireNonNull(source);
            if (factor < 250 || factor > 4000) throw new IllegalArgumentException("invalid movement factor");
        });
    }
    public static ResidentMovementModifiers initial() { return new ResidentMovementModifiers(1000, Map.of()); }
    public double multiplier() {
        double value = basePermille / 1000.0;
        for (var entry : factors.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) value *= entry.getValue() / 1000.0;
        return Math.clamp(value, 0.25, 4.0);
    }
    public ResidentMovementModifiers with(SubjectId source, int factor) {
        return new ResidentMovementModifiers(basePermille, ImmutableLookupMap.withEntry(factors, source, factor));
    }
    public ResidentMovementModifiers without(SubjectId source) {
        if (!factors.containsKey(source)) throw new IllegalArgumentException("unknown movement modifier source");
        return new ResidentMovementModifiers(basePermille, ImmutableLookupMap.withoutEntry(factors, source));
    }
}
