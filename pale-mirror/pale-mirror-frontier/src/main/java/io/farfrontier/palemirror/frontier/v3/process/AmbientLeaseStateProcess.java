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
        HiveMobilization returning = state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.status() == HiveMobilizationStatus.RETURNING)
                .filter(value -> value.returnAssembly().map(assembly -> assembly.members().containsKey(lease.actorId())).orElse(false))
                .reduce((left, right) -> { throw new IllegalArgumentException("ambient bioform belongs to more than one hive return"); })
                .orElse(null);
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
        boolean allowed = current.status() == AmbientLeaseStatus.PREPARED && (nextStatus == AmbientLeaseStatus.HOT || nextStatus == AmbientLeaseStatus.DRAINING
                || nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == AmbientLeaseStatus.HOT && (nextStatus == AmbientLeaseStatus.DRAINING || nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == AmbientLeaseStatus.DRAINING && nextStatus == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                || current.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART && (nextStatus == AmbientLeaseStatus.HOT || nextStatus == AmbientLeaseStatus.DRAINING);
        if (!allowed) throw new IllegalArgumentException("ambient lease transition is not allowed");
        Map<SubjectId, AmbientActorLease> next = new LinkedHashMap<>(state.ambientLeases()); next.put(actorId, current.withStatus(nextStatus));
        FrontierWorldState changed = copy(state, state.actorLocations(), next);
        if (nextStatus != AmbientLeaseStatus.HOT) return changed;
        changed = ActorBodyAuthority.observedPresent(changed, actorId);
        BioformLifecycle lifecycle = changed.hiveColony().bioformLifecycles().get(actorId);
        if (lifecycle == null || lifecycle.phase() != BioformLifecyclePhase.WAKING) return changed;
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(changed.hiveColony().bioformLifecycles());
        lifecycles.put(actorId, lifecycle.active());
        return changed.withChanges(FrontierWorldStateUpdate.begin().hiveColony(changed.hiveColony().withBioformLifecycles(lifecycles)));
    }

    public static FrontierWorldState release(FrontierWorldState state, AmbientLeaseReleased release) {
        Objects.requireNonNull(release, "ambient release"); AmbientActorLease current = state.ambientLeases().get(release.actorId());
        if (current == null || current.status() != AmbientLeaseStatus.DRAINING) throw new IllegalArgumentException("only a draining ambient lease can be released");
        ActorLocation actor = state.actorLocations().get(release.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient release actor is not alive");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), release.body().supportingSurface().support());
        ResidentMigrationJourney journey = state.humanPopulation().migration(release.actorId());
        if (journey != null && !release.body().supportingSurface().support().equals(journey.currentPosition())) {
            throw new IllegalArgumentException("HOT transit may return to COLD only at its exact canonical cursor");
        }
        RouteOperation assembling = state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.ASSEMBLING)
                .filter(operation -> operation.activeAssembly().map(assembly -> assembly.members().containsKey(release.actorId())).orElse(false)).findFirst().orElse(null);
        if (assembling != null && !release.body().supportingSurface().support().equals(assembling.activeAssembly().orElseThrow().members().get(release.actorId()).currentSurface().support())) {
            throw new IllegalArgumentException("HOT operation assembly may return to COLD only at its exact cursor");
        }
        EngineeringWorkOrder engineering = java.util.stream.Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream())
                .filter(project -> project.assembly().map(assembly -> assembly.members().containsKey(release.actorId())).orElse(false))
                .reduce((left, right) -> { throw new IllegalArgumentException("ambient actor belongs to more than one engineering journey"); }).orElse(null);
        if (engineering != null) {
            EngineeringWorkAssembly.Member member = engineering.assembly().orElseThrow().members().get(release.actorId());
            if (current.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY || !release.body().supportingSurface().support().equals(member.currentPosition())) {
                throw new IllegalArgumentException("HOT engineering assembly may return to COLD only at its exact cursor");
            }
        }
        HiveMobilization mobilization = state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.status() == HiveMobilizationStatus.ASSEMBLING)
                .filter(value -> value.assembly().map(assembly -> assembly.members().containsKey(release.actorId())).orElse(false))
                .reduce((left, right) -> { throw new IllegalArgumentException("ambient bioform belongs to more than one hive assembly"); })
                .orElse(null);
        if (mobilization != null) {
            HiveTaskAssembly.Member member = mobilization.assembly().orElseThrow().members().get(release.actorId());
            if (current.goal() != AmbientGoalKind.HIVE_TASK_ASSEMBLY
                    || !release.body().supportingSurface().equals(member.currentSurface())) {
                throw new IllegalArgumentException("HOT hive task assembly may return to COLD only at its exact retained cursor");
            }
        }
        HiveMobilization returning = state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.status() == HiveMobilizationStatus.RETURNING)
                .filter(value -> value.returnAssembly().map(assembly -> assembly.members().containsKey(release.actorId())).orElse(false))
                .reduce((left, right) -> { throw new IllegalArgumentException("ambient bioform belongs to more than one hive return"); })
                .orElse(null);
        if (returning != null) {
            HiveTaskAssembly.Member member = returning.returnAssembly().orElseThrow().members().get(release.actorId());
            if (current.goal() != AmbientGoalKind.HIVE_TASK_RETURN
                    || !release.body().supportingSurface().equals(member.currentSurface())) {
                throw new IllegalArgumentException("HOT hive return may return to COLD only at its exact retained cursor");
            }
        }
        ResourceSiteHarvestJob harvest = state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .filter(job -> job.workerId().equals(release.actorId()))
                .reduce((left, right) -> { throw new IllegalArgumentException("ambient farmer belongs to more than one active field job"); })
                .orElse(null);
        if (harvest != null) {
            if (current.goal() != AmbientGoalKind.PATROL && current.goal() != AmbientGoalKind.WORK
                    && !(current.goal() == AmbientGoalKind.ACTOR_MOVEMENT
                        && state.actorMovements().containsKey(release.actorId()))
                    && !(current.goal() == AmbientGoalKind.MEAL
                        && state.humanPopulation().meals().containsKey(release.actorId()))) {
                throw new IllegalArgumentException("HOT field worker has a foreign ambient purpose");
            }
        }
        if (state.humanPopulation().meals().containsKey(release.actorId())
                && state.humanPopulation().meals().get(release.actorId()).pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("ambient meal has an unresolved physical effect");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(release.actorId(), new ActorLocation(release.body(), actor.condition().withHealth(release.health()), actor.kind()));
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases()); leases.put(release.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        return copy(state, actors, leases);
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

    public static FrontierWorldState recordDeath(FrontierWorldState state, AmbientActorDied death) {
        Objects.requireNonNull(death, "ambient actor death"); AmbientActorLease lease = state.ambientLeases().get(death.actorId());
        if (lease == null || (lease.status() != AmbientLeaseStatus.HOT && lease.status() != AmbientLeaseStatus.DRAINING)) throw new IllegalArgumentException("ambient death is not evidence for an active ambient lease");
        ActorLocation actor = state.actorLocations().get(death.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient actor death is already recorded");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), death.body().supportingSurface().support());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations()); actors.put(death.actorId(), actor.deadAt(death.body()));
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>(state.ambientLeases()); leases.put(death.actorId(), lease.withStatus(AmbientLeaseStatus.CLOSED));
        var recovery = state.fencedRecovery();
        if (recovery.current().containsKey(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(death.actorId())))
            recovery = ActorBodyAuthority.observedDeath(state, ActorBodyAuthority.current(state, death.actorId()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).ambientLeases(leases)
                .humanPopulation(state.humanPopulation().cancelMigration(death.actorId()))
                .fencedRecovery(recovery)
                .actorExecutions(HumanPopulationStateSupport.migrationRetirement(state, death.actorId())));
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
