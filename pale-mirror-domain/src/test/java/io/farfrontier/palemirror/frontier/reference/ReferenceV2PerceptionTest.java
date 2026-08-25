package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceV2PerceptionTest {
    @Test
    void activeObservationPostIsARealLocalSensorRatherThanGlobalKnowledge() {
        ReferenceWorld world = sourceWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceV2State v2 = world.v2();
        ReferenceV2OperationalSector target = distantSector(v2, settlement.x(), settlement.y(), 14.0d);
        assertEquals("0:0", target.key());
        assertNull(v2.humanPerceptions().get(settlement.id()).belief(target.key()));

        ReferenceFieldPost post = new ReferenceFieldPost(91, ReferenceFieldPostKind.OBSERVATION,
                centerX(target), centerY(target), 1, settlement.id(), Set.of(settlement.id()), Map.of(), Map.of(),
                world.profile().personScale(), world.day(), world.day());
        post.status(ReferenceFieldPostStatus.ACTIVE);
        world.field().mutablePosts().put(post.id(), post);

        v2.observe(world);

        ReferenceV2Belief belief = v2.humanPerceptions().get(settlement.id()).belief(target.key());
        assertNotNull(belief);
        assertEquals(ReferenceObservationSource.POST, belief.source());
        assertEquals(.94d, belief.confidence());
        ReferenceV2OperationalSector outsidePostRadius = v2.sectors().values().stream()
                .filter(item -> Math.hypot(centerX(item) - post.x(), centerY(item) - post.y()) > 12.0d)
                .filter(item -> Math.hypot(centerX(item) - settlement.x(), centerY(item) - settlement.y()) > 8.0d)
                .findFirst().orElseThrow();
        assertNull(v2.humanPerceptions().get(settlement.id()).belief(outsidePostRadius.key()));
    }

    @Test
    void hiveViewExposesOnlyPerceivedCellsAndSectorsButNeverHidesItsOwnBioforms() {
        ReferenceWorld world = sourceWorld();
        ReferenceV2State v2 = world.v2();
        ReferenceV2OperationalSector tissue = v2.sectors().get("0:0");
        ReferenceGridPosition tissueCell = tissue.cells().getFirst();
        for (ReferenceGridPosition cell : tissue.cells()) world.infection().infectionAt(cell.x(), cell.y(), .90d);
        ReferenceV2OperationalSector swarmSector = v2.sectors().get("15:10");
        ReferenceGridPosition swarmCell = swarmSector.cells().getFirst();
        world.infection().swarms.add(new ReferenceSwarm(55, swarmCell.x(), swarmCell.y(), 20.0d, -1, .7d,
                ReferenceBioformKind.RAIDER, Map.of(ReferenceBioformKind.RAIDER, 1.0d), ReferenceFormationPhase.MAIN_ACTION,
                1.0d, null, swarmCell.x(), swarmCell.y(), false));
        v2.refreshTerritory(world);
        v2.observe(world);

        ReferenceHiveWorldView perceived = v2.perceivedHiveView(world);

        assertNotNull(perceived.cell(tissueCell.x(), tissueCell.y()));
        assertNotNull(perceived.cell(swarmCell.x(), swarmCell.y()));
        assertEquals(32, perceived.cells().size());
        assertEquals(List.of("0:0", "15:10"), perceived.sectors().stream().map(item -> item.x() + ":" + item.y()).toList());
        assertEquals(1, perceived.swarms().size());
        assertNull(perceived.cell(32, 20));
    }

    @Test
    void reconnaissanceTradeAndSignedCharterRevealOnlyTheirActualSectors() {
        ReferenceWorld world = sourceWorld();
        ReferenceV2State v2 = world.v2();
        ReferenceSettlement scoutOwner = world.settlements().get(2);
        ReferenceV2OperationalSector scoutSector = distantSector(v2, scoutOwner.x(), scoutOwner.y(), 12.0d);
        ReferenceOperation scout = new ReferenceOperation(72, ReferenceAgentKind.SETTLEMENT, ReferenceOperationKind.RECON,
                new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, scoutOwner.id()),
                ReferenceTargetRef.cell(centerX(scoutSector), centerY(scoutSector)), world.day(), centerX(scoutSector), centerY(scoutSector),
                scoutOwner.x(), scoutOwner.y());
        scout.contributors(Map.of(scoutOwner.id(), Map.of()));
        world.operations().mutableActive().add(scout);
        v2.observe(world);
        assertEquals(ReferenceObservationSource.SCOUT, v2.humanPerceptions().get(scoutOwner.id()).belief(scoutSector.key()).source());

        ReferenceRoute route = world.trade().routes().getFirst();
        ReferenceV2OperationalSector routeSector = distantSector(v2, world.settlements().get(route.a()).x(), world.settlements().get(route.a()).y(), 12.0d);
        route.sectorKeys(List.of(routeSector.key()));
        world.day(1);
        world.trade().appendHistory(List.of(new ReferenceTradeRecord(0, route.a(), route.b(), ReferenceResource.FOOD,
                1.0d, 1.0d, 1.0d, List.of(route.a(), route.b()))));
        v2.observe(world);
        assertEquals(ReferenceObservationSource.ROUTE, v2.humanPerceptions().get(route.a()).belief(routeSector.key()).source());

        ReferenceCoalitionCharter charter = new ReferenceCoalitionCharter(9, route.a(), List.of(route.a(), route.b()), scoutSector.key(),
                world.day(), world.day() + 2, Map.of(), Map.of(), Map.of());
        charter.status("active");
        v2.mutableCharters().put(charter.id(), charter);
        v2.humanPerceptions().get(route.a()).belief(new ReferenceV2Belief(scoutSector.key(), world.day(), .91d,
                ReferenceObservationSource.SCOUT, .3d, 0.0d, .0d, 0.0d, true));
        v2.observe(world);
        ReferenceV2Belief shared = v2.humanPerceptions().get(route.b()).belief(scoutSector.key());
        assertEquals(ReferenceObservationSource.ROUTE, shared.source());
        assertEquals(.91d, shared.confidence());
        assertTrue(shared.chrysalis());
    }

    private static ReferenceWorld sourceWorld() {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static ReferenceV2OperationalSector distantSector(ReferenceV2State v2, double x, double y, double minimumDistance) {
        return v2.sectors().values().stream().filter(item -> Math.hypot(centerX(item) - x, centerY(item) - y) > minimumDistance)
                .findFirst().orElseThrow();
    }

    private static int centerX(ReferenceV2OperationalSector sector) { return sector.x() * ReferenceV2Rules.SECTOR_SIZE + 2; }
    private static int centerY(ReferenceV2OperationalSector sector) { return sector.y() * ReferenceV2Rules.SECTOR_SIZE + 2; }
}
