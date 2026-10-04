package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteStateTest {
    @Test
    void currentDescriptorTakesIdentityFromBootstrapAndGeometryFromCanonicalCycle() {
        FrontierWorldState baseline = initial();
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        ResourceSite bootstrapSite = FrontierResourceSitePlan.compile(baseline.bootstrap()).get(siteId);
        ResourceFieldCycle initial = baseline.resourceSites().cycle(siteId);
        ResourceFieldLayout revised = initial.layout().revise(2, initial.layout().nextCellId(),
                initial.layout().cells(), initial.layout().irrigationSlots());
        ResourceFieldCycle current = initial.revised(revised, java.util.Set.of());
        Map<SubjectId, ResourceFieldCycle> cycles = new java.util.LinkedHashMap<>(baseline.resourceSites().cycles());
        cycles.put(siteId, current);
        ResourceSiteState sites = new ResourceSiteState(baseline.resourceSites().sites(), cycles);
        ResourceSite descriptor = sites.descriptor(baseline.bootstrap(), siteId);
        assertEquals(bootstrapSite.settlementId(), descriptor.settlementId());
        assertEquals(bootstrapSite.facilityId(), descriptor.facilityId());
        assertEquals(revised, descriptor.layout());
        assertEquals(revised, sites.descriptors(baseline.bootstrap()).get(siteId).layout());
        assertEquals(1, bootstrapSite.layout().revision(), "bootstrap remains an immutable initial plan");
        assertThrows(IllegalArgumentException.class, () -> sites.descriptor(baseline.bootstrap(), new SubjectId("site:missing")));
    }

    @Test
    void fieldLifecycleKeepsOneExactWorkIdentityAndRecoversWithoutChangingIt() {
        FrontierWorldState baseline = initial();
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        ResourceSitePreparationJob preparation = new ResourceSitePreparationJob(new SubjectId("job:site-prepare-1-wheat-field"), siteId,
                new PhysicalIntentId("intent:site-prepare-1-wheat-field"));
        ResourceSiteLifecycle preparing = baseline.resourceSites().site(siteId).preparing(preparation);
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(
                baseline.withResourceSites(baseline.resourceSites().replace(preparing))));

        assertEquals(preparing, recovered.resourceSites().site(siteId));
        ResourceSiteLifecycle growing = preparing.prepared();
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) growing = growing.advanceGrowth();
        assertEquals(ResourceSitePhase.READY, growing.phase());
        SubjectId jobId = new SubjectId("job:site-harvest-1-wheat-field-1");
        ResourceSite site = FrontierResourceSitePlan.compile(baseline.bootstrap()).get(siteId);
        ResourceSiteHarvestJob harvest = new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-1-wheat-field-1"), new SubjectId("task:field-harvest-1"), siteId,
                new SubjectId("resident:1-1"), new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"),
                ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(new SubjectId("settlement:1"))),
                new SubjectId("item:site-harvest-1-wheat-field-1"),
                new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(new SubjectId("settlement:1")), 1),
                new PhysicalIntentId("intent:site-harvest-1-wheat-field-1"), completeProgress(site.layout().cells().size()),
                ResourceFieldCycle.seeded(siteId, site.layout(), 1).target(site.layout().cells().getFirst().id()));
        ResourceFieldCycle unworked = ResourceFieldCycle.seeded(siteId, site.layout(), 1);
        ResourceSiteLifecycle ready = growing;
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteState(Map.of(siteId, ready),
                Map.of(siteId, ResourceFieldCycle.seeded(new SubjectId("site:foreign"), site.layout(), 1))),
                "the map key and matching geometry cannot adopt another site's cycle");
        ResourceSiteLifecycle falselyComplete = growing.harvesting(harvest);
        assertEquals(harvest, new ResourceSiteState(Map.of(siteId, falselyComplete), Map.of(siteId, unworked))
                .site(siteId).harvestJob(harvest.id()).orElseThrow(),
                "execution accounting is independent of collective plant flags");
        SurfaceAnchor depotStation = SurfaceAnchor.at(1, 64, 1);
        ResourceSiteHarvestGoal depotGoal = new ResourceSiteHarvestGoal(harvest.id(), siteId, harvest.workerId(),
                site.layout().revision(), site.layout().cells().size(), ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE,
                Optional.empty(), List.of(depotStation), TraversalCapability.PEDESTRIAN,
                ResourceSiteHarvestGoal.ArrivalContract.ANY_DEPOT_SERVICE_STATION);
        ResourceSiteLifecycle harvested = growing.harvesting(harvest).harvestedAt(depotGoal, depotStation.standingBody());
        assertEquals(ResourceSitePhase.READY, harvested.phase());
        assertEquals(1L, harvested.growthEpoch()); assertEquals(7, harvested.growthStage());
        assertTrue(harvested.harvestJobs().isEmpty());
    }

    private static ResourceSiteHarvestProgress completeProgress(int slots) {
        ResourceSiteHarvestProgress progress = ResourceSiteHarvestProgress.notStarted(slots);
        for (int index = 0; index < slots; index++) {
            progress = progress.prepareNextCrop().confirmPreparedCrop();
        }
        return progress;
    }

    @Test
    void registerRejectsMissingSitesAndIllegalLifecycleTransitions() {
        FrontierWorldState baseline = initial(); SubjectId siteId = new SubjectId("site:1-wheat-field");
        assertThrows(IllegalArgumentException.class, () -> baseline.withResourceSites(new ResourceSiteState(Map.of(), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteLifecycle(siteId, ResourceSitePhase.READY, 0L,
                ResourceSiteLifecycle.MATURE_STAGE, java.util.Optional.empty()));
        assertThrows(IllegalStateException.class, () -> baseline.resourceSites().site(siteId).advanceGrowth());
        assertTrue(baseline.resourceSites().sites().values().stream().allMatch(site -> site.phase() == ResourceSitePhase.UNPREPARED));
    }

    @Test
    void destroyedFarmRetiresOnlyItsOwnResourceSite() {
        FrontierWorldState baseline = initial();
        FrontierWorldState destroyed = baseline.withStructureCondition(new SubjectId("structure:1-farm"), StructureCondition.DESTROYED);
        assertEquals(ResourceSitePhase.DESTROYED, destroyed.resourceSites().site(new SubjectId("site:1-wheat-field")).phase());
        assertEquals(ResourceSitePhase.UNPREPARED, destroyed.resourceSites().site(new SubjectId("site:2-wheat-field")).phase());
        assertEquals(destroyed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(destroyed)));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-state"), 1234L));
    }
}
