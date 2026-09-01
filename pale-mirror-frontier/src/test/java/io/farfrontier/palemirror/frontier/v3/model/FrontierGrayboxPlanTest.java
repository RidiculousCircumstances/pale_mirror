package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierGrayboxPlanTest {
    @Test
    void fullBootstrapCompilesAStableAttributablePlan() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan first = FrontierGrayboxPlan.compile(state);
        FrontierGrayboxPlan second = FrontierGrayboxPlan.compile(state);

        assertEquals(first.cells(), second.cells());
        assertTrue(first.cells().size() > 10_000);
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-depot")
                && cell.material() == GrayboxMaterial.DEPOT));
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("organ:west-heart")
                && cell.semanticPart() == GrayboxSemanticPart.HIVE_TISSUE));
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.material() == GrayboxMaterial.ROUTE));
        assertEquals(state.infection(), first.infection());
    }

    @Test
    void structuralInputIgnoresActorMotionButInvalidatesOnSilhouetteChange() {
        FrontierWorldState state = initial();
        var actor = state.actorLocations().keySet().iterator().next();
        FrontierWorldState moved = state.withActorLocation(actor, state.actorLocations().get(actor).position().offset(1, 0, 0));
        FrontierWorldState damaged = state.withStructureCondition(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("structure:1-workshop"),
                StructureCondition.DAMAGED);

        assertEquals(FrontierGrayboxPlan.structuralInput(state), FrontierGrayboxPlan.structuralInput(moved),
                "actor-only canonical revisions must retain the same structural projection");
        assertFalse(FrontierGrayboxPlan.structuralInput(state).equals(FrontierGrayboxPlan.structuralInput(damaged)),
                "a visible structural condition change must rebuild the projection");
    }

    @Test
    void destroyedStructureHasNoDesiredCellsButOtherOwnersRemain() {
        FrontierWorldState state = initial().withStructureCondition(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("structure:1-workshop"), StructureCondition.DESTROYED);
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        assertFalse(plan.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-workshop")));
        assertTrue(plan.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-hall")));
    }

    @Test
    void cargoOperationUsesTheSameRouteNetworkAsTheVisibleCorridor() {
        FrontierWorldState state = initial();
        var waypoints = FrontierRouteNetwork.supplyWaypoints(state.bootstrap(), state.bootstrap().settlements().getFirst().id());
        var plan = FrontierGrayboxPlan.compile(state);

        assertTrue(waypoints.stream().skip(1).allMatch(position -> plan.cells().containsKey(position)));
        assertTrue(waypoints.stream().skip(1).allMatch(position -> plan.cells().get(position).ownerId().equals(FrontierRouteNetwork.OWNER)));
    }

    @Test
    void everyHallCompilesOneAuditablePublicAccessPortWithARealTwoBodyThroat() {
        FrontierWorldState state = initial(); FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().settlements().forEach(settlement -> {
            SettlementStructure hall = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HALL).findFirst().orElseThrow();
            SettlementAccessPort port = SettlementAccessPort.forHall(hall);
            assertEquals(new GrayboxCell(port.assemblyFloor(), hall.id(), GrayboxMaterial.HALL, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE),
                    plan.cells().get(port.assemblyFloor()), "public assembly sill must retain exact Hall provenance");
            assertEquals(new GrayboxCell(port.routeFloor(), FrontierRouteNetwork.OWNER, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE),
                    plan.cells().get(port.routeFloor()), "public assembly sill must join the route graph at its exact route cell");
            port.throatAirCells().forEach(position -> assertEquals(null, plan.cells().get(position),
                    "Hall throat must retain two body-clear cells: " + hall.id()));
            assertEquals(FrontierGrayboxPlan.intactStructureCell(hall, port.assemblyFloor()), plan.cells().get(port.assemblyFloor()));
        });
    }

    @Test
    void everyInfirmaryCompilesAnOpenTreatmentPortAndReachableBoundedCareFormation() {
        FrontierWorldState state = initial(); FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().settlements().forEach(settlement -> {
            SettlementStructure infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
            SettlementInfirmaryTreatmentPort port = SettlementInfirmaryTreatmentPort.forInfirmary(infirmary);
            port.ownedAccessSurfaces().forEach(surface -> assertEquals(new GrayboxCell(surface.support(), infirmary.id(), GrayboxMaterial.INFIRMARY, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE),
                    plan.cells().get(surface.support()), "treatment access walk must retain exact infirmary provenance"));
            port.throatAirCells().forEach(position -> assertEquals(null, plan.cells().get(position),
                    "infirmary throat must retain two body-clear cells: " + infirmary.id()));
            assertEquals(2, port.ingressSurfaces().size() - 1, "treatment ingress must retain two adjacent portal transitions");
            for (int index = 1; index < port.arrivalSurfaces().size(); index++) {
                SurfaceAnchor previous = port.arrivalSurfaces().get(index - 1), current = port.arrivalSurfaces().get(index);
                assertEquals(1, Math.abs(previous.x() - current.x()) + Math.abs(previous.z() - current.z()),
                        "treatment arrival must remain a contiguous semantic path: " + infirmary.id());
                assertTrue(Math.abs(previous.y() - current.y()) <= 1,
                        "treatment arrival may use only declared walkable grades: " + infirmary.id());
            }
            for (int ordinal = 0; ordinal < 3; ordinal++) {
                SurfaceAnchor floor = port.treatmentSurface(ordinal);
                assertEquals(new GrayboxCell(floor.support(), infirmary.id(), GrayboxMaterial.INFIRMARY, GrayboxSemanticPart.FOUNDATION), plan.cells().get(floor.support()),
                        "treatment position must be a retained infirmary floor: " + infirmary.id());
                assertEquals(null, plan.cells().get(floor.support().offset(0, 1, 0)), "treatment body clearance must stay open: " + infirmary.id());
                assertEquals(null, plan.cells().get(floor.support().offset(0, 2, 0)), "treatment head clearance must stay open: " + infirmary.id());
            }
        });
    }

    @Test
    void everyBootstrapContainerHasOnePlannedProvenanceSupportSocket() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.inventory().surfaces().values().forEach(surface -> {
            GrayboxCell support = FrontierContainerSocketPlan.support(state, surface).orElseThrow();
            assertEquals(support, plan.cells().get(support.position()), "socket must be part of the immutable graybox plan: " + surface.containerId());
        });
    }

    @Test
    void exactOrganCellCountMatchesTheMaterializedSemanticGeometry() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().hive().organs().forEach(organ -> assertEquals(
                plan.cells().values().stream().filter(cell -> cell.ownerId().equals(organ.id())).count(),
                FrontierGrayboxPlan.intactOrganCellCount(organ),
                "operational damage threshold must count the exact compiled organ geometry: " + organ.id()));
    }

    @Test
    void everyBootstrapResidentUsesOneClearDeterministicStreetOrPerimeterSlot() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().settlements().forEach(settlement -> {
            java.util.Set<BlockPosition> positions = new java.util.HashSet<>();
            settlement.residents().forEach(resident -> {
                BlockPosition position = state.actorLocations().get(resident.id()).position();
                assertTrue(positions.add(position), "resident slots must not overlap: " + resident.id());
                GrayboxCell foot = plan.cells().get(position);
                assertTrue(foot == null || foot.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE,
                        "resident foot cell may use a route surface but must stay outside structure geometry: " + resident.id());
                assertEquals(null, plan.cells().get(position.offset(0, 1, 0)), "resident body clearance must stay outside graybox geometry: " + resident.id());
                assertEquals(null, plan.cells().get(position.offset(0, 2, 0)), "resident head clearance must stay outside graybox geometry: " + resident.id());
            });
        });
    }

    @Test
    void everyBootstrapBioformUsesOneClearDeterministicHivePerimeterSlot() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().hive().bioforms().forEach(bioform -> {
            BlockPosition position = state.actorLocations().get(bioform.id()).position();
            GrayboxCell foot = plan.cells().get(position);
            assertTrue(foot == null || foot.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE,
                    "bioform foot cell must stay outside hive organ geometry: " + bioform.id());
            assertEquals(null, plan.cells().get(position.offset(0, 1, 0)),
                    "bioform body clearance must stay outside hive organ geometry: " + bioform.id());
            assertEquals(null, plan.cells().get(position.offset(0, 2, 0)),
                    "bioform head clearance must stay outside hive organ geometry: " + bioform.id());
        });
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:graybox-plan"), 1234L));
    }
}
