package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgressed;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestColdTraversalAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestReturned;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceSiteWorkReceiptCodecTest {
    @Test void labourWalRetainsExactPredecessorIntervalAndRejectsTruncation() {
        var before = io.farfrontier.palemirror.frontier.v3.model.WorkProgress.pending(200, 100).resume(100, 1_250, 260);
        var value = new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestWorkChanged(
                new SubjectId("site:test"), new SubjectId("job:site-harvest-test"), new SubjectId("resident:test"),
                1, 1, new ResourceFieldLayout.CellId(97), io.farfrontier.palemirror.frontier.v3.model.WorkOperation.HARVEST,
                120, java.util.Optional.of(before), before.pause(120),
                new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-test"), 260, java.util.Optional.empty());
        var codec = ResourceSitePayloadCodecs.harvestWorkChanged();
        byte[] bytes = codec.encode(value);
        assertEquals(value, codec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
    }
    @Test void coldReturnEdgeRetainsItsExactDueScheduleAcrossWalRoundTrip() {
        var codec = ResourceSitePayloadCodecs.harvestColdTraversalAdvanced();
        var edge = new ResourceSiteHarvestColdTraversalAdvanced(new SubjectId("job:site-harvest-test"),
                new SubjectId("resident:test"), 71,
                new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-test"), 22_301L);
        byte[] encoded = codec.encode(edge);
        assertEquals(edge, codec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length - Long.BYTES)));
    }

    @Test void completedHotReturnRetainsItsExactDueScheduleAcrossWalRoundTrip() {
        var codec = ResourceSitePayloadCodecs.harvestReturned();
        var returned = new ResourceSiteHarvestReturned(new SubjectId("job:site-harvest-test"),
                new SubjectId("resident:test"),
                new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-test"), 22_301L);
        byte[] encoded = codec.encode(returned);
        assertEquals(returned, codec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
    }

    @Test void hotCropReceiptRetainsTheExactActorHandAndRejectsAnIncompletePhysicalClaim() {
        var codec = ResourceSitePayloadCodecs.harvestProgressed();
        var receipt = new ResourceSiteHarvestProgressed(new SubjectId("site:test"), 3,
                new SubjectId("job:site-harvest-test"), 1,
                2, new ResourceFieldLayout.CellId(97), 0L, ResourceFieldCycle.WorkOutcome.HARVESTED,
                new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-test"), 22_301L,
                java.util.Optional.of(new ResourceSiteHarvestProgressed.HandObservation(
                        new PhysicalStackAddress.ActorHand(new SubjectId("resident:test"),
                                java.util.UUID.fromString("00000000-0000-0000-0000-000000000097")), 9L, 1)));
        byte[] encoded = codec.encode(receipt);
        assertEquals(receipt, codec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
        byte[] unknownHand = encoded.clone();
        int tagOffset = codec.encode(new ResourceSiteHarvestProgressed(receipt.siteId(), receipt.epoch(), receipt.jobId(),
                receipt.completedCropSlots(), receipt.layoutRevision(), receipt.cellId(), 0L, receipt.outcome(),
                receipt.coldScheduleId(), receipt.coldDueAt())).length - 1;
        unknownHand[tagOffset] = 7;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknownHand));
    }

    @Test void retainsExactCellAndOutcomeAndRejectsUnknownWireMeaning() {
        var codec = ResourceSitePayloadCodecs.harvestProgressed();
        var receipt = new ResourceSiteHarvestProgressed(new SubjectId("site:test"), 3,
                new SubjectId("job:site-harvest-test"), 1,
                2, new ResourceFieldLayout.CellId(97), 0L, ResourceFieldCycle.WorkOutcome.TILLED_AND_PLANTED,
                new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-test"), 22_301L);
        byte[] encoded = codec.encode(receipt);
        assertEquals(receipt, codec.decode(encoded));

        byte[] unknownOutcome = encoded.clone();
        int jobLengthOffset = 1 + Byte.toUnsignedInt(unknownOutcome[0]) + Long.BYTES;
        int outcomeOffset = jobLengthOffset + 1 + Byte.toUnsignedInt(unknownOutcome[jobLengthOffset])
                + Integer.BYTES + Long.BYTES * 3;
        unknownOutcome[outcomeOffset] = 99;
        org.junit.jupiter.api.Assertions.assertTrue(assertThrows(IllegalArgumentException.class,
                () -> codec.decode(unknownOutcome)).getMessage().contains("work outcome"),
                "the negative must reach the outcome tag, not fail on a corrupted cell count");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
    }
}
