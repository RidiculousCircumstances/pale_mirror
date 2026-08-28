package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteStateTest {
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
        ResourceSiteHarvestJob harvest = new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-1-wheat-field-1"), siteId,
                new SubjectId("resident:1-1"), new SubjectId("item:site-harvest-1-wheat-field-1"),
                new PhysicalIntentId("intent:site-harvest-1-wheat-field-1"));
        ResourceSiteLifecycle harvested = growing.harvesting(harvest).harvested();
        assertEquals(ResourceSitePhase.GROWING, harvested.phase());
        assertEquals(2L, harvested.growthEpoch()); assertEquals(0, harvested.growthStage());
    }

    @Test
    void registerRejectsMissingSitesAndIllegalLifecycleTransitions() {
        FrontierWorldState baseline = initial(); SubjectId siteId = new SubjectId("site:1-wheat-field");
        assertThrows(IllegalArgumentException.class, () -> baseline.withResourceSites(new ResourceSiteState(Map.of())));
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
