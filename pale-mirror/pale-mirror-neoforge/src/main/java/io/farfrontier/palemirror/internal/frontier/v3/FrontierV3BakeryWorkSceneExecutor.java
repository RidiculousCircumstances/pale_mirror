package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;

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
        BakeryWorkGoal goal = BakeryWorkGoal.current(state, job);
        if (ServiceAccessCoordinator.witnessedBakeryExit(state, job, FrontierV3SurfaceObservation.observedBody(worker))) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-access-cleared", lease.id().value(),
                    new BakeryHotAccessCleared(job.id(), lease.id(), FrontierV3SurfaceObservation.observedBody(worker)));
            return;
        }
        if ((goal.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || goal.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY)
                && !ServiceAccessCoordinator.depotAvailableForWork(state,
                        FrontierWorldState.depotId(job.settlementId()), job.id(), job.workerId())) {
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "depot-service-unavailable");
            FrontierV3GoalNavigation.stop(worker);
            return;
        }
        if (ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state, job.workerId(),
                        runtime.checkpointImage().orElseThrow().instant().ticks())
                && FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-resident-yield", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            FrontierV3PhysicalWaitTrace.clear(worker);
            FrontierV3GoalNavigation.stop(worker);
            if (!lease.memberPosition(job.workerId()).equals(goal.station().standingBody())) {
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
            known = BakeryKnownNavigation.pathFrom(state, job, lease.memberPosition(job.workerId()).supportingSurface());
        } catch (IllegalArgumentException unavailable) {
            FrontierV3PhysicalWaitTrace.bakery(worker, state, job, "known-route:" + unavailable.getMessage());
            return; // The job remains retained; an unsupported path is not a fabricated arrival.
        }
        FrontierV3PhysicalWaitTrace.clear(worker);
        MovementOrder order = new MovementOrder(job.id(), job.workerId(), goal.phase().wireTag(),
                1L, List.of(goal.station()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        FrontierV3GoalNavigation.Result movement = FrontierV3GoalNavigation.pursue(level, worker,
                FrontierV3GoalNavigation.Goal.routed(order, known, state.bootstrap().bounds()));
        if (movement.status() == FrontierV3GoalNavigation.Status.BLOCKED
                && movement.blockReason().orElseThrow() != FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED) {
            FrontierV3BakeryPhysicalEffect.block(level, runtime, lease, job,
                    new BakeryWorkBlock(BakeryWorkBlock.Reason.ROUTE_BLOCKED,
                            job.facilityId(), -1, "minecraft:air", 0));
        } else if (job.bakeryWork().orElseThrow().block()
                .map(value -> value.reason() == BakeryWorkBlock.Reason.ROUTE_BLOCKED).orElse(false)
                && movement.status() != FrontierV3GoalNavigation.Status.BLOCKED) {
            FrontierV3BakeryPhysicalEffect.clearBlock(level, runtime, lease, job);
        }
    }

}
