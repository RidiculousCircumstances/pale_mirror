package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.Comparator;
import java.util.List;

/** COLD visual range is the only bridge from an en-route caravan to hive strategic knowledge. */
final class HivePerceptionProcess {
    static final long SIGHT_RADIUS_SQUARED = 9_216L, REFRESH_INTERVAL = 800L;
    private HivePerceptionProcess() { }
    static Refresh refresh(FrontierWorldState state, long now) {
        HiveOperationKnowledge next = state.strategicPlans().hiveOperationKnowledge();
        List<ProposedEvent> events = new java.util.ArrayList<>();
        for (RouteOperation operation : state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).sorted(Comparator.comparing(RouteOperation::id)).toList()) {
            Bioform scout = scouts(state).stream().filter(value -> nearby(state.actorLocations().get(value.id()).position(), operation.currentPosition()))
                    .min(Comparator.comparing(Bioform::id)).orElse(null);
            if (scout == null) continue;
            HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation.id(), scout.id(), operation.currentPosition(), now);
            HiveOperationKnowledge.Sighting previous = next.entries().get(operation.id());
            if (previous != null && previous.scoutId().equals(scout.id()) && previous.position().equals(sighting.position())
                    && previous.observedAt() > now - REFRESH_INTERVAL) continue;
            next = next.observe(sighting); events.add(new ProposedEvent(state.bootstrap().hive().id(), new HiveOperationObserved(sighting)));
        }
        return new Refresh(next, List.copyOf(events));
    }
    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, HiveOperationObserved observed) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive sighting has a foreign owner");
        HiveOperationKnowledge.Sighting sighting = observed.sighting(); RouteOperation operation = state.operations().get(sighting.operationId());
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), sighting.scoutId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || scout.role() != BioformRole.SCOUT
                || state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || !operation.currentPosition().equals(sighting.position()) || !nearby(state.actorLocations().get(scout.id()).position(), sighting.position())) {
            throw new IllegalArgumentException("hive sighting lacks a nearby living scout and current caravan");
        }
        return state.withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(state.strategicPlans().hiveOperationKnowledge().observe(sighting)));
    }
    private static List<Bioform> scouts(FrontierWorldState state) { return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
            .filter(value -> value.role() == BioformRole.SCOUT).filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE)
            .filter(value -> { AmbientActorLease lease = state.ambientLeases().get(value.id()); return lease == null || lease.status() == AmbientLeaseStatus.CLOSED; }).toList(); }
    private static boolean nearby(BlockPosition left, BlockPosition right) { long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z <= SIGHT_RADIUS_SQUARED; }
    record Refresh(HiveOperationKnowledge knowledge, List<ProposedEvent> events) { }
}
