package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;

import java.util.Map;

/** Validates the durable exact-member boundary for a non-replayable HOT scene strike. */
public final class SceneStrikeStateSupport {
    private SceneStrikeStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.SCENE_STRIKE) return;
        SceneLease lease = matchingHotLease(state, intent);
        validateMembers(state.actorLocations(), lease, intent);
        validateSettlementSelection(state.strategicPlans(), state.actorLocations(), state.physicalIntents().values(), lease, intent);
        validateSettlementLeaseBinding(state, lease, intent);
    }

    public static void validateIntent(Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases,
                               Map<SubjectId, ActorLocation> actors, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.SCENE_STRIKE) return;
        SceneLease lease = leases.values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT).filter(value -> matches(value, intent))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("scene strike requires one HOT owned lease"));
        SubjectId attacker = attacker(intent), target = target(intent);
        ActorLocation attackerLocation = actors.get(attacker), targetLocation = actors.get(target);
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(members(intent))
                || attackerLocation == null || targetLocation == null || attackerLocation.condition().status() != ActorLifeStatus.ALIVE
                || targetLocation.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("scene strike must bind two living members of its exact HOT lease");
        }
    }

    public static void validateObservation(FrontierWorldState state, PhysicalIntent intent, SceneStrikeObservation observation) {
        if (!intent.equals(state.physicalIntents().get(intent.id()))) {
            throw new IllegalArgumentException("scene strike observation lacks its exact admitted intent");
        }
        SceneLease lease = matchingLease(state, intent);
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(members(intent))) {
            throw new IllegalArgumentException("scene strike receipt has no matching exact scene lease");
        }
        validateMembers(intent, observation);
        validateObservedWound(state.actorLocations(), intent, observation);
        // Admission already fixed the exact participants. Death or another ordinary
        // physical consequence must not select a new living target for this old hit.
        // matchingLease above retains the exact epoch; the receipt retains both roles.
        validateSettlementLeaseBinding(state, lease, intent);
    }

    static void validateObservation(Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases,
                                    PhysicalIntent intent, SceneStrikeObservation observation) {
        if (leases.values().stream().filter(lease -> matches(lease, intent)).noneMatch(lease -> lease.members().stream()
                .map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(members(intent)))) {
            throw new IllegalArgumentException("scene strike receipt has no matching exact scene lease");
        }
        validateMembers(intent, observation);
    }

    public static SubjectId owner(FrontierWorldState state, PhysicalIntent intent) {
        return FrontierSceneOwnerSupport.owner(state, matchingHotLease(state, intent));
    }

    public static SubjectId transitionOwner(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        if (intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                && transition.status() == PhysicalIntentStatus.CONFLICTED) {
            var fence = state.fencedRecovery().current().get(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
            if (fence == null || fence.phase() != FencedRecoveryPhase.AMBIGUOUS
                    || fence.asset() != FencedRecoveryAsset.EFFECT
                    || !fence.ownerId().equals(intent.causeSubjectId())
                    || fence.recoveryAttempts() < FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS) {
                throw new IllegalArgumentException("unknown strike requires bounded inspection before abandonment");
            }
        }
        SceneLease lease = matchingLease(state, intent);
        boolean permitted = switch (transition.status()) {
            case RUNNING -> lease.status() == SceneLeaseStatus.HOT;
            case CONFIRMED -> lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING;
            case UNKNOWN_AFTER_RESTART, CONFLICTED -> lease.status() != SceneLeaseStatus.CLOSED;
            case PREPARED -> false;
        };
        if (!permitted) throw new IllegalArgumentException("scene strike transition lacks current bound scene permission");
        return FrontierSceneOwnerSupport.owner(state, lease);
    }

    public static boolean boundTo(SceneLease lease, PhysicalIntent intent) {
        var binding = intent.roles().scene().orElseThrow(() -> new IllegalArgumentException("scene strike lacks explicit scene binding"));
        boolean family = switch (intent.lifecycleOwner()) {
            case SETTLEMENT_ASSAULT -> FrontierSceneBehaviors.isSettlementAssault(lease);
            default -> false;
        };
        return family && binding.leaseId().equals(lease.id()) && binding.revision() == lease.revision();
    }

    /** Atomic release revokes only effects whose durable start was never admitted. */
    public static FrontierWorldState prepareRelease(FrontierWorldState state, SceneLease lease) {
        var intents = new java.util.LinkedHashMap<>(state.physicalIntents());
        var recovery = state.fencedRecovery();
        for (PhysicalIntent intent : state.physicalIntents().values()) {
            if (intent.kind() != PhysicalIntentKind.SCENE_STRIKE || !boundTo(lease, intent)) continue;
            switch (intent.status()) {
                case CONFIRMED, CONFLICTED -> { }
                case RUNNING, UNKNOWN_AFTER_RESTART -> throw new IllegalArgumentException("unresolved scene strike retains its exact scene custody");
                case PREPARED -> {
                    FencedRecoveryPhysicalIntentSupport.requirePreparedExecutionAuthority(recovery, intent, FencedRecoveryAsset.EFFECT);
                    SubjectId id = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
                    recovery = recovery.conflict(id, recovery.current().get(id).authorityEpoch(), "scene-release-unstarted-strike");
                    intents.remove(intent.id());
                }
            }
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).fencedRecovery(recovery));
    }

    static boolean isSettlementAssaultCause(StrategicPlanState plans, PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.SCENE_STRIKE && plans.settlementAssaults().values().stream()
                .anyMatch(assault -> SettlementAssaultCauseIdentity.belongsTo(assault.id(), intent.causeSubjectId()));
    }

    private static SceneLease matchingHotLease(FrontierWorldState state, PhysicalIntent intent) {
        SceneLease lease = matchingLease(state, intent);
        if (lease.status() != SceneLeaseStatus.HOT) throw new IllegalArgumentException("scene strike has no HOT owned lease");
        return lease;
    }

    private static SceneLease matchingLease(FrontierWorldState state, PhysicalIntent intent) {
        var binding = intent.roles().scene().orElseThrow(() -> new IllegalArgumentException("scene strike lacks explicit scene binding"));
        SceneLease lease = state.sceneLeases().get(binding.leaseId());
        if (lease == null || !boundTo(lease, intent) || !matches(state, lease, intent)) {
            throw new IllegalArgumentException("scene strike receipt has no matching exact scene lease");
        }
        return lease;
    }

    private static boolean matches(FrontierWorldState state, SceneLease lease, PhysicalIntent intent) {
        return matches(state.strategicPlans(), state.physicalIntents().values(), lease, intent);
    }

    private static boolean matches(SceneLease lease, PhysicalIntent intent) {
        if (!boundTo(lease, intent)) return false;
        if (FrontierSceneBehaviors.owns(lease, intent.causeSubjectId())) return true;
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) return false;
        SettlementAssaultSceneCause cause = FrontierSceneBehaviors.settlementAssault(lease);
        if (!SettlementAssaultCauseIdentity.belongsTo(cause.assaultId(), intent.causeSubjectId())) return false;
        long epoch = SettlementAssaultCauseIdentity.epoch(cause.assaultId(), intent.causeSubjectId());
        return intent.causeSubjectId().equals(SettlementAssaultCauseIdentity.strike(cause.assaultId(), attacker(intent), epoch));
    }

    private static boolean matches(StrategicPlanState plans, Iterable<PhysicalIntent> intents, SceneLease lease, PhysicalIntent intent) {
        if (!boundTo(lease, intent)) return false;
        if (FrontierSceneBehaviors.owns(lease, intent.causeSubjectId())) return true;
        if (!FrontierSceneBehaviors.isSettlementAssault(lease) || !isSettlementAssaultCause(plans, intent)) return false;
        SettlementAssault assault = plans.settlementAssaults().get(FrontierSceneBehaviors.settlementAssault(lease).assaultId());
        if (assault == null || !SettlementAssaultCauseIdentity.belongsTo(assault.id(), intent.causeSubjectId())) return false;
        long epoch = SettlementAssaultCauseIdentity.epoch(assault.id(), intent.causeSubjectId());
        if (!intent.causeSubjectId().equals(SettlementAssaultCauseIdentity.strike(assault.id(), attacker(intent), epoch))) return false;
        long next = SettlementAssaultCauseIdentity.hotEpoch(assault, intents);
        return intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                ? epoch < next : epoch == next;
    }

    private static void validateSettlementLeaseBinding(FrontierWorldState state, SceneLease lease, PhysicalIntent intent) {
        if (FrontierSceneBehaviors.isSettlementAssault(lease)
                && !SettlementAssaultStrikeReceiptBinding.belongsToLease(state, lease, intent)) {
            throw new IllegalArgumentException("settlement scene strike belongs to a foreign lease identity or revision");
        }
    }

    private static void validateMembers(Map<SubjectId, ActorLocation> actors, SceneLease lease, PhysicalIntent intent) {
        SubjectId attacker = attacker(intent), target = target(intent);
        ActorLocation attackerLocation = actors.get(attacker), targetLocation = actors.get(target);
        if (!lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(members(intent))
                || attackerLocation == null || targetLocation == null || attackerLocation.condition().status() != ActorLifeStatus.ALIVE
                || targetLocation.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("scene strike must bind two living members of its exact HOT lease");
        }
    }

    private static void validateMembers(PhysicalIntent intent, SceneStrikeObservation observation) {
        if (!attacker(intent).equals(observation.attackerId()) || !target(intent).equals(observation.targetId())) {
            throw new IllegalArgumentException("scene strike receipt differs from its exact prepared members");
        }
    }

    /**
     * A non-lethal physical hit is the exact durable wound for the same actor.  A lethal hit is
     * deliberately different: the common ActorBodyDied must have already recorded its body/death
     * evidence, so a delayed receipt cannot manufacture a death or revive that actor.
     */
    private static void validateObservedWound(Map<SubjectId, ActorLocation> actors, PhysicalIntent intent,
                                              SceneStrikeObservation observation) {
        ActorLocation target = actors.get(target(intent));
        if (target == null) throw new IllegalArgumentException("scene strike has no target actor");
        if (observation.targetHealthAfter().compareTo(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO) == 0) {
            if (target.condition().status() != ActorLifeStatus.DEAD) {
                throw new IllegalArgumentException("lethal scene strike requires exact prior death evidence");
            }
            return;
        }
        if (target.condition().status() != ActorLifeStatus.ALIVE
                || !target.condition().health().equals(observation.targetHealthBefore())) {
            throw new IllegalArgumentException("scene strike wound does not match the current exact target health");
        }
    }

    /**
     * A HOT assault is the physical executor of the same deterministic COLD strike, not a
     * second combat chooser.  The cause identifies only assault/attacker/epoch; this method
     * fences the separately-bound target and rejects a live-but-wrong lease member.
     */
    private static void validateSettlementSelection(StrategicPlanState plans, Map<SubjectId, ActorLocation> actors,
                                                    Iterable<PhysicalIntent> intents, SceneLease lease, PhysicalIntent intent) {
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) return;
        SettlementAssault assault = plans.settlementAssaults().get(FrontierSceneBehaviors.settlementAssault(lease).assaultId());
        if (assault == null) throw new IllegalArgumentException("settlement scene strike has no canonical assault");
        long epoch = SettlementAssaultCauseIdentity.epoch(assault.id(), intent.causeSubjectId());
        long currentEpoch = SettlementAssaultCauseIdentity.hotEpoch(assault, intents);
        if ((intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED ? epoch >= currentEpoch : epoch != currentEpoch)) {
            throw new IllegalArgumentException("settlement scene strike does not retain the current epoch");
        }
        boolean hiveTurn = (epoch & 1L) == 0L;
        java.util.List<SubjectId> attackers = (hiveTurn ? assault.combatantAttackerIds() : assault.defenderIds()).stream()
                .filter(id -> alive(actors, id)).sorted().toList();
        java.util.List<SubjectId> targets = (hiveTurn ? assault.defenderIds() : assault.combatantAttackerIds()).stream()
                .filter(id -> alive(actors, id)).sorted().toList();
        if (attackers.isEmpty() || targets.isEmpty()) throw new IllegalArgumentException("settlement scene strike has no living exact combatants");
        SubjectId expectedAttacker = attackers.get(Math.floorMod(epoch, attackers.size()));
        SubjectId expectedTarget = targets.get(Math.floorMod(epoch, targets.size()));
        if (!intent.roles().equals(PhysicalIntentRoleBinding.assaultSceneStrike(expectedAttacker, expectedTarget, lease.id(), lease.revision()))) {
            throw new IllegalArgumentException("settlement scene strike does not match the exact COLD attacker and target");
        }
    }

    private static boolean alive(Map<SubjectId, ActorLocation> actors, SubjectId actorId) {
        ActorLocation actor = actors.get(actorId);
        return actor != null && actor.condition().status() == ActorLifeStatus.ALIVE;
    }

    private static SubjectId attacker(PhysicalIntent intent) { return intent.roles().require(PhysicalIntentSubjectRole.ATTACKER); }
    private static SubjectId target(PhysicalIntent intent) { return intent.roles().require(PhysicalIntentSubjectRole.TARGET); }
    private static java.util.Set<SubjectId> members(PhysicalIntent intent) { return java.util.Set.of(attacker(intent), target(intent)); }
}
