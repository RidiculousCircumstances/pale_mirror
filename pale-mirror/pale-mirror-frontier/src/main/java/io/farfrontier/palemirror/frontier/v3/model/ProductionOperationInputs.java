package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Machine-owned validation of consumed materials and a separately retained maintenance target. */
final class ProductionOperationInputs {
    static void validate(ExactInventory inventory, ProductionStationSpec station,
                         ProductionOperation operation, ProductionRecipe recipe) {
        if ((recipe.outcome() instanceof ProductionRecipeOutcome.Repair)
                != (operation.inputCustody() instanceof ProductionOperation.RepairInput))
            throw new IllegalArgumentException("recipe and operation disagree on maintenance identity");
        if (operation.inputCustody() instanceof ProductionOperation.ExactInput exact) {
            var item = inventory.items().get(exact.itemId());
            if (!(recipe.outcome() instanceof ProductionRecipeOutcome.Fungible) || item == null
                    || item.count() != operation.inputQuantity() || !recipe.ingredient().accepts(item.itemKind())
                    || !item.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot())))
                throw new IllegalArgumentException("station operation lost its exact reserved input");
            return;
        }
        var input = operation.inputCustody().materials().orElseThrow();
        var ledger = inventory.fungibleResources(); var account = ledger.accounts().get(input.accountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Container(station.containerId()))
                || input.lots().entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue()
                    || !recipe.ingredient().accepts(ledger.lots().get(entry.getKey()).itemKind()))
                || input.claims().entrySet().stream().anyMatch(entry -> account.claimQuantities().getOrDefault(entry.getKey(), 0) != entry.getValue()
                    || ledger.claims().get(entry.getKey()).purpose() != ClaimPurpose.STATION_PROCESSING
                    || !ledger.claims().get(entry.getKey()).claimantId().equals(operation.id().value())))
            throw new IllegalArgumentException("station operation lost its exact lot/claim allocation");
        var owners = input.lots().keySet().stream().map(ledger.lots()::get).map(ResourceLot::economicOwnerId).distinct().toList();
        if (owners.size() != 1) throw new IllegalArgumentException("station recipe crosses material ownership");
        operation.inputCustody().maintenanceTarget().ifPresent(target -> {
            if (!target.equals(inventory.items().get(target.id())) || !target.id().equals(operation.outputId())
                    || !target.economicOwnerId().equals(owners.getFirst()) || !target.itemKind().equals(recipe.product().itemKind())
                    || !target.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(),
                        station.slot(ActorContainerItemOrder.StationPort.MAINTENANCE_TARGET))))
                throw new IllegalArgumentException("station maintenance lost its exact target port, owner or condition");
        });
    }
    private ProductionOperationInputs() { }
}
