package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Starts one exact hive route interception and advances its COLD bodies along retained routes. */
final class HiveRouteEngagementProcess {
    private static final long STEP_INTERVAL = 100L;
    private static final int COLD_STEP_BLOCKS = 16;
    private HiveRouteEngagementProcess() { }

    static Optional<SubjectId> targetOperation(FrontierWorldState state) {
        return state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.EN_ROUTE)
                .filter(operation -> state.strategicPlans().routeEngagements().values().stream().noneMatch(engagement -> engagement.operationId().equals(operation.id())
                        && engagement.status() != RouteEngagementStatus.RESOLVED)).sorted(Comparator.comparing(RouteOperation::id)).map(RouteOperation::id).findFirst();
    }

    static ScheduledAction start(StrategicTask task, long due) {
        if (task.kind() != StrategicTaskKind.INTERCEPT_ROUTE_OPERATION) throw new IllegalArgumentException("invalid hive interception task schedule");
        return new ScheduledAction(new ScheduleId("schedule:hive-route-engagement-start-" + task.id().value().replace(':', '-')), new SimInstant(due), 0,
                task.id(), "frontier.hive_route_engagement.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        RouteOperation operation = task.operationTarget().map(state.operations()::get).orElse(null);
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || activeForOperation(state, operation.id())) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        BlockPosition intercept = operation.route().get(Math.max(operation.routeIndex(), operation.route().size() - 2));
        List<EngagementAttacker> attackers = attackers(state, intercept);
        if (attackers.isEmpty()) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        RouteEngagement engagement = new RouteEngagement(engagementId(task), task.id(), operation.id(), task.ownerId(), attackers,
                intercept, RouteEngagementStatus.APPROACHING);
        List<ProposedEvent> events = new ArrayList<>(List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(task.ownerId(), new RouteEngagementStarted(engagement))));
        if (engagement.allAttackersAtIntercept()) events.add(new ProposedEvent(task.ownerId(), new RouteEngagementTransition(engagement.id(), RouteEngagementStatus.READY_FOR_SCENE)));
        else events.add(schedule(progress(engagement, action.dueAt().ticks() + STEP_INTERVAL)));
        return List.copyOf(events);
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(action.subject());
        if (engagement == null || engagement.status() != RouteEngagementStatus.APPROACHING) return List.of();
        StrategicTask task = task(state, engagement.taskId(), StrategicTaskStatus.ACTIVE);
        RouteOperation operation = state.operations().get(engagement.operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || engagement.attackerIds().stream()
                .anyMatch(actor -> state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE)) {
            return List.of(new ProposedEvent(engagement.hiveId(), new RouteEngagementTransition(engagement.id(), RouteEngagementStatus.RESOLVED)),
                    transition(task, StrategicTaskStatus.BLOCKED));
        }
        List<ProposedEvent> events = new ArrayList<>();
        for (EngagementAttacker attacker : engagement.attackers()) {
            if (!attacker.atDestination()) events.add(new ProposedEvent(engagement.hiveId(),
                    new RouteEngagementAttackerAdvanced(engagement.id(), attacker.actorId(), attacker.routeIndex() + 1)));
        }
        if (engagement.attackers().stream().allMatch(attacker -> attacker.routeIndex() + 1 >= attacker.route().size() - 1)) {
            events.add(new ProposedEvent(engagement.hiveId(), new RouteEngagementTransition(engagement.id(), RouteEngagementStatus.READY_FOR_SCENE)));
        } else events.add(schedule(progress(engagement, action.dueAt().ticks() + STEP_INTERVAL)));
        return List.copyOf(events);
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RouteEngagementStarted started) {
        RouteEngagement engagement = started.engagement(); StrategicTask task = task(state, engagement.taskId(), StrategicTaskStatus.ACTIVE);
        if (!subject.equals(engagement.hiveId()) || !task.ownerId().equals(subject) || state.strategicPlans().routeEngagements().containsKey(engagement.id())) {
            throw new IllegalArgumentException("route engagement start has a foreign owner or duplicate identity");
        }
        return state.withStrategicPlans(state.strategicPlans().startEngagement(engagement));
    }

    static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, RouteEngagementAttackerAdvanced advanced) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(advanced.engagementId());
        if (engagement == null || !subject.equals(engagement.hiveId())) throw new IllegalArgumentException("route engagement advancement has a foreign owner");
        RouteEngagement next = engagement.advanceAttacker(advanced.attackerId(), advanced.routeIndex());
        EngagementAttacker attacker = next.attackers().stream().filter(value -> value.actorId().equals(advanced.attackerId())).findFirst().orElseThrow();
        return state.withActorLocation(attacker.actorId(), attacker.position(), state.strategicPlans().replaceEngagement(next));
    }

    static FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, RouteEngagementTransition transition) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(transition.engagementId());
        if (engagement == null || !subject.equals(engagement.hiveId())) throw new IllegalArgumentException("route engagement transition has a foreign owner");
        if (transition.status() == RouteEngagementStatus.READY_FOR_SCENE && !engagement.allAttackersAtIntercept()) {
            throw new IllegalArgumentException("route engagement cannot enter a scene before all attackers arrive");
        }
        return state.withStrategicPlans(state.strategicPlans().transitionEngagement(engagement.id(), transition.status()));
    }

    private static boolean activeForOperation(FrontierWorldState state, SubjectId operationId) {
        return state.strategicPlans().routeEngagements().values().stream().anyMatch(engagement -> engagement.operationId().equals(operationId)
                && engagement.status() != RouteEngagementStatus.RESOLVED);
    }
    private static List<EngagementAttacker> attackers(FrontierWorldState state, BlockPosition intercept) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.role() == BioformRole.GUARD).filter(bioform -> state.actorLocations().get(bioform.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(bioform -> state.ambientLeases().get(bioform.id()) == null || state.ambientLeases().get(bioform.id()).status() == AmbientLeaseStatus.CLOSED)
                .sorted(Comparator.comparingLong((Bioform bioform) -> distanceSquared(state.actorLocations().get(bioform.id()).position(), intercept)).thenComparing(Bioform::id))
                .limit(3).map(bioform -> new EngagementAttacker(bioform.id(), approach(state.actorLocations().get(bioform.id()).position(), intercept), 0)).toList();
    }
    private static List<BlockPosition> approach(BlockPosition start, BlockPosition end) {
        if (start.equals(end)) return List.of(start);
        long dx = (long) end.x() - start.x(), dy = (long) end.y() - start.y(), dz = (long) end.z() - start.z();
        long distance = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)); int steps = Math.max(1, Math.toIntExact((distance + COLD_STEP_BLOCKS - 1) / COLD_STEP_BLOCKS));
        List<BlockPosition> route = new ArrayList<>(steps + 1);
        for (int index = 0; index <= steps; index++) route.add(new BlockPosition(interpolate(start.x(), dx, index, steps),
                interpolate(start.y(), dy, index, steps), interpolate(start.z(), dz, index, steps)));
        return List.copyOf(route);
    }
    private static int interpolate(int start, long delta, int index, int steps) {
        return Math.toIntExact(Math.addExact(start, Math.floorDiv(Math.multiplyExact(delta, index), steps)));
    }
    private static long distanceSquared(BlockPosition left, BlockPosition right) { long dx = (long) left.x() - right.x(), dy = (long) left.y() - right.y(), dz = (long) left.z() - right.z(); return dx * dx + dy * dy + dz * dz; }
    private static ScheduledAction progress(RouteEngagement engagement, long due) { return new ScheduledAction(new ScheduleId("schedule:hive-route-engagement-progress-" + engagement.id().value().substring("engagement:".length())),
            new SimInstant(due), 0, engagement.id(), "frontier.hive_route_engagement.progress", 1); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static SubjectId engagementId(StrategicTask task) { return new SubjectId("engagement:" + task.id().value().substring("task:".length())); }
    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.INTERCEPT_ROUTE_OPERATION || task.status() != status) throw new IllegalStateException("hive route engagement has no matching strategic task");
        return task;
    }
}
