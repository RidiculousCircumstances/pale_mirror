package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical goal/route execution, independent of the activity that issued the order. */
public final class ActorMovementProcess {
    public static final String PROGRESS = "frontier.actor.movement.progress";
    private static final long COLD_TICKS_PER_EDGE = 20L;
    public static long maximumJourneyTicks() { return Math.multiplyExact((long) TimedKnownRoute.MAX_SURFACES, COLD_TICKS_PER_EDGE); }
    private ActorMovementProcess() { }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject,
                                                   ActorMovementStarted started) {
        ActorMovement movement = started.movement();
        ActorLocation actor = state.actorLocations().get(subject);
        if (!subject.equals(movement.order().actorId())
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || state.actorMovements().containsKey(subject) || state.humanPopulation().meals().containsKey(subject)
                || ActorExecutionCoordinator.sceneOwns(state, subject))
            throw new IllegalArgumentException("movement start lacks exclusive living actor authority");
        // Admission retains an exact goal, not an assertion that a route has already executed.
        ActorMovementProviders.require(movement).validate(state, movement);
        var movements = new LinkedHashMap<>(state.actorMovements());
        movements.put(subject, movement);
        return ResidentActivityProcess.retargetHotResident(ActorMovementProviders.require(movement).start(state, movement,
                FrontierWorldStateUpdate.begin().actorMovements(movements)), subject, movement.issuedAtTick());
    }

    public static ScheduledAction progress(ActorMovement movement, long dueAt) {
        if (dueAt <= movement.issuedAtTick()) throw new IllegalArgumentException("movement progress precedes order");
        MovementOrder order = movement.order();
        return new ScheduledAction(new ScheduleId("schedule:actor-movement-"
                + order.actorId().value().replace(':', '-') + "-" + movement.executionId().generation() + "-" + order.goalRevision()),
                new SimInstant(dueAt), 12, order.actorId(), PROGRESS, 1);
    }

    public static BodyPosition bodyAt(FrontierWorldState state, SubjectId actorId, long atTick) {
        var physical = state.fencedRecovery().current().get(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(actorId));
        if (physical != null && (physical.phase() == FencedRecoveryPhase.RUNNING || physical.phase() == FencedRecoveryPhase.AMBIGUOUS))
            return state.actorLocations().get(actorId).body();
        ActorMovement movement = state.actorMovements().get(actorId);
        if (movement != null && movement.coldTravel().isPresent()) {
            TimedKnownRoute travel = movement.coldTravel().orElseThrow();
            long safeTick = Math.min(atTick, travel.arrivalTick() - 1L);
            int index = travel.indexAt(safeTick);
            int barrier = firstKnownBarrier(state, travel);
            if (barrier >= 0) index = Math.min(index, barrier - 1);
            return BodyPosition.above(travel.route().get(index));
        }
        ActorLocation body = state.actorLocations().get(actorId);
        if (body == null) throw new IllegalArgumentException("moving actor lacks canonical body");
        return body.body();
    }

    public static boolean held(FrontierWorldState state, ScheduledAction action) {
        if (!PROGRESS.equals(action.kind())) return false;
        ActorMovement movement = state.actorMovements().get(action.subject());
        if (movement == null) return false;
        ActorLocation actor = state.actorLocations().get(action.subject());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) return false;
        if (!ActorExecutionCoordinator.coldAvailable(state, action.subject()))
            return true;
        // The queue may inspect every overdue action on each tick. A route search belongs to
        // the admitted action, never to this predicate; otherwise one hungry cohort repeatedly
        // recompiles all settlement occupancy before any movement can commit.
        // Even an unexpectedly early retained due must reach the planner so it can be
        // moved to the route's exact arrival tick. Holding it would compare against
        // the same stale due forever, including after recovery.
        return false;
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        ActorMovement movement = state.actorMovements().get(action.subject());
        if (!PROGRESS.equals(action.kind())) throw new IllegalArgumentException("movement owner rejects a foreign schedule kind");
        if (movement == null || !action.id().equals(progress(movement, action.dueAt().ticks()).id()))
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        long now = Math.max(currentTick, action.dueAt().ticks());
        Optional<ActorMovementColdAdvanced> candidate = coldStep(state, movement, now);
        if (candidate.isEmpty()) {
            if (held(state, action)) throw new IllegalArgumentException("movement has no current COLD boundary");
            // No known legal route is presently available. Retain the exact order and retry
            // without falsely completing or quarantining an otherwise healthy settlement.
            long retryAt = movement.coldTravel().filter(travel -> firstKnownBarrier(state, travel) < 0
                            && !travel.arrivedBy(now))
                    .map(TimedKnownRoute::arrivalTick).orElse(Math.addExact(now, 20L));
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                    progress(movement, retryAt))));
        }
        ActorMovementColdAdvanced step = candidate.orElseThrow();
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(action.subject(), step));
        boolean dead = state.actorLocations().get(action.subject()).condition().status() != ActorLifeStatus.ALIVE;
        boolean terminal = dead || movement.coldTravel().isEmpty()
                && movement.order().arrivedAt(state.actorLocations().get(action.subject()).supportingSurface())
                || step.arrivedSurface().filter(movement.order()::arrivedAt).isPresent();
        if (terminal) {
            events.addAll(ActorMovementProviders.require(movement).arrived(state, movement, now));
            events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        } else {
            long nextDue = step.plannedRoute().isPresent()
                    ? timedSegment(state, movement, step.plannedRoute().orElseThrow().route(), now).arrivalTick()
                    : Math.addExact(now, 1L);
            events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                    progress(movement, nextDue))));
        }
        return List.copyOf(events);
    }

    private static Optional<ActorMovementColdAdvanced> coldStep(FrontierWorldState state,
                                                                  ActorMovement movement, long now) {
        SubjectId actorId = movement.order().actorId();
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null) return Optional.empty();
        if (actor.condition().status() != ActorLifeStatus.ALIVE)
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now, movement.executionId()));
        if (!ActorExecutionCoordinator.coldAvailable(state, actorId))
            return Optional.empty();
        if (movement.coldTravel().isPresent()) {
            TimedKnownRoute travel = movement.coldTravel().orElseThrow();
            if (firstKnownBarrier(state, travel) >= 0)
                return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now, movement.executionId()));
            if (!travel.arrivedBy(now)) return Optional.empty();
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now,
                    Optional.of(travel.route().getLast()), movement.executionId()));
        }
        if (movement.order().arrivedAt(actor.supportingSurface()))
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now, movement.executionId()));
        try {
            var travel = segmentRoute(state, movement, now);
            if (travel.route().size() <= 1) return Optional.empty();
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now,
                    Optional.empty(), movement.executionId(), Optional.of(new PedestrianRouteReceipt(travel.route()))));
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return Optional.empty();
        }
    }

    public static FrontierWorldState reduceColdAdvanced(FrontierWorldState state, SubjectId subject,
                                                         ActorMovementColdAdvanced step) {
        ActorMovement movement = state.actorMovements().get(step.actorId());
        if (!subject.equals(step.actorId()) || movement == null
                || !movement.executionId().equals(step.executionId())
                || movement.order().goalRevision() != step.goalRevision()
                || !ActorExecutionCoordinator.coldAvailable(state, subject))
            throw new IllegalArgumentException("COLD movement lacks exact current order or causal boundary");
        state.actorExecutions().requireCurrent(movement.executionId());
        ActorLocation actor = state.actorLocations().get(subject);
        if (step.plannedRoute().isPresent()) {
            var route = step.plannedRoute().orElseThrow().route();
            if (movement.coldTravel().isPresent() || actor.condition().status() != ActorLifeStatus.ALIVE
                    || !route.getFirst().equals(actor.supportingSurface()) || step.atTick() < movement.issuedAtTick())
                throw new IllegalArgumentException("accepted route lacks its exact current departure");
            ActorMovementProviders.require(movement).requireRoute(state, movement, route);
        } else if ((movement.coldTravel().isEmpty() && actor.condition().status() == ActorLifeStatus.ALIVE
                && !movement.order().arrivedAt(actor.supportingSurface()))
                || !step.equals(coldStep(state, movement, step.atTick()).orElse(null)))
            throw new IllegalArgumentException("COLD movement has no accepted route or causal arrival");
        Map<SubjectId, ActorMovement> next = new LinkedHashMap<>(state.actorMovements());
        if (actor.condition().status() != ActorLifeStatus.ALIVE) {
            next.remove(subject);
            return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(next)
                    .actorExecutions(ActorMovementProviders.require(movement).arrivalAuthority(state, movement)));
        }
        if (movement.coldTravel().isEmpty()) {
            if (movement.order().arrivedAt(actor.supportingSurface())) {
                next.remove(subject);
                return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(next)
                        .actorExecutions(ActorMovementProviders.require(movement).arrivalAuthority(state, movement)));
            }
            next.put(subject, movement.withColdTravel(timedSegment(state, movement,
                    step.plannedRoute().orElseThrow().route(), step.atTick())));
            return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(next));
        }
        TimedKnownRoute travel = movement.coldTravel().orElseThrow();
        SurfaceAnchor arrived = step.arrivedSurface().orElse(null);
        int safeIndex = travel.indexAt(Math.min(step.atTick(), travel.arrivalTick() - 1L));
        int barrier = firstKnownBarrier(state, travel);
        if (barrier >= 0) safeIndex = Math.min(safeIndex, barrier - 1);
        BodyPosition body = arrived == null
                ? BodyPosition.above(travel.route().get(safeIndex))
                : BodyPosition.above(arrived);
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actor.withBody(body));
        if (arrived != null && movement.order().arrivedAt(arrived)) next.remove(subject);
        else next.put(subject, movement.withoutColdTravel());
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).actorMovements(next)
                .actorExecutions(next.containsKey(subject) ? state.actorExecutions()
                        : ActorMovementProviders.require(movement).arrivalAuthority(state, movement)));
    }

    public static FrontierWorldState reduceHotObserved(FrontierWorldState state, SubjectId subject,
                                                       ActorMovementHotObserved observed, long atTick) {
        ActorMovement movement = state.actorMovements().get(observed.actorId());
        AmbientActorLease lease = state.ambientLeases().get(observed.actorId());
        ActorLocation actor = state.actorLocations().get(observed.actorId());
        if (!subject.equals(observed.actorId()) || movement == null || actor == null
                || !movement.executionId().equals(observed.executionId())
                || movement.order().goalRevision() != observed.goalRevision()
                || lease == null || lease.status() != AmbientLeaseStatus.HOT
                || lease.goal() != AmbientGoalKind.ACTOR_MOVEMENT
                || lease.revision() != observed.ambientRevision()
                || !lease.goalBody().supportingSurface().equals(movement.order().legalStations().getFirst()))
            throw new IllegalArgumentException("HOT movement lacks exact order, lease or actor");
        state.actorExecutions().requireCurrent(movement.executionId());
        observed.observation().require(state, movement.executionId(), lease.revision(), observed.observedBody());
        boolean arrived = movement.order().arrivedAt(observed.observedBody().supportingSurface());
        if (!arrived)
            throw new IllegalArgumentException("HOT movement receipt is not a semantic goal arrival");
        Map<SubjectId, ActorMovement> movements = new LinkedHashMap<>(state.actorMovements());
        movements.remove(subject);
        FrontierWorldState next = state.withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(movements)
                .actorExecutions(ActorMovementProviders.require(movement).arrivalAuthority(state, movement)));
        return ResidentActivityProcess.retargetHotResident(next, subject, atTick);
    }

    public static List<ProposedEvent> planHotObserved(FrontierWorldState state,
                                                      ActorMovementHotObserved observed, long atTick) {
        reduceHotObserved(state, observed.actorId(), observed, atTick);
        ActorMovement prior = state.actorMovements().get(observed.actorId());
        var events = new ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(observed.actorId(), observed));
        events.addAll(ActorMovementProviders.require(prior).arrived(state, prior, atTick));
        events.add(new ProposedEvent(observed.actorId(), new ScheduleEffect.Cancelled(
                progress(prior, Math.addExact(prior.issuedAtTick(), 1L)).id())));
        return List.copyOf(events);
    }

    private static TimedKnownRoute segmentRoute(FrontierWorldState state, ActorMovement movement, long now) {
        MovementOrder order = movement.order();
        var provider = ActorMovementProviders.require(movement);
        List<SurfaceAnchor> route = provider.coldSegment(state, movement,
                provider.route(state, movement, state.actorLocations().get(order.actorId()).supportingSurface()));
        return timedSegment(state, movement, route, now);
    }

    private static TimedKnownRoute timedSegment(FrontierWorldState state, ActorMovement movement,
                                               List<SurfaceAnchor> route, long now) {
        MovementOrder order = movement.order();
        AmbientActorLease lease = state.ambientLeases().get(order.actorId());
        long epoch = lease == null ? 1L : Math.addExact(lease.revision(), 1L);
        return new TimedKnownRoute(ActorMovement.segmentOrder(order, route.getLast()), route,
                now, COLD_TICKS_PER_EDGE, epoch);
    }

    private static int firstKnownBarrier(FrontierWorldState state, TimedKnownRoute travel) {
        for (int index = 1; index < travel.route().size(); index++) {
            BlockPosition support = travel.route().get(index).support();
            if (state.physicalDeltas().containsKey(support)
                    || state.physicalDeltas().containsKey(support.offset(0, 1, 0))
                    || state.physicalDeltas().containsKey(support.offset(0, 2, 0))) return index;
        }
        return -1;
    }

    public static boolean travelIntersects(ActorMovement movement, BlockPosition cell) {
        if (movement.coldTravel().isEmpty()) return false;
        List<SurfaceAnchor> route = movement.coldTravel().orElseThrow().route();
        for (int index = 1; index < route.size(); index++) {
            BlockPosition support = route.get(index).support();
            if (cell.equals(support) || cell.equals(support.offset(0, 1, 0))
                    || cell.equals(support.offset(0, 2, 0))) return true;
        }
        return false;
    }
}
