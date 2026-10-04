package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BakeryHotVerticalTest {
    @Test
    void replacingStolenExactWheatRetainsTheSameBakerAndJob() {
        WorldId world = new WorldId("frontier:bakery-exact-input-replacement");
        FrontierWorldState state = ProductionProcessTest.productionTask(ProductionProcessTest.withLegacyExactWheat(
                FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L))), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        assertInstanceOf(ProductionInputHold.Materialized.class, started.job().inputHold());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob original = state.productionJobs().get(started.job().id());
        ExactItemStack input = state.inventory().items().get(original.consumedItemId());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, state, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        CommandId command = new CommandId("command:bakery-exact-player-takes-input");
        var checkpoint = engine.checkpoint();
        var player = new InventoryCustody.Player(java.util.UUID.fromString("00000000-0000-0000-0000-000000000157"));
        CommandResult result = engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command),
                new ExactItemCustodyChanged(input.id(), input.custody(), player)));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(BakeryWorkBlock.Reason.SOURCE_CHANGED,
                state.productionJobs().get(original.id()).bakeryWork().orElseThrow().block().orElseThrow().reason());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().get(task.id()).status());
        assertTrue(ProductionProcess.planCompletion(state, ProductionProcess.complete(original, 300L)).stream()
                .noneMatch(event -> event.payload() instanceof BakeryInputReallocated));

        SubjectId depot = FrontierWorldState.depotId(task.ownerId());
        SubjectId replacementId = new SubjectId("item:bakery-replacement-exact-wheat");
        ExactItemStack replacement = new ExactItemStack(replacementId, task.ownerId(), "minecraft:wheat", 64,
                new InventoryCustody.ContainerSlot(depot, 1));
        state = state.withInventory(state.inventory().store(replacement));
        BakeryInputReallocated event = ProductionProcess.planCompletion(state, ProductionProcess.complete(original, 320L)).stream()
                .map(ProposedEvent::payload).filter(BakeryInputReallocated.class::isInstance)
                .map(BakeryInputReallocated.class::cast).findFirst().orElseThrow();
        assertEquals(event, FrontierWorldRuntimeDefinition.payloadCodecs().decode(event.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(event)));
        state = ProductionProcess.reduceBakeryInputReallocated(state, task.ownerId(), event);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        ProductionJob resumed = state.productionJobs().get(original.id());
        assertEquals(original.workerId(), resumed.workerId());
        assertEquals(replacementId, resumed.consumedItemId());
        assertEquals(original.outputItemId(), resumed.outputItemId());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().get(task.id()).status());
        assertTrue(resumed.bakeryWork().orElseThrow().block().isEmpty());

        // A second, destructive departure is still a local input pause, not an
        // order cancellation or a silent adoption of the first player's stack.
        engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, state, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        command = new CommandId("command:bakery-replacement-wheat-destroyed");
        checkpoint = engine.checkpoint();
        result = engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command),
                new ExactItemDestroyed(replacementId, replacement.custody(), "player destroyed depot wheat")));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(state.productionJobs().containsKey(original.id()));
        assertEquals(BakeryWorkBlock.Reason.SOURCE_CHANGED,
                state.productionJobs().get(original.id()).bakeryWork().orElseThrow().block().orElseThrow().reason());
    }

    @Test
    void workerLossOrWorkshopDamageRetainsTheAcceptedPrePickupBakeryAllocation() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-local-work-loss"), 91L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = state.productionJobs().get(started.job().id());

        ActorLocation actor = state.actorLocations().get(job.workerId());
        Map<SubjectId, ActorLocation> locations = new java.util.LinkedHashMap<>(state.actorLocations());
        locations.put(job.workerId(), new ActorLocation(actor.body(), ActorCondition.dead(), actor.kind()));
        FrontierWorldState deadWorker = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
        List<ProposedEvent> deathEvents = ProductionProcess.failPreEffectWorkForDeath(deadWorker, job.workerId());
        assertEquals(2, deathEvents.size());
        assertEquals(ProductionBlockReason.WORKER_UNAVAILABLE,
                assertInstanceOf(ProductionBlocked.class, deathEvents.getFirst().payload()).reason());
        FrontierWorldState deathBlocked = ProductionProcess.reduceBlocked(deadWorker, task.ownerId(),
                (ProductionBlocked) deathEvents.getFirst().payload());
        deathBlocked = StrategicObjectiveProcess.reduceTaskTransition(deathBlocked, task.ownerId(),
                (StrategicTaskTransition) deathEvents.get(1).payload());
        assertEquals(job, deathBlocked.productionJobs().get(job.id()));
        assertEquals(StrategicTaskStatus.BLOCKED, deathBlocked.strategicPlans().tasks().get(task.id()).status());

        FrontierWorldState damaged = state.withStructureCondition(job.facilityId(), StructureCondition.DAMAGED);
        List<ProposedEvent> damageEvents = ProductionProcess.planFacilityUnavailable(damaged, job.facilityId());
        assertEquals(2, damageEvents.size());
        assertEquals(ProductionBlockReason.FACILITY_UNAVAILABLE,
                assertInstanceOf(ProductionBlocked.class, damageEvents.getFirst().payload()).reason());
        FrontierWorldState facilityBlocked = ProductionProcess.reduceBlocked(damaged, task.ownerId(),
                (ProductionBlocked) damageEvents.getFirst().payload());
        facilityBlocked = StrategicObjectiveProcess.reduceTaskTransition(facilityBlocked, task.ownerId(),
                (StrategicTaskTransition) damageEvents.get(1).payload());
        assertEquals(job, facilityBlocked.productionJobs().get(job.id()));
        assertEquals(StrategicTaskStatus.BLOCKED, facilityBlocked.strategicPlans().tasks().get(task.id()).status());
    }

    @Test
    void stolenWheatRetainsAcceptedBakeryJobUntilAnotherOwnedBatchIsAllocated() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-source-reallocation"), 91L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob original = state.productionJobs().get(started.job().id());
        BakeryWorkState work = original.bakeryWork().orElseThrow();
        SubjectId depot = FrontierWorldState.depotId(task.ownerId());
        FungibleResourceLedger unbound = state.inventory().fungibleResources();
        state = ProductionResourceCustody.bind(state, work.sourceAccountId(), 1L,
                FungiblePhysicalObservation.bind(unbound, work.sourceAccountId(), 1L,
                        List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64))));
        FungibleResourceLedger bound = state.inventory().fungibleResources();
        PhysicalStackBinding source = bound.bindings().values().stream()
                .filter(value -> value.accountId().equals(work.sourceAccountId())).findFirst().orElseThrow();
        var player = java.util.UUID.fromString("00000000-0000-0000-0000-000000000155");
        FungibleResourceHandoffObserved theft = FungiblePhysicalHandoff.departToNew(bound, work.sourceAccountId(),
                1L, source, 0, new SubjectId("custody:bakery-test-player"), new ResourceCustody.Player(player),
                1L, new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitMovedClaims()
                .withPlayerSaveFence(java.util.UUID.fromString("00000000-0000-0000-0000-000000000156"));
        assertTrue(FungibleClaimForfeitureStateSupport.supports(state, theft));
        state = FungibleClaimForfeitureStateSupport.apply(state, theft);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(original.workerId(), state.productionJobs().get(original.id()).workerId());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().get(task.id()).status());
        assertEquals(BakeryWorkBlock.Reason.SOURCE_CHANGED,
                state.productionJobs().get(original.id()).bakeryWork().orElseThrow().block().orElseThrow().reason());
        assertTrue(ProductionProcess.planCompletion(state, ProductionProcess.complete(original, 300L)).stream()
                .noneMatch(value -> value.payload() instanceof BakeryInputReallocated));

        SubjectId replacementLotId = new SubjectId("lot:bakery-replacement-wheat");
        SubjectId replacementAccountId = new SubjectId("custody:bakery-replacement-depot");
        ResourceLot replacementLot = new ResourceLot(replacementLotId, task.ownerId(), "minecraft:wheat", 64,
                "test-replenishment", List.of());
        FungibleResourceLedger replenished = state.inventory().fungibleResources().issue(replacementLot,
                new CustodyAccount(replacementAccountId, new ResourceCustody.Container(depot),
                        Map.of(replacementLotId, 64), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(replenished));
        BakeryInputReallocated event = ProductionProcess.planCompletion(state, ProductionProcess.complete(original, 320L)).stream()
                .map(ProposedEvent::payload).filter(BakeryInputReallocated.class::isInstance)
                .map(BakeryInputReallocated.class::cast).findFirst().orElseThrow();
        assertEquals(event, io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                .payloadCodecs().decode(event.type(), io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                        .payloadCodecs().encode(event)));
        state = ProductionProcess.reduceBakeryInputReallocated(state, task.ownerId(), event);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        ProductionJob resumed = state.productionJobs().get(original.id());
        assertEquals(original.workerId(), resumed.workerId());
        assertEquals(replacementLotId, resumed.consumedItemId());
        assertEquals(replacementAccountId, resumed.bakeryWork().orElseThrow().sourceAccountId());
        assertTrue(resumed.bakeryWork().orElseThrow().block().isEmpty());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().get(task.id()).status());
    }

    @Test
    void preparedLoadedStepsKeepOneBakerAndDeliverOnlyAfterStationRecipe() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-hot-vertical"), 91L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        ProductionJob admitted = started.job();
        ProductionJob legacyRouteOnly = new ProductionJob(admitted.id(), admitted.taskId(), admitted.settlementId(),
                admitted.facilityId(), admitted.workerId(), admitted.consumedItemId(), admitted.inputHold(),
                admitted.outputItemId(), admitted.outputItemKind(), admitted.outputCount(), admitted.workProgress(),
                admitted.workTraversal(), admitted.traversalCursor(), Optional.empty());
        FrontierWorldState beforeAdmission = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceStarted(beforeAdmission,
                task.ownerId(), new ProductionStarted(legacyRouteOnly, started.inputItemId())),
                "a forged route-only job cannot reopen direct depot-slot bread production");
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        for (int index = 0; index < 100; index++) {
            BakeryColdStep step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 300L + index * 20L)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            if (step.action() == BakeryColdStep.Action.PICKUP) break;
            assertEquals(BakeryColdStep.Action.MOVE, step.action());
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
        }
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        var stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64);
        FungibleResourceLedger cold = state.inventory().fungibleResources();
        state = state.withInventory(state.inventory().withFungibleResources(cold.rebind(work.sourceAccountId(), 1L,
                FungiblePhysicalObservation.bind(cold, work.sourceAccountId(), 1L, List.of(stack)))));
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(state, depot),
                7L, ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(replica)
                .observe(depot, 7L, 1L, replica.fingerprint(), replica.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot,
                        ReferenceContainerCustody.PROVIDER_ID, 1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        SceneLeaseId leaseId = new SceneLeaseId("lease:bakery-hot-vertical");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        SceneLease lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                actor.supportingSurface().support(), new SimInstant(300L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))), java.util.Set.of(), Optional.empty());
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertEquals(BakeryWorkGoal.current(state, job).station().standingBody(), state.sceneLeases().get(leaseId).memberBody(state.actorLocations(), job.workerId()));
        BakeryWorkBlock changedSource = new BakeryWorkBlock(BakeryWorkBlock.Reason.SOURCE_CHANGED,
                depot, 0, "minecraft:wheat", 32);
        BakeryHotBlockChanged blocked = new BakeryHotBlockChanged(job.id(), leaseId,
                BakeryWorkState.Phase.DEPOT_PICKUP, Optional.of(changedSource));
        assertEquals(blocked, io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                .payloadCodecs().decode(blocked.type(), io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                        .payloadCodecs().encode(blocked)));
        state = ProductionProcess.reduceBakeryHotBlockChanged(state, task.ownerId(), blocked);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(Optional.of(changedSource), state.productionJobs().get(job.id()).bakeryWork().orElseThrow().block());
        FrontierWorldState retainedBlock = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectPrepared(retainedBlock,
                task.ownerId(), new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_PICKUP, -1)),
                "a physical transfer cannot cross a retained source obstruction");
        state = ProductionProcess.reduceBakeryHotBlockChanged(state, task.ownerId(),
                new BakeryHotBlockChanged(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_PICKUP, Optional.empty()));
        BakeryHotEffectPrepared prepared = new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_PICKUP, -1);
        state = ProductionProcess.reduceBakeryHotEffectPrepared(state, task.ownerId(), prepared);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertTrue(state.productionJobs().get(job.id()).bakeryWork().orElseThrow().pendingPhysicalStep().isPresent());
        assertTrue(ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 400L)).stream()
                .noneMatch(event -> event.payload() instanceof BakeryColdStep));
        FrontierWorldState preparedPickup = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectObserved(
                preparedPickup, task.ownerId(), new BakeryHotEffectObserved(job.id(), leaseId,
                        BakeryWorkState.Phase.DEPOT_PICKUP, actor.body(), 1L, 1L, List.of(),
                        List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                                lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN), "minecraft:wheat", 63)))),
                "a depleted depot batch cannot be recorded as a complete baker pickup");
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN), "minecraft:wheat", 64);
        BakeryHotEffectObserved observed = new BakeryHotEffectObserved(job.id(), leaseId,
                BakeryWorkState.Phase.DEPOT_PICKUP, actor.body(), 1L, 1L, List.of(), List.of(hand));
        state = ProductionProcess.reduceBakeryHotEffectObserved(state, task.ownerId(), observed);
        assertEquals(BakeryWorkState.Phase.STATION_LOAD, state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        // The actual pickup batch survives a failed no-demand release. Fresh recovery
        // may resume this exact body/hand, never clear an unknown pending recipe effect.
        var conflicted = state.transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        var fence = conflicted.fencedRecovery().current().get(
                ActorBodyId.recoveryBindingId(job.workerId()));
        var reconciled = new BakerySceneReconciled(job.id(), leaseId, lease.revision(),
                fence.authorityEpoch(), actor.body(), hand);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(reconciled, codecs.decode(reconciled.type(), codecs.encode(reconciled)));
        var materialized = new BakeryHotHandMaterialized(job.id(), leaseId, work.actorAccountId(), 1L, hand);
        assertEquals(materialized, codecs.decode(materialized.type(), codecs.encode(materialized)),
                "WAL recovery must retain the baker's main hand");
        var resumed = io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                conflicted, task.ownerId(), reconciled);
        assertEquals(SceneLeaseStatus.HOT, resumed.sceneLeases().get(leaseId).status());
        assertEquals(state.inventory(), resumed.inventory(), "recovery cannot mint or move cargo");
        assertEquals(state.productionJobs(), resumed.productionJobs());
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                conflicted, task.ownerId(), new BakerySceneReconciled(job.id(), leaseId, lease.revision(),
                        fence.authorityEpoch(), actor.body(), new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", 63))));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                resumed, task.ownerId(), reconciled), "a duplicate inspection cannot reset the fence");
        SubjectId nextResident = state.bootstrap().settlements().getFirst().residents().stream()
                .map(Resident::id).filter(id -> !id.equals(job.workerId())).findFirst().orElseThrow();
        assertFalse(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, nextResident),
                "the HOT worker still owns the depot approach after the pickup phase changes");
        assertEquals(new ResourceCustody.Actor(job.workerId()), state.inventory().fungibleResources().accounts()
                .get(work.actorAccountId()).custody());
        FrontierWorldState blockedWithCargo = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        var release = new SceneLeaseReleased(leaseId, List.of(new SceneMemberPosition(job.workerId(), actor.body(),
                actor.condition().health())));
        var handRelease = new BakeryHotHandRelease(job.id(), work.actorAccountId(), 1L, hand, release);
        assertEquals(handRelease, codecs.decode(handRelease.type(), codecs.encode(handRelease)),
                "scene release replay must retain the baker's main hand");
        blockedWithCargo = ProductionProcess.reduceBakeryHotHandRelease(blockedWithCargo, task.ownerId(), handRelease);
        blockedWithCargo = ProductionProcess.reduceWorkSceneFinalized(blockedWithCargo, task.ownerId(),
                new ProductionWorkSceneFinalized(leaseId, job.id()));
        assertTrue(blockedWithCargo.productionJobs().containsKey(job.id()),
                "failed scene closure must retain the exact batch in baker custody");
        assertEquals(BakeryWorkState.Phase.STATION_LOAD,
                blockedWithCargo.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        ProductionStationSpec station = state.inventory().containers().values().stream()
                .flatMap(container -> container.productionStation().stream())
                .filter(candidate -> candidate.id().equals(work.stationId())).findFirst().orElseThrow();
        SubjectId machine = station.containerId();
        PhysicalReplicaRecord machineReplica = PhysicalReplicaRecord.expected(machine,
                ReferenceContainerCustody.semanticKind(state, machine), 8L,
                ReferenceContainerCustody.canonicalFingerprint(state, machine), ReferenceContainerCustody.provenance(machine));
        custody = state.replicaCustody().declare(machineReplica)
                .observe(machine, 8L, 1L, machineReplica.fingerprint(), machineReplica.provenance(), 8L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(machine), machine,
                        ReferenceContainerCustody.PROVIDER_ID, 1L, 8L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        state = at(state, leaseId, job.workerId(), station.workerStation().standingBody());
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, nextResident),
                "the witnessed baker departure must free the depot during station work");
        BakeryWorkBlock occupiedMachine = new BakeryWorkBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED,
                machine, station.inputSlot(), "minecraft:stone", 1);
        state = ProductionProcess.reduceBakeryHotBlockChanged(state, task.ownerId(),
                new BakeryHotBlockChanged(job.id(), leaseId, BakeryWorkState.Phase.STATION_LOAD,
                        Optional.of(occupiedMachine)));
        FrontierWorldState blockedStation = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectPrepared(
                blockedStation, task.ownerId(), new BakeryHotEffectPrepared(job.id(), leaseId,
                        BakeryWorkState.Phase.STATION_LOAD, station.inputSlot())));
        state = ProductionProcess.reduceBakeryHotBlockChanged(state, task.ownerId(),
                new BakeryHotBlockChanged(job.id(), leaseId, BakeryWorkState.Phase.STATION_LOAD, Optional.empty()));
        state = ProductionProcess.reduceBakeryHotEffectPrepared(state, task.ownerId(),
                new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.STATION_LOAD, station.inputSlot()));
        FrontierWorldState preparedLoad = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectObserved(
                preparedLoad, task.ownerId(), new BakeryHotEffectObserved(job.id(), leaseId,
                        BakeryWorkState.Phase.STATION_LOAD, station.workerStation().standingBody(), 1L, 1L,
                        List.of(), List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(machine, station.outputSlot())),
                        "minecraft:wheat", 64)))),
                "wheat placed in the output port cannot masquerade as station input");
        state = ProductionProcess.reduceBakeryHotEffectObserved(state, task.ownerId(), new BakeryHotEffectObserved(
                job.id(), leaseId, BakeryWorkState.Phase.STATION_LOAD, station.workerStation().standingBody(),
                1L, 1L, List.of(), List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(machine, station.inputSlot())),
                "minecraft:wheat", 64))));
        assertEquals(BakeryWorkState.Phase.PROCESSING, state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        for (int count = 1; count <= ProductionWorkProgress.REQUIRED_PROCESSING_TICKS; count++)
            state = ProductionProcess.reduceBakeryHotWorkTick(state, task.ownerId(),
                    new BakeryHotWorkTick(job.id(), leaseId, station.workerStation().standingBody(), count));
        state = ProductionProcess.reduceBakeryHotEffectPrepared(state, task.ownerId(),
                new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.PROCESSING, station.outputSlot()));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        FrontierWorldState preparedRecipe = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectObserved(
                preparedRecipe, task.ownerId(), new BakeryHotEffectObserved(job.id(), leaseId, BakeryWorkState.Phase.PROCESSING,
                        station.workerStation().standingBody(), 1L, 1L, List.of(), List.of())));
        state = ProductionProcess.reduceBakeryHotEffectObserved(state, task.ownerId(), new BakeryHotEffectObserved(
                job.id(), leaseId, BakeryWorkState.Phase.PROCESSING, station.workerStation().standingBody(),
                1L, 1L, List.of(), List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(machine, station.outputSlot())),
                "minecraft:bread", 64))));
        assertEquals(BakeryWorkState.Phase.STATION_UNLOAD, state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        assertFalse(state.inventory().fungibleResources().lots().containsKey(job.consumedItemId()));
        state = ProductionProcess.reduceBakeryHotEffectPrepared(state, task.ownerId(),
                new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.STATION_UNLOAD, -1));
        state = ProductionProcess.reduceBakeryHotEffectObserved(state, task.ownerId(), new BakeryHotEffectObserved(
                job.id(), leaseId, BakeryWorkState.Phase.STATION_UNLOAD, station.workerStation().standingBody(),
                1L, 1L, List.of(), List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ActorHand(job.workerId(), lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN),
                "minecraft:bread", 64))));
        assertEquals(BakeryWorkState.Phase.DEPOT_DELIVERY, state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().get(task.id()).status());
        SceneLease currentLease = state.sceneLeases().get(leaseId);
        assertInstanceOf(SceneContinuation.None.class, FrontierSceneBehaviors.releasePlan(state, currentLease, 700L,
                new SceneLeaseReleased(leaseId, List.of(new SceneMemberPosition(job.workerId(),
                        station.workerStation().standingBody(), actor.condition().health())))).continuation(),
                "bakery release must leave its retained completion review intact, not invoke legacy output-ready continuation");
        // The real shared depot already contains bread when a later batch arrives.
        // Its physical receipt describes the whole occupied layout, not only the
        // baker's newly prepared destination slot.
        var existingBreadId = new SubjectId("lot:bakery-hot-existing-bread");
        var existingBread = new ResourceLot(existingBreadId, task.ownerId(), "minecraft:bread", 59,
                "test-existing-depot-bread", List.of());
        FungibleResourceLedger stocked = state.inventory().fungibleResources().issue(existingBread,
                new CustodyAccount(work.destinationAccountId(), new ResourceCustody.Container(depot),
                        Map.of(existingBreadId, 59), Map.of()));
        var firstBread = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:bread", 59);
        stocked = stocked.rebind(work.destinationAccountId(), 1L,
                FungiblePhysicalObservation.bind(stocked, work.destinationAccountId(), 1L, List.of(firstBread)));
        state = state.withInventory(state.inventory().withFungibleResources(stocked));
        int deliverySlot = state.firstFreeContainerSlot(depot).orElseThrow();
        state = at(state, leaseId, job.workerId(), actor.body());
        BakeryWorkBlock fullDepot = new BakeryWorkBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED,
                depot, -1, "minecraft:air", 0);
        state = ProductionProcess.reduceBakeryHotBlockChanged(state, task.ownerId(),
                new BakeryHotBlockChanged(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_DELIVERY,
                        Optional.of(fullDepot)));
        FrontierWorldState blockedDelivery = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectPrepared(
                blockedDelivery, task.ownerId(), new BakeryHotEffectPrepared(job.id(), leaseId,
                        BakeryWorkState.Phase.DEPOT_DELIVERY, deliverySlot)));
        state = ProductionProcess.reduceBakeryHotBlockChanged(state, task.ownerId(),
                new BakeryHotBlockChanged(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_DELIVERY, Optional.empty()));
        FrontierWorldState beforeDelivery = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectPrepared(
                beforeDelivery, task.ownerId(), new BakeryHotEffectPrepared(job.id(), leaseId,
                        BakeryWorkState.Phase.DEPOT_DELIVERY, 20)),
                "an occupied depot slot cannot be selected for bread delivery");
        state = ProductionProcess.reduceBakeryHotEffectPrepared(state, task.ownerId(),
                new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_DELIVERY, deliverySlot));
        FrontierWorldState beforeObservedDelivery = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotEffectObserved(
                beforeObservedDelivery, task.ownerId(), new BakeryHotEffectObserved(
                        job.id(), leaseId, BakeryWorkState.Phase.DEPOT_DELIVERY, actor.body(),
                        1L, 1L, List.of(), List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, deliverySlot)),
                        "minecraft:bread", 64)))), "an observed receipt cannot erase existing depot bread");
        state = ProductionProcess.reduceBakeryHotEffectObserved(state, task.ownerId(), new BakeryHotEffectObserved(
                job.id(), leaseId, BakeryWorkState.Phase.DEPOT_DELIVERY, actor.body(),
                1L, 1L, List.of(), List.of(firstBread, new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, deliverySlot)),
                "minecraft:bread", 64))));
        assertEquals(BakeryWorkState.Phase.DELIVERED, state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        assertEquals(StrategicTaskStatus.ACTIVE, state.strategicPlans().tasks().get(task.id()).status());
        // The input hold still names the historical wheat source, but this depot
        // now owns bread under a newer physical authority. Its checkpoint must
        // not mistake the departed input hold for a live epoch-1 wheat claim.
        assertEquals(work.sourceAccountId(), work.destinationAccountId());
        FungibleResourceLedger deliveredResources = state.inventory().fungibleResources();
        deliveredResources = deliveredResources.releaseBindings(work.destinationAccountId(), 1L);
        var deliveredBread = new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, deliverySlot)),
                "minecraft:bread", 64);
        deliveredResources = deliveredResources.rebind(work.destinationAccountId(), 2L,
                FungiblePhysicalObservation.bind(deliveredResources, work.destinationAccountId(), 2L,
                        List.of(firstBread, deliveredBread)));
        FrontierWorldState renewedDepot = state.withInventory(state.inventory().withFungibleResources(deliveredResources));
        assertDoesNotThrow(() -> ProductionResourceCustody.bind(renewedDepot, work.destinationAccountId(), 2L,
                renewedDepot.inventory().fungibleResources().bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(work.destinationAccountId())).toList()));
        FrontierWorldState checkpointedDepot = ProductionResourceCustody.release(renewedDepot, work.destinationAccountId(), 2L);
        assertTrue(checkpointedDepot.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(binding -> binding.accountId().equals(work.destinationAccountId())));
        assertEquals(BakeryWorkState.Phase.DELIVERED,
                checkpointedDepot.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        assertFalse(ServiceAccessCoordinator.depotAvailableForMeal(checkpointedDepot, depot,
                checkpointedDepot.bootstrap().settlements().getFirst().residents().getFirst().id()),
                "delivered bread does not release the shared entrance until the baker leaves it");
        ServiceAccessBoundary access = SettlementDepotServicePort.forDepot(
                checkpointedDepot.bootstrap().settlements().getFirst().structures().stream()
                        .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow())
                .accessBoundary();
        SurfaceAnchor exit = BakeryKnownNavigation.pathFrom(checkpointedDepot,
                        checkpointedDepot.productionJobs().get(job.id()),
                        checkpointedDepot.sceneLeases().get(leaseId).memberBody(checkpointedDepot.actorLocations(), job.workerId()).supportingSurface())
                .stream().filter(surface -> access.cleared(surface.standingBody())).findFirst().orElseThrow();
        BakeryHotAccessCleared exitObserved = new BakeryHotAccessCleared(job.id(), leaseId, exit.standingBody());
        assertEquals(exitObserved, FrontierWorldRuntimeDefinition.payloadCodecs().decode(exitObserved.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(exitObserved)));
        FrontierWorldState departedDepot = ProductionProcess.reduceBakeryHotAccessCleared(checkpointedDepot,
                task.ownerId(), exitObserved);
        assertTrue(departedDepot.productionJobs().containsKey(job.id()),
                "leaving shared access must not complete the bakery job");
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(departedDepot, depot,
                departedDepot.bootstrap().settlements().getFirst().residents().getFirst().id()),
                "the next resident may enter before the baker returns to the workshop");
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotAccessCleared(
                departedDepot, task.ownerId(), exitObserved), "one physical exit cannot be applied twice");
        state = state.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING)
                .releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), actor.body(), actor.condition().health())));
        BakeryColdStep finalization = null;
        for (int step = 0; state.productionJobs().containsKey(job.id()) && step < 300; step++) {
            BakeryColdStep clearing = ProductionProcess.planCompletion(state,
                            ProductionProcess.complete(job, 900L + 20L * step)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), clearing);
            finalization = clearing;
        }
        assertEquals(BakeryColdStep.Action.FINALIZE, finalization.action());
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        assertEquals(123, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:bread"),
                "delivery adds 64 bread without replacing the depot's existing 59");
    }

    private static FrontierWorldState at(FrontierWorldState state, SceneLeaseId leaseId,
                                         SubjectId actor, BodyPosition body) {
        Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(actor, actors.get(actor).withBody(body));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
    }
}
