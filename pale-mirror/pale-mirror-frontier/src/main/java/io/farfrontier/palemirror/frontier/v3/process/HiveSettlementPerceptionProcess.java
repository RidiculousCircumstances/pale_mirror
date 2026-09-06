package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scout-local settlement discovery that supplies the future assault planner with no hidden target authority. */
public final class HiveSettlementPerceptionProcess {
    private static final int MAX_OBSERVATIONS_PER_REFRESH = 2;
    private HiveSettlementPerceptionProcess() { }

    public static Refresh refresh(FrontierWorldState state, long now) {
        HiveSettlementKnowledge next = state.strategicPlans().hiveSettlementKnowledge();
        List<ProposedEvent> events = new ArrayList<>();
        for (Settlement settlement : state.bootstrap().settlements().stream().sorted(Comparator.comparing(Settlement::id)).toList()) {
            Bioform scout = scouts(state).stream().filter(value -> nearby(state.bootstrap().ruleset(), state.actorLocations().get(value.id()).supportingSurface().support(), settlement.anchor()))
                    .min(Comparator.comparing(Bioform::id)).orElse(null);
            if (scout == null) continue;
            HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), now);
            HiveSettlementKnowledge.Sighting prior = next.entries().get(settlement.id());
            if (prior != null && prior.scoutId().equals(scout.id()) && prior.settlementAnchor().equals(settlement.anchor())
                    && prior.observedAt() > now - state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval()) continue;
            next = next.observe(sighting);
            events.add(new ProposedEvent(state.bootstrap().hive().id(), new HiveSettlementObserved(sighting)));
            events.add(new ProposedEvent(state.bootstrap().hive().id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                    StrategicObjectiveProcess.assaultOpportunity(state.bootstrap().hive().id(), sighting, now + 1L))));
            if (events.size() >= MAX_OBSERVATIONS_PER_REFRESH) break;
        }
        return new Refresh(next, List.copyOf(events));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, HiveSettlementObserved observed) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive settlement sighting has a foreign owner");
        HiveSettlementKnowledge.Sighting sighting = observed.sighting();
        validateObservation(state.bootstrap(), state.bootstrap().ruleset(), state.hiveColony(), state.actorLocations(), sighting);
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), sighting.scoutId());
        if (!state.actorLocations().get(scout.id()).supportingSurface().support().equals(sighting.settlementAnchor())
                && !nearby(state.bootstrap().ruleset(), state.actorLocations().get(scout.id()).supportingSurface().support(), sighting.settlementAnchor())) {
            throw new IllegalArgumentException("hive settlement sighting scout is no longer local");
        }
        return state.withStrategicPlans(state.strategicPlans().withHiveSettlementKnowledge(state.strategicPlans().hiveSettlementKnowledge().observe(sighting)));
    }

    static void validateObservation(FrontierBootstrap bootstrap, FrontierRuleset ruleset, HiveColony colony, java.util.Map<SubjectId, ActorLocation> actors,
                                    HiveSettlementKnowledge.Sighting sighting) {
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(sighting.settlementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive settlement sighting names an unknown settlement"));
        Bioform scout = FrontierWorldStateSupport.bioform(bootstrap, colony, sighting.scoutId());
        ActorLocation location = actors.get(scout.id());
        if (!scout.isScout() || location.condition().status() != ActorLifeStatus.ALIVE
                || !settlement.anchor().equals(sighting.settlementAnchor()) || !nearby(ruleset, location.supportingSurface().support(), settlement.anchor())) {
            throw new IllegalArgumentException("hive settlement sighting lacks one nearby living scout");
        }
    }

    private static List<Bioform> scouts(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(Bioform::isScout)
                .filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(value -> { AmbientActorLease lease = state.ambientLeases().get(value.id()); return lease == null || lease.status() == AmbientLeaseStatus.CLOSED; })
                .toList();
    }

    private static boolean nearby(FrontierRuleset ruleset, BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z();
        int radius = ruleset.spatial().hiveSettlementSightRadius();
        return x * x + z * z <= (long) radius * radius;
    }

    public record Refresh(HiveSettlementKnowledge knowledge, List<ProposedEvent> events) { }
}
