package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierReadabilityPlanTest {
    @Test
    void localFormattingRetainsGeometryAndReadsChangedPopulation() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-local"), 91L));
        var geometry = FrontierReadabilityPlan.StableObjectGeometry.compile(state);
        var index = new FrontierReadabilityPlan.StableBoardIndex(state, geometry);
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var moved = state.withActorBody(actor, state.actorLocations().get(actor).body().offset(1, 0, 0));
        assertTrue(geometry.matches(moved));
        var settlement = state.bootstrap().settlements().getFirst();
        var changed = state.withHumanPopulation(state.humanPopulation().accrueHunger(actor, 30_000L));
        assertTrue(geometry.matches(changed), "nutrition changes must not rebuild object cells");
        var expected = FrontierReadabilityPlan.compileStableBaseline(changed).boards();
        expected.forEach((owner, board) -> assertEquals(board, index.render(changed, owner)));
        var conditions = new LinkedHashMap<>(state.structureConditions());
        conditions.put(settlement.structures().getFirst().id(), StructureCondition.DESTROYED);
        var damaged = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions));
        assertTrue(!geometry.matches(damaged));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> index.render(damaged, settlement.structures().getFirst().id()));
    }
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
        assertTrue(workshop.text().contains("BAKERY"));
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
        changedActors.put(actor, new ActorLocation(prior.body(), prior.condition().withHealth(FixedScalar.whole(7)), prior.kind()));
        FrontierWorldState injured = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(changedActors));

        org.junit.jupiter.api.Assertions.assertNotEquals(FrontierReadabilityPlan.input(state), FrontierReadabilityPlan.input(injured),
                "a real actor-condition transition must invalidate the player-facing board cursor");
    }

    @Test
    void addedHiveOrganUsesOnlyTheBoundedBoardOverlayAndRetainsTheStaticBoardBaseline() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-dynamic-hive"), 91L));
        HiveOrgan organ = new HiveOrgan(new SubjectId("organ:board-dynamic-relay"), baseline.bootstrap().hive().id(),
                new SubjectId("nest:seed-east"), HiveOrganKind.RELAY, new BlockPosition(420, 64, 432), java.util.Optional.empty());
        FrontierWorldState grown = baseline.addHiveOrgan(organ);

        FrontierReadabilityPlan stableBefore = FrontierReadabilityPlan.compileStableBaseline(baseline);
        FrontierReadabilityPlan stableAfter = FrontierReadabilityPlan.compileStableBaseline(grown);
        FrontierReadabilityPlan dynamic = FrontierReadabilityPlan.compileDynamicHiveOverlay(grown, 256 - stableAfter.boards().size());
        java.util.Map<SubjectId, FrontierObjectBoard> recomposed = new java.util.LinkedHashMap<>(stableAfter.boards());
        recomposed.putAll(dynamic.boards());

        assertEquals(stableBefore.boards(), stableAfter.boards(),
                "one grown organ must retain every settlement, route, and bootstrap-hive board");
        assertEquals(FrontierReadabilityPlan.compile(grown).boards(), java.util.Map.copyOf(recomposed),
                "the bounded overlay must retain the exact full-plan board grammar");
        assertTrue(dynamic.boards().containsKey(organ.id()), "the grown organ receives its own current local board");
        assertTrue(FrontierReadabilityPlan.input(baseline).matchesStableBaseline(FrontierReadabilityPlan.input(grown)),
                "the board cursor must retain its static baseline across the added-organ transition");
        assertTrue(!FrontierReadabilityPlan.input(baseline).matchesDynamicHiveOverlay(FrontierReadabilityPlan.input(grown)),
                "only the bounded dynamic-hive board overlay is invalidated");
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
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        ResourceSiteConflictObserved conflict = new ResourceSiteConflictObserved(site.id(), site.cropSlots().getFirst(),
                ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH);
        FrontierWorldState conflicted = state.withResourceSites(state.resourceSites().replace(lifecycle.conflicted(
                ResourceSiteConflictDisposition.terminal(site.cropSlots().getFirst(), ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH,
                        ResourceSiteConflictIncidents.first(lifecycle, conflict)))));
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(conflicted).boards().get(site.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("DAMAGED · REPAIR NEEDED"));
        SettlementStructure farm = state.bootstrap().settlements().stream().flatMap(value -> value.structures().stream())
                .filter(value -> value.id().equals(site.facilityId())).findFirst().orElseThrow();
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int minZ = site.cropSlots().stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxZ = site.cropSlots().stream().mapToInt(BlockPosition::z).max().orElseThrow();
        int centreX = (minX + maxX) / 2, centreZ = (minZ + maxZ) / 2;
        assertEquals(site.cropSlots().getFirst().y() + 3, board.position().y());
        assertFalse(site.cropSlots().contains(board.position()), "the local field board may not be embedded in a crop column");
        if (Math.abs(centreX - farm.anchor().x()) >= Math.abs(centreZ - farm.anchor().z())) {
            assertEquals(centreX >= farm.anchor().x() ? maxX + 1 : minX - 1, board.position().x());
            assertEquals(centreZ, board.position().z());
        } else {
            assertEquals(centreX, board.position().x());
            assertEquals(centreZ >= farm.anchor().z() ? maxZ + 1 : minZ - 1, board.position().z());
        }
    }

    @Test
    void namesTheExactSharedTraversalBlockerInsteadOfLeavingHarvestActive() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:field-board-route"), 91L));
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).values().iterator().next();
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        ResourceSiteConflictObserved conflict = new ResourceSiteConflictObserved(site.id(), site.cropSlots().getFirst(),
                ResourceSiteDiagnosticProducer.FIELD_ROUTE_BLOCKED_SUPPORT);
        FrontierWorldState conflicted = state.withResourceSites(state.resourceSites().replace(lifecycle.conflicted(
                ResourceSiteConflictDisposition.terminal(site.cropSlots().getFirst(), ResourceSiteConflictReason.FIELD_ROUTE_BLOCKED_SUPPORT,
                        ResourceSiteConflictIncidents.first(lifecycle, conflict)))));

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(conflicted).boards().get(site.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("HARVEST BLOCKED · ROUTE SUPPORT MISSING"));
    }

    @Test
    void namesRestartRecoveryInsteadOfClaimingThatAnEligibleFarmerIsMissing() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:field-restart-recovery-board"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(new SubjectId("site:1-wheat-field"));
        var authorizedFarmers = SettlementWorkPolicy.permissions(state, settlement.id()).workers(ResidentWorkKind.AGRICULTURE);
        SubjectId farmer = state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(settlement.id())
                        && authorizedFarmers.contains(value.id()))
                .findFirst().orElseThrow().id();
        state = state.withResourceSites(matureReadyField(state, site));
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, farmer, new SimInstant(22_000L));
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = ModeledActorBodyFacts.present(state, farmer);
        state = AmbientLeaseStateProcess.transition(state, farmer, AmbientLeaseStatus.HOT);
        FrontierWorldState unknown = AmbientLeaseStateProcess.transition(state, farmer, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART);

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(unknown).boards().get(site.id());

        assertTrue(board.text().endsWith("READY TO HARVEST · FARMER RECOVERY IN PROGRESS"));
        assertTrue(!board.text().contains("FARMERS NEEDED"));
        assertTrue(!FrontierReadabilityPlan.input(state).matchesStableBaseline(FrontierReadabilityPlan.input(unknown)),
                "the restart recovery status is a player-facing board dependency but body motion remains excluded");
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
    void hungryFarmersRemainEligibleForHarvestWhenNoMealCanStart() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-starving-farmers"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(new SubjectId("site:1-wheat-field"));
        HumanPopulation population = state.humanPopulation();
        for (int cycle = 1; cycle <= ResidentNutrition.STARVING_AFTER_MISSED_CYCLES; cycle++) {
            for (ResidentProfile resident : population.residents().values()) {
                if (resident.settlementId().equals(settlement.id()) && resident.capability(HumanCapability.AGRICULTURE) >= 50) {
                    population = population.resolveNutrition(resident.id(), cycle, false);
                }
            }
        }
        FrontierWorldState hungry = state.withResourceSites(matureReadyField(state, site)).withHumanPopulation(population);

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(hungry).boards().get(site.id());

        assertTrue(board.text().endsWith("READY TO HARVEST · FARMER ASSIGNMENT PENDING"));
        assertTrue(!board.text().contains("FARMERS NEEDED"));
    }

    @Test
    void namesAnExactPendingHarvestStartInsteadOfClaimingTheWholeFacilityLaneIsActive() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-harvest-start-pending"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(new SubjectId("site:1-wheat-field"));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:1-harvest"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, java.util.Optional.empty(), java.util.Optional.of(site.id()),
                1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:1-harvest"),
                objective.id(),
                settlement.id(),
                StrategicTaskKind.HARVEST_RESOURCE_SITE,
                java.util.Optional.empty(),
                java.util.Optional.of(site.id()),
                java.util.List.of(StrategicTaskRequirement.ACTIVE_FARM, StrategicTaskRequirement.AVAILABLE_FARMER,
                        StrategicTaskRequirement.FREE_DEPOT_SLOT),
                java.util.List.of(),
                StrategicTaskStatus.PENDING);
        FrontierWorldState pending = state.withResourceSites(matureReadyField(state, site))
                .withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(pending).boards().get(site.id());

        assertTrue(board.text().endsWith("READY TO HARVEST · HARVEST START PENDING"));
        assertTrue(!board.text().contains("HARVEST LANE ACTIVE"));
        assertTrue(!board.text().contains("FARMERS NEEDED"));
    }

    @Test
    void makesExactSettlementFoodShortageReadableAtTheOwnedDepot() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-food-shortage"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(depot.id());

        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("FOOD SHORTAGE · BREAD NEEDED"));
        assertTrue(!board.text().contains(settlement.id().value()));
    }

    @Test
    void showsRestockedBreadAndCurrentExactResidentHunger() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-restocked-food"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        java.util.List<SubjectId> recipients = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())).map(ResidentProfile::id).sorted().toList();
        HumanPopulation needs = state.humanPopulation();
        for (SubjectId resident : recipients) needs = needs.accrueHunger(resident, 27_000L);
        FrontierWorldState hungry = state.withHumanPopulation(needs);
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        int slot = hungry.inventory().availableSlots(depotId).getFirst();
        FrontierWorldState restocked = hungry.withInventory(hungry.inventory().store(new ExactItemStack(
                new SubjectId("item:board-restocked-bread"), settlement.id(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(depotId, slot))));
        SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(restocked).boards().get(depot.id());

        assertTrue(recipients.stream().allMatch(resident ->
                restocked.humanPopulation().nutrition(resident).status() == ResidentNutritionStatus.HUNGRY));
        assertFalse(FrontierReadabilityPlan.input(hungry).equals(FrontierReadabilityPlan.input(restocked)),
                "restocking must invalidate the live board projection");
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("RESIDENTS HUNGRY · " + recipients.size()
                + " · BREAD 64 / " + (recipients.size() * 3)));
        assertFalse(board.text().contains("BREAD NEEDED"));
    }

    @Test
    void depotBoardCountsFungibleBakeryBreadAgainstExactResidentNeed() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-fungible-bread"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        java.util.List<SubjectId> recipients = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())).map(ResidentProfile::id).sorted().toList();
        HumanPopulation needs = state.humanPopulation();
        for (SubjectId resident : recipients) needs = needs.accrueHunger(resident, 27_000L);
        FrontierWorldState hungry = state.withHumanPopulation(needs);
        SubjectId account = new SubjectId("custody:container-1-depot");
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        ResourceLot bread = new ResourceLot(new SubjectId("lot:board-fungible-bread"), settlement.id(), "minecraft:bread", 64,
                "test-board", java.util.List.of(wheat));
        var resources = hungry.inventory().fungibleResources().transformCold(account, java.util.Map.of(wheat, 64),
                java.util.Map.of(), bread);
        FrontierWorldState stocked = hungry.withInventory(hungry.inventory().withFungibleResources(resources));
        SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(stocked).boards().get(depot.id());
        assertTrue(board.text().endsWith("RESIDENTS HUNGRY · " + recipients.size()
                + " · BREAD 64 / " + (recipients.size() * 3)));
        assertFalse(board.text().contains("BREAD NEEDED"));
    }

    @Test
    void makesTheExactAcceptedMarketWorkReadableAtItsOwnedWorkshop() {
        FrontierDevelopmentScenarios.MaterializedProductionFixture fixture = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(
                new WorldId("frontier:board-market-work"), 94L);
        FrontierWorldState state = fixture.state();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId workshop = state.productionJobs().get(new SubjectId("job:production-development-input-theft")).facilityId();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(workshop);

        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, board.tone());
        assertTrue(board.text().contains("BAKERY"));
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
                java.util.Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "test route loss"));
        FrontierWorldState damaged = state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), deltas, state.ambientLeases());
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(damaged).boards().get(FrontierRouteNetwork.OWNER);
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("ROUTE DAMAGE · PATROL NEEDED"));
    }

    private static ResourceSiteState matureReadyField(FrontierWorldState state, ResourceSite site) {
        ResourceSiteLifecycle ready = new ResourceSiteLifecycle(site.id(), ResourceSitePhase.READY, 2L,
                ResourceSiteLifecycle.MATURE_STAGE, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        ResourceFieldCycle cycle = ResourceFieldCycle.seeded(site.id(), site.layout(), ready.growthEpoch());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) cycle = cycle.advanceGrowthStage();
        return state.resourceSites().replace(ready, cycle);
    }
}
