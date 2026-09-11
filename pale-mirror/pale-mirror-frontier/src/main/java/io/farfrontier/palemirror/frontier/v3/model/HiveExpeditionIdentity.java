package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Stable parent identity shared by cocoon assembly and its admitted settlement expedition. */
public final class HiveExpeditionIdentity {
    private HiveExpeditionIdentity() { }
    public static SubjectId forTask(SubjectId taskId) {
        Objects.requireNonNull(taskId, "expedition task");
        String value = taskId.value();
        if (!value.startsWith("task:")) throw new IllegalArgumentException("expedition task identity is invalid");
        return new SubjectId("operation:expedition-" + value.substring("task:".length()).replace(':', '-'));
    }
}
