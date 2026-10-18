package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Coverage must hold at every delivery boundary, not merely after all delayed deliveries are summed. */
public final class DatedStockCoverage {
    public record Assessment(long maximumShortfall, long firstShortfallAfterTicks) {
        public boolean covered() { return maximumShortfall == 0; }
    }
    public static Assessment assess(long available, SupplyReservePlanner.Need need, long reserve,
                                    List<SupplyPlanner.Arrival> arrivals, long horizon) {
        if (available < 0 || reserve < 0 || horizon < 1) throw new IllegalArgumentException("invalid stock coverage boundary");
        var byTime = new TreeMap<Long, Long>();
        for (var arrival : arrivals) if (arrival.afterTicks() <= horizon)
            byTime.merge(arrival.afterTicks(), arrival.quantity(), Math::addExact);
        long stock = Math.addExact(available, byTime.getOrDefault(0L, 0L));
        long shortfall = Math.max(0, Math.addExact(need.immediate(), reserve) - stock);
        long first = shortfall == 0 ? Long.MAX_VALUE : 0;
        byTime.remove(0L);
        for (var arrival : byTime.entrySet()) {
            long gap = Math.max(0, Math.addExact(Math.addExact(need.immediate(), reserve), need.consumption().over(arrival.getKey())) - stock);
            if (gap > 0) first = Math.min(first, arrival.getKey());
            shortfall = Math.max(shortfall, gap); stock = Math.addExact(stock, arrival.getValue());
        }
        long gap = Math.max(0, Math.addExact(Math.addExact(need.immediate(), reserve), need.consumption().over(horizon)) - stock);
        if (gap > 0) first = Math.min(first, horizon);
        return new Assessment(Math.max(shortfall, gap), first);
    }
    private DatedStockCoverage() { }
}
