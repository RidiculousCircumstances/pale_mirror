package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** One exact disjoint child allocation of a retained settlement expedition. */
public record SettlementAssaultFront(SubjectId id, SubjectId expeditionId, SubjectId tacticalPlanId,
                                    long tacticalPlanEpoch, List<SubjectId> actorIds) {
    public SettlementAssaultFront {
        id = Objects.requireNonNull(id, "assault front"); expeditionId = Objects.requireNonNull(expeditionId, "front expedition");
        tacticalPlanId = Objects.requireNonNull(tacticalPlanId, "front tactical plan"); actorIds = List.copyOf(actorIds);
        if (tacticalPlanEpoch < 0L || actorIds.isEmpty() || actorIds.stream().distinct().count() != actorIds.size()) {
            throw new IllegalArgumentException("assault front allocation is invalid");
        }
    }
}
