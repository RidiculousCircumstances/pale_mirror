package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class FrontierWorldRuntimeDefinitionTest {

    @Test
    void concreteProfileStartsWithTheRequiredExactWorldPopulation() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:definition"), 91L));

        FrontierWorldProjection projection = engine.projection(ProjectionQuery.summary());

        assertEquals(12, projection.settlementCount());
        assertEquals(48, projection.bioformCount());
        org.junit.jupiter.api.Assertions.assertTrue(projection.residentCount() >= 240 && projection.residentCount() <= 480);
        assertEquals(2 + projection.settlementCount() * EngineeringRecoveryTeam.MAX_MEMBERS, projection.itemStackCount());
        assertEquals(0, projection.activeProductionJobCount());
        assertEquals(18, projection.infectedCellCount());
    }

    @Test
    void hotAssemblyRequiresExactLeasesThenAtomicallyStartsTheFirstTravelSegment() {
        WorldId world = new WorldId("frontier:hot-assembly");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(
                FrontierV3FixtureCatalog.operationAssemblyConfiguration(world, 91L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = initial.operations().get(new SubjectId("operation:supply-1-2"));
        OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        SubjectId first = operation.participantIds().getFirst();
        Map<SubjectId, OperationAssembly.Member> rejectedMembers = new LinkedHashMap<>(assembly.members());
        OperationAssembly.Member firstMember = rejectedMembers.get(first);
        rejectedMembers.put(first, new OperationAssembly.Member(firstMember.topology(), firstMember.cursor() + 1));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(engine, world, "assembly-without-lease",
                new OperationAssemblyAdvanced(operation.id(), new OperationAssembly(rejectedMembers, assembly.cargoCarrierId()))));

        for (SubjectId participant : operation.participantIds()) {
            FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            AmbientActorLease lease = AmbientActorProcess.nextLease(state, participant, engine.checkpoint().instant());
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(engine, world, "assembly-prepare-" + participant.value(), new AmbientLeasePrepared(lease)));
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(engine, world, "assembly-hot-" + participant.value(), new AmbientLeaseTransition(participant, AmbientLeaseStatus.HOT)));
        }
        int sequence = 0;
        while (true) {
            FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            RouteOperation current = state.operations().get(operation.id());
            if (current.stage() == OperationStage.EN_ROUTE) break;
            OperationAssembly active = current.activeAssembly().orElseThrow();
            OperationAssembly advanced = active.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).filter(entry -> !entry.getValue().arrived())
                    .map(entry -> {
                        Map<SubjectId, OperationAssembly.Member> members = new LinkedHashMap<>(active.members());
                        OperationAssembly.Member member = members.get(entry.getKey());
                        members.put(entry.getKey(), new OperationAssembly.Member(member.topology(), member.cursor() + 1));
                        try { return new OperationAssembly(members, active.cargoCarrierId()); }
                        catch (IllegalArgumentException collision) { return null; }
                    }).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(engine, world,
                    "assembly-arrival-" + sequence++, new OperationAssemblyAdvanced(operation.id(), advanced)));
        }
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation departed = after.operations().get(operation.id());
        assertEquals(OperationStage.EN_ROUTE, departed.stage());
        assertTrue(departed.activeTravel().isPresent());
        assertEquals(departed.activeTravel().orElseThrow().formation(), after.actorLocations().entrySet().stream()
                .filter(entry -> departed.participantIds().contains(entry.getKey())).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().body())));
        assertTrue(engine.checkpoint().schedules().stream().anyMatch(action -> action.subject().equals(operation.id())
                && action.kind().equals("frontier.operation.progress")));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
    }

    @Test
    void hotAssemblyDeferralIsExactDurableAndPreventsColdFromSkippingTheLoadedObstacle() {
        WorldId world = new WorldId("frontier:hot-assembly-deferral");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(
                FrontierV3FixtureCatalog.operationAssemblyConfiguration(world, 91L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = initial.operations().get(new SubjectId("operation:supply-1-2"));
        OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        SubjectId actor = operation.participantIds().getFirst();
        Settlement settlement = initial.bootstrap().settlements().stream().filter(value -> value.id().equals(operation.settlementId())).findFirst().orElseThrow();
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.HALL).findFirst().orElseThrow());
        OperationAssemblyDeferral deferral = new OperationAssemblyDeferral(actor,
                assembly.members().get(actor).nextSurface(), new SurfaceAnchor(access.throatFloor()),
                OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION);

        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class,
                submit(engine, world, "assembly-deferral-without-hot-lease", new OperationAssemblyDeferred(operation.id(), deferral)));
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, actor, engine.checkpoint().instant());
        assertEquals(AmbientGoalKind.OPERATION_ASSEMBLY, lease.goal());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "assembly-deferral-prepare", new AmbientLeasePrepared(lease)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "assembly-deferral-hot", new AmbientLeaseTransition(actor, AmbientLeaseStatus.HOT)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "assembly-deferral", new OperationAssemblyDeferred(operation.id(), deferral)));

        FrontierWorldState deferred = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(deferral, deferred.operations().get(operation.id()).activeAssembly().orElseThrow().deferral().orElseThrow());
        assertEquals(deferred, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(deferred)),
                "the exact blocked actor and target survive a snapshot/recovery boundary");
        assertEquals(new OperationAssemblyDeferred(operation.id(), deferral), FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                "frontier.operation_assembly_deferred", FrontierWorldRuntimeDefinition.payloadCodecs().encode(new OperationAssemblyDeferred(operation.id(), deferral))));
        List<ProposedEvent> cold = SupplyOperationProcess.planAssembly(deferred,
                SupplyOperationProcess.operationAssembly(operation, engine.checkpoint().instant().ticks()));
        assertEquals(1, cold.size());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class, cold.getFirst().payload(),
                "COLD retains a bounded recovery check but cannot advance a loaded-world block on its own");
    }

    @Test
    void exactStructuralDamageIsDurableAndDerivesItsConditionFromKnownCells() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:structure-damage"), 91L));
        SettlementStructure structure = state.bootstrap().settlements().getFirst().structures().getFirst();
        List<GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().equals(structure.id())).sorted(java.util.Comparator
                        .comparingInt((GrayboxCell cell) -> cell.position().x()).thenComparingInt(cell -> cell.position().y())
                        .thenComparingInt(cell -> cell.position().z())).toList();
        int destructiveThreshold = (FrontierGrayboxPlan.intactStructureCellCount(state.bootstrap().terrain(), structure) + 2) / 3;
        StructureDamaged first = new StructureDamaged(structure.id(), cells.getFirst().position(), cells.getFirst().semanticPart(), "player:test");
        state = state.recordStructureDamage(first);
        assertEquals(StructureCondition.DAMAGED, state.structureConditions().get(structure.id()));
        assertEquals(1, state.structureDamage().get(structure.id()).cells().size());
        assertEquals(first, FrontierWorldRuntimeDefinition.payloadCodecs().decode(first.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(first)));

        for (int index = 1; index < destructiveThreshold; index++) {
            GrayboxCell cell = cells.get(index);
            state = state.recordStructureDamage(new StructureDamaged(structure.id(), cell.position(), cell.semanticPart(), "player:test"));
        }
        assertEquals(StructureCondition.DESTROYED, state.structureConditions().get(structure.id()));
        assertEquals(destructiveThreshold, state.structureDamage().get(structure.id()).cells().size());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        FrontierWorldState destroyed = state;
        assertThrows(IllegalArgumentException.class, () -> destroyed.recordStructureDamage(new StructureDamaged(structure.id(),
                new BlockPosition(0, 64, 0), GrayboxSemanticPart.WALL, "player:test")));

        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:structure-command"), 91L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        GrayboxCell acceptedCell = FrontierGrayboxPlan.compile(initial).cells().values().stream()
                .filter(cell -> cell.ownerId().value().startsWith("structure:")).findFirst().orElseThrow();
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:structure-damage");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId),
                        new StructureDamaged(acceptedCell.ownerId(), acceptedCell.position(), acceptedCell.semanticPart(), "player:test"))));
    }

    @Test
    void typedPhysicalDeltasDriveHiveAvailabilityAndRouteObstructionWithoutASecondWorldModel() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:physical-deltas"), 91L));
        HiveOrgan organ = initial.bootstrap().hive().organs().getFirst();
        GrayboxCell organCell = FrontierGrayboxPlan.compile(initial).cells().values().stream()
                .filter(value -> value.ownerId().equals(organ.id())).findFirst().orElseThrow();
        FrontierWorldState organDamaged = initial.recordPhysicalDelta(new PhysicalDelta(organCell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(organ.id()), java.util.Optional.of(organCell.semanticPart()), "explosion:test"));
        assertTrue(!FrontierGrayboxPlan.compile(organDamaged).cells().containsKey(organCell.position()));

        List<BlockPosition> supplyRoute = FrontierRouteNetwork.supplyWaypoints(initial.bootstrap(), initial.bootstrap().settlements().getFirst().id());
        BlockPosition routePosition = supplyRoute.get(2);
        PhysicalDelta routeLoss = new PhysicalDelta(routePosition, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test");
        FrontierWorldState routeDamaged = initial.recordPhysicalDelta(routeLoss);
        PhysicalDeltaObserved observed = (PhysicalDeltaObserved) FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.physical_delta_observed",
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new PhysicalDeltaObserved(routeLoss)));
        assertEquals(routeLoss, observed.delta());
        assertTrue(!FrontierGrayboxPlan.compile(routeDamaged).cells().containsKey(routePosition));
        assertTrue(!FrontierRouteNetwork.isPassable(initial.bootstrap(), supplyRoute, routeDamaged.physicalDeltas()));
    }

    @Test
    void hiveGrowthConsumesNestLocalEastStoreBiomassThenPublishesEastOrganAndBioform() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:hive-growth"), 91L));
        FrontierWorldState completed = advanceUntil(engine, 12_000L,
                state -> state.hiveColony().growthJobs().containsKey(new SubjectId("job:hive-growth-1")));
        SubjectId biomass = new SubjectId("item:bootstrap-hive-biomass");
        assertTrue(!completed.inventory().items().containsKey(biomass), "COLD growth consumes its exact inactive-store biomass before completion");
        HiveGrowthJob job = new HiveGrowthJob(new SubjectId("job:hive-growth-1"), completed.bootstrap().hive().id(),
                new SubjectId("nest:seed-east"), biomass,
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:hive-growth-biomass-1"),
                new HiveOrgan(new SubjectId("organ:east-grown-heart-1"), completed.bootstrap().hive().id(), new SubjectId("nest:seed-east"),
                        HiveOrganKind.RELAY, new BlockPosition(432, 64, 432), java.util.Optional.empty()),
                new Bioform(new SubjectId("bioform:east-grown-1"), completed.bootstrap().hive().id(), new SubjectId("nest:seed-east"),
                        BioformRole.GUARD, new BlockPosition(436, 64, 432)));
        HiveGrowthStarted started = new HiveGrowthStarted(job);
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        HiveGrowthBiomassConsumed consumed = new HiveGrowthBiomassConsumed(job.id(), biomass);
        assertEquals(consumed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(consumed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(consumed)));
        assertEquals(1, completed.hiveColony().growthJobs().size());
        assertEquals(48, engine.projection(ProjectionQuery.summary()).bioformCount(), "without a materialized receipt growth must not fabricate biomass outputs");
        HiveGrowthBlocked blocked = new HiveGrowthBlocked(job.hiveId(), job.nestId(), new SubjectId("work:hive-growth-2"), HiveGrowthBlockReason.BIOMASS_UNAVAILABLE);
        assertEquals(blocked, FrontierWorldRuntimeDefinition.payloadCodecs().decode(blocked.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(blocked)));
    }

    @Test
    void developmentHiveGrowthProfileStopsAtTheRealExactBiomassBoundary() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.hiveGrowthConfiguration(new WorldId("frontier:hive-growth-profile"), 91L));

        var checkpoint = engine.checkpoint();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
        HiveGrowthJob job = state.hiveColony().growthJobs().get(new SubjectId("job:hive-growth-1"));

        assertTrue(job != null, "the profile must retain the real scheduled growth job");
        assertEquals(PhysicalIntentStatus.PREPARED, state.physicalIntents().get(job.consumptionIntentId()).status());
        assertEquals(ContainerSurfaceStatus.PREPARED, state.inventory().surfaces().get(new SubjectId("container:hive-east-store")).status(),
                "the profile must await one real PREPARED -> ACTIVE chest lifecycle, not pretend a chest already exists");
        assertEquals(0, state.hiveColony().addedOrgans().size());
        assertEquals(0, state.hiveColony().spawnedBioforms().size());
        assertEquals(18, state.infection().size());
        assertTrue(checkpoint.schedules().stream().anyMatch(action -> action.dueAt().ticks() > checkpoint.instant().ticks()),
                "the fixture must retain the ordinary future world schedule after the physical receipt");
    }

    @Test
    void developmentHiveNutrientProfileKeepsOneExactBiomassAtItsPreparedSourceUntilObservedDeparture() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.hiveNutrientTransferConfiguration(
                new WorldId("frontier:hive-nutrient-profile"), 91L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId transferId = new SubjectId("transfer:hive-nutrient-task-development-hive-nutrient-transfer");

        assertEquals(ContainerSurfaceStatus.PREPARED, initial.inventory().surfaces().get(new SubjectId("container:hive-east-store")).status());
        assertEquals(ContainerSurfaceStatus.PREPARED, initial.inventory().surfaces().get(new SubjectId("container:hive-west-store")).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0),
                initial.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass")).custody());
        engine.advanceTo(new SimInstant(1L), new WorkBudget(32, 256));
        FrontierWorldState pending = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        HiveNutrientTransfer transfer = pending.hiveColony().nutrientTransfers().get(transferId);
        assertEquals(HiveNutrientTransferPhase.DEPARTURE_PENDING, transfer.phase());
        assertEquals(PhysicalIntentStatus.PREPARED, pending.physicalIntents().get(transfer.endpointIntentId().orElseThrow()).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0),
                pending.inventory().items().get(transfer.itemId()).custody(), "fixture must not fabricate COLD cargo before a loaded exact departure");
    }

    @Test
    void developmentRouteReturnProfileRetainsOneExactColdNorthwatchShipment() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:route-return-profile"), 91L));

        var checkpoint = engine.checkpoint();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));

        assertEquals(OperationStage.EN_ROUTE, operation.stage());
        assertEquals(0, operation.routeIndex());
        assertEquals(new BlockPosition(-366, 64, -340), operation.route().getFirst());
        assertEquals(new BlockPosition(-366, 64, -304), operation.route().get(1));
        assertEquals(List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16"), new SubjectId("resident:1-28")), operation.participantIds());
        assertTrue(checkpoint.schedules().stream().noneMatch(action -> action.subject().equals(operation.id())
                        && action.kind().equals("frontier.operation.progress")),
                "the native profile must wait for ordinary HOT admission before arming its next COLD route step");
    }

    @Test
    void developmentHealthQuarantineProfileRequiresAnOrdinarySettlementReview() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.healthQuarantineConfiguration(
                new WorldId("frontier:health-quarantine-profile"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        Settlement settlement = before.bootstrap().settlements().getFirst();

        assertEquals(SettlementQuarantineStatus.NORMAL, before.humanPopulation().quarantine(settlement.id()).status());
        assertEquals(0, before.humanPopulation().activeCases(settlement.id()));
        assertEquals(19, before.infection().size(), "the fixture adds one settlement contact to the eighteen bootstrap hive cells, not a completed disease result");
        assertTrue(HumanHealthProcess.localExposure(before, settlement),
                "the fixture contact must reach ordinary health assessment before its scheduled review");

        engine.advanceTo(new SimInstant(1L), new WorkBudget(64, 512));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId resident = settlement.residents().stream().map(Resident::id).sorted().findFirst().orElseThrow();

        assertEquals(ResidentHealthStatus.EXPOSED, after.humanPopulation().health(resident).status());
        assertEquals(SettlementQuarantineStatus.QUARANTINED, after.humanPopulation().quarantine(settlement.id()).status());
        assertEquals(1, after.humanPopulation().activeCases(settlement.id()));
        assertTrue(after.strategicPlans().hasActiveObjective(settlement.id(), StrategicObjectiveLane.STRATEGIC),
                "the same review retains the real containment objective rather than a health-only test path");
    }

    @Test
    void developmentResidentTransitProfileRetainsOneRealColdJourneyWithoutAHiddenHotBody() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.residentTransitConfiguration(
                new WorldId("frontier:resident-transit-profile"), 91L));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());

        assertEquals(1, state.humanPopulation().migrations().size());
        ResidentMigrationJourney journey = state.humanPopulation().migrations().values().iterator().next();
        assertEquals(ResidentMigrationStatus.EN_ROUTE, journey.status());
        assertEquals(journey.currentPosition(), FrontierTestPositions.supportOf(state.actorLocations().get(journey.residentId())));
        assertEquals(1L, state.humanPopulation().inboundHousingReservations(journey.destinationSettlementId()));
        assertEquals(null, state.ambientLeases().get(journey.residentId()),
                "the fixture must not mint a HOT lease/body: only an ordinary loaded-world visit may do that");
        assertTrue(engine.checkpoint().schedules().isEmpty(), "the fixture must not race the HOT evidence with a COLD timer");
    }

    @Test
    void durableObservedItemTransferMovesOnlyTheNamedCanonicalStack() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:item-custody"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId itemId = new SubjectId("item:bootstrap-1-wheat");
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) before.inventory().items().get(itemId).custody();
        var player = java.util.UUID.fromString("00000000-0000-0000-0000-000000000023");
        ExactItemCustodyChanged changed = new ExactItemCustodyChanged(itemId, source, new InventoryCustody.Player(player));
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:exact-item-withdraw");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, new WorldId("frontier:item-custody"), checkpoint.revision(), checkpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), changed)));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(new InventoryCustody.Player(player), after.inventory().items().get(itemId).custody());
        assertEquals(changed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(changed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> after.inventory().moveObservedItem(itemId, source, new InventoryCustody.Player(player)));
    }

    @Test
    void durableObservedEquipmentTransferRetainsOneCanonicalResidentAndSurvivesSnapshotRecovery() {
        WorldId worldId = new WorldId("frontier:actor-custody");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId itemId = new SubjectId("item:bootstrap-1-wheat");
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) before.inventory().items().get(itemId).custody();
        SubjectId resident = before.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(new SubjectId("settlement:1"))).findFirst().orElseThrow().id();
        ExactItemCustodyChanged changed = new ExactItemCustodyChanged(itemId, source, new InventoryCustody.Actor(resident));
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:exact-item-equip");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, worldId, checkpoint.revision(), checkpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), changed)));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(new InventoryCustody.Actor(resident), after.inventory().items().get(itemId).custody());
        assertEquals(List.of(itemId), after.inventory().actorItems(resident).stream().map(ExactItemStack::id).toList());
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        assertEquals(changed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(changed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(changed)));
    }

    @Test
    void durableInventoryConflictRetainsPhysicalDriftWithoutAdoptingOrRepairingIt() {
        WorldId worldId = new WorldId("frontier:inventory-conflict");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId itemId = new SubjectId("item:bootstrap-1-wheat");
        SubjectId containerId = ((InventoryCustody.ContainerSlot) before.inventory().items().get(itemId).custody()).containerId();
        InventoryConflict conflict = new InventoryConflict(new SubjectId("conflict:inventory-bootstrap-wheat"), itemId, containerId, 0, InventoryConflictKind.MISSING);
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:inventory-conflict");

        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, worldId, checkpoint.revision(), checkpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId),
                        new InventoryConflictObserved(conflict))));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(conflict, after.inventory().conflicts().get(conflict.id()));
        assertEquals(new InventoryCustody.ContainerSlot(containerId, 0), after.inventory().items().get(itemId).custody());
        assertEquals(1, engine.projection(ProjectionQuery.summary()).inventoryConflictCount());

        InventoryConflict foreign = new InventoryConflict(new SubjectId("conflict:foreign"), new SubjectId("item:untracked"), containerId, 1,
                InventoryConflictKind.FOREIGN_OR_DUPLICATE);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class,
                engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                        new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:foreign-inventory-conflict"), worldId,
                        engine.checkpoint().revision(), engine.checkpoint().instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                        io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:foreign-inventory-conflict")),
                        new InventoryConflictObserved(foreign))));
    }

    @Test
    void physicalExecutorCanAdvanceOnlyAnOwnedKnownContainerSurface() {
        WorldId worldId = new WorldId("frontier:container-surface");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        SubjectId container = new SubjectId("container:hive-west-store");
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:prepare-container-surface");
        ContainerSurfaceTransition prepared = new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED);

        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, worldId, checkpoint.revision(), checkpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), prepared)));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(ContainerSurfaceStatus.PREPARED, after.inventory().surfaces().get(container).status());
        assertEquals(prepared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(prepared.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(prepared)));
        var rejected = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:unknown-container-surface"), worldId,
                engine.checkpoint().revision(), engine.checkpoint().instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:unknown-container-surface")),
                new ContainerSurfaceTransition(new SubjectId("container:unknown"), ContainerSurfaceStatus.PREPARED)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, rejected);
    }

    @Test
    void hiveGrowthTaskIsPersistedDeterministicScheduledWorldWork() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:pulse"), 91L));
        FrontierWorldState state = advanceUntil(engine, 12_000L, candidate -> candidate.strategicPlans().tasks().values().stream()
                .anyMatch(task -> task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM && task.status() == StrategicTaskStatus.ACTIVE));

        FrontierWorldProjection projection = engine.projection(ProjectionQuery.summary());
        assertEquals(18, projection.infectedCellCount());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM).findFirst().orElseThrow().status());
    }

    @Test
    void longLivedPulsesKeepTheirBoundedWorkCostInsteadOfGrowingIntoTheSchedulerBudget() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:long-pulse"), 91L));
        for (long tick = 100L; tick <= 60_000L; tick += 100L) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));
            if (tick % 1_200L == 0L) {
                // Model the runtime's durable checkpoint before it releases its covered WAL.
                // This test measures bounded scheduled work, not intentionally exhausted WAL.
                engine.compact(engine.checkpoint().revision());
            }
        }

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse("no failure detail"));
        assertTrue(engine.projection(ProjectionQuery.summary()).revision().value() >= 600L);
    }

    @Test
    void productionPayloadsRoundTripAsPersistedTypedFacts() {
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-1"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:bootstrap-1-wheat"),
                new SubjectId("item:production-1-1-bread"), "minecraft:bread", 64);
        ProductionCompleted completed = new ProductionCompleted(job.id(), new ExactItemStack(job.outputItemId(), job.settlementId(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0)));

        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(completed, codecs.decode(completed.type(), codecs.encode(completed)));
        ProductionStarted started = new ProductionStarted(job, job.consumedItemId());
        assertEquals(started, codecs.decode(started.type(), codecs.encode(started)));
        SupplyContract contract = new SupplyContract(new SubjectId("contract:supply-1-1"), new SubjectId("settlement:1"),
                new SubjectId("hive:frontier"), new SubjectId("cargo:supply-1-1"), "minecraft:bread", 64, ContractStatus.ORDERED);
        SupplyContractCreated created = new SupplyContractCreated(contract);
        assertEquals(created, codecs.decode(created.type(), codecs.encode(created)));
        SupplyContractAbandoned abandoned = new SupplyContractAbandoned(contract.id());
        assertEquals(abandoned, codecs.decode(abandoned.type(), codecs.encode(abandoned)));
        CargoLoaded loaded = new CargoLoaded(contract.id(), new CargoBatch(contract.cargoId(), contract.settlementId(), List.of(job.outputItemId())));
        assertEquals(loaded, codecs.decode(loaded.type(), codecs.encode(loaded)));
        RouteOperation operation = new RouteOperation(new SubjectId("operation:supply-1-1"), contract.settlementId(), contract.cargoId(), contract.recipientId(),
                List.of(new SubjectId("resident:1-6"), new SubjectId("resident:1-4")), List.of(new BlockPosition(-360, 64, -340), new BlockPosition(-420, 64, 420)), 0, OperationStage.EN_ROUTE);
        OperationCreated operationCreated = new OperationCreated(operation);
        OperationAdvanced operationAdvanced = new OperationAdvanced(operation.id(), 1, OperationStage.ARRIVED);
        OperationTravel travel = new OperationTravel(TraversalTopology.corridor(new TraversalTopologyId("topology:runtime-payload"), 1L,
                FrontierRouteNetwork.OWNER, TraversalKind.PEDESTRIAN, java.util.Set.of(TraversalCapability.PEDESTRIAN),
                List.of(SurfaceAnchor.at(-360, 64, -340), SurfaceAnchor.at(-361, 64, -340))), 0,
                Map.of(new SubjectId("resident:1-6"), new BodyPosition(-360, 65, -339), new SubjectId("resident:1-4"), new BodyPosition(-360, 65, -341)),
                TransportAnchor.atSupportCell(new BlockPosition(-360, 64, -340)));
        OperationTravelStarted travelStarted = new OperationTravelStarted(operation.id(), travel);
        OperationTravelAdvanced travelAdvanced = new OperationTravelAdvanced(operation.id(), travel.advance(1,
                Map.of(new SubjectId("resident:1-6"), new BodyPosition(-361, 65, -339), new SubjectId("resident:1-4"), new BodyPosition(-361, 65, -341)),
                TransportAnchor.atSupportCell(new BlockPosition(-361, 64, -340))));
        assertEquals(operationCreated, codecs.decode(operationCreated.type(), codecs.encode(operationCreated)));
        assertEquals(operationAdvanced, codecs.decode(operationAdvanced.type(), codecs.encode(operationAdvanced)));
        assertThrows(IllegalArgumentException.class, () -> new OperationAdvanced(operation.id(), 0, OperationStage.RETURNING),
                "returning is entered only by a completed exact reverse travel segment, never by the obsolete direct-advance event");
        assertEquals(travelStarted, codecs.decode(travelStarted.type(), codecs.encode(travelStarted)));
        assertEquals(travelAdvanced, codecs.decode(travelAdvanced.type(), codecs.encode(travelAdvanced)));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-supply-1-1"), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, operation.id(), List.of(operation.id(), operation.cargoId()), new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO),
                0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);
        PhysicalIntentPrepared prepared = new PhysicalIntentPrepared(intent);
        assertEquals(prepared, codecs.decode(prepared.type(), codecs.encode(prepared)));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        assertEquals(transition, codecs.decode(transition.type(), codecs.encode(transition)));
        CargoHandoffObservation observation = new CargoHandoffObservation(new PhysicalObservationId("observation:supply-1-1"), intent.id(), contract.cargoId(),
                List.of(new CargoHandoffPlacement(job.outputItemId(), new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0))));
        PhysicalIntentTransition confirmed = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(confirmed, codecs.decode(confirmed.type(), codecs.encode(confirmed)));
    }

    @Test
    void supplyContractLoadsTheSameProducedBreadIntoIdentifiedCargo() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:cargo"), 91L));
        for (long tick = 100L; tick <= 4_000L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        SupplyContract contract = state.contracts().get(new SubjectId("contract:supply-1-2"));
        assertTrue(contract != null, () -> "plans=" + state.strategicPlans().objectives() + ", items=" + state.inventory().items());
        assertEquals(ContractStatus.LOADED, contract.status());
        assertEquals(64, contract.itemCount(), "a supply obligation must retain the exact produced stack count, not its one-item template");
        CargoBatch cargo = state.inventory().cargo().get(contract.cargoId());
        assertEquals(List.of(new SubjectId("item:production-1-1-bread")), cargo.itemIds());
        assertEquals(new InventoryCustody.Cargo(cargo.id()), state.inventory().items().get(cargo.itemIds().getFirst()).custody());
    }

    @Test
    void loadedCargoMovesThroughAPersistedColdRouteWithExactParticipants() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:route"), 91L));
        FrontierWorldState state = advanceUntil(engine, 12_000L, candidate -> {
            RouteOperation operation = candidate.operations().get(new SubjectId("operation:supply-1-2"));
            return operation != null && operation.stage() == OperationStage.ARRIVED;
        });
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        assertEquals(OperationStage.ARRIVED, operation.stage());
        assertEquals(operation.route().size() - 1, operation.routeIndex());
        BlockPosition destination = operation.route().getLast();
        OperationTravel arrivedTravel = operation.activeTravel().orElseThrow();
        assertTrue(arrivedTravel.arrived());
        assertEquals(destination, arrivedTravel.currentPosition());
        assertTrue(operation.participantIds().stream().allMatch(participant -> arrivedTravel.formation().get(participant)
                .equals(state.actorLocations().get(participant).body())));
        assertEquals(1, engine.projection(ProjectionQuery.summary()).activeRouteOperationCount());
        PhysicalIntent handoff = state.physicalIntents().get(new PhysicalIntentId("intent:cargo-handoff-supply-1-2"));
        assertEquals(null, handoff, "an unloaded receiver completes the exact COLD delivery without a materialization-only intent");
        assertEquals(ContractStatus.DELIVERED, state.contracts().get(new SubjectId("contract:supply-1-2")).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0),
                state.inventory().items().get(new SubjectId("item:production-1-1-bread")).custody());
        assertEquals(StrategicTaskStatus.COMPLETED, supplyTask(state).status());
        assertEquals(List.of(preparationTask(state).id()), supplyTask(state).dependencies());
        assertEquals(List.of(productionTask(state).id()), preparationTask(state).dependencies());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void routeReducerRejectsASkippedRoutePoint() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(new WorldId("frontier:route-negative"), 91L));
        for (long tick = 100L; tick <= 2_700L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));

        int skippedIndex = operation.routeIndex() + 2;
        OperationStage skippedStage = skippedIndex == operation.route().size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE;
        assertThrows(IllegalArgumentException.class, () -> state.advanceOperation(operation.id(), skippedIndex, skippedStage));
    }

    @Test
    void physicalIntentTransactionIsFlushedBeforeAnExecutorCouldObserveIt() {
        List<Durability> durabilities = new ArrayList<>();
        var configuration = materializedSupplyConfiguration(new WorldId("frontier:durability"), 91L)
                .withTransactionCommitter((transaction, durability) -> durabilities.add(durability));
        var engine = FrontierEngines.create(configuration);
        advanceUntil(engine, 12_000L, candidate -> candidate.physicalIntents()
                .containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));

        assertTrue(durabilities.contains(Durability.BATCHABLE));
        assertTrue(durabilities.contains(Durability.DURABLE_BEFORE_EFFECT));
    }

    @Test
    void physicalIntentRequiresSequentialExecutionAndAnObservedPostcondition() {
        var engine = FrontierEngines.create(materializedSupplyConfiguration(new WorldId("frontier:intent-lifecycle"), 91L));
        FrontierWorldState prepared = advanceUntil(engine, 12_000L, candidate -> candidate.physicalIntents()
                .containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));
        PhysicalIntentId id = new PhysicalIntentId("intent:cargo-handoff-supply-1-2");
        CargoHandoffObservation observation = new CargoHandoffObservation(new PhysicalObservationId("observation:cargo-handoff-supply-1-1"), id,
                new SubjectId("cargo:supply-1-2"), List.of(new CargoHandoffPlacement(new SubjectId("item:production-1-1-bread"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0))));

        assertThrows(IllegalArgumentException.class, () -> prepared.transitionPhysicalIntent(id, PhysicalIntentStatus.CONFIRMED,
                Optional.of(observation)));
        FrontierWorldState running = prepared.transitionPhysicalIntent(id, PhysicalIntentStatus.RUNNING, Optional.empty());
        CargoHandoffObservation foreignStore = new CargoHandoffObservation(new PhysicalObservationId("observation:cargo-handoff-foreign-store"), id,
                new SubjectId("cargo:supply-1-2"), List.of(new CargoHandoffPlacement(new SubjectId("item:production-1-1-bread"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0))));
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(id, PhysicalIntentStatus.CONFIRMED, Optional.of(foreignStore)));
        FrontierWorldState confirmed = running.transitionPhysicalIntent(id, PhysicalIntentStatus.CONFIRMED,
                Optional.of(observation));
        assertEquals(PhysicalIntentStatus.CONFIRMED, confirmed.physicalIntents().get(id).status());
        assertEquals(Optional.of(new PhysicalObservationId("observation:cargo-handoff-supply-1-1")), confirmed.physicalIntents().get(id).postconditionObservationId());
        assertEquals(ContractStatus.DELIVERED, confirmed.contracts().get(new SubjectId("contract:supply-1-2")).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0),
                confirmed.inventory().items().get(new SubjectId("item:production-1-1-bread")).custody());
        assertEquals(observation, confirmed.physicalObservations().get(observation.id()));
        assertEquals(confirmed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmed)));
    }

    @Test
    void onlyTheTrustedPhysicalExecutorCanStartAnIntent() {
        var engine = FrontierEngines.create(materializedSupplyConfiguration(new WorldId("frontier:intent-command"), 91L));
        advanceUntil(engine, 12_000L, candidate -> candidate.physicalIntents()
                .containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(new PhysicalIntentId("intent:cargo-handoff-supply-1-2"), PhysicalIntentStatus.RUNNING, Optional.empty());
        var revision = engine.projection(ProjectionQuery.summary()).revision();
        var accepted = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:intent-start"), new WorldId("frontier:intent-command"), revision,
                engine.checkpoint().instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:intent-start")), transition));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, accepted,
                () -> "exact scene preparation must be accepted: " + accepted);
    }

    @Test
    void observedCargoHandoffCompletesItsTaskAndUnknownRecoveryBlocksIt() {
        var completedEngine = FrontierEngines.create(materializedSupplyConfiguration(new WorldId("frontier:supply-task-completed"), 91L));
        advanceUntil(completedEngine, 12_000L, candidate -> candidate.physicalIntents()
                .containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));
        PhysicalIntentId intentId = new PhysicalIntentId("intent:cargo-handoff-supply-1-2");
        submitPhysicalTransition(completedEngine, "frontier:supply-task-completed", "command:supply-running", intentId, PhysicalIntentStatus.RUNNING, Optional.empty());
        CargoHandoffObservation observation = new CargoHandoffObservation(new PhysicalObservationId("observation:supply-task-completed"), intentId,
                new SubjectId("cargo:supply-1-2"), List.of(new CargoHandoffPlacement(new SubjectId("item:production-1-1-bread"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0))));
        submitPhysicalTransition(completedEngine, "frontier:supply-task-completed", "command:supply-confirmed", intentId, PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(completedEngine.checkpoint().canonicalState());
        assertEquals(StrategicTaskStatus.COMPLETED, supplyTask(completed).status());
        assertEquals(StrategicObjectiveStatus.COMPLETED, completed.strategicPlans().objectives().get(supplyTask(completed).objectiveId()).status());

        var unknownEngine = FrontierEngines.create(materializedSupplyConfiguration(new WorldId("frontier:supply-task-unknown"), 91L));
        advanceUntil(unknownEngine, 12_000L, candidate -> candidate.physicalIntents()
                .containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));
        submitPhysicalTransition(unknownEngine, "frontier:supply-task-unknown", "command:supply-unknown", intentId, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        FrontierWorldState unknown = new FrontierWorldStateCodec().decode(unknownEngine.checkpoint().canonicalState());
        assertEquals(StrategicTaskStatus.BLOCKED, supplyTask(unknown).status());
        assertEquals(StrategicObjectiveStatus.BLOCKED, unknown.strategicPlans().objectives().get(supplyTask(unknown).objectiveId()).status());
    }

    @Test
    void sceneLeaseDurablySuspendsColdRouteProgressAndPinsExactFutureBodies() {
        WorldId world = new WorldId("frontier:scene-lease");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        var projection = engine.projection(ProjectionQuery.summary());
        SimInstant handoffInstant = engine.checkpoint().instant();
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:supply-1-2");
        List<SceneMember> members = operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(before.bootstrap().worldId(), actor))).toList();
        Map<SubjectId, BodyPosition> memberPositions = new LinkedHashMap<>();
        members.forEach(member -> memberPositions.put(member.actorId(), before.actorLocations().get(member.actorId()).body()));
        BlockPosition cargoPosition = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        SceneLease lease = SceneLease.atExactPositions(leaseId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.currentPosition(), cargoPosition, handoffInstant,
                projection.revision().value(), SceneLeaseStatus.PREPARED, Optional.empty(), members,
                memberPositions);
        Map<SubjectId, BodyPosition> uniformBodies = new LinkedHashMap<>();
        members.forEach(member -> uniformBodies.put(member.actorId(), new BodyPosition(operation.currentPosition().x(), operation.currentPosition().y(), operation.currentPosition().z())));
        SceneLease legacyUniformLease = SceneLease.atExactPositions(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:legacy-uniform"), before.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), cargoPosition, handoffInstant, projection.revision().value(), SceneLeaseStatus.PREPARED,
                Optional.empty(), members, uniformBodies);
        SceneLease wrongCargoLease = SceneLease.atExactPositions(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:wrong-cargo"), before.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), operation.currentPosition(), handoffInstant, projection.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.empty(), members, memberPositions);
        assertThrows(IllegalArgumentException.class, () -> before.prepareSceneLease(legacyUniformLease), "a uniform handoff point must not relocate a formation");
        assertThrows(IllegalArgumentException.class, () -> before.prepareSceneLease(wrongCargoLease), "a scene must retain the operation's exact cargo anchor");
        assertEquals(cargoPosition, FrontierSceneBehaviors.logistics(lease).cargoPosition());
        WorldId foreignWorld = new WorldId("frontier:foreign-scene-world");
        List<SceneMember> foreignMembers = operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(foreignWorld, actor))).toList();
        SceneLease foreignLease = SceneLease.atExactPositions(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:foreign-scene-world"), foreignWorld,
                operation.id(), operation.cargoId(), operation.currentPosition(), cargoPosition, handoffInstant, projection.revision().value(), SceneLeaseStatus.PREPARED,
                Optional.empty(), foreignMembers, memberPositions);
        assertThrows(IllegalArgumentException.class, () -> before.prepareSceneLease(foreignLease), "a scene lease from another world cannot share this world's canonical actors");
        SceneLeasePrepared payload = new SceneLeasePrepared(lease);
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-lease-start");
        var accepted = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, world,
                projection.revision(), handoffInstant, FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, accepted);

        FrontierWorldState leased = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(lease, leased.sceneLeases().get(leaseId));
        assertEquals(1, engine.projection(ProjectionQuery.summary()).activeSceneLeaseCount());
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        assertEquals(leased, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(leased)));
        engine.advanceTo(new SimInstant(handoffInstant.ticks() + 100L), new WorkBudget(64, 512));
        FrontierWorldState afterDueColdWork = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(0, afterDueColdWork.operations().get(operation.id()).routeIndex(), "a leased operation must not execute the same COLD movement");
        assertThrows(IllegalArgumentException.class, () -> leased.prepareSceneLease(lease));

        var hotCheckpoint = engine.checkpoint();
        var hotCommand = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-hot-death");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, hotCommand, world, hotCheckpoint.revision(), hotCheckpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(hotCommand),
                        new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT))));
        FrontierWorldState hotTravel = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        OperationTravel currentTravel = hotTravel.operations().get(operation.id()).activeTravel().orElseThrow();
        assertEquals(1, hotTravel.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).filter(value -> FrontierSceneBehaviors.logistics(value).operationId().equals(operation.id())
                && value.status() != SceneLeaseStatus.CLOSED).count());
        assertEquals(SceneLeaseStatus.HOT, hotTravel.sceneLeases().get(leaseId).status());
        assertEquals(java.util.Optional.empty(), FrontierSceneBehaviors.logistics(hotTravel.sceneLeases().get(leaseId)).engagementId());
        assertEquals(currentTravel.formation(), hotTravel.sceneLeases().get(leaseId).memberPositions(),
                "a prepared/HOT scene must retain the current exact operation formation before its first observation");
        assertEquals(currentTravel.cargoAnchor().surface().support(), FrontierSceneBehaviors.logistics(hotTravel.sceneLeases().get(leaseId)).cargoPosition(),
                "a prepared/HOT scene must retain the current exact operation cargo before its first observation");
        OperationTravel oneHotCell = translateTravel(currentTravel, currentTravel.nextHotCursor());
        var hotAdvance = submit(engine, world, "scene-exact-hot-travel", new OperationTravelAdvanced(operation.id(), oneHotCell));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, hotAdvance,
                () -> "exact HOT travel must rebase the matching lease: " + hotAdvance);
        FrontierWorldState advancedHotTravel = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SceneLease advancedLease = advancedHotTravel.sceneLeases().get(leaseId);
        assertEquals(oneHotCell, advancedHotTravel.operations().get(operation.id()).activeTravel().orElseThrow());
        assertEquals(oneHotCell.currentPosition(), advancedLease.handoffPosition());
        assertEquals(oneHotCell.formation(), advancedLease.memberPositions());
        assertEquals(oneHotCell.cargoAnchor().surface().support(), FrontierSceneBehaviors.logistics(advancedLease).cargoPosition());
        assertEquals(advancedHotTravel, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(advancedHotTravel)));
        if (currentTravel.nextColdCursor() > currentTravel.nextHotCursor()) {
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class,
                    submit(engine, world, "scene-oversized-hot-travel", new OperationTravelAdvanced(operation.id(),
                            translateTravel(oneHotCell, Math.min(oneHotCell.nextColdCursor(), oneHotCell.cursor() + 2)))));
        }
        SceneMember deadMember = lease.members().getFirst();
        ActorDied death = new ActorDied(leaseId, deadMember.actorId(), lease.memberPosition(deadMember.actorId()), "entity:player-test");
        var deathCheckpoint = engine.checkpoint();
        var deathCommand = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-actor-death");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, deathCommand, world, deathCheckpoint.revision(), deathCheckpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(deathCommand), death)));
        FrontierWorldState afterDeath = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(ActorLifeStatus.DEAD, afterDeath.actorLocations().get(deadMember.actorId()).condition().status());
        assertEquals(SceneLeaseStatus.DRAINING, afterDeath.sceneLeases().get(leaseId).status());
        assertEquals(before.bootstrap().residentCount() - 1, engine.projection(ProjectionQuery.summary()).residentCount());
        assertEquals(death, FrontierWorldRuntimeDefinition.payloadCodecs().decode(death.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(death)));
        List<SceneMemberPosition> surviving = lease.members().stream().filter(member -> !member.equals(deadMember)).map(member ->
                new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList();
        var releaseCheckpoint = engine.checkpoint();
        var releaseCommand = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-death-release");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, releaseCommand, world, releaseCheckpoint.revision(), releaseCheckpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(releaseCommand), new SceneLeaseReleased(leaseId, surviving))));
        FrontierWorldState afterDeathRelease = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(OperationStage.FAILED, afterDeathRelease.operations().get(operation.id()).stage());
        assertEquals(StrategicTaskStatus.BLOCKED, supplyTask(afterDeathRelease).status());

        FrontierWorldState hot = leased.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        FrontierWorldState draining = hot.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        List<SceneMemberPosition> captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                new BodyPosition(lease.handoffPosition().x() + 1, lease.handoffPosition().y(), lease.handoffPosition().z()), FixedScalar.whole(7))).toList();
        FrontierWorldState released = draining.releaseSceneLease(leaseId, captured);
        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(leaseId).status());
        assertEquals(hot.operations().get(operation.id()).activeTravel().orElseThrow().formation().get(captured.getFirst().actorId()).supportingSurface().support(),
                FrontierTestPositions.supportOf(released.actorLocations().get(captured.getFirst().actorId())));
        assertEquals(FixedScalar.whole(7), released.actorLocations().get(captured.getFirst().actorId()).condition().health());
        SceneLeaseReleased releasePayload = new SceneLeaseReleased(leaseId, captured);
        assertEquals(releasePayload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(releasePayload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(releasePayload)));
        assertThrows(IllegalArgumentException.class, () -> hot.releaseSceneLease(leaseId, captured));

        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> retained = new LinkedHashMap<>(before.sceneLeases());
        for (int index = 0; index < 1_024; index++) {
            var oldId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:terminal-" + index);
            retained.put(oldId, SceneLease.atExactPositions(oldId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.route().getFirst(), cargoPosition,
                    new SimInstant(index), index, SceneLeaseStatus.CLOSED, Optional.empty(), members, memberPositions));
        }
        FrontierWorldState retentionState = new FrontierWorldState(before.bootstrap(), before.actorLocations(), before.structureConditions(), before.infection(),
                before.inventory(), before.productionJobs(), before.contracts(), before.operations(), before.logisticsHistory(), before.physicalIntents(), before.physicalObservations(), retained,
                before.hiveColony(), before.structureDamage(), before.physicalDeltas(), before.ambientLeases(), before.routeConstructions(),
                before.routeTopology(), before.strategicPlans(), before.humanPopulation(), before.companies(), before.resourceSites());
        var nextLeaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:after-compaction");
        SceneLease nextLease = SceneLease.atExactPositions(nextLeaseId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.currentPosition(), cargoPosition, new SimInstant(551L),
                2_000L, SceneLeaseStatus.PREPARED, Optional.empty(), members, memberPositions);
        FrontierWorldState compacted = retentionState.prepareSceneLease(nextLease);
        assertEquals(1_024, compacted.sceneLeases().size()); assertTrue(compacted.sceneLeases().containsKey(nextLeaseId));
        assertTrue(!compacted.sceneLeases().containsKey(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:terminal-0")));
    }

    private static StrategicTask supplyTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE)
                .reduce((left, right) -> { throw new AssertionError("supply task must be unique in this fixture"); }).orElseThrow();
    }

    private static OperationTravel translateTravel(OperationTravel travel, int nextCursor) {
        BlockPosition from = travel.currentPosition(), to = travel.corridor().get(nextCursor);
        int deltaX = to.x() - from.x(), deltaZ = to.z() - from.z(); Map<SubjectId, BodyPosition> formation = new LinkedHashMap<>();
        travel.formation().forEach((actor, position) -> formation.put(actor, position.offset(deltaX, 0, deltaZ)));
        return travel.advance(nextCursor, formation, travel.cargoAnchor().offset(deltaX, 0, deltaZ));
    }

    private static StrategicTask productionTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD)
                .reduce((left, right) -> { throw new AssertionError("production task must be unique in this fixture"); }).orElseThrow();
    }

    private static StrategicTask preparationTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.PREPARE_BREAD_CARGO)
                .reduce((left, right) -> { throw new AssertionError("cargo preparation task must be unique in this fixture"); }).orElseThrow();
    }

    private static void submitPhysicalTransition(FrontierEngine<FrontierWorldProjection> engine, String world, String command, PhysicalIntentId intentId,
                                                 PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation) {
        var checkpoint = engine.checkpoint(); var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId(command);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                commandId, new WorldId(world), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                        io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), new PhysicalIntentTransition(intentId, status, observation))));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                                                                   String command, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint(); var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:" + command.replace(':', '-'));
        return engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload));
    }

    private static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
    materializedSupplyConfiguration(WorldId world, long seed) {
        var base = FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(world, seed);
        SubjectId westStore = new SubjectId("container:hive-west-store");
        FrontierWorldState state = base.initialState().withInventory(base.initialState().inventory()
                .withSurfaceStatus(westStore, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(westStore, ContainerSurfaceStatus.ACTIVE));
        return new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter());
    }

    /** Mirrors the real server's one-tick cadence and waits for a durable domain result. */
    private static FrontierWorldState advanceUntil(FrontierEngine<FrontierWorldProjection> engine, long latestTick,
                                                   Predicate<FrontierWorldState> terminal) {
        long firstTick = Math.addExact(engine.checkpoint().instant().ticks(), 1L);
        for (long tick = firstTick; tick <= latestTick; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            if (tick % 20L != 0L && tick != latestTick) continue;
            FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            if (terminal.test(state)) return state;
        }
        FrontierWorldState finalState = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        throw new AssertionError("terminal Frontier state was not reached by tick " + latestTick + "; operations=" + finalState.operations()
                + "; physicalDeltas=" + finalState.physicalDeltas() + "; intents=" + finalState.physicalIntents().keySet());
    }
}
