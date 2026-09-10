package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;

/** Validates the durable exact-member boundary for a non-replayable HOT scene strike. */
public final class SceneStrikeStateSupport {
    private SceneStrikeStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.SCENE_STRIKE) return;
        SceneLease lease = matchingHotLease(state, intent);
        if (FrontierSceneBehaviors.isLogistics(lease)) {
            RouteOperation operation = state.operations().get(intent.causeSubjectId());
            if (operation == null || operation.stage() != OperationStage.EN_ROUTE) {
                throw new IllegalArgumentException("logistics scene strike must retain one en-route operation");
            }
        }
        validateMembers(state.actorLocations(), lease, intent);
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
        SceneLease lease = matchingLease(state, intent);
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(intent.subjectIds())) {
            throw new IllegalArgumentException("scene strike receipt has no matching exact scene lease");
        }
        validateMembers(intent, observation);
    }

    static void validateObservation(Map<SubjectId, RouteOperation> operations, Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases,
                                    PhysicalIntent intent, SceneStrikeObservation observation) {
        if (leases.values().stream().filter(lease -> matches(lease, intent)).noneMatch(lease -> lease.members().stream()
                .map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(intent.subjectIds()))) {
            throw new IllegalArgumentException("scene strike receipt has no matching exact scene lease");
        }
        validateMembers(intent, observation);
    }

    public static SubjectId owner(FrontierWorldState state, PhysicalIntent intent) {
        return FrontierSceneOwnerSupport.owner(state, matchingHotLease(state, intent));
    }

    static boolean isSettlementAssaultCause(StrategicPlanState plans, PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.SCENE_STRIKE && plans.settlementAssaults().values().stream()
                .anyMatch(assault -> SettlementAssaultCauseIdentity.belongsTo(assault.id(), intent.causeSubjectId()));
    }

    private static SceneLease matchingHotLease(FrontierWorldState state, PhysicalIntent intent) {
        return state.sceneLeases().values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(value -> matches(state, value, intent)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scene strike has no HOT owned lease"));
    }

    private static SceneLease matchingLease(FrontierWorldState state, PhysicalIntent intent) {
        return state.sceneLeases().values().stream().filter(value -> matches(state, value, intent)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scene strike receipt has no matching exact scene lease"));
    }

    private static boolean matches(FrontierWorldState state, SceneLease lease, PhysicalIntent intent) {
        return matches(state.strategicPlans(), state.physicalIntents().values(), lease, intent);
    }

    private static boolean matches(SceneLease lease, PhysicalIntent intent) {
        if (FrontierSceneBehaviors.owns(lease, intent.causeSubjectId())) return true;
        if (!FrontierSceneBehaviors.isSettlementAssault(lease) || intent.subjectIds().isEmpty()) return false;
        SettlementAssaultSceneCause cause = FrontierSceneBehaviors.settlementAssault(lease);
        if (!SettlementAssaultCauseIdentity.belongsTo(cause.assaultId(), intent.causeSubjectId())) return false;
        long epoch = SettlementAssaultCauseIdentity.epoch(cause.assaultId(), intent.causeSubjectId());
        return intent.causeSubjectId().equals(SettlementAssaultCauseIdentity.strike(cause.assaultId(), intent.subjectIds().getFirst(), epoch));
    }

    private static boolean matches(StrategicPlanState plans, Iterable<PhysicalIntent> intents, SceneLease lease, PhysicalIntent intent) {
        if (FrontierSceneBehaviors.owns(lease, intent.causeSubjectId())) return true;
        if (!FrontierSceneBehaviors.isSettlementAssault(lease) || intent.subjectIds().isEmpty() || !isSettlementAssaultCause(plans, intent)) return false;
        SettlementAssault assault = plans.settlementAssaults().get(FrontierSceneBehaviors.settlementAssault(lease).assaultId());
        if (assault == null || !SettlementAssaultCauseIdentity.belongsTo(assault.id(), intent.causeSubjectId())) return false;
        long epoch = SettlementAssaultCauseIdentity.epoch(assault.id(), intent.causeSubjectId());
        if (!intent.causeSubjectId().equals(SettlementAssaultCauseIdentity.strike(assault.id(), intent.subjectIds().getFirst(), epoch))) return false;
        long next = SettlementAssaultCauseIdentity.hotEpoch(assault, intents);
        return intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                ? epoch < next : epoch == next;
    }

    private static void validateMembers(Map<SubjectId, ActorLocation> actors, SceneLease lease, PhysicalIntent intent) {
        SubjectId attacker = intent.subjectIds().getFirst(), target = intent.subjectIds().getLast();
        ActorLocation attackerLocation = actors.get(attacker), targetLocation = actors.get(target);
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(intent.subjectIds())
                || attackerLocation == null || targetLocation == null || attackerLocation.condition().status() != ActorLifeStatus.ALIVE
                || targetLocation.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("scene strike must bind two living members of its exact HOT lease");
        }
    }

    private static void validateMembers(PhysicalIntent intent, SceneStrikeObservation observation) {
        if (!intent.subjectIds().getFirst().equals(observation.attackerId()) || !intent.subjectIds().getLast().equals(observation.targetId())) {
            throw new IllegalArgumentException("scene strike receipt differs from its exact prepared members");
        }
    }
}
