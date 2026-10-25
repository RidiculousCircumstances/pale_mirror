package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryWork;
import java.util.*;

/** Only exact tool/cargo interactions request storage access; logging and return travel do not. */
public final class ForestryServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.FORESTRY; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    public static boolean needsAccess(ForestryWork work) {
        return work.phase() == ForestryWork.Phase.TAKE_TOOL || work.phase() == ForestryWork.Phase.STORE
                || work.phase() == ForestryWork.Phase.RETURN_TOOL;
    }
    public static ServiceAccessDemand.Identity identity(ForestryWork work) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.FORESTRY, work.id(), work.execution().actorId(),
                (work.phase() == ForestryWork.Phase.TAKE_TOOL ? work.toolSource() : work.storageTarget()).containerId());
    }
    @Override public Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        return ServiceAccessWakeDependencies.changed(previous.forest().work(), next.forest().work(),
                work -> java.util.stream.Stream.of(work.toolSource().containerId(), work.storageTarget().containerId()));
    }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId point) {
        var boundary = ServiceAccessCoordinator.boundary(state, point);
        return state.forest().work().values().stream().filter(ForestryServiceAccess::needsAccess)
                .filter(work -> identity(work).pointId().equals(point))
                .filter(work -> state.actorExecutions().actors().get(work.execution().actorId()).current().equals(Optional.of(work.execution())))
                .map(work -> new ServiceAccessDemand(identity(work), priority(),
                        ServiceAccessCoordinator.occupies(state, boundary, work.execution().actorId())
                            ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH,
                        0, work.pending().isPresent())).toList();
    }
}
