package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Read-only admission plan for the already assembled exact construction crew. */
public record EngineeringWorkSceneCandidate(SubjectId projectId, int workCellIndex, BlockPosition workCell,
                                            Map<SubjectId, BlockPosition> memberPositions) {
    public EngineeringWorkSceneCandidate {
        Objects.requireNonNull(projectId, "engineering scene project");
        if (workCellIndex < 0) throw new IllegalArgumentException("engineering scene work-cell index is negative");
        Objects.requireNonNull(workCell, "engineering scene work cell");
        Map<SubjectId, BlockPosition> copy = new LinkedHashMap<>(Objects.requireNonNull(memberPositions, "engineering scene members"));
        if (copy.keySet().stream().anyMatch(Objects::isNull) || copy.values().stream().anyMatch(Objects::isNull)
                || copy.size() < EngineeringRecoveryTeam.MIN_MEMBERS || copy.size() > EngineeringRecoveryTeam.MAX_MEMBERS
                || copy.values().stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("engineering scene requires bounded exact members at distinct positions");
        }
        memberPositions = Collections.unmodifiableMap(copy);
    }
}
