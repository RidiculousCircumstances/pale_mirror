package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One exact, retained engineering crew working the current immutable route-construction cell.
 *
 * <p>The cursor is part of the durable cause.  A later cell therefore cannot accidentally claim
 * the prior crew lease or reuse an observation from it.</p>
 */
public record EngineeringWorkSceneCause(SubjectId projectId, int workCellIndex) implements SceneCause {
    public EngineeringWorkSceneCause {
        Objects.requireNonNull(projectId, "engineering scene project");
        if (workCellIndex < 0) throw new IllegalArgumentException("engineering scene work-cell index is negative");
    }

    @Override public SceneCauseKind kind() { return SceneCauseKind.ENGINEERING_WORKSITE; }
}
