package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change.*;

/** Staffing and need thresholds, never the pedestrian's route or current feet position. */
final class PopulationEconomicInputChanges implements EconomicInputChangePort {
    @Override public void collect(FrontierWorldState before, FrontierWorldState after, Sink sink) {
        var a = before.humanPopulation(); var b = after.humanPopulation();
        // Physical death owns the fenced life boundary. Ordinary movement does
        // not change it and therefore never scans the resident population here.
        if (before.fencedRecovery().tombstones() != after.fencedRecovery().tombstones())
            for (var resident : b.residents().values()) {
                var old = before.actorLocations().get(resident.id()); var next = after.actorLocations().get(resident.id());
                if (old != null && next != null && old.condition().status() != next.condition().status())
                    sink.changed(resident.settlementId(), CAPACITY, Set.of());
            }
        EconomicInputChangePort.difference(a.residents(), b.residents(), (old, next) -> {
            if (old != null) sink.changed(old.settlementId(), CAPACITY, Set.of());
            if (next != null) sink.changed(next.settlementId(), CAPACITY, Set.of());
        });
        if (a.nutrition() != b.nutrition()) for (var entry : b.nutrition().entrySet()) {
            var prior = a.nutrition().get(entry.getKey()); var next = entry.getValue();
            if (prior != null && prior.status() == next.status()) continue;
            var resident = b.residents().get(entry.getKey());
            sink.resource(resident.settlementId(), NEED, SettlementFoodPolicy.BREAD);
        }
        if (a.schedules() != b.schedules()) for (var entry : b.schedules().entrySet())
            if (!entry.getValue().equals(a.schedules().get(entry.getKey()))) sink.changed(entry.getKey(), CAPACITY, Set.of());
        EconomicInputChangePort.difference(before.transportFleet().assets(), after.transportFleet().assets(), (old, next) -> {
            if (old != null) sink.changed(old.homeSettlementId(), COMMITMENT, Set.of());
            if (next != null) sink.changed(next.homeSettlementId(), COMMITMENT, Set.of());
        });
        var previous = before.strategicPlans().decisionAuthorities().authorities();
        var current = after.strategicPlans().decisionAuthorities().authorities();
        EconomicInputChangePort.difference(previous, current, (old, next) -> {
            if (old != null && next != null && old.workPermissions().equals(next.workPermissions())) return;
            if (next != null) sink.changed(next.ownerId(), CAPACITY, Set.of());
        });
    }
}
