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
        assertEquals(projection.settlementCount() * EngineeringRecoveryTeam.MAX_MEMBERS, projection.itemStackCount(),
                "ordinary bootstrap wheat and hive biomass are fungible lots, not permanent exact stacks");
        assertEquals(0, projection.activeProductionJobCount());
        assertEquals(18, projection.infectedCellCount());
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
                java.util.Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, organ.id())), java.util.Optional.of(organCell.semanticPart()), "explosion:test"));
        assertTrue(!FrontierGrayboxPlan.compile(organDamaged).cells().containsKey(organCell.position()));

        List<BlockPosition> supplyRoute = FrontierRouteNetwork.settlementWaypoints(initial.bootstrap(), initial.bootstrap().settlements().getFirst().id());
        BlockPosition routePosition = supplyRoute.get(2);
        PhysicalDelta routeLoss = new PhysicalDelta(routePosition, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test");
        FrontierWorldState routeDamaged = initial.recordPhysicalDelta(routeLoss);
        PhysicalDeltaObserved observed = (PhysicalDeltaObserved) FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.physical_delta_observed",
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new PhysicalDeltaObserved(routeLoss)));
        assertEquals(routeLoss, observed.delta());
        assertTrue(!FrontierGrayboxPlan.compile(routeDamaged).cells().containsKey(routePosition));
        assertTrue(!FrontierRouteNetwork.isPassable(initial.bootstrap(), supplyRoute, routeDamaged.physicalDeltas()));
    }

    @Test
    void hiveGrowthConsumesNestLocalEastStoreBiomassThenPublishesEastOrganAndBioform() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:hive-growth"), 91L));
        FrontierWorldState completed = advanceUntil(engine, 12_000L,
                state -> state.hiveColony().growthJobs().containsKey(new SubjectId("job:hive-growth-1")));
        SubjectId biomass = new SubjectId("item:bootstrap-hive-biomass");
        assertTrue(!completed.inventory().items().containsKey(biomass), "COLD growth consumes its exact inactive-store biomass before completion");
        HiveGrowthJob job = new HiveGrowthJob(new SubjectId("job:hive-growth-1"), completed.hiveColony().growthJobs().get(new SubjectId("job:hive-growth-1")).taskId(), completed.bootstrap().hive().id(),
                new SubjectId("nest:seed-east"), biomass,
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:hive-growth-biomass-1"),
                new HiveOrgan(new SubjectId("organ:east-grown-heart-1"), completed.bootstrap().hive().id(), new SubjectId("nest:seed-east"),
                        HiveOrganKind.RELAY, new BlockPosition(432, 64, 432), java.util.Optional.empty()),
                new Bioform(new SubjectId("bioform:east-grown-1"), completed.bootstrap().hive().id(), new SubjectId("nest:seed-east"),
                        BioformChassis.RUNT, java.util.Set.of(BioformMutation.ARMORED), BioformAssignment.DEFEND, new BlockPosition(436, 64, 432)));
        HiveGrowthStarted started = new HiveGrowthStarted(job);
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        HiveGrowthBiomassConsumed consumed = new HiveGrowthBiomassConsumed(job.id(), biomass);
        assertEquals(consumed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(consumed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(consumed)));
        assertEquals(1, completed.hiveColony().growthJobs().size());
        assertEquals(48, engine.projection(ProjectionQuery.summary()).bioformCount(), "without a materialized receipt growth must not fabricate biomass outputs");
        HiveGrowthBlocked blocked = HiveGrowthDiagnosticProducer.BIOMASS_UNAVAILABLE.create(job.hiveId(), job.nestId(), new SubjectId("work:hive-growth-2"));
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
    void developmentHiveNutrientProfileKeepsOneFungibleBiomassBindingAtItsPreparedSourceUntilObservedDeparture() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.hiveNutrientTransferConfiguration(
                new WorldId("frontier:hive-nutrient-profile"), 91L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId transferId = new SubjectId("transfer:hive-nutrient-task-development-hive-nutrient-transfer");

        assertEquals(ContainerSurfaceStatus.PREPARED, initial.inventory().surfaces().get(new SubjectId("container:hive-east-store")).status());
        assertEquals(ContainerSurfaceStatus.PREPARED, initial.inventory().surfaces().get(new SubjectId("container:hive-west-store")).status());
        SubjectId biomass = new SubjectId("lot:bootstrap-hive-biomass");
        SubjectId sourceAccount = new SubjectId("custody:container-hive-east-store");
        assertEquals(64, initial.inventory().fungibleResources().accounts().get(sourceAccount).lotQuantities().get(biomass));
        engine.advanceTo(new SimInstant(1L), new WorkBudget(32, 256));
        FrontierWorldState pending = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        HiveNutrientTransfer transfer = pending.hiveColony().nutrientTransfers().get(transferId);
        assertEquals(HiveNutrientTransferPhase.DEPARTURE_PENDING, transfer.phase());
        assertEquals(PhysicalIntentStatus.PREPARED, pending.physicalIntents().get(transfer.endpointIntentId().orElseThrow()).status());
        assertEquals(64, pending.inventory().fungibleResources().accounts().get(sourceAccount).lotQuantities().get(transfer.itemId()),
                "fixture must not fabricate COLD cargo before a loaded fungible departure");
        assertTrue(pending.inventory().fungibleResources().bindings().values().stream()
                .anyMatch(binding -> binding.accountId().equals(sourceAccount)), "the physical source remains fenced until its receipt");
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
        SubjectId itemId = new SubjectId("item:bootstrap-1-engineering-tool-1");
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
        SubjectId itemId = new SubjectId("item:bootstrap-1-engineering-tool-1");
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
        SubjectId itemId = new SubjectId("item:bootstrap-1-engineering-tool-1");
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) before.inventory().items().get(itemId).custody();
        SubjectId containerId = source.containerId();
        InventoryConflict conflict = InventoryDiagnosticProducer.PLAYER_EXPECTED_SLOT_MISSING.create(new SubjectId("conflict:inventory-bootstrap-tool"), itemId, containerId, source.slot());
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:inventory-conflict");

        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, worldId, checkpoint.revision(), checkpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId),
                        new InventoryConflictObserved(conflict))));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(conflict, after.inventory().conflicts().get(conflict.id()));
        assertEquals(source, after.inventory().items().get(itemId).custody());
        assertEquals(1, engine.projection(ProjectionQuery.summary()).inventoryConflictCount());

        InventoryConflict foreign = InventoryDiagnosticProducer.PLAYER_FOREIGN_OR_DUPLICATE_SLOT.create(new SubjectId("conflict:foreign"), new SubjectId("item:untracked"), containerId, 1);
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
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:pulse"), 91L));
        FrontierWorldState state = advanceUntil(engine, 12_000L, candidate -> candidate.strategicPlans().tasks().values().stream()
                .anyMatch(task -> task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM && task.status() == StrategicTaskStatus.ACTIVE));

        FrontierWorldProjection projection = engine.projection(ProjectionQuery.summary());
        assertEquals(18, projection.infectedCellCount());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM).findFirst().orElseThrow().status());
    }

    @Test
    void longLivedPulsesKeepTheirBoundedWorkCostInsteadOfGrowingIntoTheSchedulerBudget() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:long-pulse"), 91L));
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
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-1"), new SubjectId("task:production-1-1"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:bootstrap-1-wheat"),
                new SubjectId("item:production-1-1-bread"), "minecraft:bread", 64);
        ProductionCompleted completed = new ProductionCompleted(job.id(), new ExactItemStack(job.outputItemId(), job.settlementId(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0)));

        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(completed, codecs.decode(completed.type(), codecs.encode(completed)));
        ProductionStarted started = new ProductionStarted(job, job.consumedItemId());
        assertEquals(started, codecs.decode(started.type(), codecs.encode(started)));
        byte[] withoutDeclaredInputHold = codecs.encode(started);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(started.type(),
                java.util.Arrays.copyOf(withoutDeclaredInputHold, withoutDeclaredInputHold.length - 1)),
                "a current-format production start cannot infer a missing input-hold type");

    }


    private static StrategicTask productionTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD)
                .reduce((left, right) -> { throw new AssertionError("production task must be unique in this fixture"); }).orElseThrow();
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


    /** The fixture itself may finish assembly near tick 12,000; bound travel from admission. */

    private static FungibleCargoHandoffObservation fungibleCargoObservation(String observationId, PhysicalIntentId intentId,
                                                                              SubjectId cargoId, SubjectId receiver) {
        return new FungibleCargoHandoffObservation(new PhysicalObservationId(observationId), intentId, cargoId, 1L,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(receiver, 0)),
                        "minecraft:bread", 64)));
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
        throw new AssertionError("terminal Frontier state was not reached from tick " + firstTick + " by " + latestTick
                + "; physicalDeltas=" + finalState.physicalDeltas().keySet() + "; intents=" + finalState.physicalIntents().keySet());
    }
}
