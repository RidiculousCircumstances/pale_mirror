package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scout-local settlement discovery that supplies the future assault planner with no hidden target authority. */
final class HiveSettlementPerceptionProcess {
    static final int SIGHT_RADIUS_BLOCKS = 64;
    private static final int MAX_OBSERVATIONS_PER_REFRESH = 2;
    private HiveSettlementPerceptionProcess() { }

    static Refresh refresh(FrontierWorldState state, long now) {
        HiveSettlementKnowledge next = state.strategicPlans().hiveSettlementKnowledge();
        List<ProposedEvent> events = new ArrayList<>();
        for (Settlement settlement : state.bootstrap().settlements().stream().sorted(Comparator.comparing(Settlement::id)).toList()) {
            Bioform scout = scouts(state).stream().filter(value -> nearby(state.actorLocations().get(value.id()).position(), settlement.anchor()))
                    .min(Comparator.comparing(Bioform::id)).orElse(null);
            if (scout == null) continue;
            HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), now);
            HiveSettlementKnowledge.Sighting prior = next.entries().get(settlement.id());
            if (prior != null && prior.scoutId().equals(scout.id()) && prior.settlementAnchor().equals(settlement.anchor())
                    && prior.observedAt() > now - HivePerceptionProcess.REFRESH_INTERVAL) continue;
            next = next.observe(sighting);
            events.add(new ProposedEvent(state.bootstrap().hive().id(), new HiveSettlementObserved(sighting)));
            if (events.size() >= MAX_OBSERVATIONS_PER_REFRESH) break;
        }
        return new Refresh(next, List.copyOf(events));
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, HiveSettlementObserved observed) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive settlement sighting has a foreign owner");
        HiveSettlementKnowledge.Sighting sighting = observed.sighting();
        validateObservation(state.bootstrap(), state.hiveColony(), state.actorLocations(), sighting);
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), sighting.scoutId());
        if (!state.actorLocations().get(scout.id()).position().equals(sighting.settlementAnchor())
                && !nearby(state.actorLocations().get(scout.id()).position(), sighting.settlementAnchor())) {
            throw new IllegalArgumentException("hive settlement sighting scout is no longer local");
        }
        return state.withStrategicPlans(state.strategicPlans().withHiveSettlementKnowledge(state.strategicPlans().hiveSettlementKnowledge().observe(sighting)));
    }

    static void validateObservation(FrontierBootstrap bootstrap, HiveColony colony, java.util.Map<SubjectId, ActorLocation> actors,
                                    HiveSettlementKnowledge.Sighting sighting) {
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(sighting.settlementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive settlement sighting names an unknown settlement"));
        Bioform scout = FrontierWorldStateSupport.bioform(bootstrap, colony, sighting.scoutId());
        ActorLocation location = actors.get(scout.id());
        if (scout.role() != BioformRole.SCOUT || location.condition().status() != ActorLifeStatus.ALIVE
                || !settlement.anchor().equals(sighting.settlementAnchor()) || !nearby(location.position(), settlement.anchor())) {
            throw new IllegalArgumentException("hive settlement sighting lacks one nearby living scout");
        }
    }

    private static List<Bioform> scouts(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.role() == BioformRole.SCOUT)
                .filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(value -> { AmbientActorLease lease = state.ambientLeases().get(value.id()); return lease == null || lease.status() == AmbientLeaseStatus.CLOSED; })
                .toList();
    }

    private static boolean nearby(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z();
        return x * x + z * z <= (long) SIGHT_RADIUS_BLOCKS * SIGHT_RADIUS_BLOCKS;
    }

    record Refresh(HiveSettlementKnowledge knowledge, List<ProposedEvent> events) { }
}
