package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategicPlanStateCodecLegacyOrdinalTest {
    @Test
    void recoversTheDeployedPreAssaultHarvestLayoutEvenWhenItRetainedSchema79() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeShort(1);
            output.writeUTF("objective:settlement-1-settlement_harvest_resource_site-1");
            output.writeUTF("settlement:1");
            output.writeByte(8); // Pre-assault ordinal for SETTLEMENT_HARVEST_RESOURCE_SITE.
            output.writeBoolean(false); output.writeBoolean(true); output.writeUTF("site:1-wheat-field");
            output.writeInt(1); output.writeByte(0);
            output.writeShort(1);
            output.writeUTF("task:settlement-1-settlement_harvest_resource_site-1");
            output.writeUTF("objective:settlement-1-settlement_harvest_resource_site-1");
            output.writeUTF("settlement:1");
            output.writeByte(9); // Pre-assault ordinal for HARVEST_RESOURCE_SITE.
            output.writeBoolean(false); output.writeBoolean(false); output.writeBoolean(true); output.writeUTF("site:1-wheat-field");
            output.writeShort(3); output.writeByte(15); output.writeByte(16); output.writeByte(7); // ACTIVE_FARM, AVAILABLE_FARMER, FREE_DEPOT_SLOT.
            output.writeShort(0); output.writeByte(0); output.writeBoolean(false);
            output.writeShort(0); output.writeShort(0); output.writeShort(0); output.writeShort(0);
            output.writeShort(0); output.writeShort(0); output.writeShort(0); output.writeByte(0); output.writeLong(0L);
        }

        var restored = StrategicPlanStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), false,
                true, true, true, true, true, true, true, 79);

        assertEquals(StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE,
                restored.objectives().get(new SubjectId("objective:settlement-1-settlement_harvest_resource_site-1")).kind());
        assertEquals(StrategicTaskKind.HARVEST_RESOURCE_SITE,
                restored.tasks().get(new SubjectId("task:settlement-1-settlement_harvest_resource_site-1")).kind());
        assertEquals(Optional.of(new SubjectId("site:1-wheat-field")), restored.objectives().values().iterator().next().resourceSiteTarget());
    }
}
