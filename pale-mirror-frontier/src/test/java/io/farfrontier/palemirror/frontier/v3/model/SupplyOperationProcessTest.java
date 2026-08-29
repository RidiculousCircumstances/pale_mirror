package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SupplyOperationProcessTest {
    @Test
    void missingBreadBlocksThePreparationAndItsDependentDelivery() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:supply-blocked"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:supply"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask preparation = new StrategicTask(new SubjectId("task:supply-prepare"), objective.id(), settlement.id(), StrategicTaskKind.PREPARE_BREAD_CARGO,
                Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.PENDING);
        StrategicTask delivery = new StrategicTask(new SubjectId("task:supply-deliver"), objective.id(), settlement.id(), StrategicTaskKind.DELIVER_BREAD_TO_HIVE,
                Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER,
                StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation.id()), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(preparation).addTask(delivery));

        List<ProposedEvent> planned = SupplyOperationProcess.planStart(state, SupplyOperationProcess.start(preparation, 100L));

        assertEquals(List.of(new StrategicTaskTransition(preparation.id(), StrategicTaskStatus.BLOCKED),
                new StrategicTaskTransition(delivery.id(), StrategicTaskStatus.BLOCKED)), planned.stream().map(ProposedEvent::payload).toList());
        FrontierWorldState reduced = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), (StrategicTaskTransition) planned.getFirst().payload());
        reduced = StrategicObjectiveProcess.reduceTaskTransition(reduced, settlement.id(), (StrategicTaskTransition) planned.get(1).payload());
        assertEquals(StrategicObjectiveStatus.BLOCKED, reduced.strategicPlans().objectives().get(objective.id()).status());
    }

    @Test
    void unknownHotLeaseDefersColdRouteProgressWithoutPretendingTheSceneIsActive() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(
                new WorldId("frontier:supply-unknown-scene"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:supply-unknown-scene");
        SceneLease lease = new SceneLease(leaseId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.route().getFirst(),
                engine.checkpoint().instant(), engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(before.bootstrap().worldId(), actor))).toList());
        FrontierWorldState unknown = before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);

        List<ProposedEvent> planned = SupplyOperationProcess.planProgress(unknown, SupplyOperationProcess.operationProgress(operation, 2_650L));

        ScheduleEffect.Created deferred = assertInstanceOf(ScheduleEffect.Created.class, planned.getFirst().payload());
        assertEquals(1, planned.size());
        assertEquals(SupplyOperationProcess.operationProgress(operation, 2_750L), deferred.action());
    }

    @Test
    void observedMissingRestartSceneBlocksOnlyItsExactDeliveryRatherThanReschedulingForever() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(
                new WorldId("frontier:supply-unresolved-scene"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:supply-unresolved-scene");
        SceneLease lease = new SceneLease(leaseId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.route().getFirst(),
                engine.checkpoint().instant(), engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(before.bootstrap().worldId(), actor))).toList());
        FrontierWorldState unresolved = FrontierSceneLeaseStateSupport.recoveryUnresolved(
                before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART),
                new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false));

        List<ProposedEvent> planned = SupplyOperationProcess.planProgress(unresolved, SupplyOperationProcess.operationProgress(operation, 2_650L));

        assertEquals(List.of(new OperationFailed(operation.id(), "scene-recovery-unresolved"),
                new StrategicTaskTransition(new SubjectId("task:settlement-1-settlement_deliver_bread_to_hive-2-deliver"), StrategicTaskStatus.BLOCKED)),
                planned.stream().map(ProposedEvent::payload).toList());
        assertEquals(unresolved, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unresolved)));
        SceneLeaseRecoveryUnresolved payload = new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
    }

    @Test
    void obsoleteProgressActionIsDurablyCancelledAfterItsOperationHasAlreadyFailed() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(
                new WorldId("frontier:supply-terminal-progress"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:supply-terminal-progress");
        SceneLease lease = new SceneLease(leaseId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.route().getFirst(),
                engine.checkpoint().instant(), engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(before.bootstrap().worldId(), actor))).toList());
        FrontierWorldState failed = FrontierSceneLeaseStateSupport.recoveryUnresolved(
                before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART),
                new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false));
        failed = failed.failOperation(operation.id());
        ScheduledAction action = SupplyOperationProcess.operationProgress(operation, 2_650L);

        List<ProposedEvent> planned = SupplyOperationProcess.planProgress(failed, action);

        ScheduleEffect.Cancelled cancelled = assertInstanceOf(ScheduleEffect.Cancelled.class, planned.getFirst().payload());
        assertEquals(action.id(), cancelled.scheduleId());
        assertEquals(1, planned.size());
    }
}
