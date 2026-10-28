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
            ImmutableMapChanges.forEachIdentityChangedKey(before.actorLocations(), after.actorLocations(), id -> {
                var resident = b.residents().get(id); if (resident == null) return;
                var old = before.actorLocations().get(id); var next = after.actorLocations().get(id);
                if (old != null && next != null && old.condition().status() != next.condition().status())
                    sink.changed(resident.settlementId(), CAPACITY, Set.of());
            });
        EconomicInputChangePort.difference(a.residents(), b.residents(), (old, next) -> {
            if (old != null) sink.changed(old.settlementId(), CAPACITY, Set.of());
            if (next != null) sink.changed(next.settlementId(), CAPACITY, Set.of());
        });
        ImmutableMapChanges.forEachIdentityChangedKey(a.nutrition(), b.nutrition(), id -> {
            var next = b.nutrition().get(id); if (next == null) return;
            var prior = a.nutrition().get(id);
            if (prior != null && prior.status() == next.status()) return;
            sink.resource(b.residents().get(id).settlementId(), NEED, SettlementFoodPolicy.BREAD);
        });
        ImmutableMapChanges.forEachIdentityChangedKey(a.schedules(), b.schedules(), id -> {
            var next = b.schedules().get(id);
            if (next != null && !next.equals(a.schedules().get(id))) sink.changed(id, CAPACITY, Set.of());
        });
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
