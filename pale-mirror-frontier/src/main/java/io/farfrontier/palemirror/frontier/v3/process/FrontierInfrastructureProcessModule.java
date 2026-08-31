package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Exact reducer owner for route construction and patrol facts. */
final class FrontierInfrastructureProcessModule implements FrontierWorldProcessModule {
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteConstructionMaterialLoaded loaded -> RouteConstructionStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case RoutePatrolStarted started -> RoutePatrolProcess.reduceStarted(state, event.subject(), started);
            case RoutePatrolAdvanced advanced -> RoutePatrolProcess.reduceAdvanced(state, event.subject(), advanced);
            case RoutePatrolObstructionConfirmed confirmed -> RoutePatrolProcess.reduceObstruction(state, event.subject(), confirmed);
            case RoutePatrolFailed failed -> RoutePatrolProcess.reduceFailed(state, event.subject(), failed);
            default -> throw new IllegalArgumentException("infrastructure process does not own event: " + event.payload().type());
        };
    }
}
