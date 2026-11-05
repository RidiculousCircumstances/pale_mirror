package io.farfrontier.palemirror.frontier.v3.model.buildings;

import static org.junit.jupiter.api.Assertions.*;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class BuildingCatalogTest {
    @Test void authoredVisualDataRetainsItsOwnKindVersionAndExactPayloadAcrossWireEncoding() {
        var data = new AuthoredBlockData(AuthoredBlockData.Kind.MATERIAL_TEXTURES, 3465,
                "{textureData:{\"domum_ornamentum:plain_main\":\"minecraft:spruce_planks\"}}");
        var material = new StructureMaterial.Authored(new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState("domum_ornamentum:plain", Map.of()),
                "domum_ornamentum:plain", Optional.of(data));
        assertEquals(material, StructureMaterial.require(material.wireIdentity()));
        assertThrows(IllegalArgumentException.class, () -> AuthoredBlockData.decode(999, 3465, data.encoded()));
        assertThrows(IllegalArgumentException.class, () -> AuthoredBlockData.decode(1, 0, data.encoded()));
        assertThrows(IllegalArgumentException.class, () -> StructureMaterial.require(material.wireIdentity().replace("3|", "2|")));
        assertThrows(IllegalArgumentException.class, () -> AuthoredBlockData.decode(1, 3465, data.encoded() + "="));
    }

    @Test
    void courtyardSeatContactCanDepartWithoutMakingFurnitureAPreferredStation() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:yard-contact"), 139));
        var home = state.bootstrap().settlements().getFirst();
        var bakery = home.structures().stream().filter(s -> s.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        var plan = BuildingSpatialPlan.of(bakery);
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, home.id(), List.of()).forProfile(UnitNavigationProfile.VILLAGER);
        var seat = plan.placement().support(new BlockPosition(5, 3, 14));
        var floor = plan.placement().support(new BlockPosition(7, 2, 13));
        assertFalse(knowledge.geometry().blocked(seat), "an edge-supported occupant has a real departure contact");
        assertFalse(knowledge.clearStation(seat), "departure does not invent standing room at the seat centre");
        assertTrue(knowledge.geometry().traversalPenalty(seat) > knowledge.geometry().traversalPenalty(floor));
        var order = new MovementOrder(home.id(), home.id(), 0, 1, List.of(floor), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        var route = knowledge.path(seat, order);
        assertEquals(floor, route.getLast()); knowledge.requireRoute(route);
        var removed = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(
                state.navigationGeometry().revision(), Map.of(seat.support(),
                        io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.known(
                                io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell.AIR)))
                .apply(state, io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved.OWNER);
        var changed = KnownPedestrianRouteKnowledge.forPublicJourney(removed, home.id(), List.of()).forProfile(UnitNavigationProfile.VILLAGER);
        assertTrue(changed.geometry().blocked(seat), "edge contacts cannot resurrect a destroyed seat");
    }

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
        assertEquals(
                new BlockPosition(-1496, 64, -1346),
                BuildingDefinitionFormat.worldPoint("-1496,64,-1346"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildingDefinitionFormat.point("-1496,64,-1346"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildingDefinitionFormat.worldPoint("30000001,64,0"));
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
            var mount = definition.primaryWorkplaceDefinition();
            var worldFront = placement.direction(mount.facing());
            assertEquals(mount.facing(), placement.localDirection(worldFront));
            assertEquals(placement.support(mount.worker()), worldFront.step(placement.support(mount.socket()), 1),
                    "the declared machine front must face the transformed operator for every building rotation");
            for (var p :
                    List.of(
                            definition.origin(),
                            definition.primaryWorkplaceDefinition().worker(),
                            definition.fabric().min()))
                assertEquals(p, placement.local(placement.world(p)));
        }
    }
    @Test void equipmentMarkupRejectsMissingOrBackwardsFrontBeforeNativePlacement() {
        var catalog=BuildingCatalog.standard();
        var template=catalog.select(BuildingCatalog.MEDIEVAL_SPRUCE,StructureKind.WORKSHOP);
        var definition=catalog.require(template,StructureKind.WORKSHOP);
        var prefix="/io/farfrontier/palemirror/frontier/v3/buildings/medieval_spruce/bakery.properties";
        try(var input=getClass().getResourceAsStream(prefix)) {
            var markup=new HashMap<>(BuildingDefinitionFormat.properties(input.readAllBytes()));
            markup.remove("workplace.oven.facing");
            assertThrows(IllegalArgumentException.class,()->BuildingDefinitionFormat.decode(markup,definition.blocks(),definition.sha256()));
            markup.put("workplace.oven.facing","NORTH");
            assertThrows(IllegalArgumentException.class,()->BuildingDefinitionFormat.decode(markup,definition.blocks(),definition.sha256()),
                    "an equipment face pointing into its rear wall cannot share the declared operator post");
            markup.put("workplace.oven.facing","SOUTH");
            assertEquals(definition,BuildingDefinitionFormat.decode(markup,definition.blocks(),definition.sha256()));
        } catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }

}
