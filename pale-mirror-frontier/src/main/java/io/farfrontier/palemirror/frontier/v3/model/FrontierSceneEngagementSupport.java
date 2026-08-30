package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Derives a read-only bounded HOT-admission projection from canonical COLD combat state. */
final class FrontierSceneEngagementSupport {
    private FrontierSceneEngagementSupport() { }

    static List<SceneEngagementCandidate> candidates(FrontierWorldState state) {
        return state.strategicPlans().routeEngagements().values().stream()
                .filter(engagement -> engagement.status() == RouteEngagementStatus.COLD_COMBAT)
                .sorted(Comparator.comparing(RouteEngagement::id)).map(engagement -> candidate(state, engagement)).toList();
    }

    private static SceneEngagementCandidate candidate(FrontierWorldState state, RouteEngagement engagement) {
        RouteOperation operation = state.operations().get(engagement.operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE
                || !operation.currentPosition().equals(engagement.intercept())) {
            throw new IllegalStateException("COLD engagement has no current interception operation");
        }
        List<SubjectId> actors = new ArrayList<>(operation.participantIds()); actors.addAll(engagement.attackerIds()); actors.sort(Comparator.naturalOrder());
        return new SceneEngagementCandidate(engagement.id(), operation.id(), operation.cargoId(), engagement.intercept(), actors);
    }
}
