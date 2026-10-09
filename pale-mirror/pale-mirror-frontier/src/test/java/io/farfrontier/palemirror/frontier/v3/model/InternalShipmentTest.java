package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InternalShipmentTest {
    @Test void sameOwnerHaulAcceptsExactStockWithoutSellingItAndRejectsForgedOrRepeatedReceipts() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:internal-receipt"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r1")));
        var site = state.extractionSites().deposits().values().iterator().next().site();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        var source = new SubjectId("custody:test-quarry-stock"); var lot = new SubjectId("lot:test-quarry-stock");
        var claimId = new SubjectId("claim:test-local-haul"); var shipmentId = new SubjectId("shipment:test-local-haul");
        var resource = new ResourceLot(lot, home.id(), "minecraft:cobblestone", 10, "isolated-fixture", List.of());
        var ledger = state.inventory().fungibleResources().issue(resource,
                new CustodyAccount(source, new ResourceCustody.Container(site.containerId()), Map.of(lot, 10), Map.of()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger)));
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var receiver = GoodsParticipantDeclarations.endpoint(home);
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, receiver.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(receiver.containerId()));
        var shipment = new Shipment(shipmentId, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT,
                claimId, home.id(), shipmentId, 1), state.actorExecutions().next(worker.id(), ActorActivityKind.COURIER, shipmentId),
                new ShipmentEndpoint.ExtractiveSite(home.id(), site.id(), site.containerId(), site.layout().storagePort()), receiver,
                source, new SubjectId("custody:test-local-hauler"), receiving, "minecraft:cobblestone", Map.of(lot, 10), Shipment.Status.AWAITING_LOAD, 1);
        var claim = new ClaimAllocation(claimId, home.id(), home.id(), shipment.itemKind(), 10, shipment.lotQuantities(), ClaimPurpose.INTERNAL_LOGISTICS);
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentStateSupport.dispatch(before,
                new SubjectId("settlement:wrong-issuer"), new InternalShipmentDispatched(shipment, claim)));
        var economics = state.inventory().economics();
        var sources = state.extractionSites();
        state = InternalShipmentStateSupport.dispatch(state, home.id(), new InternalShipmentDispatched(shipment, claim));
        state = state.withActorBody(worker.id(), site.layout().storagePort().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipmentId, shipmentId, 1, Shipment.Status.AWAITING_LOAD);
        state = state.withActorBody(worker.id(), receiver.station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipmentId, shipmentId, 2, Shipment.Status.CARRYING);
        var unloaded = state.shipments().shipments().get(shipmentId);
        assertEquals(Shipment.Status.DELIVERED, unloaded.status());
        assertTrue(state.inventory().fungibleResources().claims().containsKey(claimId));
        var expected = state;
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentStateSupport.receive(expected, shipmentId,
                new InternalShipmentReceived(shipmentId, unloaded.revision(), new SubjectId("receipt:forged"))));
        var receipt = new InternalShipmentReceived(shipmentId, unloaded.revision(), unloaded.reception().orElseThrow().id());
        state = InternalShipmentStateSupport.receive(state, shipmentId, receipt);
        assertEquals(10, state.inventory().fungibleResources().unclaimedQuantity(receiving, home.id(), shipment.itemKind()));
        assertEquals(economics, state.inventory().economics());
        assertEquals(sources, state.extractionSites());
        assertFalse(state.inventory().fungibleResources().claims().containsKey(claimId));
        assertTrue(state.shipments().shipments().get(shipmentId).reception().isEmpty());
        var accepted = state;
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentStateSupport.receive(accepted, shipmentId, receipt));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
}
