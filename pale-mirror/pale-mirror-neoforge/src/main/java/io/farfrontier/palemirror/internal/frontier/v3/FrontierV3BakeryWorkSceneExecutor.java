package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Optional;

/** HOT movement to the same semantic bakery goals as the COLD order; effects remain job-owned. */
final class FrontierV3BakeryWorkSceneExecutor {
    private FrontierV3BakeryWorkSceneExecutor() { }

    static void drainPending(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, SceneLease lease, ProductionJob job) {
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Villager baker) || !baker.isAlive()
                || !FrontierV3SceneExecutor.recognizes(runtime, baker)) return;
        if (job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DELIVERED
                || !FrontierV3SemanticMovement.arrived(level, baker, BakeryWorkGoal.current(state, job).station())) return;
        FrontierV3BakeryPhysicalEffect.tick(level, runtime, state, lease, job, baker);
    }

    static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                     FrontierWorldState state, SceneLease lease, ProductionJob job) {
        if (job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                && job.bakeryWork().orElseThrow().block()
                    .map(value -> value.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(false)
                && state.inventory().fungibleResources().claims().values().stream()
                    .noneMatch(claim -> claim.claimantId().equals(job.id()))) return;
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
        var actuation = FrontierV3ActorActuation.capture(state, worker, execution, runtime::decodedState);
        if (!actuation.current(worker)) return;
        BakeryWorkGoal goal = BakeryWorkGoal.current(state, job);
        if (ServiceAccessCoordinator.witnessedBakeryExit(state, job, FrontierV3SurfaceObservation.observedBody(worker))) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-access-cleared", lease.id().value(),
                    new BakeryHotAccessCleared(job.id(), lease.id(), FrontierV3SurfaceObservation.observedBody(worker)));
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
        if ((goal.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || goal.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY)
                && !ServiceAccessCoordinator.depotAvailableForWork(state,
                        FrontierWorldState.depotId(job.settlementId()), job.id(), job.workerId())) {
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "depot-service-unavailable");
            FrontierV3GoalNavigation.stop(worker, actuation);
            return;
        }
        if (FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            FrontierV3PhysicalWaitTrace.clear(worker);
            FrontierV3GoalNavigation.stop(worker, actuation);
            if (job.bakeryWork().orElseThrow().block()
                    .filter(block -> !block.requiresPhysicalReconciliation()).isPresent()) {
                FrontierV3BakeryPhysicalEffect.clearBlock(level, runtime, lease, job);
                return;
            }
            if (!lease.memberBody(state.actorLocations(), job.workerId()).equals(goal.station().standingBody())) {
                FrontierV3CommandSubmission.submit(runtime, "bakery-goal-arrived", lease.id().value(),
                        new BakeryHotGoalArrived(job.id(), lease.id(), goal.phase(),
                                FrontierV3SurfaceObservation.observedAt(worker, goal.station())));
                return;
            }
            if (job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DELIVERED) {
                FrontierV3CommandSubmission.submit(runtime, "bakery-delivered-draining", lease.id().value(),
                        new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
                return;
            }
            BakeryWorkState work = job.bakeryWork().orElseThrow();
            if (work.phase() == BakeryWorkState.Phase.PROCESSING
                    && work.completedWorkTicks() < ProductionWorkProgress.REQUIRED_PROCESSING_TICKS) {
                if (level.getGameTime() % 20L == 0L) {
                    FrontierV3ControlledMobMotion.showStationWorkGesture(level, worker);
                    FrontierV3CommandSubmission.submit(runtime, "bakery-station-labor", lease.id().value(),
                            new BakeryHotWorkTick(job.id(), lease.id(), goal.station().standingBody(),
                                    work.completedWorkTicks() + 1));
                }
                return;
            }
            if (worker instanceof Villager baker)
                FrontierV3BakeryPhysicalEffect.tick(level, runtime, state, lease, job, baker);
            return;
        }
        List<SurfaceAnchor> known;
        try {
            known = BakeryKnownNavigation.pathFrom(state, job, lease.memberBody(state.actorLocations(), job.workerId()).supportingSurface());
        } catch (IllegalArgumentException unavailable) {
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "known-route:" + unavailable.getMessage());
            return; // The job remains retained; an unsupported path is not a fabricated arrival.
        }
        MovementOrder order = new MovementOrder(job.id(), job.workerId(), goal.phase().wireTag(),
                1L, List.of(goal.station()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        FrontierV3GoalNavigation.Result movement = FrontierV3GoalNavigation.pursue(level, worker,
                FrontierV3GoalNavigation.Goal.routed(order, known, state.bootstrap().bounds()), actuation);
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        // Delivery is an already confirmed inventory effect. Clearance can wait for
        // navigation, but cannot retroactively block that completed production.
        if (work.phase() == BakeryWorkState.Phase.DELIVERED
                && movement.status() == FrontierV3GoalNavigation.Status.BLOCKED) {
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "delivered-clearance:" + movement.reason());
        } else {
            FrontierV3PhysicalWaitTrace.clear(worker);
        }
        switch (navigationBlockChange(work.phase(), work.block(), movement)) {
        case BLOCK -> {
            FrontierV3BakeryPhysicalEffect.block(level, runtime, lease, job,
                    new BakeryWorkBlock(BakeryWorkBlock.Reason.ROUTE_BLOCKED,
                            job.facilityId(), -1, "minecraft:air", 0));
        }
        case CLEAR -> FrontierV3BakeryPhysicalEffect.clearBlock(level, runtime, lease, job);
        case NONE -> { }
        }
    }

    enum NavigationBlockChange { NONE, BLOCK, CLEAR }

    /** Bakery-owned interpretation; navigation itself does not decide production outcomes. */
    static NavigationBlockChange navigationBlockChange(BakeryWorkState.Phase phase,
            Optional<BakeryWorkBlock> current, FrontierV3GoalNavigation.Result movement) {
        if (phase == BakeryWorkState.Phase.DELIVERED) return NavigationBlockChange.NONE;
        if (movement.status() == FrontierV3GoalNavigation.Status.BLOCKED) {
            return movement.blockReason().orElseThrow() == FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED
                    ? NavigationBlockChange.NONE : NavigationBlockChange.BLOCK;
        }
        return current.filter(block -> block.reason() == BakeryWorkBlock.Reason.ROUTE_BLOCKED).isPresent()
                ? NavigationBlockChange.CLEAR : NavigationBlockChange.NONE;
    }
}
