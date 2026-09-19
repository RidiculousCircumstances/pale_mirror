package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProcessLifecycleTest extends ProductionProcessTest {
    @Test
    void nextProductionJobRetainsACompletedWorkersWorkshopReturnCorridor() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-return"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResidentProfile worker = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())
                        && resident.profession() == ResidentProfession.INDUSTRIAL_WORKER)
                .findFirst().orElseThrow();
        SettlementStructure workshop = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP)
                .findFirst().orElseThrow();
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);

        FrontierWorldState completed = state.withActorBody(worker.id(), port.workStation().standingBody());
        List<SurfaceAnchor> corridor = ProductionWorkTraversal.compile(completed, workshop, worker.id(),
                completed.actorLocations().get(worker.id()), new SubjectId("job:production-return")).linearCorridorSurfaces();

        assertEquals(port.workStation(), corridor.getFirst());
        assertEquals(port.inputStation(), corridor.get(corridor.size() - 2));
        assertEquals(port.workStation(), corridor.getLast());
        assertTrue(corridor.stream().filter(port.workStation()::equals).count() > 1,
                "the exact completed worker may revisit its semantic work station without a replacement or reroute");
    }

    @Test
    void coldProductionCorridorAvoidsAnotherLivingActorsRetainedSupportBeforeHotAdmission() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-occupancy"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResidentProfile worker = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())
                        && resident.profession() == ResidentProfession.INDUSTRIAL_WORKER)
                .findFirst().orElseThrow();
        SettlementStructure workshop = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP)
                .findFirst().orElseThrow();
        SubjectId jobId = new SubjectId("job:production-occupancy");
        TraversalTopology unobstructed = ProductionWorkTraversal.compile(state.bootstrap(), workshop, state.actorLocations().get(worker.id()), jobId);
        SurfaceAnchor occupiedSurface = unobstructed.linearCorridorSurfaces().get(1);
        SubjectId idleResident = state.humanPopulation().residents().keySet().stream().filter(id -> !id.equals(worker.id())).findFirst().orElseThrow();

        FrontierWorldState occupied = state.withActorBody(idleResident, occupiedSurface.standingBody());
        TraversalTopology planned = ProductionWorkTraversal.compile(occupied, workshop, worker.id(), occupied.actorLocations().get(worker.id()), jobId);

        assertEquals(unobstructed.linearCorridorSurfaces().getFirst(), planned.linearCorridorSurfaces().getFirst());
        assertFalse(planned.linearCorridorSurfaces().contains(occupiedSurface),
                "a future HOT worker must not inherit a living-body collision as a retained edge");
    }

    @Test
    void coldFungibleProductionKeepsOneReservedLotUntilItTransformsIntoOneOutputLot() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fungible-production"), 91L));
        SubjectId settlement = new SubjectId("settlement:1"), depot = FrontierWorldState.depotId(settlement);
        SubjectId lotId = new SubjectId("lot:bootstrap-1-wheat"), accountId = new SubjectId("custody:container-1-depot");
        ExactInventory inventory = initial.inventory();
        FrontierWorldState pending = productionTask(initial.withInventory(inventory), StrategicTaskStatus.PENDING);
        StrategicTask task = pending.strategicPlans().tasks().values().iterator().next();
        List<ProposedEvent> planned = ProductionProcess.planStart(pending, ProductionProcess.start(task, 100L));
        ProductionStarted started = planned.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        ProductionInputHold.FungibleCold hold = assertInstanceOf(ProductionInputHold.FungibleCold.class, started.job().inputHold());
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(pending, settlement,
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        FrontierWorldState reserved = ProductionProcess.reduceStarted(active, settlement, started);
        assertEquals(64, reserved.inventory().fungibleResources().accounts().get(accountId).claimQuantities().get(hold.claimId()));
        List<ProposedEvent> completion = ProductionProcess.planCompletion(reserved, ProductionProcess.complete(started.job(), 200L));
        FungibleProductionCompleted completed = assertInstanceOf(FungibleProductionCompleted.class, completion.getFirst().payload());
        FrontierWorldState transformed = ProductionProcess.reduceFungibleCompleted(reserved, settlement, completed);
        assertTrue(transformed.productionJobs().isEmpty());
        assertFalse(transformed.inventory().items().containsKey(completed.output().id()));
        assertEquals(64, transformed.inventory().fungibleResources().totalQuantity(settlement, "minecraft:bread"));
        assertFalse(transformed.inventory().fungibleResources().claims().containsKey(hold.claimId()));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceFungibleCompleted(reserved, settlement,
                new FungibleProductionCompleted(started.job().id(), new ResourceLot(new SubjectId("lot:forged-bread"), settlement,
                        "minecraft:bread", 63, "recipe:bread", List.of(lotId)))));
    }

    @Test
    void workProgressSurvivesSnapshotAndStartedPayloadWithoutUsingEnumOrder() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ProductionJob working = prepared.job().withWorkProgress(ProductionWorkProgress.processing(37));
        FrontierWorldState workingState = prepared.state().withChanges(FrontierWorldStateUpdate.begin()
                .productionJobs(java.util.Map.of(working.id(), working)));
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(workingState));
        assertEquals(ProductionWorkProgress.processing(37), restored.productionJobs().get(working.id()).workProgress());
        assertEquals(working.workTraversal(), restored.productionJobs().get(working.id()).workTraversal());
        assertEquals(working.traversalCursor(), restored.productionJobs().get(working.id()).traversalCursor());

        ProductionStarted started = new ProductionStarted(working, working.consumedItemId());
        ProductionStarted decoded = (ProductionStarted) FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(started));
        assertEquals(ProductionWorkProgress.processing(37), decoded.job().workProgress());
    }

    @Test
    void releasedMaterializedProductionTransfersToColdAndCompletesBeforeIngress() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream()
                .filter(value -> value.id().equals(prepared.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop,
                worker, prepared.job().id()), 0);
        FrontierWorldState state = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of(job.id(), job)));
        ScheduledAction[] action = { ProductionProcess.complete(job, 1_000L) };

        boolean reachedOutputReady = false;
        boolean transferredToCold = false;
        while (state.productionJobs().containsKey(job.id())) {
            List<ProposedEvent> planned = ProductionProcess.planCompletion(state, action[0]);
            if (planned.getFirst().payload() instanceof ProductionColdWorkAdvanced advanced) {
                state = ProductionProcess.reduceColdWorkAdvanced(state, prepared.settlementId(), advanced);
                reachedOutputReady |= state.productionJobs().get(job.id()).workProgress().terminalEffectEligible();
                transferredToCold |= state.productionJobs().get(job.id()).inputHold() instanceof ProductionInputHold.Cold;
                action[0] = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.get(1).payload()).replacement();
            } else {
                ProductionCompleted completion = assertInstanceOf(ProductionCompleted.class, planned.getFirst().payload());
                state = ProductionProcess.reduceCompleted(state, prepared.settlementId(), completion);
            }
        }
        assertTrue(reachedOutputReady, "the retained COLD route reaches its output station before semantic completion");
        assertTrue(transferredToCold, "release transfers the exact materialized input to COLD rather than retaining an observer lease");
        assertEquals(job.workTraversal().linearCorridorSurfaces().getLast().standingBody(),
                state.actorLocations().get(job.workerId()).body(), "first visibility retains the current workshop station, not the route origin");
        assertTrue(state.sceneLeases().isEmpty(), "COLD owns the semantic route without a synthetic physical worker");
        assertTrue(state.productionJobs().isEmpty(), "COLD terminal ownership may not wait for a physical transformation observation");
        assertEquals("minecraft:bread", state.inventory().items().get(job.outputItemId()).itemKind());
        assertFalse(state.inventory().items().containsKey(job.consumedItemId()), "the exact wheat is consumed once into the retained bread result");
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state.inventory().items().get(job.outputItemId()), recovered.inventory().items().get(job.outputItemId()),
                "restart retains the exact COLD terminal output without a physical transformation receipt");
    }

    @Test
    void coldProductionStillAdvancesWithoutMaterializingAnUnloadedContainer() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production"), 91L));
        for (long tick = 100L; tick <= 2_200L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(completed.productionJobs().isEmpty());
        assertEquals(64, completed.inventory().fungibleResources().totalQuantity(new SubjectId("settlement:1"), "minecraft:bread"));
        assertEquals(StrategicTaskStatus.COMPLETED, completed.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
        MarketDemand demand = completed.companies().market().demands().values().stream().filter(value -> value.buyerId().equals(new SubjectId("settlement:1")))
                .findFirst().orElseThrow();
        assertEquals(MarketDemandStatus.FULFILLED, demand.status());
        MarketWorkOrder terminalOrder = completed.companies().market().workOrders().values().stream()
                .filter(order -> order.demandId().equals(demand.id())).findFirst().orElseThrow();
        assertEquals(MarketWorkOrderStatus.FULFILLED, terminalOrder.status());
        TerminalProductionReceipt receipt = terminalOrder.terminalReceipt().orElseThrow();
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(completed));
        TerminalProductionReceipt recoveredReceipt = recovered.companies().market().workOrders().get(terminalOrder.id()).terminalReceipt().orElseThrow();
        assertEquals(receipt.topologyId(), recoveredReceipt.topologyId());
        assertEquals(receipt.topologyRevision(), recoveredReceipt.topologyRevision());
        assertEquals(receipt.traversalCursor(), recoveredReceipt.traversalCursor());
        assertEquals(receipt.terminalBody(), recoveredReceipt.terminalBody());
        assertThrows(IllegalArgumentException.class, () -> new TerminalProductionReceipt(receipt.jobId(), receipt.workerId(),
                receipt.inputId(), receipt.outputId(), receipt.inputRepresentation(), receipt.outputRepresentation(), receipt.outputKind(),
                receipt.outputCount(), receipt.topologyId(), -1L, receipt.traversalCursor(), receipt.terminalBody()));
        FrontierDomainRelationships.View view = FrontierDomainRelationships.view(recovered, 99L);
        FrontierDomainRelationships.Endpoint terminalJob = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.PRODUCTION_JOB, receipt.jobId());
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.JOB_WORKER && edge.target().stableKey().contains(receipt.workerId().value())));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.JOB_OUTPUT && edge.target().stableKey().contains(receipt.outputId().value())));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.JOB_OUTPUT
                && edge.target().kind() == FrontierDomainRelationships.EntityKind.RESOURCE_LOT), "fungible terminal output retains its lot type");
        Settlement settlement = recovered.bootstrap().settlements().stream().filter(value -> value.id().equals(demand.buyerId())).findFirst().orElseThrow();
        List<ProposedEvent> provisionPlan = SettlementProvisionProcess.planReview(recovered,
                SettlementProvisionProcess.review(settlement.id(), 99, 2_300L));
        SettlementProvisionStarted provision = provisionPlan.stream().map(ProposedEvent::payload).filter(SettlementProvisionStarted.class::isInstance)
                .map(SettlementProvisionStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState provisioned = SettlementProvisionProcess.reduceStarted(recovered, settlement.id(), provision);
        FrontierDomainRelationships.View provisionView = FrontierDomainRelationships.view(provisioned, 100L);
        assertTrue(provisionView.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ALLOCATION_RESOURCE
                && edge.target().equals(new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_LOT, receipt.outputId()))));
        assertTrue(provisionView.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ALLOCATION_RECIPIENT),
                "the retained terminal output reaches only the named provision recipients");
        assertTrue(completed.inventory().economics().reservations().isEmpty());
    }

    @Test
    void terminalProductionReceiptRejectsThePriorSchemaBeforeItsExtendedWireLayoutCanBeRead() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production-terminal-schema"), 91L));
        for (long tick = 100L; tick <= 2_200L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] current = codec.encode(new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()));
        byte[] prior = current.clone();
        prior[4]--;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(prior),
                "the pre-receipt schema must fail before an old terminal stream can be decoded as the extended receipt");
    }

    @Test
    void terminalFungibleBreadProvisionConnectsItsNamedFarmerToTheExactHarvestSuccessorWithoutReselection() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production-harvest-relation"), 91L));
        for (long tick = 100L; tick <= 2_200L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState terminal = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        MarketWorkOrder order = terminal.companies().market().workOrders().values().stream()
                .filter(value -> value.status() == MarketWorkOrderStatus.FULFILLED).findFirst().orElseThrow();
        TerminalProductionReceipt receipt = order.terminalReceipt().orElseThrow();

        HarvestLineage harvest = completedHarvestAndSuccessor(terminal, new SubjectId("site:1-wheat-field"));
        assertEquals(ResidentProfession.AGRICULTURAL_WORKER,
                harvest.state().humanPopulation().resident(harvest.farmerId()).profession());
        SettlementProvisionStarted started = SettlementProvisionProcess.planReview(harvest.state(),
                        SettlementProvisionProcess.review(new SubjectId("settlement:1"), 1, 2_300L)).stream()
                .map(ProposedEvent::payload).filter(SettlementProvisionStarted.class::isInstance)
                .map(SettlementProvisionStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState provisioned = SettlementProvisionProcess.reduceStarted(harvest.state(), new SubjectId("settlement:1"), started);
        FrontierDomainRelationships.View view = FrontierDomainRelationships.view(provisioned, 160L);
        FrontierDomainRelationships.Endpoint terminalJob = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.PRODUCTION_JOB, receipt.jobId());
        FrontierDomainRelationships.Endpoint outputLot = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.RESOURCE_LOT, receipt.outputId());
        FrontierDomainRelationships.Endpoint farmer = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.RESIDENT, harvest.farmerId());
        FrontierDomainRelationships.Endpoint successorTask = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.TASK, harvest.successor().taskId());
        FrontierDomainRelationships.Endpoint successorJob = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.RESOURCE_HARVEST_JOB, harvest.successor().id());

        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.JOB_OUTPUT
                && edge.target().equals(outputLot)));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ALLOCATION_RESOURCE
                && edge.target().equals(outputLot)));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ALLOCATION_RECIPIENT
                && edge.target().equals(farmer)));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.HARVEST_WORKER
                && edge.target().equals(farmer)));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.HARVEST_SUCCESSOR_TASK
                && edge.target().equals(successorTask)));
        assertTrue(view.causalChain(terminalJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.HARVEST_SUCCESSOR_JOB
                && edge.target().equals(successorJob)));
        assertTrue(view.causalChain(successorJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.JOB_OUTPUT
                && edge.target().equals(outputLot)), "reverse inspection reaches the same terminal production lot");
        assertEquals(harvest.farmerId(), harvest.successor().workerId(), "the successor retains the completed agricultural worker");
        assertFalse(view.edges().stream().filter(edge -> edge.kind() == FrontierDomainRelationships.Kind.HARVEST_SUCCESSOR_JOB)
                .anyMatch(edge -> !edge.target().equals(successorJob)), "another same-kind harvest job cannot become this lineage successor");
        assertFalse(view.edges().stream().filter(edge -> edge.kind() == FrontierDomainRelationships.Kind.HARVEST_SUCCESSOR_TASK)
                .anyMatch(edge -> !edge.target().equals(successorTask)), "another same-kind task cannot replace the retained successor task");
        assertFalse(view.causalChain(successorJob).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ALLOCATION_RESOURCE
                && edge.target().kind() == FrontierDomainRelationships.EntityKind.EXACT_ITEM),
                "the successor does not reselect a different exact bread resource");
    }

    @Test
    void admittedOrderJobChainIsReadOnlyDeterministicAndRestartStable() {
        ColdMarketJob prepared = coldMarketJob();
        FrontierDomainRelationships.View before = FrontierDomainRelationships.view(prepared.state(), 71L);
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(prepared.state()));
        FrontierDomainRelationships.View after = FrontierDomainRelationships.view(restored, 71L);

        assertEquals(before, after, "the relationship view is reconstructed from the canonical owners");
        assertTrue(after.incidents().isEmpty(), () -> "unexpected relationship incident: " + after.incidents());
        assertTrue(after.edges().stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ORDER_JOB
                && edge.target().equals(new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.PRODUCTION_JOB, prepared.job().id()))));
        FrontierDomainRelationships.Endpoint job = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.PRODUCTION_JOB, prepared.job().id());
        assertTrue(after.causalChain(job).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ORDER_TASK),
                "the exact job reaches its retained order/task cause without a semantic re-selection");
        FrontierDomainRelationships.View withCarrier = FrontierDomainRelationships.withCarrierEvidence(after, List.of(
                new FrontierDomainRelationships.CarrierEvidence(prepared.job().workerId(), "carrier:inactive-worker", "trace:carrier")));
        FrontierDomainRelationships.Endpoint carrier = new FrontierDomainRelationships.CarrierEvidenceEndpoint(prepared.job().workerId(), "carrier:inactive-worker");
        assertTrue(withCarrier.causalChain(carrier).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ORDER_TASK));
        assertEquals(after.edges().size() + 1, withCarrier.edges().size(), "carrier evidence cannot mutate canonical relationships");
    }

    @Test
    void firstRelationshipFailureIsOwnerLocalImmutableAndSnapshotStable() {
        ColdMarketJob prepared = coldMarketJob();
        RelationshipIncident first = new RelationshipIncident(FrontierDomainRelationships.Kind.ORDER_JOB, prepared.order().id(), prepared.order().id(),
                "one active production job", "wrong-type:resident", FrontierDomainRelationships.IncidentReason.WRONG_TYPE,
                FrontierDomainRelationships.Disposition.FAIL_CLOSED_LOCAL, 73L, "trace:relationship-order-job");
        MarketOrderBook conflicted = prepared.state().companies().market().relationshipConflict(prepared.order().id(), first);
        assertThrows(IllegalArgumentException.class, () -> conflicted.relationshipConflict(prepared.order().id(), first));
        FrontierWorldState state = prepared.state().withCompanies(prepared.state().companies().withMarket(conflicted));
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        FrontierDomainRelationships.Incident incident = FrontierDomainRelationships.view(restored, 73L).incidents().stream()
                .filter(value -> value.kind() == FrontierDomainRelationships.Kind.ORDER_JOB).findFirst().orElseThrow();
        assertEquals(FrontierDomainRelationships.IncidentReason.WRONG_TYPE, incident.reason());
        assertEquals(73L, incident.canonicalRevision());
        assertEquals("trace:relationship-order-job", incident.traceCorrelation());
    }

    @Test
    void relationshipIncidentPayloadRoundTripsThroughInstalledWalCodec() {
        RelationshipIncident incident = new RelationshipIncident(FrontierDomainRelationships.Kind.ORDER_JOB, new SubjectId("order:wal"), new SubjectId("order:wal"),
                "job:expected", "job:observed", FrontierDomainRelationships.IncidentReason.STALE_RELATION,
                FrontierDomainRelationships.Disposition.FAIL_CLOSED_LOCAL, 1L, "trace:wal");
        MarketRelationshipIncidentRecorded payload = new MarketRelationshipIncidentRecorded(new SubjectId("order:wal"), incident);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
    }

    @Test
    void missingDuplicateAndStaleOrderEndpointsFailClosedWithoutSelectingAnotherCandidate() {
        ColdMarketJob prepared = coldMarketJob();
        FrontierWorldState missingJob = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of()));
        FrontierDomainRelationships.Incident missing = FrontierDomainRelationships.view(missingJob, 74L).incidents().stream()
                .filter(value -> value.kind() == FrontierDomainRelationships.Kind.ORDER_JOB).findFirst().orElseThrow();
        assertEquals(FrontierDomainRelationships.IncidentReason.MISSING_ENDPOINT, missing.reason());
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.validate(missingJob));
        MarketWorkOrder duplicate = new MarketWorkOrder(new SubjectId("order:production-duplicate"), prepared.order().demandId(), prepared.order().quoteId(),
                prepared.order().sellerId(), prepared.order().taskId(), prepared.order().jobId(), prepared.order().reservationId(), prepared.order().acceptedTotalPrice(), MarketWorkOrderStatus.ACCEPTED);
        assertThrows(IllegalArgumentException.class, () -> new MarketOrderBook(prepared.state().companies().market().demands(), prepared.state().companies().market().quotes(),
                Map.of(prepared.order().id(), prepared.order(), duplicate.id(), duplicate)));
        RelationshipIncident stale = new RelationshipIncident(FrontierDomainRelationships.Kind.ORDER_JOB, prepared.order().id(), prepared.order().id(),
                "active job revision=74", "job revision=73", FrontierDomainRelationships.IncidentReason.STALE_RELATION,
                FrontierDomainRelationships.Disposition.FAIL_CLOSED_LOCAL, 74L, "trace:stale-order-job");
        MarketOrderBook conflicted = prepared.state().companies().market().relationshipConflict(prepared.order().id(), stale);
        assertEquals(FrontierDomainRelationships.IncidentReason.STALE_RELATION, conflicted.workOrders().get(prepared.order().id()).relationshipIncident().orElseThrow().reason());
    }

    @Test
    void materializedTransformationCannotBePreparedBeforeTheExactWorkerFinishesItsRetainedCycle() {
        MaterializedProduction prepared = activeMaterializedProduction();
        PhysicalIntent early = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-too-early"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, prepared.job().id(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.production(prepared.job().id(), prepared.job().consumedItemId(), prepared.job().outputItemId()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
        assertThrows(IllegalArgumentException.class, () -> prepared.state().preparePhysicalIntent(early));
    }

    @Test
    void blockedWorkshopWorkRetiresOnlyAfterTheExactWorkerSceneCloses() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SubjectId settlementId = prepared.settlementId(), workshopId = prepared.job().facilityId();
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(settlementId))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(workshopId)).findFirst().orElseThrow();
        ProductionJob workJob = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        prepared = new MaterializedProduction(prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(workJob.id(), workJob))),
                workJob, prepared.order(), prepared.settlementId(), prepared.taskId());
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-finalize-test");
        SceneLease lease = SceneLease.forCause(leaseId, prepared.state().bootstrap().worldId(), new ProductionWorkSceneCause(prepared.job().id()),
                worker.supportingSurface().support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(prepared.job().workerId(), SceneLease.deterministicEntityId(prepared.state().bootstrap().worldId(), prepared.job().workerId()))),
                java.util.Map.of(prepared.job().workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = prepared.state().prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        int nextCursor = 1;
        BodyPosition nextStation = workJob.workTraversal().linearCorridorSurfaces().get(nextCursor).standingBody();
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceWorkTraversalAdvanced(hot, settlementId,
                new ProductionWorkTraversalAdvanced(workJob.id(), leaseId, worker.body(), nextCursor)));
        FrontierWorldState advanced = ProductionProcess.reduceWorkTraversalAdvanced(hot, prepared.settlementId(),
                new ProductionWorkTraversalAdvanced(workJob.id(), leaseId, nextStation, nextCursor));
        assertEquals(nextCursor, advanced.productionJobs().get(workJob.id()).traversalCursor());
        assertEquals(nextStation, advanced.sceneLeases().get(leaseId).memberPosition(workJob.workerId()),
                "one observed arrival must atomically advance both the work cursor and persisted HOT-body position");
        FrontierWorldState recoveredAdvance = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(advanced));
        assertEquals(nextStation, recoveredAdvance.sceneLeases().get(leaseId).memberPosition(workJob.workerId()),
                "restart recovery must retain the current work station rather than the initial workshop approach");
        byte[] preAtomicCursorSchema = new FrontierWorldStateCodec().encode(advanced);
        preAtomicCursorSchema[4] = 118;
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldStateCodec().decode(preAtomicCursorSchema),
                "the former schema must fail closed because its HOT lease did not retain the current worker cursor");
        SceneLease staleLease = advanced.sceneLeases().get(leaseId).withMemberPositions(java.util.Map.of(workJob.workerId(), worker.body()));
        FrontierWorldState staleCursor = advanced.withChanges(FrontierWorldStateUpdate.begin()
                .sceneLeases(java.util.Map.of(leaseId, staleLease)));
        int cursorAfterNext = nextCursor + 1;
        BodyPosition stationAfterNext = workJob.workTraversal().linearCorridorSurfaces().get(cursorAfterNext).standingBody();
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceWorkTraversalAdvanced(staleCursor, settlementId,
                        new ProductionWorkTraversalAdvanced(workJob.id(), leaseId, stationAfterNext, cursorAfterNext)),
                "a malformed recovered HOT lease must fail closed rather than advance a split worker cursor");
        FrontierWorldState blocked = hot.withStrategicPlans(hot.strategicPlans().transitionTask(prepared.taskId(), StrategicTaskStatus.BLOCKED))
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        FrontierWorldState closed = blocked.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(prepared.job().workerId(), worker.body(), worker.condition().health())));
        assertTrue(closed.productionJobs().containsKey(prepared.job().id()), "closed lease remains the durable hand-off before job retirement");
        FrontierWorldState finalized = ProductionProcess.reduceWorkSceneFinalized(closed, prepared.settlementId(), new ProductionWorkSceneFinalized(leaseId, prepared.job().id()));
        assertFalse(finalized.productionJobs().containsKey(prepared.job().id()));
        assertEquals(MarketWorkOrderStatus.CANCELLED, finalized.companies().market().workOrders().get(prepared.order().id()).status());
        assertFalse(finalized.inventory().economics().reservations().containsKey(prepared.order().reservationId()));
    }

    @Test
    void workSceneFinalizationUsesItsAcceptedOrderTaskWhenAnotherBreadTaskIsBlocked() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream()
                .filter(value -> value.id().equals(prepared.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob workJob = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker,
                prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(workJob.id(), workJob)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-finalize-order-task");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(workJob.id()),
                worker.supportingSurface().support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(workJob.workerId(), SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), workJob.workerId()))),
                java.util.Map.of(workJob.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        StrategicTask bound = hot.strategicPlans().tasks().get(prepared.taskId());
        StrategicTask unrelatedBlocked = new StrategicTask(new SubjectId("task:production-unrelated-blocked"), bound.objectiveId(), bound.ownerId(), bound.kind(),
                bound.infectionTarget(), bound.operationTarget(), bound.resourceSiteTarget(), bound.requirements(), bound.dependencies(),
                StrategicTaskStatus.BLOCKED, bound.operationObservationPosition(), bound.authorityId(), bound.authorityEpoch());
        FrontierWorldState blocked = hot.withStrategicPlans(hot.strategicPlans().addTask(unrelatedBlocked)
                        .transitionTask(prepared.taskId(), StrategicTaskStatus.BLOCKED))
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        FrontierWorldState closed = blocked.releaseSceneLease(leaseId,
                List.of(new SceneMemberPosition(workJob.workerId(), worker.body(), worker.condition().health())));

        FrontierWorldState finalized = ProductionProcess.reduceWorkSceneFinalized(closed, prepared.settlementId(),
                new ProductionWorkSceneFinalized(leaseId, workJob.id()));

        assertFalse(finalized.productionJobs().containsKey(workJob.id()),
                "the closed scene must retire only the job bound by its accepted order");
        assertEquals(MarketWorkOrderStatus.CANCELLED, finalized.companies().market().workOrders().get(prepared.order().id()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, finalized.strategicPlans().tasks().get(unrelatedBlocked.id()).status(),
                "an unrelated blocked bread task remains a distinct ordinary-work fact");
    }

    @Test
    void blockedRetainedWorkEdgeDrainsOnlyThatExactHotWorkerAndRejectsForgedCursorEvidence() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(prepared.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(job.id(), job)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-route-blocked-hot");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(),
                new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), job.workerId()))), java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        WorldId world = new WorldId("frontier:production-route-blocked");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        List<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord> mismatchTransactions = new java.util.ArrayList<>();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> mismatchConfiguration = new FrontierEngineConfiguration<>(world, hot, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(),
                (transaction, durability) -> mismatchTransactions.add(transaction));
        var mismatch = FrontierEngines.create(mismatchConfiguration);
        var mismatchCheckpoint = mismatch.checkpoint(); CommandId mismatchId = new CommandId("command:production-route-mismatch");
        CommandResult mismatchResult = mismatch.submit(new FrontierCommand(1, mismatchId, world, mismatchCheckpoint.revision(),
                mismatchCheckpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(mismatchId),
                new ProductionWorkTraversalBlocked(new SubjectId("job:asserted-foreign"), leaseId, worker.body(), 1)));
        assertInstanceOf(CommandResult.Accepted.class, mismatchResult, mismatchResult.toString());
        FrontierWorldState conflicted = new FrontierWorldStateCodec().decode(mismatch.checkpoint().canonicalState());
        MarketWorkOrder conflictOrder = conflicted.companies().market().workOrders().get(prepared.order().id());
        RelationshipIncident incident = conflictOrder.relationshipIncident().orElseThrow();
        assertEquals(MarketWorkOrderStatus.CONFLICT, conflictOrder.status());
        assertEquals(FrontierDomainRelationships.Kind.ORDER_JOB, incident.kind());
        assertEquals(prepared.order().id(), incident.ownerId());
        assertEquals(job.id(), conflicted.productionJobs().get(job.id()).id());
        assertEquals(StrategicTaskStatus.BLOCKED, conflicted.strategicPlans().tasks().get(prepared.taskId()).status());
        assertEquals(SceneLeaseStatus.DRAINING, conflicted.sceneLeases().get(leaseId).status());
        assertTrue(conflicted.inventory().economics().reservations().containsKey(prepared.order().reservationId()));
        assertEquals(List.of("frontier.market_relationship_incident_recorded", "frontier.production_blocked",
                "frontier.strategic_task_transition", "frontier.scene_lease_transition"), mismatchTransactions.getFirst().events().stream()
                .map(event -> event.payload().type()).toList());

        var recoveredMismatch = FrontierEngines.recover(mismatchConfiguration, new RecoveryImage(world,
                Optional.of(new SnapshotRecord(mismatchCheckpoint, mismatchCheckpoint.revision().value())), mismatchTransactions));
        FrontierWorldState recoveredConflict = new FrontierWorldStateCodec().decode(recoveredMismatch.checkpoint().canonicalState());
        MarketWorkOrder recoveredOrder = recoveredConflict.companies().market().workOrders().get(prepared.order().id());
        assertEquals(mismatch.checkpoint(), recoveredMismatch.checkpoint());
        assertEquals(incident, recoveredOrder.relationshipIncident().orElseThrow());
        assertEquals(FrontierDomainRelationships.view(conflicted, mismatch.checkpoint().revision().value()),
                FrontierDomainRelationships.view(recoveredConflict, recoveredMismatch.checkpoint().revision().value()));
        assertEquals(conflicted.productionJobs().get(job.id()), recoveredConflict.productionJobs().get(job.id()));
        assertEquals(conflicted.strategicPlans().tasks().get(prepared.taskId()), recoveredConflict.strategicPlans().tasks().get(prepared.taskId()));
        assertEquals(conflicted.sceneLeases().get(leaseId), recoveredConflict.sceneLeases().get(leaseId));
        assertEquals(conflicted.inventory().economics().reservations().get(prepared.order().reservationId()),
                recoveredConflict.inventory().economics().reservations().get(prepared.order().reservationId()));
        CommandId releaseId = new CommandId("command:production-route-mismatch-release");
        var releaseCheckpoint = mismatch.checkpoint();
        CommandResult releaseResult = mismatch.submit(new FrontierCommand(1, releaseId, world, releaseCheckpoint.revision(), releaseCheckpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(releaseId), new SceneLeaseReleased(leaseId,
                List.of(new SceneMemberPosition(job.workerId(), worker.body(), worker.condition().health())))));
        assertInstanceOf(CommandResult.Accepted.class, releaseResult, releaseResult.toString());
        FrontierWorldState finalizedConflict = new FrontierWorldStateCodec().decode(mismatch.checkpoint().canonicalState());
        MarketWorkOrder finalizedOrder = finalizedConflict.companies().market().workOrders().get(prepared.order().id());
        assertEquals(MarketWorkOrderStatus.CONFLICT, finalizedOrder.status());
        assertEquals(incident, finalizedOrder.relationshipIncident().orElseThrow());
        assertFalse(finalizedConflict.productionJobs().containsKey(job.id()));
        assertFalse(finalizedConflict.inventory().economics().reservations().containsKey(prepared.order().reservationId()));
        assertEquals(StrategicTaskStatus.BLOCKED, finalizedConflict.strategicPlans().tasks().get(prepared.taskId()).status());
        assertEquals(SceneLeaseStatus.CLOSED, finalizedConflict.sceneLeases().get(leaseId).status());
        assertEquals(worker.body(), finalizedConflict.actorLocations().get(job.workerId()).body());
        assertTrue(finalizedConflict.productionJobs().values().stream().noneMatch(value -> value.workerId().equals(job.workerId())));
        ProductionWorkTraversalBlocked blocked = new ProductionWorkTraversalBlocked(job.id(), leaseId, worker.body(), 1);
        var checkpoint = engine.checkpoint(); CommandId command = new CommandId("command:production-route-blocked");

        CommandResult result = engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), blocked));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState draining = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.DRAINING, draining.sceneLeases().get(leaseId).status());
        assertEquals(StrategicTaskStatus.BLOCKED, draining.strategicPlans().tasks().get(prepared.taskId()).status());
        assertTrue(draining.productionJobs().containsKey(job.id()), "the job must wait for the exact physical worker release");
        assertEquals(blocked, FrontierWorldRuntimeDefinition.payloadCodecs().decode(blocked.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(blocked)));

        FrontierWorldState closed = draining.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), worker.body(), worker.condition().health())));
        FrontierWorldState cancelled = ProductionProcess.reduceWorkSceneFinalized(closed, prepared.settlementId(), new ProductionWorkSceneFinalized(leaseId, job.id()));
        assertFalse(cancelled.productionJobs().containsKey(job.id()));
        assertEquals(MarketWorkOrderStatus.CANCELLED, cancelled.companies().market().workOrders().get(prepared.order().id()).status());

        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceWorkTraversalBlocked(hot, prepared.settlementId(),
                new ProductionWorkTraversalBlocked(job.id(), leaseId, worker.body(), 2)), "an executor cannot skip an immutable edge when reporting a block");
    }

    @Test
    void conflictedProductionWorkLeaseKeepsItsExactWorkerReservedUntilExplicitRecovery() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream()
                .filter(value -> value.id().equals(prepared.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob workJob = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(workJob.id(), workJob)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-conflict-reservation");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(workJob.id()),
                worker.supportingSurface().support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(workJob.workerId(), SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), workJob.workerId()))),
                java.util.Map.of(workJob.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState conflicted = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);

        assertTrue(FrontierProductionWorkSceneSupport.candidates(conflicted).isEmpty(),
                "a visible conflict retains the same exact worker instead of admitting a second scene lease");
    }

    @Test
    void ambientHandoffRebasesOnlyAnUnstartedWorkshopTraversalToTheObservedWorkerBody() {
        FrontierWorldState state = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(
                new WorldId("frontier:production-observed-handoff"), 41L).state();
        ProductionJob job = state.productionJobs().get(new SubjectId("job:production-development-input-theft"));
        SurfaceAnchor observedSurface = job.workTraversal().linearCorridorSurfaces().get(1);
        BodyPosition observedBody = observedSurface.standingBody();
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-observed-handoff");
        SceneLease lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                observedSurface.support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), leaseId, job.workerId()))),
                java.util.Map.of(job.workerId(), observedBody), java.util.Set.of(job.workerId()), Optional.empty());
        ProductionWorkSceneLeaseHandoff handoff = new ProductionWorkSceneLeaseHandoff(lease,
                List.of(new SceneMemberPosition(job.workerId(), observedBody, state.actorLocations().get(job.workerId()).condition().health())));

        FrontierWorldState rebased = ProductionProcess.rebaseForAmbientHandoff(state, job.settlementId(), handoff);
        ProductionJob accepted = rebased.productionJobs().get(job.id());
        assertEquals(observedSurface, accepted.workTraversal().linearCorridorSurfaces().getFirst());
        SurfaceAnchor firstFreeEdge = accepted.workTraversal().linearCorridorSurfaces().get(1);
        Map<BlockPosition, GrayboxCell> geometry = FrontierGrayboxPlan.compile(rebased).cells();
        assertFalse(geometry.containsKey(firstFreeEdge.support().offset(0, 1, 0)),
                "an ambient hand-off must retain a supporting datum, never the planned body cell above it");
        assertFalse(geometry.containsKey(firstFreeEdge.support().offset(0, 2, 0)),
                "an ambient hand-off must retain a clear head cell at its first free edge");
        assertEquals(0, accepted.traversalCursor());
        assertEquals(accepted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(rebased)).productionJobs().get(job.id()),
                "restart retains the observed worker as the origin of its unstarted HOT route");

        ProductionJob advanced = accepted.withWorkTraversal(accepted.workTraversal(), 1);
        FrontierWorldState afterProgress = rebased.withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(advanced.id(), advanced)));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.rebaseForAmbientHandoff(afterProgress, job.settlementId(), handoff),
                "a later hand-off may not erase retained workshop progress");
    }

    @Test
    void ambientHandoffCarriesTheObservedStartSurfaceIntoPreparedSceneValidation() {
        FrontierWorldState initial = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(
                new WorldId("frontier:production-observed-handoff-anchor"), 41L).state();
        ProductionJob job = initial.productionJobs().get(new SubjectId("job:production-development-input-theft"));
        SurfaceAnchor staleSurface = job.workTraversal().linearCorridorSurfaces().getFirst();
        SurfaceAnchor observedSurface = job.workTraversal().linearCorridorSurfaces().get(1);
        BodyPosition observedBody = observedSurface.standingBody();
        Map<SubjectId, ActorLocation> locations = new LinkedHashMap<>(initial.actorLocations());
        locations.put(job.workerId(), new ActorLocation(observedBody, initial.actorLocations().get(job.workerId()).condition()));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-observed-handoff-anchor");
        SceneLease staleAnchor = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                staleSurface.support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), leaseId, job.workerId()))),
                Map.of(job.workerId(), observedBody), Set.of(job.workerId()), Optional.empty());
        ProductionWorkSceneLeaseHandoff handoff = new ProductionWorkSceneLeaseHandoff(staleAnchor,
                List.of(new SceneMemberPosition(job.workerId(), observedBody, state.actorLocations().get(job.workerId()).condition().health())));
        FrontierWorldState rebased = ProductionProcess.rebaseForAmbientHandoff(state, job.settlementId(), handoff);

        assertThrows(IllegalArgumentException.class, () -> FrontierProductionWorkSceneSupport.validatePrepared(rebased, staleAnchor),
                "a stale admission anchor may not masquerade as the observed unstarted worker surface");
        SceneLease capturedAnchor = staleAnchor.withHandoffPosition(observedSurface.support());
        FrontierProductionWorkSceneSupport.validatePrepared(rebased, capturedAnchor);

        // Exercise the real command/reducer order: it rebases the unstarted job before the
        // generic scene hand-off validates the prepared lease.  The lease anchor must therefore
        // already be the observed support, or a normal ambient worker would fail closed here.
        AmbientActorLease ambient = new AmbientActorLease(job.workerId(), observedBody, new SimInstant(100L), 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, observedBody);
        FrontierWorldState ambientState = state.withChanges(FrontierWorldStateUpdate.begin()
                .ambientLeases(Map.of(job.workerId(), ambient)));
        WorldId world = ambientState.bootstrap().worldId();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 41L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, ambientState, new SimInstant(100L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(),
                base.limits(), List.of(), base.transactionCommitter()));
        CommandId command = new CommandId("command:production-observed-handoff-anchor");
        var checkpoint = engine.checkpoint();
        CommandResult result = engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command),
                new ProductionWorkSceneLeaseHandoff(capturedAnchor, handoff.ambientMembers())));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState accepted = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(observedSurface, accepted.productionJobs().get(job.id()).workTraversal().linearCorridorSurfaces().getFirst());
        assertEquals(observedSurface.support(), accepted.sceneLeases().get(leaseId).handoffPosition());
        assertEquals(AmbientLeaseStatus.CLOSED, accepted.ambientLeases().get(job.workerId()).status());
    }

    @Test
    void activeMaterializedProductionRetainsInputUntilOneDurablePhysicalTransformationConfirmsOutput() {
        PreparedProduction prepared = activePhysicalProduction();
        ExactItemStack input = prepared.state().inventory().items().get(prepared.job().consumedItemId());
        FrontierWorldState running = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(new PhysicalObservationId("observation:test-production"), prepared.intent().id(),
                input.id(), prepared.job().outputItemId(), input.count(), prepared.job().outputCount());
        PhysicalIntentTransition transition = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition))).observation().orElseThrow());
        FrontierWorldState completed = running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertTrue(completed.productionJobs().isEmpty());
        assertEquals("minecraft:bread", completed.inventory().items().get(prepared.job().outputItemId()).itemKind());
        assertEquals(FixedScalar.ONE, completed.inventory().economics().require(prepared.job().workerId()).balance());
        assertEquals(FixedScalar.ONE, completed.inventory().economics().require(CompanyFoundationProcess.companyId(prepared.job().settlementId())).balance());
        MarketWorkOrder terminalOrder = completed.companies().market().workOrders().values().stream()
                .filter(order -> order.jobId().equals(prepared.job().id())).findFirst().orElseThrow();
        assertEquals(MarketWorkOrderStatus.FULFILLED, terminalOrder.status());
        TerminalProductionReceipt terminalReceipt = terminalOrder.terminalReceipt().orElseThrow();
        assertEquals(prepared.job().id(), terminalReceipt.jobId());
        assertEquals(prepared.job().workerId(), terminalReceipt.workerId());
        assertEquals(prepared.job().workTraversal().id(), terminalReceipt.topologyId());
        assertEquals(prepared.job().workTraversal().revision(), terminalReceipt.topologyRevision());
        assertEquals(prepared.job().traversalCursor(), terminalReceipt.traversalCursor());
        assertEquals(prepared.job().workTraversal().linearCorridorSurfaces().get(prepared.job().traversalCursor()).standingBody(),
                terminalReceipt.terminalBody());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(completed));
        assertEquals(terminalReceipt, recovered.companies().market().workOrders().get(terminalOrder.id()).terminalReceipt().orElseThrow(),
                "physical terminal receipt survives snapshot restart after its live job is removed");
    }

    @Test
    void nonTerminalPhysicalProductionCannotCreateOrRetainATerminalReceipt() {
        MaterializedProduction prepared = activeMaterializedProduction();
        PhysicalIntent premature = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-premature"),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, prepared.job().id(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.production(prepared.job().id(), prepared.job().consumedItemId(), prepared.job().outputItemId()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.PRODUCTION_WORK);

        assertThrows(IllegalArgumentException.class, () -> ProductionTransformationStateSupport.validateIntent(prepared.state(), premature));
        assertTrue(prepared.state().productionJobs().containsKey(prepared.job().id()));
        assertTrue(prepared.state().companies().market().workOrders().get(prepared.order().id()).terminalReceipt().isEmpty());
        assertEquals(MarketWorkOrderStatus.ACCEPTED, prepared.state().companies().market().workOrders().get(prepared.order().id()).status());
    }

    @Test
    void outputReadyHotWorkerMustReleaseBeforeItsPhysicalTransformationCanBePrepared() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream()
                .filter(value -> value.id().equals(prepared.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob workJob = prepared.job().withWorkTraversal(
                ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-output-ready-release");
        SceneLease lease = SceneLease.forCause(leaseId, prepared.state().bootstrap().worldId(), new ProductionWorkSceneCause(workJob.id()),
                worker.supportingSurface().support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(workJob.workerId(), SceneLease.deterministicEntityId(prepared.state().bootstrap().worldId(), workJob.workerId()))),
                java.util.Map.of(workJob.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(workJob.id(), workJob)))
                .prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        ProductionJob outputReady = workJob.withWorkProgress(ProductionWorkProgress.outputReady());
        hot = hot.withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(outputReady.id(), outputReady)));
        StrategicTask unrelatedBlockedBread = new StrategicTask(new SubjectId("task:unrelated-blocked-bread"), prepared.state().strategicPlans()
                .tasks().get(prepared.taskId()).objectiveId(), prepared.settlementId(), StrategicTaskKind.PRODUCE_BREAD, Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), StrategicTaskStatus.BLOCKED);
        hot = hot.withStrategicPlans(hot.strategicPlans().addTask(unrelatedBlockedBread));
        // The orderless compatibility lane remains a valid retained job state. It must not
        // infer this job's disposition from the unrelated blocked task above.
        hot = hot.withCompanies(hot.companies().withMarket(MarketOrderBook.empty()));
        ScheduledAction completion = ProductionProcess.complete(outputReady, 1_000L);

        ScheduleEffect.Rescheduled deferred = assertInstanceOf(ScheduleEffect.Rescheduled.class,
                ProductionProcess.planCompletion(hot, completion).getFirst().payload(),
                "a scheduled COLD completion must retain—not consume—its one review while the exact worker remains HOT");
        assertEquals(completion.id(), deferred.scheduleId());
        assertEquals(completion.id(), deferred.replacement().id());
        assertTrue(hot.physicalIntents().isEmpty());

        FrontierWorldState draining = hot.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        SceneLeaseReleased released = new SceneLeaseReleased(leaseId, List.of(new SceneMemberPosition(outputReady.workerId(), worker.body(), worker.condition().health())));
        List<ProposedEvent> continuation = FrontierSceneContinuationPlanner.releaseEvents(draining, lease, 1_000L, released);
        assertEquals(2, continuation.size());
        ScheduleEffect.Rescheduled scheduled = assertInstanceOf(ScheduleEffect.Rescheduled.class, continuation.get(1).payload());
        assertEquals(completion.id(), scheduled.scheduleId());
        assertEquals(1_001L, scheduled.replacement().dueAt().ticks());
        assertEquals(outputReady.id(), scheduled.replacement().subject());

        FrontierWorldState closed = draining.releaseSceneLease(leaseId, released.members());
        List<ProposedEvent> effect = ProductionProcess.planCompletion(closed, scheduled.replacement());
        ProductionCompleted direct = assertInstanceOf(ProductionCompleted.class, effect.getFirst().payload(),
                "after release with no current physical lease, the exact retained result belongs to COLD rather than a new observer gate");
        FrontierWorldState completed = ProductionProcess.reduceCompleted(closed, prepared.settlementId(), direct);
        assertTrue(completed.productionJobs().isEmpty());
        assertEquals(SceneLeaseStatus.CLOSED, completed.sceneLeases().get(leaseId).status());
    }

    @Test
    void blockedBreadElsewhereCannotAuthorizeThisJobsMissingInputRelease() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ProductionJob job = prepared.job();
        ActorLocation worker = prepared.state().actorLocations().get(job.workerId());
        SceneLease lease = SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-exact-blocked-release"),
                prepared.state().bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(), new SimInstant(100L), 1L,
                SceneLeaseStatus.DRAINING, List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(prepared.state().bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        StrategicTask unrelated = new StrategicTask(new SubjectId("task:unrelated-blocked-bread-release"), prepared.state().strategicPlans().tasks()
                .get(prepared.taskId()).objectiveId(), prepared.settlementId(), StrategicTaskKind.PRODUCE_BREAD, Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), StrategicTaskStatus.BLOCKED);
        StrategicPlanState mixed = prepared.state().strategicPlans().addTask(unrelated);

        assertFalse(FrontierProductionWorkSceneSupport.awaitingBlockedRelease(java.util.Map.of(lease.id(), lease), mixed,
                prepared.state().companies(), job));
        StrategicPlanState ownBlocked = mixed.transitionTask(prepared.taskId(), StrategicTaskStatus.BLOCKED);
        assertTrue(FrontierProductionWorkSceneSupport.awaitingBlockedRelease(java.util.Map.of(lease.id(), lease), ownBlocked,
                prepared.state().companies(), job));
    }

    @Test
    void productionStartRetainsTheExactCrafterToWorkshopPortTopology() {
        FrontierWorldState state = productionTask(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-route"), 91L)),
                StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionJob job = ProductionProcess.planStart(state, ProductionProcess.start(task, 100L)).stream().map(ProposedEvent::payload)
                .filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow().job();
        SettlementStructure workshop = state.bootstrap().settlements().stream().filter(settlement -> settlement.id().equals(job.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(structure -> structure.id().equals(job.facilityId())).findFirst().orElseThrow();
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        java.util.List<SurfaceAnchor> corridor = job.workTraversal().linearCorridorSurfaces();
        assertEquals(state.actorLocations().get(job.workerId()).supportingSurface(), corridor.getFirst());
        assertEquals(port.inputStation(), corridor.get(corridor.size() - 2));
        assertEquals(port.workStation(), corridor.getLast());
        Map<BlockPosition, GrayboxCell> geometry = FrontierGrayboxPlan.compile(state).cells();
        corridor.forEach(surface -> {
            assertFalse(geometry.containsKey(surface.support().offset(0, 1, 0)),
                    () -> "production corridor body collides with planned geometry at " + surface);
            assertFalse(geometry.containsKey(surface.support().offset(0, 2, 0)),
                    () -> "production corridor head collides with planned geometry at " + surface);
        });
        assertEquals(0, job.traversalCursor());
    }

}
