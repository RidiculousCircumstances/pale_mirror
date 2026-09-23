package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskStatus;
import java.util.HashSet;
import java.util.List;

/** Strategy owns compaction of terminal tasks and their exact scheduled continuations. */
public final class StrategicScheduleRetirement {
    private StrategicScheduleRetirement() { }

    public static List<ScheduledAction> retiredBy(FrontierWorldState previous, FrontierWorldState next,
                                                 java.util.function.Supplier<List<ScheduledAction>> pending) {
        if (previous.strategicPlans() == next.strategicPlans()) return List.of();
        var retired = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (var task : previous.strategicPlans().tasks().values()) {
            if (next.strategicPlans().tasks().containsKey(task.id())) continue;
            if (task.status() != StrategicTaskStatus.COMPLETED && task.status() != StrategicTaskStatus.BLOCKED) {
                throw new IllegalArgumentException("cannot compact a nonterminal strategic task: " + task.id().value());
            }
            retired.add(task.id());
        }
        // Exact relationship comparison, not an ID-prefix or kind-based owner inference.
        return retired.isEmpty() ? List.of() : pending.get().stream()
                .filter(action -> retired.contains(action.subject())).toList();
    }
}
