package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void everySettlementPublishesOneTypedWarehouseWithAllCanonicalResources() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(grayboxWorld());

        assertEquals(snapshot.settlements().size(), snapshot.warehouses().size(),
                "the physical economy must not omit a settlement warehouse from the immutable source projection");
        for (ReferenceGrayboxSnapshot.Warehouse warehouse : snapshot.warehouses()) {
            assertEquals("settlement:" + warehouse.settlementId() + ":warehouse", warehouse.id());
            assertEquals(ReferenceGrayboxLayout.facility(snapshot.settlements().stream()
                    .filter(settlement -> settlement.id() == warehouse.settlementId()).findFirst().orElseThrow().rectangle(), "warehouse"),
                    warehouse.rectangle(), "the physical storage bay must retain the functional warehouse footprint");
            assertEquals(Set.of(ReferenceResource.values()).stream().map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet()), warehouse.stockpiles().stream()
                    .map(ReferenceGrayboxSnapshot.Stockpile::resource).collect(java.util.stream.Collectors.toSet()),
                    "each container family must expose every source resource without inventing a material-only type");
            assertTrue(warehouse.stockpiles().stream().allMatch(stockpile -> Double.isFinite(stockpile.quantity()) && stockpile.quantity() >= 0.0d));
        }
    }

    @Test
    void tradeRoutesHaveAContinuousInspectableGrayboxCorridorAndBoundedDamageSlots() {
        ReferenceGrayboxLayout.Point start = new ReferenceGrayboxLayout.Point(10, 10);
        ReferenceGrayboxLayout.Point end = new ReferenceGrayboxLayout.Point(74, 31);

        List<ReferenceGrayboxLayout.Point> corridor = ReferenceGrayboxLayout.routeLine(start, end);
        List<ReferenceGrayboxLayout.Point> slots = ReferenceGrayboxLayout.routeSlots(start, end);

        assertEquals(start, corridor.getFirst());
        assertEquals(end, corridor.getLast());
        assertTrue(corridor.size() > slots.size(), "the visible route must be a continuous corridor, not its sparse interaction points");
        assertEquals(corridor.size(), corridor.stream().distinct().count(),
                "the raster must never repeat a physical corridor block");
        for (int index = 1; index < corridor.size(); index++) {
            ReferenceGrayboxLayout.Point previous = corridor.get(index - 1);
            ReferenceGrayboxLayout.Point current = corridor.get(index);
            assertTrue(Math.abs(current.x() - previous.x()) <= 1 && Math.abs(current.z() - previous.z()) <= 1,
                    "each route block must touch its predecessor so the corridor has no visual gaps");
        }
        assertTrue(slots.stream().allMatch(corridor::contains),
                "each breakable route fact must stay on the visible continuous corridor");
    }

    @Test
    void nonSpatialMarketCivicAndHiveOwnersRemainReadableSourceDashboardFacts() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(grayboxWorld());

        assertTrue(snapshot.readouts().stream().anyMatch(item -> item.category().equals("CIVIC") && item.text().contains("doctrine=")));
        assertTrue(snapshot.readouts().stream().anyMatch(item -> item.category().equals("MARKET") && item.text().contains("stock=")));
        assertTrue(snapshot.readouts().stream().anyMatch(item -> item.category().equals("COMPANY") && item.text().contains("sector=")));
        assertTrue(snapshot.readouts().stream().anyMatch(item -> item.category().equals("HIVE") && item.text().contains("genome=")));
        assertTrue(snapshot.readouts().stream().allMatch(item -> item.position().x() >= ReferenceGrayboxLayout.MIN_X
                && item.position().x() < ReferenceGrayboxLayout.MAX_X_EXCLUSIVE && item.position().z() >= ReferenceGrayboxLayout.MIN_Z
                && item.position().z() < ReferenceGrayboxLayout.MAX_Z_EXCLUSIVE));
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

    @Test
    void operationCargoSlotsStayInsideTheirCellAndClearItsTerrainMarker() {
        ReferenceGrayboxLayout.Point anchor = ReferenceGrayboxLayout.centre(10, 10);
        ReferenceGrayboxLayout.Rectangle cell = ReferenceGrayboxLayout.cell(10, 10);
        int terrainMarkerX = cell.x() + 1;
        int terrainMarkerZ = cell.z() + 1;

        for (int ordinal = 0; ordinal < 16; ordinal++) {
            ReferenceGrayboxLayout.Rectangle pallet = ReferenceGrayboxLayout.cargo(anchor, ordinal);
            assertTrue(pallet.x() >= cell.x() && pallet.z() >= cell.z()
                    && pallet.x() + pallet.width() <= cell.x() + cell.width()
                    && pallet.z() + pallet.depth() <= cell.z() + cell.depth(), "cargo pallet " + ordinal + " fits its cell");
            assertTrue(terrainMarkerX < pallet.x() || terrainMarkerX >= pallet.x() + pallet.width()
                    || terrainMarkerZ < pallet.z() || terrainMarkerZ >= pallet.z() + pallet.depth(),
                    "cargo pallet " + ordinal + " must not claim the terrain marker");
        }
    }

    @Test
    void concurrentActivitySlotsKeepTheFirstProcessAtItsCanonicalPointAndSeparateTheRest() {
        ReferenceGrayboxLayout.Point anchor = ReferenceGrayboxLayout.centre(10, 10);
        ReferenceGrayboxLayout.Rectangle cell = ReferenceGrayboxLayout.cell(10, 10);

        List<ReferenceGrayboxLayout.Point> slots = java.util.stream.IntStream.range(0, 49)
                .mapToObj(ordinal -> ReferenceGrayboxLayout.activitySlot(anchor, ordinal)).toList();

        assertEquals(anchor, slots.getFirst());
        assertEquals(49, slots.stream().distinct().count());
        assertTrue(slots.stream().allMatch(slot -> slot.x() >= cell.x() && slot.z() >= cell.z()
                && slot.x() < cell.x() + cell.width() && slot.z() < cell.z() + cell.depth()));
    }

    @Test
    void sourceOwnedOperationsAndCampaignsRemainReadableWithTheirExactPeopleAndPhysicalFacts() {
        ReferenceWorld world = grayboxWorld();
        ReferenceOperation operation = world.operations().launchHuman(world, ReferenceOperationKind.RECON, 1,
                ReferenceTargetRef.cell(10, 10));
        assertNotNull(operation);

        ReferenceSettlement fieldLeader = world.settlements().get(2);
        ReferenceFieldCampaign fieldCampaign = world.field().createCampaign(world, ReferenceCampaignKind.CONTAINMENT,
                fieldLeader.id(), Set.of(), "cell", null, 12, 12, "projection coverage");
        assertNotNull(fieldCampaign);
        List<String> firstGarrison = List.copyOf(fieldLeader.residents().availableIds().subList(0, 2));
        fieldLeader.assignPeopleToFieldPost(firstGarrison, world.field().nextPostId());
        ReferenceFieldPost firstPost = startBuildablePost(world, fieldCampaign, fieldLeader, firstGarrison, null);

        ReferenceSettlement fieldSupport = world.settlements().get(3);
        List<String> secondGarrison = List.copyOf(fieldSupport.residents().availableIds().subList(0, 2));
        fieldSupport.assignPeopleToFieldPost(secondGarrison, world.field().nextPostId());
        ReferenceFieldPost secondPost = startBuildablePost(world, fieldCampaign, fieldSupport, secondGarrison, firstPost);
        ReferenceFieldLink fieldLink = world.field().startLink(world, fieldCampaign.id(), ReferenceFieldLinkKind.SUPPLY_CORRIDOR,
                firstPost.id(), secondPost.id());
        assertNotNull(fieldLink);

        ReferenceSettlement frontLeader = world.settlements().get(4);
        List<String> frontPeople = frontLeader.deployPeople(1_000_001,
                Map.of(ReferenceHumanUnitKind.LINE.id(), 3)).get(ReferenceHumanUnitKind.LINE.id());
        String sector = world.v2().sectors().keySet().iterator().next();
        ReferenceFrontCampaign frontCampaign = new ReferenceFrontCampaign(1, ReferenceFrontCampaignKind.CORDON, frontLeader.id(),
                List.of(frontLeader.id()), sector, world.day(), ReferenceFrontPhase.CORDON,
                Map.of(frontLeader.id(), 3.0d), Map.of(frontLeader.id(), frontPeople),
                Map.of(frontLeader.id(), Map.of(ReferenceHumanUnitKind.LINE, 3.0d)), "projection coverage");
        frontCampaign.risk(.4d);
        world.v2().mutableFrontCampaigns().put(frontCampaign.id(), frontCampaign);
        world.assertProfileInvariants();

        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(world);
        Map<String, ReferenceGrayboxSnapshot.Activity> activities = snapshot.activities().stream()
                .collect(java.util.stream.Collectors.toMap(ReferenceGrayboxSnapshot.Activity::id, item -> item));

        assertActivity(activities.get("operation:" + operation.id()), "operation", operation.kind().id(), operation.status().id(),
                operation.personnel(), "activity.operation." + operation.status().id());
        assertActivity(activities.get("field-campaign:" + fieldCampaign.id()), "field_campaign", fieldCampaign.kind().id(),
                fieldCampaign.phase().id(), fieldCampaign.expectedPersonnel(), "activity.field_campaign." + fieldCampaign.phase().id());
        assertActivity(activities.get("front-campaign:" + frontCampaign.id()), "front_campaign", frontCampaign.kind().id(),
                frontCampaign.phase().id(), frontCampaign.personnel(), "activity.front_campaign." + frontCampaign.phase().id());

        assertEquals(Set.of(firstPost.id(), secondPost.id()), snapshot.fieldPosts().stream()
                .filter(post -> post.campaignId() == fieldCampaign.id()).map(ReferenceGrayboxSnapshot.FieldPost::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(snapshot.fieldLinks().stream().anyMatch(link -> link.id() == fieldLink.id()
                && link.campaignId() == fieldCampaign.id() && link.postA() == firstPost.id() && link.postB() == secondPost.id()
                && link.kind().equals(ReferenceFieldLinkKind.SUPPLY_CORRIDOR.id())));
        assertTrue(snapshot.cargoes().stream().anyMatch(cargo -> cargo.id().equals("field_post:" + firstPost.id() + ":cargo:food")
                && cargo.quantity() == firstPost.stock(ReferenceResource.FOOD)));
        assertTrue(snapshot.interactions().stream().anyMatch(item -> item.subjectId().equals("field_post:" + firstPost.id())
                && item.kind().equals("field_post_damaged")));
        assertTrue(snapshot.interactions().stream().anyMatch(item -> item.subjectId().equals("field_link:" + fieldLink.id())
                && item.kind().equals("field_link_damaged")));

        assertResidentsAt(snapshot, operation.residentIdsBySettlement().get(1), "operation", operation.id());
        assertResidentsAt(snapshot, firstGarrison, "field_post", firstPost.id());
        assertResidentsAt(snapshot, secondGarrison, "field_post", secondPost.id());
        assertResidentsAt(snapshot, frontPeople, "operation", 1_000_000 + frontCampaign.id());
    }

    private static void assertActivity(ReferenceGrayboxSnapshot.Activity activity, String family, String kind, String phase,
                                       double personnel, String colour) {
        assertNotNull(activity);
        assertEquals(family, activity.family());
        assertEquals(kind, activity.kind());
        assertEquals(phase, activity.phase());
        assertEquals(personnel, activity.personnel());
        assertEquals(colour, activity.colour());
    }

    private static void assertResidentsAt(ReferenceGrayboxSnapshot snapshot, List<String> residentIds, String location, int locationId) {
        for (String residentId : residentIds) {
            assertTrue(snapshot.residents().stream().anyMatch(resident -> resident.id().equals(residentId)
                    && resident.location().equals(location) && Integer.valueOf(locationId).equals(resident.locationId())), residentId);
        }
    }

    private static ReferenceFieldPost startBuildablePost(ReferenceWorld world, ReferenceFieldCampaign campaign,
                                                          ReferenceSettlement settlement, List<String> garrison,
                                                          ReferenceFieldPost firstPost) {
        for (int y = 1; y < world.config().height() - 1; y++) for (int x = 1; x < world.config().width() - 1; x++) {
            if (firstPost != null) {
                double distance = Math.hypot(firstPost.x() - x, firstPost.y() - y);
                if (distance < ReferenceFieldRules.MINIMUM_POST_SPACING
                        || distance > ReferenceFieldRules.linkMaximumLength(ReferenceFieldLinkKind.SUPPLY_CORRIDOR)) continue;
            }
            ReferenceFieldPost post = world.field().startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, x, y,
                    settlement.id(), Set.of(settlement.id()), Map.of(settlement.id(), (double) garrison.size()),
                    Map.of(ReferenceResource.FOOD, 2.0d), Map.of(settlement.id(), garrison));
            if (post != null) return post;
        }
        throw new AssertionError("graybox test world has no buildable field-post location");
    }

    private static ReferenceWorld grayboxWorld() {
        return new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
    }
}
