package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationScaleProfileTest {
    @Test void declaredHundredSettlementsUseIrregularDistancesAndLocalRoads() {
        var rules = FrontierRulesets.installed("frontier-v3-simulation-scale-r31");
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:scale-bootstrap"), 126L, rules);
        assertEquals(100, bootstrap.settlements().size());
        assertEquals(100_000, bootstrap.bounds().width()); assertEquals(100_000, bootstrap.bounds().depth());
        assertTrue(bootstrap.residentCount() >= 2_000 && bootstrap.residentCount() <= 4_000);
        assertEquals(100, bootstrap.settlements().stream().map(Settlement::displayName).distinct().count());
        var population = HumanPopulation.bootstrap(bootstrap);
        assertEquals(bootstrap.residentCount(), population.residents().size());
        var roads = FrontierRouteNetwork.surfaceCells(bootstrap);
        assertTrue(roads.size() < 100_000, "local content must not pre-expand distant paths into millions of road blocks");
        for (var settlement : bootstrap.settlements()) {
            var route = FrontierRouteNetwork.settlementWaypoints(bootstrap, settlement.id());
            assertDoesNotThrow(() -> FrontierRouteNetwork.validateSettlementWaypoints(bootstrap, settlement.id(), route));
            for (var position : route) assertTrue(FrontierRouteNetwork.isSurfaceCell(bootstrap, RouteTopology.initial(), position));
            assertTrue(route.stream().allMatch(p -> Math.abs(p.x() - settlement.anchor().x()) < 100
                    && Math.abs(p.z() - settlement.anchor().z()) < 100));
        }
    }
    @Test void completeScaleStateHydratesDeclaredPopulationDepositsAndStorage() {
        var rules = FrontierRulesets.installed("frontier-v3-simulation-scale-r31");
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:scale-state"), 126L, rules);
        var state = FrontierWorldState.initial(bootstrap);
        assertEquals(100, state.bootstrap().settlements().size());
        assertEquals(bootstrap.residentCount(), state.humanPopulation().residents().size());
        assertTrue(state.extractionSites().deposits().size() > 64, "the former registry bound cannot serve this content");
        assertEquals(100, state.companies().goodsTrade().participants().participants().size());
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void baselineRetainsCurrentTwelveSettlementContentAndPhysicalRoadAgreement() {
        var rules = FrontierRulesets.installed("frontier-v3-simulation-baseline-r31");
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:baseline-bootstrap"), 126L, rules);
        assertEquals(12, bootstrap.settlements().size());
        var roads = FrontierRouteNetwork.surfaceCells(bootstrap);
        for (var position : roads) {
            assertTrue(FrontierRouteNetwork.isSurfaceCell(bootstrap, RouteTopology.initial(), position));
            assertTrue(FrontierRouteNetwork.surfaceAt(bootstrap, RouteTopology.initial(), position.x(), position.z()).isPresent());
        }
    }
}
