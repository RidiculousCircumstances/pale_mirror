package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Commodity-independent read of held goods and already accepted production intent. Never calculates a plan. */
final class FrontierV3SettlementStockDiagnosticJson {
    static String render(FrontierWorldState state, SubjectId subject) {
        var stockKinds = state.bootstrap().ruleset().goodsTrade().policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream()
                .map(GoodsTradeRules.Commodity::itemKind).sorted().toList();
        var stockCounts = new java.util.LinkedHashMap<String, Long>();
        stockKinds.forEach(kind -> stockCounts.put(kind, ProductiveResourceStock.held(state.inventory(), subject, kind)));
        var available = new java.util.LinkedHashMap<String, Long>();
        for (var kind : stockKinds) {
            long quantity = 0;
            for (var container : ResourceStorageSelection.containers(state, subject, kind, java.util.Set.of(ResourceStorageRole.WAREHOUSE))) {
                var account = FungibleResourceCustodySupport.accountAtContainer(state, container);
                if (account.isPresent()) quantity += state.inventory().fungibleResources().unclaimedQuantity(account.orElseThrow().id(), subject, kind);
            }
            available.put(kind, quantity);
        }
        var ceilings = state.companies().goodsTrade().participants().economicState(subject).accepted()
                .map(io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlan::productionCeilings).orElse(java.util.Map.of());
        String productionCeilings = ceilings.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> "\"" + quote(entry.getKey()) + "\":" + entry.getValue()).collect(java.util.stream.Collectors.joining(",", "{", "}"));
        String stockPresent = stockCounts.entrySet().stream().map(entry -> "\"" + quote(entry.getKey()) + "\":" + (entry.getValue() > 0))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        String warehouseStock = available.entrySet().stream().map(entry -> "\"" + quote(entry.getKey()) + "\":" + (entry.getValue() > 0))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        return ",\"stockPresent\":" + stockPresent + ",\"warehouseFungibleStockPresent\":" + warehouseStock
                + ",\"productionCeilings\":" + productionCeilings;
    }
    private static String quote(String value) { return FrontierV3DiagnosticJson.quote(value); }
    private FrontierV3SettlementStockDiagnosticJson() { }
}
