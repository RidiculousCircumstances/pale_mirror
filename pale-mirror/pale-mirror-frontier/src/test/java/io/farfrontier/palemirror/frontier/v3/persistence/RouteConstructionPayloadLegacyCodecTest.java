package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStarted;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStatus;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Golden historical WAL shape before construction work acquired an exact crew. */
class RouteConstructionPayloadLegacyCodecTest {
    @Test
    void recoversHistoricalAutonomousConstructionWalWithoutInventingPeople() throws Exception {
        byte[] historical = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            output.writeUTF("construction:route-reroute-settlement-1--380-64--304");
            output.writeUTF("settlement:1");
            output.writeByte(RouteConstructionStatus.BUILDING.wireTag());
            output.writeShort(2); output.writeByte(3);
            for (BlockPosition point : java.util.List.of(new BlockPosition(-380, 64, -304), new BlockPosition(-416, 64, -304), new BlockPosition(-416, 64, -244))) {
                output.writeInt(point.x()); output.writeInt(point.y()); output.writeInt(point.z());
            }
        });

        RouteConstructionStarted restored = (RouteConstructionStarted) FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode("frontier.route_construction_started", historical);

        assertEquals(new SubjectId("construction:route-reroute-settlement-1--380-64--304"), restored.project().id());
        assertTrue(restored.project().team().isEmpty(), "historical work stays explicitly autonomous; recovery never manufactures a crew");
    }
}
