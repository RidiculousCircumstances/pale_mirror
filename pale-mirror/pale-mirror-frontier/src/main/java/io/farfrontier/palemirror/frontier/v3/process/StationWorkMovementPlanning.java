package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.*;

/** Station fulfillment supplies a semantic leg, without a second movement ticker. */
final class StationWorkMovementPlanning {
    private StationWorkMovementPlanning() { }
    static boolean needsReview(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        return state.structureConditions().get(job.facilityId()) != StructureCondition.INTACT
                || state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE
                || work.block().filter(block -> block.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED).isPresent()
                || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY && StationWorkStorageReplacement.unavailable(state, job);
    }
    static ActivityInterruptionPlanner.Assessment handoff(FrontierWorldState state, ProductionJob job, long tick) {
        var movement = state.actorMovements().get(job.workerId());
        return new ActorMovementInterruptionPlanner().assessForOwnerTransition(state, movement.executionId(),
                Math.max(tick, movement.issuedAtTick()));
    }
    static boolean held(FrontierWorldState state, ProductionJob job, long tick) {
        return !needsReview(state, job) || !(handoff(state, job, tick) instanceof ActivityInterruptionPlanner.Ready);
    }
    static List<ProposedEvent> start(FrontierWorldState state, ProductionJob job, long tick) {
        var work = job.stationWork().orElseThrow();
        if (work.pendingPhysicalStep().isPresent() || ActorExecutionCoordinator.sceneOwns(state, job.workerId())
                || state.actorMovements().containsKey(job.workerId())
                || work.block().filter(BakeryWorkBlock::requiresPhysicalReconciliation).isPresent()) return List.of();
        if (work.phase() == StationWorkState.Phase.DEPOT_DELIVERY && StationWorkStorageReplacement.unavailable(state, job)) return List.of();
        var actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) return List.of();
        MovementOrder order;
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            if (StationWorkGoal.deliveryAccessCleared(state, job, actor.body())) return List.of();
            var route = StationWorkNavigation.plannedPath(state, job);
            if (route.size() < 2) return List.of();
            order = new MovementOrder(job.id(), job.workerId(), work.goalOrdinal(), 1L, List.of(route.getLast()),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        } else {
            order = StationWorkGoal.current(state, job).movementOrder();
            if (order.arrivedAt(actor.supportingSurface())) return List.of();
        }
        var execution = state.actorExecutions().current(ActorActivityKind.PRODUCTION).get(job.workerId());
        var movement = new ActorMovement(order, tick, new ActorMovementContext.StationWorkLeg(job.id(), work.phase(),
                work.materials().tripOrdinal(), ProductionServiceAccess.point(state, job)), execution);
        return List.of(new ProposedEvent(job.workerId(), new ActorMovementStarted(movement)),
                new ProposedEvent(job.workerId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, Math.addExact(tick, 1L)))),
                ProductionContinuation.wake(job, tick));
    }
}
