package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ProductionInputHold;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ProductionStarted;
import io.farfrontier.palemirror.frontier.v3.model.TerminalProductionReceipt;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionMultiLotCodecTest {
    private static final SubjectId FIRST = new SubjectId("lot:field-one-wheat");
    private static final SubjectId SECOND = new SubjectId("lot:field-two-wheat");
    private static final SubjectId ACCOUNT = new SubjectId("custody:depot");
    private static final SubjectId CLAIM = new SubjectId("claim:bread-input");
    private static final Map<SubjectId, Integer> PORTIONS = Map.of(FIRST, 32, SECOND, 32);

    @Test
    void coldAndBoundProductionRetainTheSameTwoLotsAcrossSnapshotAndStartEvent() throws Exception {
        for (var portions : java.util.List.of(PORTIONS, Map.of(FIRST, 30, SECOND, 30), Map.of(FIRST, 60))) {
            int quantity = portions.values().stream().mapToInt(Integer::intValue).sum();
            for (ProductionInputHold hold : new ProductionInputHold[]{
                    new ProductionInputHold.FungibleCold(FIRST, ACCOUNT, CLAIM, portions),
                    new ProductionInputHold.FungibleBound(FIRST, ACCOUNT, CLAIM, 9L, portions)}) {
                ProductionJob job = new ProductionJob(new SubjectId("job:two-field-bread"), new SubjectId("task:two-field-bread"), new SubjectId("settlement:1"),
                        new SubjectId("structure:1-workshop"), new SubjectId("resident:1-15"), FIRST, hold,
                        new SubjectId("lot:two-field-bread"), "minecraft:bread", quantity);
                var bytes = new ByteArrayOutputStream();
                ProductionJobStateCodec.write(new DataOutputStream(bytes), Map.of(job.id(), job));
                assertEquals(Map.of(job.id(), job), ProductionJobStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));

                var started = new ProductionStarted(job, FIRST);
                var codecs = FrontierWorldPayloadCodecs.create();
                assertEquals(started, codecs.decode(started.type(), codecs.encode(started)));

                TerminalProductionReceipt terminal = TerminalProductionReceipt.of(job);
                assertEquals(portions, terminal.inputLots());
                bytes.reset();
                FrontierMarketRelationCodec.writeTerminalProductionReceipt(new DataOutputStream(bytes), terminal);
                assertEquals(terminal, FrontierMarketRelationCodec.readTerminalProductionReceipt(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new ProductionInputHold.FungibleCold(
                FIRST, ACCOUNT, CLAIM, Map.of(FIRST, 32, SECOND, 33)));
        var partial = new ProductionInputHold.FungibleCold(FIRST, ACCOUNT, CLAIM, Map.of(FIRST, 30, SECOND, 30));
        assertThrows(IllegalArgumentException.class, () -> new ProductionJob(new SubjectId("job:mismatched-batch"),
                new SubjectId("task:mismatched-batch"), new SubjectId("settlement:1"), new SubjectId("structure:1-workshop"),
                new SubjectId("resident:1-15"), FIRST, partial, new SubjectId("lot:mismatched-bread"), "minecraft:bread", 64));
    }
}
