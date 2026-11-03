package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Optional;

/** HOT movement to the same semantic bakery goals as the COLD order; effects remain job-owned. */
final class FrontierV3StationWorkExecutor {
    private FrontierV3StationWorkExecutor() { }

    static void drainPending(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, SceneLease lease, ProductionJob job) {
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Villager baker) || !baker.isAlive()
                || !FrontierV3SceneExecutor.recognizes(runtime, baker)) return;
        if (job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED
                || !FrontierV3SemanticMovement.arrived(level, baker, StationWorkGoal.current(state, job).station())) return;
        FrontierV3StationWorkPhysicalEffect.tick(level, runtime, state, lease, job, baker);
    }

    static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                     FrontierWorldState state, SceneLease lease, ProductionJob job) {
        if (state.actorExecutions().retainsSuspended(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, job.id())) {
            // The old scope cannot drive or stop the new activity. Resource settlement
            // remains in its owning release path; closing this scope does not remove the body.
            FrontierV3CommandSubmission.submit(runtime, "bakery-suspended-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (job.stationWork().orElseThrow().phase().inputAtSource()
                && job.stationWork().orElseThrow().block()
                    .map(value -> value.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(false)
                && state.inventory().fungibleResources().claims().values().stream()
                    .noneMatch(claim -> claim.claimantId().equals(job.id()))) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-source-replacement-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DEPOT_DELIVERY
                && job.stationWork().orElseThrow().pendingPhysicalStep().isEmpty()
                && io.farfrontier.palemirror.frontier.v3.process.StationWorkStorageReplacement.unavailable(state, job)) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-storage-replacement-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (state.structureConditions().get(job.facilityId()) != StructureCondition.INTACT) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-facility-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) return;
        var execution = state.actorExecutions().current(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION).get(job.workerId());
        if (execution == null || !execution.activityOwnerId().equals(job.id()))
            throw new IllegalArgumentException("bakery command lost its exact admitted execution");
        StationWorkGoal goal = StationWorkGoal.current(state, job);
        var actuation = FrontierV3ActorActuation.capture(state, worker, execution, () -> runtime.decodedState().filter(current -> {
            var retained = current.productionJobs().get(job.id());
            var scope = current.sceneLeases().get(lease.id());
            return retained != null && retained.stationWork().isPresent()
                    && scope != null && scope.status() == SceneLeaseStatus.HOT && scope.revision() == lease.revision()
                    && StationWorkGoal.current(current, retained).equals(goal);
        }));
        if (!actuation.current(worker)) return;
        if (job.stationWork().orElseThrow().pendingPhysicalStep().isEmpty()
                && state.inventory().surfaces().get(ProductionStationEnergy.record(state,
                    job.stationWork().orElseThrow().stationId()).id()).status() == ContainerSurfaceStatus.RETIRED) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            FrontierV3CommandSubmission.submit(runtime, "bakery-machine-retired", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        var supportedExit = FrontierV3SupportedBodyCapture.observe(level, worker);
        var labour = StationWorkLabour.progress(state, job);
        if (StationWorkLabour.attended(state, job) && (labour.filter(WorkProgress::running).isPresent() || StationWorkLabour.holdsAttendance(state, job))
                && !FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            if (supportedExit.isEmpty()) return; // No fabricated physical pose or work boundary.
            if (!lease.memberBody(state.actorLocations(), job.workerId()).equals(supportedExit.orElseThrow())) {
                FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker);
                return;
            }
            long tick = runtime.canonicalState().orElseThrow().instant().ticks();
            if (!StationWorkLabour.updateNeeded(state, job, tick, false)) return;
            FrontierV3CommandSubmission.submit(runtime, "bakery-labour-paused", job.id().value(),
                    new BakeryWorkChanged(job.id(), lease.id(), execution, supportedExit.orElseThrow(),
                            StationWorkLabour.propose(state, job, tick, false)));
            return;
        }
        if (supportedExit.isPresent() && StationWorkGoal.deliveryAccessCleared(state, job, supportedExit.orElseThrow())) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            if (!state.actorLocations().get(job.workerId()).body().equals(supportedExit.orElseThrow()))
                FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker);
            else FrontierV3CommandSubmission.submit(runtime, "bakery-delivered-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (supportedExit.isPresent() && ProductionServiceAccess.witnessedExit(
                state, job, supportedExit.orElseThrow())) {
            FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker);
            return;
        }
        if (ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state, job.workerId(),
                        runtime.canonicalState().orElseThrow().instant().ticks())
                && FrontierV3SupportedBodyCapture.observe(level, worker).isPresent()) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            FrontierV3CommandSubmission.submit(runtime, "bakery-resident-yield", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (goal.phase() == StationWorkState.Phase.DELIVERED) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            FrontierV3CommandSubmission.submit(runtime, "station-travel-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if ((goal.phase().sourcePickup()
                || goal.phase() == StationWorkState.Phase.DEPOT_DELIVERY
                || goal.phase() == StationWorkState.Phase.TARGET_LOAD
                || goal.phase() == StationWorkState.Phase.STATION_LOAD
                || goal.phase() == StationWorkState.Phase.STATION_UNLOAD)
                // A durable prepared transfer settles under its original witness;
                // only a new transfer can yield to a later service contender.
                && job.stationWork().orElseThrow().pendingPhysicalStep().isEmpty()
                && !ProductionServiceAccess.available(state, job)) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            FrontierV3CommandSubmission.submit(runtime, "station-access-draining", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        FrontierV3ServiceApproachNavigation.forget(worker);
        if (FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            FrontierV3PhysicalWaitTrace.clear(worker);
            FrontierV3GoalNavigation.stop(worker, actuation);
            if (job.stationWork().orElseThrow().block()
                    .filter(block -> !block.requiresPhysicalReconciliation()).isPresent()) {
                FrontierV3StationWorkPhysicalEffect.clearBlock(level, runtime, lease, job);
                return;
            }
            if (!lease.memberBody(state.actorLocations(), job.workerId()).equals(goal.station().standingBody())) {
                FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker);
                return;
            }
            StationWorkState work = job.stationWork().orElseThrow();
            if (work.phase() == StationWorkState.Phase.PROCESSING && !StationWorkLabour.complete(state, job)) {
                if (!StationWorkLabour.attended(state, job)) return;
                long tick = runtime.canonicalState().orElseThrow().instant().ticks();
                var prior = StationWorkLabour.progress(state, job);
                if (prior.filter(progress -> progress.running() && tick < progress.activeUntilTick()).isPresent()) {
                    // The bounded pose pulse carries no work credit and is throttled per native body.
                    FrontierV3WorkPresentation.pulse(worker,io.farfrontier.palemirror.internal.network.ResidentWorkGesture.HANDLE);
                    return;
                }
                boolean run = prior.isEmpty() || prior.orElseThrow().completedAt(tick) < prior.orElseThrow().requiredMilliWork();
                if (!StationWorkLabour.updateNeeded(state, job, tick, run)) return;
                FrontierV3CommandSubmission.submit(runtime, "bakery-station-labour", job.id().value(),
                        new BakeryWorkChanged(job.id(), lease.id(), execution, goal.station().standingBody(),
                                StationWorkLabour.propose(state, job, tick, run)));
                return;
            }
            if (work.phase() == StationWorkState.Phase.PROCESSING
                    && StationWorkOperation.require(state, job).inputCustody().materials().isPresent())
                return; // The machine effect owner completes and acknowledges this order atomically.
            if (worker instanceof Villager baker)
                FrontierV3StationWorkPhysicalEffect.tick(level, runtime, state, lease, job, baker);
            return;
        }
        // The effect scene never pursues the next station. Release the same body;
        // the production owner will issue its common ActorMovement leg after closure.
        if (job.stationWork().orElseThrow().pendingPhysicalStep().isPresent()) return;
        FrontierV3GoalNavigation.stop(worker, actuation);
        FrontierV3CommandSubmission.submit(runtime, "station-next-leg-draining", lease.id().value(),
                new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
    }
}
