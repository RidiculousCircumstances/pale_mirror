package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Changed economic generations request one coalesced review per owner. */
public final class SettlementEconomicReviewRequests {
    public static List<ScheduledAction> changed(FrontierWorldState previous, FrontierWorldState next, long now) {
        var before = previous.companies().goodsTrade().participants(); var after = next.companies().goodsTrade().participants();
        if (before.planning() == after.planning()) return List.of();
        var result = new ArrayList<ScheduledAction>();
        for (var entry : after.planning().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var old = before.planning().get(entry.getKey()); var current = entry.getValue();
            if (old != null && current.inputRevision() == old.inputRevision()
                    || current.status(now) == SettlementEconomicPlanningState.Status.CURRENT) continue;
            var reasons = current.changes();
            boolean urgent = current.accepted().isEmpty()
                    || reasons.contains(SettlementEconomicPlanningState.Change.NEED)
                    || reasons.contains(SettlementEconomicPlanningState.Change.CAPACITY)
                    || reasons.contains(SettlementEconomicPlanningState.Change.MARKET)
                    || reasons.contains(SettlementEconomicPlanningState.Change.CASH)
                    || reasons.contains(SettlementEconomicPlanningState.Change.DELIVERY);
            long due = urgent ? Math.addExact(now, 1) : Math.max(Math.addExact(now, 1), current.accepted().orElseThrow().reviewAtTick());
            result.add(GoodsParticipantWakeup.action(entry.getKey(), due));
        }
        return List.copyOf(result);
    }
    private SettlementEconomicReviewRequests() { }
}
