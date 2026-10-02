package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.HarvestFixtureOwners;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3HarvestDepotSlotReservationTest {
    @Test
    void firstVisibilitySlotSelectionSkipsTheActiveFarmersPromisedOutputSlot() {
        FrontierWorldState state = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(
                new WorldId("frontier:harvest-depot-reservation"), 91L).initialState();
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) state.resourceSites()
                .site(new SubjectId("site:1-wheat-field")).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        SubjectId depot = job.outputSlot().containerId();
        int capacity = state.inventory().containers().get(depot).slotCount();

        int reservedSlot = job.outputSlot().slot();
        assertTrue(reservedSlot >= 0 && reservedSlot < capacity,
                "the farmer must reserve an actual output slot in the depot");
        int selectedSlot = FrontierV3ContainerSurfaceExecutor.nextProjectionSlot(state, depot, capacity, 0,
                slot -> state.inventory().itemAt(depot, slot).isPresent());
        assertTrue(selectedSlot >= 0 && selectedSlot < capacity,
                "the cold-stock writer must find another physical slot");
        assertFalse(selectedSlot == reservedSlot,
                "the cold-stock writer must not select the farmer's promised output slot");
        assertTrue(FrontierV3ContainerSurfaceExecutor.overlapsHarvestOutputReservation(state,
                java.util.List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(job.outputSlot()), "minecraft:wheat", 64))),
                "a loaded stack in the promised slot must not be adopted as current fungible stock");
        assertFalse(FrontierV3ContainerSurfaceExecutor.overlapsHarvestOutputReservation(state,
                java.util.List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, selectedSlot)),
                        "minecraft:wheat", 64))));
    }
}
