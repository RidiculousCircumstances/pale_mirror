package io.farfrontier.palemirror.frontier.v3.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        PhysicalPostcondition postcondition,
        Optional<PhysicalObservationId> postconditionObservationId
) {
    public PhysicalIntent {
        Objects.requireNonNull(id, "physical intent id");
        Objects.requireNonNull(kind, "physical intent kind");
        Objects.requireNonNull(status, "physical intent status");
        Objects.requireNonNull(causeSubjectId, "physical intent cause subject");
        subjectIds = List.copyOf(subjectIds);
        Objects.requireNonNull(origin, "physical intent origin");
        Objects.requireNonNull(postcondition, "physical intent postcondition");
        postconditionObservationId = Objects.requireNonNull(postconditionObservationId, "physical intent postcondition observation");
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
            case STRUCTURAL_REPAIR -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED) {
                    throw new IllegalArgumentException("structural repair must use zero radius and repair postcondition");
                }
            }
            case EXPLOSION -> {
                if (radiusBlocks == 0 || postcondition != PhysicalPostcondition.EXPLOSION_OBSERVED) {
                    throw new IllegalArgumentException("explosion must use positive radius and explosion postcondition");
                }
            }
            case SCENE_STRIKE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.SCENE_STRIKE_OBSERVED || subjectIds.size() != 2) {
                    throw new IllegalArgumentException("scene strike must bind one exact attacker and target without an area radius");
                }
            }
            case EXACT_ITEM_CONSUMPTION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED || subjectIds.size() != 2) {
                    throw new IllegalArgumentException("exact item consumption must bind one owner and one exact stack without an area radius");
                }
            }
        }
        if (status == PhysicalIntentStatus.CONFIRMED != postconditionObservationId.isPresent()) {
            throw new IllegalArgumentException("only confirmed physical intent has an observed postcondition");
        }
    }

    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          List<SubjectId> subjectIds, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition) {
        this(id, kind, status, causeSubjectId, subjectIds, origin, radiusBlocks, postcondition, Optional.empty());
    }

    public PhysicalIntent withStatus(PhysicalIntentStatus nextStatus, Optional<PhysicalObservationId> observationId) {
        return new PhysicalIntent(id, kind, nextStatus, causeSubjectId, subjectIds, origin, radiusBlocks, postcondition, observationId);
    }
}
