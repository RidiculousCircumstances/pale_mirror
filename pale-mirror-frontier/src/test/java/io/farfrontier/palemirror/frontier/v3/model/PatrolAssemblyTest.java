package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolAssemblyTest {
    private static final SubjectId LEADER = new SubjectId("resident:assembly-leader");
    private static final SubjectId SCOUT = new SubjectId("resident:assembly-scout");

    @Test
    void ingressCannotEnterAnotherExactMembersOccupiedBody() {
        PatrolAssembly initial = new PatrolAssembly(Map.of(
                LEADER, new PatrolAssembly.Member(corridor("leader", -1, 0, 1), 0),
                SCOUT, new PatrolAssembly.Member(corridor("scout", -2, -1, 0), 0)));

        assertEquals(List.of(LEADER), initial.safeAdvances());
        PatrolAssembly leaderAdvanced = initial.advanceOne(LEADER);
        assertEquals(List.of(LEADER, SCOUT), leaderAdvanced.safeAdvances());
        assertTrue(leaderAdvanced.advanceOne(SCOUT).advanceCold().complete());
    }

    @Test
    void rejectsOverlappingDestinationsAndBlockedIngressAdvance() {
        TraversalTopology shared = corridor("shared", 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new PatrolAssembly(Map.of(
                LEADER, new PatrolAssembly.Member(shared, 0), SCOUT, new PatrolAssembly.Member(shared, 0))));

        TraversalTopology blocked = new TraversalTopology(shared.id(), shared.revision(), shared.provenance(), shared.nodes(),
                shared.edges().stream().map(edge -> new TraversalTopology.Edge(edge.id(), edge.from(), edge.to(), edge.kind(), edge.capabilities(),
                        edge.grade(), edge.clearance(), edge.revision(), TraversalAvailability.BLOCKED)).toList());
        PatrolAssembly blockedAssembly = new PatrolAssembly(Map.of(
                LEADER, new PatrolAssembly.Member(blocked, 0), SCOUT, new PatrolAssembly.Member(corridor("scout-blocked", 2, 3), 0)));
        assertThrows(IllegalArgumentException.class, () -> blockedAssembly.advanceOne(LEADER));
    }

    private static TraversalTopology corridor(String name, int... xs) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:patrol-assembly-test:" + name), 0L, new SubjectId("task:assembly"),
                TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN),
                java.util.Arrays.stream(xs).mapToObj(x -> new SurfaceAnchor(new BlockPosition(x, 64, 0))).toList());
    }
}
