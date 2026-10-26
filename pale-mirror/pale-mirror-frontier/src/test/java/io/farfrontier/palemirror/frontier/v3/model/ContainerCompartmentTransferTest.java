package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Accounting accepts a witnessed location, without classifying a material's role by its name. */
class ContainerCompartmentTransferTest {
    @Test void movingBetweenSectionsPreservesLotsAndEconomicTitleRatherThanMintingAGift() {
        var state = AutomaticStationProductionTest.initial(); var record = AutomaticStationProductionTest.station(state);
        var spec = record.productionStation().orElseThrow(); var lot = new SubjectId("lot:section-move");
        var input = spec.custody(ActorContainerItemOrder.StationPort.INPUT); var fuel = spec.custody(ActorContainerItemOrder.StationPort.FUEL);
        var source = ContainerResourceAccounts.id(input); var destination = ContainerResourceAccounts.id(fuel);
        var owner = state.bootstrap().settlements().get(1).id();
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, owner, "minecraft:coal", 5, "fixture:original-title", List.of()),
                new CustodyAccount(source, input, Map.of(lot, 5), Map.of()));
        var before = List.of(ProductionStationOperatorTest.stack(spec, spec.inputSlots().getFirst(), "minecraft:coal", 5));
        ledger = ledger.rebind(source, 1, FungiblePhysicalObservation.bind(ledger, source, 1, before));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(state.inventory().withFungibleResources(ledger)), record.id());
        var accounts = new TreeMap<ContainerSectionId, SubjectId>();
        for (var section : record.compartmentLayout().sections().keySet()) accounts.put(section, ContainerResourceAccounts.id(new ResourceCustody.Container(record.id(), section)));
        var after = List.of(ProductionStationOperatorTest.stack(spec, spec.fuelSlot().orElseThrow(), "minecraft:coal", 5));
        var member = new ContainerPlayerEditObserved.Member(record.id(), accounts, record.ownerId(), 1, before, after);
        var handoff = ContainerCompartmentTransfer.propose(state, member).orElseThrow();
        state = FungibleResourceHandoffStateSupport.apply(state, handoff);
        assertEquals(Map.of(lot, 5), state.inventory().fungibleResources().accounts().get(destination).lotQuantities());
        assertEquals(owner, state.inventory().fungibleResources().lots().get(lot).economicOwnerId());
        assertEquals("fixture:original-title", state.inventory().fungibleResources().lots().get(lot).provenance());
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(source));
        var changedCount = List.of(ProductionStationOperatorTest.stack(spec, spec.fuelSlot().orElseThrow(), "minecraft:coal", 4));
        assertTrue(ContainerCompartmentTransfer.propose(state, new ContainerPlayerEditObserved.Member(record.id(), accounts,
                record.ownerId(), 1, before, changedCount)).isEmpty(), "unbalanced external loss is not an internal move");
    }
}
