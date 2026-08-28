package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
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
        assertEquals(2, projection.itemStackCount());
        assertEquals(0, projection.activeProductionJobCount());
        assertEquals(2, projection.infectedCellCount());
    }

    @Test
    void exactStructuralDamageIsDurableAndDerivesItsConditionFromKnownCells() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:structure-damage"), 91L));
        SettlementStructure structure = state.bootstrap().settlements().getFirst().structures().getFirst();
        List<GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().equals(structure.id())).sorted(java.util.Comparator
                        .comparingInt((GrayboxCell cell) -> cell.position().x()).thenComparingInt(cell -> cell.position().y())
                        .thenComparingInt(cell -> cell.position().z())).toList();
        int destructiveThreshold = (FrontierGrayboxPlan.intactStructureCellCount(structure) + 2) / 3;
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
    void sharedHiveGrowthConsumesEastStoreBiomassThenPublishesWestOrganAndBioform() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:hive-growth"), 91L));
        for (long tick = 600L; tick <= 640L; tick++) engine.advanceTo(new SimInstant(tick), new WorkBudget(32, 256));
        FrontierWorldState active = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        HiveGrowthJob job = active.hiveColony().growthJobs().get(new SubjectId("job:hive-growth-1"));
        assertEquals(new SubjectId("item:bootstrap-hive-biomass"), job.consumedItemId());
        assertTrue(!active.inventory().items().containsKey(job.consumedItemId()));
        HiveGrowthStarted started = new HiveGrowthStarted(job);
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));

        for (long tick = 800L; tick <= 840L; tick++) engine.advanceTo(new SimInstant(tick), new WorkBudget(32, 256));
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(completed.hiveColony().growthJobs().isEmpty());
        assertEquals(job.organ(), completed.hiveColony().addedOrgans().get(job.organ().id()));
        assertEquals(job.bioform(), completed.hiveColony().spawnedBioforms().get(job.bioform().id()));
        assertEquals(49, engine.projection(ProjectionQuery.summary()).bioformCount());
        HiveGrowthBlocked blocked = new HiveGrowthBlocked(job.hiveId(), job.nestId(), new SubjectId("work:hive-growth-2"), HiveGrowthBlockReason.BIOMASS_UNAVAILABLE);
        assertEquals(blocked, FrontierWorldRuntimeDefinition.payloadCodecs().decode(blocked.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(blocked)));
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
    void infectionPulseIsPersistedDeterministicScheduledWorldWork() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:pulse"), 91L));
        engine.advanceTo(new SimInstant(100L), new WorkBudget(8, 64));

        FrontierWorldProjection projection = engine.projection(ProjectionQuery.summary());
        assertEquals(3, projection.infectedCellCount());
        assertEquals(1L, projection.revision().value());
    }

    @Test
    void longLivedPulsesKeepTheirBoundedWorkCostInsteadOfGrowingIntoTheSchedulerBudget() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:long-pulse"), 91L));
        for (long tick = 100L; tick <= 60_000L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertTrue(engine.projection(ProjectionQuery.summary()).revision().value() >= 600L);
    }

    @Test
    void productionConsumesARealInputThenStoresOnlyItsDurableExactOutput() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production"), 91L));

        // A newly scheduled same-instant pulse remains ahead in the deterministic queue, so the
        // facility start is admitted on the following server tick rather than being overtaken.
        engine.advanceTo(new SimInstant(200L), new WorkBudget(8, 64));
        engine.advanceTo(new SimInstant(201L), new WorkBudget(8, 64));
        FrontierWorldState started = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(1, started.inventory().items().size());
        assertEquals(1, started.productionJobs().size());
        ProductionJob job = started.productionJobs().values().iterator().next();
        assertEquals(new SubjectId("item:bootstrap-1-wheat"), job.consumedItemId());

        engine.advanceTo(new SimInstant(300L), new WorkBudget(8, 64));
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(completed.productionJobs().isEmpty());
        ExactItemStack bread = completed.inventory().items().get(new SubjectId("item:production-1-1-bread"));
        assertEquals("minecraft:bread", bread.itemKind());
        assertEquals(64, bread.count());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0), bread.custody());
    }

    @Test
    void completionBlocksWhenAPlayerOrAnotherProcessHasFilledEveryExactDepotSlot() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:storage"), 91L));
        SubjectId depot = new SubjectId("container:1-depot");
        Map<SubjectId, ExactItemStack> fullItems = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            SubjectId id = new SubjectId("item:storage-" + slot);
            fullItems.put(id, new ExactItemStack(id, "minecraft:cobblestone", 64, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        ExactInventory fullInventory = new ExactInventory(baseline.inventory().containers(), fullItems, Map.of(), Map.of(), Map.of(), Map.of(), baseline.inventory().surfaces());
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-99"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:consumed-99"),
                new SubjectId("item:production-1-99-bread"), "minecraft:bread", 64);
        FrontierWorldState blocked = baseline.withInventory(fullInventory).withProductionJob(job);

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(blocked,
                new ScheduledAction(new ScheduleId("schedule:production-complete-production-1-99"), new SimInstant(500L), 0, job.id(), "frontier.settlement.production.complete", 1));

        ProductionBlocked event = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        assertEquals(ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE, event.reason());
        assertEquals(job.id(), event.workId());
    }

    @Test
    void productionRefusesToStartWithoutAnExactInputStack() {
        FrontierWorldState withoutInput = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:input"), 91L))
                .withInventory(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:input"), 91L)).inventory().withoutItem(new SubjectId("item:bootstrap-1-wheat")));

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(withoutInput,
                new ScheduledAction(new ScheduleId("schedule:production-start-1-1"), new SimInstant(200L), 0, new SubjectId("settlement:1"), "frontier.settlement.production.start", 1));

        ProductionBlocked event = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        assertEquals(ProductionBlockReason.INPUT_UNAVAILABLE, event.reason());
    }

    @Test
    void productionPayloadsRoundTripAsPersistedTypedFacts() {
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-1"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:bootstrap-1-wheat"),
                new SubjectId("item:production-1-1-bread"), "minecraft:bread", 64);
        ProductionCompleted completed = new ProductionCompleted(job.id(), new ExactItemStack(job.outputItemId(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0)));

        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(completed, codecs.decode(completed.type(), codecs.encode(completed)));
        ProductionStarted started = new ProductionStarted(job, job.consumedItemId());
        assertEquals(started, codecs.decode(started.type(), codecs.encode(started)));
        SupplyContract contract = new SupplyContract(new SubjectId("contract:supply-1-1"), new SubjectId("settlement:1"),
                new SubjectId("hive:frontier"), new SubjectId("cargo:supply-1-1"), "minecraft:bread", 64, ContractStatus.ORDERED);
        SupplyContractCreated created = new SupplyContractCreated(contract);
        assertEquals(created, codecs.decode(created.type(), codecs.encode(created)));
        CargoLoaded loaded = new CargoLoaded(contract.id(), new CargoBatch(contract.cargoId(), contract.settlementId(), List.of(job.outputItemId())));
        assertEquals(loaded, codecs.decode(loaded.type(), codecs.encode(loaded)));
        RouteOperation operation = new RouteOperation(new SubjectId("operation:supply-1-1"), contract.settlementId(), contract.cargoId(), contract.recipientId(),
                List.of(new SubjectId("resident:1-6"), new SubjectId("resident:1-4")), List.of(new BlockPosition(-360, 64, -340), new BlockPosition(-420, 64, 420)), 0, OperationStage.EN_ROUTE);
        OperationCreated operationCreated = new OperationCreated(operation);
        OperationAdvanced operationAdvanced = new OperationAdvanced(operation.id(), 1, OperationStage.ARRIVED);
        assertEquals(operationCreated, codecs.decode(operationCreated.type(), codecs.encode(operationCreated)));
        assertEquals(operationAdvanced, codecs.decode(operationAdvanced.type(), codecs.encode(operationAdvanced)));
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
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:cargo"), 91L));
        for (long tick = 100L; tick <= 500L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SupplyContract contract = state.contracts().get(new SubjectId("contract:supply-1-1"));
        assertEquals(ContractStatus.LOADED, contract.status());
        CargoBatch cargo = state.inventory().cargo().get(contract.cargoId());
        assertEquals(List.of(new SubjectId("item:production-1-1-bread")), cargo.itemIds());
        assertEquals(new InventoryCustody.Cargo(cargo.id()), state.inventory().items().get(cargo.itemIds().getFirst()).custody());
    }

    @Test
    void loadedCargoMovesThroughAPersistedColdRouteWithExactParticipants() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:route"), 91L));
        for (long tick = 100L; tick <= 1_500L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-1"));
        assertEquals(OperationStage.ARRIVED, operation.stage());
        assertEquals(operation.route().size() - 1, operation.routeIndex());
        BlockPosition destination = operation.route().getLast();
        assertTrue(operation.participantIds().stream().allMatch(participant -> destination.equals(state.actorLocations().get(participant).position())));
        assertEquals(1, engine.projection(ProjectionQuery.summary()).activeRouteOperationCount());
        assertEquals(1, engine.projection(ProjectionQuery.summary()).preparedPhysicalIntentCount());
        PhysicalIntent handoff = state.physicalIntents().get(new PhysicalIntentId("intent:cargo-handoff-supply-1-1"));
        assertEquals(PhysicalIntentStatus.PREPARED, handoff.status());
        assertEquals(List.of(operation.id(), operation.cargoId()), handoff.subjectIds());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void routeReducerRejectsASkippedRoutePoint() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:route-negative"), 91L));
        for (long tick = 100L; tick <= 500L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-1"));

        assertThrows(IllegalArgumentException.class, () -> state.advanceOperation(operation.id(), 2, OperationStage.EN_ROUTE));
    }

    @Test
    void physicalIntentTransactionIsFlushedBeforeAnExecutorCouldObserveIt() {
        List<Durability> durabilities = new ArrayList<>();
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:durability"), 91L)
                .withTransactionCommitter((transaction, durability) -> durabilities.add(durability));
        var engine = FrontierEngines.create(configuration);
        for (long tick = 100L; tick <= 1_500L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));

        assertTrue(durabilities.contains(Durability.BATCHABLE));
        assertTrue(durabilities.contains(Durability.DURABLE_BEFORE_EFFECT));
    }

    @Test
    void physicalIntentRequiresSequentialExecutionAndAnObservedPostcondition() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:intent-lifecycle"), 91L));
        for (long tick = 100L; tick <= 1_500L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));
        FrontierWorldState prepared = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        PhysicalIntentId id = new PhysicalIntentId("intent:cargo-handoff-supply-1-1");
        CargoHandoffObservation observation = new CargoHandoffObservation(new PhysicalObservationId("observation:cargo-handoff-supply-1-1"), id,
                new SubjectId("cargo:supply-1-1"), List.of(new CargoHandoffPlacement(new SubjectId("item:production-1-1-bread"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0))));

        assertThrows(IllegalArgumentException.class, () -> prepared.transitionPhysicalIntent(id, PhysicalIntentStatus.CONFIRMED,
                Optional.of(observation)));
        FrontierWorldState running = prepared.transitionPhysicalIntent(id, PhysicalIntentStatus.RUNNING, Optional.empty());
        CargoHandoffObservation foreignStore = new CargoHandoffObservation(new PhysicalObservationId("observation:cargo-handoff-foreign-store"), id,
                new SubjectId("cargo:supply-1-1"), List.of(new CargoHandoffPlacement(new SubjectId("item:production-1-1-bread"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0))));
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(id, PhysicalIntentStatus.CONFIRMED, Optional.of(foreignStore)));
        FrontierWorldState confirmed = running.transitionPhysicalIntent(id, PhysicalIntentStatus.CONFIRMED,
                Optional.of(observation));
        assertEquals(PhysicalIntentStatus.CONFIRMED, confirmed.physicalIntents().get(id).status());
        assertEquals(Optional.of(new PhysicalObservationId("observation:cargo-handoff-supply-1-1")), confirmed.physicalIntents().get(id).postconditionObservationId());
        assertEquals(ContractStatus.DELIVERED, confirmed.contracts().get(new SubjectId("contract:supply-1-1")).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0),
                confirmed.inventory().items().get(new SubjectId("item:production-1-1-bread")).custody());
        assertEquals(observation, confirmed.physicalObservations().get(observation.id()));
        assertEquals(confirmed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmed)));
    }

    @Test
    void onlyTheTrustedPhysicalExecutorCanStartAnIntent() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:intent-command"), 91L));
        for (long tick = 100L; tick <= 1_500L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(new PhysicalIntentId("intent:cargo-handoff-supply-1-1"), PhysicalIntentStatus.RUNNING, Optional.empty());
        var revision = engine.projection(ProjectionQuery.summary()).revision();
        var accepted = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:intent-start"), new WorldId("frontier:intent-command"), revision,
                new SimInstant(1_500L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:intent-start")), transition));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, accepted);
    }

    @Test
    void sceneLeaseDurablySuspendsColdRouteProgressAndPinsExactFutureBodies() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:scene-lease"), 91L));
        for (long tick = 100L; tick <= 550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(8, 64));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-1"));
        var projection = engine.projection(ProjectionQuery.summary());
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:supply-1-1");
        SceneLease lease = new SceneLease(leaseId, operation.id(), operation.cargoId(), operation.route().getFirst(), new SimInstant(550L), projection.revision().value(),
                SceneLeaseStatus.PREPARED, operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(leaseId, actor))).toList());
        SceneLeasePrepared payload = new SceneLeasePrepared(lease);
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-lease-start");
        var accepted = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, new WorldId("frontier:scene-lease"),
                projection.revision(), new SimInstant(550L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, accepted);

        FrontierWorldState leased = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(lease, leased.sceneLeases().get(leaseId));
        assertEquals(1, engine.projection(ProjectionQuery.summary()).activeSceneLeaseCount());
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        assertEquals(leased, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(leased)));
        engine.advanceTo(new SimInstant(650L), new WorkBudget(8, 64));
        FrontierWorldState afterDueColdWork = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(0, afterDueColdWork.operations().get(operation.id()).routeIndex(), "a leased operation must not execute the same COLD movement");
        assertThrows(IllegalArgumentException.class, () -> leased.prepareSceneLease(lease));

        var hotCheckpoint = engine.checkpoint();
        var hotCommand = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-hot-death");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, hotCommand, new WorldId("frontier:scene-lease"), hotCheckpoint.revision(), hotCheckpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(hotCommand),
                        new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT))));
        SceneMember deadMember = lease.members().getFirst();
        ActorDied death = new ActorDied(leaseId, deadMember.actorId(), lease.handoffPosition(), "entity:player-test");
        var deathCheckpoint = engine.checkpoint();
        var deathCommand = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-actor-death");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, deathCommand, new WorldId("frontier:scene-lease"), deathCheckpoint.revision(), deathCheckpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(deathCommand), death)));
        FrontierWorldState afterDeath = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(ActorLifeStatus.DEAD, afterDeath.actorLocations().get(deadMember.actorId()).condition().status());
        assertEquals(SceneLeaseStatus.DRAINING, afterDeath.sceneLeases().get(leaseId).status());
        assertEquals(before.bootstrap().residentCount() - 1, engine.projection(ProjectionQuery.summary()).residentCount());
        assertEquals(death, FrontierWorldRuntimeDefinition.payloadCodecs().decode(death.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(death)));
        List<SceneMemberPosition> surviving = lease.members().stream().filter(member -> !member.equals(deadMember)).map(member ->
                new SceneMemberPosition(member.actorId(), lease.handoffPosition())).toList();
        var releaseCheckpoint = engine.checkpoint();
        var releaseCommand = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:scene-death-release");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, releaseCommand, new WorldId("frontier:scene-lease"), releaseCheckpoint.revision(), releaseCheckpoint.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(releaseCommand), new SceneLeaseReleased(leaseId, surviving))));
        FrontierWorldState afterDeathRelease = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(OperationStage.FAILED, afterDeathRelease.operations().get(operation.id()).stage());

        FrontierWorldState hot = leased.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        FrontierWorldState draining = hot.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        List<SceneMemberPosition> captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                new BlockPosition(lease.handoffPosition().x() + 1, lease.handoffPosition().y(), lease.handoffPosition().z()), FixedScalar.whole(7))).toList();
        FrontierWorldState released = draining.releaseSceneLease(leaseId, captured);
        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(leaseId).status());
        assertEquals(captured.getFirst().position(), released.actorLocations().get(captured.getFirst().actorId()).position());
        assertEquals(FixedScalar.whole(7), released.actorLocations().get(captured.getFirst().actorId()).condition().health());
        SceneLeaseReleased releasePayload = new SceneLeaseReleased(leaseId, captured);
        assertEquals(releasePayload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(releasePayload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(releasePayload)));
        assertThrows(IllegalArgumentException.class, () -> hot.releaseSceneLease(leaseId, captured));

        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> retained = new LinkedHashMap<>(released.sceneLeases());
        for (int index = 0; index < 1_023; index++) {
            var oldId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:terminal-" + index);
            retained.put(oldId, new SceneLease(oldId, operation.id(), operation.cargoId(), operation.route().getFirst(), new SimInstant(index), index,
                    SceneLeaseStatus.CLOSED, operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(oldId, actor))).toList()));
        }
        FrontierWorldState retentionState = new FrontierWorldState(before.bootstrap(), before.actorLocations(), released.structureConditions(), released.infection(),
                released.inventory(), released.productionJobs(), released.contracts(), released.operations(), released.physicalIntents(), released.physicalObservations(), retained);
        var nextLeaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:after-compaction");
        SceneLease nextLease = new SceneLease(nextLeaseId, operation.id(), operation.cargoId(), operation.route().getFirst(), new SimInstant(551L), 2_000L,
                SceneLeaseStatus.PREPARED, operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(nextLeaseId, actor))).toList());
        FrontierWorldState compacted = retentionState.prepareSceneLease(nextLease);
        assertEquals(1_024, compacted.sceneLeases().size());
        assertTrue(compacted.sceneLeases().containsKey(nextLeaseId));
        assertTrue(!compacted.sceneLeases().containsKey(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:terminal-0")));
    }
}
