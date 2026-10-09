package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.HumanHealthProcess;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.MedicalTreatmentProcess;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalEvacuationOperationTest {
    @Test void exactLocalPatientTeamSupplyAndAssignmentSurviveSnapshot() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:medical-owner"), 41L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        HumanPopulation initialPopulation = state.humanPopulation();
        SubjectId medic = settlement.residents().stream().map(Resident::id)
                .filter(id -> initialPopulation.resident(id).capability(HumanCapability.MEDICINE) >= 50).findFirst().orElseThrow();
        SubjectId patient = settlement.residents().stream().map(Resident::id).filter(id -> !id.equals(medic)).findFirst().orElseThrow();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId supply = new SubjectId("item:medical-owner-remedy");
        ExactInventory inventory = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(supply, settlement.id(), MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY, 1,
                        new InventoryCustody.ContainerSlot(depot, 1)));
        state = state.withInventory(inventory).withHumanPopulation(state.humanPopulation().transitionHealth(patient, ResidentHealthStatus.EXPOSED, 100L)
                .transitionHealth(patient, ResidentHealthStatus.INFECTED, 200L));
        SubjectId operationId = new SubjectId("medical:owner-test");
        SubjectId infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY).map(SettlementStructure::id).findFirst().orElseThrow();
        MedicalEvacuationOperation operation = new MedicalEvacuationOperation(operationId, settlement.id(), patient, infirmary,
                MedicalEvacuationTeam.forOperation(operationId, settlement.id(), List.of(medic)), supply,
                new PhysicalIntentId("intent:medical-owner-test-consume"), MedicalEvacuationStatus.PREPARED, -1L);
        FrontierWorldState admitted = MedicalTreatmentProcess.reduceStarted(state, settlement.id(),
                new MedicalTreatmentStarted(operation, MedicalExecutionAuthority.admission(state, operation)));

        assertEquals(HumanAssignmentKind.MEDICAL_EVACUATION, HumanAssignmentProjection.compile(admitted).assignment(medic).kind());
        assertEquals("Medical worker", ResidentPresentation.from(admitted, medic).role());
        assertEquals("Patient", ResidentPresentation.from(admitted, patient).role());
        assertEquals("Receive medical care", ResidentPresentation.from(admitted, patient).task());
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(admitted));
        assertEquals(operation, restored.humanPopulation().medicalOperations().get(operationId));
        assertEquals(HumanAssignmentKind.MEDICAL_EVACUATION, HumanAssignmentProjection.compile(restored).assignment(medic).kind());
        assertEquals(MedicalExecutionAuthority.current(admitted, operation), MedicalExecutionAuthority.current(restored, operation));
        assertThrows(IllegalArgumentException.class, () -> admitted.withChanges(FrontierWorldStateUpdate.begin()
                .actorExecutions(MedicalExecutionAuthority.retired(admitted, operation))),
                "an active care owner cannot lose either patient or medic execution");
        var group = MedicalExecutionAuthority.current(admitted, operation);
        assertThrows(IllegalArgumentException.class, () -> new MedicalTreatmentStarted(operation,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup(List.of(group.members().getFirst()))),
                "patient and medical team must enter the protocol together");
        var entryState = state;
        var oversizedTeam = settlement.residents().stream().map(Resident::id).filter(id -> !id.equals(patient)).limit(3).toList();
        var oversized = new MedicalEvacuationOperation(operationId, settlement.id(), patient, infirmary,
                MedicalEvacuationTeam.forOperation(operationId, settlement.id(), oversizedTeam), supply,
                operation.consumptionIntentId(), MedicalEvacuationStatus.PREPARED, -1L);
        var capacityFailure = assertThrows(IllegalArgumentException.class, () -> MedicalTreatmentProcess.reduceStarted(
                entryState, settlement.id(), new MedicalTreatmentStarted(oversized, MedicalExecutionAuthority.admission(entryState, oversized))),
                "a three-member evacuation team cannot claim two physical treatment stations");
        assertTrue(capacityFailure.getMessage().contains("treatment capacity"), capacityFailure.getMessage());
    }

    @Test void patientCannotAlsoBeTheirOwnMedicalTeam() {
        SubjectId operation = new SubjectId("medical:invalid-team");
        SubjectId resident = new SubjectId("resident:1-1");
        assertThrows(IllegalArgumentException.class, () -> new MedicalEvacuationOperation(operation, new SubjectId("settlement:1"), resident,
                new SubjectId("structure:1-infirmary"), MedicalEvacuationTeam.forOperation(operation, new SubjectId("settlement:1"), List.of(resident)),
                new SubjectId("item:medical-invalid"), new PhysicalIntentId("intent:medical-invalid-team-consume"), MedicalEvacuationStatus.PREPARED, -1L));
    }

    @Test void admissionCompactsOnlyTheOldestConfirmedReceiptTailAndNeverUnknownCare() {
        FrontierWorldState state = treatmentReadyState();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId patient = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resident.capability(HumanCapability.MEDICINE) < 50)
                .map(ResidentProfile::id).findFirst().orElseThrow();
        SubjectId medic = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resident.capability(HumanCapability.MEDICINE) >= 50)
                .map(ResidentProfile::id).findFirst().orElseThrow();
        List<SubjectId> alternatives = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .map(ResidentProfile::id).filter(id -> !id.equals(patient) && !id.equals(medic)).limit(2).toList();
        SubjectId nextPatient = alternatives.getFirst(); SubjectId nextMedic = alternatives.getLast();
        SubjectId infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY).map(SettlementStructure::id).findFirst().orElseThrow();
        LinkedHashMap<SubjectId, MedicalEvacuationOperation> completed = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < MedicalEvacuationOperation.RETAINED_COMPLETED + 1; ordinal++) {
            MedicalEvacuationOperation operation = operation(settlement.id(), patient, medic, infirmary, "completed-" + ordinal,
                    MedicalEvacuationStatus.COMPLETED, ordinal);
            completed.put(operation.id(), operation);
        }
        MedicalEvacuationOperation unknown = operation(settlement.id(), patient, medic, infirmary, "unknown", MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART, -1L);
        completed.put(unknown.id(), unknown);
        LinkedHashMap<SubjectId, ResidentProfile> residents = new LinkedHashMap<>(state.humanPopulation().residents());
        var learned = new java.util.EnumMap<>(residents.get(nextMedic).capabilities());
        learned.put(HumanCapability.MEDICINE, 100);
        residents.put(nextMedic, residents.get(nextMedic).withCapabilities(learned));
        HumanPopulation retained = new HumanPopulation(state.humanPopulation().households(), residents, state.humanPopulation().birthJobs(),
                state.humanPopulation().health(), state.humanPopulation().quarantines(), state.humanPopulation().migrations(), state.humanPopulation().provisions(),
                state.humanPopulation().nutrition(), completed).startMedicalOperation(operation(settlement.id(), nextPatient, nextMedic, infirmary, "new", MedicalEvacuationStatus.PREPARED, -1L));

        assertEquals(MedicalEvacuationOperation.RETAINED_COMPLETED, retained.medicalOperations().values().stream().filter(MedicalEvacuationOperation::compactable).count());
        assertTrue(!retained.medicalOperations().containsKey(new SubjectId("medical:completed-0")));
        assertTrue(retained.medicalOperations().containsKey(new SubjectId("medical:completed-1")));
        assertEquals(unknown, retained.medicalOperations().get(unknown.id()));
        assertTrue(retained.medicalOperations().containsKey(new SubjectId("medical:new")));
    }

    @Test void physicalExactSupplyReceiptPrecedesExactPatientRecoveryAndUnknownRetainsTheTeam() {
        FrontierWorldState state = treatmentReadyState();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        var planned = MedicalTreatmentProcess.planStart(state, settlement.id(), 1);
        MedicalTreatmentStarted started = planned.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = planned.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(started, io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode(started.type(), io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        byte[] encoded = FrontierWorldRuntimeDefinition.payloadCodecs().encode(started);
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                java.util.Arrays.copyOf(encoded, encoded.length - 1)), "partial care execution declaration cannot be hydrated");
        state = MedicalTreatmentProcess.reduceStarted(state, settlement.id(), started);
        state = PhysicalIntentLifecycleFixture.prepare(state, settlement.id(), prepared.intent());
        state = admitTreatmentScene(state, started.operation()).state();
        var running = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        var runningEvents = MedicalTreatmentProcess.planTransition(state, prepared.intent(), running, 300L);
        state = PhysicalIntentLifecycleFixture.transition(state, settlement.id(), prepared.intent(),
                PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        MedicalTreatmentTransition treating = runningEvents.stream().map(event -> event.payload()).filter(MedicalTreatmentTransition.class::isInstance)
                .map(MedicalTreatmentTransition.class::cast).findFirst().orElseThrow();
        var future = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup(treating.executions().members().stream()
                .map(id -> new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(
                        id.actorId(), id.activityKind(), id.activityOwnerId(), id.generation() + 1L)).toList());
        FrontierWorldState beforeTreating = state;
        assertThrows(IllegalArgumentException.class, () -> MedicalTreatmentProcess.reduceTransition(beforeTreating, settlement.id(), 300L,
                new MedicalTreatmentTransition(treating.operationId(), treating.status(), future)),
                "matching patient and physical intent cannot authorize a different execution generation");
        assertEquals(treating, FrontierWorldRuntimeDefinition.payloadCodecs().decode(treating.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(treating)));
        state = MedicalTreatmentProcess.reduceTransition(state, settlement.id(), 300L, treating);

        ExactItemConsumedObservation receipt = new ExactItemConsumedObservation(new PhysicalObservationId("observation:medical-treatment"), prepared.intent().id(),
                started.operation().supplyItemId(), 1, 0);
        var confirmed = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt));
        var confirmedEvents = MedicalTreatmentProcess.planTransition(state, prepared.intent(), confirmed, 400L);
        state = PhysicalIntentLifecycleFixture.transition(state, settlement.id(), prepared.intent(),
                PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt));
        ResidentHealthTransition recovering = confirmedEvents.stream().map(event -> event.payload()).filter(ResidentHealthTransition.class::isInstance)
                .map(ResidentHealthTransition.class::cast).findFirst().orElseThrow();
        state = HumanHealthProcess.reduceResidentTransition(state, settlement.id(), 400L, recovering);
        MedicalTreatmentTransition completed = confirmedEvents.stream().map(event -> event.payload()).filter(MedicalTreatmentTransition.class::isInstance)
                .map(MedicalTreatmentTransition.class::cast).findFirst().orElseThrow();
        state = MedicalTreatmentProcess.reduceTransition(state, settlement.id(), 400L, completed);
        assertEquals(ResidentHealthStatus.RECOVERING, state.humanPopulation().health(started.operation().patientId()).status());
        assertEquals(MedicalEvacuationStatus.COMPLETED, state.humanPopulation().medicalOperations().get(started.operation().id()).status());
        FrontierWorldState completedState = state;
        assertTrue(MedicalExecutionAuthority.participants(started.operation()).stream().allMatch(actor ->
                completedState.actorExecutions().actors().get(actor).current().isEmpty()),
                "terminal care releases the entire declared group without waiting for body removal");
        assertTrue(!state.inventory().items().containsKey(started.operation().supplyItemId()));

        FrontierWorldState unknownState = treatmentReadyState();
        var unknownPlan = MedicalTreatmentProcess.planStart(unknownState, settlement.id(), 2);
        MedicalTreatmentStarted unknownStart = unknownPlan.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared unknownPrepared = unknownPlan.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        unknownState = PhysicalIntentLifecycleFixture.prepare(
                MedicalTreatmentProcess.reduceStarted(unknownState, settlement.id(), unknownStart), settlement.id(), unknownPrepared.intent());
        unknownState = admitTreatmentScene(unknownState, unknownStart.operation()).state();
        MedicalTreatmentTransition unknownRunning = MedicalTreatmentProcess.planTransition(unknownState, unknownPrepared.intent(),
                new PhysicalIntentTransition(unknownPrepared.intent().id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty()), 300L).stream()
                .map(event -> event.payload()).filter(MedicalTreatmentTransition.class::isInstance).map(MedicalTreatmentTransition.class::cast).findFirst().orElseThrow();
        unknownState = PhysicalIntentLifecycleFixture.transition(unknownState, settlement.id(), unknownPrepared.intent(),
                PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        unknownState = MedicalTreatmentProcess.reduceTransition(unknownState, settlement.id(), 300L, unknownRunning);
        MedicalTreatmentTransition unknown = MedicalTreatmentProcess.planTransition(unknownState, unknownPrepared.intent(),
                new PhysicalIntentTransition(unknownPrepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()), 400L).stream()
                .map(event -> event.payload()).filter(MedicalTreatmentTransition.class::isInstance).map(MedicalTreatmentTransition.class::cast).findFirst().orElseThrow();
        unknownState = PhysicalIntentLifecycleFixture.transition(unknownState, settlement.id(), unknownPrepared.intent(),
                PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        unknownState = MedicalTreatmentProcess.reduceTransition(unknownState, settlement.id(), 400L, unknown);
        assertEquals(MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART, unknownState.humanPopulation().medicalOperations().get(unknownStart.operation().id()).status());
        assertEquals(HumanAssignmentKind.MEDICAL_EVACUATION, HumanAssignmentProjection.compile(unknownState)
                .assignment(unknownStart.operation().team().leaderId()).kind());
    }

    @Test void activeMedicalOperationExclusivelyOwnsItsPatientHealthUntilItsReceiptResolves() {
        FrontierWorldState state = treatmentReadyState();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = MedicalTreatmentProcess.planStart(state, settlement.id(), 1);
        MedicalTreatmentStarted started = planned.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = planned.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = MedicalTreatmentProcess.reduceStarted(state, settlement.id(), started).preparePhysicalIntent(prepared.intent());

        long ordinaryRecoveryTick = 200L + state.bootstrap().ruleset().cadence().humanHealthProgressionDelay();
        assertFalse(HumanHealthProcess.assess(state, settlement, ordinaryRecoveryTick).stream().map(event -> event.payload())
                .filter(ResidentHealthTransition.class::isInstance).map(ResidentHealthTransition.class::cast)
                .anyMatch(transition -> transition.residentId().equals(started.operation().patientId())));
    }

    @Test void hotAmbientTreatmentMembersRemainCandidatesForTheExplicitSceneHandoff() {
        FrontierWorldState state = treatmentReadyState();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = MedicalTreatmentProcess.planStart(state, settlement.id(), 1);
        MedicalTreatmentStarted started = planned.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = planned.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = MedicalTreatmentProcess.reduceStarted(state, settlement.id(), started).preparePhysicalIntent(prepared.intent());
        FrontierMedicalTreatmentSceneSupport.Candidate candidate = FrontierMedicalTreatmentSceneSupport.candidates(state).stream().findFirst().orElseThrow();

        LinkedHashMap<SubjectId, AmbientActorLease> ambient = new LinkedHashMap<>();
        candidate.memberPositions().forEach((actor, position) -> ambient.put(actor, new AmbientActorLease(actor, FrontierTestPositions.bodyAboveSupport(position),
                new SimInstant(300L), 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, FrontierTestPositions.bodyAboveSupport(position))));
        FrontierWorldState hotAmbient = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(ambient));

        assertFalse(FrontierSceneAdmission.available(hotAmbient, candidate.memberPositions().keySet()));
        assertEquals(started.operation().id(), FrontierMedicalTreatmentSceneSupport.candidates(hotAmbient).stream().findFirst().orElseThrow().operationId());
        var population = FrontierWorldProcessCatalog.descriptors().stream()
                .filter(descriptor -> descriptor.id().equals("population")).findFirst().orElseThrow();
        assertTrue(population.commandPayloadTypes().contains("frontier.medical_treatment_scene_lease_prepared"));
        assertTrue(population.commandPayloadTypes().contains("frontier.medical_treatment_scene_lease_handoff"));
    }

    @Test void settlementHealthReviewAdmitsOneExactTreatmentButRejectsConsumptionOutsideHotInfirmaryScene() {
        WorldId world = new WorldId("frontier:medical-scheduled");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 42L);
        FrontierWorldState active = treatmentReadyState(world);
        SubjectId settlement = active.bootstrap().settlements().getFirst().id();
        var scheduledReview = StrategicObjectiveProcess.review(settlement, 1, 300L);
        var review = StrategicObjectiveProcess.plan(active, scheduledReview);
        assertTrue(FrontierWorldProcessCatalog.planScheduled(FrontierWorldRuntimeDefinition.processRegistry(), active, scheduledReview).stream()
                .map(event -> event.payload()).anyMatch(PhysicalIntentPrepared.class::isInstance));
        MedicalTreatmentStarted startedEvent = review.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        var preparedEvent = MedicalTreatmentProcess.planStart(active, settlement, 1).stream().map(event -> event.payload())
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        FrontierWorldState preparedState = MedicalTreatmentProcess.reduceStarted(active, settlement, startedEvent)
                .preparePhysicalIntent(preparedEvent.intent());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, preparedState,
                new SimInstant(300L), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration);
        FrontierWorldState started = state(engine);
        MedicalEvacuationOperation operation = started.humanPopulation().medicalOperations().values().stream().findFirst().orElseThrow();
        assertEquals(MedicalEvacuationStatus.PREPARED, operation.status());
        CommandResult result = engine.submit(command(world, engine, "command:medical-running",
                new PhysicalIntentTransition(operation.consumptionIntentId(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty())));
        assertInstanceOf(CommandResult.Rejected.class, result);
        assertEquals(MedicalEvacuationStatus.PREPARED, state(engine).humanPopulation().medicalOperations().get(operation.id()).status());
    }

    @Test void exactTreatmentMayCompleteOnlyAfterItsOwnHotSceneAndUnknownReceiptReconcilesWithoutReplay() {
        WorldId world = new WorldId("frontier:medical-hot");
        FrontierWorldState state = treatmentReadyState(world);
        Settlement settlement = state.bootstrap().settlements().getFirst();
        var planned = MedicalTreatmentProcess.planStart(state, settlement.id(), 1);
        MedicalTreatmentStarted started = planned.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = planned.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = PhysicalIntentLifecycleFixture.prepare(MedicalTreatmentProcess.reduceStarted(state, settlement.id(), started),
                settlement.id(), prepared.intent());
        FrontierMedicalTreatmentSceneSupport.Candidate candidate = FrontierMedicalTreatmentSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:medical-hot");
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList();
        SceneLease lease = SceneLease.forCause(leaseId, world, new MedicalTreatmentSceneCause(started.operation().id()), candidate.infirmaryAnchor(),
                new SimInstant(300L), 1L, SceneLeaseStatus.PREPARED, members, java.util.Set.of(), java.util.Optional.empty());
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease);
        var approaching = state;
        assertThrows(IllegalArgumentException.class, () -> approaching.transitionSceneLease(leaseId, SceneLeaseStatus.HOT),
                "presence near an ingress node is not independent arrival at the declared clinical station");
        var stations = FrontierMedicalTreatmentSceneSupport.treatmentStations(state, started.operation());
        var infirmary = FrontierWorldStateSupport.structure(settlement, started.operation().infirmaryId());
        assertEquals(SettlementInfirmaryTreatmentPort.forInfirmary(infirmary).patientSurface(), stations.get(started.operation().patientId()));
        for (var station : stations.entrySet()) state = ModeledActorBodyFacts.inspected(state, station.getKey(), station.getValue().standingBody());
        state = state.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertTrue(FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, prepared.intent()));

        state = PhysicalIntentLifecycleFixture.transition(state, settlement.id(), prepared.intent(),
                PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        assertEquals(DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED,
                state.physicalIntents().get(prepared.intent().id()).diagnostic().orElseThrow().reason());
        MedicalTreatmentTransition unknown = MedicalTreatmentProcess.planTransition(state, prepared.intent(),
                new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()), 400L).stream()
                .map(event -> event.payload()).filter(MedicalTreatmentTransition.class::isInstance).map(MedicalTreatmentTransition.class::cast).findFirst().orElseThrow();
        state = MedicalTreatmentProcess.reduceTransition(state, settlement.id(), 400L, unknown);

        var unknownIntent = state.physicalIntents().get(prepared.intent().id());
        assertFalse(FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, unknownIntent),
                "ambiguous treatment cannot consume a second supply");
        assertTrue(FrontierMedicalTreatmentSceneSupport.permitsConsumptionReceipt(state, unknownIntent),
                "its exact late receipt may resolve the retained ambiguity");
        var ambiguous = state;
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(ambiguous,
                settlement.id(), unknownIntent, PhysicalIntentStatus.RUNNING, java.util.Optional.empty()),
                "recovery must not replay physical consumption");
        var withoutHotAuthority = state.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        assertTrue(FrontierMedicalTreatmentSceneSupport.permitsConsumptionReceipt(withoutHotAuthority, unknownIntent),
                "settlement of a possibly applied effect is independent of live presentation/actuation authority");
        var codec = new FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        assertEquals(ambiguous, state, "recovery must retain the exact ambiguity, team, supply and fence");
        ExactItemConsumedObservation receipt = new ExactItemConsumedObservation(new PhysicalObservationId("observation:medical-hot"), prepared.intent().id(),
                started.operation().supplyItemId(), 1, 0);
        var admittedReceipt = plannedMedicalTransition(state,
                new PhysicalIntentTransition(unknownIntent.id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt)));
        var settledAfterDrain = replayMedicalTransition(withoutHotAuthority, settlement.id(), admittedReceipt);
        assertEquals(PhysicalIntentStatus.CONFIRMED, settledAfterDrain.physicalIntents().get(unknownIntent.id()).status(),
                "a decoded exact receipt settles the retained effect after presentation has drained");
        assertEquals(withoutHotAuthority.actorLocations(), settledAfterDrain.actorLocations(), "receipt settlement has no pose authority");
        assertEquals(withoutHotAuthority.actorExecutions(), settledAfterDrain.actorExecutions(), "receipt settlement cannot recreate an execution");
        assertEquals(withoutHotAuthority.sceneLeases(), settledAfterDrain.sceneLeases(), "receipt settlement cannot reopen presentation");
        assertThrows(IllegalArgumentException.class, () -> replayMedicalTransition(ambiguous, settlement.id(),
                new PhysicalIntentTransition(unknownIntent.id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty())),
                "decoded transitions cannot replay ambiguous consumption");
        var replayedConfirmation = replayMedicalTransition(state, settlement.id(),
                admittedReceipt);
        state = PhysicalIntentLifecycleFixture.transition(state, settlement.id(), prepared.intent(),
                PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt));
        assertEquals(state, replayedConfirmation, "valid live and decoded-event receipt reduction must agree");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> completion = MedicalTreatmentProcess.planTransition(state, prepared.intent(),
                new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt)), 500L);
        state = HumanHealthProcess.reduceResidentTransition(state, settlement.id(), 500L, completion.stream().map(event -> event.payload())
                .filter(ResidentHealthTransition.class::isInstance).map(ResidentHealthTransition.class::cast).findFirst().orElseThrow());
        state = MedicalTreatmentProcess.reduceTransition(state, settlement.id(), 500L, completion.stream().map(event -> event.payload())
                .filter(MedicalTreatmentTransition.class::isInstance).map(MedicalTreatmentTransition.class::cast).findFirst().orElseThrow());
        assertEquals(MedicalEvacuationStatus.COMPLETED, state.humanPopulation().medicalOperations().get(started.operation().id()).status());
        assertTrue(!state.inventory().items().containsKey(started.operation().supplyItemId()));
    }

    @Test void completedTreatmentCanRecoverItsBodiesForReleaseWithoutRepeatingTreatment() {
        TreatmentSceneFixture fixture = hotTreatmentFixture(new WorldId("frontier:medical-completed-recovery"));
        // Terminal owner-state input; actual treatment receipts are verified in the preceding test.
        var completed = fixture.state().withChanges(FrontierWorldStateUpdate.begin().humanPopulation(fixture.state().humanPopulation()
                .transitionMedicalOperation(fixture.operation().id(), MedicalEvacuationStatus.COMPLETED, 500L))
                .actorExecutions(MedicalExecutionAuthority.retired(fixture.state(), fixture.operation())));
        var leaseId = fixture.lease().id();
        var unknownScene = completed.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        unknownScene = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknownScene));
        assertEquals(SceneLeaseStatus.DRAINING, FrontierSceneBehaviors.recoveredStatus(unknownScene, unknownScene.sceneLeases().get(leaseId)));
        var draining = unknownScene.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        var captured = fixture.lease().members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                draining.actorLocations().get(member.actorId()).body(), draining.actorLocations().get(member.actorId()).condition().health())).toList();
        var released = draining.releaseSceneLease(leaseId, captured);
        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(leaseId).status());
        assertEquals(completed.inventory(), released.inventory());
        assertEquals(completed.humanPopulation(), released.humanPopulation());
    }

    @Test void participantDeathBlocksCareBeforeTheDeathFactAndLeavesPreEffectSupplyUntouched() {
        assertParticipantDeathSettlement(true);
        assertParticipantDeathSettlement(false);
    }

    private void assertParticipantDeathSettlement(boolean retainScope) {
        TreatmentSceneFixture fixture = hotTreatmentFixture(new WorldId("frontier:medical-death"));
        var initial = fixture.state();
        if (!retainScope) {
            initial = initial.transitionSceneLease(fixture.lease().id(), SceneLeaseStatus.DRAINING);
            var beforeRelease = initial;
            initial = initial.releaseSceneLease(fixture.lease().id(), fixture.lease().members().stream().map(member ->
                    new SceneMemberPosition(member.actorId(), beforeRelease.actorLocations().get(member.actorId()).body(),
                            beforeRelease.actorLocations().get(member.actorId()).condition().health())).toList());
        }
        var base = FrontierWorldRuntimeDefinition.configuration(fixture.world(), 42L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(fixture.world(), initial, new SimInstant(300L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter()));
        SceneMember dead = fixture.lease().members().getFirst();
        CommandResult result = engine.submit(command(fixture.world(), engine, "command:medical-participant-death",
                ModeledActorBodyFacts.death(new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()), dead.actorId(),
                        FrontierTestPositions.bodyCellOf(fixture.state().actorLocations().get(dead.actorId())), "test:medical-death")));
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);

        FrontierWorldState afterDeath = state(engine);
        MedicalEvacuationOperation operation = afterDeath.humanPopulation().medicalOperations().get(fixture.operation().id());
        assertEquals(MedicalEvacuationStatus.BLOCKED, operation.status());
        assertEquals(ActorLifeStatus.DEAD, afterDeath.actorLocations().get(dead.actorId()).condition().status());
        assertEquals(retainScope ? SceneLeaseStatus.DRAINING : SceneLeaseStatus.CLOSED, afterDeath.sceneLeases().get(fixture.lease().id()).status());
        assertTrue(MedicalExecutionAuthority.participants(operation).stream().noneMatch(actor -> afterDeath.actorExecutions()
                .current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEDICAL_TREATMENT).containsKey(actor)));
        assertEquals(PhysicalIntentStatus.PREPARED, afterDeath.physicalIntents().get(operation.consumptionIntentId()).status());
        assertTrue(operation.compactable());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(fixture.world(), engine, "command:medical-blocked-running",
                new PhysicalIntentTransition(operation.consumptionIntentId(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty()))));

        List<SceneMemberPosition> survivors = fixture.lease().members().stream().filter(member -> !member.equals(dead))
                .map(member -> new SceneMemberPosition(member.actorId(), FrontierTestPositions.bodyCellOf(afterDeath.actorLocations().get(member.actorId())))).toList();
        if (retainScope) assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(fixture.world(), engine, "command:medical-death-release",
                new SceneLeaseReleased(fixture.lease().id(), survivors))));
        FrontierWorldState released = state(engine);
        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(fixture.lease().id()).status());
        assertEquals(MedicalEvacuationStatus.BLOCKED, released.humanPopulation().medicalOperations().get(operation.id()).status());
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
    }

    @Test void preEffectInfirmarySceneMayDrainAndLaterBeReadmittedWithoutReplacingAnyone() {
        TreatmentSceneFixture fixture = hotTreatmentFixture(new WorldId("frontier:medical-drain"));
        List<SceneMemberPosition> observed = fixture.lease().members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                FrontierTestPositions.bodyCellOf(fixture.state().actorLocations().get(member.actorId())))).toList();
        FrontierWorldState released = fixture.state().transitionSceneLease(fixture.lease().id(), SceneLeaseStatus.DRAINING)
                .releaseSceneLease(fixture.lease().id(), observed);

        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(fixture.lease().id()).status());
        assertEquals(MedicalEvacuationStatus.PREPARED, released.humanPopulation().medicalOperations().get(fixture.operation().id()).status());
        FrontierMedicalTreatmentSceneSupport.Candidate candidate = FrontierMedicalTreatmentSceneSupport.candidates(released).stream().findFirst().orElseThrow();
        assertEquals(fixture.operation().id(), candidate.operationId());
        assertEquals(fixture.lease().members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()),
                candidate.memberPositions().keySet());
    }

    private static PhysicalIntentTransition plannedMedicalTransition(FrontierWorldState state, PhysicalIntentTransition transition) {
        var id = new CommandId("command:medical-replay-admission");
        var command = new FrontierCommand(1, id, state.bootstrap().worldId(), Revision.ZERO, new SimInstant(500),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), transition);
        var plan = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 42L).commandPlanner().plan(state, command);
        var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class, plan);
        return accepted.events().stream().map(event -> event.payload()).filter(PhysicalIntentTransition.class::isInstance)
                .map(PhysicalIntentTransition.class::cast).findFirst().orElseThrow();
    }

    private static FrontierWorldState replayMedicalTransition(FrontierWorldState state, SubjectId subject,
                                                              PhysicalIntentTransition transition) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var decoded = codecs.decode(transition.type(), codecs.encode(transition));
        var command = new CommandId("command:medical-replay-test");
        var event = new FrontierEvent(1, new EventId("event:medical-replay-test"),
                new TransactionId("transaction:medical-replay-test"), state.bootstrap().worldId(),
                new Revision(1), new SimInstant(500), subject, CauseChain.root(command), decoded);
        return FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 42L).reducer().apply(state, event);
    }

    private static FrontierWorldState treatmentReadyState() {
        return treatmentReadyState(new WorldId("frontier:medical-lifecycle"));
    }

    private static FrontierWorldState treatmentReadyState(WorldId world) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 42L));
        Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId supply = new SubjectId("item:medical-lifecycle-remedy");
        SubjectId patient = settlement.residents().stream().map(Resident::id).filter(id -> state.humanPopulation().resident(id).capability(HumanCapability.MEDICINE) < 50).findFirst().orElseThrow();
        ExactInventory inventory = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(supply, settlement.id(), MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY, 1, new InventoryCustody.ContainerSlot(depot, 1)));
        return state.withInventory(inventory).withHumanPopulation(state.humanPopulation().transitionHealth(patient, ResidentHealthStatus.EXPOSED, 100L)
                .transitionHealth(patient, ResidentHealthStatus.INFECTED, 200L));
    }

    private static TreatmentSceneFixture hotTreatmentFixture(WorldId world) {
        FrontierWorldState state = treatmentReadyState(world);
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = MedicalTreatmentProcess.planStart(state, settlement.id(), 1);
        MedicalTreatmentStarted started = planned.stream().map(event -> event.payload()).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = planned.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = PhysicalIntentLifecycleFixture.prepare(MedicalTreatmentProcess.reduceStarted(state, settlement.id(), started),
                settlement.id(), prepared.intent());
        return admitTreatmentScene(state, started.operation());
    }

    private static TreatmentSceneFixture admitTreatmentScene(FrontierWorldState state, MedicalEvacuationOperation operation) {
        WorldId world = state.bootstrap().worldId();
        FrontierMedicalTreatmentSceneSupport.Candidate candidate = FrontierMedicalTreatmentSceneSupport.candidates(state).stream()
                .filter(value -> value.operationId().equals(operation.id())).findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:medical-" + world.value().substring("frontier:".length()));
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList();
        SceneLease lease = SceneLease.forCause(leaseId, world, new MedicalTreatmentSceneCause(operation.id()), candidate.infirmaryAnchor(),
                new SimInstant(300L), 1L, SceneLeaseStatus.PREPARED, members, java.util.Set.of(), java.util.Optional.empty());
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease);
        for (var station : FrontierMedicalTreatmentSceneSupport.treatmentStations(state, operation).entrySet())
            state = ModeledActorBodyFacts.inspected(state, station.getKey(), station.getValue().standingBody());
        return new TreatmentSceneFixture(world, state.transitionSceneLease(leaseId, SceneLeaseStatus.HOT), operation, lease);
    }

    private record TreatmentSceneFixture(WorldId world, FrontierWorldState state, MedicalEvacuationOperation operation, SceneLease lease) { }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static FrontierCommand command(WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
                                           String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandId command = new CommandId(id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload);
    }

    private static MedicalEvacuationOperation operation(SubjectId settlement, SubjectId patient, SubjectId medic, SubjectId infirmary,
                                                         String suffix, MedicalEvacuationStatus status, long terminalAtTick) {
        SubjectId id = new SubjectId("medical:" + suffix);
        return new MedicalEvacuationOperation(id, settlement, patient, infirmary, MedicalEvacuationTeam.forOperation(id, settlement, List.of(medic)),
                new SubjectId("item:medical-" + suffix), new PhysicalIntentId("intent:medical-" + suffix + "-consume"), status, terminalAtTick);
    }
}
