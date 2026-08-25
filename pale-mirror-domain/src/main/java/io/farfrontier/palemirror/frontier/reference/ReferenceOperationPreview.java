package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable source preview of a field-post formation. */
public record ReferenceOperationPreview(double personnel, double power, Map<ReferenceHumanUnitKind, Double> roles) {
    public ReferenceOperationPreview {
        Objects.requireNonNull(roles, "roles");
        roles = Collections.unmodifiableMap(new EnumMap<>(roles));
    }
}
