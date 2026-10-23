package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

/** Bakery phase/authority validation of accepted facts, never planner rediscovery during replay. */
final class StationWorkColdStepAdmission {
    private StationWorkColdStepAdmission() { }

    static void require(FrontierWorldState state, ProductionJob job, BakeryColdStep step) {
        state.actorExecutions().requireCurrent(step.executionId());
        var actor = state.actorLocations().get(job.workerId());
        if (!step.executionId().actorId().equals(job.workerId()) || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("bakery step lost its exact living worker");
        var work = job.stationWork().orElseThrow();
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            boolean scene = state.sceneLeases().values().stream().anyMatch(lease ->
                    lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.isProductionWork(lease)
                    && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()));
            if (scene || step.action() != BakeryColdStep.Action.MOVE && step.action() != BakeryColdStep.Action.FINALIZE)
                throw new IllegalArgumentException("delivered bakery step still has a scene or a foreign action");
            if (step.action() == BakeryColdStep.Action.FINALIZE) {
                if (!step.nextSurface().equals(actor.supportingSurface())
                        || !StationWorkGoal.deliveryAccessCleared(state, job, actor.body()))
                    throw new IllegalArgumentException("bakery finalization has no observed cleared boundary");
                return;
            }
            if (!ActorExecutionCoordinator.coldAvailable(state, job.workerId()))
                throw new IllegalArgumentException("bakery clearance lacks exclusive COLD authority");
        } else {
            boolean pause = step.action() == BakeryColdStep.Action.LABOUR
                    && !step.labourChange().orElseThrow().next().running();
            if (pause && (!ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                    || ActorExecutionCoordinator.sceneOwns(state, job.workerId())
                    || !ActorExecutionCoordinator.ambientAvailable(state, java.util.List.of(job.workerId()))))
                throw new IllegalArgumentException("bakery labour pause lacks exclusive COLD authority");
            if (!pause && StationWorkProcess.coldBlocker(state, job, false).isPresent())
                throw new IllegalArgumentException("bakery step is held by current custody or service access");
            boolean atGoal = actor.supportingSurface().equals(StationWorkGoal.current(state, job).station());
            if (!pause && (step.action() == BakeryColdStep.Action.MOVE) == atGoal)
                throw new IllegalArgumentException("bakery movement/work is not at its phase boundary");
            if (step.action() != BakeryColdStep.Action.MOVE
                    && (step.action() != stationAction(state, job) || !step.nextSurface().equals(actor.supportingSurface())))
                throw new IllegalArgumentException("bakery station step has a foreign phase or body");
        }
        if (step.action() == BakeryColdStep.Action.MOVE) {
            var receipt = step.plannedRoute().orElseThrow();
            StationWorkNavigation.requireAcceptedRoute(state, job, receipt);
            if ((work.phase().sourcePickup() || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY)
                    && !ProductionServiceAccess.mayAdvance(state, job, step.nextSurface()))
                throw new IllegalArgumentException("bakery route cannot enter reserved service access");
        }
    }

    static BakeryColdStep.Action stationAction(FrontierWorldState state, ProductionJob job) {
        return switch (job.stationWork().orElseThrow().phase()) {
            case TARGET_PICKUP, DEPOT_PICKUP -> BakeryColdStep.Action.PICKUP;
            case TARGET_LOAD, STATION_LOAD -> BakeryColdStep.Action.LOAD;
            case PROCESSING -> !StationWorkLabour.complete(state, job)
                    ? BakeryColdStep.Action.LABOUR : BakeryColdStep.Action.RECIPE;
            case STATION_UNLOAD -> BakeryColdStep.Action.UNLOAD;
            case DEPOT_DELIVERY -> BakeryColdStep.Action.DELIVER;
            case DELIVERED -> throw new IllegalArgumentException("delivered bakery has no station action");
        };
    }
}
