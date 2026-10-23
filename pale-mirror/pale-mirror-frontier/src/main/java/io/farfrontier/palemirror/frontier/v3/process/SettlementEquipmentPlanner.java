package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
/** Settlement equipment policy; no direct inventory, recipe or worker mutation. */
final class SettlementEquipmentPlanner implements SettlementOperationPlanner {
    public String id() { return "frontier:equipment"; }
    public Assessment assess(FrontierWorldState state, Settlement settlement, long tick) {
        return EquipmentProductionPlanning.ready(state, settlement.id(), tick).map(ready -> Assessment.offer(settlement.id(),
                new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT, Optional.empty(),
                        ready.request().urgent() ? Long.MAX_VALUE - 2L : FixedScalar.SCALE),
                ready.request().urgent() ? Priority.CRITICAL : Priority.NORMAL)).orElseGet(Assessment::empty);
    }
    public List<ProposedEvent> expandActiveTasks(FrontierWorldState state, Settlement settlement, ScheduleId cause, long tick) {
        return StationTaskExpansion.expand(state, settlement, cause, tick, StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT);
    }
}
