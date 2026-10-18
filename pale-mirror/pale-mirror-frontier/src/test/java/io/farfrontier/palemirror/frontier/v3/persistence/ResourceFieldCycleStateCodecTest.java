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
    @Test void extensionRecoveryRetainsBareGroundLineageAndFutureSowingSeparately() throws IOException {
        var first = SurfaceAnchor.at(1, 63, 2); var second = SurfaceAnchor.at(2, 63, 2);
        var id = new ResourceFieldLayout.CellId(1); var addedId = new ResourceFieldLayout.CellId(2);
        var layout = new ResourceFieldLayout(1, 2, List.of(new ResourceFieldLayout.Cell(id,
                first.support().offset(0, 1, 0), first, first)), List.of());
        var ground = java.util.Map.of(addedId, ResourceFieldCycle.Soil.GRASS);
        var next = layout.extend(List.of(new ResourceFieldLayout.Cell(addedId,
                second.support().offset(0, 1, 0), second, second)), 3, ground);
        var cycle = ResourceFieldCycle.seeded(new SubjectId("site:extended"), layout, 1).advanceGrowthStage().extended(next, ground);
        var bytes = new ByteArrayOutputStream();
        ResourceFieldCycleStateCodec.write(new DataOutputStream(bytes), cycle);
        var recovered = ResourceFieldCycleStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(cycle, recovered);
        assertEquals(layout.fingerprint(), recovered.layout().extensions().getFirst().predecessorFingerprint());
        assertEquals(ResourceFieldCycle.Crop.ABSENT, recovered.cell(addedId).crop());
        assertEquals(java.util.Set.of(id), recovered.sowing().cells());
    }
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

    @Test void roundTripsBlockedWorkAccessIndependentlyOfAnIntactCrop() throws IOException {
        var support = SurfaceAnchor.at(17, 63, -2);
        var id = new ResourceFieldLayout.CellId(1);
        var layout = new ResourceFieldLayout(1, 2,
                List.of(new ResourceFieldLayout.Cell(id, support.support().offset(0, 1, 0), support, support)), List.of());
        var cycle = ResourceFieldCycle.seeded(new SubjectId("site:field-access-codec"), layout, 1)
                .observedWorkAccess(id, true).skipBlocked(id).nextEpoch();
        var bytes = new ByteArrayOutputStream();
        ResourceFieldCycleStateCodec.write(new DataOutputStream(bytes), cycle);
        var recovered = ResourceFieldCycleStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(cycle, recovered);
        assertEquals(ResourceFieldCycle.Crop.GROWING, recovered.cell(id).crop());
        assertEquals(ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED, recovered.expectedWorkOutcome(id));
    }

    @Test void snapshotRejectsAFieldCycleOwnedByAnotherSiteDespiteMatchingGeometry() throws IOException {
        var outerSite = new SubjectId("site:outer");
        var foreignSite = new SubjectId("site:foreign");
        var layout = new ResourceFieldLayout(1, 1, List.of(), List.of());
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes);
        output.writeInt(1);
        FrontierWorldStateCodec.writeString(output, "settlement:1");
        FrontierWorldStateCodec.writeString(output, "structure:1-farm");
        output.writeByte(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind.WHEAT_FIELD.wireTag());
        output.writeByte(io.farfrontier.palemirror.frontier.v3.model.FieldLandOrigin.Kind.AUTHORED.wireTag());
        output.writeInt(0);
        FrontierWorldStateCodec.writeString(output, outerSite.value());
        output.writeByte(ResourceSitePhase.UNPREPARED.wireTag());
        output.writeLong(0L);
        output.writeByte(0);
        output.writeBoolean(false);
        output.writeLong(0L);
        output.writeByte(0);
        output.writeBoolean(false);
        output.writeByte(0);
        ResourceFieldCycleStateCodec.write(output, ResourceFieldCycle.unsurveyed(foreignSite, layout, 0));
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(0); // Cultivation reviews follow the complete site registry.
        var failure = assertThrows(IllegalArgumentException.class, () -> ResourceSiteStateCodec.read(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals("resource-site cell cycle declares another site owner", failure.getMessage());
    }
}
