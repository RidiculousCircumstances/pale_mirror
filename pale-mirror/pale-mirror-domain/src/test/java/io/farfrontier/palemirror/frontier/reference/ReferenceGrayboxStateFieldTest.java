package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateFieldTest {
    @Test
    void restoresPostsLinksCampaignsAndEngagementsAtEveryPinnedCheckpoint() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Set<Integer> checkpoints = Set.of(0, 1, 2, 3, 5, 10, 15, 20, 25, 30);
        for (int day = 0; day <= 30; day++) {
            if (checkpoints.contains(day)) {
                Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                        ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
                Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
                ReferenceGrayboxStateField.State field = ReferenceGrayboxStateField.read(reference.get("field"));
                ReferenceWorld restored = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
                field.applyTo(restored.field());
                assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateField.capture(source)),
                        ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateField.capture(restored)), "field day " + day);
            }
            if (day < 30) source.tick();
        }
    }

    @Test
    void rejectsAnIncompleteFieldOwnerBeforeMutatingIt() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateField.read(Map.of()));
    }

    @Test
    void completedCampaignRetainsItsCanonicalCampaignIdentity() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        ReferenceFieldCampaign campaign = source.field().createCampaign(source, ReferenceCampaignKind.CONTAINMENT, 1, Set.of(),
                "cell", null, 10, 10, "completed recovery");
        campaign.phase(ReferenceCampaignPhase.COMPLETE);
        campaign.finishedDay(0);
        source.field().mutableCompletedCampaigns().add(campaign);

        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateField.State restored = ReferenceGrayboxStateField.read(reference.get("field"));

        assertSame(restored.campaigns().get(campaign.id()), restored.completedCampaigns().getFirst());
    }

    @Test
    void restoredPostContinuesDailyUpkeepWithoutReplayingItsBuild() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        ReferenceFieldCampaign campaign = source.field().createCampaign(source, ReferenceCampaignKind.CONTAINMENT, 1, Set.of(),
                "cell", null, 10, 10, "recovery");
        ReferenceFieldPost post = source.field().startPost(source, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 10, 10, 1,
                Set.of(1), Map.of(1, 2.0d), supplies(), Map.of());
        for (int day = 1; day <= 3; day++) {
            source.day(day);
            source.field().step(source);
        }

        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
        ReferenceGrayboxStateField.State field = ReferenceGrayboxStateField.read(reference.get("field"));
        ReferenceWorld restored = new ReferenceWorld(root.config());
        restored.populationRng().restore(root.populationRng());
        restored.marketWorld().settlements().clear();
        ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), restored.profile(), restored.populationRng()).values()
                .forEach(restored.marketWorld()::addSettlement);
        field.applyTo(restored.field());

        source.day(4);
        source.field().step(source);
        restored.day(4);
        restored.field().step(restored);

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateField.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateField.capture(restored)));
        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateSettlements.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateSettlements.capture(restored)));
        assertEquals(ReferenceFieldPostStatus.ACTIVE, post.status());
    }

    @Test
    void restoresAPhysicallyDestroyedFieldLinkWithoutSilentlyRestoringItsIntegrity() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        ReferenceFieldCampaign campaign = source.field().createCampaign(source, ReferenceCampaignKind.CONTAINMENT, 1, Set.of(),
                "cell", null, 10, 10, "destroyed-line recovery");
        ReferenceFieldPost first = source.field().startPost(source, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 10, 10, 1,
                Set.of(1), Map.of(1, 1.0d), supplies(), Map.of());
        ReferenceFieldPost second = source.field().startPost(source, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 15, 10, 1,
                Set.of(1), Map.of(1, 1.0d), supplies(), Map.of());
        ReferenceFieldLink link = source.field().startLink(source, campaign.id(), ReferenceFieldLinkKind.SUPPLY_CORRIDOR, first.id(), second.id());
        link.integrity(0.0d);
        link.status("destroyed");

        ReferenceWorld restored = ReferenceGrayboxWorldHydrator.restore(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));

        ReferenceFieldLink restoredLink = restored.field().links().get(link.id());
        assertEquals(0.0d, restoredLink.integrity());
        assertEquals("destroyed", restoredLink.status());
        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateField.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateField.capture(restored)));
    }

    private static Map<ReferenceResource, Double> supplies() {
        EnumMap<ReferenceResource, Double> values = new EnumMap<>(ReferenceResource.class);
        values.put(ReferenceResource.FOOD, 10.0d);
        values.put(ReferenceResource.MEDICINE, 2.0d);
        return values;
    }
}
