package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
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
    private static final long COMBAT_INTERVAL = 20L;
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
                intercept, RouteEngagementStatus.APPROACHING, 0, Optional.empty());
        List<ProposedEvent> events = new ArrayList<>(List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(task.ownerId(), new RouteEngagementStarted(engagement))));
        if (engagement.allAttackersAtIntercept()) events.addAll(beginOrWait(state, engagement, action.dueAt().ticks()));
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
            return abort(engagement);
        }
        List<ProposedEvent> events = new ArrayList<>();
        for (EngagementAttacker attacker : engagement.attackers()) {
            if (!attacker.atDestination()) events.add(new ProposedEvent(engagement.hiveId(),
                    new RouteEngagementAttackerAdvanced(engagement.id(), attacker.actorId(), attacker.routeIndex() + 1)));
        }
        if (engagement.attackers().stream().allMatch(attacker -> attacker.routeIndex() + 1 >= attacker.route().size() - 1)) {
            events.addAll(beginOrWait(state, engagement, action.dueAt().ticks()));
        } else events.add(schedule(progress(engagement, action.dueAt().ticks() + STEP_INTERVAL)));
        return List.copyOf(events);
    }

    static List<ProposedEvent> planReadiness(FrontierWorldState state, ScheduledAction action) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(action.subject());
        if (engagement == null || engagement.status() != RouteEngagementStatus.WAITING_FOR_INTERCEPT) return List.of();
        RouteOperation operation = state.operations().get(engagement.operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || !engagement.allAttackersAtIntercept()
                || engagement.attackerIds().stream().anyMatch(actor -> !RouteEngagementCombatRules.alive(state, actor))) {
            return abort(engagement);
        }
        if (!operation.route().get(operation.routeIndex()).equals(engagement.intercept())) return List.of(schedule(readiness(engagement, action.dueAt().ticks() + STEP_INTERVAL)));
        return List.of(new ProposedEvent(engagement.hiveId(), new RouteEngagementTransition(engagement.id(), RouteEngagementStatus.COLD_COMBAT)),
                schedule(combat(engagement, action.dueAt().ticks() + COMBAT_INTERVAL)));
    }

    static List<ProposedEvent> planCombat(FrontierWorldState state, ScheduledAction action) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(action.subject());
        if (engagement == null || engagement.status() != RouteEngagementStatus.COLD_COMBAT) return List.of();
        List<SubjectId> attackers = RouteEngagementCombatRules.livingAttackers(state, engagement);
        List<SubjectId> defenders = RouteEngagementCombatRules.livingDefenders(state, engagement);
        if (attackers.isEmpty() || defenders.isEmpty()) return terminal(state, engagement);
        boolean hiveTurn = (engagement.nextStrikeEpoch() & 1) == 0;
        SubjectId attacker = RouteEngagementCombatRules.choose(hiveTurn ? attackers : defenders, engagement.nextStrikeEpoch());
        SubjectId target = RouteEngagementCombatRules.choose(hiveTurn ? defenders : attackers, engagement.nextStrikeEpoch());
        RouteEngagementStrike strike = new RouteEngagementStrike(engagement.id(), attacker, target, engagement.nextStrikeEpoch(),
                RouteEngagementCombatRules.damage(state, attacker));
        FixedScalar after = state.actorLocations().get(target).condition().health().minus(strike.damage());
        List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(engagement.hiveId(), strike)));
        if (after.compareTo(FixedScalar.ZERO) <= 0 && ((hiveTurn && defenders.size() == 1) || (!hiveTurn && attackers.size() == 1))) {
            RouteEngagementOutcome outcome = hiveTurn ? RouteEngagementOutcome.HIVE_VICTORY : RouteEngagementOutcome.SETTLEMENT_VICTORY;
            events.add(new ProposedEvent(engagement.hiveId(), new RouteEngagementResolved(engagement.id(), outcome)));
            if (outcome == RouteEngagementOutcome.SETTLEMENT_VICTORY) {
                events.add(schedule(SupplyOperationProcess.operationProgress(state.operations().get(engagement.operationId()), action.dueAt().ticks() + STEP_INTERVAL)));
            }
        } else events.add(schedule(combat(engagement, action.dueAt().ticks() + COMBAT_INTERVAL)));
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
        if ((transition.status() == RouteEngagementStatus.WAITING_FOR_INTERCEPT || transition.status() == RouteEngagementStatus.COLD_COMBAT)
                && !engagement.allAttackersAtIntercept()) {
            throw new IllegalArgumentException("route engagement cannot wait or fight before all attackers arrive");
        }
        return state.withStrategicPlans(state.strategicPlans().transitionEngagement(engagement.id(), transition.status()));
    }

    static FrontierWorldState reduceStrike(FrontierWorldState state, SubjectId subject, RouteEngagementStrike strike) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(strike.engagementId());
        if (engagement == null || !subject.equals(engagement.hiveId())) throw new IllegalArgumentException("COLD strike has a foreign owner");
        return FrontierRouteEngagementStateSupport.strike(state, strike);
    }

    static FrontierWorldState reduceResolved(FrontierWorldState state, SubjectId subject, RouteEngagementResolved resolved) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(resolved.engagementId());
        if (engagement == null || !subject.equals(engagement.hiveId())) throw new IllegalArgumentException("route engagement resolution has a foreign owner");
        return FrontierRouteEngagementStateSupport.resolve(state, resolved);
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
    private static ScheduledAction readiness(RouteEngagement engagement, long due) { return new ScheduledAction(new ScheduleId("schedule:hive-route-engagement-readiness-" + engagement.id().value().substring("engagement:".length())),
            new SimInstant(due), 0, engagement.id(), "frontier.hive_route_engagement.readiness", 1); }
    private static ScheduledAction combat(RouteEngagement engagement, long due) { return new ScheduledAction(new ScheduleId("schedule:hive-route-engagement-combat-" + engagement.id().value().substring("engagement:".length())),
            new SimInstant(due), 0, engagement.id(), "frontier.hive_route_engagement.combat", 1); }
    private static List<ProposedEvent> beginOrWait(FrontierWorldState state, RouteEngagement engagement, long now) {
        RouteOperation operation = state.operations().get(engagement.operationId());
        if (operation.route().get(operation.routeIndex()).equals(engagement.intercept())) {
            return List.of(new ProposedEvent(engagement.hiveId(), new RouteEngagementTransition(engagement.id(), RouteEngagementStatus.COLD_COMBAT)),
                    schedule(combat(engagement, now + COMBAT_INTERVAL)));
        }
        return List.of(new ProposedEvent(engagement.hiveId(), new RouteEngagementTransition(engagement.id(), RouteEngagementStatus.WAITING_FOR_INTERCEPT)),
                schedule(readiness(engagement, now + STEP_INTERVAL)));
    }
    private static List<ProposedEvent> terminal(FrontierWorldState state, RouteEngagement engagement) {
        List<SubjectId> attackers = RouteEngagementCombatRules.livingAttackers(state, engagement);
        List<SubjectId> defenders = RouteEngagementCombatRules.livingDefenders(state, engagement);
        RouteEngagementOutcome outcome = attackers.isEmpty() && defenders.isEmpty() ? RouteEngagementOutcome.ABORTED
                : attackers.isEmpty() ? RouteEngagementOutcome.SETTLEMENT_VICTORY : RouteEngagementOutcome.HIVE_VICTORY;
        return List.of(new ProposedEvent(engagement.hiveId(), new RouteEngagementResolved(engagement.id(), outcome)));
    }
    private static List<ProposedEvent> abort(RouteEngagement engagement) {
        return List.of(new ProposedEvent(engagement.hiveId(), new RouteEngagementResolved(engagement.id(), RouteEngagementOutcome.ABORTED)));
    }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static SubjectId engagementId(StrategicTask task) { return new SubjectId("engagement:" + task.id().value().substring("task:".length())); }
    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.INTERCEPT_ROUTE_OPERATION || task.status() != status) throw new IllegalStateException("hive route engagement has no matching strategic task");
        return task;
    }
}
