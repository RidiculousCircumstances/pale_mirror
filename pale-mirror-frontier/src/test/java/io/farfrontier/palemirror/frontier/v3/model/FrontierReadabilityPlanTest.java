package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierReadabilityPlanTest {
    @Test
    void givesEveryFunctionalBuildingOrganAndResourceSiteOneStablePlayerFacingBoard() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-plan"), 91L));
        FrontierReadabilityPlan plan = FrontierReadabilityPlan.compile(state);
        int expected = state.bootstrap().settlements().stream().mapToInt(value -> value.structures().size()).sum()
                + state.bootstrap().hive().organs().size() + state.resourceSites().sites().size() + 1;
        assertEquals(expected, plan.boards().size());
        FrontierObjectBoard workshop = plan.boards().get(state.bootstrap().settlements().getFirst().structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id());
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, workshop.tone());
        assertEquals(FrontierObjectBoard.Scope.LOCAL, workshop.scope());
        assertTrue(workshop.text().contains("WORKSHOP"));
        assertTrue(workshop.text().endsWith("OPERATIONAL"));
        ResourceSite field = FrontierResourceSitePlan.compile(state.bootstrap()).values().iterator().next();
        FrontierObjectBoard fieldBoard = plan.boards().get(field.id());
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, fieldBoard.tone());
        assertTrue(fieldBoard.text().contains("WHEAT FIELD"));
        assertTrue(fieldBoard.text().contains("PREPARING SOIL"));
        assertTrue(!fieldBoard.text().contains(field.id().value()));
        FrontierObjectBoard routeBoard = plan.boards().get(FrontierRouteNetwork.OWNER);
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, routeBoard.tone());
        assertTrue(routeBoard.text().contains("FRONTIER ROUTES"));
        assertEquals(FrontierObjectBoard.Scope.LANDMARK, routeBoard.scope());
        assertTrue(routeBoard.text().endsWith("ACTIVE · 12 SETTLEMENTS"));
        assertEquals(FrontierRouteNetwork.maintenanceContainerPosition(state.bootstrap()).offset(0, 3, -3), routeBoard.position());
        assertEquals(plan.boards(), FrontierReadabilityPlan.compile(state).boards());
    }

    @Test
    void boardInputIgnoresActorMotion() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-motion"), 91L));
        SubjectId actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        FrontierWorldState moved = state.withActorBody(actor, state.actorLocations().get(actor).body().offset(1, 0, 0));

        assertEquals(FrontierReadabilityPlan.input(state), FrontierReadabilityPlan.input(moved),
                "walking changes no player-facing board and must not rebuild the complete board plan");
    }

    @Test
    void boardInputStillRefreshesWhenAnActorConditionChanges() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-condition-change"), 91L));
        SubjectId actor = state.actorLocations().keySet().iterator().next();
        java.util.Map<SubjectId, ActorLocation> changedActors = new LinkedHashMap<>(state.actorLocations());
        ActorLocation prior = changedActors.get(actor);
        changedActors.put(actor, new ActorLocation(prior.body(), prior.condition().withHealth(FixedScalar.whole(7))));
        FrontierWorldState injured = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(changedActors));

        org.junit.jupiter.api.Assertions.assertNotEquals(FrontierReadabilityPlan.input(state), FrontierReadabilityPlan.input(injured),
                "a real actor-condition transition must invalidate the player-facing board cursor");
    }

    @Test
    void reportsDamagedAndDisabledObjectsWithoutOpaqueIdentifiers() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-condition"), 91L));
        SettlementStructure structure = state.bootstrap().settlements().getFirst().structures().getFirst();
        FrontierWorldState damaged = state.withStructureCondition(structure.id(), StructureCondition.DAMAGED);
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(damaged).boards().get(structure.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("DAMAGED · REPAIR NEEDED"));
        assertTrue(!board.text().contains(structure.id().value()));
        assertEquals(FrontierObjectBoard.Scope.LANDMARK, board.scope());
    }

    @Test
    void makesCanonicalInfectionContactVisibleOnTheAffectedHiveOrgan() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-infection"), 91L));
        HiveOrgan ganglion = state.bootstrap().hive().organs().stream().filter(organ -> organ.kind() == HiveOrganKind.GANGLION).findFirst().orElseThrow();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(ganglion.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("INFECTED\nSATURATED"));
        assertTrue(!board.text().contains(ganglion.id().value()));
        assertEquals(FrontierObjectBoard.Scope.LANDMARK, board.scope());
    }

    @Test
    void makesAConflictedFieldLocallyVisibleAsARepairableWarning() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:field-board-conflict"), 91L));
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).values().iterator().next();
        FrontierWorldState conflicted = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()).conflicted()));
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(conflicted).boards().get(site.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("DAMAGED · REPAIR NEEDED"));
        assertEquals(site.cropSlots().getFirst().offset(4, 3, -2), board.position());
    }

    @Test
    void makesAnActiveSettlementQuarantineReadableAtItsInfirmaryWithoutHudState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-quarantine"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        HumanPopulation population = state.humanPopulation().transitionHealth(resident, ResidentHealthStatus.EXPOSED, 100L)
                .transitionQuarantine(settlement.id(), SettlementQuarantineStatus.QUARANTINED, 100L);
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state.withHumanPopulation(population)).boards().get(settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow().id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("QUARANTINE · 1 ACTIVE CASES"));
        assertTrue(!board.text().contains(resident.value()));
    }

    @Test
    void makesExactSettlementFoodShortageReadableAtTheOwnedDepot() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-food-shortage"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        java.util.List<SubjectId> recipients = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())).map(ResidentProfile::id).sorted().toList();
        SettlementProvision shortage = SettlementProvision.started(settlement.id(), 1, 100L, settlement.residents().size(), recipients, java.util.List.of());
        FrontierWorldState hungry = state.withHumanPopulation(state.humanPopulation().withProvision(shortage));
        SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(hungry).boards().get(depot.id());

        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("FOOD SHORTAGE · BREAD NEEDED"));
        assertTrue(!board.text().contains(settlement.id().value()));
    }

    @Test
    void makesTheExactAcceptedMarketWorkReadableAtItsOwnedWorkshop() {
        FrontierDevelopmentScenarios.MaterializedProductionFixture fixture = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(
                new WorldId("frontier:board-market-work"), 94L);
        FrontierWorldState state = fixture.state();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId workshop = state.productionJobs().get(new SubjectId("job:development-production-input-theft")).facilityId();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(workshop);

        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, board.tone());
        assertTrue(board.text().contains("WORKSHOP"));
        assertTrue(board.text().contains("ORDER · 64 BREAD"));
        assertTrue(board.text().contains("FOR " + settlement.displayName()));
        assertTrue(board.text().contains("2 CREDITS"));
        assertTrue(!board.text().contains(fixture.orderId().value()));
    }

    @Test
    void makesAKnownRouteSurfaceLossLocallyVisibleAsAPatrolWarning() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:route-board-conflict"), 91L));
        BlockPosition lost = FrontierRouteNetwork.surfaceCells(state.bootstrap()).iterator().next();
        java.util.Map<BlockPosition, PhysicalDelta> deltas = new java.util.LinkedHashMap<>(state.physicalDeltas());
        deltas.put(lost, new PhysicalDelta(lost, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "test route loss"));
        FrontierWorldState damaged = state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), deltas, state.ambientLeases());
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(damaged).boards().get(FrontierRouteNetwork.OWNER);
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("ROUTE DAMAGE · PATROL NEEDED"));
    }
}
