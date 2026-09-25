package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceFieldCycleStateCodecTest {
    @Test void roundTripsBlockedBareDirtWithoutPlantingOrYieldAcrossRecovery() throws IOException {
        var support = SurfaceAnchor.at(17, 63, -2);
        var id = new ResourceFieldLayout.CellId(1);
        var layout = new ResourceFieldLayout(1, 2,
                List.of(new ResourceFieldLayout.Cell(id, support.support().offset(0, 1, 0), support, support)), List.of());
        var cycle = ResourceFieldCycle.seeded(new SubjectId("site:field-codec-test"), layout, 1)
                .cropRemoved(id).soilBecameDirt(id)
                .cropObstructed(id).skipBlocked(id);
        var bytes = new ByteArrayOutputStream();
        ResourceFieldCycleStateCodec.write(new DataOutputStream(bytes), cycle);
        var recovered = ResourceFieldCycleStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(cycle, recovered);
        assertEquals(ResourceFieldCycle.Soil.DIRT, recovered.obstructionCleared(id).cell(id).soil());
        assertEquals(0, recovered.harvestedCount());
    }

    @Test void snapshotRejectsAFieldCycleOwnedByAnotherSiteDespiteMatchingGeometry() throws IOException {
        var outerSite = new SubjectId("site:outer");
        var foreignSite = new SubjectId("site:foreign");
        var layout = new ResourceFieldLayout(1, 1, List.of(), List.of());
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes);
        output.writeByte(1);
        FrontierWorldStateCodec.writeString(output, outerSite.value());
        output.writeByte(ResourceSitePhase.UNPREPARED.wireTag());
        output.writeLong(0L);
        output.writeByte(0);
        output.writeBoolean(false);
        output.writeBoolean(false);
        output.writeBoolean(false);
        ResourceFieldCycleStateCodec.write(output, ResourceFieldCycle.unsurveyed(foreignSite, layout, 0));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteStateCodec.read(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
