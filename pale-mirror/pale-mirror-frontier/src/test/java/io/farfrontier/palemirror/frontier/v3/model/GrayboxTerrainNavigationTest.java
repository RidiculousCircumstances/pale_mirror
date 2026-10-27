package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GrayboxTerrainNavigationTest {
    private static FrontierWorldState world() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:terrain-navigation"), 126L,
                FrontierRulesets.installed("frontier-v3-simulation-terrain-r31")));
    }
    @Test void bridgeAndTunnelAreRealThreeDimensionalFactsWithLegalRoutes() {
        var state = world(); var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state).forProfile(UnitNavigationProfile.VILLAGER);
        var levels = knowledge.geometry().surfacesAt(-340, 14);
        assertTrue(levels.contains(SurfaceAnchor.at(-340, 63, 14)), "tunnel floor remains usable beneath the hill");
        assertTrue(levels.contains(SurfaceAnchor.at(-340, 71, 14)), "same column retains its independent upper contact");
        assertTrue(knowledge.clearStation(SurfaceAnchor.at(-340, 63, 14)));
        assertTrue(knowledge.clearStation(SurfaceAnchor.at(-340, 71, 14)));
        var tunnel = knowledge.plannedBetween(SurfaceAnchor.at(-370, 63, 14), SurfaceAnchor.at(-310, 63, 14));
        assertTrue(tunnel.stream().allMatch(surface -> surface.y() == 63));
        var ascent = knowledge.plannedBetween(SurfaceAnchor.at(-370, 63, 24), SurfaceAnchor.at(-340, 70, 24));
        assertTrue(ascent.stream().anyMatch(surface -> surface.y() > 63)); knowledge.requireRoute(ascent);
        var bridge = knowledge.plannedBetween(SurfaceAnchor.at(-92, 63, 14), SurfaceAnchor.at(-68, 63, 14));
        assertTrue(bridge.contains(SurfaceAnchor.at(-80, 63, 14)));
        var content = GrayboxTerrainPlan.forLayout(state.bootstrap().ruleset().worldLayout(), 63);
        assertEquals(GrayboxTerrainPlan.WATER, content.at(new BlockPosition(-80, 58, 14)).orElseThrow());
        assertEquals(GrayboxTerrainPlan.AIR, content.at(new BlockPosition(-80, 62, 14)).orElseThrow());
        for (var region : content.regions()) for (var position : content.in(region).keySet())
            assertTrue(state.extractionSites().geology().at(position).isEmpty(),
                    "natural fixture cannot overwrite a separately declared mineral source: " + position);
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void packAnimalUsesWidthCorrectBypassInsteadOfHumanPassage() {
        var state = world(); var base = KnownPedestrianRouteKnowledge.forFrontier(state);
        var human = base.forProfile(UnitNavigationProfile.VILLAGER); var donkey = base.forProfile(UnitNavigationProfile.DONKEY);
        var narrow = SurfaceAnchor.at(-75, 63, 145);
        assertTrue(human.clearStation(narrow)); assertFalse(donkey.clearStation(narrow));
        var start = SurfaceAnchor.at(-100, 63, 145); var goal = SurfaceAnchor.at(-60, 63, 145);
        var route = donkey.plannedBetween(start, goal);
        assertTrue(route.stream().anyMatch(surface -> surface.z() < 144 || surface.z() > 146));
        donkey.requireRoute(route);
    }
    @Test void observedBridgeObstructionAndUnknownKnowledgeOverrideTheInitialDeclaration() {
        var state = world(); var surface = SurfaceAnchor.at(-80, 63, 14); var body = surface.support().offset(0, 1, 0);
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(state).clearStation(surface));
        var blocked = new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(body,
                NavigationObservation.known(NavigationCell.SOLID))).apply(state, NavigationGeometryObserved.OWNER);
        assertFalse(KnownPedestrianRouteKnowledge.forFrontier(blocked).clearStation(surface));
        var unknown = new NavigationGeometryObserved(blocked.navigationGeometry().revision(), Map.of(body,
                NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING))).apply(blocked, NavigationGeometryObserved.OWNER);
        assertFalse(KnownPedestrianRouteKnowledge.forFrontier(unknown).clearStation(surface), "authored air cannot reopen unknown geometry");
        var restored = new NavigationGeometryObserved(unknown.navigationGeometry().revision(), Map.of(body,
                NavigationObservation.known(NavigationCell.AIR))).apply(unknown, NavigationGeometryObserved.OWNER);
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(restored).clearStation(surface));
    }
}
