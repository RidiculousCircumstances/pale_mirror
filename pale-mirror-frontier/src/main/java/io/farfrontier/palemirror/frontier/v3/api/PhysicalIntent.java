package io.farfrontier.palemirror.frontier.v3.api;

import java.util.List;
import java.util.Objects;

/**
 * Durable, exact request for one external physical action. Its executor may run only after the
 * containing transaction is durable and must resolve the stated postcondition afterward.
 */
public record PhysicalIntent(
        PhysicalIntentId id,
        PhysicalIntentKind kind,
        PhysicalIntentStatus status,
        SubjectId causeSubjectId,
        List<SubjectId> subjectIds,
        FixedPosition origin,
        int radiusBlocks,
        PhysicalPostcondition postcondition
) {
    public PhysicalIntent {
        Objects.requireNonNull(id, "physical intent id");
        Objects.requireNonNull(kind, "physical intent kind");
        Objects.requireNonNull(status, "physical intent status");
        Objects.requireNonNull(causeSubjectId, "physical intent cause subject");
        subjectIds = List.copyOf(subjectIds);
        Objects.requireNonNull(origin, "physical intent origin");
        Objects.requireNonNull(postcondition, "physical intent postcondition");
        if (subjectIds.isEmpty() || subjectIds.size() > 32 || subjectIds.stream().distinct().count() != subjectIds.size()) {
            throw new IllegalArgumentException("physical intent must name one to thirty-two distinct subjects");
        }
        if (radiusBlocks < 0 || radiusBlocks > 64) throw new IllegalArgumentException("physical intent radius must be 0..64 blocks");
        switch (kind) {
            case CARGO_HANDOFF -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.CARGO_HANDOFF_OBSERVED) {
                    throw new IllegalArgumentException("cargo hand-off must use zero radius and cargo postcondition");
                }
            }
            case EXPLOSION -> {
                if (radiusBlocks == 0 || postcondition != PhysicalPostcondition.EXPLOSION_OBSERVED) {
                    throw new IllegalArgumentException("explosion must use positive radius and explosion postcondition");
                }
            }
        }
    }
}
