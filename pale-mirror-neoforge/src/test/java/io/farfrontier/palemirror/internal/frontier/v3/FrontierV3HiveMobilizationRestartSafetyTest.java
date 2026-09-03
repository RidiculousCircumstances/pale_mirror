package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveDoctrine;
import io.farfrontier.palemirror.frontier.v3.model.HiveDoctrineState;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.HiveTerritoryKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjective;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveStatus;
import io.farfrontier.palemirror.frontier.v3.model.StrategicPlanState;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskRequirement;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskStatus;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskTransition;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.HiveMobilizationProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3HiveMobilizationRestartSafetyTest {
    @Test
    void restartQuarantinesOnlyTheExactInFlightCocoonRelease(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:hive-mobilization-restart");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 8128L);
        FrontierWorldState releasing = releasingState(base.initialState());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, releasing,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(configuration,
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);

        assertEquals(1, FrontierV3HiveMobilizationRestartSafety.quarantineReleasingMobilizations(runtime));
        assertEquals(0, FrontierV3HiveMobilizationRestartSafety.quarantineReleasingMobilizations(runtime));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
        HiveMobilization mobilization = after.hiveColony().mobilizations().values().iterator().next();
        assertEquals(HiveMobilizationStatus.CONFLICT, mobilization.status());
        assertEquals(Optional.of(HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART), mobilization.conflictReason());
        runtime.shutdown();
    }

    private static FrontierWorldState releasingState(FrontierWorldState state) {
        Settlement settlement = state.bootstrap().settlements().getFirst();
        FrontierWorldState initial = state;
        Bioform scout = initial.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .filter(bioform -> initial.hiveColony().bioformLifecycles().get(bioform.id()).phase().permitsAmbientBody()).findFirst().orElseThrow();
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        state = state.withInfection(cell, new FixedRatio(FixedScalar.ONE));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-mobilization-restart"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-mobilization-restart"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        StrategicPlanState plans = StrategicPlanState.empty()
                .withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting)))
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(java.util.Map.of(cell,
                        new HiveTerritoryKnowledge.Belief(cell, new FixedRatio(FixedScalar.ONE), scout.id(), settlement.anchor(), 100L))))
                .withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L))
                .addObjective(objective).addTask(task);
        state = state.withStrategicPlans(plans);
        HiveMobilization mobilization = HiveMobilizationProcess.forSettlementAssault(state, task, sighting, 200L).orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = HiveMobilizationProcess.reduceStarted(state, hive, new io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStarted(mobilization));
        return HiveMobilizationProcess.reduceReleaseStarted(state, hive,
                new io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationReleaseStarted(mobilization.id()));
    }
}
