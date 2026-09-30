package io.farfrontier.palemirror.frontier.v3.model;

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
        if (lease.members().stream().anyMatch(member -> state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE)) {
            throw new IllegalArgumentException("scene lease cannot materialize a dead actor");
        }
        if (lease.members().stream().anyMatch(member -> !state.actorLocations().get(member.actorId()).body()
                .equals(lease.memberPosition(member.actorId())))) {
            throw new IllegalArgumentException("scene lease must retain every exact canonical member position");
        }
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
        Objects.requireNonNull(handoff, "scene hand-off");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        Map<SubjectId, AmbientActorLease> ambient = new LinkedHashMap<>(state.ambientLeases());
        Set<SubjectId> captured = handoff.ambientMembers().stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet());
        for (SceneMember member : handoff.lease().members()) {
            AmbientActorLease current = ambient.get(member.actorId());
            if (current == null || current.status() == AmbientLeaseStatus.CLOSED) {
                if (captured.contains(member.actorId())) throw new IllegalArgumentException("scene hand-off captured an unleased actor");
            } else if (current.status() != AmbientLeaseStatus.HOT || !captured.contains(member.actorId())) {
                throw new IllegalArgumentException("scene hand-off requires every active ambient member to be HOT and captured");
            }
        }
        for (SceneMemberPosition capture : handoff.ambientMembers()) {
            AmbientActorLease current = ambient.get(capture.actorId()); ActorLocation actor = actors.get(capture.actorId());
            if (current == null || current.status() != AmbientLeaseStatus.HOT || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) {
                throw new IllegalArgumentException("scene hand-off capture lacks one living HOT ambient actor");
            }
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), capture.body().supportingSurface().support());
            actors.put(capture.actorId(), new ActorLocation(capture.body(), actor.condition().withHealth(capture.health())));
            ambient.put(capture.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        }
        // The observed body is the sole admissible replacement for the outgoing ambient
        // position. Validate the incoming scene lease against that captured state, not against
        // the predecessor's historical grid cell: otherwise a normal moving Villager can never
        // enter any exact HOT scene without being despawned and recreated.
        FrontierWorldState captureState = copy(state, actors, state.sceneLeases(), ambient, state.strategicPlans());
        SceneLease lease = handoff.lease();
        return copy(captureState, actors, withPreparedLease(captureState, lease), ambient, captureState.strategicPlans(), prepareRecovery(captureState.fencedRecovery(), lease));
    }

    static FrontierWorldState transition(FrontierWorldState state, SceneLeaseId leaseId, SceneLeaseStatus nextStatus) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || !current.status().canTransitionTo(nextStatus)) throw new IllegalArgumentException("scene lease transition is not allowed");
        StrategicPlanState plans = FrontierSceneBehaviors.transitionPlans(state, current, nextStatus);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(nextStatus));
        FencedRecoveryState recovery = switch (nextStatus) {
            case HOT -> runningRecovery(state.fencedRecovery(), current);
            case CONFLICT -> isolateRecovery(state.fencedRecovery(), current, "scene-conflict");
            // CONFLICT -> PREPARED is the attributed local-resolution boundary.  The old
            // materialization is retained as a stale tombstone and this exact lease receives a
            // new epoch before any body can run again.
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
        leases.put(current.id(), current.withRecoveryEvidence(new SceneRecoveryEvidence(unresolved.missingActorIds(), unresolved.missingCargoCarrier())));
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
        FencedRecoveryState recovery = revokeReversibleBodies(state.fencedRecovery(), current);
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
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        for (SceneMemberPosition position : positions) {
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position.body().supportingSurface().support());
            ActorLocation currentActor = actors.get(position.actorId());
            // The operation cursor is the durable HOT/COLD hand-off.  A body may have been
            // halfway through ordinary Minecraft movement when its chunk vanished, but that
            // transient sub-cell location must not become a second strategic travel state.
            BodyPosition canonical = FrontierSceneBehaviors.releasedBody(state, current, position.actorId(), position.body());
            actors.put(position.actorId(), new ActorLocation(canonical, currentActor.condition().withHealth(position.health())));
        }
        StrategicPlanState plans = FrontierSceneBehaviors.releasePlans(state, current);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(SceneLeaseStatus.CLOSED));
        return copy(releaseReady, actors, leases, state.ambientLeases(), plans, confirmRecovery(releaseReady, releaseReady.fencedRecovery(), current));
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
                .filter(PhysicalStackAddress.ActorHand.class::isInstance)
                .map(PhysicalStackAddress.ActorHand.class::cast)
                .anyMatch(hand -> members.contains(hand.actorId()));
    }

    /** An unstarted lease has not transferred authority to a Minecraft body. */
    public static FrontierWorldState abortPrepared(FrontierWorldState state, SceneLeaseId leaseId) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || current.status() != SceneLeaseStatus.PREPARED
                && (current.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || current.recoveryEvidence().isPresent())) {
            throw new IllegalArgumentException("only an unstarted scene lease can be aborted before materialization");
        }
        for (SceneMember member : current.members()) {
            FencedRecoveryBinding binding = state.fencedRecovery().current().get(bodyRecoveryBindingId(member.actorId()));
            if (binding == null || binding.phase() != FencedRecoveryPhase.PREPARED
                    || binding.asset() != FencedRecoveryAsset.BODY
                    || !binding.ownerId().equals(recoveryOwner(current))
                    || binding.ownerRevision() != current.revision()) {
                throw new IllegalArgumentException("scene preparation abort lacks exact unstarted body authority");
            }
        }
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
    /** Stable physical-body key used by runtime stale-load guards as well as canonical recovery. */
    public static SubjectId bodyRecoveryBindingId(SubjectId actorId) { return new SubjectId("recovery:body_" + actorId.value().replace(':', '_')); }
    /** Stable owner identity for an exact scene epoch; it is deliberately not an ambient roster. */
    public static SubjectId recoveryOwner(SceneLease lease) { return recoveryOwner(lease.id()); }
    /** Stable persisted recovery binding for one exact lease; this is identity translation, never owner discovery. */
    public static SubjectId recoveryOwner(io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId) {
        return new SubjectId("scene:" + leaseId.value().replace(':', '_'));
    }
    /** Stable physical-cargo key shared with the naturally loaded stale-carrier guard. */
    public static SubjectId cargoRecoveryBindingId(SubjectId cargoId) { return new SubjectId("recovery:cargo_" + cargoId.value().replace(':', '_')); }
    private static FencedRecoveryState prepareRecovery(FencedRecoveryState recovery, SceneLease lease) {
        return prepareCargo(prepareBodies(recovery, lease), lease);
    }
    private static FencedRecoveryState prepareBodies(FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery; SubjectId owner = recoveryOwner(lease);
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId());
            next = next.prepare(FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY, owner, lease.revision(), next.nextEpoch(id), true));
        }
        return next;
    }
    private static FencedRecoveryState prepareCargo(FencedRecoveryState recovery, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) return recovery;
        SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId());
        return recovery.prepare(FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.CARGO, recoveryOwner(lease), lease.revision(),
                recovery.nextEpoch(id), true));
    }
    private static FencedRecoveryState reprepareConflictRecovery(FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery; SubjectId owner = recoveryOwner(lease);
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId());
            next = next.supersedeAmbiguous(FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY, owner, lease.revision(),
                    next.nextEpoch(id), true), "scene-conflict-resolved");
        }
        if (!FrontierSceneBehaviors.isLogistics(lease)) return next;
        SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId());
        return next.supersedeAmbiguous(FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.CARGO, owner, lease.revision(),
                next.nextEpoch(id), true), "scene-conflict-resolved");
    }
    private static FencedRecoveryState runningRecovery(FencedRecoveryState recovery, SceneLease lease) {
        return runningCargo(runningBodies(recovery, lease), lease);
    }
    private static FencedRecoveryState runningBodies(FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery;
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId()); FencedRecoveryBinding binding = next.current().get(id);
            if (binding == null) throw new IllegalArgumentException("scene body recovery authority is absent");
            if (binding.phase() == FencedRecoveryPhase.PREPARED) next = next.running(id, binding.authorityEpoch());
            else if (binding.phase() != FencedRecoveryPhase.RUNNING) throw new IllegalArgumentException("scene body recovery authority cannot be reclaimed");
        }
        return next;
    }
    private static FencedRecoveryState runningCargo(FencedRecoveryState recovery, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) return recovery;
        SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId()); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || !binding.ownerId().equals(recoveryOwner(lease)) || binding.ownerRevision() != lease.revision()) {
            throw new IllegalArgumentException("scene cargo recovery authority is absent");
        }
        return binding.phase() == FencedRecoveryPhase.PREPARED ? recovery.running(id, binding.authorityEpoch()) : recovery;
    }
    private static FencedRecoveryState confirmRecovery(FrontierWorldState state, FencedRecoveryState recovery, SceneLease lease) {
        return confirmCargo(state, confirmBodies(state, recovery, lease), lease);
    }
    private static FencedRecoveryState confirmBodies(FrontierWorldState state, FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery;
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId());
            FencedRecoveryBinding binding = next.current().get(id);
            if (state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE) continue;
            if (binding == null) throw new IllegalArgumentException("scene body recovery authority is absent at release");
            long epoch = binding.authorityEpoch(); next = next.observed(id, epoch).confirm(id, epoch);
        }
        return next;
    }
    private static FencedRecoveryState confirmCargo(FrontierWorldState state, FencedRecoveryState recovery, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) return recovery;
        SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId()); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null) throw new IllegalArgumentException("scene cargo recovery authority is absent at release");
        long epoch = binding.authorityEpoch();
        FencedRecoveryState observed = binding.phase() == FencedRecoveryPhase.OBSERVED ? recovery : recovery.observed(id, epoch);
        FencedRecoveryState confirmed = observed.confirm(id, epoch);
        return confirmed.retainCargoRetirement(CargoProjectionRetirement.confirmed(
                lease.withStatus(SceneLeaseStatus.CLOSED), confirmed.tombstones().get(id),
                FrontierSceneBehaviors.logistics(lease).carrierDisposition()));
    }
    static FencedRecoveryState observeCargoCarrier(FencedRecoveryState recovery, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) throw new IllegalArgumentException("cargo recovery requires logistics scene");
        SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId()); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.phase() != FencedRecoveryPhase.RUNNING || !binding.ownerId().equals(recoveryOwner(lease))
                || binding.ownerRevision() != lease.revision()) throw new IllegalArgumentException("cargo recovery observation is stale");
        return recovery.observed(id, binding.authorityEpoch());
    }
    private static FencedRecoveryState revokePreparedRecovery(FencedRecoveryState recovery, SceneLease lease) {
        return revokePreparedCargo(revokePreparedBodies(recovery, lease), lease);
    }
    private static FencedRecoveryState isolateRecovery(FencedRecoveryState recovery, SceneLease lease, String reason) {
        FencedRecoveryState next = recovery;
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId()); FencedRecoveryBinding binding = next.current().get(id);
            if (binding != null) next = next.ambiguous(id, binding.authorityEpoch(), reason, FencedRecoveryDisposition.INSPECT);
        }
        if (FrontierSceneBehaviors.isLogistics(lease)) {
            SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId()); FencedRecoveryBinding binding = next.current().get(id);
            if (binding != null) next = next.ambiguous(id, binding.authorityEpoch(), reason, FencedRecoveryDisposition.INSPECT);
        }
        return next;
    }
    private static FencedRecoveryState revokePreparedBodies(FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery;
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId()); next = next.revokeToCold(id, next.current().get(id).authorityEpoch());
        }
        return next;
    }
    private static FencedRecoveryState revokePreparedCargo(FencedRecoveryState recovery, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) return recovery;
        SubjectId id = cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId()); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null) throw new IllegalArgumentException("prepared scene cargo recovery authority is absent");
        return recovery.revokeToCold(id, binding.authorityEpoch());
    }
    private static FencedRecoveryState revokeReversibleBodies(FencedRecoveryState recovery, SceneLease lease) {
        FencedRecoveryState next = recovery;
        SubjectId owner = recoveryOwner(lease);
        for (SceneMember member : lease.members()) {
            SubjectId id = bodyRecoveryBindingId(member.actorId());
            FencedRecoveryBinding binding = next.current().get(id);
            if (binding == null || binding.asset() != FencedRecoveryAsset.BODY || !binding.ownerId().equals(owner)
                    || binding.ownerRevision() != lease.revision()) {
                throw new IllegalArgumentException("scene recovery revoke lacks exact body authority");
            }
            next = next.revokeToCold(id, binding.authorityEpoch());
        }
        return next;
    }
}
