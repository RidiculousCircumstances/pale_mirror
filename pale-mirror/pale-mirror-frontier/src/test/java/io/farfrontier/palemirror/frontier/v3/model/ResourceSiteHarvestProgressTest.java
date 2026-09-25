package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResourceSiteHarvestProgressTest {
    private static ResourceSiteHarvestJob job(ResourceSiteHarvestProgress progress) {
        SubjectId site = new SubjectId("site:large-field-1");
        return new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-large-field-1"),
                new SubjectId("task:large-field-1"), site, new SubjectId("resident:large-field-1"),
                new SubjectId("custody:field-actor-large-field-1"), new SubjectId("custody:container-large-field-1"),
                new SubjectId("item:site-harvest-large-field-1"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:large-field-1"), 0),
                new PhysicalIntentId("intent:site-harvest-large-field-1"), progress);
    }

    @Test void deliveredBatchIsBoundedByActualHandAndRoundTripsWithoutRouteCache() {
        ResourceSiteHarvestJob base = job(new ResourceSiteHarvestProgress(65, 64, -1));
        ResourceSiteHarvestJob returning = base.withFullBatchReturn(64);
        InventoryCustody.ContainerSlot nextSlot = new InventoryCustody.ContainerSlot(base.outputSlot().containerId(), 1);
        ResourceSiteHarvestJob reserved = returning.reserveBatchSuccessorSlot(nextSlot, 64);
        ResourceSiteHarvestJob continued = reserved.afterFullBatchDelivery(nextSlot, 64, Optional.empty());
        assertEquals(64, continued.progress().completedCropSlots());
        assertEquals(64, continued.deliveredYieldQuantity());
        assertEquals(0, continued.carriedYieldQuantity(64));
        assertEquals(nextSlot, continued.outputSlot());
        assertFalse(continued.returningForBatch());
        assertThrows(IllegalArgumentException.class, () -> reserved.afterFullBatchDelivery(
                new InventoryCustody.ContainerSlot(base.outputSlot().containerId(), 2), 64, Optional.empty()));
        ResourceSiteHarvestStarted started = new ResourceSiteHarvestStarted(continued);
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
    }

    @Test void cropReceiptRequiresObservedSemanticStationNotAPathIndex() {
        ResourceSiteHarvestJob approaching = job(ResourceSiteHarvestProgress.notStarted(1));
        ResourceSiteLifecycle before = new ResourceSiteLifecycle(approaching.siteId(), ResourceSitePhase.HARVESTING,
                1L, ResourceSiteLifecycle.MATURE_STAGE, Optional.of(approaching));
        SurfaceAnchor station = SurfaceAnchor.at(1, 64, 0);
        ResourceSiteHarvestGoal goal = new ResourceSiteHarvestGoal(approaching.id(), approaching.siteId(), approaching.workerId(),
                1L, 0, ResourceSiteHarvestGoal.Kind.WORK_CELL, Optional.of(new ResourceFieldLayout.CellId(1)),
                List.of(station), TraversalCapability.PEDESTRIAN,
                ResourceSiteHarvestGoal.ArrivalContract.EXACT_WORK_STATION);
        assertThrows(IllegalArgumentException.class,
                () -> before.prepareHarvestCrop(approaching, 0, goal, SurfaceAnchor.at(0, 64, 0)));
        ResourceSiteHarvestJob prepared = (ResourceSiteHarvestJob) before.prepareHarvestCrop(approaching, 0, goal, station)
                .activeWork().orElseThrow();
        assertEquals(1, before.prepareHarvestCrop(approaching, 0, goal, station)
                .advanceHarvest(prepared, 1, goal, station).activeWork()
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow().progress().completedCropSlots());
    }

    @Test void completionUsesActualFieldSizeAndWalPreservesLargeCellIndexes() {
        ResourceSiteHarvestProgress one = ResourceSiteHarvestProgress.notStarted(1);
        assertTrue(one.prepareNextCrop().confirmPreparedCrop().complete());
        ResourceSiteHarvestProgress sixtyFive = new ResourceSiteHarvestProgress(65, 64, -1);
        assertFalse(sixtyFive.complete());
        assertTrue(sixtyFive.prepareNextCrop().confirmPreparedCrop().complete());
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteHarvestProgress(65, 66, -1));
        SubjectId id = new SubjectId("job:site-harvest-large-field-1");
        ResourceSiteHarvestCropPrepared prepared = new ResourceSiteHarvestCropPrepared(id, 256);
        ResourceSiteHarvestProgressed progressed = new ResourceSiteHarvestProgressed(new SubjectId("site:large-field-1"), 3,
                id, 257, 2, new ResourceFieldLayout.CellId(257), ResourceFieldCycle.WorkOutcome.HARVESTED,
                new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-large-field-1"), 22_301L);
        assertEquals(prepared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(prepared.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(prepared)));
        assertEquals(progressed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(progressed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(progressed)));
    }
}
