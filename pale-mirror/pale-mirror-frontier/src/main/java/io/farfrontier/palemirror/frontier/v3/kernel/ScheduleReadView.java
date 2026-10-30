package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Borrowed synchronous transaction view; the engine queue owns every derived index. */
public interface ScheduleReadView {
    List<ScheduledAction> snapshot();
    List<ScheduledAction> forSubjects(Set<SubjectId> subjects);
    Optional<ScheduledAction> action(ScheduleId id);

    /** Complete ingress image, used where no live engine queue exists. */
    static ScheduleReadView of(List<ScheduledAction> actions) {
        var queue = new ScheduledActionQueue();
        actions.forEach(queue::schedule);
        return queue;
    }
}
