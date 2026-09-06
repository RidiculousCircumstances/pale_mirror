package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveRouteEngagementProcessTest {
    @Test void hotOrRecoveryLeaseDefersTheSameColdInterceptionWithoutClaimingItsOperation() {
        FrontierWorldState state = enRouteState().withStrategicPlans(StrategicPlanState.empty());
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        SceneLease lease = routeLease(state, operation, "lease:intercept-exclusive", SceneLeaseStatus.PREPARED);
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);

        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-intercept-exclusive"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-intercept-exclusive"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(operation.currentPosition()));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = withSighting(state, operation, 3_000L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> deferred = HiveRouteEngagementProcess.planStart(state,
                HiveRouteEngagementProcess.start(task, 3_000L));
        assertEquals(1, deferred.size());
        ScheduleEffect.Created retry = (ScheduleEffect.Created) deferred.getFirst().payload();
        assertEquals(3_100L, retry.action().dueAt().ticks());
        assertEquals(task.id(), retry.action().subject());
    }

    @Test void hiveReviewDoesNotInspectAnUnobservedHumanOperation() {
        FrontierWorldState state = enRouteState().withStrategicPlans(StrategicPlanState.empty());
        SubjectId hive = state.bootstrap().hive().id();

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = StrategicObjectiveProcess.plan(state,
                StrategicObjectiveProcess.review(hive, 1, 2_600L));

        List<StrategicTask> tasks = events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(StrategicTaskPlanned.class::isInstance).map(StrategicTaskPlanned.class::cast).map(StrategicTaskPlanned::task).toList();
        assertFalse(tasks.stream().anyMatch(task -> task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION),
                "an unobserved human operation may not become a hive target");
        assertTrue(tasks.stream().allMatch(task -> task.operationTarget().isEmpty()));
        assertTrue(events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).anyMatch(ScheduleEffect.Created.class::isInstance));
    }

    @Test void taskBindsOneOperationAndMovesExactGuardsThroughPersistedColdRoutes() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept.offset(-16, 0, 0))));
        }
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-intercept"), state.bootstrap().hive().id(),
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-intercept"), objective.id(), objective.ownerId(),
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = withSighting(state, operation, 2_600L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> start = HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 2_600L));
        assertEquals(4, start.size());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, objective.ownerId(), (StrategicTaskTransition) start.getFirst().payload());
        RouteEngagementStarted started = (RouteEngagementStarted) start.get(1).payload();
        assertEquals(operation.id(), started.engagement().operationId());
        assertEquals(Optional.of(operation.id()), task.operationTarget());
        assertEquals(Optional.of(intercept), task.operationObservationPosition());
        StrategicTaskPlanned persistedTask = new StrategicTaskPlanned(task);
        assertEquals(persistedTask, FrontierWorldRuntimeDefinition.payloadCodecs().decode(persistedTask.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(persistedTask)));
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        state = HiveRouteEngagementProcess.reduceStarted(state, objective.ownerId(), started);
        ScheduledAction scheduled = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action)
                .filter(value -> value.kind().equals("frontier.hive_route_engagement.progress")).findFirst().orElseThrow();

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
        assertTrue(engagement.attackers().stream().allMatch(attacker -> completed.actorLocations().get(attacker.actorId()).supportingSurface().support().equals(intercept)));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void loadedSceneConflictIsNotRestartUnknownAndCanResumeOnlyThroughFreshPreparation() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream()
                .filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-scene-conflict"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-scene-conflict"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = withSighting(state, operation, 3_000L);
        for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L))) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof RouteEngagementStarted started) state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
            if (event.payload() instanceof RouteEngagementTransition transition) state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
        }
        RouteEngagement engagement = state.strategicPlans().routeEngagements().values().stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:hot-scene-conflict");
        List<SubjectId> actorIds = java.util.stream.Stream.concat(operation.participantIds().stream(), engagement.attackerIds().stream()).sorted().toList();
        SceneLease lease = FrontierTestSceneLeases.exact(state, leaseId, operation.id(), operation.cargoId(), operation.currentPosition(),
                new SimInstant(3_000L), 0L, Optional.of(engagement.id()), actorIds);

        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT)
                .transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        assertEquals(SceneLeaseStatus.CONFLICT, state.sceneLeases().get(leaseId).status());
        assertTrue(state.sceneLeases().get(leaseId).recoveryEvidence().isEmpty(), "a live obstruction must not manufacture restart evidence");
        assertEquals(RouteEngagementStatus.CONFLICT, state.strategicPlans().routeEngagements().get(engagement.id()).status());
        FrontierObjectBoard routeBoard = FrontierReadabilityPlan.compile(state).boards().get(FrontierRouteNetwork.OWNER);
        assertEquals(FrontierObjectBoard.Tone.WARNING, routeBoard.tone());
        assertTrue(routeBoard.text().endsWith("SCENE BLOCKED · KEEP CLEAR"), "the player-facing route board must name a live obstruction");
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertEquals(new SceneLeaseTransition(leaseId, SceneLeaseStatus.CONFLICT), FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                "frontier.scene_lease_transition", FrontierWorldRuntimeDefinition.payloadCodecs().encode(new SceneLeaseTransition(leaseId, SceneLeaseStatus.CONFLICT))));

        state = state.transitionSceneLease(leaseId, SceneLeaseStatus.PREPARED).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertEquals(SceneLeaseStatus.HOT, state.sceneLeases().get(leaseId).status());
        assertEquals(RouteEngagementStatus.HOT, state.strategicPlans().routeEngagements().get(engagement.id()).status());
    }

    @Test void interceptTaskRejectsAnOperationThatDoesNotExistInCanonicalWorld() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:intercept-negative"), 91L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-intercept"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> new StrategicTask(new SubjectId("task:hive-intercept"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(new SubjectId("operation:missing")),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING),
                "a current-format interception cannot omit its retained observed position");
    }

    @Test void legacyInterceptTaskWithoutAnObservedPositionCannotAcquireOneAtExecutionTime() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-legacy-intercept"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> new StrategicTask(new SubjectId("task:hive-legacy-intercept"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING),
                "fresh schema rejects a legacy interception before it can invent a target at execution time");
    }

    @Test void coldCombatPersistsEveryExactStrikeAndFailsTheRouteWithoutAPlayer() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-cold-combat"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-cold-combat"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = withSighting(state, operation, 3_000L);

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
        assertEquals(operation.currentPosition(), candidate.handoffPosition(),
                "a carrier sighting must not overwrite the adjacent participant hand-off");
        assertEquals(intercept, candidate.cargoPosition(),
                "the engagement must retain the exact cargo anchor the Scout observed");
        assertEquals(7, candidate.actorIds().size(), "the exact mobile Overseer is present in the same owned scene roster");
        SceneLeaseId leaseId = new SceneLeaseId("lease:hive-cold-combat-r1");
        var world = state.bootstrap().worldId();
        List<SceneMember> sceneMembers = candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))).toList();
        SceneLeaseId incompleteLeaseId = new SceneLeaseId("lease:hive-cold-combat-incomplete");
        List<SceneMember> incompleteMembers = candidate.actorIds().stream().limit(2).map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))).toList();
        FrontierWorldState incompleteState = state;
        SceneLease incomplete = SceneLease.atExactPositions(incompleteLeaseId, world, candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(),
                candidate.cargoPosition(), new SimInstant(3_001L), 1L, SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), incompleteMembers,
                incompleteMembers.stream().collect(java.util.stream.Collectors.toMap(SceneMember::actorId,
                        member -> incompleteState.actorLocations().get(member.actorId()).body(), (left, right) -> left, java.util.LinkedHashMap::new)));
        FrontierWorldState coldBeforeLease = state;
        assertThrows(IllegalArgumentException.class, () -> coldBeforeLease.prepareSceneLease(incomplete));
        SceneLease lease = FrontierTestSceneLeases.exact(state, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), new SimInstant(3_001L), 1L, Optional.of(candidate.engagementId()), candidate.actorIds());
        FrontierWorldState hot = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertEquals(RouteEngagementStatus.HOT, hot.strategicPlans().routeEngagements().get(engagementId).status());
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> commandedWhileHot = HiveRouteEngagementProcess.planCommandControl(hot,
                new ScheduledAction(new ScheduleId("schedule:hot-controller-is-live"), new SimInstant(3_020L), 0, engagementId,
                        "frontier.hive_route_engagement.control", 1));
        assertFalse(commandedWhileHot.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .anyMatch(RouteEngagementCommandAuthorityChanged.class::isInstance), "the exact controller remains connected while its own scene owns physical bodies");
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
                .recordActorDeath(new ActorDied(leaseId, target, hot.actorLocations().get(target).body(), "scene-strike-test"), 0L)
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING)
                .transitionPhysicalIntent(strikeIntent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(strikeReceipt));
        assertEquals(strikeReceipt, struck.physicalObservations().get(strikeReceipt.id()));
        assertEquals(struck, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(struck)));
        PhysicalIntentTransition strikeTransition = new PhysicalIntentTransition(strikeIntent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(strikeReceipt));
        assertEquals(strikeTransition, FrontierWorldRuntimeDefinition.payloadCodecs().decode(strikeTransition.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(strikeTransition)));
        List<SceneMemberPosition> postStrikeSurvivors = candidate.actorIds().stream().filter(actor -> !actor.equals(target))
                .map(actor -> {
                    ActorLocation location = struck.actorLocations().get(actor);
                    return new SceneMemberPosition(actor, FrontierTestPositions.bodyCellOf(location), location.condition().health());
                }).toList();
        FrontierWorldState closedStrike = struck.releaseSceneLease(leaseId, postStrikeSurvivors);
        assertEquals(strikeReceipt, closedStrike.physicalObservations().get(strikeReceipt.id()));
        FrontierWorldState recovered = hot.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                .transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertEquals(RouteEngagementStatus.HOT, recovered.strategicPlans().routeEngagements().get(engagementId).status());
        assertEquals(SceneLeaseStatus.HOT, recovered.sceneLeases().get(leaseId).status());
        SubjectId deadActor = candidate.actorIds().stream().filter(actor -> !hot.strategicPlans().routeEngagements().get(engagementId).attackerIds().contains(actor)).findFirst().orElseThrow();
        FrontierWorldState afterRecordedDeath = hot.recordActorDeath(new ActorDied(leaseId, deadActor, hot.actorLocations().get(deadActor).body(), "restart-fixture"), 0L);
        List<SceneMemberPosition> surviving = candidate.actorIds().stream().filter(actor -> !actor.equals(deadActor))
                .map(actor -> {
                    ActorLocation location = afterRecordedDeath.actorLocations().get(actor);
                    return new SceneMemberPosition(actor, FrontierTestPositions.bodyCellOf(location), location.condition().health());
                }).toList();
        FrontierWorldState drainedRecovery = afterRecordedDeath.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING).releaseSceneLease(leaseId, surviving);
        assertEquals(RouteEngagementStatus.COLD_COMBAT, drainedRecovery.strategicPlans().routeEngagements().get(engagementId).status());
        assertEquals(SceneLeaseStatus.CLOSED, drainedRecovery.sceneLeases().get(leaseId).status());
        List<SceneMemberPosition> captured = candidate.actorIds().stream().map(actor -> {
            ActorLocation location = hot.actorLocations().get(actor);
            return new SceneMemberPosition(actor, location.body(), location.condition().health());
        }).toList();
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

    @Test void coldCombatWaitsRatherThanResolvingOverAnActiveRouteScene() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-cold-exclusive"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-cold-exclusive"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = withSighting(state, operation, 3_000L);
        for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L))) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof RouteEngagementStarted started) state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
            if (event.payload() instanceof RouteEngagementTransition transition) state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
        }
        SubjectId engagementId = new SubjectId("engagement:hive-cold-exclusive");
        SceneLease lease = routeLease(state, operation, "lease:cold-exclusive", SceneLeaseStatus.PREPARED);
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> deferred = HiveRouteEngagementProcess.planCombat(state,
                new ScheduledAction(new ScheduleId("schedule:cold-exclusive"), new SimInstant(3_020L), 0, engagementId,
                        "frontier.hive_route_engagement.combat", 1));
        assertEquals(1, deferred.size());
        ScheduleEffect.Created retry = (ScheduleEffect.Created) deferred.getFirst().payload();
        assertEquals(3_040L, retry.action().dueAt().ticks());
        assertEquals(OperationStage.EN_ROUTE, state.operations().get(operation.id()).stage());
    }

    @Test void coldCombatDefersAndReducerRejectsAnActorRetainedByActiveOrRecoveryAmbientLease() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-ambient-recovery-exclusive"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-ambient-recovery-exclusive"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = withSighting(state, operation, 3_000L);
        for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L))) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof RouteEngagementStarted started) state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
            if (event.payload() instanceof RouteEngagementTransition transition) state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
        }
        SubjectId engagementId = new SubjectId("engagement:hive-ambient-recovery-exclusive");
        SubjectId defender = operation.participantIds().getFirst();
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, defender, new SimInstant(3_010L));
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = AmbientLeaseStateProcess.transition(state, defender, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> deferred = HiveRouteEngagementProcess.planCombat(state,
                new ScheduledAction(new ScheduleId("schedule:ambient-recovery-exclusive"), new SimInstant(3_020L), 0, engagementId,
                        "frontier.hive_route_engagement.combat", 1));
        assertEquals(1, deferred.size());
        ScheduleEffect.Created retry = (ScheduleEffect.Created) deferred.getFirst().payload();
        assertEquals(3_040L, retry.action().dueAt().ticks());
        assertEquals(OperationStage.EN_ROUTE, state.operations().get(operation.id()).stage());

        RouteEngagementStrike impossible = new RouteEngagementStrike(engagementId, state.strategicPlans().routeEngagements().get(engagementId).attackerIds().getFirst(), defender,
                0, RouteEngagementCombatRules.damage(state, state.strategicPlans().routeEngagements().get(engagementId).attackerIds().getFirst()));
        FrontierWorldState retained = state;
        assertThrows(IllegalArgumentException.class, () -> HiveRouteEngagementProcess.reduceStrike(retained, hive, impossible));

        state = AmbientLeaseStateProcess.transition(state, defender, AmbientLeaseStatus.HOT);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> hotDeferred = HiveRouteEngagementProcess.planCombat(state,
                new ScheduledAction(new ScheduleId("schedule:ambient-hot-exclusive"), new SimInstant(3_040L), 0, engagementId,
                        "frontier.hive_route_engagement.combat", 1));
        assertEquals(1, hotDeferred.size());
        assertEquals(3_060L, ((ScheduleEffect.Created) hotDeferred.getFirst().payload()).action().dueAt().ticks());
    }

    @Test void autonomousSupplyProfileCreatesOnlyAnExactScoutBoundInterceptionBeforeTheMobilizationOwnerExists() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.autonomousSupplyInterceptionConfiguration(new WorldId("frontier:production-intercept"), 91L));
        FrontierWorldState latest = null;
        boolean sighted = false;
        boolean interceptedFromBoundPosition = false;

        for (long tick = 20L; tick <= 10_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            latest = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            sighted |= !latest.strategicPlans().hiveOperationKnowledge().entries().isEmpty();
            interceptedFromBoundPosition |= latest.strategicPlans().tasks().values().stream().anyMatch(task -> task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION
                    && task.operationTarget().isPresent() && task.operationObservationPosition().isPresent());
        }

        FrontierWorldState finalState = latest;
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        assertTrue(sighted, "an interception must first retain a Scout-owned sighting");
        assertTrue(interceptedFromBoundPosition, "the durable intercept task must retain the exact Scout-observed position");
        assertTrue(finalState.strategicPlans().routeEngagements().values().stream().allMatch(engagement -> engagement.attackerIds().stream()
                        .allMatch(actor -> HivePhysiologySupport.permitsAmbientLease(finalState.hiveColony(), actor))),
                () -> "cocoon-retained bioform became an autonomous intercept attacker: " + finalState.strategicPlans().routeEngagements());
    }

    @Test void remoteInterceptionRejectsDirectBodiesWithoutAnExactRelayOrOverseer() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:no-controller"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:no-controller"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()), Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = withSighting(state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), operation, 3_000L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L));
        assertEquals(1, events.size());
        assertEquals(StrategicTaskStatus.BLOCKED, ((StrategicTaskTransition) events.getFirst().payload()).status());
    }

    @Test void relayCoverageAdmitsOnlyTheExactLocalAttackerRoutes() {
        FrontierWorldState state = enRouteState();
        SubjectId hive = state.bootstrap().hive().id();
        SubjectId eastNest = new SubjectId("nest:seed-east");
        HiveOrgan ganglion = state.bootstrap().hive().organs().stream()
                .filter(value -> value.nestId().equals(eastNest) && value.kind() == HiveOrganKind.GANGLION).findFirst().orElseThrow();
        HiveOrgan relay = new HiveOrgan(new SubjectId("organ:east-command-relay"), hive, eastNest, HiveOrganKind.RELAY,
                ganglion.anchor().offset(32, 0, 0), Optional.empty());
        state = state.addHiveOrgan(relay);
        List<Bioform> localBodies = state.bootstrap().hive().bioforms().stream()
                .filter(value -> value.nestId().equals(eastNest) && (value.isDefender() || value.isExplosiveAssaulter())).limit(2).toList();
        assertEquals(2, localBodies.size());
        List<EngagementAttacker> attackers = new java.util.ArrayList<>();
        for (int index = 0; index < localBodies.size(); index++) {
            Bioform bioform = localBodies.get(index);
            BlockPosition origin = relay.anchor().offset(index + 1, 0, 0);
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(origin)));
            attackers.add(new EngagementAttacker(bioform.id(), List.of(origin, relay.anchor()), 0));
        }

        HiveOperationCommandAuthority authority = HiveRouteEngagementCommandSupport.admit(state, attackers, 3_000L).orElseThrow();

        assertEquals(HiveCommandAuthorityKind.RELAY, authority.kind());
        assertEquals(relay.id(), authority.currentAuthorityId());
        assertEquals(relay.anchor(), authority.relayCoverage().orElseThrow().centre());
        assertEquals(attackers.stream().map(EngagementAttacker::actorId).toList(), authority.rosterIds());
    }

    @Test void relayLossSurvivesRestartAsBoundedMemoryThenInstinctWithoutRewritingItsProof() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        SubjectId hive = state.bootstrap().hive().id(), eastNest = new SubjectId("nest:seed-east");
        HiveOrgan ganglion = state.bootstrap().hive().organs().stream()
                .filter(value -> value.nestId().equals(eastNest) && value.kind() == HiveOrganKind.GANGLION).findFirst().orElseThrow();
        HiveOrgan relay = new HiveOrgan(new SubjectId("organ:east-loss-relay"), hive, eastNest, HiveOrganKind.RELAY,
                ganglion.anchor().offset(32, 0, 0), Optional.empty());
        state = state.addHiveOrgan(relay);
        List<Bioform> localBodies = state.bootstrap().hive().bioforms().stream()
                .filter(value -> value.nestId().equals(eastNest) && (value.isDefender() || value.isExplosiveAssaulter())).limit(2).toList();
        List<EngagementAttacker> attackers = new java.util.ArrayList<>();
        for (int index = 0; index < localBodies.size(); index++) {
            Bioform bioform = localBodies.get(index);
            BlockPosition origin = relay.anchor().offset(index + 1, 0, 0);
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(origin)));
            attackers.add(new EngagementAttacker(bioform.id(), List.of(origin, relay.anchor()), 0));
        }
        HiveOperationCommandAuthority authority = HiveRouteEngagementCommandSupport.admit(state, attackers, 3_000L).orElseThrow();
        assertEquals(HiveCommandAuthorityKind.RELAY, authority.kind());
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:relay-loss"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:relay-loss"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()), Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.ACTIVE, Optional.of(relay.anchor()));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        RouteEngagement engagement = new RouteEngagement(new SubjectId("engagement:relay-loss"), task.id(), operation.id(), hive,
                attackers, relay.anchor(), authority, RouteEngagementStatus.APPROACHING, 0, Optional.empty());
        state = HiveRouteEngagementProcess.reduceStarted(state, hive, new RouteEngagementStarted(engagement));
        assertTrue(HiveRouteEngagementCommandSupport.connected(state, engagement));

        HiveOrgan selectedRelay = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(value -> value.id().equals(authority.currentAuthorityId())).findFirst().orElseThrow();
        state = destroyOrgan(state, selectedRelay);
        assertFalse(state.isHiveOrganOperational(selectedRelay.id()));
        assertEquals(HiveCommandAuthorityKind.RELAY, state.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority().kind());
        assertEquals(selectedRelay.id(), state.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority().currentAuthorityId());
        assertFalse(HiveRouteEngagementCommandSupport.connected(state,
                state.strategicPlans().routeEngagements().get(engagement.id())));
        state = reduceControl(state, engagement.id(), 3_010L);
        HiveOperationCommandAuthority memory = state.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority();
        assertEquals(HiveCommandSignalPhase.SIGNAL_MEMORY, memory.signalPhase());
        assertEquals(authority.rosterIds(), memory.rosterIds());
        assertEquals(authority.relayCoverage(), memory.relayCoverage());

        FrontierWorldState restarted = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        restarted = reduceControl(restarted, engagement.id(), 3_210L);
        HiveOperationCommandAuthority instinct = restarted.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority();
        assertEquals(HiveCommandSignalPhase.INSTINCT, instinct.signalPhase());
        assertEquals(memory.rosterIds(), instinct.rosterIds());
        assertEquals(memory.relayCoverage(), instinct.relayCoverage());
    }

    @Test void signalTransitionCannotRewriteTheExactCommandRosterOrCapacity() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:command-forgery"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:command-forgery"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()), Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = withSighting(state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), operation, 3_000L);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, (StrategicTaskTransition) events.getFirst().payload());
        RouteEngagementStarted started = events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(RouteEngagementStarted.class::isInstance).map(RouteEngagementStarted.class::cast).findFirst().orElseThrow();
        state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(started.engagement().id());
        HiveOperationCommandAuthority expected = engagement.commandAuthority();
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        ActorLocation controller = actors.get(expected.currentAuthorityId());
        actors.put(expected.currentAuthorityId(), new ActorLocation(controller.body(), ActorCondition.dead()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        HiveOperationCommandAuthority forged = new HiveOperationCommandAuthority(expected.kind(), expected.originalAuthorityId(), expected.currentAuthorityId(),
                expected.rosterIds(), expected.subordinateWeight() + 1, expected.relayCoverage(), HiveCommandSignalPhase.SIGNAL_MEMORY, 3_010L);
        FrontierWorldState retained = state;

        assertThrows(IllegalArgumentException.class, () -> HiveRouteEngagementProcess.reduceCommandAuthorityChanged(retained, hive,
                new RouteEngagementCommandAuthorityChanged(engagement.id(), expected, forged)));
    }

    @Test void controllerLossUsesBoundedMemoryThenInstinctAndAnExactSecondOverseerReclaimsSurvivors() {
        FrontierWorldState state = enRouteState();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream().filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:controller-loss"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:controller-loss"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()), Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = withSighting(state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), operation, 3_000L);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> started = HiveRouteEngagementProcess.planStart(state, HiveRouteEngagementProcess.start(task, 3_000L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, (StrategicTaskTransition) started.getFirst().payload());
        RouteEngagementStarted start = (RouteEngagementStarted) started.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(RouteEngagementStarted.class::isInstance).findFirst().orElseThrow();
        state = HiveRouteEngagementProcess.reduceStarted(state, hive, start);
        for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : started) {
            if (event.payload() instanceof RouteEngagementTransition transition) {
                state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
            }
        }
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(start.engagement().id());
        assertEquals(RouteEngagementStatus.COLD_COMBAT, engagement.status());
        assertEquals(List.of(engagement.id()), state.coldEngagementSceneCandidates().stream().map(SceneEngagementCandidate::engagementId).toList());
        SubjectId fallen = engagement.commandAuthority().currentAuthorityId();
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        ActorLocation controller = actors.get(fallen); actors.put(fallen, new ActorLocation(controller.body(), ActorCondition.dead()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));

        state = reduceControl(state, engagement.id(), 3_010L);
        assertEquals(HiveCommandSignalPhase.SIGNAL_MEMORY, state.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority().signalPhase());
        state = reduceControl(state, engagement.id(), 3_210L);
        assertEquals(HiveCommandSignalPhase.INSTINCT, state.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority().signalPhase());
        assertTrue(state.coldEngagementSceneCandidates().isEmpty(), "instinct survivors may not admit a new coordinated HOT scene");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> instinctCombat = HiveRouteEngagementProcess.planCombat(state,
                HiveRouteEngagementProcess.combat(engagement, 3_211L));
        assertEquals(1, instinctCombat.size(), "instinct may wait for a reclaim, never continue strategic target selection");
        ScheduleEffect.Created instinctRetry = (ScheduleEffect.Created) instinctCombat.getFirst().payload();
        assertEquals("frontier.hive_route_engagement.control", instinctRetry.action().kind());
        RouteEngagementStrike forbiddenStrike = new RouteEngagementStrike(engagement.id(), engagement.attackerIds().getFirst(),
                operation.participantIds().getFirst(), engagement.nextStrikeEpoch(),
                RouteEngagementCombatRules.damage(state, engagement.attackerIds().getFirst()));
        FrontierWorldState instinctRetained = state;
        assertThrows(IllegalArgumentException.class, () -> HiveRouteEngagementProcess.reduceStrike(instinctRetained, hive, forbiddenStrike));
        SceneLease forbiddenScene = FrontierTestSceneLeases.exact(state, new SceneLeaseId("lease:instinct-command-forbidden"), operation.id(), operation.cargoId(),
                operation.currentPosition(), new SimInstant(3_211L), 0L, Optional.of(engagement.id()),
                java.util.stream.Stream.concat(operation.participantIds().stream(), engagement.attackerIds().stream()).sorted().toList());
        FrontierWorldState instinctState = state;
        assertThrows(IllegalArgumentException.class, () -> instinctState.prepareSceneLease(forbiddenScene),
                "a hand-crafted physical lease may not bypass the same command authority boundary");
        state = reduceControl(state, engagement.id(), 3_220L);
        HiveOperationCommandAuthority reclaimed = state.strategicPlans().routeEngagements().get(engagement.id()).commandAuthority();
        assertEquals(HiveCommandSignalPhase.RECLAIMED, reclaimed.signalPhase());
        assertFalse(reclaimed.currentAuthorityId().equals(fallen));
        assertEquals(engagement.attackerIds(), reclaimed.rosterIds(), "reclaim changes signal authority, not exact survivor roster or actor ownership");
        RouteEngagementCommandAuthorityChanged persisted = new RouteEngagementCommandAuthorityChanged(engagement.id(), engagement.commandAuthority(), reclaimed);
        assertEquals(persisted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(persisted.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(persisted)));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    private static FrontierWorldState enRouteState() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:intercept"), 91L));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE));
        return state;
    }

    private static SceneLease routeLease(FrontierWorldState state, RouteOperation operation, String id, SceneLeaseStatus status) {
        return FrontierTestSceneLeases.exact(state, new SceneLeaseId(id), operation.id(), operation.cargoId(),
                operation.currentPosition(), SimInstant.ZERO, 0L, Optional.empty(), operation.participantIds()).withStatus(status);
    }

    private static FrontierWorldState withSighting(FrontierWorldState state, RouteOperation operation, long observedAt) {
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        BlockPosition carrierPosition = operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support()).orElse(operation.currentPosition());
        state = FrontierTestPositions.deployBioform(state, scout.id(), BodyPosition.above(new SurfaceAnchor(carrierPosition)));
        HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation.id(), scout.id(), carrierPosition, observedAt);
        return state.withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(state.strategicPlans().hiveOperationKnowledge().observe(sighting)));
    }

    private static FrontierWorldState destroyOrgan(FrontierWorldState state, HiveOrgan organ) {
        int losses = (FrontierGrayboxPlan.intactOrganCellCount(state.bootstrap().terrain(), organ) + 2) / 3;
        List<GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(cell -> cell.ownerId().equals(organ.id()))
                .sorted(java.util.Comparator.comparingInt((GrayboxCell cell) -> cell.position().x()).thenComparingInt(cell -> cell.position().y())
                        .thenComparingInt(cell -> cell.position().z())).limit(losses).toList();
        if (cells.size() != losses) throw new IllegalStateException("test organ has too few exact cells");
        for (GrayboxCell cell : cells) {
            state = state.recordPhysicalDelta(new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                    Optional.of(organ.id()), Optional.of(cell.semanticPart()), "test:relay-loss"));
        }
        return state;
    }

    private static FrontierWorldState reduceControl(FrontierWorldState state, SubjectId engagementId, long at) {
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveRouteEngagementProcess.planCommandControl(state,
                new ScheduledAction(new ScheduleId("schedule:control-" + at), new SimInstant(at), 0, engagementId, "frontier.hive_route_engagement.control", 1));
        RouteEngagementCommandAuthorityChanged changed = events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(RouteEngagementCommandAuthorityChanged.class::isInstance).map(RouteEngagementCommandAuthorityChanged.class::cast).findFirst().orElseThrow();
        return HiveRouteEngagementProcess.reduceCommandAuthorityChanged(state, state.bootstrap().hive().id(), changed);
    }
}
