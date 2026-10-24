package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual policy/queue itinerary, including commercial pickup and current-schema in-flight recovery. */
class BuyerCollectionTest {
    @Test void depletedSourceReserveReleasesUnloadedRemoteGoodsAndMoneyWithoutAWorldTransfer() {
        var configuration = FrontierV3FixtureCatalog.buyerCollectionConfiguration(
                new WorldId("frontier:buyer-preparation-recall"), 20260918065L);
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        for (int turn = 0; turn < 256; turn++) {
            var state = engine.canonicalState().state();
            if (state.shipments().missions().values().stream().anyMatch(mission -> mission.preparingSupplies()
                    && io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.mayReconsider(state, mission))) break;
            var next = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, a))
                    .sorted().findFirst().orElseThrow();
            assertEquals(EngineStatus.Kind.ACTIVE, engine.advanceTo(new SimInstant(Math.max(
                    engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(1, 1024)).status().kind());
        }
        var state = engine.canonicalState().state();
        var mission = state.shipments().missions().values().iterator().next();
        assertEquals(TransportMission.Stage.PREPARING, mission.stage());
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.mayReconsider(state, mission),
                "fixture must reach a reversible preparation boundary before removing food");
        var resources = state.inventory().fungibleResources();
        var depletedKinds = new HashSet<String>();
        depletedKinds.add(FoodCatalog.BREAD);
        for (var id : mission.shipmentIds()) depletedKinds.add(state.shipments().shipments().get(id).itemKind());
        for (var account : new ArrayList<>(resources.accounts().values())) {
            var food = new LinkedHashMap<SubjectId, Integer>();
            for (var entry : account.lotQuantities().entrySet()) {
                if (!depletedKinds.contains(resources.lots().get(entry.getKey()).itemKind())) continue;
                int reserved = 0;
                for (var claim : account.claimQuantities().keySet())
                    reserved += resources.claims().get(claim).lotQuantities().getOrDefault(entry.getKey(), 0);
                if (entry.getValue() > reserved) food.put(entry.getKey(), entry.getValue() - reserved);
            }
            if (!food.isEmpty()) resources = resources.destroy(account.id(), food, Map.of());
        }
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        // The collection fixture disables all seller work to select buyer transport.
        // Re-enable a real bakery after admission: its inputs now have a local need.
        var sellerId = mission.sender().settlementId();
        var baker = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(sellerId)).findFirst().orElseThrow();
        state = state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans()
                .withWorkPermissions(sellerId, new ResidentWorkPermissions(Map.of(ResidentWorkKind.PRODUCTION, Set.of(baker.id()))))));
        long now = engine.checkpoint().instant().ticks();
        state = AcceptedEconomyFixture.publish(state, sellerId, now);
        assertTrue(TransportPreparationRecovery.needed(state, mission, now));
        var before = state;
        var after = TransportPreparationRecovery.recall(state, mission, now);
        assertEquals(before.actorLocations(), after.actorLocations());
        assertEquals(before.inventory().economics().accounts(), after.inventory().economics().accounts(), "no payment or refund is invented");
        for (var id : mission.shipmentIds()) {
            var parcel = before.shipments().shipments().get(id);
            assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, after.shipments().shipments().get(id).status());
            assertEquals(before.inventory().fungibleResources().accounts().get(parcel.sourceAccountId()).lotQuantities(),
                    after.inventory().fungibleResources().accounts().get(parcel.sourceAccountId()).lotQuantities());
            assertFalse(after.inventory().fungibleResources().claims().containsKey(parcel.authorization().claimId()));
            var contract = after.companies().goodsTrade().contracts().get(parcel.authorization().claimantId());
            assertTrue(contract.terminal());
            assertFalse(after.inventory().economics().reservations().containsKey(contract.financialReservationId()));
        }
        assertEquals(TransportMission.Stage.RECOVERING, after.shipments().missions().get(mission.id()).stage());
        var codec = new FrontierWorldStateCodec();
        assertEquals(after, codec.decode(codec.encode(after)));
        assertThrows(IllegalArgumentException.class, () -> TransportPreparationRecovery.recall(after, mission, now),
                "the same withdrawal cannot release resources twice");
    }
    @Test void buyerCollectsWhenSellerHasNoCrewPaysOnlyAtPickupAndReturnsOwnedCargoAfterRecovery() {
        var configuration = FrontierV3FixtureCatalog.buyerCollectionConfiguration(
                new WorldId("frontier:buyer-collection"), 20260918065L);
        var initial = configuration.initialState();
        var seller = initial.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = initial.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var buyerMoney = initial.inventory().economics().require(buyer.party().id()).balance();
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        TransportMission admitted = null; boolean reachedSource = false, paid = false, recovered = false, returned = false, promptRetirement = false;
        GoodsTradeCollected pickup = null;
        int purchased = 0; FixedScalar payment = FixedScalar.ZERO; var originalHolds = new HashSet<SubjectId>();
        var originalContracts = new HashSet<SubjectId>();
        for (int boundary = 0; boundary < 4000; boundary++) {
            var state = engine.canonicalState().state();
            if (admitted == null && !state.shipments().missions().isEmpty()) {
                admitted = state.shipments().missions().values().iterator().next();
                System.out.println("collection fixture: mission=" + admitted.id() + ", group=" + admitted.groupId()
                        + ", home=" + admitted.organization().home().containerId() + ", source=" + admitted.sender().containerId());
                System.out.println("collection timing: tick=" + engine.executionView().instant().ticks()
                        + ", forecast=" + admitted.supplies().orElseThrow().forecastDurationTicks()
                        + ", cargo=" + admitted.shipmentIds().stream().map(state.shipments().shipments()::get)
                            .map(parcel -> parcel.itemKind() + "=" + parcel.quantity()).toList());
                assertEquals(TransportOrganization.Itinerary.DESTINATION_COLLECTION, admitted.organization().itinerary());
                assertEquals(buyer.party().id(), admitted.organization().sponsor().id());
                assertEquals(buyer.party().id(), admitted.organization().home().settlementId());
                assertEquals(TransportMission.Stage.PREPARING, admitted.stage());
                assertEquals(buyerMoney, state.inventory().economics().require(buyer.party().id()).balance());
                for (var id : admitted.shipmentIds()) {
                    var parcel = state.shipments().shipments().get(id);
                    var contract = state.companies().goodsTrade().contracts().get(parcel.authorization().claimantId());
                    assertEquals(GoodsTradeFulfillment.Mode.BUYER_COLLECTION, contract.fulfillment().mode());
                    originalHolds.add(contract.financialReservationId());
                    originalContracts.add(contract.id());
                    purchased += contract.quantity(); payment = payment.plus(contract.outstandingPrice());
                }
            }
            if (admitted != null) {
                var mission = state.shipments().missions().get(admitted.id());
                if (mission == null) { returned = true; break; }
                if (mission.stage() == TransportMission.Stage.RETURNING) {
                    long now = engine.checkpoint().instant().ticks();
                    var progress = io.farfrontier.palemirror.frontier.v3.process.TransportMissionProcess.progress(mission.id(), now);
                    var plan = io.farfrontier.palemirror.frontier.v3.process.TransportMissionProcess.plan(state, progress, now);
                    if (plan.stream().map(ProposedEvent::payload).anyMatch(payload -> payload instanceof TransportMissionAdvanced changed
                            && changed.next() == TransportMission.Stage.COMPLETE)) {
                        promptRetirement = true;
                        assertTrue(plan.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Rescheduled.class::isInstance)
                                .map(ScheduleEffect.Rescheduled.class::cast).anyMatch(event -> event.scheduleId().equals(progress.id())
                                    && event.replacement().dueAt().ticks() == now + 1),
                                "a proven workflow transition must continue next turn, not after the strategic polling interval");
                    }
                }
                reachedSource |= mission.stage() == TransportMission.Stage.LOADING;
                if (pickup == null && mission.stage() == TransportMission.Stage.LOADING) {
                    for (var id : mission.shipmentIds()) {
                        var parcel = state.shipments().shipments().get(id);
                        if (parcel.status() != Shipment.Status.CARRYING
                                || parcel.authorization().kind() != ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT) continue;
                        long now = engine.checkpoint().instant().ticks();
                        var action = GoodsTradeReceiptProcess.review(parcel.authorization().claimantId(), parcel.id(), now);
                        pickup = GoodsTradeReceiptProcess.plan(state, action, new SimInstant(now)).stream()
                                .map(ProposedEvent::payload).filter(GoodsTradeCollected.class::isInstance)
                                .map(GoodsTradeCollected.class::cast).findFirst().orElseThrow();
                        var receipt = pickup;
                        // Compare the exact acceptance transition, not the global
                        // balances over a journey with concurrent independent trades.
                        var accepted = GoodsTradeCollectionSupport.accept(state, buyer.party().id(), receipt, now);
                        var contract = state.companies().goodsTrade().contracts().get(receipt.receipt().contractId());
                        var amount = contract.goodsUnitPrice().multiply(receipt.receipt().title().quantity());
                        assertEquals(state.inventory().economics().require(buyer.party().id()).balance().minus(amount),
                                accepted.inventory().economics().require(buyer.party().id()).balance());
                        assertEquals(state.inventory().economics().require(seller.party().id()).balance().plus(amount),
                                accepted.inventory().economics().require(seller.party().id()).balance());
                        assertThrows(IllegalArgumentException.class, () -> GoodsTradeCollectionSupport.accept(state, seller.party().id(), receipt, now));
                        assertThrows(IllegalArgumentException.class, () -> GoodsTradeCollectionSupport.accept(state, buyer.party().id(),
                                new GoodsTradeCollected(receipt.shipmentId(), receipt.expectedShipmentRevision() - 1, receipt.receipt()), now));
                        break;
                    }
                }
                if (mission.stage() == TransportMission.Stage.TO_SOURCE) {
                    assertTrue(state.inventory().economics().reservations().keySet().containsAll(originalHolds),
                            "this mission must retain its payment holds until actual pickup");
                    for (var id : originalContracts)
                        assertEquals(0, state.companies().goodsTrade().contracts().get(id).acceptedQuantity());
                }
                if (!paid && mission.stage() == TransportMission.Stage.OUTBOUND) {
                    paid = true;
                    for (var id : originalContracts) {
                        var contract = state.companies().goodsTrade().contracts().get(id);
                        assertTrue(contract.fulfilled(), "ordinary pickup must accept this mission's whole allocation");
                    }
                    assertTrue(Collections.disjoint(originalHolds, state.inventory().economics().reservations().keySet()),
                            "the collected contracts must be paid; other admitted trips may retain their own holds");
                    for (var id : mission.shipmentIds()) {
                        var parcel = state.shipments().shipments().get(id);
                        assertEquals(ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT, parcel.authorization().kind());
                        assertEquals(buyer.party().id(), state.inventory().fungibleResources().claims()
                                .get(parcel.authorization().claimId()).economicOwnerId());
                        assertTrue(InternalShipmentIncomingStock.quantity(state.shipments(), buyer.party().id(),
                                parcel.receiver().containerId(), parcel.itemKind()) >= parcel.quantity());
                    }
                    var receipt = Objects.requireNonNull(pickup, "ordinary pickup boundary must have been observed");
                    long now = engine.checkpoint().instant().ticks();
                    assertThrows(IllegalArgumentException.class, () -> GoodsTradeCollectionSupport.accept(state, buyer.party().id(), receipt, now));
                    verifyPaidCargoLossOwner(state, state.shipments().shipments().get(mission.shipmentIds().getFirst()));
                    var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
                    var checkpoint = engine.checkpoint();
                    engine = FrontierEngines.recoverCanonicalStateAccess(configuration, new RecoveryImage(initial.bootstrap().worldId(),
                            Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
                    recovered = true;
                }
            }
            var current = engine.canonicalState().state();
            var next = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())),
                    new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            if (boundary % 64 == 0) engine.compact(engine.checkpoint().revision());
        }
        var last = engine.canonicalState().state();
        assertNotNull(admitted, () -> "no collection admitted: buyer=" + GoodsParticipantView.observe(last,
                last.companies().goodsTrade().participants().participants().get(buyer.party().id()))
                + "; seller=" + GoodsParticipantView.observe(last, last.companies().goodsTrade().participants().participants().get(seller.party().id())));
        assertTrue(reachedSource, "never reached source: " + engine.canonicalState().state().shipments());
        assertTrue(paid, "never purchased: " + engine.canonicalState().state().shipments()); assertTrue(recovered);
        assertTrue(returned, "collection must unload and release its crew; state=" + engine.canonicalState().state().shipments());
        assertTrue(promptRetirement, "the actual recovered collection exercised prompt terminal continuation");
        assertTrue(purchased > 0);
        assertTrue(engine.canonicalState().state().inventory().economics().require(buyer.party().id()).balance()
                .compareTo(buyerMoney.minus(payment)) <= 0, "return does not refund an already accepted purchase");
        assertFalse(engine.canonicalState().state().unitGroups().groups().containsKey(admitted.groupId()));
    }
    /** Isolated authorized disposition accounting, not a fabricated native death receipt. */
    private static void verifyPaidCargoLossOwner(FrontierWorldState state, Shipment parcel) {
        var resources = state.inventory().fungibleResources().releaseClaim(parcel.carriedAccountId(), parcel.authorization().claimId())
                .destroy(parcel.carriedAccountId(), parcel.lotQuantities(), Map.of());
        var observed = state.inventory().withFungibleResources(resources);
        var after = new OwnedGoodsShipmentAuthorization().allocationDisposed(state, parcel, observed).inventoryOnly();
        assertEquals(state.inventory().economics(), after.economics(), "paid cargo loss does not refund or settle again");
        assertFalse(after.fungibleResources().claims().containsKey(parcel.authorization().claimId()));
        assertEquals(Set.of(FrontierWorldStateUpdate.Component.INVENTORY),
                new OwnedGoodsShipmentAuthorization().allocationDisposed(state, parcel, observed).changedComponents());
    }
}
