package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxProjectionTest {
    @Test
    void grayboxProjectionCoversEveryCellSettlementAndNamedResidentWithoutCohorts() {
        ReferenceWorld world = grayboxWorld();

        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(world);

        assertEquals("graybox_1_40", snapshot.profileId());
        assertTrue(snapshot.stateRevision().matches("[0-9a-f]{64}"));
        assertEquals(1_024, snapshot.bounds().width());
        assertEquals(704, snapshot.bounds().depth());
        assertEquals(2_816, snapshot.cells().size());
        assertEquals(12, snapshot.settlements().size());
        assertEquals(84, snapshot.facilities().size());
        assertEquals(ReferenceGrayboxLayout.cell(0, 0), snapshot.cells().getFirst().rectangle());
        assertEquals(ReferenceGrayboxLayout.cell(63, 43), snapshot.cells().getLast().rectangle());

        List<String> sourceResidentIds = world.settlements().values().stream()
                .flatMap(settlement -> settlement.residents().livingIds().stream()).sorted().toList();
        List<String> projectedResidentIds = snapshot.residents().stream()
                .map(ReferenceGrayboxSnapshot.Resident::id).sorted().toList();
        assertEquals(sourceResidentIds, projectedResidentIds);
        assertEquals(sourceResidentIds.size(), snapshot.residents().size());
        assertTrue(snapshot.residents().stream().allMatch(resident -> resident.id().startsWith("resident:")));
    }

    @Test
    void functionalBuildingsRemainExplicitRectanglesWithTypeOnlyColourTokens() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(grayboxWorld());

        List<ReferenceGrayboxSnapshot.Facility> firstSettlement = snapshot.facilities().stream()
                .filter(facility -> facility.settlementId() == 1).toList();

        assertEquals(Set.of("civic_hall", "workshop", "armory", "clinic", "warehouse", "housing", "fortification"),
                firstSettlement.stream().map(ReferenceGrayboxSnapshot.Facility::kind).collect(java.util.stream.Collectors.toSet()));
        assertTrue(firstSettlement.stream().allMatch(facility -> facility.colour().equals("facility." + facility.kind())));
        assertEquals(48, snapshot.settlements().getFirst().rectangle().width());
        assertEquals(48, snapshot.settlements().getFirst().rectangle().depth());
    }

    @Test
    void discreteBioformDescriptorsHaveStableDerivedIdentityAndRejectFractionalCounts() {
        ReferenceWorld world = grayboxWorld();
        ReferenceHiveOrgan brood = world.infection().createOrgan(10, 10, 100.0d, null, null, ReferenceOrganKind.BROOD_SAC);
        assertThrows(IllegalArgumentException.class, () -> world.infection().launchBioform(brood, ReferenceBioformKind.RAIDER,
                12, 11, -1, Map.of(ReferenceBioformKind.RAIDER, 1.5d)));
        world.infection().launchBioform(brood, ReferenceBioformKind.RAIDER, 12, 11, -1,
                Map.of(ReferenceBioformKind.BREAKER, 1.0d, ReferenceBioformKind.RAIDER, 2.0d));

        List<ReferenceGrayboxSnapshot.Bioform> first = ReferenceGrayboxProjection.from(world).bioforms();
        List<ReferenceGrayboxSnapshot.Bioform> second = ReferenceGrayboxProjection.from(world).bioforms();

        assertEquals(first, second);
        assertEquals(List.of("bioform:1:breaker:1", "bioform:1:raider:1", "bioform:1:raider:2"),
                first.stream().map(ReferenceGrayboxSnapshot.Bioform::id).toList());
        assertTrue(first.stream().allMatch(bioform -> bioform.colour().equals("bioform." + bioform.kind())));
    }

    @Test
    void sourceScaleOrLegacyWorldFailsClosedInsteadOfApplyingAHiddenMultiplier() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        assertThrows(IllegalStateException.class, () -> ReferenceGrayboxProjection.from(source));
    }

    @Test
    void stateRevisionChangesWhenTheCanonicalSourceStateChanges() {
        ReferenceWorld world = grayboxWorld();
        String before = ReferenceGrayboxProjection.from(world).stateRevision();

        world.tick();

        assertTrue(!before.equals(ReferenceGrayboxProjection.from(world).stateRevision()));
    }

    private static ReferenceWorld grayboxWorld() {
        return new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
    }
}
