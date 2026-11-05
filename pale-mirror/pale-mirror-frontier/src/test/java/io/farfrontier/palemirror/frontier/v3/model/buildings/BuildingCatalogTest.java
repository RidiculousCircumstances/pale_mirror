package io.farfrontier.palemirror.frontier.v3.model.buildings;

import static org.junit.jupiter.api.Assertions.*;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class BuildingCatalogTest {
    @Test
    void currentDoorObservationAndPendingWriteOverridePristineContentWithoutMovingTheWorkplace() {
        var state =
                FrontierWorldState.initial(
                        FrontierBootstrapper.create(
                                new WorldId("frontier:building-intervention"), 139));
        var settlement = state.bootstrap().settlements().getFirst();
        var bakery =
                settlement.structures().stream()
                        .filter(s -> s.kind() == StructureKind.WORKSHOP)
                        .findFirst()
                        .orElseThrow();
        var entrance = BuildingSpatialPlan.of(bakery).primaryEntrance();
        var threshold = entrance.thresholdSurface();
        var passages =
                List.of(
                        new KnownPedestrianRouteKnowledge.Passage(
                                bakery, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS));
        var before =
                KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages)
                        .forProfile(UnitNavigationProfile.VILLAGER)
                        .geometry();
        assertFalse(before.blocked(threshold));
        var point = threshold.support().offset(0, 1, 0);
        state =
                new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(
                                state.navigationGeometry().revision(),
                                Map.of(
                                        point,
                                        io.farfrontier.palemirror.frontier.v3.model.geometry
                                                .NavigationObservation.known(
                                                io.farfrontier.palemirror.frontier.v3.model.geometry
                                                        .NavigationCell.SOLID)))
                        .apply(
                                state,
                                io.farfrontier.palemirror.frontier.v3.model.geometry
                                        .NavigationGeometryObserved.OWNER);
        var closed =
                KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages)
                        .forProfile(UnitNavigationProfile.VILLAGER)
                        .geometry();
        assertTrue(closed.blocked(threshold));
        assertFalse(before.blocked(threshold));
        state =
                new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(
                                state.navigationGeometry().revision(),
                                Map.of(
                                        point,
                                        io.farfrontier.palemirror.frontier.v3.model.geometry
                                                .NavigationObservation.unavailable(
                                                io.farfrontier.palemirror.frontier.v3.model.geometry
                                                        .NavigationObservation.Kind.WRITE_PENDING)))
                        .apply(
                                state,
                                io.farfrontier.palemirror.frontier.v3.model.geometry
                                        .NavigationGeometryObserved.OWNER);
        var pending =
                KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages)
                        .forProfile(UnitNavigationProfile.VILLAGER)
                        .geometry();
        assertFalse(pending.clearanceKnown(threshold));
        state =
                new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(
                                state.navigationGeometry().revision(),
                                Map.of(
                                        point,
                                        io.farfrontier.palemirror.frontier.v3.model.geometry
                                                .NavigationObservation.known(
                                                io.farfrontier.palemirror.frontier.v3.model.geometry
                                                        .NavigationCell.AIR)))
                        .apply(
                                state,
                                io.farfrontier.palemirror.frontier.v3.model.geometry
                                        .NavigationGeometryObserved.OWNER);
        assertFalse(
                KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages)
                        .forProfile(UnitNavigationProfile.VILLAGER)
                        .geometry()
                        .blocked(threshold));
    }

    @Test
    void actualBakerySupportsWholeProductionApproachOnBothLevelsBeforeFirstVisit() {
        var state =
                FrontierWorldState.initial(
                        FrontierBootstrapper.create(
                                new WorldId("frontier:authored-building"), 139));
        var settlement = state.bootstrap().settlements().getFirst();
        var bakery =
                settlement.structures().stream()
                        .filter(s -> s.kind() == StructureKind.WORKSHOP)
                        .findFirst()
                        .orElseThrow();
        var plan = BuildingSpatialPlan.of(bakery);
        var work = SettlementWorkshopServicePort.forWorkshop(bakery);
        var knowledge =
                KnownPedestrianRouteKnowledge.forSettlement(
                                state,
                                settlement.id(),
                                List.of(
                                        new KnownPedestrianRouteKnowledge.Passage(
                                                bakery,
                                                KnownPedestrianRouteKnowledge.Passage.Reach
                                                        .STATIONS)))
                        .forProfile(UnitNavigationProfile.VILLAGER);
        assertNotEquals(work.inputStation().y(), work.workStation().y());
        var actor = settlement.residents().getFirst().id();
        for (var goal : List.of(work.inputStation(), work.workStation())) {
            var order =
                    new MovementOrder(
                            actor,
                            actor,
                            0,
                            1,
                            List.of(goal),
                            TraversalCapability.PEDESTRIAN,
                            MovementOrder.ArrivalPolicy.EXACT_STATION);
            var route = knowledge.path(work.exteriorApproach(), order);
            assertEquals(goal, route.getLast());
        }
        assertEquals(2, plan.entrances().size());
        assertEquals(1, plan.publicEntrances().size());
        assertTrue(
                FrontierGrayboxPlan.compileInitialSettlementFabric(state.bootstrap())
                        .cells()
                        .values()
                        .stream()
                        .anyMatch(
                                c ->
                                        c.ownerId().equals(bakery.id())
                                                && c.material()
                                                        instanceof StructureMaterial.Authored));
    }

    @Test
    void exactSelectionRejectsForeignContentAndTransformRotatesMetadataAndStateTogether() {
        var catalog = BuildingCatalog.standard();
        var ref = catalog.select(BuildingCatalog.MEDIEVAL_SPRUCE, StructureKind.WORKSHOP);
        var definition = catalog.require(ref, StructureKind.WORKSHOP);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        catalog.require(
                                new BuildingPlanRef(ref.style(), ref.template(), "0".repeat(64)),
                                StructureKind.WORKSHOP));
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.select(new BuildingStyleId("test:absent"), StructureKind.WORKSHOP));
        for (var facing : FacilityFacing.values()) {
            var placement = definition.placement(new BlockPosition(10, 64, -15), facing);
            for (var p :
                    List.of(
                            definition.origin(),
                            definition.primaryWorkplaceDefinition().worker(),
                            definition.fabric().min()))
                assertEquals(p, placement.local(placement.world(p)));
        }
    }
}
