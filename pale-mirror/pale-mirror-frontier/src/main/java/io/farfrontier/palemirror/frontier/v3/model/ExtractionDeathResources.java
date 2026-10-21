package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import java.util.Optional;

/** Mining owns work/tool references; common inventory owns the actual resource disposition. */
public final class ExtractionDeathResources {
    private ExtractionDeathResources() { }
    public static Optional<ExtractionWork> work(FrontierWorldState state, SubjectId actor) {
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null) return Optional.empty();
        return authority.current().filter(id -> id.activityKind() == ActorActivityKind.EXTRACTION)
                .or(() -> authority.suspended().filter(id -> id.activityKind() == ActorActivityKind.EXTRACTION))
                .map(id -> ExtractionWorkAuthority.require(state, id));
    }
    public static boolean resourcesSettled(FrontierWorldState state, ExtractionWork job) {
        var tool = state.inventory().items().get(job.toolId());
        return job.pending().isEmpty() && ExtractionWorkAuthority.carried(state, job) == 0
                && !ExtractionWorkAuthority.physicalCargo(state, job)
                && (tool == null || !tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId())));
    }
    public static FrontierWorldState settle(FrontierWorldState state, SubjectId subject, ExtractionDeathSettled event) {
        var identity = event.identity(); ActorBodyAuthority.requireRetiredDeath(state, identity.body());
        state.actorExecutions().requireRetained(identity.execution());
        var job = ExtractionWorkAuthority.require(state, identity.execution());
        if (!subject.equals(job.id()) || job.revision() != event.expectedRevision() || !resourcesSettled(state, job))
            throw new IllegalArgumentException("dead extraction worker still owns an unresolved effect, tool or cargo");
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().releaseDeadWork(job))
                .actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state.actorExecutions(),
                        identity.body().actorId(), ActorActivityKind.EXTRACTION, job.id())));
    }
}
