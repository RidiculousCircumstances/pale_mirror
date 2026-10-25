package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Addressed work continuations. No tree/leaf scans on ordinary world ticks. */
final class ForestryWorkProcess {
    private ForestryWorkProcess() { }
    static FrontierScheduledPlanner progressPlanner() {
        return new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
                return progress(state, action, action.dueAt().ticks());
            }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
                return progress(state, action, instant.ticks());
            }
            @Override public boolean held(FrontierWorldState state, ScheduledAction action) { return ForestryWorkProcess.held(state, action); }
        };
    }
    static List<ProposedEvent> review(FrontierWorldState state, ScheduledAction action, long tick) {
        if (!action.kind().equals(ForestryContinuation.REVIEW)
                || !action.id().equals(ForestryContinuation.review(action.subject(), action.dueAt().ticks()).id())
                || !ForestryStockDemand.enabled(state, action.subject())) throw new IllegalArgumentException("foreign forestry review");
        long now = Math.max(tick, action.dueAt().ticks()); var events = new ArrayList<ProposedEvent>(); var projected = state;
        var provider = new ForestryWorkAdmission();
        for (var resident : ResidentWorkComposition.SELECTION.eligible(projected, action.subject(), ResidentWorkKind.FORESTRY, HumanCapability.EXTRACTION, now)) {
            Optional<ResidentWorkOffer<ForestryWorkStarted>> offer;
            try { offer = ResidentWorkComposition.SELECTION.offer(projected, resident, now, provider); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { continue; }
            if (offer.isEmpty()) continue;
            var start = offer.orElseThrow().execution(); projected = ForestryWorkAdmission.start(projected, start, now);
            events.add(new ProposedEvent(start.work().id(), start));
            events.add(new ProposedEvent(start.work().id(), new ScheduleEffect.Created(ForestryContinuation.progress(start.work().id(), Math.addExact(now, 1)))));
        }
        var next = ForestryContinuation.review(action.subject(), Math.addExact(now, state.bootstrap().ruleset().forestry().reviewTicks()));
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), next))); return List.copyOf(events);
    }
    static boolean held(FrontierWorldState state, ScheduledAction action) {
        var job = state.forest().work().get(action.subject()); if (job == null) return false;
        var actor = job.execution().actorId(); var authority = state.actorExecutions().actors().get(actor);
        if (job.pending().isPresent() || authority == null || !authority.current().equals(Optional.of(job.execution()))
                || state.actorMovements().containsKey(actor) || ActorExecutionCoordinator.sceneOwns(state, actor)
                || state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE) return true;
        var lease = state.ambientLeases().get(actor);
        return lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT;
    }
    static List<ProposedEvent> progress(FrontierWorldState state, ScheduledAction action, long tick) {
        if (!action.kind().equals(ForestryContinuation.PROGRESS)
                || !action.id().equals(ForestryContinuation.progress(action.subject(), action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("foreign forestry continuation");
        var job = state.forest().work().get(action.subject());
        if (job == null) return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        if (held(state, action)) throw new IllegalArgumentException("forestry awaits its addressed authority/arrival boundary");
        long now = Math.max(tick, action.dueAt().ticks());
        if (job.target().isPresent() && !ForestryWorkSelection.targetCurrent(state, job))
            return advance(state, job, new ForestryWorkAdvanced(job.id(), job.revision(), ForestryWorkAdvanced.Operation.ABANDON_TARGET), action, now);
        if (job.phase() == ForestryWork.Phase.SELECT_TREE) {
            Optional<ForestryWorkAdvanced.TreeSelection> selected;
            try { selected = ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), now)
                    ? ForestryWorkSelection.next(state, job) : Optional.empty(); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, job, now); }
            if (selected.isPresent()) return advance(state, job, new ForestryWorkAdvanced(job.id(), job.revision(),
                    ForestryWorkAdvanced.Operation.SELECT_TREE, Optional.of(selected.orElseThrow())), action, now);
            return advance(state, job, new ForestryWorkAdvanced(job.id(), job.revision(), ForestryWorkAdvanced.Operation.END_WORK), action, now);
        }
        if ((job.phase() == ForestryWork.Phase.TAKE_TOOL || job.phase() == ForestryWork.Phase.FELL && job.felling().isEmpty())
                && !ForestryStockDemand.requested(state, job.home(), job.outputKind(), Optional.of(job.id())))
            return advance(state, job, new ForestryWorkAdvanced(job.id(), job.revision(), ForestryWorkAdvanced.Operation.END_WORK), action, now);
        if ((job.phase() == ForestryWork.Phase.STORE || job.phase() == ForestryWork.Phase.RETURN_TOOL)
                && !WorkStorageSelection.available(state, ForestryWorkSelection.portion(state, job), job.storageTarget())) {
            Optional<WorkStorageSelection.Selected> storage;
            try { storage = WorkStorageSelection.select(state, ForestryWorkSelection.portion(state, job),
                    state.actorLocations().get(job.execution().actorId()).supportingSurface(), Optional.of(job.storageTarget())); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, job, now); }
            if (storage.isEmpty()) return retry(state, job, now);
            var selected = storage.orElseThrow();
            return advance(state, job, new ForestryWorkAdvanced(job.id(), job.revision(), ForestryWorkAdvanced.Operation.SELECT_STORAGE,
                    Optional.of(new ForestryWorkAdvanced.StorageSelection(selected.endpoint(), selected.route()))), action, now);
        }
        // A begun bounded physical consequence must settle; a new work interval obeys needs/schedule.
        if (job.felling().isEmpty() && !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), now))
            return retry(state, job, now);
        var order = job.movementOrder();
        if (!order.arrivedAt(state.actorLocations().get(order.actorId()).supportingSurface())) {
            var movement = new ActorMovement(order, now, new ActorMovementContext.ForestryLeg(job.id(), job.revision()), job.execution());
            try { ActorMovementProviders.require(movement).route(state, movement, state.actorLocations().get(order.actorId()).supportingSurface()); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { return retry(state, job, now); }
            return List.of(new ProposedEvent(order.actorId(), new ActorMovementStarted(movement)),
                    new ProposedEvent(order.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, Math.addExact(now, 1)))),
                    ForestryContinuation.wake(job.id(), now));
        }
        ForestryWorkAdvanced.Operation operation;
        if (job.phase() == ForestryWork.Phase.FELL) {
            if (job.felling().isEmpty()) {
                if (job.labour().isEmpty() || job.labour().orElseThrow().completedAt(now) < job.labour().orElseThrow().requiredMilliWork()) {
                    var labour = ForestryWorkAuthority.labour(state, job, now, true);
                    return List.of(new ProposedEvent(job.id(), new ForestryWorkAdvanced(job.id(), job.revision(), ForestryWorkAdvanced.Operation.LABOUR)),
                            new ProposedEvent(job.id(), new ScheduleEffect.Rescheduled(action.id(), ForestryContinuation.progress(job.id(),
                                    Math.max(Math.addExact(now, 1), labour.activeUntilTick())))));
                }
                operation = ForestryWorkAdvanced.Operation.BEGIN_FELLING;
            } else {
                if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                        || ForestrySourceCustody.blocksCold(state, job.target().orElseThrow().tree())) return retry(state, job, now);
                operation = ForestryWorkAdvanced.Operation.APPLY_CHILD;
            }
        } else {
            var access = ForestryServiceAccess.identity(job);
            if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                    || ReferenceContainerCustody.hasLiveCustody(state, access.pointId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, access.pointId())
                    || !ServiceAccessCoordinator.available(state, access)) return retry(state, job, now);
            operation = switch (job.phase()) {
                case TAKE_TOOL -> ForestryWorkAdvanced.Operation.TAKE_TOOL;
                case STORE -> ForestryWorkAdvanced.Operation.STORE;
                case RETURN_TOOL -> ForestryWorkAdvanced.Operation.RETURN_TOOL;
                default -> throw new IllegalArgumentException("forestry phase has no declared station interaction");
            };
        }
        return advance(state, job, new ForestryWorkAdvanced(job.id(), job.revision(), operation), action, now);
    }
    private static List<ProposedEvent> advance(FrontierWorldState state, ForestryWork job, ForestryWorkAdvanced event, ScheduledAction action, long now) {
        var preview = ForestryColdWork.apply(state, job.id(), event, now);
        return preview.forest().work().containsKey(job.id()) ? List.of(new ProposedEvent(job.id(), event), ForestryContinuation.wake(job.id(), now))
                : List.of(new ProposedEvent(job.id(), event), new ProposedEvent(job.id(), new ScheduleEffect.Cancelled(action.id())),
                    ResidentActivityProcess.wakeAfterActivity(job.execution().actorId(), now));
    }
    private static List<ProposedEvent> retry(FrontierWorldState state, ForestryWork job, long now) {
        var next = ForestryContinuation.progress(job.id(), Math.addExact(now, state.bootstrap().ruleset().forestry().reviewTicks()));
        return List.of(new ProposedEvent(job.id(), new ScheduleEffect.Rescheduled(next.id(), next)));
    }
}
