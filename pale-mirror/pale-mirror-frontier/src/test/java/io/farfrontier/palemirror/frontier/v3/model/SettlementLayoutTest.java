package io.farfrontier.palemirror.frontier.v3.model;

import static org.junit.jupiter.api.Assertions.*;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import org.junit.jupiter.api.*;

import java.util.*;

/**
 * Changed obligations: real parcels/beds, flush connected streets and retained recovery/extension.
 */
class SettlementLayoutTest {
    private static final FrontierBootstrap BOOTSTRAP =
            FrontierBootstrapper.create(
                    new WorldId("frontier:layout"),
                    139,
                    FrontierRulesets.installed("frontier-v3-simulation-terrain-r31"));

    @Test
    void allSchemesUseAuthoredBuildingsAndConnectedFlushStreets() {
        assertEquals(
                Set.of("crossroads", "spine", "courtyards"),
                BOOTSTRAP.settlements().stream()
                        .map(s -> s.layout().scheme())
                        .collect(java.util.stream.Collectors.toSet()));
        for (var home : BOOTSTRAP.settlements()) {
            assertTrue(
                    home.structures().stream()
                                    .filter(s -> s.kind() == StructureKind.HOUSING)
                                    .count()
                            > 1);
            assertTrue(SettlementBeds.declaredCapacity(home) >= home.residents().size());
            assertTrue(
                    home.structures().stream()
                            .allMatch(
                                    s ->
                                            s.definition().representation()
                                                    == BuildingDefinition.Representation.AUTHORED));
            assertTrue(
                    home.layout().paving().stream()
                            .allMatch(p -> p.y() == BOOTSTRAP.terrain().supportYAt(p.x(), p.z())));
            SettlementLayoutValidation.validate(
                    new SettlementLayoutPlanner.Result(home.structures(), home.layout()));
        }
    }

    @Test
    void flushAuthoredYardsRetainTheSurveyedGroundUnderTheirDeclaredAir() {
        var state = FrontierWorldState.initial(BOOTSTRAP);
        var home = BOOTSTRAP.settlements().getFirst();
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, home.id(), List.of());
        BlockPosition candidate = null;
        for (var building : home.structures()) {
            var plan = BuildingSpatialPlan.of(building);
            var box = building.placement().volume(building.definition().fabric());
            for (int x = box.min().x(); x < box.max().x() && candidate == null; x++)
                for (int z = box.min().z(); z < box.max().z(); z++) {
                    var p = new BlockPosition(x, BOOTSTRAP.terrain().supportYAt(x, z), z);
                    if (!home.layout().paving().contains(p)
                            && !plan.blocks().containsKey(p)
                            && !plan.blocks().containsKey(p.offset(0, 1, 0))
                            && !plan.blocks().containsKey(p.offset(0, 2, 0))) {
                        candidate = p;
                        break;
                    }
                }
            if (candidate != null) break;
        }
        assertNotNull(candidate, "fixture must include a natural empty courtyard column");
        assertEquals(
                io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape.FULL,
                knowledge
                        .geometry()
                        .collisions()
                        .orElseThrow()
                        .cell(candidate)
                        .orElseThrow()
                        .shape(),
                "a blueprint cannot excavate ground merely by omitting it");
    }

    @Test
    void recoveryRetainsExactPlansInsteadOfReallocatingPlots() {
        var state = FrontierWorldState.initial(BOOTSTRAP);
        var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(state));
        for (int i = 0; i < BOOTSTRAP.settlements().size(); i++)
            assertEquals(
                    BOOTSTRAP.settlements().get(i).layout(),
                    restored.bootstrap().settlements().get(i).layout());
        var home = BOOTSTRAP.settlements().getFirst();
        var translated = home.layout().translated(40_000, 0, -40_000);
        var buildings =
                home.structures().stream()
                        .map(
                                s ->
                                        new SettlementStructure(
                                                s.id(),
                                                s.settlementId(),
                                                s.kind(),
                                                s.anchor().offset(40_000, 0, -40_000),
                                                s.facing(),
                                                s.building()))
                        .toList();
        var plan = new SettlementLayoutPlanner.Result(buildings, translated);
        assertEquals(
                plan,
                SettlementLayoutFormat.decode(SettlementLayoutFormat.encode(plan)),
                "world coordinates must not use local-template bounds");
    }

    @Test
    void extensionPreservesExistingAllocationsAndCannotReplaceTheirIds() {
        var layout = BOOTSTRAP.settlements().getFirst().layout();
        var old = layout.plots().values().iterator().next();
        var id = new SettlementLayout.PlotId("plot:extension");
        var box =
                new BuildingVolume(
                        old.bounds().min().offset(400, 0, 400),
                        old.bounds().max().offset(400, 0, 400));
        var added =
                new SettlementLayout.Plot(
                        id, new SubjectId("structure:extension"), box, old.streetAccess());
        var next = layout.extend(List.of(added), Set.of(), layout.extensionFronts());
        layout.plots().forEach((key, value) -> assertEquals(value, next.plots().get(key)));
        assertTrue(next.paving().containsAll(layout.paving()));
        assertThrows(
                IllegalArgumentException.class,
                () -> layout.extend(List.of(old), Set.of(), layout.extensionFronts()));
    }

    @Test
    void losingOneBedHalfRemovesOnePlaceAndSurvivesRecovery() {
        var state = FrontierWorldState.initial(BOOTSTRAP);
        var home = BOOTSTRAP.settlements().getFirst();
        var house =
                home.structures().stream()
                        .filter(s -> s.kind() == StructureKind.HOUSING)
                        .findFirst()
                        .orElseThrow();
        var bed = house.definition().beds().values().iterator().next();
        var head = house.placement().world(bed.head());
        var part = BuildingSpatialPlan.of(house).blocks().get(head).semanticPart();
        int before = SettlementFacilityCapability.housingCapacity(state, home.id());
        var changed =
                state.recordStructureDamage(
                        new StructureDamaged(house.id(), head, part, "test:player-bed-loss"));
        assertEquals(before - 1, SettlementFacilityCapability.housingCapacity(changed, home.id()));
        var codec = new FrontierWorldStateCodec();
        assertEquals(
                before - 1,
                SettlementFacilityCapability.housingCapacity(
                        codec.decode(codec.encode(changed)), home.id()));
    }

    @Test
    void publicServiceStationsAreWalkableTransitButNeverIdleParking() {
        var home = BOOTSTRAP.settlements().getFirst();
        var depot =
                home.structures().stream()
                        .filter(b -> b.kind() == StructureKind.DEPOT)
                        .findFirst()
                        .orElseThrow();
        var port = FrontierTraversalPlan.facilityPort(depot).orElseThrow();
        var contacts = FacilityPedestrianContacts.of(BOOTSTRAP);
        assertEquals(port.stations(), port.publicStations());
        for (var station : port.publicStations()) {
            assertFalse(contacts.workstations().contains(station.support()));
            assertTrue(contacts.noParking().contains(station.support()));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FacilityTraversalPort(
                                port.facilityId(),
                                port.facing(),
                                port.exteriorApproach(),
                                port.thresholdSurface(),
                                port.interiorConnector(),
                                port.stations(),
                                List.of(SurfaceAnchor.at(0, 999, 0)),
                                port.capabilities()));
    }

    @Test
    void forgedOwnerAndObstructedStreetAreRejectedBeforeUse() {
        var home = BOOTSTRAP.settlements().getFirst();
        var original = new SettlementLayoutPlanner.Result(home.structures(), home.layout());
        var l = home.layout();
        var building = home.structures().getFirst();
        var floor =
                BuildingSpatialPlan.of(building).blocks().values().stream()
                        .filter(
                                c ->
                                        c.semanticPart() == GrayboxSemanticPart.FOUNDATION
                                                && c.position().y() <= building.anchor().y())
                        .map(GrayboxCell::position)
                        .filter(p -> !p.equals(building.anchor()))
                        .findFirst()
                        .orElseThrow();
        for (int grade : new int[] {-1, 1}) {
            var slope =
                    BOOTSTRAP
                            .terrain()
                            .withSurveyedSupport(
                                    floor.x(), floor.z(), building.anchor().y() + grade);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettlementLayoutValidation.validate(original, slope, BOOTSTRAP.bounds()),
                    "a moved draft may not bury or suspend its foundation without an earthworks"
                        + " process");
        }
        var forged =
                new SettlementLayout(
                        new SubjectId("settlement:foreign"),
                        l.revision(),
                        l.scheme(),
                        l.style(),
                        l.plots(),
                        l.paving(),
                        l.extensionFronts());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLayoutPlanner.Result(home.structures(), forged));
        var foreignStyle =
                new SettlementLayout(
                        l.settlementId(),
                        l.revision(),
                        l.scheme(),
                        new io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingStyleId(
                                "pale_mirror:foreign"),
                        l.plots(),
                        l.paving(),
                        l.extensionFronts());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLayoutPlanner.Result(home.structures(), foreignStyle));
        var roof =
                home.structures()
                        .getFirst()
                        .placement()
                        .world(home.structures().getFirst().definition().fabric().min());
        var cells = new LinkedHashSet<>(l.paving());
        cells.add(roof.offset(300, 0, 300));
        var broken =
                new SettlementLayout(
                        l.settlementId(),
                        l.revision(),
                        l.scheme(),
                        l.style(),
                        l.plots(),
                        cells,
                        l.extensionFronts());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SettlementLayoutValidation.validate(
                                new SettlementLayoutPlanner.Result(home.structures(), broken)));
    }
}
