package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticStationSupplyTest {
    @Test void genericCourierCollectsOutputFromItsMachinePortAndDeliversToPreferredWarehouse() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:automatic-haul"), 125,
                FrontierRulesets.smeltingGraybox()));
        var home = state.bootstrap().settlements().getFirst();
        var facility = home.structures().stream().filter(value -> value.kind() == StructureKind.SMELTERY).findFirst().orElseThrow();
        var spec = ProductionFacilityLayout.station(facility); var record = state.inventory().containers().get(spec.containerId());
        var lot = new SubjectId("lot:automatic-haul-output"); var account = ReferenceContainerCustody.scopeId(record.id());
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().issue(
                new ResourceLot(lot, home.id(), "minecraft:iron_ingot", 3, "fixture-output", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(record.id()), Map.of(lot, 3), Map.of()))));
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var offered = AutomaticStationSupply.offer(state, worker, record, 1).orElseThrow();
        assertEquals(ProductionFacilityLayout.metalStorageId(facility), offered.shipment().receiver().containerId());
        assertEquals(ActorContainerItemOrder.StationPort.OUTPUT, ((ShipmentEndpoint.ProductionPort) offered.shipment().sender()).port());
        var payloads = io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.create();
        assertEquals(offered, payloads.decode(offered.type(), payloads.encode(offered)), "WAL keeps the output pickup port");
        state = InternalShipmentStateSupport.dispatch(state, home.id(), offered);
        var codec = new FrontierWorldStateCodec();
        assertEquals(state, codec.decode(codec.encode(state)), "restart before pickup cannot turn OUTPUT into FUEL");
        state = codec.decode(codec.encode(state));
        var shipment = state.shipments().shipments().get(offered.shipment().id());
        state = state.withActorBody(worker.id(), shipment.sender().station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), 1, Shipment.Status.AWAITING_LOAD);
        state = codec.decode(codec.encode(state));
        shipment = state.shipments().shipments().get(shipment.id());
        state = state.withActorBody(worker.id(), shipment.receiver().station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), shipment.revision(), Shipment.Status.CARRYING);
        shipment = state.shipments().shipments().get(shipment.id());
        state = InternalShipmentStateSupport.receive(state, shipment.id(), new InternalShipmentReceived(shipment.id(),
                shipment.revision(), shipment.reception().orElseThrow().id()));
        assertEquals(3, state.inventory().fungibleResources().unclaimedQuantity(shipment.receivingAccountId(), home.id(), "minecraft:iron_ingot"));
        assertTrue(ProductionStationRecipe.portVacant(state, spec, ActorContainerItemOrder.StationPort.OUTPUT));
        assertEquals(state, codec.decode(codec.encode(state)));
    }
}
