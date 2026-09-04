package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.SettlementServiceWorkProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @Test
    void hotWorkerAdvanceMovesOnlyOneRetainedCursorAndLeaseCheckpoint() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work-hot"), 214L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId item = new SubjectId("item:service-work-hot-reagent");
        FrontierWorldState source = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        SettlementServiceWorkStarted started = SettlementServiceWorkProcess.planDecontamination(source, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState active = source.withStrategicPlans(source.strategicPlans().transitionTask(started.taskId(), StrategicTaskStatus.ACTIVE));
        FrontierWorldState admitted = SettlementServiceWorkProcess.reduceStarted(active, settlement.id(), started);
        SettlementServiceWork work = admitted.serviceWorks().get(started.work().id());
        assertTrue(work.inputTraversal().linearCorridorSurfaces().size() > 1, "fixture must exercise a real retained source approach");
        SurfaceAnchor start = work.inputTraversal().linearCorridorSurfaces().getFirst();
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:service-work-hot"), bootstrap.worldId(), new SettlementServiceWorkSceneCause(work.id()),
                start.support(), new SimInstant(1_001L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(work.workerId(), SceneLease.deterministicEntityId(bootstrap.worldId(), work.workerId()))),
                java.util.Map.of(work.workerId(), start.standingBody()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = admitted.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        SurfaceAnchor next = work.inputTraversal().linearCorridorSurfaces().get(1);
        SettlementServiceWorkTraversalAdvanced advance = new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), next.standingBody(), 1);

        FrontierWorldState advanced = SettlementServiceWorkProcess.reduceHotTraversalAdvanced(hot, settlement.id(), advance);

        assertEquals(1, advanced.serviceWorks().get(work.id()).inputTraversalCursor());
        assertEquals(next.standingBody(), advanced.sceneLeases().get(lease.id()).memberPosition(work.workerId()));
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotTraversalAdvanced(hot, settlement.id(),
                new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), next.standingBody(), 2)),
                "an observed arrival may not skip a retained edge");
    }

    @Test
    void medicHeldEndpointConsumesOnlyAfterWorkAndConfirmsTheExactCell() {
        ReadyEndpoint ready = readyEndpoint(215L);
        long prior = ready.state().infection().get(ready.cell()).value().raw();
        FrontierWorldState running = ready.state().transitionPhysicalIntent(ready.intent().id(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        DecontaminationObservation observation = new DecontaminationObservation(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-complete"),
                ready.intent().id(), ready.item(), ready.cell(), prior,
                Math.max(0L, prior - ready.state().bootstrap().ruleset().rates().decontaminationReduction().raw()));

        FrontierWorldState complete = running.transitionPhysicalIntent(ready.intent().id(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(observation));

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED,
                complete.physicalIntents().get(ready.intent().id()).status());
        assertFalse(complete.inventory().items().containsKey(ready.item()));
        assertEquals(SettlementServiceWorkPhase.COMPLETED, complete.serviceWorks().get(ready.work().id()).phase());
        assertEquals(observation.remainingRaw(), complete.infection().get(ready.cell()).value().raw());
    }

    @Test
    void unknownEndpointRetainsTheExactMedicAndRecoversOnlyByObservedPostcondition() {
        ReadyEndpoint ready = readyEndpoint(216L);
        FrontierWorldState unknown = ready.state().transitionPhysicalIntent(ready.intent().id(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown));
        assertEquals(SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART, recovered.serviceWorks().get(ready.work().id()).phase());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                recovered.physicalIntents().get(ready.intent().id()).status());

        SceneLeaseId leaseId = recovered.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isServiceWork)
                .map(SceneLease::id).findFirst().orElseThrow();
        recovered = recovered.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);

        long prior = recovered.infection().get(ready.cell()).value().raw();
        DecontaminationObservation observation = new DecontaminationObservation(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-recovered"),
                ready.intent().id(), ready.item(), ready.cell(), prior,
                Math.max(0L, prior - recovered.bootstrap().ruleset().rates().decontaminationReduction().raw()));
        FrontierWorldState complete = recovered.transitionPhysicalIntent(ready.intent().id(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(observation));

        assertEquals(SettlementServiceWorkPhase.COMPLETED, complete.serviceWorks().get(ready.work().id()).phase());
        assertFalse(complete.inventory().items().containsKey(ready.item()));
    }

    private static ReadyEndpoint readyEndpoint(long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-ready-" + seed), seed);
        Settlement settlement = settlement(bootstrap, "settlement:9"); InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:service-ready-" + seed);
        FrontierWorldState source = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        SettlementServiceWorkStarted started = SettlementServiceWorkProcess.planDecontamination(source, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState admitted = SettlementServiceWorkProcess.reduceStarted(
                source.withStrategicPlans(source.strategicPlans().transitionTask(started.taskId(), StrategicTaskStatus.ACTIVE)), settlement.id(), started);
        SettlementServiceWork work = admitted.serviceWorks().get(started.work().id());
        while (work.inputTraversalCursor() < work.inputTraversal().linearCorridorSurfaces().size() - 1) work = work.withInputTraversalCursor(work.inputTraversalCursor() + 1);
        work = work.withInputIssued();
        while (work.workTraversalCursor() < work.workTraversal().linearCorridorSurfaces().size() - 1) work = work.withWorkTraversalCursor(work.workTraversalCursor() + 1);
        work = work.withPhase(SettlementServiceWorkPhase.EFFECT_READY, 0);
        Map<SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(admitted.serviceWorks()); works.put(work.id(), work);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(admitted.physicalIntents());
        SettlementServiceInputIssueObservation inputReceipt = new SettlementServiceInputIssueObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-input-" + seed), work.inputIssueIntentId(),
                work.id(), work.workerId(), item, work.inputSource());
        intents.put(work.inputIssueIntentId(), intents.get(work.inputIssueIntentId()).withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED,
                Optional.of(inputReceipt.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(admitted.physicalObservations());
        observations.put(inputReceipt.id(), inputReceipt);
        SceneLeaseId leaseId = new SceneLeaseId("lease:service-ready-" + seed);
        SceneLease lease = SceneLease.forCause(leaseId, bootstrap.worldId(), new SettlementServiceWorkSceneCause(work.id()), work.workStation().support(),
                new SimInstant(1_001L), 1L, SceneLeaseStatus.HOT, List.of(new SceneMember(work.workerId(),
                SceneLease.deterministicEntityId(bootstrap.worldId(), leaseId, work.workerId()))),
                Map.of(work.workerId(), work.workStation().standingBody()), java.util.Set.of(), Optional.empty());
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(admitted.sceneLeases()); leases.put(leaseId, lease);
        FrontierWorldState ready = admitted.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(works).physicalIntents(intents).physicalObservations(observations).sceneLeases(leases)
                .inventory(admitted.inventory().moveObservedItem(item, work.inputSource(), new InventoryCustody.Actor(work.workerId()))));
        return new ReadyEndpoint(ready, work, ready.physicalIntents().get(work.endpointIntentId()), item, cell);
    }

    private record ReadyEndpoint(FrontierWorldState state, SettlementServiceWork work,
                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, SubjectId item, InfectionCell cell) { }

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
