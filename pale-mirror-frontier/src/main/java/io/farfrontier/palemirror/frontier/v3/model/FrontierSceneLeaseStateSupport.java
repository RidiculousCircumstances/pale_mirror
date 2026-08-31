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
final class FrontierSceneLeaseStateSupport {
    private static final int MAX_SCENE_LEASES = 1_024;
    private FrontierSceneLeaseStateSupport() { }

    static FrontierWorldState prepare(FrontierWorldState state, SceneLease lease) {
        return copy(state, state.actorLocations(), withPreparedLease(state, lease), state.ambientLeases(), state.strategicPlans());
    }

    private static Map<SceneLeaseId, SceneLease> withPreparedLease(FrontierWorldState state, SceneLease lease) {
        Objects.requireNonNull(lease, "scene lease");
        if (state.sceneLeases().containsKey(lease.id())) throw new IllegalArgumentException("scene lease identity already exists: " + lease.id().value());
        if (lease.status() != SceneLeaseStatus.PREPARED) throw new IllegalArgumentException("new scene lease must be prepared");
        if (lease.members().stream().anyMatch(member -> state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE)) {
            throw new IllegalArgumentException("scene lease cannot materialize a dead actor");
        }
        if (lease.members().stream().anyMatch(member -> !state.actorLocations().get(member.actorId()).position().equals(lease.memberPosition(member.actorId())))) {
            throw new IllegalArgumentException("scene lease must retain every exact canonical member position");
        }
        FrontierSceneBehaviors.validatePrepared(state, lease);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        int requiredCompaction = leases.size() - MAX_SCENE_LEASES + 1;
        if (requiredCompaction > 0) {
            List<SceneLease> terminal = leases.values().stream().filter(existing -> existing.status() == SceneLeaseStatus.CLOSED)
                    .sorted(Comparator.comparing(SceneLease::handoffInstant).thenComparing(existing -> existing.id().value())).toList();
            if (terminal.size() < requiredCompaction) throw new IllegalArgumentException("scene lease retention limit has no terminal leases to compact");
            terminal.stream().limit(requiredCompaction).forEach(existing -> leases.remove(existing.id()));
        }
        leases.put(lease.id(), lease);
        return leases;
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
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), capture.position());
            actors.put(capture.actorId(), new ActorLocation(capture.position(), actor.condition().withHealth(capture.health())));
            ambient.put(capture.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        }
        return copy(state, actors, withPreparedLease(state, handoff.lease()), ambient, state.strategicPlans());
    }

    static FrontierWorldState transition(FrontierWorldState state, SceneLeaseId leaseId, SceneLeaseStatus nextStatus) {
        SceneLease current = state.sceneLeases().get(leaseId);
        if (current == null || !current.status().canTransitionTo(nextStatus)) throw new IllegalArgumentException("scene lease transition is not allowed");
        StrategicPlanState plans = FrontierSceneBehaviors.transitionPlans(state, current, nextStatus);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(nextStatus));
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), plans);
    }

    /**
     * Records a naturally loaded recovery observation without inventing a death, body, cargo
     * hand-off or COLD continuation.  The owning operation is subsequently blocked by the same
     * transaction, so an uninspectable old scene cannot monopolize its settlement forever.
     */
    static FrontierWorldState recoveryUnresolved(FrontierWorldState state, SceneLeaseRecoveryUnresolved unresolved) {
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
        return copy(state, state.actorLocations(), leases, state.ambientLeases(), state.strategicPlans());
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
            ActorLocation currentActor = actors.get(position.actorId());
            // The operation cursor is the durable HOT/COLD hand-off.  A body may have been
            // halfway through ordinary Minecraft movement when its chunk vanished, but that
            // transient sub-cell location must not become a second strategic travel state.
            BlockPosition canonical = FrontierSceneBehaviors.releasedPosition(state, current, position.actorId(), position.position());
            actors.put(position.actorId(), new ActorLocation(canonical, currentActor.condition().withHealth(position.health())));
        }
        StrategicPlanState plans = FrontierSceneBehaviors.releasePlans(state, current);
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(leaseId, current.withStatus(SceneLeaseStatus.CLOSED));
        return copy(state, actors, leases, state.ambientLeases(), plans);
    }

    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors,
                                           Map<SceneLeaseId, SceneLease> leases, Map<SubjectId, AmbientActorLease> ambient,
                                           StrategicPlanState plans) {
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).sceneLeases(leases)
                .ambientLeases(ambient).strategicPlans(plans));
    }
}
