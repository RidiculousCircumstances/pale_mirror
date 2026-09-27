package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionStationRecipeTest {
    @Test
    void coldAndObservedBreadRecipesConsumeOnlyTheMachineInputAndRetainOutputAtThatMachine() {
        FrontierWorldState genesis = FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:station-recipe"), 91L));
        ContainerRecord container = genesis.inventory().containers().values().stream()
                .filter(value -> value.productionStation().isPresent()).findFirst().orElseThrow();
        ProductionStationSpec machine = container.productionStation().orElseThrow();
        SubjectId accountId = new SubjectId("custody:station-recipe");
        SubjectId wheatId = new SubjectId("lot:station-recipe-wheat");
        SubjectId breadId = new SubjectId("lot:station-recipe-bread");
        SubjectId claimId = new SubjectId("claim:station-recipe");
        SubjectId jobId = new SubjectId("job:station-recipe");
        ResourceLot wheat = new ResourceLot(wheatId, container.ownerId(), "minecraft:wheat", 64,
                "fixture:bakery", List.of());
        ResourceLot bread = new ResourceLot(breadId, container.ownerId(), "minecraft:bread", 64,
                "recipe:bread", List.of(wheatId));
        FungibleResourceLedger resources = genesis.inventory().fungibleResources()
                .issue(wheat, new CustodyAccount(accountId, new ResourceCustody.Container(container.id()),
                        Map.of(wheatId, 64), Map.of()))
                .reserve(new ClaimAllocation(claimId, jobId, container.ownerId(), "minecraft:wheat", 64,
                        Map.of(wheatId, 64), ClaimPurpose.PRODUCTION_WORK), accountId);
        FrontierWorldState cold = genesis.withInventory(genesis.inventory().withFungibleResources(resources));
        ExactInventory cooked = ProductionStationRecipe.transformCold(cold, machine, accountId,
                Map.of(wheatId, 64), Map.of(claimId, 64), bread);
        assertEquals(Map.of(breadId, 64), cooked.fungibleResources().accounts().get(accountId).lotQuantities());
        assertEquals(new ResourceCustody.Container(machine.containerId()),
                cooked.fungibleResources().accounts().get(accountId).custody());
        assertEquals(new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:wheat", 64),
                ReferenceContainerCustody.expectedFungibleSlot(cold, machine.containerId(), machine.inputSlot()).orElseThrow());
        FrontierWorldState coldOutput = cold.withInventory(cooked);
        assertEquals(new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:bread", 64),
                ReferenceContainerCustody.expectedFungibleSlot(coldOutput, machine.containerId(), machine.outputSlot()).orElseThrow());
        assertEquals(java.util.Optional.empty(),
                ReferenceContainerCustody.expectedFungibleSlot(coldOutput, machine.containerId(), machine.inputSlot()),
                "an unbound station output must retain its declared physical slot through restart");

        InventoryCustody.ContainerSlot input = new InventoryCustody.ContainerSlot(container.id(), machine.inputSlot());
        InventoryCustody.ContainerSlot output = new InventoryCustody.ContainerSlot(container.id(), machine.outputSlot());
        FungibleResourceLedger bound = resources.rebind(accountId, 7,
                FungiblePhysicalObservation.bind(resources, accountId, 7, List.of(
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(input), "minecraft:wheat", 64))));
        FrontierWorldState hot = ReferenceContainerCustodyFixtures.observedAndHeld(
                genesis.withInventory(genesis.inventory().withFungibleResources(bound)), machine.containerId());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.transformCold(hot, machine,
                accountId, Map.of(wheatId, 64), Map.of(claimId, 64), bread));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.transformObserved(hot, machine,
                accountId, 7, Map.of(wheatId, 64), Map.of(claimId, 64), bread,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(input), "minecraft:bread", 64))),
                "a machine cannot report finished output in its input port");
        ExactInventory observed = ProductionStationRecipe.transformObserved(hot, machine,
                accountId, 7, Map.of(wheatId, 64), Map.of(claimId, 64), bread,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(output), "minecraft:bread", 64)));
        assertEquals(Map.of(breadId, 64), observed.fungibleResources().accounts().get(accountId).lotQuantities());
        assertEquals(new PhysicalStackAddress.ContainerSlot(output),
                observed.fungibleResources().bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(accountId)).findFirst().orElseThrow().address());
        FrontierWorldState releasedOutput = cold.withInventory(observed.withFungibleResources(
                observed.fungibleResources().releaseBindings(accountId, 7)));
        assertEquals(new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:bread", 64),
                ReferenceContainerCustody.expectedFungibleSlot(releasedOutput, machine.containerId(), machine.outputSlot()).orElseThrow());
    }
}
