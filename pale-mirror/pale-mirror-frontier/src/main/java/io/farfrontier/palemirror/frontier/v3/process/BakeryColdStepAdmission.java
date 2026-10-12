package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

/** Bakery phase/authority validation of accepted facts, never planner rediscovery during replay. */
final class BakeryColdStepAdmission {
    private BakeryColdStepAdmission() { }

    static void require(FrontierWorldState state, ProductionJob job, BakeryColdStep step) {
        state.actorExecutions().requireCurrent(step.executionId());
        var actor = state.actorLocations().get(job.workerId());
        if (!step.executionId().actorId().equals(job.workerId()) || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("bakery step lost its exact living worker");
        var work = job.bakeryWork().orElseThrow();
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            boolean scene = state.sceneLeases().values().stream().anyMatch(lease ->
                    lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.isProductionWork(lease)
                    && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()));
            if (scene || step.action() != BakeryColdStep.Action.MOVE && step.action() != BakeryColdStep.Action.FINALIZE)
                throw new IllegalArgumentException("delivered bakery step still has a scene or a foreign action");
            if (step.action() == BakeryColdStep.Action.FINALIZE) {
                if (!step.nextSurface().equals(actor.supportingSurface())
                        || !BakeryWorkGoal.deliveryAccessCleared(state, job, actor.body()))
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
            if (!pause && BakeryProcess.coldBlocker(state, job, false).isPresent())
                throw new IllegalArgumentException("bakery step is held by current custody or service access");
            boolean atGoal = actor.supportingSurface().equals(BakeryWorkGoal.current(state, job).station());
            if (!pause && (step.action() == BakeryColdStep.Action.MOVE) == atGoal)
                throw new IllegalArgumentException("bakery movement/work is not at its phase boundary");
            if (step.action() != BakeryColdStep.Action.MOVE
                    && (step.action() != stationAction(work) || !step.nextSurface().equals(actor.supportingSurface())))
                throw new IllegalArgumentException("bakery station step has a foreign phase or body");
        }
        if (step.action() == BakeryColdStep.Action.MOVE) {
            var receipt = step.plannedRoute().orElseThrow();
            BakeryKnownNavigation.requireAcceptedRoute(state, job, receipt);
            if ((work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY)
                    && !ProductionServiceAccess.mayAdvance(state, job, step.nextSurface()))
                throw new IllegalArgumentException("bakery route cannot enter reserved service access");
        }
    }

    static BakeryColdStep.Action stationAction(BakeryWorkState work) {
        return switch (work.phase()) {
            case DEPOT_PICKUP -> BakeryColdStep.Action.PICKUP;
            case STATION_LOAD -> BakeryColdStep.Action.LOAD;
            case PROCESSING -> !work.labourComplete()
                    ? BakeryColdStep.Action.LABOUR : BakeryColdStep.Action.RECIPE;
            case STATION_UNLOAD -> BakeryColdStep.Action.UNLOAD;
            case DEPOT_DELIVERY -> BakeryColdStep.Action.DELIVER;
            case DELIVERED -> throw new IllegalArgumentException("delivered bakery has no station action");
        };
    }
}
