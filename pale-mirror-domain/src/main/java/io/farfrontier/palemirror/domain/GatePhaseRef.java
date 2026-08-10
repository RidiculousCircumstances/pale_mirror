package io.farfrontier.palemirror.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** One ordered logical phase in a source-owned clearance gate. */
public record GatePhaseRef(String id, List<String> requiredPartIds) {
    public GatePhaseRef {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Gate phase id must not be blank");
        requiredPartIds = List.copyOf(Objects.requireNonNull(requiredPartIds, "requiredPartIds"));
        if (requiredPartIds.isEmpty()) throw new IllegalArgumentException("Gate phase must require at least one part");
        if (requiredPartIds.stream().anyMatch(value -> value == null || value.isBlank())
                || new LinkedHashSet<>(requiredPartIds).size() != requiredPartIds.size()) {
            throw new IllegalArgumentException("Gate phase part ids must be non-blank and unique");
        }
    }
}
