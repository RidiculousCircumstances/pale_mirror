package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringRecoveryTeam;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteMaintenanceStateCodecTest {
    @Test
    void exactRetainedRepairRoundTripsWithItsDistinctOwnerAndTeam() throws Exception {
        SubjectId maintenanceId = new SubjectId("maintenance:route-12-64-8");
        SubjectId settlementId = new SubjectId("settlement:12");
        RouteMaintenance maintenance = new RouteMaintenance(maintenanceId, settlementId, new BlockPosition(12, 64, 8),
                GrayboxSemanticPart.ROUTE_SURFACE, RouteMaintenanceStatus.BUILDING, Optional.empty(),
                EngineeringRecoveryTeam.forWorkOrder(maintenanceId, settlementId, List.of(new SubjectId("resident:12-1"))), Optional.empty());
        Map<SubjectId, RouteMaintenance> expected = new LinkedHashMap<>();
        expected.put(maintenanceId, maintenance);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) { RouteMaintenanceStateCodec.write(output, expected); }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(expected, RouteMaintenanceStateCodec.read(input));
        }
    }

    @Test
    void engineeringTeamRejectsAForeignOwnerInsteadOfBecomingAnUnclaimedRoster() {
        assertThrows(IllegalArgumentException.class, () -> EngineeringRecoveryTeam.forWorkOrder(
                new SubjectId("route:frontier-network"), new SubjectId("settlement:12"), List.of(new SubjectId("resident:12-1"))));
    }
}
