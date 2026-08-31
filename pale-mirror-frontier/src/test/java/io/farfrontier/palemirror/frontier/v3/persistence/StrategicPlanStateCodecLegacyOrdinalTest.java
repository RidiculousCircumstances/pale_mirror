package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.OperationCreated;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.RoutePatrolStarted;
import io.farfrontier.palemirror.frontier.v3.model.RoutePatrolStatus;
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

    @Test
    void recoversSchema82PatrolAsItsExactUnderstrengthHistoricalUnit() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeShort(1); // objectives
            output.writeUTF("objective:settlement-1-settlement_patrol_obstructed_route");
            output.writeUTF("settlement:1");
            output.writeByte(7); output.writeBoolean(false); output.writeBoolean(false); output.writeInt(1); output.writeByte(0);
            output.writeShort(1); // tasks
            output.writeUTF("task:settlement-1-settlement_patrol_obstructed_route");
            output.writeUTF("objective:settlement-1-settlement_patrol_obstructed_route");
            output.writeUTF("settlement:1");
            output.writeByte(8); output.writeBoolean(false); output.writeBoolean(false); output.writeBoolean(false);
            output.writeShort(1); output.writeByte(11); // AVAILABLE_GUARD
            output.writeShort(0); output.writeByte(1); output.writeBoolean(false); // dependencies, ACTIVE, no observed position
            output.writeShort(1); // patrols
            output.writeUTF("task:settlement-1-settlement_patrol_obstructed_route");
            output.writeUTF("settlement:1");
            output.writeUTF("resident:1-16"); // historical guard field
            output.writeShort(2);
            output.writeInt(0); output.writeInt(64); output.writeInt(0);
            output.writeInt(1); output.writeInt(64); output.writeInt(0);
            output.writeByte(0); output.writeByte(RoutePatrolStatus.EN_ROUTE.wireTag()); output.writeBoolean(false);
            output.writeShort(0); // engagements
            output.writeShort(0); // assaults
            output.writeShort(0); // infection knowledge
            output.writeShort(0); // hive operation knowledge
            output.writeShort(0); // hive territory knowledge
            output.writeShort(0); // hive settlement knowledge
            output.writeByte(0); output.writeLong(0L); // doctrine
        }

        var restored = StrategicPlanStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), false,
                true, true, true, true, true, true, true, 82);

        var patrol = restored.routePatrols().get(new SubjectId("task:settlement-1-settlement_patrol_obstructed_route"));
        assertEquals(true, patrol.unit().legacyUnderstrength());
        assertEquals(new SubjectId("resident:1-16"), patrol.unit().leaderId());
        assertEquals(java.util.List.of(new SubjectId("resident:1-16")), patrol.memberIds());
    }

    @Test
    void recoversHistoricalPatrolWalAsItsExactUnderstrengthHistoricalUnit() throws Exception {
        byte[] oldPayload = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            output.writeUTF("task:settlement-1-settlement_patrol_obstructed_route");
            output.writeUTF("settlement:1");
            output.writeUTF("resident:1-16"); // old WAL had no manifest envelope
            output.writeByte(2);
            output.writeInt(0); output.writeInt(64); output.writeInt(0);
            output.writeInt(1); output.writeInt(64); output.writeInt(0);
            output.writeByte(0); output.writeByte(RoutePatrolStatus.EN_ROUTE.wireTag()); output.writeBoolean(false);
        });

        RoutePatrolStarted restored = (RoutePatrolStarted) RoutePatrolPayloadCodecs.started().decode(oldPayload);

        assertEquals(true, restored.patrol().unit().legacyUnderstrength());
        assertEquals(new SubjectId("resident:1-16"), restored.patrol().unit().leaderId());
        assertEquals(java.util.List.of(new SubjectId("resident:1-16")), restored.patrol().memberIds());
    }

    @Test
    void recoversHistoricalCargoOperationWalAsItsExactUnderstrengthEscort() throws Exception {
        byte[] oldPayload = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            output.writeUTF("operation:supply-1-2"); output.writeUTF("settlement:1");
            output.writeUTF("cargo:supply-1-2"); output.writeUTF("hive:frontier");
            output.writeByte(2); // old participant envelope: crew then its one guard
            output.writeUTF("resident:1-30"); output.writeUTF("resident:1-16");
            output.writeByte(2);
            output.writeInt(0); output.writeInt(64); output.writeInt(0);
            output.writeInt(1); output.writeInt(64); output.writeInt(0);
            output.writeByte(0); output.writeByte(OperationStage.EN_ROUTE.wireCode());
            output.writeByte(0); // old no-active-travel flag
        });

        OperationCreated restored = (OperationCreated) io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode("frontier.operation_created", oldPayload);

        assertEquals(true, restored.operation().unit().legacyUnderstrength());
        assertEquals(new SubjectId("resident:1-30"), restored.operation().unit().cargoCrewId());
        assertEquals(new SubjectId("resident:1-16"), restored.operation().unit().leaderId());
        assertEquals(java.util.List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16")), restored.operation().participantIds());
    }
}
