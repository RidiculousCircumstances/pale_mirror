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
        if (StationWorkLabour.attended(state, job) && labour.filter(WorkProgress::running).isPresent()
                && !FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            if (supportedExit.isEmpty()) return; // No fabricated physical pose or work boundary.
            if (!lease.memberBody(state.actorLocations(), job.workerId()).equals(supportedExit.orElseThrow())) {
                FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker);
                return;
            }
            long tick = runtime.canonicalState().orElseThrow().instant().ticks();
            FrontierV3CommandSubmission.submit(runtime, "bakery-labour-paused", job.id().value(),
                    new BakeryWorkChanged(job.id(), lease.id(), execution, supportedExit.orElseThrow(),
                            new BakeryLabourChange(labour, StationWorkLabour.next(state, job, tick, false))));
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
            var movement = FrontierV3ServiceClearanceNavigation.pursue(level, worker, state, job.settlementId(),
                    StationWorkStorage.current(job), job.id(), job.workerId(), goal.phase().wireTag(),
                    1L, actuation);
            if (movement.status() == FrontierV3GoalNavigation.Status.BLOCKED)
                FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "delivered-clearance:" + movement.reason());
            else FrontierV3PhysicalWaitTrace.clear(worker);
            return; // Access completion is witnessed above, never arrival at one preferred cell.
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
            var point = ProductionServiceAccess.point(state, job);
            var waiting = FrontierV3ServiceClearanceNavigation.waitForAccess(level, runtime, worker, state,
                    job.settlementId(), point, job.id(), job.workerId(), goal.phase().wireTag(), 1L, actuation);
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job,
                    "service-access:" + waiting.reason());
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
                    // Vanilla bounds the swing animation; no per-frame custom broadcast or work credit.
                    worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    return;
                }
                boolean run = prior.isEmpty() || prior.orElseThrow().completedAt(tick) < prior.orElseThrow().requiredMilliWork();
                FrontierV3CommandSubmission.submit(runtime, "bakery-station-labour", job.id().value(),
                        new BakeryWorkChanged(job.id(), lease.id(), execution, goal.station().standingBody(),
                                new BakeryLabourChange(prior, StationWorkLabour.next(state, job, tick, run))));
                return;
            }
            if (work.phase() == StationWorkState.Phase.PROCESSING
                    && StationWorkOperation.require(state, job).inputCustody().materials().isPresent())
                return; // The machine effect owner completes and acknowledges this order atomically.
            if (worker instanceof Villager baker)
                FrontierV3StationWorkPhysicalEffect.tick(level, runtime, state, lease, job, baker);
            return;
        }
        List<SurfaceAnchor> known;
        try {
            known = StationWorkNavigation.pathFrom(state, job, lease.memberBody(state.actorLocations(), job.workerId()).supportingSurface());
        } catch (IllegalArgumentException unavailable) {
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "known-route:" + unavailable.getMessage());
            return; // The job remains retained; an unsupported path is not a fabricated arrival.
        }
        MovementOrder order = new MovementOrder(job.id(), job.workerId(), goal.phase().wireTag(),
                1L, List.of(goal.station()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        FrontierV3GoalNavigation.Result movement = FrontierV3GoalNavigation.pursue(level, worker,
                FrontierV3GoalNavigation.Goal.routed(order, known, state.bootstrap().bounds()), actuation);
        StationWorkState work = job.stationWork().orElseThrow();
        FrontierV3PhysicalWaitTrace.clear(worker);
        switch (navigationBlockChange(work.phase(), work.block(), movement)) {
        case BLOCK -> {
            FrontierV3StationWorkPhysicalEffect.block(level, runtime, lease, job,
                    new BakeryWorkBlock(BakeryWorkBlock.Reason.ROUTE_BLOCKED,
                            job.facilityId(), -1, "minecraft:air", 0));
        }
        case CLEAR -> FrontierV3StationWorkPhysicalEffect.clearBlock(level, runtime, lease, job);
        case NONE -> { }
        }
    }

    enum NavigationBlockChange { NONE, BLOCK, CLEAR }

    /** Bakery-owned interpretation; navigation itself does not decide production outcomes. */
    static NavigationBlockChange navigationBlockChange(StationWorkState.Phase phase,
            Optional<BakeryWorkBlock> current, FrontierV3GoalNavigation.Result movement) {
        if (phase == StationWorkState.Phase.DELIVERED) return NavigationBlockChange.NONE;
        if (movement.status() == FrontierV3GoalNavigation.Status.BLOCKED) {
            return movement.blockReason().orElseThrow() == FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED
                    ? NavigationBlockChange.NONE : NavigationBlockChange.BLOCK;
        }
        return current.filter(block -> block.reason() == BakeryWorkBlock.Reason.ROUTE_BLOCKED).isPresent()
                ? NavigationBlockChange.CLEAR : NavigationBlockChange.NONE;
    }
}
