package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceOperationManagerTest {
    private static final ReferenceTargetRef CELL_TEN_TEN = ReferenceTargetRef.cell(10, 10);

    @Test
    void sourceReconLaunchKeepsSourceLogisticsAndPathBeforeItsFirstMove() {
        ReferenceWorld world = sourceWorld();
        ReferenceOperation operation = world.operations().launchHuman(world, ReferenceOperationKind.RECON, 1, CELL_TEN_TEN);

        assertNotNull(operation);
        assertEquals(1, operation.id());
        assertEquals(ReferenceOperationStatus.ASSEMBLING, operation.status());
        assertEquals(new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, 1), operation.owner());
        assertEquals("cell:10,10", operation.target().key());
        assertEquals(9.892902069743818d, operation.personnel());
        assertEquals(13.78892877091931d, operation.power(), () -> operation.unitCompositionBySettlement() + " / " + operation.supplies());
        assertEquals(Map.of(ReferenceHumanUnitKind.LINE, 2.967870620923145d,
                        ReferenceHumanUnitKind.SCOUT, 5.4410961383591d,
                        ReferenceHumanUnitKind.LOGISTICS, 1.4839353104615725d),
                operation.unitCompositionBySettlement().get(1));
        assertEquals(Map.of(ReferenceResource.FOOD, 22.259029656923587d, ReferenceResource.MEDICINE, .2374296496738516d,
                        ReferenceResource.WEAPONS, 2.0d, ReferenceResource.AMMO, 8.0d),
                nonZero(operation.supplies()));
        assertEquals(List.of(new ReferencePoint(20, 27), new ReferencePoint(12, 23), new ReferencePoint(7, 18),
                new ReferencePoint(10, 10)), operation.waypoints());
        assertEquals("D0: Ashfield-01 launched recon operation 1 toward cell:10,10", world.events().getLast());
    }

    @Test
    void rejectedLaunchCannotMoveAResidentOrRemovePartialStock() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        Map<ReferenceResource, Double> stockBefore = new EnumMap<>(settlement.stock());
        int activeBefore = settlement.residents().availableIds().size();
        settlement.remove(ReferenceResource.AMMO, settlement.amount(ReferenceResource.AMMO));
        Map<ReferenceResource, Double> afterAmmoRemoval = new EnumMap<>(settlement.stock());

        assertNull(world.operations().launchHuman(world, ReferenceOperationKind.RECON, 1, CELL_TEN_TEN));
        assertEquals(afterAmmoRemoval, settlement.stock());
        assertEquals(activeBefore, settlement.residents().availableIds().size());
        assertEquals(0, world.operations().active().size());
        assertFalse(stockBefore.equals(afterAmmoRemoval));
    }

    @Test
    void sourceTargetKeysAndIntelDecayRemainStableAtTheBoundary() {
        ReferenceWorld world = sourceWorld();
        ReferenceOperationManager operations = world.operations();
        assertEquals(new ReferencePoint(10, 10), operations.targetPosition(world, CELL_TEN_TEN).orElseThrow());
        assertEquals("site:7", new ReferenceTargetRef(ReferenceTargetKind.SITE, 7, 5, 6).key());
        ReferenceIntelRecord intel = new ReferenceIntelRecord(CELL_TEN_TEN, 4, .8d, "recon");
        assertEquals(.8d, intel.confidenceOn(3));
        assertEquals(.8d * Math.pow(.955d, 6), intel.confidenceOn(10));
    }

    @Test
    void postLaunchRestoresExactNamedGarrisonWhenItsLateSupplyCheckRejectsTheOrder() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        List<String> garrison = settlement.residents().availableIds().subList(0, 4);
        settlement.assignPeopleToFieldPost(garrison, 1);
        ReferenceFieldCampaign campaign = world.field().createCampaign(world, ReferenceCampaignKind.CONTAINMENT, 1, Set.of(),
                "cell", null, 10, 10, "operation-recovery");
        assertNotNull(campaign);
        ReferenceFieldPost post = world.field().startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 1, 1, 1,
                Set.of(1), Map.of(1, 4.0d), Map.of(), Map.of(1, garrison));
        assertNotNull(post);
        post.status(ReferenceFieldPostStatus.ACTIVE);

        assertNull(world.operations().launchFromPost(world, ReferenceOperationKind.RECON, 1, post.id(), CELL_TEN_TEN,
                campaign.id(), Set.of(1), Map.of()));
        assertEquals(garrison.stream().sorted().toList(), post.residentIdsBySettlement().get(1));
        assertEquals(4.0d, post.garrisonBySettlement().get(1));
        for (String residentId : garrison) {
            ReferenceResident resident = settlement.residents().resident(residentId);
            assertEquals(ReferenceResidentLocation.FIELD_POST, resident.location());
            assertEquals(post.id(), resident.locationRef());
        }

        // The 1:40 post has only 3.5 cargo-volume capacity.  This exact small
        // payload fits it and is still sufficient for the two named scouts.
        post.receiveCargo(Map.of(ReferenceResource.FOOD, 2.0d, ReferenceResource.MEDICINE, .1d,
                ReferenceResource.WEAPONS, .1d, ReferenceResource.AMMO, .3d));
        assertNotNull(world.operations().previewFromPost(world, ReferenceOperationKind.RECON, post.id(), CELL_TEN_TEN, Set.of(1)), () -> post.stock().toString());
        ReferenceOperation launched = world.operations().launchFromPost(world, ReferenceOperationKind.RECON, 1, post.id(), CELL_TEN_TEN,
                campaign.id(), Set.of(1), Map.of("test", "post-launch"));
        assertNotNull(launched);
        assertEquals(new ReferenceTargetRef(ReferenceTargetKind.FIELD_POST, post.id()), launched.origin());
        assertEquals(List.of(new ReferencePoint(10, 10)), launched.waypoints());
        assertEquals(1.4616d, launched.power());
        assertEquals(Map.of(ReferenceResource.FOOD, 1.5d, ReferenceResource.MEDICINE, .016d,
                ReferenceResource.WEAPONS, .05d, ReferenceResource.AMMO, .2d), nonZero(launched.supplies()));
        assertEquals(2.0d, post.garrisonBySettlement().get(1));
        assertEquals(List.of("resident:1:11", "resident:1:12"), post.residentIdsBySettlement().get(1));
        assertEquals("post-launch", launched.details().get("test"));
    }

    private static ReferenceWorld sourceWorld() {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static ReferenceWorld grayboxWorld() {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.GRAYBOX_1_40));
    }

    private static Map<ReferenceResource, Double> nonZero(Map<ReferenceResource, Double> values) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        values.forEach((resource, amount) -> { if (amount != 0.0d) result.put(resource, amount); });
        return result;
    }
}
