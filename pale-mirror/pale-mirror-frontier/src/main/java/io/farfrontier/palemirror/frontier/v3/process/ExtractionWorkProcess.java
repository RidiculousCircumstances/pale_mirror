package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Sparse semantic work clock. Arrival, resource receipts and need boundaries wake the retained owner. */
final class ExtractionWorkProcess {
    private ExtractionWorkProcess() { }
    static List<ProposedEvent> review(FrontierWorldState state, ScheduledAction action, long tick) {
        var site = Objects.requireNonNull(state.extractionSites().deposits().get(action.subject()), "review mining site").site();
        if (!action.kind().equals(ExtractionContinuation.REVIEW)
                || !action.id().equals(ExtractionContinuation.review(site.id(), action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("mining review has a foreign schedule");
        long now = Math.max(tick, action.dueAt().ticks());
        var events = new ArrayList<ProposedEvent>(); var projected = state;
        var provider = new ExtractionWorkAdmission(site.id());
        for (var resident : ResidentWorkComposition.SELECTION.eligible(state, site.settlementId(), ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, now)) {
            var offer = ResidentWorkComposition.SELECTION.offer(projected, resident, now, provider);
            if (offer.isEmpty()) continue;
            var start = new ExtractionWorkStarted(offer.orElseThrow().execution(), projected.extractionSites().nextWorkOrdinal());
            projected = ExtractionWorkAdmission.start(projected, start, now);
            events.add(new ProposedEvent(start.work().id(), start));
            events.add(new ProposedEvent(start.work().id(), new ScheduleEffect.Created(
                    ExtractionContinuation.at(start.work().id(), Math.addExact(now, 1)))));
        }
        events.addAll(ExtractionHaulingPolicy.dispatch(projected, site, now));
        var next = ExtractionContinuation.review(site.id(), Math.addExact(now, state.bootstrap().ruleset().extraction().reviewTicks()));
        events.add(new ProposedEvent(site.id(), new ScheduleEffect.Rescheduled(action.id(), next)));
        return List.copyOf(events);
    }
    static boolean held(FrontierWorldState state, ScheduledAction action) {
        var job = state.extractionSites().work().get(action.subject());
        if (job == null || job.terminal()) return false;
        var actor = job.execution().actorId(); var retained = state.actorExecutions().actors().get(actor);
        if (job.pending().isPresent() || retained == null || !retained.current().equals(Optional.of(job.execution()))
                || state.actorMovements().containsKey(actor) || ActorExecutionCoordinator.sceneOwns(state, actor)
                || state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE) return true;
        var lease = state.ambientLeases().get(actor);
        return lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT;
    }
    static List<ProposedEvent> progress(FrontierWorldState state, ScheduledAction action, long tick) {
        if (!action.kind().equals(ExtractionContinuation.PROGRESS)
                || !action.id().equals(ExtractionContinuation.at(action.subject(), action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("mining progress has a foreign schedule");
        var job = state.extractionSites().work().get(action.subject());
        if (job == null || job.terminal()) return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        long now = Math.max(tick, action.dueAt().ticks());
        if (held(state, action)) throw new IllegalArgumentException("mining awaits its exact custody or continuation boundary");
        if (!ExtractionWorkPolicy.requested(state, job)
                && (job.phase() == ExtractionWork.Phase.TAKE_TOOL || job.phase() == ExtractionWork.Phase.EXTRACT))
            return advance(state, job, ExtractionWorkProgressed.Operation.END_WORK, action, now);
        if (job.phase() == ExtractionWork.Phase.SELECT_SOURCE) {
            if (!ExtractionWorkPolicy.finished(state, job)
                    && (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), now)
                        || ExtractionWorkPolicy.nextTarget(state, job).isEmpty())) return retry(state, job, now);
            return advance(state, job, ExtractionWorkProgressed.Operation.SELECT_SOURCE, action, now);
        }
        if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), now)) return retry(state, job, now);
        var site = ExtractionWorkAuthority.site(state, job); var order = job.movementOrder(site);
        if (!order.arrivedAt(state.actorLocations().get(order.actorId()).supportingSurface())) {
            var movement = new ActorMovement(order, now, new ActorMovementContext.ExtractionLeg(job.id(), job.revision()), job.execution());
            try { ActorMovementProviders.require(movement).route(state, movement, state.actorLocations().get(order.actorId()).supportingSurface()); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (ExtractionWorkTargets.alternative(state, job).isEmpty()) return retry(state, job, now);
                return List.of(new ProposedEvent(job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                        ExtractionWorkProgressed.Operation.SELECT_REACHABLE)), ExtractionContinuation.wake(job.id(), now));
            }
            return List.of(new ProposedEvent(order.actorId(), new ActorMovementStarted(movement)),
                    new ProposedEvent(order.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, Math.addExact(now, 1)))),
                    ExtractionContinuation.wake(job.id(), now));
        }
        ExtractionWorkProgressed.Operation operation;
        if (job.phase() == ExtractionWork.Phase.EXTRACT) {
            if (job.labour().isEmpty() || job.labour().orElseThrow().completedAt(now) < job.labour().orElseThrow().requiredMilliWork()) {
                var labour = ExtractionWorkAuthority.labour(state, job, now, true);
                var due = Math.max(Math.addExact(now, 1), labour.activeUntilTick());
                return List.of(new ProposedEvent(job.id(), new ExtractionWorkProgressed(job.id(), job.revision(), ExtractionWorkProgressed.Operation.LABOUR)),
                        new ProposedEvent(job.id(), new ScheduleEffect.Rescheduled(action.id(), ExtractionContinuation.at(job.id(), due))));
            }
            if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                    || ExtractionSourceCustody.blocksCold(state, job.target().orElseThrow())
                    || ReferenceContainerCustody.hasLiveCustody(state, site.containerId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, site.containerId())) return retry(state, job, now);
            operation = ExtractionWorkProgressed.Operation.EXTRACT_BLOCK;
        } else {
            if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                    || ReferenceContainerCustody.hasLiveCustody(state, site.containerId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, site.containerId())
                    || !ServiceAccessCoordinator.available(state, ExtractionServiceAccess.identity(state, job))) return retry(state, job, now);
            operation = switch (job.phase()) {
                case TAKE_TOOL -> ExtractionWorkProgressed.Operation.TAKE_TOOL;
                case STORE -> ExtractionWorkProgressed.Operation.STORE;
                case RETURN_TOOL -> ExtractionWorkProgressed.Operation.RETURN_TOOL;
                default -> throw new IllegalArgumentException("mining phase lacks a declared station operation");
            };
            if (job.phase() == ExtractionWork.Phase.STORE && !state.canReceiveFungible(site.containerId(), job.outputKind(), ExtractionWorkAuthority.carried(state, job)))
                return retry(state, job, now);
            if (job.phase() == ExtractionWork.Phase.RETURN_TOOL && state.inventory().firstFreeSlot(site.containerId()).isEmpty())
                return retry(state, job, now);
        }
        return advance(state, job, operation, action, now);
    }
    private static List<ProposedEvent> advance(FrontierWorldState state, ExtractionWork job,
            ExtractionWorkProgressed.Operation operation, ScheduledAction action, long now) {
        var event = new ExtractionWorkProgressed(job.id(), job.revision(), operation);
        var preview = ExtractionColdWork.apply(state, job.id(), event, now);
        if (!preview.extractionSites().work().containsKey(job.id())) return List.of(new ProposedEvent(job.id(), event),
                new ProposedEvent(job.id(), new ScheduleEffect.Cancelled(action.id())), ResidentActivityProcess.wakeAfterActivity(job.execution().actorId(), now));
        return List.of(new ProposedEvent(job.id(), event), ExtractionContinuation.wake(job.id(), now));
    }
    private static List<ProposedEvent> retry(FrontierWorldState state, ExtractionWork job, long tick) {
        var action = ExtractionContinuation.at(job.id(), Math.addExact(tick, state.bootstrap().ruleset().extraction().reviewTicks()));
        return List.of(new ProposedEvent(job.id(), new ScheduleEffect.Rescheduled(action.id(), action)));
    }
}
