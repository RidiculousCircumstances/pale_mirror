package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical mutations for bounded per-actor ambient execution leases. */
final class AmbientLeaseStateProcess {
    private AmbientLeaseStateProcess() { }

    static FrontierWorldState prepare(FrontierWorldState state, AmbientActorLease lease) {
        Objects.requireNonNull(lease, "ambient lease"); ActorLocation actor = state.actorLocations().get(lease.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || lease.status() != AmbientLeaseStatus.PREPARED
                || !actor.position().equals(lease.handoffPosition())) throw new IllegalArgumentException("ambient lease must prepare one living actor at its canonical handoff position");
        if (state.sceneLeases().values().stream().anyMatch(scene -> scene.status() != SceneLeaseStatus.CLOSED
                && scene.members().stream().anyMatch(member -> member.actorId().equals(lease.actorId())))) throw new IllegalArgumentException("scene-leased actor cannot receive an ambient lease");
        AmbientActorLease previous = state.ambientLeases().get(lease.actorId());
        if (previous != null && previous.status() != AmbientLeaseStatus.CLOSED) throw new IllegalArgumentException("actor already has an active ambient lease");
        long expectedRevision = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
        if (lease.revision() != expectedRevision) throw new IllegalArgumentException("ambient lease revision is not the actor's next revision");
        Map<SubjectId, AmbientActorLease> next = new LinkedHashMap<>(state.ambientLeases()); next.put(lease.actorId(), lease);
        return copy(state, state.actorLocations(), next);
    }

    static FrontierWorldState transition(FrontierWorldState state, SubjectId actorId, AmbientLeaseStatus nextStatus) {
        AmbientActorLease current = state.ambientLeases().get(Objects.requireNonNull(actorId, "ambient actor id"));
        if (current == null) throw new IllegalArgumentException("unknown ambient lease actor: " + actorId.value());
        boolean allowed = current.status() == AmbientLeaseStatus.PREPARED && (nextStatus == AmbientLeaseStatus.HOT || nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == AmbientLeaseStatus.HOT && (nextStatus == AmbientLeaseStatus.DRAINING || nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == AmbientLeaseStatus.DRAINING && nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                || current.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART && (nextStatus == AmbientLeaseStatus.HOT || nextStatus == AmbientLeaseStatus.DRAINING);
        if (!allowed) throw new IllegalArgumentException("ambient lease transition is not allowed");
        Map<SubjectId, AmbientActorLease> next = new LinkedHashMap<>(state.ambientLeases()); next.put(actorId, current.withStatus(nextStatus));
        return copy(state, state.actorLocations(), next);
    }

    static FrontierWorldState release(FrontierWorldState state, AmbientLeaseReleased release) {
        Objects.requireNonNull(release, "ambient release"); AmbientActorLease current = state.ambientLeases().get(release.actorId());
        if (current == null || current.status() != AmbientLeaseStatus.DRAINING) throw new IllegalArgumentException("only a draining ambient lease can be released");
        ActorLocation actor = state.actorLocations().get(release.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient release actor is not alive");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), release.position());
        ResidentMigrationJourney journey = state.humanPopulation().migration(release.actorId());
        if (journey != null && !release.position().equals(journey.currentPosition())) {
            throw new IllegalArgumentException("HOT transit may return to COLD only at its exact canonical cursor");
        }
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(release.actorId(), new ActorLocation(release.position(), actor.condition().withHealth(release.health())));
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases()); leases.put(release.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        return copy(state, actors, leases);
    }

    static FrontierWorldState recordDeath(FrontierWorldState state, AmbientActorDied death) {
        Objects.requireNonNull(death, "ambient actor death"); AmbientActorLease lease = state.ambientLeases().get(death.actorId());
        if (lease == null || (lease.status() != AmbientLeaseStatus.HOT && lease.status() != AmbientLeaseStatus.DRAINING)) throw new IllegalArgumentException("ambient death is not evidence for an active ambient lease");
        ActorLocation actor = state.actorLocations().get(death.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient actor death is already recorded");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), death.position());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations()); actors.put(death.actorId(), actor.deadAt(death.position()));
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases()); leases.put(death.actorId(), lease.withStatus(AmbientLeaseStatus.CLOSED));
        return copy(state, actors, leases, state.humanPopulation().cancelMigration(death.actorId()));
    }

    static FrontierWorldState retarget(FrontierWorldState state, SubjectId actorId, AmbientGoalKind goal, BlockPosition goalPosition) {
        AmbientActorLease current = state.ambientLeases().get(Objects.requireNonNull(actorId, "ambient actor id"));
        if (current == null || current.status() != AmbientLeaseStatus.HOT) throw new IllegalArgumentException("only a HOT ambient lease may retarget");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), goalPosition);
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases()); leases.put(actorId, current.withGoal(goal, goalPosition));
        return copy(state, state.actorLocations(), leases);
    }

    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors, Map<SubjectId, AmbientActorLease> leases) {
        return copy(state, actors, leases, state.humanPopulation());
    }
    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors, Map<SubjectId, AmbientActorLease> leases,
                                           HumanPopulation population) {
        return new FrontierWorldState(state.bootstrap(), actors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), leases, state.routeConstructions(), state.routeTopology(), state.strategicPlans(), population, state.resourceSites());
    }
}
