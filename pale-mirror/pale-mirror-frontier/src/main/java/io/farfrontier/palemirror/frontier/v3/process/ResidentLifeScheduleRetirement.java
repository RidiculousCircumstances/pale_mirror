package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.List;
import java.util.function.Supplier;

/** Death closes the exact resident's timers in the same event transaction. */
final class ResidentLifeScheduleRetirement {
    private ResidentLifeScheduleRetirement() { }

    static List<ScheduledAction> afterDeath(FrontierWorldState before, FrontierWorldState after,
                                            Supplier<List<ScheduledAction>> pending) {
        List<SubjectId> newlyDead = before.humanPopulation().residentIds().stream().filter(id -> {
            ActorLocation prior = before.actorLocations().get(id);
            ActorLocation current = after.actorLocations().get(id);
            return prior != null && current != null
                    && prior.condition().status() == ActorLifeStatus.ALIVE
                    && current.condition().status() == ActorLifeStatus.DEAD;
        }).toList();
        if (newlyDead.isEmpty()) return List.of();
        return pending.get().stream().filter(action -> newlyDead.contains(action.subject()))
                .filter(action -> action.kind().equals(ResidentNeedProcess.REVIEW)
                        || action.kind().equals(ResidentActivityProcess.REVIEW)
                        || action.kind().equals(ResidentMealProcess.PROGRESS)).toList();
    }
}
