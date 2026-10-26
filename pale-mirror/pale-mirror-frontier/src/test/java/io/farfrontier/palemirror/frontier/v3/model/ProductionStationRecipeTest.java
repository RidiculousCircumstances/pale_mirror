package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionStationRecipeTest {
    @Test
    void declaredConversionRatioBelongsToRecipeNotInventoryAccounting() {
        var recipe = new ProductionRecipe(BakeryRecipes.BREAD, ProductionStationSpec.Capability.BAKING,
                new ProductionRecipe.Ingredient(java.util.Set.of("minecraft:wheat"), 3),
                new ProductionRecipe.Product("minecraft:bread", 1), false, ProductionRecipe.ProcessingMode.ATTENDED);
        var base = FrontierRulesets.production();
        var rules = new FrontierRuleset("frontier-v3-recipe-ratio-test", 25, base.cadence(), base.spatial(),
                base.rates(), base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(),
                base.resourceHarvestColdTravelTicksPerEdge(), base.workCatalog(), base.goodsTrade(),
                base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(), base.execution(),
                new ProductionCatalog(List.of(recipe), List.of(), List.of(ProductionEquipment.bench())));
        var genesis = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ratio-test"), 91L, rules));
        var container = genesis.inventory().containers().values().stream()
                .filter(value -> value.productionStation().isPresent()).findFirst().orElseThrow();
        var station = container.productionStation().orElseThrow();
        var input = new ResourceLot(new SubjectId("lot:ratio-input"), container.ownerId(), "minecraft:wheat", 6,
                "fixture:ratio", List.of());
        var account = new SubjectId("custody:ratio-station");
        var claim = new SubjectId("claim:ratio-input");
        var resources = genesis.inventory().fungibleResources().issue(input,
                new CustodyAccount(account, station.custody(ActorContainerItemOrder.StationPort.INPUT), Map.of(input.id(), 6), Map.of()))
                .reserve(new ClaimAllocation(claim, new SubjectId("job:ratio"), container.ownerId(), input.itemKind(),
                        6, Map.of(input.id(), 6), ClaimPurpose.PRODUCTION_WORK), account);
        var state = genesis.withInventory(genesis.inventory().withFungibleResources(resources));
        var output = new ResourceLot(new SubjectId("lot:ratio-output"), container.ownerId(), "minecraft:bread", 2,
                "recipe:ratio", List.of(input.id()));
        var noOperation = state;
        assertThrows(java.util.NoSuchElementException.class, () -> ProductionStationRecipe.transformCold(noOperation, station, recipe.id(),
                account, Map.of(input.id(), 6), Map.of(claim, 6), output), "inventory alone is not a completed operation");
        state = completed(state, station, recipe.id(), account, Map.of(input.id(), 6), Map.of(claim, 6), output);
        final var ready = state;
        var result = ProductionStationRecipe.transformCold(state, station, recipe.id(), account,
                Map.of(input.id(), 6), Map.of(claim, 6), output);
        assertEquals(0, result.fungibleResources().totalQuantity(container.ownerId(), "minecraft:wheat")
                - genesis.inventory().fungibleResources().totalQuantity(container.ownerId(), "minecraft:wheat"));
        assertEquals(Map.of(output.id(), 2), result.fungibleResources().accounts().get(ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.OUTPUT))).lotQuantities());
        assertEquals(false, result.fungibleResources().claims().containsKey(claim));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.transformCold(ready, station,
                new ProductionRecipe.Id("pale_mirror:unknown"), account, Map.of(input.id(), 6), Map.of(claim, 6), output));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.transformCold(ready, station,
                recipe.id(), account, Map.of(input.id(), 6), Map.of(claim, 6), output.withQuantity(6)),
                "same-count output is not legal when the declared recipe is three-to-one");
    }

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
                .issue(wheat, new CustodyAccount(accountId, machine.custody(ActorContainerItemOrder.StationPort.INPUT),
                        Map.of(wheatId, 64), Map.of()))
                .reserve(new ClaimAllocation(claimId, jobId, container.ownerId(), "minecraft:wheat", 64,
                        Map.of(wheatId, 64), ClaimPurpose.PRODUCTION_WORK), accountId);
        FrontierWorldState cold = completed(genesis.withInventory(genesis.inventory().withFungibleResources(resources)),
                machine, BakeryRecipes.BREAD, accountId, Map.of(wheatId, 64), Map.of(claimId, 64), bread);
        var outputAccount = ContainerResourceAccounts.id(machine.custody(ActorContainerItemOrder.StationPort.OUTPUT));
        ExactInventory cooked = ProductionStationRecipe.transformCold(cold, machine, BakeryRecipes.BREAD, accountId,
                Map.of(wheatId, 64), Map.of(claimId, 64), bread);
        assertEquals(Map.of(breadId, 64), cooked.fungibleResources().accounts().get(outputAccount).lotQuantities());
        assertEquals(machine.custody(ActorContainerItemOrder.StationPort.OUTPUT),
                cooked.fungibleResources().accounts().get(outputAccount).custody());
        assertEquals(new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:wheat", 64),
                ReferenceContainerCustody.expectedFungibleSlot(cold, machine.containerId(), machine.inputSlots().getFirst()).orElseThrow());
        FrontierWorldState coldOutput = transformed(cold, machine, cooked);
        assertEquals(new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:bread", 64),
                ReferenceContainerCustody.expectedFungibleSlot(coldOutput, machine.containerId(), machine.outputSlot()).orElseThrow());
        assertEquals(java.util.Optional.empty(),
                ReferenceContainerCustody.expectedFungibleSlot(coldOutput, machine.containerId(), machine.inputSlots().getFirst()),
                "an unbound station output must retain its declared physical slot through restart");

        InventoryCustody.ContainerSlot input = new InventoryCustody.ContainerSlot(container.id(), machine.inputSlots().getFirst());
        InventoryCustody.ContainerSlot output = new InventoryCustody.ContainerSlot(container.id(), machine.outputSlot());
        FungibleResourceLedger bound = cold.inventory().fungibleResources().rebind(accountId, 7,
                FungiblePhysicalObservation.bind(cold.inventory().fungibleResources(), accountId, 7, List.of(
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(input), "minecraft:wheat", 64))));
        FrontierWorldState hot = ReferenceContainerCustodyFixtures.observedAndHeld(
                cold.withInventory(cold.inventory().withFungibleResources(bound)), machine.containerId());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.transformCold(hot, machine, BakeryRecipes.BREAD,
                accountId, Map.of(wheatId, 64), Map.of(claimId, 64), bread));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.transformObserved(hot, machine, BakeryRecipes.BREAD,
                accountId, 7, Map.of(wheatId, 64), Map.of(claimId, 64), bread,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(input), "minecraft:bread", 64))),
                "a machine cannot report finished output in its input port");
        ExactInventory observed = ProductionStationRecipe.transformObserved(hot, machine, BakeryRecipes.BREAD,
                accountId, 7, Map.of(wheatId, 64), Map.of(claimId, 64), bread,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(output), "minecraft:bread", 64)));
        assertEquals(Map.of(breadId, 64), observed.fungibleResources().accounts().get(outputAccount).lotQuantities());
        assertEquals(new PhysicalStackAddress.ContainerSlot(output),
                observed.fungibleResources().bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(outputAccount)).findFirst().orElseThrow().address());
        FrontierWorldState releasedOutput = transformed(hot, machine, observed.withFungibleResources(
                observed.fungibleResources().releaseBindings(outputAccount, 7)));
        assertEquals(new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:bread", 64),
                ReferenceContainerCustody.expectedFungibleSlot(releasedOutput, machine.containerId(), machine.outputSlot()).orElseThrow());
    }
    private static FrontierWorldState transformed(FrontierWorldState state, ProductionStationSpec station, ExactInventory inventory) {
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionStations(
                ProductionStationProcessing.transformed(state, state.productionStations().require(station.id()).operation().orElseThrow())));
    }
    private static FrontierWorldState completed(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipe,
            SubjectId account, Map<SubjectId, Integer> lots, Map<SubjectId, Integer> claims, ResourceLot output) {
        var id = new SubjectId("operation:station-recipe-test");
        var progress = new WorkProgress(1000, 1000, 1, 0, 1);
        var operation = new ProductionOperation(new ProductionOperation.Id(id), station.id(), id, recipe,
                new ProductionOperation.FungibleInput(account, lots, claims), output.id(),
                lots.values().stream().mapToInt(Integer::intValue).sum(), output.quantity(), Optional.of(progress), false,
                new ProductionOperation.PlayerInitiator(new java.util.UUID(0, 1), ProductionOperation.PlayerAttendanceIntent.RUN_REQUESTED), Optional.empty());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(ProductionStationProcessing.adoptInputOwnership(state.inventory(), operation)).productionStations(
                state.productionStations().with(state.productionStations().require(station.id()).start(operation))));
    }

}
