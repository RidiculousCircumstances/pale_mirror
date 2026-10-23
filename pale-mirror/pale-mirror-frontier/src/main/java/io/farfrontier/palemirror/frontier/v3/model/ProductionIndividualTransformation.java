package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** One immutable material-consumption and individual-result transaction, under the station receipt. */
final class ProductionIndividualTransformation {
    static ExactInventory apply(FrontierWorldState state, ProductionStationSpec station,
            ProductionOperation operation, ProductionStationRecipeBoundary boundary, boolean cold) {
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        ProductionOperationInputs.validate(state.inventory(), station, operation, recipe);
        var input = operation.inputCustody().materials().orElseThrow();
        var inventory = state.inventory(); var resources = inventory.fungibleResources();
        var owner = resources.lots().get(input.lots().keySet().iterator().next()).economicOwnerId();
        var result = ProductionIndividualResult.resolve(recipe, state.bootstrap().ruleset().tools(), operation.outputId(), owner,
                new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()), operation.inputCustody().maintenanceTarget());
        if (!boundary.exactAfter().equals(Optional.of(result)) || !boundary.exactBefore().equals(operation.inputCustody().maintenanceTarget())
                || !inventory.slotVacant((InventoryCustody.ContainerSlot) result.custody())
                || recipe.outcome() instanceof ProductionRecipeOutcome.Individual && inventory.items().containsKey(result.id()))
            throw new IllegalArgumentException("station individual result lost its exact pre/postimage");
        resources = cold ? resources.destroy(input.accountId(), input.lots(), input.claims())
                : resources.destroyObserved(input.accountId(), boundary.authorityEpoch(), input.lots(), input.claims(), boundary.after());
        inventory = inventory.withFungibleResources(resources);
        if (boundary.exactBefore().isPresent()) inventory = inventory.withoutItem(boundary.exactBefore().orElseThrow().id());
        return inventory.store(result);
    }
    private ProductionIndividualTransformation() { }
}
