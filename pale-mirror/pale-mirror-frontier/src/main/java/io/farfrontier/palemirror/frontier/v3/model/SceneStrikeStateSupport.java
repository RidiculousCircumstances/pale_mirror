package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;

/** Validates the durable exact-member boundary for a non-replayable HOT scene strike. */
public final class SceneStrikeStateSupport {
    private SceneStrikeStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        validateIntent(state.operations(), state.sceneLeases(), state.actorLocations(), intent);
    }

    public static void validateIntent(Map<SubjectId, RouteOperation> operations, Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases,
                               Map<SubjectId, ActorLocation> actors, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.SCENE_STRIKE) return;
        SceneLease lease = leases.values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT).filter(value -> FrontierSceneBehaviors.owns(value, intent.causeSubjectId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("scene strike requires one HOT owned lease"));
        if (FrontierSceneBehaviors.isLogistics(lease)) {
            RouteOperation operation = operations.get(intent.causeSubjectId());
            if (operation == null || operation.stage() != OperationStage.EN_ROUTE) {
                throw new IllegalArgumentException("logistics scene strike must retain one en-route operation");
            }
        }
        SubjectId attacker = intent.subjectIds().getFirst(), target = intent.subjectIds().getLast();
        ActorLocation attackerLocation = actors.get(attacker), targetLocation = actors.get(target);
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(intent.subjectIds())
                || attackerLocation == null || targetLocation == null || attackerLocation.condition().status() != ActorLifeStatus.ALIVE
                || targetLocation.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("scene strike must bind two living members of its exact HOT lease");
        }
    }

    static void validateObservation(FrontierWorldState state, PhysicalIntent intent, SceneStrikeObservation observation) {
        validateObservation(state.operations(), state.sceneLeases(), intent, observation);
    }

    static void validateObservation(Map<SubjectId, RouteOperation> operations, Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases,
                                    PhysicalIntent intent, SceneStrikeObservation observation) {
        if (leases.values().stream().filter(lease -> FrontierSceneBehaviors.owns(lease, intent.causeSubjectId())).noneMatch(lease -> lease.members().stream()
                .map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(intent.subjectIds()))) {
            throw new IllegalArgumentException("scene strike receipt has no matching exact scene lease");
        }
        validateMembers(intent, observation);
    }

    public static SubjectId owner(FrontierWorldState state, PhysicalIntent intent) {
        SceneLease lease = state.sceneLeases().values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(value -> FrontierSceneBehaviors.owns(value, intent.causeSubjectId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scene strike has no HOT owned lease"));
        return FrontierSceneOwnerSupport.owner(state, lease);
    }

    private static void validateMembers(PhysicalIntent intent, SceneStrikeObservation observation) {
        if (!intent.subjectIds().getFirst().equals(observation.attackerId()) || !intent.subjectIds().getLast().equals(observation.targetId())) {
            throw new IllegalArgumentException("scene strike receipt differs from its exact prepared members");
        }
    }
}
