package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
/** Equipment access is temporary; duty and meals do not retain a warehouse turn. */
final class GuardServiceAccess implements ServiceAccessCapability {
    private final ImmutableGroupView<SubjectId, GuardDuty, SubjectId> candidates = new ImmutableGroupView<>();
    public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.GUARD_EQUIPMENT; }
    public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId point) {
        if (!ReferenceContainerCustody.isReferenceContainer(state, point)) return List.of();
        var boundary = ServiceAccessCoordinator.boundary(state, point);
        return candidates.keys(state.bootstrap(), state.strategicPlans().guards(), duty -> Set.of(duty.source().containerId()), point).stream()
                .map(state.strategicPlans().guards()::get).filter(duty -> duty.phase() != GuardDuty.Phase.ON_DUTY)
                .filter(duty -> state.actorExecutions().actors().get(duty.execution().actorId()).current().equals(Optional.of(duty.execution())))
                .filter(duty -> state.actorLocations().get(duty.execution().actorId()).condition().status() == ActorLifeStatus.ALIVE)
                .map(duty -> new ServiceAccessDemand(GuardAuthority.service(duty), priority(),
                        ServiceAccessCoordinator.occupies(state, boundary, duty.execution().actorId())
                            ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH,
                        0, GuardAuthority.pending(state, duty),
                        !ReferenceContainerCustody.blocksCanonicalUse(state, point) && GuardAuthority.goalAvailable(state, duty))).toList();
    }
    public Set<SubjectId> wakePoints(FrontierWorldState before, FrontierWorldState after) {
        return ServiceAccessWakeDependencies.changed(before.strategicPlans().guards(), after.strategicPlans().guards(),
                duty -> java.util.stream.Stream.of(duty.source().containerId()));
    }
}
