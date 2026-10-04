package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;

import java.util.LinkedHashMap;
import java.util.List;

/** Canonical admission and reduction for observations from non-leased ambient bodies. */
public final class AmbientActorProcess {
    private AmbientActorProcess() { }


    public static CommandPlan plan(FrontierWorldState state, AmbientActorObserved observation) {
        try {
            validate(state, observation.actorId(), observation.body());
            requireUnleased(state, observation.actorId());
        } catch (IllegalArgumentException invalid) {
            return rejected(invalid);
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, observation.actorId()), observation)));
    }

    public static CommandPlan plan(FrontierWorldState state, AmbientLeasePrepared prepared) {
        try { AmbientLeaseStateProcess.prepare(state, prepared.lease()); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, prepared.lease().actorId()), prepared)));
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseTransition transition) {
        try {
            AmbientActorLease current = state.ambientLeases().get(transition.actorId());
            if (current != null && current.status() == AmbientLeaseStatus.PREPARED && transition.status() == AmbientLeaseStatus.HOT)
                throw new IllegalArgumentException("fresh HOT admission requires exact physical body confirmation");
            AmbientLeaseStateProcess.transition(state, transition.actorId(), transition.status());
        } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, transition.actorId()), transition)));
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseReleased release) {
        return plan(state, release, -1L);
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseReleased release, long currentTick) {
        try { AmbientLeaseStateProcess.release(state, release); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        java.util.ArrayList<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(owner(state, release.actorId()), release));
        resumeMealAfterLease(state, release.actorId(), currentTick, events);
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseRestartAbsenceObserved absence) {
        return plan(state, absence, -1L);
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseRestartAbsenceObserved absence, long currentTick) {
        try { AmbientLeaseStateProcess.resolveRestartAbsence(state, absence); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        java.util.ArrayList<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(owner(state, absence.actorId()), absence));
        resumeMealAfterLease(state, absence.actorId(), currentTick, events);
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    private static void resumeMealAfterLease(FrontierWorldState state, SubjectId actorId, long currentTick,
                                             java.util.List<ProposedEvent> events) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (currentTick < 0L) return;
        var movement = state.actorMovements().get(actorId);
        if (movement != null) {
            events.add(new ProposedEvent(actorId, new ScheduleEffect.Rescheduled(
                    ActorMovementProcess.progress(movement, Math.addExact(movement.issuedAtTick(), 1L)).id(),
                    ActorMovementProcess.progress(movement, Math.max(Math.addExact(currentTick, 1L),
                            Math.addExact(movement.issuedAtTick(), 1L))))));
        }
        if (meal == null) return;
        events.add(new ProposedEvent(actorId, new ScheduleEffect.Rescheduled(
                ResidentMealProcess.progress(meal, Math.addExact(meal.startedAtTick(), 1L)).id(),
                ResidentMealProcess.progress(meal, Math.max(Math.addExact(currentTick, 1L),
                        Math.addExact(meal.startedAtTick(), 1L))))));
    }
    public static CommandPlan planLease(FrontierWorldState state,
                                        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload,
                                        long currentTick) {
        return switch (payload) {
            case AmbientBodyConfirmed confirmed -> plan(state, confirmed);
            case AmbientLeasePrepared prepared -> plan(state, prepared);
            case AmbientLeaseTransition transition -> plan(state, transition);
            case AmbientLeaseReleased release -> plan(state, release, currentTick);
            case AmbientLeaseRestartAbsenceObserved absence -> plan(state, absence, currentTick);
            default -> throw new IllegalArgumentException("payload is not an ambient lease transition");
        };
    }

    public static AmbientActorLease nextLease(FrontierWorldState state, SubjectId actorId, SimInstant instant) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient lease requires a living canonical actor");
        if (!HivePhysiologySupport.permitsAmbientLease(state, actorId)) throw new IllegalArgumentException("cocoon-retained or unconfirmed waking bioform may not receive an ambient lease");
        AmbientActorLease previous = state.ambientLeases().get(actorId);
        long revision = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
        AmbientGoal goal = goalFor(state, actorId, instant.ticks());
        return new AmbientActorLease(actorId, state.actorMovements().containsKey(actorId)
                ? ActorMovementProcess.bodyAt(state, actorId, instant.ticks())
                : ResidentMealProcess.bodyAt(state, actorId, instant.ticks()), instant,
                revision, AmbientLeaseStatus.PREPARED, goal.kind(), BodyPosition.above(new SurfaceAnchor(goal.position())));
    }


    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorObserved observation) {
        validate(state, observation.actorId(), observation.body());
        requireUnleased(state, observation.actorId());
        if (!subject.equals(owner(state, observation.actorId()))) throw new IllegalArgumentException("ambient actor observation lacks its canonical owner");
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(observation.actorId(), new ActorLocation(observation.body(), state.actorLocations().get(observation.actorId()).condition().withHealth(observation.health()), state.actorLocations().get(observation.actorId()).kind()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(nextActors));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, SimInstant instant, AmbientLeasePrepared prepared) {
        if (!subject.equals(owner(state, prepared.lease().actorId())) || !prepared.lease().handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("ambient lease lacks its owner or current handoff instant");
        }
        return AmbientLeaseStateProcess.prepare(state, prepared.lease());
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseTransition transition) {
        if (!subject.equals(owner(state, transition.actorId()))) throw new IllegalArgumentException("ambient lease transition lacks its canonical owner");
        return AmbientLeaseStateProcess.transition(state, transition.actorId(), transition.status());
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseReleased release) {
        if (!subject.equals(owner(state, release.actorId()))) throw new IllegalArgumentException("ambient lease release lacks its canonical owner");
        return AmbientLeaseStateProcess.release(state, release);
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseRestartAbsenceObserved absence) {
        if (!subject.equals(owner(state, absence.actorId()))) throw new IllegalArgumentException("ambient restart absence lacks its canonical owner");
        return AmbientLeaseStateProcess.resolveRestartAbsence(state, absence);
    }
    public static FrontierWorldState reduceLease(FrontierWorldState state, SubjectId subject, SimInstant instant, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return switch (payload) {
            case AmbientBodyConfirmed confirmed -> {
                if (!subject.equals(owner(state, confirmed.actorId()))) throw new IllegalArgumentException("body confirmation lacks its canonical owner");
                yield AmbientBodyConfirmationProcess.reduce(state, confirmed);
            }
            case AmbientLeasePrepared prepared -> reduce(state, subject, instant, prepared);
            case AmbientLeaseTransition transition -> reduce(state, subject, transition);
            case AmbientLeaseReleased release -> reduce(state, subject, release);
            case AmbientLeaseRestartAbsenceObserved absence -> reduce(state, subject, absence);
            default -> throw new IllegalArgumentException("payload is not an ambient lease transition");
        };
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientBodyConfirmed confirmed) {
        try { AmbientBodyConfirmationProcess.reduce(state, confirmed); }
        catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, confirmed.actorId()), confirmed)));
    }

    private static void requireUnleased(FrontierWorldState state, SubjectId actorId) {
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) {
            throw new IllegalArgumentException("leased ambient actor observation must use its ambient lease evidence");
        }
    }

    private static void validate(FrontierWorldState state, SubjectId actorId, BodyPosition body) {
        ActorLocation current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("ambient actor observation is not evidence for a living canonical actor");
        }
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))) {
            throw new IllegalArgumentException("leased actor observation must use its scene lease evidence");
        }
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), body.supportingSurface().support());
        if (owner(state, actorId) == null) throw new IllegalArgumentException("ambient actor has no canonical owner");
    }

    public static AmbientGoal goalFor(FrontierWorldState state, SubjectId actorId, long atTick) {
        ActorLocation declaration = state.actorLocations().get(actorId);
        if (declaration == null) throw new IllegalArgumentException("ambient goal lacks its declared canonical actor");
        HiveMobilization assemblingMobilization = io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyExecutionAuthority.owner(state, actorId).orElse(null);
        if (assemblingMobilization != null) {
            if (assemblingMobilization.status() != HiveMobilizationStatus.ASSEMBLING)
                return new AmbientGoal(AmbientGoalKind.HIVE_TASK_ASSEMBLY, state.actorLocations().get(actorId).supportingSurface().support());
            HiveTaskAssembly.Member member = assemblingMobilization.assembly().orElseThrow().members().get(actorId);
            SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
            return new AmbientGoal(AmbientGoalKind.HIVE_TASK_ASSEMBLY, target.support());
        }
        HiveMobilization returningMobilization = io.farfrontier.palemirror.frontier.v3.model.HiveReturnExecutionAuthority.owner(state, actorId).orElse(null);
        if (returningMobilization != null) {
            HiveTaskAssembly.Member member = returningMobilization.returnAssembly().orElseThrow().members().get(actorId);
            SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
            return new AmbientGoal(AmbientGoalKind.HIVE_TASK_RETURN, target.support());
        }
        EngineeringWorkOrder engineering = io.farfrontier.palemirror.frontier.v3.model.EngineeringExecutionAuthority.owner(state, actorId).orElse(null);
        if (engineering != null) {
            if (engineering.assembly().isEmpty()) return new AmbientGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY,
                    state.actorLocations().get(actorId).supportingSurface().support());
            EngineeringWorkAssembly.Member member = engineering.assembly().orElseThrow().members().get(actorId);
            BlockPosition target = member.arrived() ? member.currentPosition() : member.nextSurface().support();
            return new AmbientGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, target);
        }
        RouteOperation assembling = OperationExecutionAuthority.assemblyOwner(state, actorId).orElse(null);
        if (assembling != null) {
            OperationAssembly.Member member = assembling.activeAssembly().orElseThrow().members().get(actorId);
            SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
            return new AmbientGoal(AmbientGoalKind.OPERATION_ASSEMBLY, target.support());
        }
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (journey != null) {
            BlockPosition target = journey.status() == ResidentMigrationStatus.EN_ROUTE && !journey.arriving()
                    ? journey.nextColdPosition() : journey.currentPosition();
            return new AmbientGoal(AmbientGoalKind.TRANSIT, target);
        }
        if (declaration.kind() == ActorKind.RESIDENT) {
            ResidentProfile resident = state.humanPopulation().resident(actorId);
            if (resident == null) throw new IllegalArgumentException("declared resident goal lacks its exact resident profile");
            var movement = state.actorMovements().get(actorId);
            if (movement != null) return new AmbientGoal(AmbientGoalKind.ACTOR_MOVEMENT,
                    movement.order().legalStations().getFirst().support());
            ResidentMeal meal = state.humanPopulation().meals().get(actorId);
            if (meal != null) return new AmbientGoal(AmbientGoalKind.MEAL,
                    ResidentMealProcess.goalSurface(state, meal).support());
            ResidentActivityChoice choice = ResidentActivityCoordinator.assess(state, actorId, atTick);
            HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(actorId);
            ResourceSiteHarvestJob harvest = assignment.kind() == HumanAssignmentKind.FIELD_HARVEST
                    && choice.kind() == ResidentActivityChoice.Kind.WORK
                    ? state.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                    .filter(job -> job.id().equals(assignment.ownerId().orElseThrow(() ->
                            new IllegalStateException("field-harvest assignment lacks its retained job"))))
                    .reduce((left, right) -> { throw new IllegalStateException("field-harvest assignment has duplicate retained jobs"); })
                    .orElseThrow(() -> new IllegalStateException("field-harvest assignment has no active retained job"))
                    : null;
            if (harvest != null) {
                // A generic lease may be prepared to materialize this registered field scene.
                // Its fallback goal is the actor's actual retained support, never the
                // obsolete route cursor or an earlier post-harvest departure.
                return new AmbientGoal(AmbientGoalKind.WORK,
                        state.actorLocations().get(harvest.workerId()).supportingSurface().support());
            }
            Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
            if (resident.profession() == ResidentProfession.SECURITY_WORKER) return new AmbientGoal(AmbientGoalKind.GUARD, settlement.anchor());
            // Only a retained process owns purposeful work travel.  Profession is neither a
            // route nor a work assignment: using the agricultural fallback to send a newly
            // admitted body to the field-edge return surface made every idle farmer visibly
            // converge there on first ingress.  PATROL keeps the physical body at its exact
            // retained canonical support.  A completed harvest has already checkpointed the
            // same actor at its observed terminal body, while active field, production,
            // engineering, operation, migration and assembly paths resolve above with their
            // exact retained cursors.
            return new AmbientGoal(AmbientGoalKind.PATROL,
                    state.actorLocations().get(actorId).body().supportingSurface().support());
        }
        Bioform bioform = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), actorId);
        BlockPosition nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(bioform.nestId())).findFirst().orElseThrow().anchor();
        if (bioform.isScout()) return new AmbientGoal(AmbientGoalKind.SCOUT_PATROL,
                HiveScoutPatrolProcess.nextPosition(state, bioform));
        return new AmbientGoal(bioform.isDefender() ? AmbientGoalKind.GUARD : AmbientGoalKind.PATROL, nest);
    }

    private static CommandPlan.Rejected rejected(IllegalArgumentException invalid) {
        return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
    }

    private static SubjectId owner(FrontierWorldState state, SubjectId actorId) {
        return FrontierWorldStateSupport.actorOwner(state, actorId);
    }
    public record AmbientGoal(AmbientGoalKind kind, BlockPosition position) { }
}
