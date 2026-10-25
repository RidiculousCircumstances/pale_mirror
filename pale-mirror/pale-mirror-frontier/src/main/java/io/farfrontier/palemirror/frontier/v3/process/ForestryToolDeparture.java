package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryWork;
import java.util.*;
import java.util.function.Supplier;

/** The forestry owner withdraws an unacquired tool offer atomically with an external stock change. */
final class ForestryToolDeparture implements ExactItemDepartureReactions.Consumer {
    @Override public ExactItemDepartureReactions.Owner owner() { return ExactItemDepartureReactions.Owner.FORESTRY; }
    @Override public FrontierWorldState beforeInventory(FrontierWorldState state, SubjectId item, ExactInventory successor, long tick) {
        var next = state;
        for (var job : state.forest().work().values()) {
            if (!job.toolId().equals(item)) continue; // Validate this owner's explicit input relation, never select another owner.
            var tool = successor.items().get(item);
            if (job.phase() != ForestryWork.Phase.TAKE_TOOL) {
                if (tool == null || !tool.custody().equals(state.inventory().items().get(item).custody()))
                    throw new IllegalArgumentException("held forestry equipment departure requires the exact body-resource protocol");
                continue;
            }
            if (tool != null && tool.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(job.toolSource().containerId()) && tool.economicOwnerId().equals(job.home())
                    && state.bootstrap().ruleset().tools().serviceable(tool, state.bootstrap().ruleset().forestry().tool())) continue;
            if (job.pending().isPresent() || ForestryWorkAuthority.carried(state, job) != 0)
                throw new IllegalArgumentException("unacquired forestry tool still retains an unresolved physical effect/resource");
            var update = FrontierWorldStateUpdate.begin().forest(next.forest().replaceWork(job,
                    job.next(ForestryWork.Phase.FINISHED, Optional.empty()))).actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(
                        next, job.execution().actorId(), job.execution().activityKind(), job.id()));
            var movement = next.actorMovements().get(job.execution().actorId());
            next = movement != null && movement.executionId().equals(job.execution())
                    ? ActorMovementProcess.retireOwned(next, movement, tick, update) : next.withChanges(update);
        }
        return next;
    }
    @Override public List<ScheduledAction> retiredSchedules(FrontierWorldState before, FrontierWorldState after,
            Supplier<List<ScheduledAction>> pending) {
        if (before.forest().work() == after.forest().work()) return List.of();
        var ids = new HashSet<ScheduleId>();
        for (var job : before.forest().work().values()) if (!after.forest().work().containsKey(job.id())) {
            ids.add(ForestryContinuation.progress(job.id(), 0).id());
            var movement = before.actorMovements().get(job.execution().actorId());
            if (movement != null && movement.executionId().equals(job.execution())
                    && !after.actorMovements().containsKey(job.execution().actorId()))
                ids.add(ActorMovementProcess.progress(movement, Math.addExact(movement.issuedAtTick(), 1)).id());
        }
        return ids.isEmpty() ? List.of() : pending.get().stream().filter(action -> ids.contains(action.id())).toList();
    }
}
