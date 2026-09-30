package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.BakeryProcess;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BakeryColdVerticalTest {
    @Test
    void hungryBakerYieldsAtEmptyHandAndResumesSameJobAfterColdMeal() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-resident-meal"), 41L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        SubjectId resident = job.workerId();
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        SubjectId accountId = ReferenceContainerCustody.scopeId(depot);
        SubjectId breadId = new SubjectId("lot:bakery-resident-meal-bread");
        FungibleResourceLedger prior = state.inventory().fungibleResources();
        var lots = new java.util.LinkedHashMap<>(prior.lots());
        lots.put(breadId, new ResourceLot(breadId, job.settlementId(), "minecraft:bread", 4, "test", List.of()));
        var accounts = new java.util.LinkedHashMap<>(prior.accounts());
        CustodyAccount account = accounts.get(accountId);
        var quantities = new java.util.LinkedHashMap<>(account.lotQuantities());
        quantities.put(breadId, 4);
        accounts.put(accountId, new CustodyAccount(accountId, account.custody(), quantities, account.claimQuantities()));
        state = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(new FungibleResourceLedger(
                        lots, prior.claims(), accounts, prior.bindings())))
                .humanPopulation(state.humanPopulation().accrueHunger(resident, 27_000L)));
        SubjectId otherResident = state.bootstrap().settlements().stream()
                .filter(value -> value.id().equals(job.settlementId())).findFirst().orElseThrow()
                .residents().stream().map(value -> value.id()).filter(id -> !id.equals(resident))
                .findFirst().orElseThrow();
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, otherResident),
                "a baker travelling to the depot does not occupy its physical service throat");
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, resident),
                "the retained baker may safely yield the same service turn to their own meal");
        assertEquals(ResidentWorkYield.Status.READY, ResidentWorkYield.assess(state,
                HumanAssignmentProjection.compile(state).assignment(resident)).status());
        ResidentMealStarted meal = ResidentMealProcess.selectSourceAtYield(state, resident, 27_000L).orElseThrow();
        assertEquals(Optional.of(job.id()), meal.meal().retainedWorkOwner());
        state = ResidentMealProcess.reduceStarted(state, resident, meal);
        assertFalse(ServiceAccessCoordinator.depotAvailableForWork(state, depot, job.id(), resident));
        assertTrue(FrontierProductionWorkSceneSupport.candidate(state, job).isEmpty());
        var held = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 27_001L));
        assertEquals(1, held.size());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                held.getFirst().payload());
        long mealTick = 27_002L;
        for (int turn = 0; turn < 24 && state.humanPopulation().meals().containsKey(resident); turn++) {
            var currentMeal = state.humanPopulation().meals().get(resident);
            if (currentMeal.coldTravel().isPresent())
                mealTick = Math.max(mealTick, currentMeal.coldTravel().orElseThrow().arrivalTick());
            FrontierWorldState beforeMealStep = state;
            long atMealTick = mealTick;
            ResidentMealColdStep step = ResidentMealProcess.planColdStep(state, resident, mealTick)
                    .orElseThrow(() -> new AssertionError("meal=" + beforeMealStep.humanPopulation().meals().get(resident)
                            + " body=" + beforeMealStep.actorLocations().get(resident).body()
                            + " at=" + atMealTick + " depotAvailable="
                            + ServiceAccessCoordinator.depotAvailableForMeal(beforeMealStep, depot, resident)));
            state = ResidentMealProcess.reduceColdStep(state, resident, step);
            mealTick++;
        }
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertTrue(ServiceAccessCoordinator.depotAvailableForWork(state, depot, job.id(), resident));
        assertEquals(job, state.productionJobs().get(job.id()));
        assertEquals(3, state.inventory().fungibleResources().totalQuantity(job.settlementId(), "minecraft:bread"));
        assertTrue(ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 27_300L))
                .stream().anyMatch(event -> event.payload() instanceof BakeryColdStep));
    }

    @Test
    void fullDepotWaitsForColdBreadDeliveryWithoutQuarantiningAndResumesAfterSpaceReturns() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-full-depot-delivery"), 41L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        long due = 300L;
        for (int turn = 0; turn < 700; turn++, due += 20L) {
            ProductionJob current = state.productionJobs().get(job.id());
            if (current.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_DELIVERY) break;
            BakeryColdStep step = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            if (step.action() == BakeryColdStep.Action.PICKUP) {
                SubjectId other = state.bootstrap().settlements().getFirst().residents().stream()
                        .map(Resident::id).filter(id -> !id.equals(job.workerId())).findFirst().orElseThrow();
                assertFalse(ServiceAccessCoordinator.depotAvailableForMeal(state,
                        FrontierWorldState.depotId(task.ownerId()), other),
                        "pickup changes the work phase before the same baker has cleared the depot");
            }
        }
        assertEquals(BakeryWorkState.Phase.DEPOT_DELIVERY,
                state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        assertTrue(BakeryKnownNavigation.path(state, state.productionJobs().get(job.id())).size() > 1);
        BakeryColdStep historicalMove = ProductionProcess.planCompletion(state,
                ProductionProcess.complete(state.productionJobs().get(job.id()), due)).stream()
                .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
        assertEquals(BakeryColdStep.Action.MOVE, historicalMove.action());
        SubjectId depot = FrontierWorldState.depotId(task.ownerId());
        ExactInventory inventory = state.inventory();
        while (state.withInventory(inventory).firstFreeContainerSlot(depot).isPresent()) {
            int slot = state.withInventory(inventory).firstFreeContainerSlot(depot).orElseThrow();
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:bakery-capacity-" + slot), task.ownerId(),
                    "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        FrontierWorldState full = state.withInventory(inventory);
        assertFalse(full.inventory().canReceiveFungible(depot, "minecraft:bread", job.outputCount()));
        assertEquals(Optional.of("DEPOT_STORAGE_FULL"), BakeryProcess.coldBlocker(full, full.productionJobs().get(job.id())));
        // A MOVE already committed to the old WAL remains replayable after this planner change.
        full = ProductionProcess.reduceBakeryColdStep(full, task.ownerId(), historicalMove);
        var action = ProductionProcess.complete(job, due + 20L);
        List<ProposedEvent> deferred = ProductionProcess.planCompletion(full, action);
        var retry = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                deferred.getFirst().payload()).replacement();
        assertEquals(1, deferred.size());
        assertEquals(action.id(), retry.id());
        assertEquals(action.dueAt().ticks() + full.bootstrap().ruleset().cadence().strategicReviewInterval(),
                retry.dueAt().ticks());
        SubjectId freed = inventory.items().values().stream().filter(item -> item.itemKind().equals("minecraft:stone"))
                .findFirst().orElseThrow().id();
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(full));
        FrontierWorldState withSpace = recovered.withInventory(recovered.inventory().consumeOne(freed));
        while (BakeryKnownNavigation.path(withSpace, withSpace.productionJobs().get(job.id())).size() > 1) {
            BakeryColdStep move = ProductionProcess.planCompletion(withSpace, retry).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            assertEquals(BakeryColdStep.Action.MOVE, move.action());
            withSpace = ProductionProcess.reduceBakeryColdStep(withSpace, task.ownerId(), move);
        }
        BakeryColdStep deliver = ProductionProcess.planCompletion(withSpace, retry).stream()
                .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
        assertEquals(BakeryColdStep.Action.DELIVER, deliver.action());
        FrontierWorldState completed = ProductionProcess.reduceBakeryColdStep(withSpace, task.ownerId(), deliver);
        assertEquals(BakeryWorkState.Phase.DELIVERED,
                completed.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
        SubjectId waitingResident = completed.bootstrap().settlements().stream()
                .filter(settlement -> settlement.id().equals(job.settlementId())).findFirst().orElseThrow()
                .residents().stream().map(Resident::id).filter(id -> !id.equals(job.workerId()))
                .findFirst().orElseThrow();
        boolean releasedBeforeFinalization = false;
        boolean breadEatenBeforeFinalization = false;
        for (int step = 0; completed.productionJobs().containsKey(job.id()) && step < 300; step++) {
            BakeryColdStep clearing = ProductionProcess.planCompletion(completed,
                            ProductionProcess.complete(job, retry.dueAt().ticks() + 20L * (step + 1)))
                    .stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            completed = ProductionProcess.reduceBakeryColdStep(completed, task.ownerId(), clearing);
            if (completed.productionJobs().containsKey(job.id())) {
                boolean released = ServiceAccessCoordinator.depotAvailableForMeal(completed,
                        FrontierWorldState.depotId(job.settlementId()), waitingResident);
                releasedBeforeFinalization |= released;
                if (released && !breadEatenBeforeFinalization) {
                    completed = completed.withHumanPopulation(completed.humanPopulation()
                            .accrueHunger(waitingResident, 27_000L));
                    ResidentMealStarted meal = ResidentMealProcess.selectSourceAtYield(completed,
                            waitingResident, 27_000L).orElseThrow();
                    assertEquals(job.outputItemId(), meal.meal().lotId());
                    completed = ResidentMealProcess.reduceStarted(completed, waitingResident, meal);
                    long mealTick = 27_001L;
                    for (int mealStep = 0; mealStep < 24
                            && completed.humanPopulation().meals().get(waitingResident).phase() != ResidentMeal.Phase.RETURN; mealStep++) {
                        var currentMeal = completed.humanPopulation().meals().get(waitingResident);
                        if (currentMeal.coldTravel().isPresent())
                            mealTick = Math.max(mealTick, currentMeal.coldTravel().orElseThrow().arrivalTick());
                        Optional<ResidentMealColdStep> planned = ResidentMealProcess.planColdStep(completed,
                                waitingResident, mealTick);
                        assertTrue(planned.isPresent(), "meal cannot advance in "
                                + completed.humanPopulation().meals().get(waitingResident).phase()
                                + " at step " + mealStep);
                        ResidentMealColdStep progress = planned.orElseThrow();
                        completed = ResidentMealProcess.reduceColdStep(completed, waitingResident, progress);
                        mealTick++;
                    }
                    assertEquals(ResidentMeal.Phase.RETURN,
                            completed.humanPopulation().meals().get(waitingResident).phase());
                    assertEquals(63, completed.inventory().fungibleResources()
                            .totalQuantity(job.settlementId(), "minecraft:bread"));
                    assertEquals(BakeryWorkState.Phase.DELIVERED,
                            completed.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
                    breadEatenBeforeFinalization = true;
                }
            }
        }
        assertTrue(releasedBeforeFinalization, "a delivered baker may continue to the workshop without monopolizing an empty depot");
        assertTrue(breadEatenBeforeFinalization, "delivered bread must become available while the baker returns");
        assertFalse(completed.productionJobs().containsKey(job.id()));
        assertEquals(StrategicTaskStatus.COMPLETED, completed.strategicPlans().tasks().get(task.id()).status());
        assertEquals(completed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(completed)));
    }

    @Test
    void hotReleasedBakerCanRouteFromObservedRoadSupport() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-road-handoff"), 41L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        for (int turn = 0; turn < 100; turn++) {
            BakeryColdStep step = ProductionProcess.planCompletion(state,
                    ProductionProcess.complete(started.job(), 300L + turn * 20L)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            if (step.action() == BakeryColdStep.Action.PICKUP) break;
        }
        FrontierWorldState retained = state;
        ProductionJob job = state.productionJobs().get(started.job().id());
        assertEquals(BakeryWorkState.Phase.STATION_LOAD, job.bakeryWork().orElseThrow().phase());
        SurfaceAnchor observed = SurfaceAnchor.at(-349, 63, -326);
        assertDoesNotThrow(() -> BakeryKnownNavigation.pathFrom(retained, job, observed),
                "the observed HOT body must be a valid COLD route origin");
    }

    @Test
    void coldHeldBakerHandRequiresOneObservedHotBodyBinding() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-cold-hand-projection"), 91L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        for (int turn = 0; turn < 100; turn++) {
            BakeryColdStep step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 300L + turn * 20L))
                    .stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            if (step.action() == BakeryColdStep.Action.PICKUP) break;
        }
        BakeryWorkState work = state.productionJobs().get(job.id()).bakeryWork().orElseThrow();
        assertEquals(BakeryWorkState.Phase.STATION_LOAD, work.phase());
        assertTrue(state.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(binding -> binding.accountId().equals(work.actorAccountId())));
        ActorLocation actor = state.actorLocations().get(job.workerId());
        SceneLeaseId leaseId = new SceneLeaseId("lease:bakery-cold-hand-projection");
        SceneLease lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(),
                new ProductionWorkSceneCause(job.id()), actor.supportingSurface().support(),
                new SimInstant(800L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), actor.body()), java.util.Set.of(), Optional.empty());
        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), "minecraft:wheat", 64);
        var observed = new BakeryHotHandMaterialized(job.id(), leaseId, work.actorAccountId(), 1L, hand);
        state = ProductionProcess.reduceBakeryHotHandMaterialized(state, task.ownerId(), observed);
        assertEquals(1, state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(work.actorAccountId())).count());
        FrontierWorldState once = state;
        assertThrows(IllegalArgumentException.class,
                () -> ProductionProcess.reduceBakeryHotHandMaterialized(once, task.ownerId(), observed));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void loadedPickupBindsObservedDepotAndBakerHandWithoutInventingCustody() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-observed-pickup"), 91L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        FungibleResourceLedger cold = state.inventory().fungibleResources();
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        FungiblePhysicalObservation.Stack wheat = new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64);
        FungibleResourceLedger hot = cold.rebind(work.sourceAccountId(), 1L,
                FungiblePhysicalObservation.bind(cold, work.sourceAccountId(), 1L, List.of(wheat)));
        SubjectId claim = hot.accounts().get(work.sourceAccountId()).claimQuantities().keySet().iterator().next();
        ActorContainerItemOrder order = new ActorContainerItemOrder(job.id(), job.workerId(),
                ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(work.sourceAccountId(), new ResourceCustody.Container(depot),
                        work.actorAccountId(), new ResourceCustody.Actor(job.workerId()), Optional.of(claim),
                        "minecraft:wheat", job.inputQuantities()),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(depot),
                BakeryWorkGoal.current(state, job).station(), ActorContainerItemOrder.Hand.MAIN, 1L, 1L);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                UUID.fromString("00000000-0000-0000-0000-000000000111")), "minecraft:wheat", 64);
        FungibleResourceLedger arrived = hot.transferActorOrderObservedStacks(order, 1L, 1L, List.of(), List.of(hand));
        assertFalse(arrived.accounts().containsKey(work.sourceAccountId()));
        assertEquals(new ResourceCustody.Actor(job.workerId()), arrived.accounts().get(work.actorAccountId()).custody());
        assertEquals(64, arrived.accounts().get(work.actorAccountId()).lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
        assertThrows(IllegalArgumentException.class, () -> hot.transferActorOrderObservedStacks(order, 1L, 1L,
                List.of(), List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 2)), "minecraft:wheat", 64))));
    }

    @Test
    void exactWheatAlsoPassesThroughStationWithoutRemoteTransformation() {
        FrontierWorldState state = ProductionProcessTest.productionTask(ProductionProcessTest.withLegacyExactWheat(
                FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:bakery-exact-cold"), 92L))),
                StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        assertInstanceOf(ProductionInputHold.Materialized.class, job.inputHold());
        int count = 0;
        for (long due = 300L; state.productionJobs().containsKey(job.id()) && count < 700; due += 20L, count++) {
            BakeryColdStep step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            if (step.action() == BakeryColdStep.Action.PICKUP) {
                FrontierWorldState retained = state;
                assertThrows(IllegalArgumentException.class, () -> retained.cancelProductionJob(job.id()),
                        "an exact batch in the baker's hand must not lose its owning job");
            }
            if (step.action() == BakeryColdStep.Action.RECIPE) {
                ProductionStationSpec station = state.inventory().containers().values().stream()
                        .flatMap(container -> container.productionStation().stream())
                        .filter(candidate -> candidate.id().equals(job.bakeryWork().orElseThrow().stationId()))
                        .findFirst().orElseThrow();
                assertFalse(state.inventory().items().containsKey(job.consumedItemId()));
                assertEquals(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()),
                        state.inventory().items().get(job.outputItemId()).custody());
            }
        }
        assertTrue(count < 700, "exact bakery job stalled");
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        assertEquals(new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(task.ownerId()), 0),
                state.inventory().items().get(job.outputItemId()).custody());
    }

    @Test
    void oneFreshBreadTaskMovesWheatThroughBakerAndStationBeforeDepositingBread() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-cold-vertical"), 41L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        List<ProposedEvent> start = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        ProductionStarted started = start.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        assertTrue(job.bakeryWork().isPresent());
        SubjectId wheat = job.consumedItemId(), bread = job.outputItemId();
        int pickups = 0, loads = 0, recipes = 0, unloads = 0, deliveries = 0;
        for (long due = 300L, steps = 0; state.productionJobs().containsKey(job.id()) && steps < 700; due += 20L, steps++) {
            List<ProposedEvent> planned = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, due));
            BakeryColdStep step = planned.stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow(() -> new AssertionError("bakery COLD step stalled: " + planned));
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            switch (step.action()) {
                case PICKUP -> { pickups++; assertEquals(new ResourceCustody.Actor(job.workerId()),
                        state.inventory().fungibleResources().accounts().get(job.bakeryWork().orElseThrow().actorAccountId()).custody());
                    FrontierWorldState retained = state;
                    assertThrows(IllegalArgumentException.class, () -> retained.cancelProductionJob(job.id()),
                            "a fungible batch in the baker's hand must not lose its owning job"); }
                case LOAD -> { loads++; assertEquals(new ResourceCustody.Container(state.inventory().containers().values().stream()
                        .flatMap(record -> record.productionStation().stream()).filter(station -> station.id().equals(job.bakeryWork().orElseThrow().stationId()))
                        .findFirst().orElseThrow().containerId()), state.inventory().fungibleResources().accounts()
                        .get(job.bakeryWork().orElseThrow().stationAccountId()).custody()); }
                case RECIPE -> { recipes++; assertFalse(state.inventory().fungibleResources().lots().containsKey(wheat));
                        assertTrue(state.inventory().fungibleResources().lots().containsKey(bread)); }
                case UNLOAD -> { unloads++; assertEquals(new ResourceCustody.Actor(job.workerId()),
                        state.inventory().fungibleResources().accounts().get(job.bakeryWork().orElseThrow().actorAccountId()).custody()); }
                case DELIVER -> deliveries++;
                default -> { }
            }
        }
        String remaining = state.productionJobs().containsKey(job.id())
                ? "phase=" + state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase()
                + " actor=" + state.actorLocations().get(job.workerId()).supportingSurface()
                + " route=" + BakeryKnownNavigation.path(state, state.productionJobs().get(job.id())).subList(0,
                Math.min(5, BakeryKnownNavigation.path(state, state.productionJobs().get(job.id())).size()))
                + " depot=" + SettlementDepotServicePort.forDepot(state.bootstrap().settlements().getFirst().structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface() : "finished";
        assertEquals(1, pickups, remaining);
        assertEquals(1, loads, remaining);
        assertEquals(1, recipes, remaining);
        assertEquals(1, unloads, remaining);
        assertEquals(1, deliveries, remaining);
        assertTrue(state.productionJobs().isEmpty());
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:bread"));
    }
}
