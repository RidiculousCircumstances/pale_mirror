package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;
import java.util.function.Supplier;

/** Composition supplies timer ownership; physical body authority knows no resident/job timers. */
@FunctionalInterface
interface ActorScheduleRetirement {
    List<ScheduledAction> retiredBy(FrontierWorldState before, FrontierWorldState after,
                                  Supplier<List<ScheduledAction>> pending);
}
