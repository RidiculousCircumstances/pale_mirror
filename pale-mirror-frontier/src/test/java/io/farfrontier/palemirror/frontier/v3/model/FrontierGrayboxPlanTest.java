package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierGrayboxPlanTest {
    @Test
    void activeEngineeringWorksiteHasExactTemporaryFloorsAndTheirLossConflictsOnlyThatProject() {
        FrontierWorldState state = FrontierV3FixtureCatalog.engineeringWorksiteConfiguration(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:graybox-worksite-staging"), 41L).initialState();
        RouteConstruction project = state.routeConstructions().values().stream().findFirst().orElseThrow();
        java.util.List<BlockPosition> staging = EngineeringWorksite.activeStagingCells(state.bootstrap(), state.routeTopology(), project);
        assertEquals(project.team().orElseThrow().memberIds().size(), staging.size(),
                "the complete exact crew must receive one canonical temporary floor each");
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);
        staging.forEach(position -> assertEquals(new GrayboxCell(position, project.id(), GrayboxMaterial.WORKSITE,
                GrayboxSemanticPart.WORKSITE_STAGING), plan.cells().get(position),
                "temporary support must remain distinct from both a route and a completed building"));

        BlockPosition broken = staging.getFirst();
        FrontierWorldState conflicted = state.recordPhysicalDelta(new PhysicalDelta(broken, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(project.id()), java.util.Optional.of(GrayboxSemanticPart.WORKSITE_STAGING), "player:test"));
        assertEquals(RouteConstructionStatus.CONFLICT, conflicted.routeConstructions().get(project.id()).status(),
                "breaking the project-owned floor must be an immediate canonical construction conflict, never a silent rebuild");
        assertEquals(null, FrontierGrayboxPlan.compile(conflicted).cells().get(broken),
                "a broken temporary floor must not remain in desired-state materialization after that conflict");
        assertEquals(conflicted, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(conflicted)),
                "the exact staging loss and its project conflict must survive recovery");
    }

    @Test
    void fullBootstrapCompilesAStableAttributablePlan() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan first = FrontierGrayboxPlan.compile(state);
        FrontierGrayboxPlan second = FrontierGrayboxPlan.compile(state);

        assertEquals(first.cells(), second.cells());
        assertTrue(first.cells().size() > 10_000);
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-depot")
                && cell.material() == GrayboxMaterial.DEPOT));
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("organ:west-ganglion")
                && cell.semanticPart() == GrayboxSemanticPart.HIVE_TISSUE));
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.material() == GrayboxMaterial.ROUTE));
        assertEquals(state.infection(), first.infection());
    }

    @Test
    void structuralInputIgnoresActorMotionButInvalidatesOnSilhouetteChange() {
        FrontierWorldState state = initial();
        var actor = state.actorLocations().keySet().iterator().next();
        FrontierWorldState moved = state.withActorBody(actor, state.actorLocations().get(actor).body().offset(1, 0, 0));
        FrontierWorldState damaged = state.withStructureCondition(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("structure:1-workshop"),
                StructureCondition.DAMAGED);

        assertEquals(FrontierGrayboxPlan.structuralInput(state), FrontierGrayboxPlan.structuralInput(moved),
                "actor-only canonical revisions must retain the same structural projection");
        assertFalse(FrontierGrayboxPlan.structuralInput(state).equals(FrontierGrayboxPlan.structuralInput(damaged)),
                "a visible structural condition change must rebuild the projection");
    }

    @Test
    void physicalLossIsAnExactDynamicMaskOverTheRetainedStructuralBaseline() {
        FrontierWorldState state = initial();
        GrayboxCell lost = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().value().equals("organ:west-ganglion")).findFirst().orElseThrow();
        BlockPosition broken = lost.position();
        FrontierWorldState afterLoss = state.recordPhysicalDelta(new PhysicalDelta(broken, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(lost.ownerId()), java.util.Optional.of(lost.semanticPart()), "player:test"));

        assertEquals(FrontierGrayboxPlan.structuralInput(state), FrontierGrayboxPlan.structuralInput(afterLoss),
                "one exact observed loss must not invalidate the immutable world-wide structural baseline");
        assertEquals(FrontierGrayboxPlan.compileStructuralBaseline(state).cells(), FrontierGrayboxPlan.compileStructuralBaseline(afterLoss).cells(),
                "the cached baseline retains only geometry; the canonical loss is applied as a per-cell projection mask");
        assertEquals(null, FrontierGrayboxPlan.compile(afterLoss).cells().get(broken),
                "the player-facing desired projection still excludes the exact lost cell");
    }

    @Test
    void localObjectCellIndexMatchesTheFullPlanForEveryBuildingAndHiveOrgan() {
        FrontierWorldState state = initial();
        GrayboxCell lost = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().value().startsWith("organ:")).findFirst().orElseThrow();
        state = state.recordPhysicalDelta(new PhysicalDelta(lost.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(lost.ownerId()), java.util.Optional.of(lost.semanticPart()), "test:object-index-loss"));

        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, java.util.Set<BlockPosition>> expected = new java.util.LinkedHashMap<>();
        FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().value().startsWith("structure:") || cell.ownerId().value().startsWith("organ:"))
                .forEach(cell -> expected.computeIfAbsent(cell.ownerId(), ignored -> new java.util.LinkedHashSet<>()).add(cell.position()));
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, java.util.Set<BlockPosition>> immutable = new java.util.LinkedHashMap<>();
        expected.forEach((owner, positions) -> immutable.put(owner, java.util.Set.copyOf(positions)));

        assertEquals(java.util.Map.copyOf(immutable), FrontierGrayboxPlan.currentObjectCellsByOwner(state),
                "local board contamination must retain exact object geometry and aftermath without rebuilding the route network");
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
            assertEquals(new GrayboxCell(port.assemblyFloor(), hall.id(), GrayboxMaterial.HALL, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE),
                    plan.cells().get(port.assemblyFloor()), "Hall-owned sill must win the declared public-route seam");
            port.throatAirCells().forEach(position -> assertEquals(null, plan.cells().get(position),
                    "Hall throat must retain two body-clear cells: " + hall.id()));
            assertEquals(FrontierGrayboxPlan.intactStructureCell(state.bootstrap().terrain(), hall, port.assemblyFloor()), plan.cells().get(port.assemblyFloor()));
        });
    }

    @Test
    void activeConvoyFormationAndCargoHaveExactPlannedSupportAcrossTheWholeCarriageway() {
        FrontierWorldState state = FrontierDevelopmentScenarios.routeSceneReturnFixture(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:carriageway-support"), 91L).state();
        RouteOperation operation = state.operations().get(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("operation:supply-1-2"));
        OperationTravel travel = operation.activeTravel().orElseThrow();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        travel.formation().forEach((actor, body) -> org.junit.jupiter.api.Assertions.assertNotNull(
                plan.cells().get(body.supportingSurface().support()),
                () -> "active convoy body must have a compiled exact support: " + actor));
        org.junit.jupiter.api.Assertions.assertNotNull(plan.cells().get(travel.cargoAnchor().surface().support()),
                "active convoy cargo must have a compiled exact support");
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
            assertEquals(null, plan.cells().get(surface.position()),
                    "the exact chest body cell must remain clear of the structural plan: " + surface.containerId());
        });
    }

    @Test
    void everyDepotHasAVisibleWalkableServicePortRatherThanAnInteriorRemoteChestSocket() {
        FrontierWorldState state = initial(); FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().settlements().forEach(settlement -> {
            SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
            SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
            assertEquals(port.containerPosition(), state.inventory().surfaces().get(FrontierWorldState.depotId(settlement.id())).position(),
                    "the exact depot chest must use its semantic service socket");
            port.ownedAccessSurfaces().forEach(surface -> assertEquals(new GrayboxCell(surface.support(), depot.id(), GrayboxMaterial.DEPOT,
                    GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE), plan.cells().get(surface.support()),
                    "the depot service floor must retain depot provenance"));
            port.throatAirCells().forEach(cell -> assertEquals(null, plan.cells().get(cell),
                    "the depot service doorway must retain two-body headroom"));
            port.stations().forEach(station -> {
                assertEquals(null, plan.cells().get(station.support().offset(0, 1, 0)), "service station feet cell must stay clear");
                assertEquals(null, plan.cells().get(station.support().offset(0, 2, 0)), "service station head cell must stay clear");
            });
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
                BodyPosition body = state.actorLocations().get(resident.id()).body();
                BlockPosition position = new BlockPosition(body.x(), body.y(), body.z());
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
            BodyPosition body = state.actorLocations().get(bioform.id()).body();
            BlockPosition position = new BlockPosition(body.x(), body.y(), body.z());
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
