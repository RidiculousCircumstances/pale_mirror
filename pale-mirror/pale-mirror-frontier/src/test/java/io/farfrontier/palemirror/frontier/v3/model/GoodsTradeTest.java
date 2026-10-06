package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Canonical commercial/ledger boundary, not native delivery or visual acceptance. */
class GoodsTradeTest {
    @Test void loadedCourierCanYieldSpatiallyWithoutChangingCargoOrItsRetainedExecution() {
        var state = reserved(); var shipment = shipment(state); var actor = shipment.execution().actorId();
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        state = atStation(state, actor, shipment.sender().station());
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 1, Shipment.Status.AWAITING_LOAD));
        var checkpoint = ActorSpatialCourtesy.assess(state, shipment.execution());
        assertTrue(checkpoint.ready(), "waiting for a companion does not pin a loaded carrier to a passage");
        assertSame(state, checkpoint.basis());
        assertEquals(Map.of(LOT, 60), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).lotQuantities());
        assertEquals(shipment.execution(), state.actorExecutions().actors().get(actor).current().orElseThrow());
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertTrue(ActorSpatialCourtesy.assess(restored, shipment.execution()).ready());
        var foreign = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor,
                shipment.execution().activityKind(), id("shipment:foreign"), shipment.execution().generation());
        assertThrows(IllegalArgumentException.class, () -> ActorSpatialCourtesy.assess(restored, foreign));
    }
    @Test void hungryTravellingCourierEatsThroughTheActualActivityOwnerAndResumesWithItsCargo() {
        var state = reserved(); var shipment = shipment(state); var actor = shipment.execution().actorId();
        // Settlement1's production bootstrap has wheat but no ready food. Declare finite
        // public food as initial test setup; actual taking/consumption must use the live owners.
        var food = id("lot:courier-meal-fixture"); var staging = id("custody:courier-meal-fixture");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(food, SELLER, "minecraft:bread", 2,
                "fixture:courier-meal", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(SOURCE), Map.of(food, 2), Map.of()));
        resources = resources.transfer(staging, SOURCE_ACCOUNT, Map.of(food, 2), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        state = atStation(state, actor, shipment.sender().station());
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 1, Shipment.Status.AWAITING_LOAD));
        long now = 30_001;
        state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), now), now), now, "shipments");
        var movement = state.actorMovements().get(actor);
        now++;
        state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now), now, "actor-movement");
        now += 200;
        var checkpoint = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, actor, now);
        assertNotEquals(BodyPosition.above(shipment.sender().station()), checkpoint);
        var preview = factForFamily(state, actor, new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted(
                actor, movement.order().goalRevision(), now, checkpoint, movement.executionId()), now, "actor-movement");
        assertEquals(ResidentActivityChoice.Kind.EAT, ResidentActivityCoordinator.assess(preview, actor, now).kind(),
                "safe preview: " + ResidentMealOpportunity.candidateAdmission(preview, actor, now) + " actual="
                        + ResidentMealOpportunity.find(preview, actor, now) + " yield=" + ResidentWorkYield.assess(preview,
                            HumanAssignmentProjection.compile(preview).assignment(actor)));
        state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.review(actor, now), now), now, "population");
        assertTrue(state.humanPopulation().meals().containsKey(actor), "courier did not enter its actual meal owner: "
                + ResidentActivityCoordinator.assess(state, actor, now) + " source=" + ResidentMealOpportunity.candidateAdmission(state, actor, now)
                + " nutrition=" + state.humanPopulation().nutrition(actor).accrueThrough(now, state.bootstrap().ruleset().residentLife(),
                    state.humanPopulation().resident(actor).characteristics().effectiveMetabolismPermille(now)));
        assertEquals(shipment.execution(), state.actorExecutions().actors().get(actor).suspended().orElseThrow());
        assertEquals(checkpoint, state.actorLocations().get(actor).body());
        assertTrue(state.humanPopulation().meals().containsKey(actor));
        assertFalse(state.actorMovements().containsKey(actor));
        assertEquals(Map.of(LOT, 60), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).lotQuantities());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        for (int boundary = 0; boundary < 16 && state.humanPopulation().meals().containsKey(actor); boundary++) {
            var meal = state.humanPopulation().meals().get(actor);
            now = meal.coldTravel().map(io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute::arrivalTick)
                    .orElse(now + 200);
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.planProgress(state,
                    io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.progress(meal, now), now), now, "population");
        }
        assertFalse(state.humanPopulation().meals().containsKey(actor), "the actual food owner must finish consumption");
        var postMeal = state.actorLocations().get(actor).body();
        now++;
        state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.review(actor, now), now), now, "population");
        var resumed = state.shipments().shipments().get(shipment.id()).execution();
        assertTrue(resumed.generation() > shipment.execution().generation());
        state.actorExecutions().requireCurrent(resumed);
        assertEquals(postMeal, state.actorLocations().get(actor).body(), "resumption cannot teleport the courier");
        assertEquals(Map.of(LOT, 60), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).lotQuantities());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(0, state.companies().goodsTrade().contracts().get(CONTRACT).acceptedQuantity());
    }
    @Test void exactPreLootCargoDispositionReleasesPaymentWithoutSellingOrDuplicatingGoods() {
        for (var outcome : ShipmentCargoDispositionObserved.Outcome.values()) {
            var state = reserved(); var shipment = shipment(state); var actor = shipment.execution().actorId();
            state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
            state = atStation(state, actor, shipment.sender().station());
            state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 1, Shipment.Status.AWAITING_LOAD));
            shipment = state.shipments().shipments().get(shipment.id());
            // Explicit bound-hand/dead-body fixture; these are not native death/loot acceptance claims.
            state = ModeledActorBodyFacts.present(state, actor);
            var body = ActorBodyAuthority.current(state, actor);
            var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, shipment.execution());
            var resources = state.inventory().fungibleResources();
            var address = new PhysicalStackAddress.ActorHand(actor,
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), actor), ActorContainerItemOrder.Hand.MAIN);
            resources = resources.rebind(shipment.carriedAccountId(), body.physicalEpoch(), FungiblePhysicalObservation.bind(resources,
                    shipment.carriedAccountId(), body.physicalEpoch(), List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:wheat", 60))));
            state = state.withInventory(state.inventory().withFungibleResources(resources));
            var carrier = java.util.UUID.nameUUIDFromBytes(outcome.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var observed = new ShipmentCargoDispositionObserved(shipment.id(), shipment.revision(), identity, outcome,
                    outcome == ShipmentCargoDispositionObserved.Outcome.WORLD_DROP ? java.util.Optional.of(carrier) : java.util.Optional.empty());
            var alive = state;
            assertThrows(IllegalArgumentException.class, () -> shipmentFact(alive, observed.shipmentId(), observed));
            state = ActorBodyAuthority.died(state, ModeledActorBodyFacts.death(state, actor, state.actorLocations().get(actor).body(), "fixture:fatality"),
                    FrontierActorDeathConsequences.INSTANCE, 10);
            var buyerBalance = state.inventory().economics().require(BUYER).balance();
            var lost = shipmentFact(state, shipment.id(), observed);
            assertEquals(Shipment.Status.CARGO_DISPOSED, lost.shipments().shipments().get(shipment.id()).status());
            assertFalse(lost.inventory().economics().reservations().containsKey(HOLD));
            assertEquals(buyerBalance, lost.inventory().economics().require(BUYER).balance());
            assertEquals(0, lost.companies().goodsTrade().contracts().get(CONTRACT).acceptedQuantity());
            assertEquals(60, lost.companies().goodsTrade().contracts().get(CONTRACT).disposedQuantity());
            assertFalse(lost.inventory().fungibleResources().accounts().containsKey(shipment.carriedAccountId()));
            if (outcome == ShipmentCargoDispositionObserved.Outcome.WORLD_DROP) {
                var drop = lost.inventory().fungibleResources().accounts().get(id("custody:world-" + carrier));
                assertEquals(Map.of(LOT, 60), drop.lotQuantities()); assertTrue(drop.claimQuantities().isEmpty());
                assertEquals(SELLER, lost.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
            } else assertEquals(4, lost.inventory().fungibleResources().lots().get(LOT).quantity());
            var codec = new FrontierWorldStateCodec(); assertEquals(lost, codec.decode(codec.encode(lost)));
            assertThrows(IllegalArgumentException.class, () -> shipmentFact(lost, observed.shipmentId(), observed));
        }
    }
    @Test void boundedTerminalRetentionMakesRoomOnlyByExplicitReferenceClosedRetirement() {
        var state = reserved(); var fresh = shipment(state);
        var history = new java.util.LinkedHashMap<SubjectId, Shipment>();
        for (int i = 0; i < ShipmentState.MAX_SHIPMENTS; i++) {
            var oldId = id("shipment:closed-" + i);
            var execution = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(fresh.execution().actorId(),
                    fresh.execution().activityKind(), oldId, fresh.execution().generation());
            history.put(oldId, new Shipment(oldId, new ResourceClaimDelegation(fresh.authorization().kind(), CLAIM, CONTRACT, oldId, 0),
                    execution, fresh.sender(), fresh.receiver(), fresh.sourceAccountId(), fresh.carriedAccountId(), fresh.receivingAccountId(),
                    fresh.itemKind(), fresh.lotQuantities(), Shipment.Status.ALLOCATION_WITHDRAWN, 2));
        }
        state = state.withChanges(FrontierWorldStateUpdate.begin().shipments(new ShipmentState(history)));
        var events = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.dispatch(state, SELLER, fresh, 0);
        assertEquals(1, events.stream().filter(e -> e.payload() instanceof ShipmentRetired).count());
        var admitted = applyEvents(state, events, 0, "shipments");
        assertEquals(ShipmentState.MAX_SHIPMENTS, admitted.shipments().shipments().size());
        assertEquals(fresh, admitted.shipments().shipments().get(fresh.id()));
    }
    @Test void shipmentJourneyAndRecipientAcceptanceUseTheRealKernelScheduleLifecycle() {
        var configuration = FrontierV3FixtureCatalog.goodsShipmentConfiguration(new WorldId("frontier:shipment-schedules"), 41);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        for (int boundary = 0; boundary < 20; boundary++) {
            var current = engine.canonicalState().state();
            if (current.companies().goodsTrade().contracts().get(new SubjectId("contract:development-goods")).fulfilled()
                    && engine.checkpoint().schedules().stream().noneMatch(action -> action.subject().equals(ShipmentFixture.ID))) break;
            var next = engine.checkpoint().schedules().stream()
                    .filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action)).sorted().findFirst().orElseThrow();
            var advance = engine.advanceTo(next.dueAt(), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, advance.status().kind(), advance.status().failureDetail().orElse("active"));
        }
        var state = engine.canonicalState().state();
        var shipment = state.shipments().shipments().get(ShipmentFixture.ID);
        assertEquals(Shipment.Status.DELIVERED, shipment.status());
        assertTrue(shipment.reception().isEmpty());
        assertTrue(state.companies().goodsTrade().contracts().get(new SubjectId("contract:development-goods")).fulfilled());
        assertFalse(engine.checkpoint().schedules().stream().anyMatch(s -> s.subject().equals(shipment.id())));
    }
    @Test void partialUnloadingRetainsTheCourierAndPaysOnlyTheExactReceivedPortionAcrossRecovery() {
        var state = reserved(); var shipment = shipment(state); var actor = shipment.execution().actorId();
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        state = atStation(state, actor, shipment.sender().station());
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 1, Shipment.Status.AWAITING_LOAD));
        // A receiver became nearly full after consent. This is initial-state setup, not a physical player action.
        var resources = state.inventory().fungibleResources();
        int existing = resources.accounts().get(RECEIVER_ACCOUNT).lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        var capacity = state.inventory().containerCapacity(RECEIVER, java.util.Set.of());
        int fillerQuantity = (capacity.slotCount() - capacity.exactSlots()) * 64 - existing - 17;
        var filler = id("lot:receiver-later-stock"); var staging = id("custody:receiver-stock-setup");
        resources = resources.issue(new ResourceLot(filler, BUYER, "minecraft:wheat", fillerQuantity, "fixture:receiver-stock", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(RECEIVER), Map.of(filler, fillerQuantity), Map.of()));
        resources = resources.transfer(staging, RECEIVER_ACCOUNT, Map.of(filler, fillerQuantity), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        state = atStation(state, actor, shipment.receiver().station());
        var beforeBuyer = state.inventory().economics().require(BUYER).balance();
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 2, Shipment.Status.CARRYING));
        var partial = state.shipments().shipments().get(shipment.id());
        assertEquals(Shipment.Status.CARRYING, partial.status()); assertEquals(43, partial.quantity());
        assertEquals(17, partial.reception().orElseThrow().quantity());
        assertNotEquals(CLAIM, partial.reception().orElseThrow().claimId());
        state.actorExecutions().requireCurrent(shipment.execution());
        assertEquals(beforeBuyer, state.inventory().economics().require(BUYER).balance());
        var waiting = state;
        var pendingReception = partial;
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(waiting, shipment.id(),
                new ShipmentReceiptAcknowledged(shipment.id(), pendingReception.revision(), pendingReception.reception().orElseThrow().id())));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.GoodsTradeReceiptProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.GoodsTradeReceiptProcess.review(CONTRACT, state.shipments().shipments().get(shipment.id()).reception().orElseThrow().id(), 10), new SimInstant(10)), 10, "goods-trade");
        assertEquals(beforeBuyer.minus(FixedScalar.whole(17)), state.inventory().economics().require(BUYER).balance());
        assertEquals(43, state.inventory().fungibleResources().claims().get(CLAIM).quantity());
        assertTrue(state.shipments().shipments().get(shipment.id()).reception().isEmpty());
        assertFalse(ShipmentStateSupport.coldTransferAvailable(state, state.shipments().shipments().get(shipment.id())));
        var retry = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), 11), 11);
        var next = (io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled) retry.getFirst().payload();
        assertEquals(11 + state.bootstrap().ruleset().cadence().terminalLogisticsReviewInterval(), next.replacement().dueAt().ticks());
        assertEquals(43, state.shipments().shipments().get(shipment.id()).quantity());
        resources = state.inventory().fungibleResources().destroy(RECEIVER_ACCOUNT, Map.of(filler, 43), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        partial = state.shipments().shipments().get(shipment.id());
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), partial.revision(), Shipment.Status.CARRYING));
        assertEquals(Shipment.Status.DELIVERED, state.shipments().shipments().get(shipment.id()).status());
        assertEquals(beforeBuyer.minus(FixedScalar.whole(17)), state.inventory().economics().require(BUYER).balance());
        state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.GoodsTradeReceiptProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.GoodsTradeReceiptProcess.review(CONTRACT, state.shipments().shipments().get(shipment.id()).reception().orElseThrow().id(), 20), new SimInstant(20)), 20, "goods-trade");
        assertEquals(beforeBuyer.minus(FixedScalar.whole(60)), state.inventory().economics().require(BUYER).balance());
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(shipment.carriedAccountId()));
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void courierSafeContinuationRetainsCargoAndAtomicallyAdoptsTheSharedSuccessorGeneration() {
        var state = reserved(); var shipment = shipment(state); var actor = shipment.execution().actorId();
        var request = new ShipmentDispatchRequested(SELLER, EconomicOwnerKind.SETTLEMENT_TREASURY, shipment);
        var commandId = new CommandId("command:shipment-request");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                new Revision(0), new SimInstant(0), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), request);
        var planned = (io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted) FrontierWorldRuntimeDefinition.planCommand(state, command);
        state = applyEvents(state, planned.events(), 0, "shipments");
        state = atStation(state, actor, shipment.sender().station());
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 1, Shipment.Status.AWAITING_LOAD));
        var passive = state.actorExecutions().next(actor, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, passive, 10).commit(state, FrontierWorldStateUpdate.begin());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(shipment.execution(), state.actorExecutions().actors().get(actor).suspended().orElseThrow());
        assertTrue(io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.held(state,
                io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), 11)));
        var successor = state.actorExecutions().next(actor, shipment.execution().activityKind(), shipment.id());
        state = ActorExecutionComposition.LIFECYCLE.prepareResume(state, shipment.execution(), successor,
                java.util.Optional.of(passive), 20).commit(state, FrontierWorldStateUpdate.begin());
        assertEquals(successor, state.shipments().shipments().get(shipment.id()).execution());
        assertEquals(successor, state.actorExecutions().actors().get(actor).current().orElseThrow());
        assertEquals(Map.of(LOT, 60), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).lotQuantities());
        var resumed = state;
        assertThrows(IllegalArgumentException.class, () -> new ShipmentExecutionCapability().validateReference(resumed, shipment.execution()));
        ShipmentStateSupport.validateOrder(resumed, resumed.shipments().shipments().get(shipment.id()).itemOrder());
    }
    @Test void hotPickupIsFencedReplaySafeAndSavedDepartureKeepsExactCargo() {
        var state = reserved(); var shipment = shipment(state); var actor = shipment.execution().actorId();
        state = atStation(state, actor, shipment.sender().station());
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        state = ModeledActorBodyFacts.present(state, actor);
        var lease = new AmbientActorLease(actor, state.actorLocations().get(actor).body(), new SimInstant(0), 1,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, state.actorLocations().get(actor).body());
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor, lease)));
        var record = PhysicalReplicaRecord.expected(SOURCE, ReferenceContainerCustody.semanticKind(state, SOURCE), 7,
                ReferenceContainerCustody.canonicalFingerprint(state, SOURCE), ReferenceContainerCustody.provenance(SOURCE));
        var custody = state.replicaCustody().declare(record).observe(SOURCE, 7, 1, record.fingerprint(), record.provenance(), 7)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(SOURCE), SOURCE, ReferenceContainerCustody.PROVIDER_ID,
                        7, 7, 2, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        var resources = state.inventory().fungibleResources();
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(SOURCE, 0));
        resources = resources.rebind(SOURCE_ACCOUNT, 7, FungiblePhysicalObservation.bind(resources, SOURCE_ACCOUNT, 7,
                List.of(new FungiblePhysicalObservation.Stack(address, shipment.itemKind(), 64))));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody).inventory(state.inventory().withFungibleResources(resources)));
        var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(ActorBodyAuthority.current(state, actor), shipment.execution());
        var step = new ShipmentPhysicalStep(shipment.status(), shipment.revision(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation(identity, lease.revision()),
                MaterialSourceSelection.select(resources, shipment.itemOrder()), -1, identity.body().physicalEpoch(), shipment.lotQuantities(), 0);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), actor),
                ActorContainerItemOrder.Hand.MAIN), shipment.itemKind(), 60);
        var receipt = new ShipmentHotTransferred(shipment.id(), step,
                List.of(new FungiblePhysicalObservation.Stack(address, shipment.itemKind(), 4)), List.of(hand));
        var unprepared = state;
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(unprepared, shipment.id(), receipt));
        state = shipmentFact(state, shipment.id(), new ShipmentHotPrepared(shipment.id(), step));
        assertFalse(ActorSpatialCourtesy.assess(state, shipment.execution()).ready(),
                "courtesy cannot displace a carrier during an unresolved physical pickup");
        assertTrue(ContainerPhysicalAuthorityComposition.pending(state, SOURCE));
        var prepared = state;
        assertThrows(IllegalArgumentException.class, () -> ModeledActorBodyFacts.unloaded(prepared, actor));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        state = shipmentFact(state, shipment.id(), receipt);
        assertEquals(Shipment.Status.CARRYING, state.shipments().shipments().get(shipment.id()).status());
        assertTrue(ActorSpatialCourtesy.assess(state, shipment.execution()).ready());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(Map.of(LOT, 60), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).lotQuantities());
        var committed = state;
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(committed, shipment.id(), receipt));
        assertThrows(IllegalArgumentException.class, () -> ModeledActorBodyFacts.unloaded(committed, actor));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.release(
                io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(committed, actor, AmbientLeaseStatus.DRAINING),
                new AmbientLeaseReleased(actor, committed.actorLocations().get(actor).body(), committed.actorLocations().get(actor).condition().health())),
                "projection must retain custody until the owner acknowledges its saved cargo hand, even if ACTOR runs before EFFECT");
        state = shipmentFact(state, shipment.id(), new ShipmentHandCustodyObserved(shipment.id(), identity,
                ShipmentHandCustodyObserved.Boundary.SAVED_DEPARTURE, hand));
        assertEquals(Map.of(LOT, 60), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).lotQuantities());
        assertTrue(state.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(binding -> binding.accountId().equals(shipment.carriedAccountId())));
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.DRAINING);
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.release(state,
                new AmbientLeaseReleased(actor, state.actorLocations().get(actor).body(), state.actorLocations().get(actor).condition().health()));
        state = ModeledActorBodyFacts.unloaded(state, actor);
        assertEquals(shipment.execution(), state.actorExecutions().actors().get(actor).current().orElseThrow());
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    private static final SubjectId SELLER = id("settlement:1"), BUYER = id("settlement:2");
    private static final SubjectId SOURCE = FrontierWorldState.depotId(SELLER), RECEIVER = FrontierWorldState.depotId(BUYER);
    private static final SubjectId SOURCE_ACCOUNT = ReferenceContainerCustody.scopeId(SOURCE), RECEIVER_ACCOUNT = ReferenceContainerCustody.scopeId(RECEIVER);
    private static final SubjectId LOT = id("lot:bootstrap-1-wheat"), CLAIM = id("claim:goods-wheat"), CONTRACT = id("contract:goods-wheat");
    private static final SubjectId HOLD = id("reservation:goods-wheat"), SELL_ORDER = id("order:goods-sell"), BUY_ORDER = id("order:goods-buy");
    private static final GoodsTradeParty SELLER_PARTY = new GoodsTradeParty(SELLER, EconomicOwnerKind.SETTLEMENT_TREASURY);
    private static final GoodsTradeParty BUYER_PARTY = new GoodsTradeParty(BUYER, EconomicOwnerKind.SETTLEMENT_TREASURY);
    private static final FixedScalar PRICE = FixedScalar.whole(1);

    @Test void courierActuallyWalksBothColdLegsWithTheCommonClockAndDoesNotSellOnArrival() {
        FrontierWorldState state = reserved(); Shipment shipment = shipment(state);
        var actor = shipment.execution().actorId(); var beforeBuyer = state.inventory().economics().require(BUYER).balance();
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        long now = 1;
        for (int leg = 0; leg < 2; leg++) {
            var action = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), now);
            var start = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state, action, now);
            assertTrue(start.stream().anyMatch(event -> event.payload()
                    instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted),
                    "leg=" + leg + " start=" + state.actorLocations().get(actor).body() + " goal=" + state.shipments().shipments().get(shipment.id()).movementOrder());
            state = applyEvents(state, start, now, "shipments");
            var movement = state.actorMovements().get(actor);
            assertEquals(shipment.execution(), movement.executionId());
            now++;
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now), now, "actor-movement");
            movement = state.actorMovements().get(actor);
            var travel = movement.coldTravel().orElseThrow();
            assertTrue(travel.route().size() > 1);
            long middle = travel.departedAtTick() + (travel.arrivalTick() - travel.departedAtTick()) / 2;
            var expectedBody = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, actor, middle);
            var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
            assertEquals(expectedBody, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, actor, middle));
            now = travel.arrivalTick();
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now), now, "actor-movement");
            assertFalse(state.actorMovements().containsKey(actor));
            state.actorExecutions().requireCurrent(shipment.execution());
            assertEquals(leg == 0 ? shipment.sender().station() : shipment.receiver().station(), state.actorLocations().get(actor).supportingSurface());
            assertEquals(beforeBuyer, state.inventory().economics().require(BUYER).balance());
            now++;
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), now), now), now, "shipments");
            assertEquals(leg == 0 ? Shipment.Status.CARRYING : Shipment.Status.DELIVERED, state.shipments().shipments().get(shipment.id()).status());
            now++;
        }
        assertEquals(beforeBuyer, state.inventory().economics().require(BUYER).balance());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        long executedAt = Math.max(now + 59, state.companies().goodsTrade().orders().get(SELL_ORDER).expiresAtTick() + 1);
        var receiptReview = io.farfrontier.palemirror.frontier.v3.process.GoodsTradeReceiptProcess.review(CONTRACT,
                state.shipments().shipments().get(shipment.id()).reception().orElseThrow().id(), now);
        var receiptEvents = FrontierWorldRuntimeDefinition.planScheduled(state, receiptReview, true, new SimInstant(executedAt));
        assertTrue(receiptEvents.stream().map(ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::cast)
                .allMatch(rescheduled -> rescheduled.replacement().dueAt().ticks() == executedAt + 1));
        assertTrue(receiptEvents.stream().map(ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                .allMatch(created -> created.action().dueAt().ticks() == executedAt + 1));
        state = applyEvents(state, receiptEvents, executedAt, "goods-trade");
        assertEquals(beforeBuyer.minus(FixedScalar.whole(60)), state.inventory().economics().require(BUYER).balance());
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
    }

    private static FrontierWorldState applyEvents(FrontierWorldState state, List<ProposedEvent> events, long tick, String family) {
        FrontierWorldRuntimeDefinition.processRegistry().validateEmissions(family, events);
        // Kernel schedule effects belong to the engine's due index, not the world reducer.
        // This focused journey checks actual route/pose/custody transitions; not an engine/WAL run.
        for (var event : events) if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect))
            state = factForFamily(state, event.subject(), event.payload(), tick, family);
        return state;
    }

    @Test void bothOwnersConsentAndPartialAcceptancePaysOnlyDeliveredRightsAcrossRecovery() {
        FrontierWorldState state = reserved();
        FixedScalar initialBuyer = state.inventory().economics().require(BUYER).balance();
        FixedScalar initialSeller = state.inventory().economics().require(SELLER).balance();
        assertEquals(FixedScalar.whole(60), state.inventory().economics().reservations().get(HOLD).amount());
        state = arrive(state);
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt("receipt:goods-one", 0, 20, "lot:goods-one")));
        assertEquals(initialBuyer.minus(FixedScalar.whole(20)), state.inventory().economics().require(BUYER).balance());
        assertEquals(initialSeller.plus(FixedScalar.whole(20)), state.inventory().economics().require(SELLER).balance());
        assertEquals(40, state.inventory().fungibleResources().claims().get(CLAIM).quantity());
        assertEquals(FixedScalar.whole(40), state.inventory().economics().reservations().get(HOLD).amount());
        assertEquals(44, state.inventory().fungibleResources().lots().get(LOT).quantity());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(BUYER, state.inventory().fungibleResources().lots().get(id("lot:goods-one")).economicOwnerId());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt("receipt:goods-two", 1, 40, "lot:goods-two")));
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
        assertFalse(state.inventory().economics().reservations().containsKey(HOLD));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(CLAIM));
        assertEquals(4, state.inventory().fungibleResources().lots().get(LOT).quantity());
        assertEquals(initialBuyer.minus(FixedScalar.whole(60)), state.inventory().economics().require(BUYER).balance());
        assertEquals(state, codec.decode(codec.encode(state)));
        FrontierWorldState completed = state;
        assertThrows(IllegalArgumentException.class, () -> fact(completed, BUYER,
                new GoodsTradeAccepted(receipt("receipt:goods-two", 1, 40, "lot:goods-two"))));
    }

    @Test void sellerCannotInventBuyerConsentOrSpendSomeoneElsesBalance() {
        FrontierWorldState state = initial();
        state = fact(state, SELLER, new GoodsTradeOrderPlaced(sellOrder()));
        FrontierWorldState offered = state;
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER, reservation()));
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER, new GoodsTradeOrderPlaced(buyOrder())));
        assertTrue(state.inventory().economics().reservations().isEmpty());
    }

    @Test void receivingPositionOrContractAloneCannotAwardDelivery() {
        FrontierWorldState state = reserved();
        var premature = receipt("receipt:premature", 0, 20, "lot:premature");
        assertThrows(IllegalArgumentException.class, () -> fact(state, BUYER, new GoodsTradeAccepted(premature)));
        assertEquals(0, state.companies().goodsTrade().contracts().get(CONTRACT).revision());
        assertEquals(FixedScalar.whole(60), state.inventory().economics().reservations().get(HOLD).amount());
    }

    @Test void wrongPriceOvercommitAndForgedParticipantKindAreRejected() {
        FrontierWorldState state = fact(fact(initial(), SELLER, new GoodsTradeOrderPlaced(sellOrder())), BUYER, new GoodsTradeOrderPlaced(buyOrder()));
        GoodsTradeContract priced = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY,
                SOURCE, RECEIVER, "minecraft:wheat", 60, FixedScalar.whole(4), HOLD, 0, Map.of(CLAIM, 60), Map.of());
        FrontierWorldState offered = state;
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER, new GoodsTradeReserved(priced, reservation().allocations())));
        FrontierWorldState held = fact(state, SELLER, reservation());
        assertThrows(IllegalArgumentException.class, () -> fact(held, SELLER, reservation()));
        var forged = new GoodsTradeOrder(id("order:forged"), new GoodsTradeParty(SELLER, EconomicOwnerKind.COMPANY), BUYER_PARTY,
                GoodsTradeOrder.Side.SELL, SOURCE, "minecraft:wheat", 10, 0, PRICE, 10_000);
        assertThrows(IllegalArgumentException.class, () -> fact(initial(), SELLER, new GoodsTradeOrderPlaced(forged)));
    }

    @Test void breadPromisedForDeliveryIsNotCountedAsAvailablePublicFood() {
        FrontierWorldState state = initial(); var resources = state.inventory().fungibleResources();
        resources = resources.destroy(SOURCE_ACCOUNT, Map.of(LOT, 64), Map.of());
        SubjectId bread = id("lot:trade-bread");
        resources = resources.issue(new ResourceLot(bread, SELLER, "minecraft:bread", 64, "fixture:trade-bread", List.of()),
                new CustodyAccount(SOURCE_ACCOUNT, new ResourceCustody.Container(SOURCE), Map.of(bread, 64), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        state = fact(state, SELLER, new GoodsTradeOrderPlaced(new GoodsTradeOrder(SELL_ORDER, SELLER_PARTY, BUYER_PARTY,
                GoodsTradeOrder.Side.SELL, SOURCE, "minecraft:bread", 60, 0, PRICE, 10_000)));
        state = fact(state, BUYER, new GoodsTradeOrderPlaced(new GoodsTradeOrder(BUY_ORDER, BUYER_PARTY, SELLER_PARTY,
                GoodsTradeOrder.Side.BUY, RECEIVER, "minecraft:bread", 60, 0, PRICE, 10_000)));
        var contract = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY,
                SOURCE, RECEIVER, "minecraft:bread", 60, PRICE, HOLD, 0, Map.of(CLAIM, 60), Map.of());
        state = fact(state, SELLER, new GoodsTradeReserved(contract, List.of(new GoodsTradeStockAllocation(SOURCE_ACCOUNT,
                new ClaimAllocation(CLAIM, CONTRACT, SELLER, "minecraft:bread", 60, Map.of(bread, 60), ClaimPurpose.GOODS_TRADE),
                GoodsTradeStockAllocation.Authority.COLD, 0))));
        assertEquals(64, SettlementFoodPolicy.breadStock(state, SELLER));
        assertEquals(4, SettlementFoodPolicy.reserveCoverageBread(state, SELLER));
        assertEquals(4, SettlementFoodPolicy.coldUsableBread(state, SELLER));
        assertFalse(SettlementFoodPolicy.allowsPopulationGrowth(state, SELLER));
    }

    @Test void inboundCommitmentUsesTheExistingStorageOwnerAndStopsDoubleCountingAfterArrival() {
        FrontierWorldState state = reserved();
        assertEquals(Map.of("minecraft:wheat", 60L), ContainerStorageAdmission.inbound(state, RECEIVER, java.util.Optional.empty()));
        FrontierWorldState arrived = arrive(state);
        assertTrue(ContainerStorageAdmission.inbound(arrived, RECEIVER, java.util.Optional.empty()).isEmpty());
        assertEquals(60, arrived.companies().goodsTrade().contracts().get(CONTRACT).remainingQuantity());
    }

    @Test void activeContractAndReservedStockCannotBeErasedAtPublication() {
        FrontierWorldState state = reserved();
        FrontierWorldState erased = state.withCompanies(state.companies().withGoodsTrade(GoodsTradeState.empty()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(state, erased, List.of()));
        var resources = state.inventory().fungibleResources().releaseClaim(SOURCE_ACCOUNT, CLAIM);
        FrontierWorldState unreserved = state.withInventory(state.inventory().withFungibleResources(resources));
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.validate(unreserved));
    }

    @Test void independentShipmentPortionCanArriveWhileTheOtherPortionIsCancelledWithoutPayment() {
        FrontierWorldState state = reserved(); SubjectId child = id("claim:shipment-one");
        state = fact(state, SELLER, new GoodsTradeClaimPartitioned(CONTRACT,
                new ResourceClaimPartition(SOURCE_ACCOUNT, CLAIM, child, Map.of(LOT, 20))));
        assertEquals(Map.of(CLAIM, 40, child, 20), state.companies().goodsTrade().contracts().get(CONTRACT).outstandingClaims());
        var resources = state.inventory().fungibleResources().transfer(SOURCE_ACCOUNT, RECEIVER_ACCOUNT, Map.of(LOT, 20), Map.of(child, 20));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        FrontierDomainRelationships.validate(state);
        assertEquals(Map.of("minecraft:wheat", 40L), ContainerStorageAdmission.inbound(state, RECEIVER, java.util.Optional.empty()));
        var receipt = new GoodsTradeAcceptance(id("receipt:shipment-one"), CONTRACT, 0,
                new ResourceTitleTransfer(RECEIVER_ACCOUNT, child, SELLER, BUYER, Map.of(LOT, 20), Map.of(LOT, id("lot:shipment-bought"))));
        FixedScalar beforeBuyer = state.inventory().economics().require(BUYER).balance();
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt));
        var cancelled = new GoodsTradeCancelled(new GoodsTradeDisposition(id("receipt:cancel-rest"), CONTRACT, 1,
                CLAIM, 40, GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING));
        FrontierWorldState partial = state;
        assertThrows(IllegalArgumentException.class, () -> fact(partial, BUYER, cancelled));
        state = fact(state, SELLER, cancelled);
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).terminal());
        assertFalse(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
        assertEquals(beforeBuyer.minus(FixedScalar.whole(20)), state.inventory().economics().require(BUYER).balance());
        assertFalse(state.inventory().economics().reservations().containsKey(HOLD));
        assertEquals(44, state.inventory().fungibleResources().accounts().get(SOURCE_ACCOUNT).lotQuantities().get(LOT));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        FrontierWorldState closed = state;
        assertThrows(IllegalArgumentException.class, () -> fact(closed, SELLER, cancelled));
        var retired = new GoodsTradeRetired(java.util.Set.of(SELL_ORDER, BUY_ORDER), java.util.Set.of(CONTRACT));
        assertThrows(IllegalArgumentException.class, () -> fact(closed, GoodsTradeMarketIdentity.OWNER, retired));
        assertThrows(IllegalArgumentException.class, () -> fact(partial, GoodsTradeMarketIdentity.OWNER, retired, 10_001));
        FrontierWorldState compacted = fact(closed, GoodsTradeMarketIdentity.OWNER, retired, 10_001);
        assertTrue(compacted.companies().goodsTrade().orders().isEmpty());
        assertTrue(compacted.companies().goodsTrade().contracts().isEmpty());
        assertEquals(closed.companies().goodsTrade().participants(), compacted.companies().goodsTrade().participants());
        assertEquals(closed.inventory(), compacted.inventory());
        assertEquals(compacted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(compacted)));
        FrontierWorldState delivered = arrive(reserved());
        assertThrows(IllegalArgumentException.class, () -> fact(delivered, SELLER, new GoodsTradeCancelled(
                new GoodsTradeDisposition(id("receipt:too-late"), CONTRACT, 0, CLAIM, 60,
                        GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING))));
    }

    @Test void observedPlayerRemovalClosesTheAffectedPromiseWithoutAwardingDeliveryOrMoney() {
        FrontierWorldState state = reserved(); Shipment shipment = shipment(state);
        state = atStation(state, shipment.execution().actorId(), shipment.sender().station());
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        var resources = state.inventory().fungibleResources();
        resources = resources.rebind(SOURCE_ACCOUNT, 7, FungiblePhysicalObservation.bind(resources, SOURCE_ACCOUNT, 7,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(SOURCE, 0)), "minecraft:wheat", 64))));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var binding = resources.bindings().values().stream().filter(b -> b.accountId().equals(SOURCE_ACCOUNT)).findFirst().orElseThrow();
        var player = java.util.UUID.fromString("00000000-0000-0000-0000-000000000131");
        var observed = FungiblePhysicalHandoff.departToNew(resources, SOURCE_ACCOUNT, 7, binding, 32,
                id("custody:goods-player"), new ResourceCustody.Player(player), 1,
                new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitAffectedClaims(resources);
        FixedScalar buyerBalance = state.inventory().economics().require(BUYER).balance();
        assertTrue(FungibleClaimForfeitureStateSupport.supports(state, observed));
        FrontierWorldState after = FungibleClaimForfeitureStateSupport.apply(state, observed);
        FrontierReferenceClosure.validateTransition(state, after, List.of());
        var contract = after.companies().goodsTrade().contracts().get(CONTRACT);
        assertTrue(contract.terminal()); assertFalse(contract.fulfilled()); assertTrue(contract.acceptances().isEmpty());
        assertEquals(GoodsTradeDisposition.Reason.OBSERVED_ALLOCATION_CHANGED, contract.dispositions().values().iterator().next().reason());
        assertEquals(buyerBalance, after.inventory().economics().require(BUYER).balance());
        assertFalse(after.inventory().economics().reservations().containsKey(HOLD));
        assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, after.shipments().shipments().get(shipment.id()).status());
        assertTrue(after.actorExecutions().actors().get(shipment.execution().actorId()).current().isEmpty());
        assertEquals(SELLER, after.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(32, after.inventory().fungibleResources().accounts().get(id("custody:goods-player")).lotQuantities().get(LOT));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
    }

    @Test void manySmallReceiptsHaveBoundedEvidenceWithoutForgettingCancelledQuantityOrAllowingReplay() {
        SubjectId cancelled = id("claim:cancelled-one");
        var contract = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY, SOURCE, RECEIVER,
                "minecraft:wheat", 131, PRICE, HOLD, 0, Map.of(CLAIM, 130, cancelled, 1), Map.of());
        var first = new GoodsTradeDisposition(id("receipt:early-cancel"), CONTRACT, 0, cancelled, 1,
                GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING);
        contract = contract.dispose(first);
        for (int i = 1; i <= 130; i++) contract = contract.accept(receipt("receipt:part-" + i, i, 1, "lot:part-" + i));
        assertTrue(contract.terminal()); assertFalse(contract.fulfilled());
        assertEquals(131, contract.revision()); assertEquals(130, contract.acceptedQuantity()); assertEquals(1, contract.disposedQuantity());
        assertEquals(GoodsTradeContract.MAX_RECEIPTS, contract.acceptances().size()); assertTrue(contract.dispositions().isEmpty());
        GoodsTradeContract closed = contract;
        assertThrows(IllegalArgumentException.class, () -> closed.dispose(first));
        assertThrows(IllegalArgumentException.class, () -> closed.accept(receipt("receipt:part-1", 1, 1, "lot:part-1")));
    }

    @Test void titleTransferPreservesHotLayoutEpochAndOtherOwnerRemainderWithoutMovingAStack() {
        SubjectId account = id("custody:hot-title"), lot = id("lot:hot-title"), claim = id("claim:hot-title"), child = id("lot:hot-bought");
        var resources = FungibleResourceLedger.empty().issue(new ResourceLot(lot, SELLER, "minecraft:bread", 60, "fixture:hot-title", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(RECEIVER), Map.of(lot, 60), Map.of()))
                .reserve(new ClaimAllocation(claim, CONTRACT, SELLER, "minecraft:bread", 40, Map.of(lot, 40), ClaimPurpose.GOODS_TRADE), account);
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(RECEIVER, 0));
        resources = resources.rebind(account, 7, FungiblePhysicalObservation.bind(resources, account, 7,
                List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:bread", 60))));
        var before = resources.bindings().values().iterator().next();
        var result = resources.transferTitle(new ResourceTitleTransfer(account, claim, SELLER, BUYER, Map.of(lot, 20), Map.of(lot, child)));
        var after = result.bindings().get(before.id());
        assertEquals(address, after.address()); assertEquals(7, after.authorityEpoch()); assertEquals(60, after.quantity());
        assertEquals(20, result.claims().get(claim).quantity()); assertEquals(40, result.lots().get(lot).quantity());
        assertEquals(BUYER, result.lots().get(child).economicOwnerId()); assertEquals(SELLER, result.lots().get(lot).economicOwnerId());
        assertEquals(Map.of(lot, 40, child, 20), result.accounts().get(account).lotQuantities());
        var partitioned = resources.partitionClaim(new ResourceClaimPartition(account, claim, id("claim:hot-child"), Map.of(lot, 20)));
        assertEquals(Map.of(claim, 20, id("claim:hot-child"), 20), partitioned.accounts().get(account).claimQuantities());
        assertEquals(resources.lots(), partitioned.lots());
        assertEquals(7, partitioned.bindings().get(before.id()).authorityEpoch());
        assertEquals(60, partitioned.bindings().get(before.id()).quantity());
        assertEquals(2, partitioned.bindings().get(before.id()).claimQuantities().size());
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:goods-trade-core"), 17));
    }

    @Test void independentShipmentMovesTheOriginalClaimWithoutSellingItAndSurvivesRecovery() {
        FrontierWorldState state = reserved(); Shipment shipment = shipment(state);
        state = atStation(state, shipment.execution().actorId(), shipment.sender().station());
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        assertEquals(shipment.execution(), state.actorExecutions().actors().get(shipment.execution().actorId()).current().orElseThrow());
        assertSame(state.shipments(), state.withCompanies(state.companies()).shipments());
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 1, Shipment.Status.AWAITING_LOAD));
        assertEquals(CONTRACT, state.inventory().fungibleResources().claims().get(CLAIM).claimantId());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(new ResourceCustody.Actor(shipment.execution().actorId()),
                state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).custody());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        // Explicit station fixture placement isolates loading/unloading authority. This is NOT travel evidence.
        state = atStation(state, shipment.execution().actorId(), shipment.receiver().station());
        FixedScalar buyer = state.inventory().economics().require(BUYER).balance();
        state = shipmentFact(state, shipment.id(), new ShipmentColdTransferred(shipment.id(), 2, Shipment.Status.CARRYING));
        assertEquals(Shipment.Status.DELIVERED, state.shipments().shipments().get(shipment.id()).status());
        assertTrue(state.actorExecutions().actors().get(shipment.execution().actorId()).current().isEmpty());
        assertEquals(buyer, state.inventory().economics().require(BUYER).balance());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        FrontierWorldState delivered = state;
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(delivered, shipment.id(),
                new ShipmentColdTransferred(shipment.id(), 2, Shipment.Status.CARRYING)));
        var reception = state.shipments().shipments().get(shipment.id()).reception().orElseThrow();
        state = fact(state, BUYER, new GoodsTradeAccepted(new GoodsTradeAcceptance(reception.id(), CONTRACT, 0,
                new ResourceTitleTransfer(shipment.receivingAccountId(), CLAIM, SELLER, BUYER, Map.of(LOT, 60), Map.of(LOT, id("lot:shipment-accepted"))))));
        assertEquals(buyer.minus(FixedScalar.whole(60)), state.inventory().economics().require(BUYER).balance());
        state = shipmentFact(state, shipment.id(), new ShipmentReceiptAcknowledged(shipment.id(), 3, reception.id()));
        assertEquals(state, codec.decode(codec.encode(state)));
        state = shipmentFact(state, shipment.id(), new ShipmentRetired(shipment.id(), 4));
        assertTrue(state.shipments().shipments().isEmpty());
    }

    @Test void dispatchedAllocationRejectsForgeryCompetingCourierAndSilentCancellation() {
        FrontierWorldState state = reserved(); Shipment shipment = shipment(state);
        state = atStation(state, shipment.execution().actorId(), shipment.sender().station());
        FrontierWorldState source = state;
        assertThrows(IllegalArgumentException.class, () -> ActorItemCustody.transferCold(source, shipment.itemOrder()));
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(source, BUYER, new ShipmentDispatched(shipment)));
        var foreignReceivingAccount = new Shipment(shipment.id(), shipment.authorization(), shipment.execution(), shipment.sender(),
                shipment.receiver(), shipment.sourceAccountId(), shipment.carriedAccountId(), id("custody:second-receiver-account"),
                shipment.itemKind(), shipment.lotQuantities(), shipment.status(), shipment.revision());
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(source, SELLER, new ShipmentDispatched(foreignReceivingAccount)),
                "a container's existing canonical account must not be replaced by a shipment-owned duplicate");
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment)); FrontierWorldState dispatched = state;
        assertThrows(IllegalArgumentException.class, () -> fact(dispatched, SELLER,
                new GoodsTradeClaimPartitioned(CONTRACT, new ResourceClaimPartition(SOURCE_ACCOUNT, CLAIM, id("claim:late-partition"), Map.of(LOT, 20)))));
        assertThrows(IllegalArgumentException.class, () -> fact(dispatched, SELLER, new GoodsTradeCancelled(
                new GoodsTradeDisposition(id("receipt:bad-cancel"), CONTRACT, 0, CLAIM, 60, GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING))));
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(dispatched, shipment.id(), new ShipmentRetired(shipment.id(), 1)));
        assertThrows(IllegalArgumentException.class, () -> shipmentFact(dispatched, shipment.id(),
                new ShipmentColdTransferred(shipment.id(), 2, Shipment.Status.AWAITING_LOAD)));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(dispatched,
                dispatched.withChanges(FrontierWorldStateUpdate.begin().shipments(ShipmentState.empty())
                        .actorExecutions(dispatched.actorExecutions().finish(shipment.execution()))), List.of()));
        assertEquals(dispatched, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(dispatched)));
    }

    private static Shipment shipment(FrontierWorldState state) {
        SubjectId shipment = id("shipment:goods-first");
        SubjectId actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var execution = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.COURIER, shipment,
                state.actorExecutions().generation(actor) + 1);
        return new Shipment(shipment, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
                CLAIM, CONTRACT, shipment, 0), execution,
                new ShipmentEndpoint(ShipmentEndpoint.Kind.SETTLEMENT_DEPOT, SELLER, depotStructure(state, SELLER), SOURCE, station(state, SELLER)),
                new ShipmentEndpoint(ShipmentEndpoint.Kind.SETTLEMENT_DEPOT, BUYER, depotStructure(state, BUYER), RECEIVER, station(state, BUYER)),
                SOURCE_ACCOUNT, id("custody:shipment-carried"), RECEIVER_ACCOUNT, "minecraft:wheat", Map.of(LOT, 60),
                Shipment.Status.AWAITING_LOAD, 1);
    }
    private static FrontierWorldState shipmentFact(FrontierWorldState state, SubjectId subject, FrontierPayload payload) {
        return factForFamily(state, subject, payload, 0, "shipments");
    }
    private static SubjectId depotStructure(FrontierWorldState state, SubjectId settlementId) {
        return state.bootstrap().settlements().stream().filter(s -> s.id().equals(settlementId)).findFirst().orElseThrow()
                .structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow().id();
    }
    private static GoodsTradeOrder sellOrder() {
        return new GoodsTradeOrder(SELL_ORDER, SELLER_PARTY, BUYER_PARTY, GoodsTradeOrder.Side.SELL, SOURCE,
                "minecraft:wheat", 60, 0, PRICE, 10_000);
    }
    private static GoodsTradeOrder buyOrder() {
        return new GoodsTradeOrder(BUY_ORDER, BUYER_PARTY, SELLER_PARTY, GoodsTradeOrder.Side.BUY, RECEIVER,
                "minecraft:wheat", 60, 0, FixedScalar.whole(3), 10_000);
    }
    private static GoodsTradeReserved reservation() {
        var contract = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY, SOURCE, RECEIVER,
                "minecraft:wheat", 60, PRICE, HOLD, 0, Map.of(CLAIM, 60), Map.of());
        var claim = new ClaimAllocation(CLAIM, CONTRACT, SELLER, "minecraft:wheat", 60, Map.of(LOT, 60), ClaimPurpose.GOODS_TRADE);
        return new GoodsTradeReserved(contract, List.of(new GoodsTradeStockAllocation(SOURCE_ACCOUNT, claim,
                GoodsTradeStockAllocation.Authority.COLD, 0)));
    }
    private static FrontierWorldState reserved() {
        return fact(fact(fact(initial(), SELLER, new GoodsTradeOrderPlaced(sellOrder())), BUYER,
                new GoodsTradeOrderPlaced(buyOrder())), SELLER, reservation());
    }
    private static FrontierWorldState arrive(FrontierWorldState state) {
        // Shared COLD handoff fixture at declared stations. Body placement is test setup,
        // not navigation, elapsed travel or native unloading acceptance.
        SubjectId actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId carried = id("custody:goods-courier");
        SurfaceAnchor sourceStation = station(state, SELLER), receiverStation = station(state, BUYER);
        state = atStation(state, actor, sourceStation);
        var taking = new ActorContainerItemOrder(CONTRACT, actor, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(SOURCE_ACCOUNT, new ResourceCustody.Container(SOURCE),
                        carried, new ResourceCustody.Actor(actor), java.util.Optional.of(CLAIM), "minecraft:wheat", Map.of(LOT, 60)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(SOURCE), sourceStation,
                ActorContainerItemOrder.Hand.MAIN, 0, 1);
        FrontierWorldState loaded = state.withInventory(ActorItemCustody.transferCold(state, taking));
        FrontierReferenceClosure.validateTransition(state, loaded, List.of());
        loaded = atStation(loaded, actor, receiverStation);
        var placing = new ActorContainerItemOrder(CONTRACT, actor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(carried, new ResourceCustody.Actor(actor),
                        RECEIVER_ACCOUNT, new ResourceCustody.Container(RECEIVER), java.util.Optional.of(CLAIM), "minecraft:wheat", Map.of(LOT, 60)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(RECEIVER), receiverStation,
                ActorContainerItemOrder.Hand.MAIN, 1, 1);
        FrontierWorldState arrived = loaded.withInventory(ActorItemCustody.transferCold(loaded, placing));
        FrontierReferenceClosure.validateTransition(loaded, arrived, List.of()); return arrived;
    }
    private static SurfaceAnchor station(FrontierWorldState state, SubjectId settlementId) {
        var settlement = state.bootstrap().settlements().stream().filter(s -> s.id().equals(settlementId)).findFirst().orElseThrow();
        return SettlementDepotServicePort.forDepot(settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow()).serviceSurface();
    }
    private static FrontierWorldState atStation(FrontierWorldState state, SubjectId actor, SurfaceAnchor station) {
        var actors = new java.util.HashMap<>(state.actorLocations());
        actors.put(actor, actors.get(actor).withBody(BodyPosition.above(station)));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
    }
    private static GoodsTradeAcceptance receipt(String id, long revision, int quantity, String child) {
        return new GoodsTradeAcceptance(id(id), CONTRACT, revision,
                new ResourceTitleTransfer(RECEIVER_ACCOUNT, CLAIM, SELLER, BUYER, Map.of(LOT, quantity), Map.of(LOT, id(child))));
    }
    private static FrontierWorldState fact(FrontierWorldState state, SubjectId actor, FrontierPayload payload) {
        return fact(state, actor, payload, 0);
    }
    private static FrontierWorldState fact(FrontierWorldState state, SubjectId actor, FrontierPayload payload, long atTick) {
        return factForFamily(state, actor, payload, atTick, "goods-trade");
    }
    private static FrontierWorldState factForFamily(FrontierWorldState state, SubjectId actor, FrontierPayload payload, long atTick, String family) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var emitted = new ProposedEvent(actor, payload);
        FrontierWorldRuntimeDefinition.processRegistry().validateEmissions(family, List.of(emitted));
        var decoded = codecs.decode(emitted.payload().type(), codecs.encode(emitted.payload()));
        assertEquals(emitted.payload(), decoded);
        var command = new CommandId("command:goods-reduce");
        var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:goods-reduce"),
                new TransactionId("transaction:goods-reduce"), state.bootstrap().worldId(), new Revision(1), new SimInstant(atTick),
                emitted.subject(), CauseChain.root(command), decoded);
        var next = FrontierWorldRuntimeDefinition.reduce(state, event);
        FrontierReferenceClosure.validateTransition(state, next, List.of()); return next;
    }
    private static SubjectId id(String value) { return new SubjectId(value); }
}
