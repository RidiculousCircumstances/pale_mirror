package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Atomic coupled-scene transitions between persisted HOT lease and the same COLD engagement. */
final class FrontierSceneLeaseStateSupport {
    private FrontierSceneLeaseStateSupport() { }

    static FrontierWorldState transition(FrontierWorldState state, SceneLeaseId leaseId, SceneLeaseStatus nextStatus) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || !current.status().canTransitionTo(nextStatus)) throw new IllegalArgumentException("scene lease transition is not allowed");
        StrategicPlanState plans = state.strategicPlans();
        if (current.engagementId().isPresent()) {
            SubjectId engagement = current.engagementId().orElseThrow();
            if (nextStatus == SceneLeaseStatus.HOT) plans = plans.transitionEngagement(engagement, RouteEngagementStatus.HOT);
            if (nextStatus == SceneLeaseStatus.UNKNOWN_AFTER_RESTART) {
                plans = plans.transitionEngagement(engagement, RouteEngagementStatus.UNKNOWN_AFTER_RESTART);
            }
        }
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(nextStatus));
        return copy(state, state.actorLocations(), leases, plans);
    }

    static FrontierWorldState release(FrontierWorldState state, SceneLeaseId leaseId, List<SceneMemberPosition> positions) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || current.status() != SceneLeaseStatus.DRAINING) throw new IllegalArgumentException("only a draining scene lease can be released");
        Set<SubjectId> expected = current.members().stream().map(SceneMember::actorId).filter(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE)
                .collect(java.util.stream.Collectors.toSet());
        Set<SubjectId> observed = positions.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet());
        if (!expected.equals(observed) || observed.size() != positions.size()) throw new IllegalArgumentException("scene release must capture exactly its leased actors");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        for (SceneMemberPosition position : positions) {
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position.position());
            ActorLocation currentActor = actors.get(position.actorId()); actors.put(position.actorId(), new ActorLocation(position.position(), currentActor.condition().withHealth(position.health())));
        }
        StrategicPlanState plans = current.engagementId().map(id -> state.strategicPlans().transitionEngagement(id, RouteEngagementStatus.COLD_COMBAT)).orElse(state.strategicPlans());
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(SceneLeaseStatus.CLOSED));
        return copy(state, actors, leases, plans);
    }

    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors,
                                           Map<SceneLeaseId, SceneLease> leases, StrategicPlanState plans) {
        return new FrontierWorldState(state.bootstrap(), actors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), leases, state.hiveColony(), state.structureDamage(),
                state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), plans);
    }
}
