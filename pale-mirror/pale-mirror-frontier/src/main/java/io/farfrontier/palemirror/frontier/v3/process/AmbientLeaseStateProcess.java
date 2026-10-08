package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical mutations for bounded per-actor ambient execution leases. */
public final class AmbientLeaseStateProcess {
    private AmbientLeaseStateProcess() { }

    public static FrontierWorldState prepare(FrontierWorldState state, AmbientActorLease lease) {
        Objects.requireNonNull(lease, "ambient lease"); ActorLocation actor = state.actorLocations().get(lease.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || lease.status() != AmbientLeaseStatus.PREPARED
                || !(state.actorMovements().containsKey(lease.actorId())
                        ? ActorMovementProcess.bodyAt(state, lease.actorId(), lease.handoffInstant().ticks())
                        : ResidentMealProcess.bodyAt(state, lease.actorId(), lease.handoffInstant().ticks()))
                        .equals(lease.handoffBody()))
            throw new IllegalArgumentException("ambient lease must prepare one living actor at its canonical as-of handoff body");
        if (!HivePhysiologySupport.permitsAmbientLease(state, lease.actorId())) {
            throw new IllegalArgumentException("cocoon-retained bioform may not prepare an ambient lease");
        }
        HiveMobilization returning = io.farfrontier.palemirror.frontier.v3.model.HiveReturnExecutionAuthority.owner(state, lease.actorId()).orElse(null);
        if (returning != null) {
            HiveTaskAssembly.Member member = returning.returnAssembly().orElseThrow().members().get(lease.actorId());
            SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
            if (lease.goal() != AmbientGoalKind.HIVE_TASK_RETURN || !lease.goalBody().equals(expected.standingBody())) {
                throw new IllegalArgumentException("a returning expedition member may prepare only its exact parent-owned return lease");
            }
        }
        FrontierSceneAdmission.GenericAmbientAdmission genericAdmission = FrontierSceneAdmission.genericAmbientAdmission(state);
        if (genericAdmission.reserves(lease.actorId()) && genericAdmission.preLeaseSceneCause(lease.actorId()).isEmpty()) {
            throw new IllegalArgumentException("a strategic scene or engagement exclusively owns the ambient actor");
        }
        if (state.sceneLeases().values().stream().anyMatch(scene -> scene.status() != SceneLeaseStatus.CLOSED
                && scene.members().stream().anyMatch(member -> member.actorId().equals(lease.actorId())))) throw new IllegalArgumentException("scene-leased actor cannot receive an ambient lease");
        AmbientActorLease previous = state.ambientLeases().get(lease.actorId());
        if (previous != null && previous.status() != AmbientLeaseStatus.CLOSED) throw new IllegalArgumentException("actor already has an active ambient lease");
        long expectedRevision = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
        if (lease.revision() != expectedRevision) throw new IllegalArgumentException("ambient lease revision is not the actor's next revision");
        state = ActorBodyAuthority.demand(state, lease.actorId());
        Map<SubjectId, AmbientActorLease> next = new LinkedHashMap<>(state.ambientLeases()); next.put(lease.actorId(), lease);
        var movement = state.actorMovements().get(lease.actorId());
        if (movement != null && movement.coldTravel().isPresent()) {
            if (movement.coldTravel().orElseThrow().authorityEpoch() != lease.revision())
                throw new IllegalArgumentException("movement travel cannot hand off across a foreign authority epoch");
            Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
            actors.put(lease.actorId(), actor.withBody(lease.handoffBody()));
            Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement> movements =
                    new LinkedHashMap<>(state.actorMovements());
            movements.put(lease.actorId(), movement.withoutColdTravel());
            return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                    .ambientLeases(next).actorMovements(movements));
        }
        ResidentMeal meal = state.humanPopulation().meals().get(lease.actorId());
        if (meal == null || meal.coldTravel().isEmpty()) return copy(state, state.actorLocations(), next);
        if (meal.coldTravel().orElseThrow().authorityEpoch() != lease.revision())
            throw new IllegalArgumentException("meal travel cannot hand off across a foreign authority epoch");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(lease.actorId(), actor.withBody(lease.handoffBody()));
        return copy(state, actors, next,
                state.humanPopulation().advanceMeal(meal, meal.withoutColdTravel()));
    }

    public static FrontierWorldState transition(FrontierWorldState state, SubjectId actorId, AmbientLeaseStatus nextStatus) {
        AmbientActorLease current = state.ambientLeases().get(Objects.requireNonNull(actorId, "ambient actor id"));
        if (current == null) throw new IllegalArgumentException("unknown ambient lease actor: " + actorId.value());
        requireTransition(current, nextStatus);
        Map<SubjectId, AmbientActorLease> next = new LinkedHashMap<>(state.ambientLeases()); next.put(actorId, current.withStatus(nextStatus));
        FrontierWorldState changed = copy(state, state.actorLocations(), next);
        if (nextStatus != AmbientLeaseStatus.HOT) return changed;
        if (ActorBodyAuthority.require(changed, ActorBodyAuthority.current(changed, actorId)).phase() != FencedRecoveryPhase.RUNNING)
            throw new IllegalArgumentException("ambient HOT requires independently confirmed physical custody");
        BioformLifecycle lifecycle = changed.hiveColony().bioformLifecycles().get(actorId);
        if (lifecycle == null || lifecycle.phase() != BioformLifecyclePhase.WAKING) return changed;
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(changed.hiveColony().bioformLifecycles());
        lifecycles.put(actorId, lifecycle.active());
        return changed.withChanges(FrontierWorldStateUpdate.begin().hiveColony(changed.hiveColony().withBioformLifecycles(lifecycles)));
    }

    private static void requireTransition(AmbientActorLease current, AmbientLeaseStatus nextStatus) {
        boolean allowed = current.status() == AmbientLeaseStatus.PREPARED && (nextStatus == AmbientLeaseStatus.HOT || nextStatus == AmbientLeaseStatus.DRAINING
                || nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == AmbientLeaseStatus.HOT && (nextStatus == AmbientLeaseStatus.DRAINING || nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == AmbientLeaseStatus.DRAINING && nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                || current.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART && (nextStatus == AmbientLeaseStatus.HOT || nextStatus == AmbientLeaseStatus.DRAINING);
        if (!allowed) throw new IllegalArgumentException("ambient lease transition is not allowed");
    }

    /** Read-only admission: no speculative draining/released world copies or whole-world audit. */
    public static void requireReleaseEligible(FrontierWorldState state, AmbientLeaseReleased release) {
        Objects.requireNonNull(release, "ambient release");
        var current = state.ambientLeases().get(release.actorId());
        if (current == null) throw new IllegalArgumentException("unknown ambient lease actor");
        if (current.status() != AmbientLeaseStatus.DRAINING) requireTransition(current, AmbientLeaseStatus.DRAINING);
        requireReleaseFacts(state, release);
    }

    public static FrontierWorldState release(FrontierWorldState state, AmbientLeaseReleased release) {
        Objects.requireNonNull(release, "ambient release"); AmbientActorLease current = state.ambientLeases().get(release.actorId());
        if (current == null || current.status() != AmbientLeaseStatus.DRAINING) throw new IllegalArgumentException("only a draining ambient lease can be released");
        requireReleaseFacts(state, release);
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases()); leases.put(release.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        return copy(state, state.actorLocations(), leases);
    }

    private static void requireReleaseFacts(FrontierWorldState state, AmbientLeaseReleased release) {
        ActorLocation actor = state.actorLocations().get(release.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient release actor is not alive");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), release.body().supportingSurface().support());
        if (!actor.body().equals(release.body()) || !actor.condition().health().equals(release.health()))
            throw new IllegalArgumentException("ambient release requires independently recorded common body observation");
        // Closing presentation neither releases the common body nor acknowledges
        // a route cursor. Exact registered owners alone assess outstanding effects.
        ActorExecutionComposition.CAPABILITIES.validateAmbientRelease(state, release.actorId());
    }

    /**
     * The physical recovery owner has retained exact custody and observed no live body.
     * An empty loaded anchor alone is not sufficient evidence for this command.
     * The canonical actor remains alive; this only completes the failed physical hand-off so
     * ordinary demand can make a fresh PREPARED lease.  It deliberately does not infer death
     * or synthesize a position/health observation.
     */
    public static FrontierWorldState resolveRestartAbsence(FrontierWorldState state, AmbientLeaseRestartAbsenceObserved absence) {
        Objects.requireNonNull(absence, "restart absence");
        AmbientActorLease current = state.ambientLeases().get(absence.actorId());
        ActorLocation actor = state.actorLocations().get(absence.actorId());
        if (current == null || current.status() != AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !actor.body().equals(absence.body()) || !current.handoffBody().equals(absence.body())) {
            throw new IllegalArgumentException("restart absence must bind the living unknown lease's exact hand-off position");
        }
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases());
        leases.put(absence.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        return copy(state, state.actorLocations(), leases);
    }


    public static FrontierWorldState retarget(FrontierWorldState state, SubjectId actorId, AmbientGoalKind goal, BodyPosition goalBody) {
        return ActorExecutionCoordinator.retargetAmbient(state, actorId, goal, goalBody);
    }

    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors, Map<SubjectId, AmbientActorLease> leases) {
        return copy(state, actors, leases, state.humanPopulation());
    }
    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors, Map<SubjectId, AmbientActorLease> leases,
                                           HumanPopulation population) {
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).ambientLeases(leases).humanPopulation(population));
    }
}
