package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import java.util.List;

/** Only station interactions claim access. Mining and transit never reserve the site chest. */
public final class ExtractionServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.EXTRACTION; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    public static boolean needsAccess(ExtractionWork work) {
        return work.phase() == ExtractionWork.Phase.TAKE_TOOL || work.phase() == ExtractionWork.Phase.STORE
                || work.phase() == ExtractionWork.Phase.RETURN_TOOL;
    }
    public static ServiceAccessDemand.Identity identity(FrontierWorldState state, ExtractionWork work) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.EXTRACTION, work.id(), work.execution().actorId(),
                ExtractionWorkAuthority.site(state, work).containerId());
    }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId point) {
        var boundary = ServiceAccessCoordinator.boundary(state, point);
        return state.extractionSites().work().values().stream().filter(ExtractionServiceAccess::needsAccess)
                .filter(work -> identity(state, work).pointId().equals(point))
                .filter(work -> state.actorExecutions().actors().get(work.execution().actorId()).current().equals(java.util.Optional.of(work.execution())))
                .map(work -> new ServiceAccessDemand(identity(state, work), priority(),
                        ServiceAccessCoordinator.occupies(state, boundary, work.execution().actorId())
                                ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH, 0, work.pending().isPresent()))
                .toList();
    }
}
