package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Optional upper reserves are not an export embargo. Protect the replenishment
 * interval and allocated inputs independently of the treasury's solvency. */
final class EconomicStockRelease {
    static Map<String, Long> assess(EconomicSnapshot facts, SupplyPlanner.Plan supply,
                                   Map<String, SupplyReservePlanner.Target> reserves) {
        var result = new TreeMap<String, Long>();
        facts.stocks().forEach((item, stock) -> {
            var reserve = reserves.get(item);
            long horizon = Math.max(facts.dayTicks(), reserve.timing().leadTicks());
            // Dated coverage checks the prefix before each arrival. An undated work
            // promise or market quote can never authorize release of current stock.
            long datedNeed = DatedStockCoverage.assess(0, facts.needs().get(item), 0,
                    stock.arrivals(), horizon).maximumShortfall();
            long keep = Math.max(reserve.protectedQuantity(), Math.max(datedNeed, supply.require(item).protectedInput()));
            result.put(item, Math.max(0, stock.available() - keep));
        });
        return Map.copyOf(result);
    }
    private EconomicStockRelease() { }
}
