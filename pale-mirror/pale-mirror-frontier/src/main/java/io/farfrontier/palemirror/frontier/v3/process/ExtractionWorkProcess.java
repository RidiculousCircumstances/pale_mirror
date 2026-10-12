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
        var events = new ArrayList<ProposedEvent>();
        FrontierWorldState projected;
        try { projected = ExtractionDevelopmentContinuation.append(state, site.id(), events, now); }
        catch (KnownPedestrianNavigation.RouteUnavailable pending) {
            return List.of(nextReview(state, action, now));
        }
        var provider = new ExtractionWorkAdmission(site.id());
        for (var resident : ResidentWorkComposition.SELECTION.eligible(state, site.settlementId(), ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, now)) {
            Optional<ResidentWorkOffer<ExtractionWorkStarted>> offer;
            try { offer = ResidentWorkComposition.SELECTION.offer(projected, resident, now, provider); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { break; }
            if (offer.isEmpty()) continue;
            var start = offer.orElseThrow().execution();
            projected = ExtractionWorkAdmission.start(projected, start, now);
            events.add(new ProposedEvent(start.work().id(), start));
            events.add(new ProposedEvent(start.work().id(), new ScheduleEffect.Created(
                    ExtractionContinuation.at(start.work().id(), Math.addExact(now, 1)))));
        }
        events.add(nextReview(state, action, now));
        return List.copyOf(events);
    }
    private static ProposedEvent nextReview(FrontierWorldState state, ScheduledAction action, long now) {
        var next = ExtractionContinuation.review(action.subject(), Math.addExact(now, state.bootstrap().ruleset().extraction().reviewTicks()));
        return new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), next));
    }
    static boolean held(FrontierWorldState state, ScheduledAction action) {
        var job = state.extractionSites().work().get(action.subject());
        if (job == null || job.terminal()) return false;
        var actor = job.execution().actorId(); var retained = state.actorExecutions().actors().get(actor);
        if (job.pending().isPresent() || retained == null || !retained.current().equals(Optional.of(job.execution()))
                || ActorExecutionCoordinator.sceneOwns(state, actor)
                || state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE) return true;
        if (state.actorMovements().containsKey(actor)) return !(needsStorage(state, job)
                && new ActorMovementInterruptionPlanner().assess(state, actor, Math.max(action.dueAt().ticks(),
                    state.actorMovements().get(actor).issuedAtTick())) instanceof ActivityInterruptionPlanner.Ready);
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
        if (needsStorage(state, job)) {
            var interrupted = new ActorMovementInterruptionPlanner().assess(state, job.execution().actorId(), now);
            if (!(interrupted instanceof ActivityInterruptionPlanner.Ready ready)) return retry(state, job, now);
            Optional<ExtractionWorkDecision.Storage> selected;
            try { selected = ExtractionStorageSelection.replacement(ready.following(), job); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, job, now); }
            if (selected.isEmpty()) return retry(state, job, now);
            var events = new ArrayList<>(ready.events());
            events.addAll(advance(ready.following(), job, new ExtractionWorkProgressed(job.id(), job.revision(),
                    ExtractionWorkProgressed.Operation.SELECT_STORAGE, Optional.of(selected.orElseThrow())), action, now));
            return List.copyOf(events);
        }
        if (!ExtractionWorkPolicy.requested(state, job)
                && (job.phase() == ExtractionWork.Phase.TAKE_TOOL || job.phase() == ExtractionWork.Phase.EXTRACT))
            return advance(state, job, ExtractionWorkProgressed.Operation.END_WORK, action, now);
        if (job.phase() == ExtractionWork.Phase.SELECT_SOURCE) {
            ExtractionWorkDecision decision;
            if (ExtractionWorkPolicy.finished(state, job)) decision = new ExtractionWorkDecision.Finished(job.execution());
            else {
                if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), now)) return retry(state, job, now);
                Optional<ExtractionWorkDecision.Source> selected;
                try { selected = ExtractionWorkPolicy.nextTarget(state, job); }
                catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, job, now); }
                if (selected.isEmpty()) return retry(state, job, now);
                decision = selected.orElseThrow();
            }
            return advance(state, job, new ExtractionWorkProgressed(job.id(), job.revision(),
                    ExtractionWorkProgressed.Operation.SELECT_SOURCE, Optional.of(decision)), action, now);
        }
        if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), now)) return retry(state, job, now);
        var site = ExtractionWorkAuthority.site(state, job); var order = job.movementOrder(site);
        if (!order.arrivedAt(state.actorLocations().get(order.actorId()).supportingSurface())) {
            var movement = new ActorMovement(order, now, new ActorMovementContext.ExtractionLeg(job.id(), job.revision()), job.execution());
            try { ActorMovementProviders.require(movement).route(state, movement, state.actorLocations().get(order.actorId()).supportingSurface()); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) return retry(state, job, now);
                Optional<ExtractionWorkDecision.Source> selected;
                try { selected = ExtractionWorkTargets.alternative(state, job); }
                catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, job, now); }
                if (selected.isEmpty()) return retry(state, job, now);
                return advance(state, job, new ExtractionWorkProgressed(job.id(), job.revision(),
                        ExtractionWorkProgressed.Operation.SELECT_REACHABLE, Optional.of(selected.orElseThrow())), action, now);
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
                    || ExtractionSourceCustody.blocksCold(state, job.target().orElseThrow())) return retry(state, job, now);
            operation = ExtractionWorkProgressed.Operation.EXTRACT_BLOCK;
        } else {
            if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                    || ReferenceContainerCustody.hasLiveCustody(state, ExtractionServiceAccess.identity(state, job).pointId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, ExtractionServiceAccess.identity(state, job).pointId())
                    || !ServiceAccessCoordinator.available(state, ExtractionServiceAccess.identity(state, job))) return retry(state, job, now);
            operation = switch (job.phase()) {
                case TAKE_TOOL -> ExtractionWorkProgressed.Operation.TAKE_TOOL;
                case STORE -> ExtractionWorkProgressed.Operation.STORE;
                case RETURN_TOOL -> ExtractionWorkProgressed.Operation.RETURN_TOOL;
                default -> throw new IllegalArgumentException("mining phase lacks a declared station operation");
            };
            if (job.phase() == ExtractionWork.Phase.STORE && !ExtractionStorageSelection.retainedAvailable(state, job))
                return retry(state, job, now);
            if (job.phase() == ExtractionWork.Phase.RETURN_TOOL && !ExtractionStorageSelection.retainedAvailable(state, job))
                return retry(state, job, now);
        }
        return advance(state, job, operation, action, now);
    }
    private static List<ProposedEvent> advance(FrontierWorldState state, ExtractionWork job,
            ExtractionWorkProgressed.Operation operation, ScheduledAction action, long now) {
        var event = new ExtractionWorkProgressed(job.id(), job.revision(), operation);
        return advance(state, job, event, action, now);
    }
    private static List<ProposedEvent> advance(FrontierWorldState state, ExtractionWork job,
            ExtractionWorkProgressed event, ScheduledAction action, long now) {
        var preview = ExtractionColdWork.apply(state, job.id(), event, now);
        if (!preview.extractionSites().work().containsKey(job.id())) return List.of(new ProposedEvent(job.id(), event),
                new ProposedEvent(job.id(), new ScheduleEffect.Cancelled(action.id())), ResidentActivityProcess.wakeAfterActivity(job.execution().actorId(), now));
        var events = new ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(job.id(), event));
        if (event.operation() == ExtractionWorkProgressed.Operation.EXTRACT_BLOCK)
            ExtractionDevelopmentContinuation.appendAfterObservation(preview, job.siteId(), events, now);
        if (events.stream().noneMatch(proposed -> proposed.subject().equals(job.id()) && proposed.payload() instanceof ScheduleEffect))
            events.add(ExtractionContinuation.wake(job.id(), now));
        return List.copyOf(events);
    }
    private static List<ProposedEvent> retry(FrontierWorldState state, ExtractionWork job, long tick) {
        var action = ExtractionContinuation.at(job.id(), Math.addExact(tick, state.bootstrap().ruleset().extraction().reviewTicks()));
        return List.of(new ProposedEvent(job.id(), new ScheduleEffect.Rescheduled(action.id(), action)));
    }
    private static boolean needsStorage(FrontierWorldState state, ExtractionWork job) {
        return job.pending().isEmpty() && (job.phase() == ExtractionWork.Phase.STORE || job.phase() == ExtractionWork.Phase.RETURN_TOOL)
                && !ExtractionStorageSelection.retainedAvailable(state, job);
    }
}
