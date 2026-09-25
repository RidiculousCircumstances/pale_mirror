package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationObservation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceSitePreparationObservationCodecTest {
    @Test void exactLargeFieldCountsSurviveWalAndSnapshotWithoutByteTruncation() throws Exception {
        PhysicalObservationId id = new PhysicalObservationId("observation:field-preparation-65");
        PhysicalIntentId intent = new PhysicalIntentId("intent:site-preparation-65");
        SubjectId site = new SubjectId("site:field-65");
        ResourceSitePreparationObservation receipt = new ResourceSitePreparationObservation(id, intent, site, 65, 65);
        var bytes = new ByteArrayOutputStream();
        PhysicalEffectObservationPayloadCodec.write(new DataOutputStream(bytes), receipt);
        assertEquals(receipt, PhysicalEffectObservationPayloadCodec.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))));

        bytes.reset();
        PhysicalEffectObservationStateCodec.write(new DataOutputStream(bytes), Map.of(id, receipt));
        assertEquals(Map.of(id, receipt), PhysicalEffectObservationStateCodec.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSitePreparationObservation(id, intent, site, 65, 64));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSitePreparationObservation(id, intent, site, 0, 0));
    }
}
