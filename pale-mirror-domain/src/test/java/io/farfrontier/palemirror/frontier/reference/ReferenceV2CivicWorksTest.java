package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ReferenceV2CivicWorksTest {
    @Test
    void sourceCivicDecisionCleansAnObservedOwnedContaminatedSite() {
        ReferenceWorld world = sourceV2World();
        ReferenceSettlement settlement = world.settlements().get(1);
        isolateToFirstSettlement(world);
        for (ReferenceResource resource : ReferenceResource.values()) settlement.add(resource, 10_000.0d);
        ReferenceResourceSite site = world.resourceSites().get(1);
        site.contamination(1.0d);
        site.condition(1.0d);

        ReferenceV2CivicWorks.Work work = world.v2().civicSiteDecision(world, settlement.id());
        assertEquals("cleanse", work.action());
        assertEquals(site.id(), work.site().id());
        world.v2().maintainCivicSites(world);

        assertEquals(0.72d, site.contamination());
        assertEquals(0, world.v2().lastCivicWorkDay().get(settlement.id()));
        assertEquals("D0: Ashfield-01 cleansed mine site 1", world.events().getLast());
    }

    @Test
    void sourceClaimProjectCompletesOnlyAfterItsRecordedDelay() {
        ReferenceWorld world = sourceV2World();
        isolateToFirstSettlement(world);
        ReferenceResourceSite site = world.resourceSites().get(4);
        site.ownerId(null);
        world.v2().queueCivicSiteProject(new ReferenceCivicSiteProject(1, site.id(), "claim", 1));
        world.day(9);

        world.v2().advanceCivicSiteProjects(world);

        assertEquals(1, site.ownerId());
        assertEquals(9, site.claimedDay());
        assertEquals(0, world.v2().civicSiteProjects().size());
        assertEquals("D9: Ashfield-01 reclaimed farm site 4", world.events().getLast());
    }

    @Test
    void sourceClaimProjectDiscardsADeadSettlementWithoutClaimingTheSite() {
        ReferenceWorld world = sourceV2World();
        ReferenceResourceSite site = world.resourceSites().get(4);
        site.ownerId(null);
        world.settlements().get(1).alive(false);
        world.v2().queueCivicSiteProject(new ReferenceCivicSiteProject(1, site.id(), "claim", 1));

        world.v2().advanceCivicSiteProjects(world);

        assertNull(site.ownerId());
        assertEquals(0, world.v2().civicSiteProjects().size());
    }

    private static ReferenceWorld sourceV2World() {
        return new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static void isolateToFirstSettlement(ReferenceWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            if (settlement.id() != 1) settlement.alive(false);
        }
    }
}
