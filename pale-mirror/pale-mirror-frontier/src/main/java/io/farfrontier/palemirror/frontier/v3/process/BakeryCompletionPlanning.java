package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.List;
import static io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.*;

/** Bakery owns its completion and station-availability boundary; policy owns later assignment. */
final class BakeryCompletionPlanning {
    private BakeryCompletionPlanning() { }
    static List<ProposedEvent> plan(FrontierWorldState state, ProductionJob job,
                                                             ScheduledAction action) {
        StrategicTask retainedTask = state.strategicPlans().tasks().get(job.taskId());
        if (retainedTask != null && retainedTask.status() == StrategicTaskStatus.BLOCKED) return List.of();
        var step = BakeryProcess.planColdStep(state, job);
        if (step.isPresent() && step.orElseThrow().action() == BakeryColdStep.Action.FINALIZE)
            return List.of(new ProposedEvent(job.settlementId(), step.orElseThrow()));
        if (state.structureConditions().get(job.facilityId()) != StructureCondition.INTACT)
            return failActiveJob(state, activeTask(state, job), settlement(state, job.settlementId()),
                    workshop(settlement(state, job.settlementId())), job, ProductionDiagnosticProducer.FACILITY_UNAVAILABLE);
        var actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            return failActiveJob(state, activeTask(state, job), settlement(state, job.settlementId()),
                    workshop(settlement(state, job.settlementId())), job, ProductionDiagnosticProducer.WORKER_UNAVAILABLE);
        var replacement = BakeryProcess.planInputReallocation(state, job);
        if (replacement.isPresent()) return List.of(new ProposedEvent(job.settlementId(), replacement.orElseThrow()),
                reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
        if (step.isEmpty()) {
            long interval = job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DELIVERED
                    && ProductionOutputCapacity.depotDeliveryUnavailable(state, job)
                    ? state.bootstrap().ruleset().cadence().strategicReviewInterval() : 20L;
            return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), interval))));
        }
        long due = Math.addExact(action.dueAt().ticks(), ProductionWorkProgress.SIMULATION_TICKS_PER_WORK_UNIT);
        if (step.orElseThrow().action() == BakeryColdStep.Action.FINALIZE)
            return List.of(new ProposedEvent(job.settlementId(), step.orElseThrow()));
        var events = new java.util.ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(job.settlementId(), step.orElseThrow()));
        if (step.orElseThrow().action() == BakeryColdStep.Action.DELIVER)
            events.addAll(GoodsParticipantWakeup.container(state, job.rights().destinationContainerId(), "bakery-delivery|" + job.id().value(), action.dueAt().ticks()));
        events.add(reschedule(action, complete(job, due)));
        FrontierWorldState successor = BakeryProcess.reduceColdStep(state, job.settlementId(), step.orElseThrow());
        events.addAll(stationReleaseWake(state, successor, job.id(),
                action.id().value() + "|" + action.dueAt().ticks(), action.dueAt().ticks()));
        return List.copyOf(events);
    }

    /** Family-owned availability boundary shared by confirmed HOT and COLD receipts. */
    static List<ProposedEvent> stationReleaseWake(FrontierWorldState before, FrontierWorldState after,
            SubjectId jobId, String receiptCause, long atTick) {
        ProductionJob prior = before.productionJobs().get(jobId);
        ProductionJob current = after.productionJobs().get(jobId);
        if (prior == null || current == null || !prior.reservesFacility() || current.reservesFacility())
            return List.of();
        return List.of(new ProposedEvent(current.settlementId(), new ScheduleEffect.ReconsiderationRequested(
                StrategicObjectiveProcess.stationReconsideration(current, receiptCause, Math.addExact(atTick, 1L)))));
    }

}
