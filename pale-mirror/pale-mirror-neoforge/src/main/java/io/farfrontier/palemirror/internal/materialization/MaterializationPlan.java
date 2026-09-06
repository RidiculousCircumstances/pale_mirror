package io.farfrontier.palemirror.internal.materialization;

import java.util.List;
import java.util.Objects;

/** Pure translation result; it has no reference to a Minecraft level or executor. */
public record MaterializationPlan(String policyId, String policyVersion, long desiredRevision,
                                  List<MaterializationOperation> operations) {
    public MaterializationPlan {
        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(policyVersion, "policyVersion");
        operations = List.copyOf(operations);
        if (operations.isEmpty()) throw new IllegalArgumentException("Materialization plan must contain operations");
    }
}
