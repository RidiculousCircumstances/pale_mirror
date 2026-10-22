package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Retained machine promises prevent duplicate production while raw materials are being transformed. */
final class AutomaticStationStockSupply implements StockSupplyPort {
    @Override public Key key() { return Key.AUTOMATIC_STATIONS; }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        var result = new ArrayList<Commitment>();
        for (var machine : state.productionStations().machines().values()) machine.operation().ifPresent(operation -> {
            if (!(operation.initiator() instanceof ProductionOperation.SettlementInitiator owner) || operation.transformed()) return;
            var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
            result.add(new Commitment(operation.id().value(), owner.settlementId(), recipe.product().itemKind(), operation.outputQuantity(), 0));
            var input = (ProductionOperation.FungibleInput) operation.inputCustody(); var byKind = new TreeMap<String, Integer>();
            input.lots().forEach((id, count) -> byKind.merge(state.inventory().fungibleResources().lots().get(id).itemKind(), count, Math::addExact));
            byKind.forEach((kind, count) -> result.add(new Commitment(operation.id().value(), owner.settlementId(), kind, 0, count)));
        });
        return List.copyOf(result);
    }
    @Override public long inputDemand(FrontierWorldState state, SubjectId owner, String itemKind, long atTick) { return 0; }
}
