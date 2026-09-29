package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** HOT movement to the same semantic bakery goals as the COLD order; effects remain job-owned. */
final class FrontierV3BakeryWorkSceneExecutor {
    private static final int LOCAL_LEG = 8;
    private static final Map<Mob, LegControl> LEGS = new WeakHashMap<>();
    private FrontierV3BakeryWorkSceneExecutor() { }

    static void drainPending(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, SceneLease lease, ProductionJob job) {
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Villager baker) || !baker.isAlive()
                || !FrontierV3SceneExecutor.recognizes(runtime, baker)) return;
        if (!FrontierV3SemanticMovement.arrived(level, baker, BakeryWorkGoal.current(state, job).station())) return;
        FrontierV3BakeryPhysicalEffect.tick(level, runtime, state, lease, job, baker);
    }

    static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                     FrontierWorldState state, SceneLease lease, ProductionJob job) {
        if (job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                && job.bakeryWork().orElseThrow().block()
                    .map(value -> value.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(false)
                && state.inventory().fungibleResources().claims().values().stream()
                    .noneMatch(claim -> claim.claimantId().equals(job.id()))) return;
        if (job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DELIVERED) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-delivered-draining", lease.id().value(),
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
        BakeryWorkGoal goal = BakeryWorkGoal.current(state, job);
        if (job.bakeryWork().orElseThrow().pendingPhysicalStep().isEmpty()
                && !state.inventory().fungibleResources().accounts().containsKey(
                        job.bakeryWork().orElseThrow().actorAccountId())
                && ResidentActivityCoordinator.requestsYield(state, job.workerId(),
                        runtime.checkpointImage().orElseThrow().instant().ticks())
                && FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-resident-yield", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (FrontierV3SemanticMovement.arrived(level, worker, goal.station())) {
            LEGS.remove(worker);
            FrontierV3GoalNavigation.stop(worker);
            if (!lease.memberPosition(job.workerId()).equals(goal.station().standingBody())) {
                FrontierV3CommandSubmission.submit(runtime, "bakery-goal-arrived", lease.id().value(),
                        new BakeryHotGoalArrived(job.id(), lease.id(), goal.phase(),
                                FrontierV3SurfaceObservation.observedAt(worker, goal.station())));
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
            return; // The job remains retained; an unsupported path is not a fabricated arrival.
        }
        LocalNavigationEnvelope envelope;
        int targetIndex = waypointIndex(level, worker, known, lease.id(), goal.phase());
        SurfaceAnchor waypoint = known.get(targetIndex);
        try {
            envelope = LocalNavigationEnvelope.localLeg(known.subList(Math.max(0, targetIndex - LOCAL_LEG), targetIndex + 1), waypoint);
        } catch (IllegalArgumentException tooWide) {
            return;
        }
        MovementOrder leg = new MovementOrder(job.id(), job.workerId(), goal.phase().wireTag(),
                targetIndex + 1L, List.of(waypoint), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        FrontierV3GoalNavigation.Result movement = FrontierV3GoalNavigation.pursue(level, worker,
                new FrontierV3GoalNavigation.Goal(leg, envelope));
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

    /** Short physical legs retain the one semantic destination while Minecraft handles local avoidance. */
    private static int waypointIndex(ServerLevel level, Mob worker, List<SurfaceAnchor> known,
                                     SceneLeaseId leaseId, BakeryWorkState.Phase phase) {
        LegControl retained = LEGS.get(worker);
        if (retained != null && retained.leaseId().equals(leaseId) && retained.phase() == phase
                && retained.targetIndex() < known.size()) {
            int target = retained.targetIndex();
            if (FrontierV3SemanticMovement.arrived(level, worker, known.get(target)))
                target = Math.min(known.size() - 1, target + LOCAL_LEG);
            LEGS.put(worker, new LegControl(leaseId, phase, target));
            return target;
        }
        BlockPos observedSupport = worker.getOnPos();
        int nearest = 0;
        long distance = Long.MAX_VALUE;
        for (int index = 0; index < known.size(); index++) {
            SurfaceAnchor anchor = known.get(index);
            long current = Math.abs((long) anchor.x() - observedSupport.getX())
                    + Math.abs((long) anchor.y() - observedSupport.getY())
                    + Math.abs((long) anchor.z() - observedSupport.getZ());
            if (current < distance) { nearest = index; distance = current; }
        }
        int target = Math.min(known.size() - 1, (nearest / LOCAL_LEG + 1) * LOCAL_LEG);
        LEGS.put(worker, new LegControl(leaseId, phase, target));
        return target;
    }

    private record LegControl(SceneLeaseId leaseId, BakeryWorkState.Phase phase, int targetIndex) { }
}
