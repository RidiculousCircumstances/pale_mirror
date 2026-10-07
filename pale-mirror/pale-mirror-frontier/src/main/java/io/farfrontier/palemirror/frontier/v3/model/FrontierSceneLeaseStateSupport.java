package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/** Atomic coupled-scene transitions between persisted HOT lease and the same COLD engagement. */
public final class FrontierSceneLeaseStateSupport {
    private static final int MAX_SCENE_LEASES = 1_024;
    private FrontierSceneLeaseStateSupport() { }

    static FrontierWorldState prepare(FrontierWorldState state, SceneLease lease) {
        Map<SceneLeaseId, SceneLease> leases = withPreparedLease(state, lease);
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), state.strategicPlans(), prepareRecovery(state.fencedRecovery(), lease));
    }

    private static Map<SceneLeaseId, SceneLease> withPreparedLease(FrontierWorldState state, SceneLease lease) {
        Objects.requireNonNull(lease, "scene lease");
        if (state.sceneLeases().containsKey(lease.id())) throw new IllegalArgumentException("scene lease identity already exists: " + lease.id().value());
        if (lease.status() != SceneLeaseStatus.PREPARED) throw new IllegalArgumentException("new scene lease must be prepared");
        ActorExecutionCoordinator.requireScenePreparation(state, lease);
        FrontierSceneBehaviors.validatePrepared(state, lease);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        int requiredCompaction = leases.size() - MAX_SCENE_LEASES + 1;
        if (requiredCompaction > 0) {
            List<SceneLease> terminal = leases.values().stream().filter(existing -> mayCompact(state, existing))
                    .sorted(Comparator.comparing(SceneLease::handoffInstant).thenComparing(existing -> existing.id().value())).toList();
            if (terminal.size() < requiredCompaction) throw new IllegalArgumentException("scene lease retention limit has no terminal leases to compact");
            terminal.stream().limit(requiredCompaction).forEach(existing -> leases.remove(existing.id()));
        }
        leases.put(lease.id(), lease);
        return leases;
    }

    static boolean mayCompact(FrontierWorldState state, SceneLease lease) {
        return lease.status() == SceneLeaseStatus.CLOSED && state.physicalIntents().values().stream()
                .noneMatch(intent -> intent.roles().scene().filter(binding -> binding.leaseId().equals(lease.id())).isPresent());
    }

    static FrontierWorldState handoff(FrontierWorldState state, SceneLeaseHandoff handoff) {
        return ActorExecutionCoordinator.transferToScene(state, handoff);
    }

    static FrontierWorldState transition(FrontierWorldState state, SceneLeaseId leaseId, SceneLeaseStatus nextStatus) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || !current.status().canTransitionTo(nextStatus)) throw new IllegalArgumentException("scene lease transition is not allowed");
        StrategicPlanState plans = FrontierSceneBehaviors.transitionPlans(state, current, nextStatus);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(nextStatus));
        FencedRecoveryState recovery = switch (nextStatus) {
            case HOT -> runningRecovery(state, current);
            case CONFLICT -> isolateRecovery(state.fencedRecovery(), current, "scene-conflict");
            // A scene resolution may supersede its cargo projection, never its actors.
            case PREPARED -> current.status() == SceneLeaseStatus.CONFLICT
                    ? reprepareConflictRecovery(state.fencedRecovery(), current) : state.fencedRecovery();
            default -> state.fencedRecovery();
        };
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), plans, recovery);
    }

    /**
     * Records a naturally loaded recovery observation without inventing a death, body, cargo
     * hand-off or COLD continuation. The registered scene owner selects any accompanying
     * continuation; combat preserves uncertainty rather than manufacturing an outcome.
     */
    public static FrontierWorldState recoveryUnresolved(FrontierWorldState state, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease current = state.sceneLeases().get(unresolved.leaseId());
        if (current == null || current.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || current.recoveryEvidence().isPresent()) {
            throw new IllegalArgumentException("scene recovery evidence requires one uninspected unknown lease");
        }
        Set<SubjectId> liveMembers = current.members().stream().map(SceneMember::actorId)
                .filter(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE)
                .collect(java.util.stream.Collectors.toSet());
        if (!liveMembers.containsAll(unresolved.missingActorIds())) {
            throw new IllegalArgumentException("scene recovery evidence names a foreign or already-dead actor");
        }
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(current.id(), current.withRecoveryEvidence(new SceneRecoveryEvidence(unresolved.missingActorIds())));
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), state.strategicPlans(), state.fencedRecovery());
    }

    /**
     * Legacy WAL reducer only. No new command may invoke this pose-only retirement: a saved
     * patrol body may carry nonfatal injury not yet published to canonical actor health.
     */
    public static FrontierWorldState revokeUnknownPatrolToCold(FrontierWorldState state, SceneLeaseRecoveryRevoked revoked) {
        SceneLease current = state.sceneLeases().get(revoked.leaseId());
        if (current == null || current.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || current.recoveryEvidence().isPresent()
                || !FrontierSceneBehaviors.isRoutePatrol(current)) {
            throw new IllegalArgumentException("recovery revoke requires one uninspected route-patrol lease");
        }
        FencedRecoveryState recovery = state.fencedRecovery();
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(current.id(), current.withStatus(SceneLeaseStatus.CLOSED));
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), state.strategicPlans(), recovery);
    }

    static FrontierWorldState release(FrontierWorldState state, SceneLeaseId leaseId, List<SceneMemberPosition> positions) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || current.status() != SceneLeaseStatus.DRAINING) throw new IllegalArgumentException("only a draining scene lease can be released");
        if (FrontierSceneBehaviors.isProductionWork(current)) {
            ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(current).jobId());
            if (job != null && job.bakeryWork().isPresent()
                    && job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent())
                throw new IllegalArgumentException("bakery scene cannot release a possibly applied physical effect");
        }
        requireNoBoundActorHand(state, current);
        FrontierWorldState releaseReady = SceneStrikeStateSupport.prepareRelease(state, current);
        Set<SubjectId> expected = current.members().stream().map(SceneMember::actorId).filter(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE)
                .collect(java.util.stream.Collectors.toSet());
        Set<SubjectId> observed = positions.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet());
        if (!expected.equals(observed) || observed.size() != positions.size()) throw new IllegalArgumentException("scene release must capture exactly its leased actors");
        for (SceneMemberPosition position : positions) {
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position.body().supportingSurface().support());
            ActorLocation currentActor = releaseReady.actorLocations().get(position.actorId());
            if (!currentActor.body().equals(position.body()) || !currentActor.condition().health().equals(position.health()))
                throw new IllegalArgumentException("scene release requires independently recorded common body observation");
            FrontierSceneBehaviors.validateRelease(state, current, position.actorId(), position.body());
        }
        StrategicPlanState plans = FrontierSceneBehaviors.releasePlans(state, current);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(SceneLeaseStatus.CLOSED));
        return copy(releaseReady, releaseReady.actorLocations(), leases, state.ambientLeases(), plans, confirmRecovery(releaseReady, releaseReady.fencedRecovery(), current));
    }

    /** Ordinary scene release cannot discard a body while its physical offhand still owns stock. */
    public static void requireNoBoundActorHand(FrontierWorldState state, SceneLease lease) {
        if (hasBoundActorHand(state, lease)) {
            throw new IllegalArgumentException("scene release requires typed actor-hand custody transfer");
        }
    }

    /** A scene with physically bound stock must retain local custody through any conflict. */
    public static boolean hasBoundActorHand(FrontierWorldState state, SceneLease lease) {
        Set<SubjectId> members = lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet());
        return state.inventory().fungibleResources().bindings().values().stream()
                .map(PhysicalStackBinding::address)
                .filter(PhysicalStackAddress.ActorStack.class::isInstance)
                .map(PhysicalStackAddress.ActorStack.class::cast)
                .anyMatch(hand -> members.contains(hand.actorId()));
    }

    /** An unstarted lease has not transferred authority to a Minecraft body. */
    public static FrontierWorldState abortPrepared(FrontierWorldState state, SceneLeaseId leaseId) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || current.status() != SceneLeaseStatus.PREPARED
                && (current.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || current.recoveryEvidence().isPresent())) {
            throw new IllegalArgumentException("only an unstarted scene lease can be aborted before materialization");
        }
        // Cancelling process admission does not revoke an actor incarnation. It may already
        // be observed under another activity; only common body unload/death may retire it.
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(leaseId, current.withStatus(SceneLeaseStatus.CLOSED));
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), state.strategicPlans(), revokePreparedRecovery(state.fencedRecovery(), current));
    }

    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors,
                                           Map<SceneLeaseId, SceneLease> leases, Map<SubjectId, AmbientActorLease> ambient,
                                           StrategicPlanState plans) {
        return copy(state, actors, leases, ambient, plans, state.fencedRecovery());
    }
    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors,
                                           Map<SceneLeaseId, SceneLease> leases, Map<SubjectId, AmbientActorLease> ambient,
                                           StrategicPlanState plans, FencedRecoveryState recovery) {
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).sceneLeases(leases)
                .ambientLeases(ambient).strategicPlans(plans).fencedRecovery(recovery));
    }
    /** Stable owner identity for scene cargo/effects only; actor bodies own their own epoch. */
    public static SubjectId recoveryOwner(SceneLease lease) { return recoveryOwner(lease.id()); }
    /** Stable persisted recovery binding for one exact lease; this is identity translation, never owner discovery. */
    public static SubjectId recoveryOwner(io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId) {
        return new SubjectId("scene:" + leaseId.value().replace(':', '_'));
    }
    /** Stable physical-cargo key shared with the naturally loaded stale-carrier guard. */
    public static SubjectId cargoRecoveryBindingId(SubjectId cargoId) { return new SubjectId("recovery:cargo_" + cargoId.value().replace(':', '_')); }
    private static FencedRecoveryState prepareRecovery(FencedRecoveryState recovery, SceneLease lease) {
        return prepareBodies(recovery, lease);
    }
    private static FencedRecoveryState prepareBodies(FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery;
        for (SceneMember member : lease.members()) {
            next = ActorBodyAuthority.demand(next, member.actorId());
        }
        return next;
    }
    private static FencedRecoveryState reprepareConflictRecovery(FencedRecoveryState recovery, SceneLease lease) {
        return recovery;
    }
    private static FencedRecoveryState runningRecovery(FrontierWorldState state, SceneLease lease) {
        for (SceneMember member : lease.members()) {
            if (ActorBodyAuthority.require(state, ActorBodyAuthority.current(state, member.actorId())).phase() != FencedRecoveryPhase.RUNNING)
                throw new IllegalArgumentException("scene HOT requires independently confirmed physical participants");
        }
        return state.fencedRecovery();
    }
    private static FencedRecoveryState confirmRecovery(FrontierWorldState state, FencedRecoveryState recovery, SceneLease lease) {
        return recovery;
    }
    private static FencedRecoveryState revokePreparedRecovery(FencedRecoveryState recovery, SceneLease lease) {
        return recovery;
    }
    private static FencedRecoveryState isolateRecovery(FencedRecoveryState recovery, SceneLease lease, String reason) {
        return recovery;
    }
}
