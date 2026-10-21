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
                                                             ScheduledAction action, long currentTick) {
        long now = Math.max(currentTick, action.dueAt().ticks());
        StrategicTask retainedTask = state.strategicPlans().tasks().get(job.taskId());
        if (retainedTask != null && retainedTask.status() == StrategicTaskStatus.BLOCKED) return List.of();
        var step = BakeryProcess.planColdStep(state, job, now);
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
                reschedule(action, complete(job, Math.addExact(now, 20L))));
        var storageReplacement = BakeryStorageReplacement.select(state, job);
        if (storageReplacement.isPresent()) return List.of(new ProposedEvent(job.settlementId(), storageReplacement.orElseThrow()),
                reschedule(action, complete(job, Math.addExact(now, 1L))));
        if (step.isEmpty()) {
            long interval = job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DELIVERED
                    && ProductionOutputCapacity.depotDeliveryUnavailable(state, job)
                    ? state.bootstrap().ruleset().cadence().strategicReviewInterval() : 20L;
            return List.of(reschedule(action, complete(job, BakeryLabour.progress(state, job)
                    .filter(work -> work.running() && work.activeUntilTick() > now)
                    .map(WorkProgress::activeUntilTick).orElseGet(() -> Math.addExact(now, interval)))));
        }
        long due = step.orElseThrow().labourChange().map(change -> change.next().running()
                ? change.next().activeUntilTick() : Math.addExact(now, 1L))
                .orElseGet(() -> workDue(job, now));
        if (step.orElseThrow().action() == BakeryColdStep.Action.FINALIZE)
            return List.of(new ProposedEvent(job.settlementId(), step.orElseThrow()));
        var events = new java.util.ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(job.settlementId(), step.orElseThrow()));
        if (step.orElseThrow().action() == BakeryColdStep.Action.DELIVER)
            events.addAll(GoodsParticipantWakeup.container(state, job.rights().destinationContainerId(), "bakery-delivery|" + job.id().value(), now));
        events.add(reschedule(action, complete(job, due)));
        FrontierWorldState successor = BakeryProcess.reduceColdStep(state, job.settlementId(), step.orElseThrow());
        events.addAll(stationReleaseWake(state, successor, job.id(),
                action.id().value() + "|" + now, now));
        return List.copyOf(events);
    }

    private static long workDue(ProductionJob job, long now) {
        return Math.addExact(now, ProductionWorkProgress.SIMULATION_TICKS_PER_WORK_UNIT);
    }

    /** Family-owned availability boundary shared by confirmed HOT and COLD receipts. */
    static List<ProposedEvent> stationReleaseWake(FrontierWorldState before, FrontierWorldState after,
            SubjectId jobId, String receiptCause, long atTick) {
        ProductionJob prior = before.productionJobs().get(jobId);
        ProductionJob current = after.productionJobs().get(jobId);
        if (prior == null || !prior.reservesFacility() || current != null && current.reservesFacility())
            return List.of();
        return List.of(new ProposedEvent(prior.settlementId(), new ScheduleEffect.ReconsiderationRequested(
                StrategicObjectiveProcess.stationReconsideration(after, prior, receiptCause, Math.addExact(atTick, 1L)))));
    }

}
