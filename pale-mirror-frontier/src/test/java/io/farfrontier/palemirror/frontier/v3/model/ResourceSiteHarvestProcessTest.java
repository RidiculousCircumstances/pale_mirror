package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteHarvestProcessTest {
    @Test
    void exactMatureFieldCreatesOneNamedWheatStackOnlyAfterObservedReceipt() {
        FrontierWorldState ready = activeDepot(ready(initial())); SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(ready,
                ResourceSiteHarvestProcess.review(ready.resourceSites().site(site), 22_000L));

        assertEquals(2, planned.size()); ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.getFirst().payload();
        assertFalse(ready.inventory().items().containsKey(started.job().outputItemId()));
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(ready, site, started);
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(1).payload()).intent();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, intent);
        harvesting = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        ResourceSiteHarvestObservation receipt = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:site-harvest-1"), intent.id(), site,
                started.job().workerId(), output, 64);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> completion = ResourceSiteHarvestProcess.planTransition(harvesting, intent,
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)), 22_010L);

        assertEquals(2, completion.size());
        FrontierWorldState complete = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(ResourceSitePhase.GROWING, complete.resourceSites().site(site).phase());
        assertEquals(output, complete.inventory().items().get(output.id()));
        assertTrue(new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(complete)).inventory().items().containsKey(output.id()));
    }

    @Test
    void inactiveOrFullDepotLeavesReadyFieldWithoutInventingAHarvest() {
        FrontierWorldState state = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state,
                ResourceSiteHarvestProcess.review(state.resourceSites().site(site), 22_000L));

        assertEquals(1, planned.size()); assertTrue(planned.getFirst().payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created);
        assertEquals(ResourceSitePhase.READY, state.resourceSites().site(site).phase());
    }

    @Test
    void receiptCannotRedirectTheNamedHarvestToAnotherDepotSlot() {
        FrontierWorldState ready = activeDepot(ready(initial())); SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(ready,
                ResourceSiteHarvestProcess.review(ready.resourceSites().site(site), 22_000L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.getFirst().payload();
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(ready, site, started);
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(1).payload()).intent();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, intent);
        harvesting = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        InventoryCustody.ContainerSlot otherSlot = new InventoryCustody.ContainerSlot(started.job().outputSlot().containerId(),
                started.job().outputSlot().slot() + 1);
        ExactItemStack redirected = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, otherSlot);
        ResourceSiteHarvestObservation receipt = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:redirected-harvest"),
                intent.id(), site, started.job().workerId(), redirected, 64);

        FrontierWorldState state = harvesting;
        assertThrows(IllegalArgumentException.class, () -> state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    private static FrontierWorldState activeDepot(FrontierWorldState state) {
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        ExactInventory inventory = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE);
        return state.withInventory(inventory);
    }

    private static FrontierWorldState ready(FrontierWorldState state) {
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        PhysicalIntent intent = ((PhysicalIntentPrepared) preparation.get(1).payload()).intent(); state = ResourceSiteProcess.reducePrepared(state, site, intent);
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(new ResourceSitePreparationObservation(
                new PhysicalObservationId("observation:site-prepare-1"), intent.id(), site, 64, 64)));
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site, new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        return state;
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest"), 125L));
    }
}
