package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;

/** Shared observation setup for modeled field-boundary tests; never a native acceptance result. */
final class HarvestObservationFixture {
    private HarvestObservationFixture() { }
    /** Independent modeled physical fact; the field receipt only clears its own blocked goal. */
    static FrontierWorldState inspectGoal(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job,
                                          SceneLeaseId leaseId) {
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        var execution = state.actorExecutions().current(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        var witness = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation(
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                        ActorBodyAuthority.current(state, job.workerId()), execution), state.sceneLeases().get(leaseId).revision());
        state = ModeledActorBodyFacts.inspected(state, job.workerId(), goal.representative().standingBody());
        return job.navigationBlock().isEmpty() ? state : ResourceSiteHarvestProcess.reduceHotGoalArrived(state, site,
                new ResourceSiteHarvestHotGoalArrived(job.id(), leaseId, job.workerId(), goal.layoutRevision(),
                        goal.nextWorkSlot(), goal.kind(), goal.representative().standingBody(), job.target().generation(), witness));
    }
}
