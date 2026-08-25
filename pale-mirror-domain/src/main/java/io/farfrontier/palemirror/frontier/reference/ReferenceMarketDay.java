package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.List;

/** Exact Python {@code MarketEconomy.run_day} phase coordinator. */
final class ReferenceMarketDay {
    private final ReferenceMarketEconomy market;
    private final ReferenceMarketFinance finance;
    private final ReferenceMarketContracts contracts;
    private final ReferenceMarketExpansion expansion;

    ReferenceMarketDay(ReferenceMarketEconomy market) {
        this.market = market;
        finance = new ReferenceMarketFinance(market);
        contracts = new ReferenceMarketContracts(market);
        expansion = new ReferenceMarketExpansion(market, finance);
    }

    List<ReferenceTradeRecord> run(ReferenceMarketWorld world) {
        market.captureExternalWarehouseChanges(world);
        for (ReferenceSettlement settlement : world.settlements().values()) market.economy().resetDailyFlows(settlement);
        finance.accrueCredit();
        market.assignLabour(world);
        market.haulSiteOutputs(world);
        market.produce(world);
        expansion.completeProjects(world);
        expansion.plan(world);
        market.syncCompatibility(world);
        List<ReferenceTradeRecord> records = new ArrayList<>(contracts.clear(world, finance));
        market.syncCompatibility(world);
        finance.householdConsumption(world);
        finance.settleCompanies(world);
        market.captureExternalWarehouseChanges(world);
        market.recordHistory(world.day());
        world.trade().appendHistory(records);
        return List.copyOf(records);
    }

    void licenseExistingSite(ReferenceMarketWorld world, ReferenceResourceSite site, int settlementId) {
        expansion.licenseExistingSite(world, site, settlementId);
    }
}
