package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Settlement creates ordinary stock orders. It does not move materials or operate machines. */
final class SettlementStockProductionPlanner implements SettlementOperationPlanner {
    public String id() { return "frontier:stock-production"; }
    public Assessment assess(FrontierWorldState state, Settlement home, long tick) {
        return AttendedStockProduction.ready(state, home.id(), tick).map(ready -> Assessment.offer(home.id(),
                new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_PRODUCE_STOCK, Optional.empty(), FixedScalar.SCALE),
                Priority.NORMAL)).orElseGet(Assessment::empty);
    }
    public List<ProposedEvent> expandActiveTasks(FrontierWorldState state, Settlement home, ScheduleId cause, long tick) {
        return StationTaskExpansion.expand(state, home, cause, tick, StrategicObjectiveKind.SETTLEMENT_PRODUCE_STOCK);
    }
}
