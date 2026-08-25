package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStructureObservationTest {
    @Test
    void projectionMakesEverySupportedMutableOwnerAnExplicitNonOverlappingPhysicalSlot() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceOperation operation = new ReferenceOperation(991, ReferenceAgentKind.SETTLEMENT, ReferenceOperationKind.RECON,
                new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, settlement.id()), ReferenceTargetRef.cell(settlement.x(), settlement.y()),
                world.day(), settlement.x(), settlement.y(), settlement.x(), settlement.y());
        operation.cargo(Map.of(ReferenceResource.FOOD, 8.0d));
        world.operations().mutableActive().add(operation);

        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(world);
        assertTrue(snapshot.interactions().stream().anyMatch(item -> item.kind().equals("facility_damaged")));
        assertTrue(snapshot.interactions().stream().anyMatch(item -> item.kind().equals("site_damaged")));
        assertTrue(snapshot.interactions().stream().anyMatch(item -> item.kind().equals("route_damaged")));
        assertTrue(snapshot.interactions().stream().anyMatch(item -> item.kind().equals("operation_cargo_lost")));
        Set<String> occupied = new HashSet<>();
        for (ReferenceGrayboxSnapshot.Interaction interaction : snapshot.interactions()) {
            for (ReferenceGrayboxLayout.Point slot : interaction.slots()) {
                assertTrue(occupied.add(interaction.yOffset() + ":" + slot.x() + ":" + slot.z()),
                        "two independent interaction claims must never require the same block");
            }
        }
    }

    @Test
    void functionalFacilitySiteAndRouteFactsUseTheirExistingCanonicalOwners() {
        ReferenceWorld world = grayboxWorld();
        ReferenceGrayboxSnapshot before = ReferenceGrayboxProjection.from(world);
        ReferenceGrayboxSnapshot.Facility facility = before.facilities().stream()
                .filter(item -> item.kind().equals("workshop") && item.level() > 0.0d).findFirst().orElseThrow();
        ReferenceGrayboxSnapshot.ResourceSite site = before.resourceSites().getFirst();
        ReferenceGrayboxSnapshot.Route route = before.routes().stream().filter(item -> item.capacity() > 0.0d).findFirst().orElseThrow();

        ReferenceGrayboxObservationOutcome facilityOutcome = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:facility", before.stateRevision(), ReferenceGrayboxStructureObservation.Kind.FACILITY_DAMAGED,
                facility.id(), facility.level() / 2.0d));
        assertTrue(facilityOutcome.applied());
        ReferenceGrayboxSnapshot afterFacility = ReferenceGrayboxProjection.from(world);
        assertEquals(facility.level() / 2.0d, afterFacility.facilities().stream()
                .filter(item -> item.id().equals(facility.id())).findFirst().orElseThrow().level());

        ReferenceGrayboxObservationOutcome siteOutcome = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:site", afterFacility.stateRevision(), ReferenceGrayboxStructureObservation.Kind.SITE_DAMAGED,
                "site:" + site.id(), .25d));
        assertTrue(siteOutcome.applied());
        ReferenceGrayboxSnapshot afterSite = ReferenceGrayboxProjection.from(world);
        assertEquals(site.condition() - .25d, afterSite.resourceSites().stream()
                .filter(item -> item.id() == site.id()).findFirst().orElseThrow().condition());

        ReferenceGrayboxObservationOutcome routeOutcome = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:route", afterSite.stateRevision(), ReferenceGrayboxStructureObservation.Kind.ROUTE_DAMAGED,
                route.id(), route.capacity() / 3.0d));
        assertTrue(routeOutcome.applied());
        assertEquals(route.capacity() * 2.0d / 3.0d, ReferenceGrayboxProjection.from(world).routes().stream()
                .filter(item -> item.id().equals(route.id())).findFirst().orElseThrow().capacity());
        world.assertProfileInvariants();
    }

    @Test
    void organAndOperationCargoFactsRemoveOnlyTheClaimedCanonicalQuantity() {
        ReferenceWorld world = grayboxWorld();
        ReferenceHiveOrgan organ = world.infection().createOrgan(8, 8, 40.0d, 0.0d, null, ReferenceOrganKind.CORE);
        String organRevision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome organOutcome = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:organ", organRevision, ReferenceGrayboxStructureObservation.Kind.ORGAN_DAMAGED,
                "organ:" + organ.id(), organ.vitality()));
        assertTrue(organOutcome.applied());
        assertFalse(world.infection().organs().containsKey(organ.id()));
        assertTrue(world.infection().damageMemory().get("combat") > 0.0d);

        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceOperation operation = new ReferenceOperation(991, ReferenceAgentKind.SETTLEMENT, ReferenceOperationKind.RECON,
                new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, settlement.id()),
                ReferenceTargetRef.cell(settlement.x(), settlement.y()), world.day(), settlement.x(), settlement.y(), settlement.x(), settlement.y());
        operation.cargo(Map.of(ReferenceResource.FOOD, 8.0d, ReferenceResource.MEDICINE, 2.0d));
        world.operations().mutableActive().add(operation);
        String cargoRevision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome cargoOutcome = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:cargo", cargoRevision, ReferenceGrayboxStructureObservation.Kind.OPERATION_CARGO_LOST,
                "operation:991:cargo:food", 3.0d));
        assertTrue(cargoOutcome.applied());
        assertEquals(5.0d, operation.cargo().get(ReferenceResource.FOOD));
        assertEquals(2.0d, operation.cargo().get(ReferenceResource.MEDICINE));
        world.assertProfileInvariants();
    }

    @Test
    void staleOrNonfunctionalFactsDoNotInventAStateChange() {
        ReferenceWorld world = grayboxWorld();
        String revision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome nonfunctional = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:housing", revision, ReferenceGrayboxStructureObservation.Kind.FACILITY_DAMAGED,
                "settlement:1:facility:housing", 1.0d));
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT, nonfunctional.status());

        ReferenceGrayboxObservationOutcome stale = world.observe(new ReferenceGrayboxStructureObservation(
                1, "physical:stale", "0".repeat(64), ReferenceGrayboxStructureObservation.Kind.SITE_DAMAGED,
                "site:1", .1d));
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE, stale.status());
        world.assertProfileInvariants();
    }

    private static ReferenceWorld grayboxWorld() {
        return new ReferenceWorld(ReferenceWorldConfig.graybox1To40(281L));
    }
}
