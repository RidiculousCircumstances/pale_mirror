package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compiles a bounded exact approach to one declared public port slot. */
public final class OperationAssemblyCorridor {
    private static final int MAX_CANDIDATES_PER_ACTOR = 8;
    private static final int MAX_JOINT_COMBINATIONS = 256;

    private OperationAssemblyCorridor() { }

    public static TraversalTopology compile(FrontierWorldState state, SubjectId operationId, SubjectId settlementId, SubjectId actorId, SurfaceAnchor destination) {
        Objects.requireNonNull(state, "assembly state"); Objects.requireNonNull(operationId, "assembly operation"); Objects.requireNonNull(actorId, "assembly actor"); Objects.requireNonNull(destination, "assembly destination");
        return topology(operationId, actorId, route(state, operationId, settlementId, actorId, destination, Set.of()));
    }

    /**
     * Compiles all exact convoy approaches together. Individual shortest paths are not enough:
     * two otherwise valid people can claim each other's next cell at a public throat forever.
     * This bounded selection proves that the same deterministic COLD scheduler can take every
     * retained member to its declared slot before the operation is created.
     */
    public static OperationAssembly compileJoint(FrontierWorldState state, SubjectId operationId, SubjectId settlementId, SubjectId cargoCarrierId,
                                                Map<SubjectId, SurfaceAnchor> destinations) {
        Objects.requireNonNull(state, "assembly state"); Objects.requireNonNull(operationId, "assembly operation");
        Objects.requireNonNull(cargoCarrierId, "assembly cargo carrier"); Objects.requireNonNull(destinations, "assembly destinations");
        if (destinations.isEmpty() || destinations.size() > 8 || !destinations.containsKey(cargoCarrierId)
                || destinations.values().stream().anyMatch(Objects::isNull)
                || destinations.values().stream().distinct().count() != destinations.size()) {
            throw new IllegalArgumentException("joint operation assembly needs 1..8 distinct declared member slots");
        }
        Map<SubjectId, List<TraversalTopology>> candidates = new HashMap<>();
        destinations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                candidates.put(entry.getKey(), candidates(state, operationId, settlementId, entry.getKey(), entry.getValue())));
        SelectionBudget budget = new SelectionBudget();
        List<SubjectId> members = destinations.keySet().stream().sorted().toList();
        OperationAssembly result = selectJoint(members, candidates, cargoCarrierId, 0, new HashMap<>(), budget);
        if (result == null) throw new IllegalArgumentException("operation assembly has no bounded jointly completable approach");
        return result;
    }

    private static OperationAssembly selectJoint(List<SubjectId> members, Map<SubjectId, List<TraversalTopology>> candidates,
                                                 SubjectId cargoCarrierId, int index,
                                                 Map<SubjectId, OperationAssembly.Member> selected, SelectionBudget budget) {
        if (budget.exhausted()) return null;
        if (index == members.size()) {
            budget.record();
            OperationAssembly assembly = new OperationAssembly(selected, cargoCarrierId);
            return completesUnderRetainedSchedule(assembly) ? assembly : null;
        }
        SubjectId actor = members.get(index);
        for (TraversalTopology corridor : candidates.get(actor)) {
            selected.put(actor, new OperationAssembly.Member(corridor, 0));
            OperationAssembly result = selectJoint(members, candidates, cargoCarrierId, index + 1, selected, budget);
            if (result != null) return result;
            if (budget.exhausted()) break;
        }
        selected.remove(actor);
        return null;
    }

    /** Mirrors {@link OperationAssembly#safeAdvances()} and its lexicographic COLD owner. */
    private static boolean completesUnderRetainedSchedule(OperationAssembly initial) {
        OperationAssembly current = initial;
        int maximumMoves = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < maximumMoves; move++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advance(safe.getFirst());
        }
        return current.complete();
    }

    private static List<TraversalTopology> candidates(FrontierWorldState state, SubjectId operationId, SubjectId settlementId, SubjectId actorId,
                                                       SurfaceAnchor destination) {
        List<SurfaceAnchor> shortest = route(state, operationId, settlementId, actorId, destination, Set.of());
        LinkedHashSet<List<SurfaceAnchor>> paths = new LinkedHashSet<>();
        paths.add(shortest);
        // Deterministically remove a spread of non-endpoint cells from the shortest route.
        // This asks the same bounded A* for local alternatives without inventing a separate
        // movement authority or a coordinate-specific escape hatch.
        int interior = shortest.size() - 2;
        for (int ordinal = 1; ordinal <= MAX_CANDIDATES_PER_ACTOR - 1 && interior > 0; ordinal++) {
            int index = 1 + Math.floorDiv(Math.multiplyExact(ordinal, interior), MAX_CANDIDATES_PER_ACTOR - 1);
            if (index >= shortest.size() - 1) index = shortest.size() - 2;
            try {
                paths.add(route(state, operationId, settlementId, actorId, destination, Set.of(shortest.get(index))));
            } catch (IllegalArgumentException unavailable) {
                // One particular detour need not exist; another retained candidate may.
            }
        }
        return paths.stream().limit(MAX_CANDIDATES_PER_ACTOR).map(path -> topology(operationId, actorId, path)).toList();
    }

    private static TraversalTopology topology(SubjectId operationId, SubjectId actorId, List<SurfaceAnchor> corridor) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:operation-assembly:" + operationId.value() + ":" + actorId.value()), 0L,
                operationId, TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, SubjectId settlementId) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        var hall = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HALL)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("assembly has no declared Hall passage"));
        return KnownPedestrianRouteKnowledge.forSettlement(state, settlementId, List.of(
                new KnownPedestrianRouteKnowledge.Passage(hall, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }

    private static List<SurfaceAnchor> route(FrontierWorldState state, SubjectId operationId, SubjectId settlementId,
                                             SubjectId actorId, SurfaceAnchor destination, Set<SurfaceAnchor> prohibitedFloors) {
        SurfaceAnchor start = Objects.requireNonNull(state.actorLocations().get(actorId), "assembly actor location").supportingSurface();
        var resident = state.humanPopulation().residents().get(actorId);
        if (resident == null || !resident.settlementId().equals(Objects.requireNonNull(settlementId)))
            throw new IllegalArgumentException("assembly member differs from its explicitly declared settlement");
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(operationId, actorId, 0L, 1L,
                List.of(destination), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        return knowledge(state, settlementId).pathAvoiding(start, order, prohibitedFloors);
    }
    private static final class SelectionBudget {
        private int combinations;
        boolean exhausted() { return combinations >= MAX_JOINT_COMBINATIONS; }
        void record() { combinations++; }
    }
}
