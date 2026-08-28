package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
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
        BlockPosition intercept = operation.route().get(operation.routeIndex());
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
        assertEquals(RouteEngagementStatus.COLD_COMBAT, engagement.status());
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

    @Test void coldCombatPersistsEveryExactStrikeAndFailsTheRouteWithoutAPlayer() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.route().get(operation.routeIndex());
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.GUARD).toList()) {
            state = state.withActorLocation(bioform.id(), intercept);
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-cold-combat"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-cold-combat"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));

        for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L))) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof RouteEngagementStarted started) state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
            if (event.payload() instanceof RouteEngagementTransition transition) state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
        }
        SubjectId engagementId = new SubjectId("engagement:hive-cold-combat");
        assertEquals(RouteEngagementStatus.COLD_COMBAT, state.strategicPlans().routeEngagements().get(engagementId).status());
        SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        assertEquals(engagementId, candidate.engagementId());
        assertEquals(operation.id(), candidate.operationId());
        assertEquals(intercept, candidate.handoffPosition());
        assertEquals(5, candidate.actorIds().size());
        SceneLeaseId leaseId = new SceneLeaseId("lease:hive-cold-combat-r1");
        List<SceneMember> sceneMembers = candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(leaseId, actor))).toList();
        SceneLeaseId incompleteLeaseId = new SceneLeaseId("lease:hive-cold-combat-incomplete");
        List<SceneMember> incompleteMembers = candidate.actorIds().stream().limit(2).map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(incompleteLeaseId, actor))).toList();
        SceneLease incomplete = new SceneLease(incompleteLeaseId, candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(),
                new SimInstant(3_001L), 1L, SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), incompleteMembers);
        FrontierWorldState coldBeforeLease = state;
        assertThrows(IllegalArgumentException.class, () -> coldBeforeLease.prepareSceneLease(incomplete));
        SceneLease lease = new SceneLease(leaseId, candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), new SimInstant(3_001L), 1L,
                SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), sceneMembers);
        FrontierWorldState hot = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertEquals(RouteEngagementStatus.HOT, hot.strategicPlans().routeEngagements().get(engagementId).status());
        SubjectId attacker = hot.strategicPlans().routeEngagements().get(engagementId).attackerIds().getFirst();
        SubjectId target = candidate.actorIds().stream().filter(actor -> !actor.equals(attacker) && !hot.strategicPlans().routeEngagements().get(engagementId).attackerIds().contains(actor)).findFirst().orElseThrow();
        FixedPosition strikeOrigin = new FixedPosition(FixedScalar.whole(candidate.handoffPosition().x()), FixedScalar.whole(candidate.handoffPosition().y()), FixedScalar.whole(candidate.handoffPosition().z()));
        PhysicalIntent strikeIntent = new PhysicalIntent(new PhysicalIntentId("intent:scene-strike-test"), PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                operation.id(), List.of(attacker, target), strikeOrigin, 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
        PhysicalIntent foreignTarget = new PhysicalIntent(new PhysicalIntentId("intent:scene-strike-foreign"), PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                operation.id(), List.of(attacker, new SubjectId("resident:12-1")), strikeOrigin, 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
        assertThrows(IllegalArgumentException.class, () -> hot.preparePhysicalIntent(foreignTarget));
        PhysicalIntent unknownTarget = new PhysicalIntent(new PhysicalIntentId("intent:scene-strike-unknown"), PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                operation.id(), List.of(attacker, new SubjectId("actor:unknown")), strikeOrigin, 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
        assertThrows(IllegalArgumentException.class, () -> hot.preparePhysicalIntent(unknownTarget));
        SceneStrikeObservation strikeReceipt = new SceneStrikeObservation(new PhysicalObservationId("observation:scene-strike-test"), strikeIntent.id(), attacker, target,
                FixedScalar.whole(20), FixedScalar.ZERO);
        FrontierWorldState struck = hot.preparePhysicalIntent(strikeIntent)
                .transitionPhysicalIntent(strikeIntent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())
                .recordActorDeath(new ActorDied(leaseId, target, hot.actorLocations().get(target).position(), "scene-strike-test"))
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING)
                .transitionPhysicalIntent(strikeIntent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(strikeReceipt));
        assertEquals(strikeReceipt, struck.physicalObservations().get(strikeReceipt.id()));
        assertEquals(struck, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(struck)));
        PhysicalIntentTransition strikeTransition = new PhysicalIntentTransition(strikeIntent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(strikeReceipt));
        assertEquals(strikeTransition, FrontierWorldRuntimeDefinition.payloadCodecs().decode(strikeTransition.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(strikeTransition)));
        List<SceneMemberPosition> postStrikeSurvivors = candidate.actorIds().stream().filter(actor -> !actor.equals(target))
                .map(actor -> new SceneMemberPosition(actor, struck.actorLocations().get(actor).position(), struck.actorLocations().get(actor).condition().health())).toList();
        FrontierWorldState closedStrike = struck.releaseSceneLease(leaseId, postStrikeSurvivors);
        assertEquals(strikeReceipt, closedStrike.physicalObservations().get(strikeReceipt.id()));
        FrontierWorldState recovered = hot.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                .transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertEquals(RouteEngagementStatus.HOT, recovered.strategicPlans().routeEngagements().get(engagementId).status());
        assertEquals(SceneLeaseStatus.HOT, recovered.sceneLeases().get(leaseId).status());
        SubjectId deadActor = candidate.actorIds().stream().filter(actor -> !hot.strategicPlans().routeEngagements().get(engagementId).attackerIds().contains(actor)).findFirst().orElseThrow();
        FrontierWorldState afterRecordedDeath = hot.recordActorDeath(new ActorDied(leaseId, deadActor, hot.actorLocations().get(deadActor).position(), "restart-fixture"));
        List<SceneMemberPosition> surviving = candidate.actorIds().stream().filter(actor -> !actor.equals(deadActor))
                .map(actor -> new SceneMemberPosition(actor, afterRecordedDeath.actorLocations().get(actor).position(), afterRecordedDeath.actorLocations().get(actor).condition().health())).toList();
        FrontierWorldState drainedRecovery = afterRecordedDeath.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING).releaseSceneLease(leaseId, surviving);
        assertEquals(RouteEngagementStatus.COLD_COMBAT, drainedRecovery.strategicPlans().routeEngagements().get(engagementId).status());
        assertEquals(SceneLeaseStatus.CLOSED, drainedRecovery.sceneLeases().get(leaseId).status());
        List<SceneMemberPosition> captured = candidate.actorIds().stream().map(actor -> new SceneMemberPosition(actor, hot.actorLocations().get(actor).position(),
                hot.actorLocations().get(actor).condition().health())).toList();
        state = hot.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING).releaseSceneLease(leaseId, captured);
        assertEquals(RouteEngagementStatus.COLD_COMBAT, state.strategicPlans().routeEngagements().get(engagementId).status());

        RouteEngagementStrike first = (RouteEngagementStrike) HiveRouteEngagementProcess.planCombat(state,
                new ScheduledAction(new ScheduleId("schedule:test-first"), new SimInstant(3_020L), 0, engagementId, "frontier.hive_route_engagement.combat", 1)).getFirst().payload();
        assertEquals(first, FrontierWorldRuntimeDefinition.payloadCodecs().decode(first.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(first)));
        state = HiveRouteEngagementProcess.reduceStrike(state, hive, first);
        FrontierWorldState afterFirst = state;
        assertThrows(IllegalArgumentException.class, () -> HiveRouteEngagementProcess.reduceStrike(afterFirst, hive, first));

        boolean cancelledFailedOperationProgress = false;
        for (int step = 1; step < 64 && state.strategicPlans().routeEngagements().get(engagementId).status() != RouteEngagementStatus.RESOLVED; step++) {
            List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveRouteEngagementProcess.planCombat(state,
                    new ScheduledAction(new ScheduleId("schedule:test-" + step), new SimInstant(3_020L + step * 20L), 0, engagementId, "frontier.hive_route_engagement.combat", 1));
            for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : events) {
                if (event.payload() instanceof RouteEngagementStrike strike) state = HiveRouteEngagementProcess.reduceStrike(state, hive, strike);
                if (event.payload() instanceof RouteEngagementResolved resolved) state = HiveRouteEngagementProcess.reduceResolved(state, hive, resolved);
                if (event.payload() instanceof ScheduleEffect.Cancelled cancelled
                        && cancelled.scheduleId().equals(SupplyOperationProcess.operationProgress(operation, 0L).id())) cancelledFailedOperationProgress = true;
            }
        }
        RouteEngagement resolved = state.strategicPlans().routeEngagements().get(engagementId);
        assertEquals(RouteEngagementStatus.RESOLVED, resolved.status());
        assertEquals(Optional.of(RouteEngagementOutcome.HIVE_VICTORY), resolved.outcome());
        assertTrue(cancelledFailedOperationProgress);
        assertEquals(OperationStage.FAILED, state.operations().get(operation.id()).stage());
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void productionProfileAutonomouslyHoldsARealCaravanUntilHiveGuardsReachItsCurrentPosition() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production-intercept"), 91L));
        boolean heldAtCurrentIntercept = false;
        boolean reachedColdCombat = false;
        FrontierWorldState latest = null;

        for (long tick = 20L; tick <= 10_000L; tick += 20L) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            latest = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            for (RouteEngagement engagement : latest.strategicPlans().routeEngagements().values()) {
                RouteOperation operation = latest.operations().get(engagement.operationId());
                if (operation == null || operation.stage() != OperationStage.EN_ROUTE) continue;
                if (operation.route().get(operation.routeIndex()).equals(engagement.intercept())) heldAtCurrentIntercept = true;
                if (engagement.status() == RouteEngagementStatus.COLD_COMBAT) reachedColdCombat = true;
            }
        }

        FrontierWorldState finalState = latest;
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        assertTrue(heldAtCurrentIntercept, () -> "normal profile never held an EN_ROUTE caravan at its intercept: " + finalState.strategicPlans().routeEngagements());
        assertTrue(reachedColdCombat, () -> "normal profile never reached COLD combat: " + finalState.strategicPlans().routeEngagements());
    }

    private static FrontierWorldState enRouteState() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:intercept"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE));
        return state;
    }
}
