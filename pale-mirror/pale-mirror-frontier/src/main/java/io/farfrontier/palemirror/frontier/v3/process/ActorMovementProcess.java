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
    private ActorMovementProcess() { }

    public static ActorMovement afterMeal(ResidentMeal meal, long issuedAtTick) {
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(), 0L,
                Math.addExact(meal.startedAtTick(), 1L), List.of(meal.clearingSurface()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return new ActorMovement(order, issuedAtTick,
                new ActorMovementContext.ServiceExit(meal.settlementId(), meal.depotId()));
    }

    public static ScheduledAction progress(ActorMovement movement, long dueAt) {
        if (dueAt <= movement.issuedAtTick()) throw new IllegalArgumentException("movement progress precedes order");
        MovementOrder order = movement.order();
        return new ScheduledAction(new ScheduleId("schedule:actor-movement-"
                + order.actorId().value().replace(':', '-') + "-" + order.goalRevision()),
                new SimInstant(dueAt), 12, order.actorId(), PROGRESS, 1);
    }

    public static BodyPosition bodyAt(FrontierWorldState state, SubjectId actorId, long atTick) {
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
        if (!FrontierSceneAdmission.available(state, List.of(action.subject()))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(action.subject())))
            return true;
        // The queue may inspect every overdue action on each tick. A route search belongs to
        // the admitted action, never to this predicate; otherwise one hungry cohort repeatedly
        // recompiles all settlement occupancy before any movement can commit.
        return movement.coldTravel().map(travel -> !travel.arrivedBy(action.dueAt().ticks())).orElse(false);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        ActorMovement movement = state.actorMovements().get(action.subject());
        if (movement == null || !PROGRESS.equals(action.kind())
                || !action.id().equals(progress(movement, action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("movement progress lacks its exact retained order");
        long now = Math.max(currentTick, action.dueAt().ticks());
        Optional<ActorMovementColdAdvanced> candidate = coldStep(state, movement, now);
        if (candidate.isEmpty()) {
            if (held(state, action)) throw new IllegalArgumentException("movement has no current COLD boundary");
            // No known legal route is presently available. Retain the exact order and retry
            // without falsely completing or quarantining an otherwise healthy settlement.
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                    progress(movement, Math.addExact(now, 20L)))));
        }
        ActorMovementColdAdvanced step = candidate.orElseThrow();
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(action.subject(), step));
        boolean dead = state.actorLocations().get(action.subject()).condition().status() != ActorLifeStatus.ALIVE;
        boolean terminal = dead || movement.coldTravel().isEmpty()
                && movement.order().arrivedAt(state.actorLocations().get(action.subject()).supportingSurface())
                || step.arrivedSurface().filter(movement.order()::arrivedAt).isPresent();
        if (terminal) {
            if (!dead) events.add(ResidentActivityProcess.wakeAfterMeal(action.subject(), now));
            events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        } else {
            long nextDue = movement.coldTravel().isEmpty() && step.arrivedSurface().isEmpty()
                    ? segmentRoute(state, movement, now).arrivalTick()
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
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now));
        if (!FrontierSceneAdmission.available(state, List.of(actorId))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(actorId)))
            return Optional.empty();
        if (movement.coldTravel().isPresent()) {
            TimedKnownRoute travel = movement.coldTravel().orElseThrow();
            if (firstKnownBarrier(state, travel) >= 0)
                return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now));
            if (!travel.arrivedBy(now)) return Optional.empty();
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now,
                    Optional.of(travel.route().getLast())));
        }
        if (movement.order().arrivedAt(actor.supportingSurface()))
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now));
        try {
            if (segmentRoute(state, movement, now).route().size() <= 1) return Optional.empty();
            return Optional.of(new ActorMovementColdAdvanced(actorId, movement.order().goalRevision(), now));
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return Optional.empty();
        }
    }

    public static FrontierWorldState reduceColdAdvanced(FrontierWorldState state, SubjectId subject,
                                                         ActorMovementColdAdvanced step) {
        ActorMovement movement = state.actorMovements().get(step.actorId());
        if (!subject.equals(step.actorId()) || movement == null
                || movement.order().goalRevision() != step.goalRevision()
                || !step.equals(coldStep(state, movement, step.atTick()).orElse(null)))
            throw new IllegalArgumentException("COLD movement lacks exact current order or causal boundary");
        ActorLocation actor = state.actorLocations().get(subject);
        Map<SubjectId, ActorMovement> next = new LinkedHashMap<>(state.actorMovements());
        if (actor.condition().status() != ActorLifeStatus.ALIVE) {
            next.remove(subject);
            return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(next));
        }
        if (movement.coldTravel().isEmpty()) {
            if (movement.order().arrivedAt(actor.supportingSurface())) {
                next.remove(subject);
                return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(next));
            }
            next.put(subject, movement.withColdTravel(segmentRoute(state, movement, step.atTick())));
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
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).actorMovements(next));
    }

    public static FrontierWorldState reduceHotObserved(FrontierWorldState state, SubjectId subject,
                                                       ActorMovementHotObserved observed, long atTick) {
        ActorMovement movement = state.actorMovements().get(observed.actorId());
        AmbientActorLease lease = state.ambientLeases().get(observed.actorId());
        ActorLocation actor = state.actorLocations().get(observed.actorId());
        if (!subject.equals(observed.actorId()) || movement == null || actor == null
                || movement.order().goalRevision() != observed.goalRevision()
                || lease == null || lease.status() != AmbientLeaseStatus.HOT
                || lease.goal() != AmbientGoalKind.ACTOR_MOVEMENT
                || lease.revision() != observed.ambientRevision()
                || !lease.goalBody().supportingSurface().equals(movement.order().legalStations().getFirst()))
            throw new IllegalArgumentException("HOT movement lacks exact order, lease or actor");
        boolean arrived = movement.order().arrivedAt(observed.observedBody().supportingSurface());
        boolean exited = ServiceAccessCoordinator.witnessedActorMovementExit(state, movement, observed.observedBody());
        if (!arrived && !exited)
            throw new IllegalArgumentException("HOT movement observation is neither goal arrival nor service exit");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actor.withBody(observed.observedBody()));
        Map<SubjectId, ActorMovement> movements = new LinkedHashMap<>(state.actorMovements());
        if (arrived) movements.remove(subject);
        FrontierWorldState next = state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).actorMovements(movements));
        return arrived ? ResidentActivityProcess.retargetHotResident(next, subject, atTick) : next;
    }

    public static List<ProposedEvent> planHotObserved(FrontierWorldState state,
                                                      ActorMovementHotObserved observed, long atTick) {
        FrontierWorldState next = reduceHotObserved(state, observed.actorId(), observed, atTick);
        if (next.actorMovements().containsKey(observed.actorId()))
            return List.of(new ProposedEvent(observed.actorId(), observed));
        ActorMovement prior = state.actorMovements().get(observed.actorId());
        return List.of(new ProposedEvent(observed.actorId(), observed),
                ResidentActivityProcess.wakeAfterMeal(observed.actorId(), atTick),
                new ProposedEvent(observed.actorId(), new ScheduleEffect.Cancelled(
                        progress(prior, Math.addExact(prior.issuedAtTick(), 1L)).id())));
    }

    private static TimedKnownRoute segmentRoute(FrontierWorldState state, ActorMovement movement, long now) {
        MovementOrder order = movement.order();
        if (order.capability() != TraversalCapability.PEDESTRIAN
                || !(movement.context() instanceof ActorMovementContext.ServiceExit serviceExit))
            throw new IllegalArgumentException("first movement provider supports declared resident service exits only");
        ResidentProfile resident = state.humanPopulation().resident(order.actorId());
        if (resident == null || !resident.settlementId().equals(serviceExit.settlementId())
                || !FrontierWorldState.depotId(serviceExit.settlementId()).equals(serviceExit.depotId()))
            throw new IllegalArgumentException("service exit does not match its declared resident and depot");
        List<SurfaceAnchor> route = KnownServiceExitNavigation.path(state,
                serviceExit.settlementId(), serviceExit.depotId(), order);
        ServiceAccessBoundary boundary = ServiceAccessCoordinator.boundary(state, serviceExit.depotId());
        if (boundary.occupied(route.getFirst().standingBody())) {
            for (int index = 1; index < route.size() - 1; index++) {
                if (boundary.cleared(route.get(index).standingBody())) {
                    route = List.copyOf(route.subList(0, index + 1));
                    break;
                }
            }
        }
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
