package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierWorldStateTest {
    private static final FixedRatio HALF = new FixedRatio(new FixedScalar(500_000L));

    @Test
    void preBioformProfileSnapshotSchemaFailsBeforeStateHydration() {
        byte[] encoded = new FrontierWorldStateCodec().encode(initial());
        encoded[4] = 103;

        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldStateCodec().decode(encoded));
    }

    @Test
    void initialStateOwnsEveryExactActorAndFunctionalStructure() {
        FrontierWorldState state = initial();

        assertEquals(state.bootstrap().residentCount() + state.bootstrap().bioformCount(), state.actorLocations().size());
        assertEquals(12 * StructureKind.values().length, state.structureConditions().size());
        assertEquals(15, state.inventory().containers().size());
        assertEquals(FrontierRouteNetwork.OWNER, state.inventory().containers().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER).ownerId());
        assertEquals(2 + state.bootstrap().settlements().size() * EngineeringRecoveryTeam.MAX_MEMBERS, state.inventory().items().size());
        assertEquals(state.inventory().containers().keySet(), state.inventory().surfaces().keySet());
        assertTrue(state.productionJobs().isEmpty());
        assertTrue(state.serviceWorks().isEmpty());
        assertTrue(state.operations().isEmpty());
        assertTrue(state.physicalIntents().isEmpty());
        assertTrue(state.structureConditions().values().stream().allMatch(condition -> condition == StructureCondition.INTACT));
        assertEquals(18, state.infection().size());
        for (HiveNest nest : state.bootstrap().hive().seedNests()) {
            InfectionCell centre = InfectionCell.at(nest.anchor());
            assertEquals(new FixedRatio(new FixedScalar(750_000L)), state.infection().get(centre));
            assertEquals(new FixedRatio(new FixedScalar(500_000L)), state.infection().get(new InfectionCell(centre.x() + 1, centre.z())));
            assertEquals(new FixedRatio(new FixedScalar(250_000L)), state.infection().get(new InfectionCell(centre.x() + 1, centre.z() + 1)));
        }
    }

    @Test
    void serviceWorkRetainsExactWorkerIntentCursorAndSnapshotBytes() {
        FrontierWorldState baseline = initial();
        ResidentProfile medic = baseline.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(new SubjectId("settlement:1")))
                .filter(value -> value.profession() == ResidentProfession.MEDICAL_WORKER).findFirst().orElseThrow();
        SubjectId workId = new SubjectId("service:decontamination-1");
        SubjectId facility = new SubjectId("structure:1-infirmary");
        ActorLocation medicLocation = baseline.actorLocations().get(medic.id());
        InfectionCell cell = null; SettlementServiceWorkTraversal.Plan plan = null;
        SettlementStructure infirmary = FrontierWorldStateSupport.structure(FrontierWorldStateSupport.settlement(baseline.bootstrap(), new SubjectId("settlement:1")), facility);
        for (int[] offset : List.of(new int[] { 12, 0 }, new int[] { -12, 0 }, new int[] { 0, 12 }, new int[] { 0, -12 })) {
            InfectionCell candidate = InfectionCell.at(infirmary.anchor().offset(offset[0], 0, offset[1]));
            try { plan = SettlementServiceWorkTraversal.compileDecontamination(baseline.bootstrap(),
                    FrontierWorldStateSupport.settlement(baseline.bootstrap(), new SubjectId("settlement:1")), medicLocation, candidate, workId); cell = candidate; break; }
            catch (IllegalArgumentException unavailable) { /* Try the next finite test fixture candidate. */ }
        }
        if (cell == null || plan == null) throw new AssertionError("test fixture has no bounded service-work corridor");
        InfectionCell selectedCell = cell;
        SettlementServiceWork work = new SettlementServiceWork(workId, new SubjectId("task:service-work-1"), SettlementServiceWorkKind.DECONTAMINATION, new SubjectId("settlement:1"),
                medic.id(), facility, new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 1), plan.inputStation(), plan.workStation(),
                new SubjectId("item:service-reagent"), new SettlementServiceTarget.Infection(selectedCell),
                new PhysicalIntentId("intent:service-input-issue-1"), new PhysicalIntentId("intent:service-decontamination-1"),
                plan.inputTraversal(), 0, plan.workTraversal(), 0, SettlementServiceWorkPhase.PREPARED, 0);
        PhysicalIntent inputIssue = new PhysicalIntent(work.inputIssueIntentId(), PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE,
                PhysicalIntentStatus.PREPARED, work.id(), List.of(work.id(), medic.id(), work.inputItemId()),
                new FixedPosition(FixedScalar.whole(plan.inputStation().x()), FixedScalar.whole(plan.inputStation().y()), FixedScalar.whole(plan.inputStation().z())),
                0, PhysicalPostcondition.SETTLEMENT_SERVICE_INPUT_ISSUED_OBSERVED);
        PhysicalIntent endpoint = new PhysicalIntent(work.endpointIntentId(), PhysicalIntentKind.DECONTAMINATION, PhysicalIntentStatus.PREPARED, work.id(),
                List.of(work.id(), medic.id(), work.inputItemId()), new FixedPosition(FixedScalar.whole(selectedCell.originAtY(0).x()), FixedScalar.whole(0), FixedScalar.whole(selectedCell.originAtY(0).z())),
                0, PhysicalPostcondition.DECONTAMINATION_OBSERVED);
        ExactInventory inventory = baseline.inventory().store(new ExactItemStack(work.inputItemId(), work.settlementId(), "minecraft:glowstone_dust", 1,
                        new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 1)))
                .withSurfaceStatus(new SubjectId("container:1-depot"), ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(new SubjectId("container:1-depot"), ContainerSurfaceStatus.ACTIVE);
        FrontierWorldState state = baseline.withChanges(FrontierWorldStateUpdate.begin().infection(withInfection(baseline.infection(), selectedCell)).inventory(inventory)
                .serviceWorks(Map.of(work.id(), work)).physicalIntents(Map.of(inputIssue.id(), inputIssue, endpoint.id(), endpoint)));

        assertEquals(work, state.serviceWorks().get(work.id()));
        assertEquals(HumanAssignmentKind.SETTLEMENT_SERVICE, HumanAssignmentProjection.compile(state).assignment(medic.id()).kind());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        SettlementServiceWork atSource = work;
        while (atSource.inputTraversalCursor() < atSource.inputTraversal().linearCorridorSurfaces().size() - 1) {
            atSource = atSource.withInputTraversalCursor(atSource.inputTraversalCursor() + 1);
        }
        FrontierWorldState sourceReady = state.withActorBody(medic.id(), atSource.inputStation().standingBody())
                .withChanges(FrontierWorldStateUpdate.begin().serviceWorks(Map.of(atSource.id(), atSource)));
        SettlementServiceInputIssueStateSupport.validateIntent(sourceReady, inputIssue);
        FrontierWorldState sourceRunning = sourceReady.transitionPhysicalIntent(inputIssue.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        SettlementServiceInputIssueObservation issueReceipt = new SettlementServiceInputIssueObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-input-issue-1"), inputIssue.id(), work.id(), medic.id(),
                work.inputItemId(), work.inputSource());
        FrontierWorldState issued = sourceRunning.transitionPhysicalIntent(inputIssue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(issueReceipt));
        assertEquals(new InventoryCustody.Actor(medic.id()), issued.inventory().items().get(work.inputItemId()).custody());
        assertEquals(SettlementServiceWorkPhase.APPROACH_WORK, issued.serviceWorks().get(work.id()).phase());
        FrontierWorldState stolen = sourceReady.withInventory(sourceReady.inventory().moveObservedItem(work.inputItemId(), work.inputSource(),
                new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000001"))));
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceInputIssueStateSupport.validateIntent(stolen, inputIssue));
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:service-work-snapshot"), state.bootstrap().worldId(),
                new SettlementServiceWorkSceneCause(work.id()), medicLocation.supportingSurface().support(), new SimInstant(100L), 1L,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(medic.id(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), medic.id()))),
                Map.of(medic.id(), medicLocation.body()), Set.of(), Optional.empty());
        SceneLease forgedLease = SceneLease.forCause(new SceneLeaseId("lease:service-work-forged-station"), state.bootstrap().worldId(),
                new SettlementServiceWorkSceneCause(work.id()), medicLocation.supportingSurface().support().offset(1, 0, 0), new SimInstant(100L), 1L,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(medic.id(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), medic.id()))),
                Map.of(medic.id(), medicLocation.body()), Set.of(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> state.prepareSceneLease(forgedLease));
        FrontierWorldState withScene = state.prepareSceneLease(lease);
        assertEquals(lease, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(withScene)).sceneLeases().get(lease.id()));
        FrontierWorldState afterDeath = withScene.transitionSceneLease(lease.id(), SceneLeaseStatus.HOT)
                .recordActorDeath(new ActorDied(lease.id(), medic.id(), medicLocation.body(), "test:service-worker-death"), 101L);
        assertEquals(SettlementServiceWorkPhase.BLOCKED, afterDeath.serviceWorks().get(work.id()).phase());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, afterDeath.physicalIntents().get(work.inputIssueIntentId()).status());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, afterDeath.physicalIntents().get(work.endpointIntentId()).status());
        SettlementServiceWork forgedStation = new SettlementServiceWork(work.id(), work.taskId(), work.kind(), work.settlementId(), work.workerId(), work.facilityId(),
                work.inputSource(), medicLocation.supportingSurface(), medicLocation.supportingSurface(), work.inputItemId(), work.target(), work.inputIssueIntentId(), work.endpointIntentId(),
                TraversalTopology.corridor(new TraversalTopologyId("topology:forged-service-input"), 0L, work.id(), TraversalKind.PEDESTRIAN,
                        java.util.Set.of(TraversalCapability.PEDESTRIAN), List.of(medicLocation.supportingSurface())), 0,
                TraversalTopology.corridor(new TraversalTopologyId("topology:forged-service-work"), 0L, work.id(), TraversalKind.PEDESTRIAN,
                        java.util.Set.of(TraversalCapability.PEDESTRIAN), List.of(medicLocation.supportingSurface())), 0, work.phase(), work.completedWorkTicks());
        assertThrows(IllegalArgumentException.class, () -> baseline.withChanges(FrontierWorldStateUpdate.begin()
                .infection(withInfection(baseline.infection(), selectedCell)).inventory(inventory).serviceWorks(Map.of(work.id(), forgedStation))
                .physicalIntents(Map.of(inputIssue.id(), inputIssue, endpoint.id(), endpoint))));
        SettlementServiceWork duplicateWorker = new SettlementServiceWork(new SubjectId("service:decontamination-2"), new SubjectId("task:service-work-2"), SettlementServiceWorkKind.DECONTAMINATION,
                new SubjectId("settlement:1"), medic.id(), facility, work.inputSource(), plan.inputStation(), plan.workStation(), new SubjectId("item:service-reagent-2"),
                new SettlementServiceTarget.Infection(selectedCell), new PhysicalIntentId("intent:service-input-issue-2"), new PhysicalIntentId("intent:service-decontamination-2"),
                SettlementServiceWorkTraversal.compileDecontamination(baseline.bootstrap(), FrontierWorldStateSupport.settlement(baseline.bootstrap(), new SubjectId("settlement:1")), medicLocation, selectedCell,
                        new SubjectId("service:decontamination-2")).inputTraversal(), 0,
                SettlementServiceWorkTraversal.compileDecontamination(baseline.bootstrap(), FrontierWorldStateSupport.settlement(baseline.bootstrap(), new SubjectId("settlement:1")), medicLocation, selectedCell,
                        new SubjectId("service:decontamination-2")).workTraversal(), 0,
                SettlementServiceWorkPhase.PREPARED, 0);
        assertThrows(IllegalArgumentException.class, () -> state.withChanges(FrontierWorldStateUpdate.begin()
                .serviceWorks(Map.of(work.id(), work, duplicateWorker.id(), duplicateWorker))));
    }

    private static Map<InfectionCell, FixedRatio> withInfection(Map<InfectionCell, FixedRatio> current, InfectionCell cell) {
        Map<InfectionCell, FixedRatio> values = new LinkedHashMap<>(current); values.put(cell, HALF); return Map.copyOf(values);
    }

    @Test
    void stateTransitionsRemainBoundedAndSparse() {
        FrontierWorldState state = initial();
        SubjectId resident = new SubjectId("resident:1-1");
        SubjectId structure = new SubjectId("structure:1-hall");
        InfectionCell cell = InfectionCell.at(new BlockPosition(-1, 64, -1));

        FrontierWorldState changed = state.withActorBody(resident, FrontierTestPositions.bodyAboveSupport(new BlockPosition(-10, 64, -10)))
                .withStructureCondition(structure, StructureCondition.DAMAGED)
                .withInfection(cell, HALF);
        assertEquals(new BlockPosition(-10, 64, -10), FrontierTestPositions.supportOf(changed.actorLocations().get(resident)));
        assertEquals(StructureCondition.DAMAGED, changed.structureConditions().get(structure));
        assertEquals(HALF, changed.infection().get(cell));
        assertEquals(new InfectionCell(-1, -1), cell);
        assertEquals(18, changed.withInfection(cell, new FixedRatio(FixedScalar.ZERO)).infection().size());
        assertThrows(IllegalArgumentException.class, () -> state.withActorBody(resident, FrontierTestPositions.bodyAboveSupport(new BlockPosition(512, 64, 0))));
        assertThrows(IllegalArgumentException.class, () -> state.withStructureCondition(new SubjectId("structure:missing"), StructureCondition.DESTROYED));
    }

    @Test
    void canonicalMapIndexesRetainOrdinaryLookupAndImmutabilitySemantics() {
        FrontierWorldState state = initial();
        SubjectId resident = new SubjectId("resident:1-1");

        assertTrue(state.actorLocations().containsKey(resident));
        assertEquals(state.actorLocations().get(resident), state.actorLocations().getOrDefault(resident, null));
        assertThrows(UnsupportedOperationException.class, () -> state.actorLocations().put(resident, state.actorLocations().get(resident)));
    }

    @Test
    void codecRoundTripsCanonicalMutableStateAndRejectsInvalidState() {
        FrontierWorldState baseline = initial();
        SubjectId container = new SubjectId("container:1-depot");
        SubjectId item = new SubjectId("item:codec");
        SubjectId wheat = new SubjectId("item:bootstrap-1-wheat");
        ExactItemStack heldWheat = new ExactItemStack(wheat, new SubjectId("settlement:1"), "minecraft:wheat", 64,
                new InventoryCustody.ContainerSlot(container, 1));
        ExactInventory inventory = new ExactInventory(baseline.inventory().containers(), Map.of(item,
                new ExactItemStack(item, new SubjectId("settlement:1"), "minecraft:iron_ingot", 64, new InventoryCustody.ContainerSlot(container, 0)),
                wheat, heldWheat), Map.of(), Map.of(), Map.of(), Map.of(), baseline.inventory().surfaces());
        inventory = inventory.recordConflict(new InventoryConflict(new SubjectId("conflict:codec-item"), item, container, 0, InventoryConflictKind.MISSING));
        ProductionJob activeJob = new ProductionJob(new SubjectId("job:production-1-1"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), wheat, new ProductionInputHold.Cold(heldWheat),
                new SubjectId("item:production-1-1-bread"), "minecraft:bread", 64);
        FrontierWorldState source = baseline.withInventory(inventory).withActorBody(new SubjectId("bioform:west-1"), FrontierTestPositions.bodyAboveSupport(new BlockPosition(-400, 64, 400)))
                .withStructureCondition(new SubjectId("structure:2-depot"), StructureCondition.DESTROYED)
                .withInfection(new InfectionCell(-100, 100), HALF).startProductionJob(activeJob, wheat);
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(source);
        assertEquals(source, codec.decode(encoded));
        assertEquals(1, codec.decode(encoded).inventory().conflicts().size());
        byte[] legacy = encoded.clone();
        legacy[4] = 46;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(legacy), "schema 46 has no resident-health tail and cannot be reinterpreted as v47");
        encoded[4] = 17;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));

        Map<SubjectId, ActorLocation> missingActor = new LinkedHashMap<>(source.actorLocations());
        missingActor.remove(new SubjectId("resident:1-1"));
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(source.bootstrap(), missingActor,
                source.structureConditions(), source.infection(), source.inventory(), source.productionJobs(), source.contracts(), source.operations(), source.logisticsHistory(), source.physicalIntents(),
                source.physicalObservations(), source.sceneLeases(), source.hiveColony(), source.structureDamage(), source.physicalDeltas(),
                source.ambientLeases(), source.routeConstructions(), source.routeTopology(), source.strategicPlans(), source.humanPopulation(), source.companies(), source.resourceSites()));
    }

    @Test
    void durableAssemblyPreservesExactPeopleWithoutCreationTeleportAndSurvivesSnapshotRecovery() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:operation-travel"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        OperationAssembly assembly = operation.activeAssembly().orElseThrow();

        assertEquals(OperationStage.ASSEMBLING, operation.stage());
        assertTrue(operation.participantIds().stream().allMatch(actor -> FrontierTestPositions.supportOf(before.actorLocations().get(actor))
                .equals(assembly.positions().get(actor).support())), "creation and recovery retain each actual person, not a route-anchor teleport");
        assertEquals(before, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(before)));
        assertThrows(IllegalArgumentException.class, () -> before.startOperationTravel(operation.id(), new OperationTravel(
                topology(adjacentSegment(operation.route().getFirst(), operation.route().get(1))), 0,
                assembly.positions().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> BodyPosition.above(entry.getValue()))),
                new TransportAnchor(assembly.positions().get(assembly.cargoCarrierId())))));
        assertThrows(IllegalArgumentException.class, () -> before.advanceOperation(operation.id(), operation.routeIndex() + 1, OperationStage.EN_ROUTE));
    }

    @Test
    void hotAssemblyMovesOnlyTheObservedMemberAndRetargetsItsSameLease() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:operation-assembly-hot"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        SubjectId hauler = operation.participantIds().getFirst(); OperationAssembly initial = operation.activeAssembly().orElseThrow();
        AmbientActorLease prepared = AmbientActorProcess.nextLease(state, hauler, new SimInstant(2_550L));
        assertEquals(AmbientGoalKind.OPERATION_ASSEMBLY, prepared.goal());
        state = AmbientLeaseStateProcess.prepare(state, prepared);
        state = AmbientLeaseStateProcess.transition(state, hauler, AmbientLeaseStatus.HOT);
        Map<SubjectId, OperationAssembly.Member> members = new LinkedHashMap<>(initial.members());
        OperationAssembly.Member current = members.get(hauler); members.put(hauler, new OperationAssembly.Member(current.topology(), current.cursor() + 1));

        FrontierWorldState advanced = state.advanceOperationAssembly(operation.id(), new OperationAssembly(members, initial.cargoCarrierId()));

        assertEquals(current.nextSurface().support(), FrontierTestPositions.supportOf(advanced.actorLocations().get(hauler)));
        assertEquals(initial.members().get(operation.participantIds().get(1)).currentSurface().support(), FrontierTestPositions.supportOf(advanced.actorLocations().get(operation.participantIds().get(1))));
        assertEquals(AmbientGoalKind.OPERATION_ASSEMBLY, advanced.ambientLeases().get(hauler).goal());
        assertEquals(advanced, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(advanced)));
    }

    @Test
    void pinnedCodecReusesOnlyItsVerifiedImmutableBootstrap() {
        FrontierBootstrap pinned = FrontierBootstrapper.create(new WorldId("frontier:pinned"), 1234L);
        FrontierWorldState ownState = FrontierWorldState.initial(pinned);
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec(pinned);

        assertSame(pinned, codec.decode(codec.encode(ownState)).bootstrap());

        FrontierWorldState foreignState = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:foreign"), 1234L));
        byte[] foreignBytes = new FrontierWorldStateCodec().encode(foreignState);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(foreignBytes));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(foreignState));
    }

    @Test
    void genericCodecCachesOnlyTheExactImmutableBootstrapHeader() {
        FrontierWorldState own = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:generic-codec-cache"), 1234L));
        byte[] ownBytes = new FrontierWorldStateCodec().encode(own);
        FrontierWorldState first = new FrontierWorldStateCodec().decode(ownBytes);
        FrontierWorldState second = new FrontierWorldStateCodec().decode(ownBytes);
        assertSame(first.bootstrap(), second.bootstrap(), "repeated generic checkpoint reads reuse only immutable genesis");

        FrontierWorldState foreign = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:generic-codec-cache-foreign"), 1234L));
        FrontierWorldState restoredForeign = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(foreign));
        assertTrue(!first.bootstrap().equals(restoredForeign.bootstrap()), "a different world header cannot reuse another world's genesis");
    }

    @Test
    void physicalDeltasRetainExactKnownLossesAndUnknownScarsWithoutTemplateRepair() {
        FrontierWorldState baseline = initial();
        HiveOrgan organ = baseline.bootstrap().hive().organs().getFirst();
        GrayboxCell cell = FrontierGrayboxPlan.compile(baseline).cells().values().stream()
                .filter(value -> value.ownerId().equals(organ.id())).findFirst().orElseThrow();
        PhysicalDelta known = new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(organ.id()), java.util.Optional.of(cell.semanticPart()), "player:test");
        PhysicalDelta scar = new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "explosion:test");

        FrontierWorldState changed = baseline.recordPhysicalDelta(known).recordPhysicalDelta(scar);
        assertEquals(2, changed.physicalDeltas().size());
        assertTrue(!FrontierGrayboxPlan.compile(changed).cells().containsKey(cell.position()));
        assertTrue(changed.isHiveOrganOperational(organ.id()));
        assertTrue(new PhysicalDeltaObserved(known).requiresDurableBeforeEffect());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> changed.recordPhysicalDelta(known));
        assertThrows(IllegalArgumentException.class, () -> baseline.recordPhysicalDelta(new PhysicalDelta(new BlockPosition(1, 64, 1),
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, java.util.Optional.of(organ.id()), java.util.Optional.of(GrayboxSemanticPart.WALL), "bad")));
    }

    @Test
    void dependentPhysicalLossesCommitAtomicallyOrLeaveTheWholeTopologyUntouched() {
        FrontierWorldState baseline = FrontierV3FixtureCatalog.steppedRouteConfiguration(
                new WorldId("frontier:atomic-dependent-loss"), 91L).initialState();
        var plan = FrontierGrayboxPlan.compile(baseline);
        GrayboxCell foundation = plan.cells().values().stream()
                .filter(cell -> cell.semanticPart() == GrayboxSemanticPart.ROUTE_FOUNDATION)
                .filter(cell -> plan.cells().get(new BlockPosition(cell.position().x(), cell.position().y() + 1, cell.position().z())) != null)
                .findFirst().orElseThrow();
        BlockPosition deckPosition = new BlockPosition(foundation.position().x(), foundation.position().y() + 1, foundation.position().z());
        GrayboxCell deck = plan.cells().get(deckPosition);
        assertEquals(GrayboxSemanticPart.ROUTE_SURFACE, deck.semanticPart());
        PhysicalDelta foundationLoss = new PhysicalDelta(foundation.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_FOUNDATION), "player:test");
        PhysicalDelta deckLoss = new PhysicalDelta(deckPosition, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test:survival-after");

        assertThrows(IllegalArgumentException.class, () -> FrontierWorldPhysicalDeltaSupport.recordAll(baseline, List.of(foundationLoss,
                new PhysicalDelta(new BlockPosition(0, 64, 0), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "forged"))));
        assertTrue(baseline.physicalDeltas().isEmpty(), "the first member cannot escape from a rejected atomic observation");

        FrontierWorldState changed = FrontierWorldPhysicalDeltaSupport.recordAll(baseline, List.of(foundationLoss, deckLoss));
        assertEquals(2, changed.physicalDeltas().size());
        assertTrue(!FrontierGrayboxPlan.compile(changed).cells().containsKey(foundation.position()));
        assertTrue(!FrontierGrayboxPlan.compile(changed).cells().containsKey(deckPosition));
        PhysicalDeltasObserved observed = new PhysicalDeltasObserved(List.of(foundationLoss, deckLoss));
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)));
    }

    @Test
    void destroyedPublicAccessSillBecomesExactHallDamageRatherThanAnUnownedPathHole() {
        FrontierWorldState baseline = initial(); Settlement settlement = baseline.bootstrap().settlements().getFirst();
        SettlementStructure hall = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HALL).findFirst().orElseThrow();
        SettlementAccessPort port = SettlementAccessPort.forHall(hall);
        PhysicalDelta loss = new PhysicalDelta(port.assemblyFloor(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(hall.id()), java.util.Optional.of(GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE), "player:test");

        FrontierWorldState changed = baseline.recordPhysicalDelta(loss);

        assertEquals(StructureCondition.DAMAGED, changed.structureConditions().get(hall.id()));
        assertEquals(GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE, changed.structureDamage().get(hall.id()).cells().get(port.assemblyFloor()).semanticPart());
        assertEquals(null, FrontierGrayboxPlan.compile(changed).cells().get(port.assemblyFloor()));
        assertEquals(loss, changed.physicalDeltas().get(port.assemblyFloor()));
    }

    @Test
    void infectionOverlayPlanKeepsOneCompleteBoundedSurfacePatchPerSparseSourceCell() {
        FrontierWorldState baseline = initial();
        InfectionCell trace = new InfectionCell(-100, 100);
        InfectionCell bloom = new InfectionCell(100, 100);
        FrontierWorldState state = baseline.withInfection(trace, new FixedRatio(new FixedScalar(249_999L)))
                .withInfection(bloom, new FixedRatio(new FixedScalar(500_000L)));

        FrontierInfectionOverlayPlan plan = FrontierInfectionOverlayPlan.compile(state);
        InfectionOverlayCell tracePatch = plan.cells().get(trace);
        InfectionOverlayCell bloomPatch = plan.cells().get(bloom);
        assertEquals(state.infection().size(), plan.cells().size());
        assertEquals(InfectionCell.BLOCKS * InfectionCell.BLOCKS, tracePatch.surfaceColumns().size());
        assertEquals(new InfectionOverlayCell.SurfaceColumn(-400, 400), tracePatch.surfaceColumns().getFirst());
        assertEquals(new InfectionOverlayCell.SurfaceColumn(-397, 403), tracePatch.surfaceColumns().getLast());
        assertEquals(InfectionOverlayStage.TRACE, tracePatch.stage());
        assertEquals(InfectionOverlayStage.BLOOM, bloomPatch.stage());
    }

    @Test
    void physicalIntentCannotReferenceAForeignCauseSubject() {
        FrontierWorldState state = initial();
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-foreign"), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, new SubjectId("operation:foreign"), List.of(new SubjectId("resident:1-1")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);

        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(),
                state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(), state.logisticsHistory(), Map.of(intent.id(), intent), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(),
                state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.companies(), state.resourceSites()));
    }

    @Test
    void dynamicHiveColonyKeepsExactGrowthIdentitiesThroughLaterStateChangesAndRecovery() {
        FrontierWorldState baseline = initial();
        SubjectId hive = baseline.bootstrap().hive().id();
        SubjectId eastNest = new SubjectId("nest:seed-east");
        HiveOrgan organ = new HiveOrgan(new SubjectId("organ:east-grown-relay-1"), hive, eastNest, HiveOrganKind.RELAY,
                new BlockPosition(420, 64, 432), java.util.Optional.empty());
        Bioform bioform = new Bioform(new SubjectId("bioform:east-grown-1"), hive, eastNest, BioformChassis.RUNT,
                java.util.Set.of(BioformMutation.ARMORED), BioformAssignment.DEFEND, new BlockPosition(424, 64, 428));

        FrontierWorldState grown = baseline.addHiveOrgan(organ).spawnBioform(bioform)
                .withInfection(InfectionCell.at(new BlockPosition(420, 64, 428)), HALF);
        assertEquals(organ, grown.hiveColony().addedOrgans().get(organ.id()));
        assertEquals(bioform, grown.hiveColony().spawnedBioforms().get(bioform.id()));
        assertEquals(bioform.position(), FrontierTestPositions.supportOf(grown.actorLocations().get(bioform.id())));
        assertEquals(grown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(grown)));
        assertThrows(IllegalArgumentException.class, () -> grown.spawnBioform(bioform));
        assertThrows(IllegalArgumentException.class, () -> baseline.addHiveOrgan(new HiveOrgan(organ.id(), hive, eastNest, HiveOrganKind.RELAY,
                new BlockPosition(520, 64, 432), java.util.Optional.empty())));
    }

    @Test
    void hiveGrowthConsumesOneExactStoreItemBeforeItPublishesItsNewIdentities() {
        FrontierWorldState baseline = initial(); SubjectId hive = baseline.bootstrap().hive().id(); SubjectId east = new SubjectId("nest:seed-east");
        HiveGrowthJob job = new HiveGrowthJob(new SubjectId("job:hive-growth-1"), hive, east, new SubjectId("item:bootstrap-hive-biomass"),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:hive-growth-biomass-1"),
                new HiveOrgan(new SubjectId("organ:east-grown-relay-1"), hive, east, HiveOrganKind.RELAY, new BlockPosition(432, 64, 432), java.util.Optional.empty()),
                new Bioform(new SubjectId("bioform:east-grown-1"), hive, east, BioformChassis.RUNT,
                        java.util.Set.of(BioformMutation.ARMORED), BioformAssignment.DEFEND, new BlockPosition(436, 64, 432)));
        SubjectId store = ((InventoryCustody.ContainerSlot) baseline.inventory().items().get(job.consumedItemId()).custody()).containerId();
        FrontierWorldState active = ReferenceContainerCustodyFixtures.observedAndHeld(baseline.withInventory(baseline.inventory().withSurfaceStatus(store, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(store, ContainerSurfaceStatus.ACTIVE)), store).startHiveGrowth(job);
        assertTrue(active.inventory().items().containsKey(job.consumedItemId()));
        assertEquals(job, active.hiveColony().growthJobs().get(job.id()));
        PhysicalIntent intent = new PhysicalIntent(job.consumptionIntentId(), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                job.id(), List.of(job.id(), job.consumedItemId()), new FixedPosition(FixedScalar.whole(420), FixedScalar.whole(64), FixedScalar.whole(420)), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        FrontierWorldState prepared = HiveGrowthProcess.reducePrepared(active, hive, intent);
        FrontierWorldState running = prepared.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        FrontierWorldState consumed = running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(
                new ExactItemConsumedObservation(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:hive-growth-biomass-1"), intent.id(), job.consumedItemId(), 64, 0)));
        assertTrue(!consumed.inventory().items().containsKey(job.consumedItemId()));
        assertEquals(consumed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(consumed)));
        assertThrows(IllegalArgumentException.class, () -> baseline.startHiveGrowth(new HiveGrowthJob(new SubjectId("job:hive-growth-bad"), hive, east,
                new SubjectId("item:bootstrap-1-wheat"), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:hive-growth-biomass-bad"), job.organ(), job.bioform())));
        assertThrows(IllegalArgumentException.class, () -> baseline.startHiveGrowth(new HiveGrowthJob(new SubjectId("job:hive-growth-remote"), hive,
                new SubjectId("nest:seed-west"), job.consumedItemId(), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:hive-growth-biomass-remote"),
                new HiveOrgan(new SubjectId("organ:west-grown-relay-remote"), hive, new SubjectId("nest:seed-west"), HiveOrganKind.RELAY,
                        new BlockPosition(-408, 64, 432), java.util.Optional.empty()),
                new Bioform(new SubjectId("bioform:west-grown-remote"), hive, new SubjectId("nest:seed-west"), BioformChassis.RUNT,
                        java.util.Set.of(BioformMutation.ARMORED), BioformAssignment.DEFEND, new BlockPosition(-404, 64, 432)))));
        assertEquals(baseline, HiveGrowthProcess.reduceBlocked(baseline, hive,
                new HiveGrowthBlocked(hive, east, new SubjectId("work:hive-growth-1"), HiveGrowthBlockReason.BIOMASS_UNAVAILABLE)));
        FrontierWorldState completed = consumed.completeHiveGrowth(job.id());
        assertTrue(completed.hiveColony().growthJobs().isEmpty());
        assertEquals(job.organ(), completed.hiveColony().addedOrgans().get(job.organ().id()));
        assertEquals(job.bioform(), completed.hiveColony().spawnedBioforms().get(job.bioform().id()));
        assertEquals(job.bioform().position(), FrontierTestPositions.supportOf(completed.actorLocations().get(job.bioform().id())));
    }

    @Test
    void unknownHiveBiomassEffectReleasesTheActiveGrowthSlotWithoutAssumingConsumption() {
        FrontierWorldState baseline = initial(); SubjectId hive = baseline.bootstrap().hive().id(); SubjectId east = new SubjectId("nest:seed-east");
        HiveGrowthJob job = new HiveGrowthJob(new SubjectId("job:hive-growth-unknown"), hive, east, new SubjectId("item:bootstrap-hive-biomass"),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:hive-growth-biomass-unknown"),
                new HiveOrgan(new SubjectId("organ:east-grown-relay-unknown"), hive, east, HiveOrganKind.RELAY, new BlockPosition(440, 64, 432), java.util.Optional.empty()),
                new Bioform(new SubjectId("bioform:east-grown-unknown"), hive, east, BioformChassis.RUNT,
                        java.util.Set.of(BioformMutation.ARMORED), BioformAssignment.DEFEND, new BlockPosition(444, 64, 432)));
        SubjectId store = ((InventoryCustody.ContainerSlot) baseline.inventory().items().get(job.consumedItemId()).custody()).containerId();
        FrontierWorldState active = ReferenceContainerCustodyFixtures.observedAndHeld(baseline.withInventory(baseline.inventory().withSurfaceStatus(store, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(store, ContainerSurfaceStatus.ACTIVE)), store).startHiveGrowth(job);
        PhysicalIntent intent = new PhysicalIntent(job.consumptionIntentId(), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                job.id(), List.of(job.id(), job.consumedItemId()), new FixedPosition(FixedScalar.whole(420), FixedScalar.whole(64), FixedScalar.whole(420)), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        FrontierWorldState running = HiveGrowthProcess.reducePrepared(active, hive, intent)
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        FrontierWorldState unknown = running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        FrontierWorldState released = HiveGrowthProcess.reduceBlocked(unknown, hive,
                new HiveGrowthBlocked(hive, east, job.id(), HiveGrowthBlockReason.PHYSICAL_CONSUMPTION_UNKNOWN));
        assertTrue(released.hiveColony().growthJobs().isEmpty());
        assertTrue(released.inventory().items().containsKey(job.consumedItemId()), "unknown postcondition must never silently consume biomass");
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:state"), 1234L));
    }

    private static List<BlockPosition> adjacentSegment(BlockPosition from, BlockPosition to) {
        java.util.ArrayList<BlockPosition> cells = new java.util.ArrayList<>();
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int x = from.x(), z = from.z();; x += stepX, z += stepZ) {
            cells.add(new BlockPosition(x, from.y(), z));
            if (x == to.x() && z == to.z()) return List.copyOf(cells);
        }
    }

    private static TraversalTopology topology(List<BlockPosition> corridor) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:world-state-test:" + corridor.hashCode()), 1L,
                FrontierRouteNetwork.OWNER, TraversalKind.PEDESTRIAN, java.util.Set.of(TraversalCapability.PEDESTRIAN),
                corridor.stream().map(SurfaceAnchor::new).toList());
    }
}
