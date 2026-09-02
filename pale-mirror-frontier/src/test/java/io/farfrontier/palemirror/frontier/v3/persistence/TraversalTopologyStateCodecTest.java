package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.TraversalKind;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopologyId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraversalTopologyStateCodecTest {
    @Test
    void roundTripsTheSingleStationarySurfaceThatAnAssemblyCanPersistAcrossRestart() throws IOException {
        TraversalTopology stationary = TraversalTopology.corridor(new TraversalTopologyId("topology:stationary"), 4L,
                new SubjectId("operation:stationary"), TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN), List.of(SurfaceAnchor.at(12, 65, -8)));

        byte[] encoded = encode(stationary);

        assertEquals(stationary, TraversalTopologyStateCodec.read(new DataInputStream(new ByteArrayInputStream(encoded))));
    }

    @Test
    void rejectsAnEmptyTopologyBeforeAnyStateCanBeHydrated() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            FrontierWorldStateCodec.writeString(output, "topology:empty");
            output.writeLong(0L);
            FrontierWorldStateCodec.writeString(output, "operation:empty");
            FrontierWorldStateCodec.writeCount(output, 0);
        }

        assertThrows(IllegalArgumentException.class, () -> TraversalTopologyStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    private static byte[] encode(TraversalTopology topology) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            TraversalTopologyStateCodec.write(output, topology);
        }
        return bytes.toByteArray();
    }
}
