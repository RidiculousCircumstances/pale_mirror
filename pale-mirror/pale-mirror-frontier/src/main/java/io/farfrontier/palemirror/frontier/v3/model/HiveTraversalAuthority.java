package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Collection;

/** Validates a retained hive step; never awards physical arrival or writes body position. */
public final class HiveTraversalAuthority {
    private HiveTraversalAuthority() { }
    public static void validate(FrontierWorldState state, ActorExecutionId execution, HiveTaskAssembly.Member member,
                                HiveTraversalStep step, AmbientGoalKind goal, Collection<SubjectId> participants) {
        state.actorExecutions().requireCurrent(execution);
        var actor = state.actorLocations().get(execution.actorId());
        if (member == null || member.arrived() || !step.matches(member) || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("hive step has a stale route, cursor or actor");
        if (step.hotArrival().isEmpty()) {
            if (!ActorExecutionCoordinator.coldAvailable(state, participants)
                    || participants.stream().map(state.ambientLeases()::get).anyMatch(lease -> lease != null && lease.status() != AmbientLeaseStatus.CLOSED))
                throw new IllegalArgumentException("COLD hive step cannot advance physically held participants");
            if (!actor.supportingSurface().equals(member.currentSurface()))
                throw new IllegalArgumentException("COLD hive predecessor differs: actor=" + execution.actorId()
                        + " actual=" + actor.supportingSurface() + " retained=" + member.currentSurface());
            if (!HiveAssemblyCorridor.stepClear(state, execution.actorId(), member))
                throw new IllegalArgumentException("COLD hive retained edge is blocked: actor=" + execution.actorId()
                        + " from=" + member.currentSurface() + " to=" + member.nextSurface());
            return;
        }
        var arrival = step.hotArrival().orElseThrow();
        var lease = state.ambientLeases().get(execution.actorId());
        if (!arrival.body().actorId().equals(execution.actorId())
                || ActorBodyAuthority.require(state, arrival.body()).phase() != FencedRecoveryPhase.RUNNING
                || lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != goal
                || lease.revision() != arrival.leaseRevision() || !lease.goalBody().equals(member.nextSurface().standingBody())
                || !actor.body().equals(lease.goalBody()))
            throw new IllegalArgumentException("HOT hive arrival lacks its captured body, scope or independently observed position");
    }
}
