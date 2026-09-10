package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeferredAftermathStateCodecTest {
    @Test void preservesRunningBoundaryAndExactChunkCursorAcrossSnapshotBytes() throws Exception {
        SubjectId hive = new SubjectId("hive:codec");
        DeferredAftermath aftermath = new DeferredAftermath(new SubjectId("aftermath:codec"), hive, new SubjectId("bioform:codec"), 10L,
                OptionalLong.empty(), "codec-test", DeferredAftermathKnowledge.KNOWN_CLEAR, 4L, List.of(
                new DeferredAftermathCell(new BlockPosition(0, 64, 0), hive, GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING)), 0);
        DeferredAftermathState original = DeferredAftermathState.empty().prepare(aftermath)
                .resolve(aftermath.id(), aftermath.expectedEpoch(), 11L, 0, DeferredAftermathCellStatus.RUNNING);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) { DeferredAftermathStateCodec.write(output, original); }
        DeferredAftermathState restored;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { restored = DeferredAftermathStateCodec.read(input); }
        assertEquals(original, restored);
    }
}
