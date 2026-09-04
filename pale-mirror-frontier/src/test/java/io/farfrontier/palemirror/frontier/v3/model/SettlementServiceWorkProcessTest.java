package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.SettlementServiceWorkProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Canonical admission regression for the first reusable settlement service-work form. */
class SettlementServiceWorkProcessTest {
    @Test
    void decontaminationAdmissionAtomicallyRetainsMedicStationsInputAndEndpoint() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work"), 211L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId item = new SubjectId("item:service-work-reagent");
        FrontierWorldState state = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));

        List<ProposedEvent> planned = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L));
        StrategicTaskTransition activation = planned.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).findFirst().orElseThrow();
        SettlementServiceWorkStarted started = planned.stream().map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance)
                .map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicTaskStatus.ACTIVE, activation.status());
        assertEquals(started.work().id(), started.inputIssueIntent().causeSubjectId());
        assertEquals(List.of(started.work().id(), started.work().workerId(), item), started.endpointIntent().subjectIds());
        assertFalse(planned.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared),
                "admission retains both boundaries atomically instead of exposing an old direct endpoint");

        FrontierWorldState active = state.withStrategicPlans(state.strategicPlans().transitionTask(activation.taskId(), activation.status()));
        FrontierWorldState admitted = SettlementServiceWorkProcess.reduceStarted(active, settlement.id(), started);
        SettlementServiceWork work = admitted.serviceWorks().get(started.work().id());
        assertEquals(started.work(), work);
        assertEquals(HumanAssignmentKind.SETTLEMENT_SERVICE, HumanAssignmentProjection.compile(admitted).assignment(work.workerId()).kind());
        assertEquals(work.inputIssueIntentId(), admitted.physicalIntents().get(work.inputIssueIntentId()).id());
        assertEquals(work.endpointIntentId(), admitted.physicalIntents().get(work.endpointIntentId()).id());
        SettlementServiceWorkStarted decoded = assertInstanceOf(SettlementServiceWorkStarted.class,
                FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        assertEquals(started, decoded);
        assertEquals(admitted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(admitted)));
    }

    @Test
    void missingExactDepotReagentBlocksInsteadOfCreatingServiceWork() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work-blocked"), 212L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        FrontierWorldState state = taskState(bootstrap, settlement, treatmentCell(bootstrap, settlement));

        List<ProposedEvent> planned = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L));

        assertTrue(planned.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).anyMatch(value -> value.status() == StrategicTaskStatus.BLOCKED));
        assertFalse(planned.stream().anyMatch(event -> event.payload() instanceof SettlementServiceWorkStarted));
    }

    @Test
    void forgedAdmissionCannotReplaceTheExactEndpointPlan() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work-forgery"), 213L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId item = new SubjectId("item:service-work-forgery-reagent");
        FrontierWorldState state = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        SettlementServiceWorkStarted planned = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast)
                .findFirst().orElseThrow();
        FrontierWorldState active = state.withStrategicPlans(state.strategicPlans().transitionTask(planned.taskId(), StrategicTaskStatus.ACTIVE));
        var forgedEndpoint = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent(planned.endpointIntent().id(), planned.endpointIntent().kind(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, planned.endpointIntent().causeSubjectId(),
                planned.endpointIntent().subjectIds(), planned.endpointIntent().origin(), planned.endpointIntent().radiusBlocks(),
                planned.endpointIntent().postcondition());

        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceStarted(active, settlement.id(),
                new SettlementServiceWorkStarted(planned.taskId(), planned.work(), planned.inputIssueIntent(), forgedEndpoint)));
    }

    private static FrontierWorldState taskState(FrontierBootstrap bootstrap, Settlement settlement, InfectionCell cell) {
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap).withInfection(cell, new FixedRatio(new FixedScalar(750_000L)));
        String suffix = settlement.id().value().replace(':', '-');
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:" + suffix), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(cell), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:" + suffix), objective.id(), settlement.id(),
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(cell), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY,
                StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        return initial.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static Settlement settlement(FrontierBootstrap bootstrap, String id) {
        return bootstrap.settlements().stream().filter(value -> value.id().value().equals(id)).findFirst().orElseThrow();
    }

    private static SettlementStructure infirmary(Settlement settlement) {
        return settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
    }

    private static InfectionCell treatmentCell(FrontierBootstrap bootstrap, Settlement settlement) {
        SettlementStructure infirmary = infirmary(settlement);
        for (int radius = 4; radius <= 32; radius += 4) for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                InfectionCell cell = InfectionCell.at(infirmary.anchor().offset(dx, 0, dz));
                try {
                    if (!InfectionTreatmentWorksite.candidates(bootstrap, cell).isEmpty()) return cell;
                } catch (IllegalArgumentException ignored) {
                    // The planner, not the fixture, decides which immutable worksite is usable.
                }
            }
        }
        throw new IllegalStateException("fixture has no treatment cell near its infirmary");
    }
}
