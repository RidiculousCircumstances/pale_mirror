package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceFieldWarfareTest {
    @Test
    void sourceCampaignPostsCargoModuleAndCorridorPreserveTheirIndependentState() {
        ReferenceWorld world = sourceWorld();
        ReferenceFieldWarfare field = world.field();
        ReferenceFieldCampaign campaign = field.createCampaign(world, ReferenceCampaignKind.CONTAINMENT, 1, Set.of(2),
                "cell", null, 10, 10, "microtrace");
        assertNotNull(campaign);
        assertEquals(Set.of(1, 2), campaign.contributors());
        assertEquals("D0: Ashfield-01 opened containment campaign 1: microtrace", world.events().getLast());

        ReferenceFieldPost post = field.startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 1, 1, 1,
                Set.of(1, 2), Map.of(1, 4.0d, 2, 2.0d), cargo(),
                Map.of(1, List.of("resident:1:3", "resident:1:1"), 2, List.of("resident:2:2")));
        ReferenceFieldPost second = field.startPost(world, campaign.id(), ReferenceFieldPostKind.OBSERVATION, 7, 1, 1,
                Set.of(1, 2), Map.of(1, 1.0d), Map.of(), Map.of());
        assertNotNull(post);
        assertNotNull(second);
        assertEquals(ReferenceCampaignPhase.ESTABLISH, campaign.phase());
        assertEquals(List.of("resident:1:3", "resident:1:1"), post.residentIdsBySettlement().get(1));
        assertEquals(0.0d, post.stock(ReferenceResource.TIMBER));
        assertEquals(2.0d, post.stock(ReferenceResource.MEDICINE));
        assertEquals(60.0d, post.stock(ReferenceResource.FOOD));
        assertEquals(100.0d, post.stock(ReferenceResource.AMMO));
        assertEquals(56.12500000000001d, post.stock(ReferenceResource.WEAPONS));
        assertEquals(140.0d, post.storedVolume());

        ReferenceFieldLink link = field.startLink(world, campaign.id(), ReferenceFieldLinkKind.SUPPLY_CORRIDOR, post.id(), second.id());
        assertNotNull(link);
        post.status(ReferenceFieldPostStatus.ACTIVE);
        link.status("active");
        assertTrue(field.startModule(world, post.id(), ReferenceFieldModuleKind.DEPOT));
        post.mutableModules().add(ReferenceFieldModuleKind.DEPOT);
        assertEquals(2, post.moduleProjects().get(ReferenceFieldModuleKind.DEPOT));
        assertEquals(308.0d, post.storageCapacity());
        assertEquals(4.92d, field.postPower(post));
        post.isolationDays(3);
        assertEquals(3.444d, field.postPower(post));
        assertEquals(1.22d, field.movementMultiplier(1.0d, 1.0d, 7.0d, 1.0d));
        assertEquals("D0: field post 1 began depot module", world.events().getLast());
    }

    @Test
    void sourceFieldAdmissionRejectsAnOverlappingPostAndModuleWorkAtABuildingPost() {
        ReferenceWorld world = sourceWorld();
        ReferenceFieldWarfare field = world.field();
        ReferenceFieldCampaign campaign = field.createCampaign(world, ReferenceCampaignKind.CONTAINMENT, 1, Set.of(2),
                "cell", null, 10, 10, "microtrace");
        assertNotNull(campaign);
        ReferenceFieldPost post = field.startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 1, 1, 1,
                Set.of(1), Map.of(1, 1.0d), Map.of(), Map.of());
        assertNotNull(post);
        assertNull(field.startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 2, 1, 1,
                Set.of(1), Map.of(1, 1.0d), Map.of(), Map.of()));
        assertFalse(field.startModule(world, post.id(), ReferenceFieldModuleKind.DEPOT));
    }

    private static ReferenceWorld sourceWorld() {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static Map<ReferenceResource, Double> cargo() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        result.put(ReferenceResource.TIMBER, 100.0d);
        result.put(ReferenceResource.ORE, 50.0d);
        result.put(ReferenceResource.TOOLS, 30.0d);
        result.put(ReferenceResource.MEDICINE, 2.0d);
        result.put(ReferenceResource.FOOD, 60.0d);
        result.put(ReferenceResource.AMMO, 100.0d);
        result.put(ReferenceResource.WEAPONS, 100.0d);
        return result;
    }
}
