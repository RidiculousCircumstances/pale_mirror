package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationTravelTest {
    private static final SubjectId HAULER = new SubjectId("resident:1-1");

    @Test
    void retainsOneAdjacentCursorAndRejectsTeleportOrOversizedColdAdvance() {
        OperationTravel travel = new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0),
                        new BlockPosition(2, 64, 0)), 0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0));

        assertEquals(1, travel.nextHotCursor());
        OperationTravel advanced = travel.advance(1, Map.of(HAULER, new BlockPosition(1, 64, 1)), new BlockPosition(1, 64, 0));
        assertEquals(new BlockPosition(1, 64, 0), advanced.currentPosition());
        org.junit.jupiter.api.Assertions.assertTrue(advanced.isExactHotAdvanceFrom(travel));
        org.junit.jupiter.api.Assertions.assertFalse(travel.advance(1, Map.of(HAULER, new BlockPosition(7, 64, 1)), new BlockPosition(1, 64, 0))
                .isExactHotAdvanceFrom(travel), "a HOT observation may not move one participant independently of its route step");
        assertThrows(IllegalArgumentException.class, () -> travel.advance(0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(2, 64, 0)),
                0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0)));
    }

    @Test
    void rejectsCollidingActorOrCargoAnchorsBeforeAnyPhysicalSceneCanBePrepared() {
        SubjectId guard = new SubjectId("resident:1-2");
        List<BlockPosition> corridor = List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0));

        assertThrows(IllegalArgumentException.class, () -> new OperationTravel(corridor, 0,
                Map.of(HAULER, new BlockPosition(0, 64, 1), guard, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 2)));
        assertThrows(IllegalArgumentException.class, () -> new OperationTravel(corridor, 0,
                Map.of(HAULER, new BlockPosition(0, 64, 1), guard, new BlockPosition(0, 64, 2)), new BlockPosition(0, 64, 2)));
    }

    @Test void typedTravelTopologySurvivesTheDurableOperationPayload() {
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:operation:test"), 12L,
                new SubjectId("route:frontier-network"), TraversalKind.PEDESTRIAN,
                java.util.Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM),
                List.of(SurfaceAnchor.at(0, 63, 0), SurfaceAnchor.at(1, 64, 0)));
        OperationTravel travel = new OperationTravel(topology, 0, Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0));
        OperationTravelStarted payload = new OperationTravelStarted(new SubjectId("operation:test"), travel);

        var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)));
        assertEquals(1, travel.topology().edges().getFirst().grade());
    }

    @Test void blockedTopologyCannotBeSmuggledIntoAnActiveOperationCursor() {
        TraversalNodeId first = new TraversalNodeId("node:first"), second = new TraversalNodeId("node:second");
        TraversalTopology blocked = new TraversalTopology(new TraversalTopologyId("topology:blocked-operation"), 1L,
                new SubjectId("route:frontier-network"),
                Map.of(first, SurfaceAnchor.at(0, 64, 0), second, SurfaceAnchor.at(1, 64, 0)),
                List.of(new TraversalTopology.Edge(new TraversalEdgeId("edge:blocked"), first, second, TraversalKind.PEDESTRIAN,
                        java.util.Set.of(TraversalCapability.PEDESTRIAN), 0, 2, 1L, TraversalAvailability.BLOCKED)));

        assertThrows(IllegalArgumentException.class, () -> new OperationTravel(blocked, 0,
                Map.of(HAULER, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0)));
    }

    @Test void historicalWalCorridorRecoversAsOneDeterministicTypedTopology() throws Exception {
        List<BlockPosition> historicalCorridor = List.of(new BlockPosition(8, 63, 4), new BlockPosition(9, 64, 4));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            FrontierWorldPayloadCodecs.writeSubject(output, new SubjectId("operation:legacy"));
            output.writeShort(historicalCorridor.size());
            for (BlockPosition point : historicalCorridor) {
                output.writeInt(point.x()); output.writeInt(point.y()); output.writeInt(point.z());
            }
            output.writeShort(1); output.writeByte(1); FrontierWorldPayloadCodecs.writeSubject(output, HAULER);
            output.writeInt(9); output.writeInt(65); output.writeInt(4);
            output.writeInt(9); output.writeInt(64); output.writeInt(3);
        }

        var codecs = FrontierWorldPayloadCodecs.create();
        OperationTravelStarted decoded = (OperationTravelStarted) codecs.decode("frontier.operation_travel_started", bytes.toByteArray());
        assertEquals(new OperationTravel(historicalCorridor, 1, Map.of(HAULER, new BlockPosition(9, 65, 4)), new BlockPosition(9, 64, 3)), decoded.travel());
        assertEquals(1, decoded.travel().topology().edges().getFirst().grade());
    }
}
