package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Persistable future work ordered by due instant, priority, subject and stable ID. */
public record ScheduledAction(
        ScheduleId id,
        SimInstant dueAt,
        int priority,
        SubjectId subject,
        String kind,
        int weight
) implements Comparable<ScheduledAction> {
    public ScheduledAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dueAt, "due at");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(kind, "kind");
        if (!kind.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("scheduled action kind is invalid: " + kind);
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("scheduled action weight must be positive");
        }
    }

    @Override
    public int compareTo(ScheduledAction other) {
        int byDue = dueAt.compareTo(other.dueAt);
        if (byDue != 0) {
            return byDue;
        }
        int byPriority = Integer.compare(other.priority, priority);
        if (byPriority != 0) {
            return byPriority;
        }
        int bySubject = subject.compareTo(other.subject);
        if (bySubject != 0) {
            return bySubject;
        }
        return id.compareTo(other.id);
    }
}
