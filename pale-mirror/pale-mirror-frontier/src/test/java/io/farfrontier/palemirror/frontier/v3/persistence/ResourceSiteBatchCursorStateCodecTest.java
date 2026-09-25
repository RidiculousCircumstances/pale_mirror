package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceSiteBatchCursorStateCodecTest {
    @Test void snapshotRetainsDeliveredFullPartBesideLaterCarriedUnit() throws Exception {
        SubjectId site = new SubjectId("site:batch-field-1");
        List<ResourceFieldLayout.Cell> cells = IntStream.range(0, 65).mapToObj(index -> {
            SurfaceAnchor soil = SurfaceAnchor.at(index, 64, 0);
            return new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(index + 1L),
                    new BlockPosition(index, 65, 0), soil, soil);
        }).toList();
        ResourceFieldLayout layout = new ResourceFieldLayout(1, 66, cells, List.of());
        Map<ResourceFieldLayout.CellId, ResourceFieldCycle.CellState> worked = cells.stream().collect(
                java.util.stream.Collectors.toMap(ResourceFieldLayout.Cell::id,
                        ignored -> new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                                ResourceFieldCycle.Crop.GROWING, 0, true, true)));
        ResourceFieldCycle cycle = ResourceFieldCycle.restore(site, layout, 1, worked);
        SubjectId jobId = new SubjectId("job:site-harvest-batch-field-1");
        SubjectId worker = new SubjectId("resident:batch-field-1");
        SubjectId actorAccount = new SubjectId("custody:field-actor-batch-field-1");
        SubjectId depotAccount = new SubjectId("custody:container-batch-field-1");
        SubjectId depot = new SubjectId("container:batch-field-1");
        PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-batch-field-1");
        UUID body = UUID.fromString("00000000-0000-0000-0000-000000000065");
        ResourceSiteHarvestDeliveryObservation receipt = new ResourceSiteHarvestDeliveryObservation(
                ResourceSiteHarvestBatchDelivered.observationId(jobId, 0), intent,
                site, jobId, worker, actorAccount, depotAccount,
                new SceneLeaseId("lease:site-harvest-batch-field-1"), body, 1L, 64, 1L,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64)),
                "sha256:" + "a".repeat(64), 2L, "witness:field-delivery-batch-field-1-part-0");
        ResourceSiteHarvestJob job = new ResourceSiteHarvestJob(jobId,
                new SubjectId("task:batch-field-1"), site, worker,
                actorAccount, depotAccount,
                new SubjectId("item:site-harvest-batch-field-1"),
                new InventoryCustody.ContainerSlot(depot, 1), intent,
                new ResourceSiteHarvestProgress(65, 65, -1), 64, false,
                Optional.empty(), Optional.of(new ResourceSiteHarvestBatchDelivered(receipt, 0)), Optional.empty());
        ResourceSiteLifecycle lifecycle = new ResourceSiteLifecycle(site, ResourceSitePhase.HARVESTING, 1, 7,
                Optional.of(job));
        ResourceSiteState state = new ResourceSiteState(Map.of(site, lifecycle), Map.of(site, cycle));
        var bytes = new ByteArrayOutputStream();
        ResourceSiteStateCodec.write(new DataOutputStream(bytes), state);
        ResourceSiteState recovered = ResourceSiteStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(state, recovered);
        assertEquals(64, ((ResourceSiteHarvestJob) recovered.site(site).activeWork().orElseThrow()).deliveredYieldQuantity());
        assertEquals(Optional.of(new ResourceSiteHarvestBatchDelivered(receipt, 0)),
                ((ResourceSiteHarvestJob) recovered.site(site).activeWork().orElseThrow()).lastConfirmedBatch());
        assertEquals(1, ((ResourceSiteHarvestJob) recovered.site(site).activeWork().orElseThrow()).carriedYieldQuantity(
                recovered.cycle(site).harvestedCount()));

        // The same serialized cursor cannot be installed against an accounted prefix
        // containing fewer real yields: it would silently skip an undelivered part.
        var nonYielding = new java.util.HashMap<>(worked);
        for (int index = 0; index < 2; index++) {
            nonYielding.put(cells.get(index).id(), new ResourceFieldCycle.CellState(
                    ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 0, true, false));
        }
        ResourceFieldCycle mismatched = ResourceFieldCycle.restore(site, layout, 1, nonYielding);
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceSiteState(Map.of(site, lifecycle), Map.of(site, mismatched)));
    }
}
