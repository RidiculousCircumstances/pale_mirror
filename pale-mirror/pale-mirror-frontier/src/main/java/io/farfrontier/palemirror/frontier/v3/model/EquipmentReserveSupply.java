package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.ProductionRequirement;
import java.util.*;

/** Planned equipment creates real material obligations. Common sourcing chooses local production or imports upstream. */
final class EquipmentReserveSupply implements ReserveSupplyPort {
    public Key key() { return Key.EQUIPMENT; }
    public Facts read(FrontierWorldState state, SubjectId home) { return readAt(state, home, 0); }
    public Facts readAt(FrontierWorldState state, SubjectId home, long tick) {
        var needs = new TreeMap<String, Need>(); var catalog = state.bootstrap().ruleset().production();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var retained = EquipmentStockProjection.retainedItems(state, home);
        var committed = WorkEquipmentClaimComposition.reserved(state);
        var promisedSale = state.companies().goodsTrade().contracts().values().stream().filter(c -> c.seller().id().equals(home))
                .flatMap(c -> c.individualAllocations().values().stream()).map(IndividualResourceAllocation::itemId)
                .collect(java.util.stream.Collectors.toSet());
        for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy())) {
            if (commodity.stock().representation() != GoodsStockSpecification.Representation.INDIVIDUAL
                    || state.bootstrap().ruleset().tools().find(commodity.itemKind()).isEmpty()) continue;
            var containers = GoodsParticipantStorage.containers(state, participant, commodity.itemKind());
            long protectedStock = ExactItemReadIndex.owned(state.inventory(), home, commodity.itemKind()).stream().filter(item -> retained.contains(item.id()) && (!committed.contains(item.id()) || promisedSale.contains(item.id()))
                    && item.economicOwnerId().equals(home) && item.itemKind().equals(commodity.itemKind()) && commodity.stock().accepts(item)
                    && item.custody() instanceof InventoryCustody.ContainerSlot slot && containers.contains(slot.containerId())).count();
            long incoming = StockSupplyComposition.pending(state, StockSupplyPort.Key.TRADE).stream().filter(c ->
                    c.economicOwner().equals(home) && c.itemKind().equals(commodity.itemKind())).mapToLong(StockSupplyPort.Commitment::incoming).sum();
            needs.put(commodity.itemKind(), new Need(Rate.ZERO, Math.addExact(protectedStock, incoming)));
        }
        for (var purchase : EquipmentProductionPlanning.acquisition(state, home, tick).purchases())
            needs.merge(purchase.itemKind(), new Need(Rate.ZERO, purchase.quantity()),
                    (a, b) -> new Need(Rate.ZERO, Math.addExact(a.immediate(), b.immediate())));
        long heat = 0;
        var plans = EquipmentProductionPlanning.planned(state, home, tick);
        var first = new LinkedHashMap<String, EquipmentProductionPlanning.Planned>();
        for (var planned : plans) {
            first.putIfAbsent(planned.request().pool(), planned);
            var recipe = planned.request().recipe();
            var material = recipe.ingredient().acceptedItems().stream().sorted().findFirst().orElseThrow();
            needs.merge(material, new Need(Rate.ZERO, Math.multiplyExact(planned.operations(), recipe.ingredient().quantity())),
                    (a, b) -> new Need(Rate.ZERO, Math.addExact(a.immediate(), b.immediate())));
            heat = Math.addExact(heat, Math.multiplyExact(planned.operations(), recipe.heatPerBatch()));
        }
        long supplied = state.inventory().containers().values().stream().filter(c -> c.ownerId().equals(home))
                .flatMap(c -> c.productionStation().stream()).filter(s -> s.capability() == ProductionStationSpec.Capability.FABRICATION)
                .mapToLong(s -> ProductionStationEnergy.suppliedHeat(state, s.id())).sum();
        var required = new TreeMap<String, ProductionRequirement>();
        var deficits = new HashMap<String, EquipmentStockProjection.Deficit>();
        EquipmentStockProjection.deficits(state, home).forEach(deficit -> deficits.put(deficit.demand().pool(), deficit));
        long window = SupplyPlanningWindow.forSettlement(state, home, List.of()).ticks();
        long minimumHeat = 0, fuelDeadline = Long.MAX_VALUE;
        for (var planned : first.values()) {
            var deficit = Objects.requireNonNull(deficits.get(planned.request().pool()));
            long coveredWear = deficit.demand().plannedWear() - deficit.missingWear();
            long deadline = deficit.immediate() ? 0 : Math.ceilDiv(Math.multiplyExact(coveredWear, window), deficit.demand().plannedWear());
            var recipe = planned.request().recipe();
            var material = recipe.ingredient().acceptedItems().stream().sorted().findFirst().orElseThrow();
            required.merge(material, new ProductionRequirement(
                    recipe.ingredient().quantity(), deadline), ProductionRequirement::plus);
            minimumHeat = Math.addExact(minimumHeat, recipe.heatPerBatch());
            if (recipe.heatPerBatch() > 0) fuelDeadline = Math.min(fuelDeadline, deadline);
        }
        if (heat > supplied) {
            var fuel = catalog.fuels().stream().sorted(Comparator.comparingLong(ProductionCatalog.Fuel::heatUnits).reversed()
                    .thenComparing(ProductionCatalog.Fuel::itemKind)).findFirst().orElseThrow();
            needs.merge(fuel.itemKind(), new Need(Rate.ZERO, Math.ceilDiv(heat - supplied, fuel.heatUnits())),
                    (a, b) -> new Need(Rate.ZERO, Math.addExact(a.immediate(), b.immediate())));
            if (minimumHeat > supplied) required.merge(fuel.itemKind(),
                    new ProductionRequirement(
                            Math.ceilDiv(minimumHeat - supplied, fuel.heatUnits()), fuelDeadline),
                    ProductionRequirement::plus);
        }
        return new Facts(needs, List.of(), Map.of(), required);
    }
}
