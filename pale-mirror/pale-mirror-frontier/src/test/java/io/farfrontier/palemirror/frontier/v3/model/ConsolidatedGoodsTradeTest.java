package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsolidatedGoodsTradeTest {
    @Test void selectedTripPendingProvisionRouteDoesNotFanOutIntoOtherCrewsOrQuotes() throws Exception {
        var state = initial(); var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        var before = review(state, home, 400);
        // Establish the real selected mission endpoints, then control only the
        // readiness of its outward provisioning query. No accepted outcome is fabricated.
        var ready = review(before, buyer, 401);
        var mission = ready.shipments().missions().values().iterator().next();
        var proof = mission.supplies().orElseThrow().journey();
        var origin = proof.outward().surfaces().getFirst();
        var destination = proof.outward().surfaces().getLast();
        var pending = new java.util.concurrent.atomic.AtomicInteger();
        var planner = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanner() {
            public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult query(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target) {
                return query(geometry, start, target, io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteExtent.COMPLETE);
            }
            public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult query(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target,
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteExtent extent) {
                if (!extent.estimateOnly() && start.equals(origin) && target.equals(destination)) {
                    pending.incrementAndGet();
                    return new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                            io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                            List.of(), 0, 0, "SELECTED_TRIP_TEST_PENDING");
                }
                return extent.limit(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.calculate(geometry, start, target));
            }
        };
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(planner)) {
            var result = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.observe(() -> review(before, buyer, 401));
            assertEquals(1, pending.get(), "incomplete provision proof must suspend this selected preparation");
            assertFalse(result.requests().isEmpty());
            assertEquals(before.inventory(), result.result().inventory());
            assertEquals(before.actorExecutions(), result.result().actorExecutions());
            assertTrue(result.result().shipments().missions().isEmpty());
            assertTrue(result.result().companies().goodsTrade().contracts().isEmpty());
        }
    }
    @Test void quoteComparisonRequestsOnlyRegionalEstimatesAndCannotReserveOrAssignACrew() throws Exception {
        var state = initial(); var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        state = review(state, home, 400); var before = state;
        var queries = new java.util.concurrent.atomic.AtomicInteger();
        var planner = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanner() {
            public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult query(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target) { throw new AssertionError("economic comparison requested full path"); }
            public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult query(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target,
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteExtent extent) {
                assertTrue(extent.estimateOnly()); queries.incrementAndGet();
                return new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                        io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                        List.of(), 0, 0, "REGIONAL_TEST_PENDING");
            }
        };
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(planner)) {
            var result = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.observe(() -> review(before, buyer, 401));
            assertTrue(queries.get() > 0); assertFalse(result.requests().isEmpty());
            assertTrue(result.requests().stream().allMatch(request -> request.extent().estimateOnly()));
            assertEquals(before.inventory(), result.result().inventory());
            assertEquals(before.actorExecutions(), result.result().actorExecutions());
            assertTrue(result.result().shipments().missions().isEmpty());
            assertTrue(result.result().companies().goodsTrade().contracts().isEmpty());
        }
    }
    @Test void walkingGroupHandCustodyUsesItsExactGroupAuthorityAndRejectsForeignOrRepeatedEvidence() {
        var state = initial();
        var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        state = review(state, home, 400); state = review(state, buyer, 401);
        var mission = state.shipments().missions().values().iterator().next();
        var parcel = mission.shipmentIds().stream().map(state.shipments().shipments()::get)
                .filter(p -> p.mobileContainerId().isEmpty()).findFirst().orElseThrow();
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GROUP_MEMBER,
                parcel.execution().activityKind());
        var actor = parcel.execution().actorId();
        // Arrange an exact already-carried model fixture through the shared resource
        // owner. This is a custody-boundary test, not evidence of a native pickup.
        state = state.withActorBody(actor, parcel.sender().station().standingBody());
        state = state.withChanges(ActorResourceTransferComposition.cold(state, parcel.itemOrder())
                .shipments(state.shipments().replace(parcel, parcel.withStatus(Shipment.Status.CARRYING))));
        parcel = state.shipments().shipments().get(parcel.id());
        state = ModeledActorBodyFacts.present(state, actor);
        var lease = new AmbientActorLease(actor, state.actorLocations().get(actor).body(), new SimInstant(402), 1,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, state.actorLocations().get(actor).body());
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor, lease)));
        var body = ActorBodyAuthority.current(state, actor);
        var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, parcel.execution());
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), actor),
                ActorContainerItemOrder.Hand.MAIN), parcel.itemKind(), parcel.quantity());
        var observed = new ShipmentHandCustodyObserved(parcel.id(), identity,
                ShipmentHandCustodyObserved.Boundary.MATERIALIZED, hand);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(observed, codecs.decode(observed.type(), codecs.encode(observed)));
        final var unbound = state; final var exactParcel = parcel;
        var forged = new ShipmentHandCustodyObserved(parcel.id(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body,
                    new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor,
                        parcel.execution().activityKind(), new SubjectId("group:foreign"), parcel.execution().generation())),
                ShipmentHandCustodyObserved.Boundary.MATERIALIZED, hand);
        assertThrows(IllegalArgumentException.class,
                () -> ShipmentPhysicalStateSupport.handCustody(unbound, exactParcel.id(), forged));
        state = ShipmentPhysicalStateSupport.handCustody(state, parcel.id(), observed);
        final var bound = state;
        assertThrows(IllegalArgumentException.class,
                () -> ShipmentPhysicalStateSupport.handCustody(bound, exactParcel.id(), observed));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var departure = new ShipmentHandCustodyObserved(parcel.id(), identity,
                ShipmentHandCustodyObserved.Boundary.SAVED_DEPARTURE, hand);
        state = ShipmentPhysicalStateSupport.handCustody(state, parcel.id(), departure);
        assertTrue(state.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(binding -> binding.accountId().equals(exactParcel.carriedAccountId())));
        assertEquals(mission.groupId(), state.actorExecutions().actors().get(actor).current().orElseThrow().activityOwnerId());
    }

    @Test void cooperativePreflightRetainsRouteWaitingWithoutPublishingReservations() throws Exception {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:consolidated-route-wait"), 20260918065L, true);
        var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        final var basis = review(state, home, 400);
        try (var planner = new io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner();
             var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(planner)) {
            var waiting = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.observe(() -> review(basis, buyer, 401));
            assertFalse(waiting.requests().isEmpty(), "waiting must declare its exact planner dependencies");
            var paused = waiting.result();
            assertTrue(paused.shipments().missions().isEmpty()); assertTrue(paused.companies().goodsTrade().contracts().isEmpty());
            assertEquals(basis.inventory().fungibleResources(), paused.inventory().fungibleResources());
            assertEquals(basis.inventory().economics(), paused.inventory().economics());
            assertEquals(basis.actorExecutions(), paused.actorExecutions());
            assertTrue(planner.pendingCount() > 0);
            // This regression covers the confirmed exception leak, not an arbitrary number
            // of search slices needed for a complete 700-block journey. Connected native
            // continuation is checked by the existing trip scenario, not a second campaign.
        }
    }
    @Test void autonomousQuotesAdmitOneMixedExactTripAndRoundTripItsSharedAuthority() {
        var state = withMixedPurchaseBudget(initial());
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = state.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var buyerMoney = state.inventory().economics().require(buyer.party().id()).balance();
        state = review(state, seller.party().id(), 400); state = review(state, buyer.party().id(), 401);
        assertEquals(1, state.shipments().missions().size(), "compatible goods must share one admitted trip");
        var mission = state.shipments().missions().values().iterator().next();
        var group = state.unitGroups().groups().get(mission.groupId());
        var kinds = mission.shipmentIds().stream().map(state.shipments().shipments()::get).map(Shipment::itemKind).collect(java.util.stream.Collectors.toSet());
        assertTrue(kinds.containsAll(Set.of("minecraft:bread", "minecraft:cobblestone", "minecraft:coal")), kinds.toString());
        assertEquals(buyerMoney, state.inventory().economics().require(buyer.party().id()).balance(), "admission is not sale settlement");
        assertTrue(group.members().stream().allMatch(m -> m.activityOwnerId().equals(group.id())));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        ShipmentStateSupport.validate(state);
    }
    private static FrontierWorldState initial() {
        return ConsolidatedTradeFixture.initial(new WorldId("frontier:consolidated-test"), 20260918065L, false);
    }
    private static FrontierWorldState withMixedPurchaseBudget(FrontierWorldState state) {
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = seller.known().getFirst().party().id();
        var ledger = state.inventory().economics();
        // This fixture tests a mixed manifest, not rationing a tiny treasury.
        // Fund it from existing accounts; no successful sale or receipt is fabricated.
        for (var home : state.bootstrap().settlements()) {
            if (home.id().equals(buyer) || home.id().equals(seller.party().id())) continue;
            if (ledger.availableToReserve(buyer).compareTo(FixedScalar.whole(400)) >= 0) break;
            var available = ledger.availableToReserve(home.id());
            ledger = ledger.transfer(home.id(), buyer, available);
        }
        assertTrue(ledger.availableToReserve(buyer).compareTo(FixedScalar.whole(400)) >= 0);
        return state.withInventory(state.inventory().withEconomics(ledger));
    }
    @Test void noWorkerPreflightPublishesQuotesButNoSaleOrStockMoneyReservation() {
        var state = initial(); var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        state = state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans()
                .withWorkPermissions(home, ResidentWorkPermissions.none())
                .withWorkPermissions(buyer, ResidentWorkPermissions.none())));
        var inventory = state.inventory();
        state = review(state, home, 400); state = review(state, buyer, 401);
        assertEquals(inventory, state.inventory());
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
        assertTrue(state.shipments().missions().isEmpty()); assertTrue(state.unitGroups().groups().isEmpty());
        assertFalse(state.companies().goodsTrade().orders().isEmpty(), "quotes are not binding reservations");
    }
    @Test void observedLossOfOneMixedParcelDoesNotRetireTheAssetOrOtherCommercialObligations() {
        var state = withMixedPurchaseBudget(ConsolidatedTradeFixture.initial(new WorldId("frontier:consolidated-loss"), 20260918065L, true));
        var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        state = review(state, home, 400); state = review(state, buyer, 401);
        var mission = state.shipments().missions().values().iterator().next();
        var parcel = mission.shipmentIds().stream().map(state.shipments().shipments()::get)
                .filter(p -> p.itemKind().equals("minecraft:coal")).findFirst().orElseThrow();
        var ledger = state.inventory().fungibleResources();
        var account = ledger.accounts().get(parcel.sourceAccountId());
        var amounts = new TreeMap<String, Integer>();
        for (var part : account.lotQuantities().entrySet())
            amounts.merge(ledger.lots().get(part.getKey()).itemKind(), part.getValue(), Math::addExact);
        var stacks = new ArrayList<FungiblePhysicalObservation.Stack>();
        for (var entry : amounts.entrySet()) for (int remaining = entry.getValue(); remaining > 0; remaining -= 64)
            stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(mission.sender().containerId(), stacks.size())), entry.getKey(), Math.min(64, remaining)));
        ledger = ledger.rebind(account.id(), 1, FungiblePhysicalObservation.bind(ledger, account.id(), 1, stacks));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(state.inventory().withFungibleResources(ledger)
                .withSurfaceStatus(mission.sender().containerId(), ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(mission.sender().containerId(), ContainerSurfaceStatus.ACTIVE)), mission.sender().containerId());
        var retainedExecution = state.actorExecutions().actors().get(parcel.execution().actorId());
        var loss = assertInstanceOf(FungibleStockDepartureObserved.class, FungibleContainerPlayerEdit.classify(ledger,
                account.id(), mission.sender().containerId(), home, 1, UUID.randomUUID(), UUID.randomUUID(),
                stacks.stream().filter(s -> !s.itemKind().equals(parcel.itemKind())).toList(), SourceStockClaimLoss.admissibleClaims(state, account.id())));
        state = GoodsTradeTest.factForFamily(state, home, loss, 402, "physical-observation");
        assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, state.shipments().shipments().get(parcel.id()).status());
        assertEquals(retainedExecution, state.actorExecutions().actors().get(parcel.execution().actorId()));
        assertEquals(mission, state.shipments().missions().get(mission.id()));
        for (var id : mission.shipmentIds()) if (!id.equals(parcel.id())) {
            var other = state.shipments().shipments().get(id);
            assertFalse(other.terminal()); assertTrue(state.inventory().fungibleResources().claims().containsKey(other.authorization().claimId()));
            assertFalse(state.companies().goodsTrade().contracts().get(other.authorization().claimantId()).terminal());
        }
        ShipmentStateSupport.validate(state);
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void supplyReplanningCannotReplaceAnAnimalsInFlightAssemblyPredecessor() {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:consolidated-assembly"), 20260918065L, true);
        var home = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(home).known().getFirst().party().id();
        state = review(state, home, 400); state = review(state, buyer, 401);
        var mission = state.shipments().missions().values().iterator().next();
        var animal = mission.transportAssetId().orElseThrow();
        assertFalse(mission.supplies().orElseThrow().foodTargets().containsKey(animal));
        var replacement = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority
                .reconsider(state, mission, 402).orElseThrow();
        var group = state.unitGroups().groups().get(mission.groupId());
        var execution = io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group)
                .execution(state, group, group.member(animal)).orElseThrow();
        var movement = new ActorMovement(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority
                .assemblyOrder(mission, animal), 402,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExpeditionAssembly(mission.id()), execution);
        var movements = new TreeMap<>(state.actorMovements()); movements.put(animal, movement);
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(movements));
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.reconsider(state, mission, 402).isEmpty());
        var retained = state;
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority
                .replanned(retained, new ExpeditionSupplyReplanned(mission.id(), mission.supplies().orElseThrow().revision(), replacement), 402));
        assertEquals(movement, state.actorMovements().get(animal));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void mixedTripActuallyLoadsSettlesAndReturnsThroughKernelAfterInFlightRecovery(boolean animal) {
        var initial = ConsolidatedTradeFixture.initial(new WorldId("frontier:consolidated-test"), 20260918065L, animal);
        var world = initial.bootstrap().worldId();
        var home = new SubjectId("settlement:1");
        var seller = initial.companies().goodsTrade().participants().participants().get(home);
        var buyer = seller.known().getFirst().party().id();
        var base = FrontierWorldRuntimeDefinition.configuration(world, initial.bootstrap().seed(), FrontierRulesets.supplyQuarryGraybox());
        var schedules = new ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>();
        schedules.add(GoodsParticipantProcess.review(home, 400)); schedules.add(GoodsParticipantProcess.review(buyer, 401));
        for (var resident : initial.humanPopulation().residents().values()) {
            schedules.add(ResidentActivityProcess.review(resident.id(), 48000));
            schedules.add(ResidentNeedProcess.review(resident.id(), initial.humanPopulation().nutrition(resident.id())
                    .nextThresholdTick(initial.bootstrap().ruleset().residentLife(), resident.characteristics().effectiveMetabolismPermille(0))));
        }
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(world, initial,
                SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), schedules, base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        TransportMission admitted = null; boolean recovered = false, delivered = false, returned = false;
        for (int boundary = 0; boundary < 4000; boundary++) {
            var state = engine.canonicalState().state();
            if (admitted == null && !state.shipments().missions().isEmpty()) {
                admitted = state.shipments().missions().values().iterator().next();
                assertEquals(animal, admitted.transportAssetId().isPresent());
                if (animal) assertEquals(2, state.unitGroups().groups().get(admitted.groupId()).members().size(),
                        "a finite animal needs one actual companion, not one person per parcel");
            }
            if (admitted != null) {
                var mission = state.shipments().missions().get(admitted.id());
                if (mission == null) { returned = true; break; }
                for (var id : mission.shipmentIds()) {
                    var parcel = state.shipments().shipments().get(id);
                    if (!parcel.terminal() && parcel.status() == Shipment.Status.CARRYING && parcel.mobileContainerId().isEmpty())
                        assertEquals(parcel.carriedAccountId(), ActorCarryCapabilities.workCargo(state, parcel.execution().actorId()).orElseThrow().accountId());
                }
                delivered |= mission.stage() == TransportMission.Stage.RETURNING;
                if (!recovered && mission.stage() == TransportMission.Stage.OUTBOUND) {
                    var checkpoint = engine.checkpoint();
                    engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.recoverCanonicalStateAccess(configuration,
                            new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(world, Optional.of(
                                new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(checkpoint, 0)), List.of()));
                    recovered = true;
                }
            }
            var current = engine.canonicalState().state();
            var next = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            if (boundary % 64 == 0) engine.compact(engine.checkpoint().revision());
        }
        assertNotNull(admitted); assertTrue(recovered); assertTrue(delivered);
        var finalState = engine.canonicalState().state();
        var finalMission = finalState.shipments().missions().get(admitted.id());
        var finalGroup = finalState.unitGroups().groups().get(admitted.groupId());
        assertTrue(returned, "mixed cargo must return, not merely be declared: tick=" + engine.checkpoint().instant().ticks()
                + " mission=" + (finalMission == null ? "retired" : finalMission.stage())
                + " group=" + (finalGroup == null ? "retired" : finalGroup.phase())
                + " movingActors=" + finalState.actorMovements().keySet());
        assertNull(finalGroup); assertNull(finalMission);
        var completedGroup = admitted.groupId();
        for (var authority : finalState.actorExecutions().actors().values())
            assertTrue(java.util.stream.Stream.concat(authority.current().stream(), authority.suspended().stream())
                    .noneMatch(e -> e.activityOwnerId().equals(completedGroup)));
    }
    private static FrontierWorldState review(FrontierWorldState state, SubjectId party, long tick) {
        for (var proposed : GoodsParticipantProcess.plan(state, GoodsParticipantProcess.review(party, tick), new SimInstant(tick))) {
            if (proposed.payload() instanceof ScheduleEffect) continue;
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            var payload = codecs.decode(proposed.payload().type(), codecs.encode(proposed.payload()));
            var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:trip-test"), new TransactionId("transaction:trip-test"),
                    state.bootstrap().worldId(), new Revision(1), new SimInstant(tick), proposed.subject(),
                    CauseChain.root(new CommandId("command:trip-test")), payload);
            state = FrontierWorldRuntimeDefinition.reduce(state, event);
        }
        return state;
    }
}
