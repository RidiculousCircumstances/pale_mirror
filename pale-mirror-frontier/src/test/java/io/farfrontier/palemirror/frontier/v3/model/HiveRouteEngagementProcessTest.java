package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveRouteEngagementProcessTest {
    @Test void hiveReviewPersistsTheExactOperationTargetBeforeSchedulingAnInterception() {
        FrontierWorldState state = enRouteState().withStrategicPlans(StrategicPlanState.empty());
        SubjectId hive = state.bootstrap().hive().id();

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = StrategicObjectiveProcess.plan(state,
                StrategicObjectiveProcess.review(hive, 1, 2_600L));

        StrategicTask task = ((StrategicTaskPlanned) events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(StrategicTaskPlanned.class::isInstance).findFirst().orElseThrow()).task();
        assertEquals(StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, task.kind());
        assertEquals(HiveRouteEngagementProcess.targetOperation(state), task.operationTarget());
        assertTrue(events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).anyMatch(ScheduleEffect.Created.class::isInstance));
    }

    @Test void taskBindsOneOperationAndMovesExactGuardsThroughPersistedColdRoutes() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.route().get(Math.max(operation.routeIndex(), operation.route().size() - 2));
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.GUARD).toList()) {
            state = state.withActorLocation(bioform.id(), intercept.offset(-16, 0, 0));
        }
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-intercept"), state.bootstrap().hive().id(),
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-intercept"), objective.id(), objective.ownerId(),
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> start = HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 2_600L));
        assertEquals(3, start.size());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, objective.ownerId(), (StrategicTaskTransition) start.getFirst().payload());
        RouteEngagementStarted started = (RouteEngagementStarted) start.get(1).payload();
        assertEquals(operation.id(), started.engagement().operationId());
        assertEquals(Optional.of(operation.id()), task.operationTarget());
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        state = HiveRouteEngagementProcess.reduceStarted(state, objective.ownerId(), started);
        ScheduledAction scheduled = ((ScheduleEffect.Created) start.get(2).payload()).action();

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> progress = HiveRouteEngagementProcess.planProgress(state, scheduled);
        for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : progress) {
            if (event.payload() instanceof RouteEngagementAttackerAdvanced advanced) {
                assertEquals(advanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advanced.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(advanced)));
                state = HiveRouteEngagementProcess.reduceAdvanced(state, objective.ownerId(), advanced);
            } else if (event.payload() instanceof RouteEngagementTransition transition) {
                state = HiveRouteEngagementProcess.reduceTransition(state, objective.ownerId(), transition);
            }
        }
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(started.engagement().id());
        assertEquals(RouteEngagementStatus.READY_FOR_SCENE, engagement.status());
        FrontierWorldState completed = state;
        assertTrue(engagement.attackers().stream().allMatch(attacker -> completed.actorLocations().get(attacker.actorId()).position().equals(intercept)));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void interceptTaskRejectsAnOperationThatDoesNotExistInCanonicalWorld() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:intercept-negative"), 91L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-intercept"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-intercept"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(new SubjectId("operation:missing")),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective).addTask(task);

        assertThrows(IllegalArgumentException.class, () -> state.withStrategicPlans(plans));
    }

    private static FrontierWorldState enRouteState() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:intercept"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE));
        return state;
    }
}
