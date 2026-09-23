package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LogisticsSceneCauseCodecTest {
    private static byte[] encode(CargoProjectionRetirement.Disposition disposition) throws IOException {
        var bytes = new ByteArrayOutputStream();
        SceneCauseStateCodec.write(new DataOutputStream(bytes), cause(disposition));
        return bytes.toByteArray();
    }

    private static LogisticsSceneCause cause(CargoProjectionRetirement.Disposition disposition) {
        return new LogisticsSceneCause(new SubjectId("operation:wire"), new SubjectId("cargo:wire"),
                Optional.empty(), new BlockPosition(-3, 64, 1), disposition);
    }

    @Test void explicitDispositionRoundTripsWithoutInventoryOrPhaseInference() throws IOException {
        for (var disposition : CargoProjectionRetirement.Disposition.values()) {
            var bytes = encode(disposition);
            assertEquals(disposition.wireTag(), Byte.toUnsignedInt(bytes[bytes.length - 1]));
            assertEquals(cause(disposition), SceneCauseStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes))));
        }
    }

    @Test void missingOrUnknownDispositionNeverDefaultsToRemovingTheCarrier() throws IOException {
        var bytes = encode(CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY);
        assertThrows(EOFException.class, () -> SceneCauseStateCodec.read(new DataInputStream(
                new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length - 1)))));
        bytes[bytes.length - 1] = 99;
        assertThrows(IllegalArgumentException.class, () -> SceneCauseStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes))));
        assertThrows(NullPointerException.class, () -> cause(null));
    }
}
