package io.farfrontier.palemirror.internal.adapter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Version-pinned current-phase presentation emitted by one source adapter. */
public record SourceGateLayout(String planId, String planVersion, List<SourceGatePartSpec> parts) {
    public SourceGateLayout {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(planVersion, "planVersion");
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (planId.isBlank() || planVersion.isBlank() || parts.isEmpty()
                || parts.stream().map(SourceGatePartSpec::slotId).collect(java.util.stream.Collectors.toSet()).size() != parts.size()) {
            throw new IllegalArgumentException("Gate layout must be pinned and contain unique parts");
        }
    }
}
