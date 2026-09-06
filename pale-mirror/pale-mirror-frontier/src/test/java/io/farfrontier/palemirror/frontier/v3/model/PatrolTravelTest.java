package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolTravelTest {
    private static final SubjectId PATROL = new SubjectId("task:patrol-travel");
    private static final SubjectId LEADER = new SubjectId("resident:leader");
    private static final SubjectId SCOUT = new SubjectId("resident:scout");

    @Test
    void columnAdvancesOnlyIntoAnExactVacatedRetainedBody() {
        TraversalTopology leaderRoute = corridor("leader", List.of(0, 1, 2, 3));
        TraversalTopology scoutRoute = corridor("scout", List.of(-1, 0, 1, 2));
        PatrolTravel travel = new PatrolTravel(LEADER, leaderRoute, Map.of(
                LEADER, new PatrolTravel.Member(leaderRoute, 0),
                SCOUT, new PatrolTravel.Member(scoutRoute, 0)));

        assertEquals(List.of(LEADER), travel.safeAdvances(), "the scout cannot enter the leader's occupied cell");
        PatrolTravel leaderAdvanced = travel.advanceOne(LEADER);
        assertEquals(List.of(SCOUT), leaderAdvanced.safeAdvances(), "the exact released cell becomes the scout's next retained body");
        PatrolTravel columnAdvanced = leaderAdvanced.advanceOne(SCOUT);

        assertEquals(1, columnAdvanced.routeCursor());
        assertEquals(new BodyPosition(1, 65, 0), columnAdvanced.bodies().get(LEADER));
        assertEquals(new BodyPosition(0, 65, 0), columnAdvanced.bodies().get(SCOUT));
    }

    @Test
    void rejectsOverlappingOrForeignLeaderFormationAndNeverCrossesUnavailableEdges() {
        TraversalTopology route = corridor("route", List.of(0, 1));
        TraversalTopology blocked = new TraversalTopology(route.id(), route.revision(), route.provenance(), route.nodes(),
                route.edges().stream().map(edge -> new TraversalTopology.Edge(edge.id(), edge.from(), edge.to(), edge.kind(), edge.capabilities(),
                        edge.grade(), edge.clearance(), edge.revision(), TraversalAvailability.BLOCKED)).toList());
        assertThrows(IllegalArgumentException.class, () -> new PatrolTravel(LEADER, route, Map.of(
                LEADER, new PatrolTravel.Member(route, 0), SCOUT, new PatrolTravel.Member(route, 0))));
        assertThrows(IllegalArgumentException.class, () -> new PatrolTravel(LEADER, route, Map.of(
                LEADER, new PatrolTravel.Member(corridor("other", List.of(0, 1)), 0), SCOUT, new PatrolTravel.Member(route, 1))));
        PatrolTravel blockedTravel = new PatrolTravel(LEADER, blocked, Map.of(
                LEADER, new PatrolTravel.Member(blocked, 0), SCOUT, new PatrolTravel.Member(route, 1)));
        assertEquals(List.of(), blockedTravel.safeAdvances());
        assertThrows(IllegalArgumentException.class, () -> blockedTravel.advanceOne(LEADER));
    }

    @Test
    void coldProgressIsBoundedAndNeverCrossesAnOccupiedColumn() {
        TraversalTopology leaderRoute = corridor("leader-cold", List.of(0, 1, 2));
        TraversalTopology scoutRoute = corridor("scout-cold", List.of(-1, 0, 1));
        PatrolTravel initial = new PatrolTravel(LEADER, leaderRoute, Map.of(
                LEADER, new PatrolTravel.Member(leaderRoute, 0), SCOUT, new PatrolTravel.Member(scoutRoute, 0)));

        PatrolTravel advanced = initial.advanceCold();

        assertTrue(advanced.complete());
        assertEquals(2, advanced.routeCursor());
        assertFalse(advanced.bodies().get(LEADER).equals(advanced.bodies().get(SCOUT)));
    }

    @Test
    void retainsTheSameColumnAcrossSurveyedGradeChanges() {
        TraversalTopology leaderRoute = topology("leader-grade", List.of(
                new BlockPosition(0, 64, 0), new BlockPosition(1, 65, 0), new BlockPosition(2, 66, 0)));
        TraversalTopology scoutRoute = topology("scout-grade", List.of(
                new BlockPosition(-1, 64, 0), new BlockPosition(0, 64, 0), new BlockPosition(1, 65, 0)));
        PatrolTravel advanced = new PatrolTravel(LEADER, leaderRoute, Map.of(
                LEADER, new PatrolTravel.Member(leaderRoute, 0), SCOUT, new PatrolTravel.Member(scoutRoute, 0))).advanceCold();

        assertTrue(advanced.complete());
        assertEquals(new BodyPosition(2, 67, 0), advanced.bodies().get(LEADER));
        assertEquals(new BodyPosition(1, 66, 0), advanced.bodies().get(SCOUT));
    }

    private static TraversalTopology corridor(String suffix, List<Integer> xs) {
        return topology(suffix, xs.stream().map(x -> new BlockPosition(x, 64, 0)).toList());
    }

    private static TraversalTopology topology(String suffix, List<BlockPosition> surfaces) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:patrol-test:" + suffix), 0L, PATROL,
                TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), surfaces.stream().map(SurfaceAnchor::new).toList());
    }
}
