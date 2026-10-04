package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

/** Logistics composes two declared purposes; scene membership never assigns a purpose. */
public final class LogisticsSceneExecutionAuthority {
    private LogisticsSceneExecutionAuthority() { }
    public static ActorExecutionGroup current(FrontierWorldState state, SceneLease lease) {
        Objects.requireNonNull(state); Objects.requireNonNull(lease);
        var cause = FrontierSceneBehaviors.logistics(lease);
        if (lease.status() != SceneLeaseStatus.HOT || !lease.equals(state.sceneLeases().get(lease.id())))
            throw new IllegalArgumentException("logistics motion needs its exact current HOT scene scope");
        var operation = state.operations().get(cause.operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || !operation.cargoId().equals(cause.cargoId()))
            throw new IllegalArgumentException("logistics motion lost its declared live operation");
        var members = new ArrayList<>(OperationExecutionAuthority.logisticsCurrent(state, operation).members());
        cause.engagementId().ifPresent(owner -> {
            var engagement = state.strategicPlans().routeEngagements().get(owner);
            if (engagement == null || !engagement.operationId().equals(operation.id())
                    || engagement.status() != RouteEngagementStatus.HOT || !engagement.commandAuthority().permitsCoordinatedAdvance())
                throw new IllegalArgumentException("logistics motion lost its declared coordinated interception");
            members.addAll(RouteEngagementExecutionAuthority.current(state, engagement).members());
        });
        var group = new ActorExecutionGroup(members);
        if (!lease.members().stream().map(SceneMember::actorId).collect(Collectors.toUnmodifiableSet()).equals(
                group.members().stream().map(id -> id.actorId()).collect(Collectors.toUnmodifiableSet())))
            throw new IllegalArgumentException("logistics scene differs from its declared participant purposes");
        return group;
    }
    /** Recheck the original complete cohort and original scene, never bless a successor. */
    public static boolean permits(FrontierWorldState state, SceneLease lease, ActorExecutionGroup captured) {
        try {
            captured.requireCurrent(state.actorExecutions());
            return current(state, lease).equals(captured);
        } catch (IllegalArgumentException stale) {
            return false;
        }
    }
}
