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
    @Test void buyerCollectsWhenSellerHasNoCrewPaysOnlyAtPickupAndReturnsOwnedCargoAfterRecovery() {
        var configuration = FrontierV3FixtureCatalog.buyerCollectionConfiguration(
                new WorldId("frontier:buyer-collection"), 20260918065L);
        var initial = configuration.initialState();
        var seller = initial.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = initial.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var buyerMoney = initial.inventory().economics().require(buyer.party().id()).balance();
        var sellerMoney = initial.inventory().economics().require(seller.party().id()).balance();
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        TransportMission admitted = null; boolean reachedSource = false, paid = false, recovered = false, returned = false, promptRetirement = false;
        GoodsTradeCollected pickup = null;
        int purchased = 0; FixedScalar payment = FixedScalar.ZERO; var originalHolds = new HashSet<SubjectId>();
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
                        assertThrows(IllegalArgumentException.class, () -> GoodsTradeCollectionSupport.accept(state, seller.party().id(), receipt, now));
                        assertThrows(IllegalArgumentException.class, () -> GoodsTradeCollectionSupport.accept(state, buyer.party().id(),
                                new GoodsTradeCollected(receipt.shipmentId(), receipt.expectedShipmentRevision() - 1, receipt.receipt()), now));
                        break;
                    }
                }
                if (mission.stage() == TransportMission.Stage.TO_SOURCE)
                    assertEquals(buyerMoney, state.inventory().economics().require(buyer.party().id()).balance());
                if (!paid && mission.stage() == TransportMission.Stage.OUTBOUND) {
                    paid = true;
                    assertEquals(buyerMoney.minus(payment), state.inventory().economics().require(buyer.party().id()).balance());
                    assertEquals(sellerMoney.plus(payment), state.inventory().economics().require(seller.party().id()).balance());
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
        assertNotNull(admitted);
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
