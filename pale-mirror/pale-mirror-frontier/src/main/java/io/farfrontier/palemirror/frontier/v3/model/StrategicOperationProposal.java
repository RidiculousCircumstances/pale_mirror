package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.Optional;

/** Immutable planning value, not a second durable objective or worker assignment. */
public record StrategicOperationProposal(StrategicObjectiveKind kind, Optional<InfectionCell> target, Optional<SubjectId> resourceSiteTarget,
                             Optional<SubjectId> operationTarget, Optional<BlockPosition> operationObservationPosition, long utility) {
        public StrategicOperationProposal {
            java.util.Objects.requireNonNull(kind); java.util.Objects.requireNonNull(target);
            java.util.Objects.requireNonNull(resourceSiteTarget); java.util.Objects.requireNonNull(operationTarget);
            java.util.Objects.requireNonNull(operationObservationPosition);
            if (utility < 0 || operationTarget.isPresent() != operationObservationPosition.isPresent()
                    || (kind == StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE) != resourceSiteTarget.isPresent())
                throw new IllegalArgumentException("operation proposal has inconsistent typed targets or utility");
        }
        public StrategicOperationProposal(StrategicObjectiveKind kind, Optional<InfectionCell> target, long utility) {
            this(kind, target, Optional.empty(), Optional.empty(), Optional.empty(), utility);
        }
        public StrategicOperationProposal(StrategicObjectiveKind kind, Optional<InfectionCell> target, Optional<SubjectId> resourceSiteTarget, long utility) {
            this(kind, target, resourceSiteTarget, Optional.empty(), Optional.empty(), utility);
        }
        public static final Comparator<StrategicOperationProposal> HIGHEST_UTILITY = Comparator.comparingLong(StrategicOperationProposal::utility).reversed()
                .thenComparing(StrategicOperationProposal::kind).thenComparing(value -> value.target().map(InfectionCell::x).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.target().map(InfectionCell::z).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.resourceSiteTarget().map(SubjectId::value).orElse(""))
                .thenComparing(value -> value.operationTarget().map(SubjectId::value).orElse(""))
                .thenComparing(value -> value.operationObservationPosition().map(BlockPosition::x).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.operationObservationPosition().map(BlockPosition::y).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.operationObservationPosition().map(BlockPosition::z).orElse(Integer.MIN_VALUE));
    }
