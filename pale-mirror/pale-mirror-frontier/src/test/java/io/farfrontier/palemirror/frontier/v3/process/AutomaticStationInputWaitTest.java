package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticStationInputWaitTest {
    @Test void automaticEmptyInputParksAndActualInputReceiptAddressesOnlyItsStation() {
        var empty = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:automatic-input-wait"),
                125, FrontierRulesets.smeltingGraybox()));
        var record = empty.inventory().containers().values().stream().filter(container -> container.productionStation()
                .filter(spec -> spec.capability() == ProductionStationSpec.Capability.SMELTING).isPresent()).findFirst().orElseThrow();
        var spec = record.productionStation().orElseThrow();
        var action = ProductionStationProcessModule.review(spec.id(), 1);
        var planner = ProductionStationProcessModule.planner();
        assertTrue(planner.held(empty, action));
        var proof = planner.holdProof(empty, action).orElseThrow();
        assertTrue(proof.stillHeld().test(empty));
        var lot = new SubjectId("lot:actual-station-input");
        var custody = spec.custody(ActorContainerItemOrder.StationPort.INPUT);
        var ledger = empty.inventory().fungibleResources().issue(new ResourceLot(lot, record.ownerId(), "minecraft:raw_iron",
                3, "fixture:received-input", List.of()), new CustodyAccount(ContainerResourceAccounts.id(custody), custody,
                Map.of(lot, 3), Map.of()));
        var stocked = empty.withInventory(empty.inventory().withFungibleResources(ledger));
        assertFalse(proof.stillHeld().test(stocked));
        assertFalse(planner.held(stocked, action));
        assertEquals(Set.of(spec.id()), ProductionStationProcessModule.wakeKeys(empty, stocked));
        var restored = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(stocked));
        assertFalse(planner.held(restored, action));
        var fuelLot = new SubjectId("lot:unrelated-fuel-receipt");
        var fuel = spec.custody(ActorContainerItemOrder.StationPort.FUEL);
        var fuelled = stocked.withInventory(stocked.inventory().withFungibleResources(ledger.issue(
                new ResourceLot(fuelLot, record.ownerId(), "minecraft:coal", 1, "fixture:fuel-receipt", List.of()),
                new CustodyAccount(ContainerResourceAccounts.id(fuel), fuel, Map.of(fuelLot, 1), Map.of()))));
        assertTrue(ProductionStationProcessModule.wakeKeys(stocked, fuelled).isEmpty(),
                "fuel beside an unchanged empty-operation input does not address an input wait");
        var second = empty.inventory().containers().values().stream().flatMap(container -> container.productionStation().stream())
                .filter(value -> !value.id().equals(spec.id()) && value.capability() == spec.capability()).findFirst().orElseThrow();
        var destination = second.custody(ActorContainerItemOrder.StationPort.INPUT);
        var movedLedger = fuelled.inventory().fungibleResources().transferToNewAccount(ContainerResourceAccounts.id(custody),
                new CustodyAccount(ContainerResourceAccounts.id(destination), destination, Map.of(lot, 3), Map.of()));
        var moved = fuelled.withInventory(fuelled.inventory().withFungibleResources(movedLedger));
        assertEquals(Set.of(spec.id(), second.id()), ProductionStationProcessModule.wakeKeys(fuelled, moved),
                "a move wakes both the departed input and its exact new recipient");
        var removed = moved.withInventory(moved.inventory().withFungibleResources(
                movedLedger.destroy(ContainerResourceAccounts.id(destination), Map.of(lot, 3), Map.of())));
        assertEquals(Set.of(second.id()), ProductionStationProcessModule.wakeKeys(moved, removed));
        assertTrue(planner.held(moved, action));
        assertEquals(Set.of(spec.id()), ProductionStationProcessModule.wakeKeys(empty, stocked));
        assertTrue(planner.held(empty, action), "a later input cannot leak into its immutable predecessor");
    }
}
