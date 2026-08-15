package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteContractTest {
    private static final WorldObjectId ORIGIN = new WorldObjectId("pale_mirror:test_origin");
    private static final WorldObjectId DESTINATION = new WorldObjectId("pale_mirror:test_destination");

    @Test
    void authoredVanillaTopologyRemainsCurrentUntilPhysicalEvidenceBlocksIt() {
        RouteContract route = RouteContract.authoredVanillaMinecart(
                new WorldObjectId("pale_mirror:test_baseline"), ORIGIN, DESTINATION,
                ResourceKind.IRON, 18, 8, 24, 0, "authored-topology:test");

        assertEquals(RouteContractStatus.VALIDATED, route.status());
        assertEquals(RouteFreshness.CURRENT, route.freshness(10_000));
        assertEquals(18, route.transferableCapacity(10_000));

        route.validate(0, 10_001, "loaded-graph:broken");
        assertEquals(RouteContractStatus.BLOCKED, route.status());
        assertEquals(0, route.transferableCapacity(10_001));

        route.validate(18, 10_002, "loaded-graph:repaired");
        assertEquals(RouteFreshness.CURRENT, route.freshness(100_000));
        assertEquals(18, route.transferableCapacity(100_000));
    }

    @Test
    void createTraversalProofStillAgesAndExpires() {
        RouteContract route = new RouteContract(new WorldObjectId("pale_mirror:test_create"),
                ORIGIN, DESTINATION, RouteProvider.CREATE, ResourceKind.IRON,
                18, 8, 24, 18, 0, "create:test-run", RouteContractStatus.VALIDATED);

        assertEquals(RouteFreshness.CURRENT, route.freshness(8));
        assertEquals(RouteFreshness.STALE, route.freshness(9));
        assertEquals(9, route.transferableCapacity(9));
        assertEquals(RouteFreshness.EXPIRED, route.freshness(25));
        assertEquals(0, route.transferableCapacity(25));
    }
}
