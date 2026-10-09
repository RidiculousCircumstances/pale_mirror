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
        var carriedResources = state.inventory().fungibleResources();
        carriedResources = carriedResources.releaseBindings(work.actorAccountId(), 1L);
        carriedResources = carriedResources.rebind(work.actorAccountId(), 37L,
                FungiblePhysicalObservation.bind(carriedResources, work.actorAccountId(), 37L, List.of(hand)));
        state = state.withInventory(state.inventory().withFungibleResources(carriedResources));
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
        var waitingMeal = withRetainedMeal(state, job.workerId(), job.settlementId())
                .transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        var hungryRecovery = io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                waitingMeal, task.ownerId(), reconciled);
        assertEquals(SceneLeaseStatus.HOT, hungryRecovery.sceneLeases().get(leaseId).status());
        assertEquals(waitingMeal.humanPopulation().meals(), hungryRecovery.humanPopulation().meals(),
                "a pending meal cannot deadlock recovery or be canceled to recover work");
        assertEquals(waitingMeal.inventory(), hungryRecovery.inventory(), "carried work stock stays exact");
        assertTrue(ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(hungryRecovery, job.workerId(), 27_000L),
                "the ordinary work owner must now yield to the retained meal");
        var beforeBinding = waitingMeal.withInventory(waitingMeal.inventory().withFungibleResources(
                waitingMeal.inventory().fungibleResources().releaseBindings(work.actorAccountId(), 37L)));
        var projectedRecovery = io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                beforeBinding, task.ownerId(), reconciled);
        var rebound = ActorCarriedResources.requireBinding(projectedRecovery.inventory().fungibleResources(),
                job.workerId(), work.actorAccountId(), hand);
        assertEquals(lease.revision(), rebound.authorityEpoch(), "only an absent binding uses the ordinary scene issuer");
        assertEquals(beforeBinding.inventory().fungibleResources().accounts(),
                projectedRecovery.inventory().fungibleResources().accounts(), "projection cannot alter lots or claims");
        assertEquals(beforeBinding.humanPopulation().meals(), projectedRecovery.humanPopulation().meals());
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                conflicted, task.ownerId(), new BakerySceneReconciled(job.id(), leaseId, lease.revision(),
                        fence.authorityEpoch(), actor.body(), new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", 63))));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                resumed, task.ownerId(), reconciled), "a duplicate inspection cannot reset the fence");
        SubjectId nextResident = state.bootstrap().settlements().getFirst().residents().stream()
                .map(Resident::id).filter(id -> !id.equals(job.workerId())).findFirst().orElseThrow();
        assertFalse(ResidentMealServiceAccess.available(state, depot, nextResident),
                "the HOT worker still owns the depot approach after the pickup phase changes");
        var accessAfterPickup = ServiceAccessCoordinator.boundary(state, depot);
        SurfaceAnchor pickupExit = BakeryKnownNavigation.pathFrom(state, state.productionJobs().get(job.id()),
                        state.actorLocations().get(job.workerId()).supportingSurface()).stream()
                .filter(surface -> accessAfterPickup.cleared(surface.standingBody())).findFirst().orElseThrow();
        ProductionJob pickedUp = state.productionJobs().get(job.id());
        assertFalse(ProductionServiceAccess.witnessedExit(state, pickedUp, actor.body()),
                "phase change alone cannot release physical access");
        assertTrue(ProductionServiceAccess.witnessedExit(state, pickedUp, pickupExit.standingBody()),
                "input departure must be observed before station arrival, not just after final delivery");
        var inputExit = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(
                ActorBodyAuthority.current(state, job.workerId()),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                actor.body(), actor.condition().health(), pickupExit.standingBody(), actor.condition().health(),
                state.actorExecutions().actors().get(job.workerId()).current());
        FrontierWorldState inputDeparted = ActorBodyAuthority.inspected(state, inputExit);
        assertTrue(ResidentMealServiceAccess.available(inputDeparted, depot, nextResident),
                "a baker stalled beyond the access boundary must not block the depot");
        assertEquals(state.productionJobs(), inputDeparted.productionJobs());
        assertEquals(state.inventory(), inputDeparted.inventory(), "exit does not consume or abandon carried input");
        assertEquals(inputDeparted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(inputDeparted)));
        assertFalse(ProductionServiceAccess.witnessedExit(inputDeparted, pickedUp, pickupExit.standingBody()),
                "an already observed exit cannot produce another access transition");
        assertEquals(new ResourceCustody.Actor(job.workerId()), state.inventory().fungibleResources().accounts()
                .get(work.actorAccountId()).custody());
        FrontierWorldState blockedWithCargo = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        var release = new SceneLeaseReleased(leaseId, List.of(new SceneMemberPosition(job.workerId(), actor.body(),
                actor.condition().health())));
        var handRelease = new BakeryHotHandRelease(job.id(), work.actorAccountId(), 37L, hand, release);
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
        assertTrue(ResidentMealServiceAccess.available(state, depot, nextResident),
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
                37L, 1L, List.of(), List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(machine, station.inputSlot())),
                "minecraft:wheat", 64))));
        assertEquals(BakeryWorkState.Phase.PROCESSING, state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        for (int count = 1; count <= ProductionWorkProgress.REQUIRED_PROCESSING_TICKS; count++)
            state = ProductionProcess.reduceBakeryHotWorkTick(state, task.ownerId(),
                    new BakeryHotWorkTick(job.id(), leaseId, station.workerStation().standingBody(), count));
        assertStationConflictCanReleaseHungryBaker(state, job.id(), leaseId);
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
        assertPreparedDeliveryReservationAndCancellation(state, job.id(), leaseId, deliverySlot);
        var pendingDelivery = state;
        var blockedInventory = pendingDelivery.inventory();
        while (blockedInventory.firstFreeSlot(depot).isPresent()) {
            int slot = blockedInventory.firstFreeSlot(depot).orElseThrow();
            blockedInventory = blockedInventory.store(new ExactItemStack(
                    new SubjectId("item:prepared-delivery-capacity-" + slot), task.ownerId(),
                    "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        var pendingFull = pendingDelivery.withInventory(blockedInventory);
        assertTrue(ProductionOutputCapacity.deliverySlot(pendingFull, pendingFull.productionJobs().get(job.id())).isEmpty());
        assertTrue(ProductionServiceAccess.available(pendingFull, pendingFull.productionJobs().get(job.id())),
                "external capacity loss cannot revoke an already prepared physical transfer's service fence");
        assertFalse(ActorSpatialCourtesy.assess(pendingFull,
                pendingFull.actorExecutions().actors().get(job.workerId()).current().orElseThrow()).ready());
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
        // A delivered baker may still carry personal inventory in OFF. That account
        // is owned by the common body, not the now-empty bakery work hand.
        var personalLot = new SubjectId("lot:bakery-foreign-offhand");
        var personalAccount = new SubjectId("custody:bakery-foreign-offhand");
        var personalResources = state.inventory().fungibleResources().issue(
                new ResourceLot(personalLot, job.settlementId(), "minecraft:stone", 64, "fixture:personal-stone", List.of()),
                new CustodyAccount(personalAccount, new ResourceCustody.Actor(job.workerId()), Map.of(personalLot, 64), Map.of()));
        var personalBinding = new PhysicalStackBinding(new SubjectId("binding:bakery-foreign-offhand"), personalAccount,
                new PhysicalStackAddress.ActorHand(job.workerId(), state.sceneLeases().get(leaseId).members().getFirst().entityId(),
                        ActorContainerItemOrder.Hand.OFF), 7L, "minecraft:stone", Map.of(personalLot, 64), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(
                personalResources.rebind(personalAccount, 7L, List.of(personalBinding))));
        assertFalse(FrontierSceneLeaseStateSupport.hasBoundSceneHand(state, state.sceneLeases().get(leaseId)));
        var deliveredGoal = BakeryWorkGoal.current(state, state.productionJobs().get(job.id()));
        assertThrows(IllegalArgumentException.class, deliveredGoal::movementOrder,
                "a delivered job cannot retain an exact exit-cell obligation");
        var firstExit = BakeryKnownNavigation.path(state, state.productionJobs().get(job.id()));
        assertTrue(ServiceAccessCoordinator.boundary(state, depot).cleared(firstExit.getLast().standingBody()));
        var exitObstructed = state.recordPhysicalDelta(new PhysicalDelta(firstExit.getLast().support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:bakery-preferred-exit-blocked"));
        var alternativeExit = BakeryKnownNavigation.path(exitObstructed, exitObstructed.productionJobs().get(job.id()));
        assertNotEquals(firstExit.getLast(), alternativeExit.getLast());
        assertTrue(ServiceAccessCoordinator.boundary(exitObstructed, depot).cleared(alternativeExit.getLast().standingBody()));
        assertEquals(state.inventory(), exitObstructed.inventory(), "clearance planning cannot replay delivery");
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
        assertFalse(ResidentMealServiceAccess.available(checkpointedDepot, depot,
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
        ActorLocation beforeExit = checkpointedDepot.actorLocations().get(job.workerId());
        var exitObserved = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(
                ActorBodyAuthority.current(checkpointedDepot, job.workerId()),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                beforeExit.body(), beforeExit.condition().health(), exit.standingBody(), beforeExit.condition().health(),
                checkpointedDepot.actorExecutions().actors().get(job.workerId()).current());
        assertEquals(exitObserved, FrontierWorldRuntimeDefinition.payloadCodecs().decode(exitObserved.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(exitObserved)));
        FrontierWorldState departedDepot = ActorBodyAuthority.inspected(checkpointedDepot, exitObserved);
        assertTrue(departedDepot.productionJobs().containsKey(job.id()),
                "leaving shared access must not complete the bakery job");
        assertTrue(ResidentMealServiceAccess.available(departedDepot, depot,
                departedDepot.bootstrap().settlements().getFirst().residents().getFirst().id()),
                "the next resident may enter before the baker returns to the workshop");
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.inspected(
                departedDepot, exitObserved), "one physical exit cannot be applied twice");
        state = state.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING)
                .releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), actor.body(), actor.condition().health())));
        assertTrue(ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 900L)).stream()
                .noneMatch(event -> event.payload() instanceof BakeryColdStep),
                "closing presentation cannot grant COLD movement over a retained physical body");
        assertTrue(FrontierProductionWorkSceneSupport.candidate(state, state.productionJobs().get(job.id())).isPresent(),
                "an inside delivered worker can reopen its exact scene to clear service access");
        var recoveredClearance = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertTrue(ProductionProcess.planCompletion(recoveredClearance, ProductionProcess.complete(job, 900L)).stream()
                .noneMatch(event -> event.payload() instanceof BakeryColdStep));
        state = departedDepot.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING)
                .releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), exit.standingBody(),
                        departedDepot.actorLocations().get(job.workerId()).condition().health())));
        var actualBodies = state.actorLocations();
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
        assertEquals(actualBodies, state.actorLocations(), "job finalization never sends the baker back to the workshop");
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        assertEquals(123, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:bread"),
                "delivery adds 64 bread without replacing the depot's existing 59");
        assertEquals(personalBinding, state.inventory().fungibleResources().bindings().get(personalBinding.id()));
        assertEquals(personalBinding, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state))
                .inventory().fungibleResources().bindings().get(personalBinding.id()));
    }

    private static void assertPreparedDeliveryReservationAndCancellation(FrontierWorldState pending, SubjectId jobId,
                                                                        SceneLeaseId leaseId, int slot) {
        var job = pending.productionJobs().get(jobId);
        var depot = FrontierWorldState.depotId(job.settlementId());
        var address = new InventoryCustody.ContainerSlot(depot, slot);
        assertTrue(pending.reservedContainerSlots(depot).contains(slot));
        assertFalse(pending.containerSlotAvailable(address), "other owners cannot steal a prepared destination");
        assertTrue(pending.containerSlotAvailableForOutput(address, jobId), "the declaring owner can finish its own effect");
        assertFalse(pending.pendingContainerInbound(depot).containsKey(job.outputItemKind()),
                "one slot-backed batch must not also consume pooled inbound capacity");
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(pending));
        assertEquals(pending.reservedContainerSlots(depot), recovered.reservedContainerSlots(depot));

        // The real historical interleave: prepare baker destination, then admit a field job.
        var concurrent = ResourceSiteHarvestProcessTest.ready(pending);
        var siteId = new SubjectId("site:1-wheat-field");
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(concurrent,
                StrategicObjectiveProcess.resourceHarvestOpportunity(concurrent, concurrent.resourceSites().site(siteId), 6_000L));
        concurrent = StrategicObjectiveProcess.reduceObjective(concurrent, job.settlementId(),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        concurrent = StrategicObjectiveProcess.reduceTask(concurrent, job.settlementId(),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        var fieldTask = concurrent.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        var start = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.plan(concurrent,
                io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.start(fieldTask, 6_100L));
        var field = start.stream().map(ProposedEvent::payload).filter(ResourceSiteHarvestStarted.class::isInstance)
                .map(ResourceSiteHarvestStarted.class::cast).findFirst().orElseThrow().job();
        assertNotEquals(address, field.outputSlot(), "new field admission must choose another exact slot");
        concurrent = StrategicObjectiveProcess.reduceTaskTransition(concurrent, job.settlementId(),
                start.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                        .map(StrategicTaskTransition.class::cast).findFirst().orElseThrow());
        concurrent = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceStarted(concurrent, siteId,
                new ResourceSiteHarvestStarted(field));
        var deliveredLayout = new java.util.ArrayList<FungiblePhysicalObservation.Stack>();
        for (var value : concurrent.inventory().fungibleResources().bindings().values()) {
            if (value.accountId().equals(ReferenceContainerCustody.scopeId(depot)))
                deliveredLayout.add(new FungiblePhysicalObservation.Stack(value.address(), value.itemKind(), value.quantity()));
        }
        deliveredLayout.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(address),
                job.outputItemKind(), job.outputCount()));
        var confirmed = ProductionProcess.reduceBakeryHotEffectObserved(concurrent, job.settlementId(),
                new BakeryHotEffectObserved(jobId, leaseId, BakeryWorkState.Phase.DEPOT_DELIVERY,
                        concurrent.actorLocations().get(job.workerId()).body(), 1L, 1L, List.of(), deliveredLayout));
        assertEquals(BakeryWorkState.Phase.DELIVERED, confirmed.productionJobs().get(jobId).bakeryWork().orElseThrow().phase());
        assertTrue(confirmed.reservedContainerSlots(depot).contains(field.outputSlot().slot()));
        assertEquals(confirmed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmed)));

        var work = job.bakeryWork().orElseThrow();
        var binding = pending.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(work.actorAccountId())).findFirst().orElseThrow();
        var hand = new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity());
        var abort = new BakeryHotDeliveryAborted(jobId, leaseId, slot, binding.authorityEpoch(), hand, Optional.empty(),
                new BakeryWorkBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED, depot, slot, "minecraft:stone", 1));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(abort, codecs.decode(abort.type(), codecs.encode(abort)));
        var cancelled = ProductionProcess.reduceBakeryHotDeliveryAborted(recovered, job.settlementId(), abort);
        assertEquals(recovered.inventory(), cancelled.inventory(), "cancellation cannot debit cargo or credit player stock");
        assertEquals(job.workerId(), cancelled.productionJobs().get(jobId).workerId());
        assertTrue(cancelled.productionJobs().get(jobId).bakeryWork().orElseThrow().pendingPhysicalStep().isEmpty());
        assertFalse(cancelled.reservedContainerSlots(depot).contains(slot));
        assertEquals(Map.of(job.outputItemKind(), (long) job.outputCount()), cancelled.pendingContainerInbound(depot));
        var classifiedPlayerStock = cancelled.withInventory(cancelled.inventory().store(new ExactItemStack(
                new SubjectId("item:player-occupied-bakery-slot"), job.settlementId(), "minecraft:stone", 1, address)));
        int replacement = ProductionOutputCapacity.deliverySlot(classifiedPlayerStock,
                classifiedPlayerStock.productionJobs().get(jobId)).orElseThrow();
        assertNotEquals(slot, replacement, "a classified occupied slot must be excluded from the new preparation");
        var reopened = ProductionProcess.reduceBakeryHotBlockChanged(classifiedPlayerStock, job.settlementId(),
                new BakeryHotBlockChanged(jobId, leaseId, work.phase(), Optional.empty()));
        var replanned = ProductionProcess.reduceBakeryHotEffectPrepared(reopened, job.settlementId(),
                new BakeryHotEffectPrepared(jobId, leaseId, work.phase(), replacement));
        assertEquals(replacement, replanned.productionJobs().get(jobId).bakeryWork().orElseThrow()
                .pendingPhysicalStep().orElseThrow().destinationSlot());
        assertEquals(binding, replanned.inventory().fungibleResources().bindings().get(binding.id()));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotDeliveryAborted(
                cancelled, job.settlementId(), abort), "the same cancellation is not replayable");
        var stale = new BakeryHotDeliveryAborted(jobId, leaseId, slot, binding.authorityEpoch() + 1, hand, Optional.empty(), abort.occupiedDestination());
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotDeliveryAborted(
                recovered, job.settlementId(), stale));
        var ambiguous = ProductionProcess.reduceBakeryHotBlockChanged(recovered, job.settlementId(),
                new BakeryHotBlockChanged(jobId, leaseId, work.phase(), Optional.of(new BakeryWorkBlock(
                        BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT, depot, slot, "minecraft:air", 0))));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryHotDeliveryAborted(
                ambiguous, job.settlementId(), abort), "an already ambiguous effect must never be retargeted");
    }

    private static void assertStationConflictCanReleaseHungryBaker(FrontierWorldState input, SubjectId jobId, SceneLeaseId leaseId) {
        var job = input.productionJobs().get(jobId);
        var actorId = job.workerId();
        var personalLot = new SubjectId("lot:station-recovery-personal-stone");
        var personalAccount = new SubjectId("custody:station-recovery-personal-stone");
        var resources = input.inventory().fungibleResources().issue(
                new ResourceLot(personalLot, job.settlementId(), "minecraft:stone", 64, "fixture:personal-stone", List.of()),
                new CustodyAccount(personalAccount, new ResourceCustody.Actor(actorId), Map.of(personalLot, 64), Map.of()));
        var personalBinding = new PhysicalStackBinding(new SubjectId("binding:station-recovery-personal-stone"), personalAccount,
                new PhysicalStackAddress.ActorHand(actorId, input.sceneLeases().get(leaseId).members().getFirst().entityId(),
                        ActorContainerItemOrder.Hand.OFF), 7L, "minecraft:stone", Map.of(personalLot, 64), Map.of());
        input = input.withInventory(input.inventory().withFungibleResources(
                resources.rebind(personalAccount, 7L, List.of(personalBinding))));
        var hungry = withRetainedMeal(input, actorId, job.settlementId());
        var conflicted = hungry.transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        var lease = conflicted.sceneLeases().get(leaseId);
        var actor = conflicted.actorLocations().get(actorId);
        var fence = ActorBodyAuthority.require(conflicted, ActorBodyAuthority.current(conflicted, actorId));
        var receipt = new BakeryStationSceneReconciled(jobId, leaseId, lease.revision(), fence.authorityEpoch(),
                lease.members().getFirst().entityId(), actor.body(), BakeryWorkState.Phase.PROCESSING);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var base = FrontierWorldRuntimeDefinition.configuration(conflicted.bootstrap().worldId(), 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(conflicted.bootstrap().worldId(), conflicted,
                new SimInstant(27_000L), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        var command = new CommandId("command:station-conflict-release");
        var checkpoint = engine.checkpoint();
        var result = engine.submit(new FrontierCommand(1, command, conflicted.bootstrap().worldId(), checkpoint.revision(),
                checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), receipt));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        var draining = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.DRAINING, draining.sceneLeases().get(leaseId).status());
        assertEquals(conflicted.inventory(), draining.inventory(), "recovery cannot move station cargo");
        assertEquals(conflicted.productionJobs(), draining.productionJobs(), "recovery cannot replay processing");
        assertEquals(conflicted.humanPopulation().meals(), draining.humanPopulation().meals(), "retained meal is preserved");
        var closed = draining.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(actorId, actor.body(), actor.condition().health())));
        assertTrue(FrontierSceneAdmission.available(closed, List.of(actorId)), "the exact body is now available for its retained meal");
        assertFalse(closed.sceneLeases().get(leaseId).retainsMemberCustody(actorId));
        assertEquals(closed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(closed)));
        var oldReview = ProductionProcess.complete(closed.productionJobs().get(jobId), 400L);
        var retry = base.scheduledPlanner().plan(closed, oldReview, new SimInstant(27_100L));
        assertEquals(1, retry.size());
        var rescheduled = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                retry.getFirst().payload());
        assertEquals(oldReview.id(), rescheduled.scheduleId());
        assertEquals(27_120L, rescheduled.replacement().dueAt().ticks(),
                "a resumed job must yield the queue to its meal, not replay historical retries");
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation
                .reduce(conflicted, job.settlementId(), new BakeryStationSceneReconciled(jobId, leaseId, lease.revision(),
                        fence.authorityEpoch() + 1, receipt.entityId(), actor.body(), receipt.phase())));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation
                .reduce(conflicted, job.settlementId(), new BakeryStationSceneReconciled(jobId, leaseId, lease.revision(),
                        fence.authorityEpoch(), java.util.UUID.randomUUID(), actor.body(), receipt.phase())));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation
                .reduce(draining, job.settlementId(), receipt), "duplicate recovery cannot reset custody");
        var ambiguous = ActorBodyAuthority.isolate(conflicted, ActorBodyAuthority.current(conflicted, actorId), "restart body needs inspection");
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation
                .reduce(ambiguous, job.settlementId(), receipt), "an indexed assertion alone cannot resolve restart ambiguity");
        var savedReceipt = new BakeryStationSceneReconciled(jobId, leaseId, lease.revision(), fence.authorityEpoch(),
                receipt.entityId(), actor.body(), receipt.phase(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.SAVED_DEPARTURE);
        assertEquals(savedReceipt, codecs.decode(savedReceipt.type(), codecs.encode(savedReceipt)));
        var savedDrain = io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation.reduce(
                ambiguous, job.settlementId(), savedReceipt);
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, ActorBodyAuthority.require(savedDrain,
                ActorBodyAuthority.current(savedDrain, actorId)).phase(), "scene recovery cannot grant a loaded body");
        assertEquals(SceneLeaseStatus.DRAINING, savedDrain.sceneLeases().get(leaseId).status());
        assertEquals(personalBinding, savedDrain.inventory().fungibleResources().bindings().get(personalBinding.id()),
                "saved station recovery cannot discard the baker's personal OFF hand");
        assertEquals(ambiguous.fencedRecovery(), savedDrain.fencedRecovery());
        var station = input.inventory().containers().values().stream().flatMap(value -> value.productionStation().stream())
                .filter(value -> value.id().equals(job.bakeryWork().orElseThrow().stationId())).findFirst().orElseThrow();
        var pending = ProductionProcess.reduceBakeryHotEffectPrepared(input, job.settlementId(),
                new BakeryHotEffectPrepared(jobId, leaseId, BakeryWorkState.Phase.PROCESSING, station.outputSlot()));
        var pendingConflict = pending.transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation
                .reduce(pendingConflict, job.settlementId(), receipt), "a potentially applied recipe is never cleared by empty hands");
    }

    private static FrontierWorldState withRetainedMeal(FrontierWorldState input, SubjectId actorId, SubjectId settlementId) {
        var prior = input.inventory().fungibleResources();
        var breadId = new SubjectId("lot:station-recovery-food");
        var depot = FrontierWorldState.depotId(settlementId);
        var accountId = ReferenceContainerCustody.scopeId(depot);
        var lots = new java.util.LinkedHashMap<>(prior.lots());
        lots.put(breadId, new ResourceLot(breadId, settlementId, "minecraft:bread", 4, "test", List.of()));
        var accounts = new java.util.LinkedHashMap<>(prior.accounts());
        accounts.put(accountId, new CustodyAccount(accountId, new ResourceCustody.Container(depot), Map.of(breadId, 4), Map.of()));
        var hungry = input.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(input.inventory().withFungibleResources(new FungibleResourceLedger(lots, prior.claims(), accounts, prior.bindings())))
                .humanPopulation(input.humanPopulation().accrueHunger(actorId, 27_000L)));
        // The meal is selected while the food source is canonical, before its
        // physical depot is acquired. Loading it later must preserve this meal.
        var canonicalSource = hungry.withChanges(FrontierWorldStateUpdate.begin()
                .replicaCustody(PhysicalReplicaCustodyState.empty()));
        var job = input.productionJobs().values().stream().filter(value -> value.workerId().equals(actorId))
                .findFirst().orElseThrow();
        canonicalSource = at(canonicalSource, null, actorId, BakeryWorkGoal.current(input, job).station().standingBody());
        var meal = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .selectSourceAtYield(canonicalSource, actorId, 27_000L).orElseThrow(() -> new AssertionError(
                        "fixture meal unavailable: " + ResidentMealOpportunity.candidateAdmission(hungry, actorId, 27_000L)
                        + " choice=" + ResidentActivityCoordinator.assess(hungry, actorId, 27_000L)));
        return io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceStarted(canonicalSource, actorId, meal)
                .withChanges(FrontierWorldStateUpdate.begin().replicaCustody(input.replicaCustody())
                        .actorLocations(input.actorLocations()));
    }

    private static FrontierWorldState at(FrontierWorldState state, SceneLeaseId leaseId,
                                         SubjectId actor, BodyPosition body) {
        Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(actor, actors.get(actor).withBody(body));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
    }
}
