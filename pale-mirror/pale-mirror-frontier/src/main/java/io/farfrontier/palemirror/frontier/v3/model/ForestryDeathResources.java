package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryWork;
import java.util.Optional;

/** Forest owns work/tool references; common inventory owns the actual resource disposition. */
public final class ForestryDeathResources {
    private ForestryDeathResources() { }
    public static Optional<ForestryWork> work(FrontierWorldState state, SubjectId actor) {
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null) return Optional.empty();
        return authority.current().filter(id -> id.activityKind() == ActorActivityKind.FORESTRY)
                .or(() -> authority.suspended().filter(id -> id.activityKind() == ActorActivityKind.FORESTRY))
                .map(id -> ForestryWorkAuthority.require(state, id));
    }
    public static boolean resourcesSettled(FrontierWorldState state, ForestryWork job) {
        var tool = state.inventory().items().get(job.toolId());
        return job.pending().isEmpty() && ForestryWorkAuthority.carried(state, job) == 0
                && !ForestryWorkAuthority.physicalCargo(state, job)
                && (tool == null || !tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId())));
    }
    public static FrontierWorldState settle(FrontierWorldState state, SubjectId subject, ForestryPhysicalEvent.DeathSettled event) {
        var identity = event.identity(); ActorBodyAuthority.requireRetiredDeath(state, identity.body());
        state.actorExecutions().requireRetained(identity.execution());
        var job = ForestryWorkAuthority.require(state, identity.execution());
        if (!subject.equals(job.id()) || job.revision() != event.expectedRevision() || !resourcesSettled(state, job))
            throw new IllegalArgumentException("dead extraction worker still owns an unresolved effect, tool or cargo");
        return state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().releaseDeadWork(job))
                .actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state.actorExecutions(),
                        identity.body().actorId(), ActorActivityKind.FORESTRY, job.id())));
    }
}
