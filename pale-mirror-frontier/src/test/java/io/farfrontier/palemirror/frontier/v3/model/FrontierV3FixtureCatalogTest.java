package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3FixtureCatalogTest {
    @Test
    void everyDeclaredFixtureProfileHasExactlyOneLoadedProviderAndRequiredEvidenceContract() {
        List<FrontierV3FixtureCatalog.Profile> profiles = FrontierV3FixtureCatalog.profiles();
        assertEquals(25, profiles.size());
        assertEquals(profiles.size(), profiles.stream().map(FrontierV3FixtureCatalog.Profile::id).distinct().count());
        for (int index = 0; index < profiles.size(); index++) {
            FrontierV3FixtureCatalog.Profile profile = profiles.get(index);
            assertTrue(profile.provider().length() > 0);
            assertEquals("production", profile.rulesetId());
            assertTrue(profile.sourceProfile().length() > 0);
            assertTrue(profile.requiredAssertion().length() > 0);
            var configuration = FrontierV3FixtureCatalog.configuration(profile.id(), new WorldId("frontier:catalog-" + index), 41L);
            assertEquals("frontier:catalog-" + index, configuration.worldId().value());
            assertEquals(FrontierRulesets.production(), configuration.initialState().bootstrap().ruleset(),
                    "a fixture may vary canonical state, but it must not silently vary production balance rules");
        }
    }

    @Test
    void unknownAndDuplicateFixtureProfilesFailBeforeAScenarioCanStart() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3FixtureCatalog.profile("not-a-profile"));
        FrontierV3FixtureCatalog.Profile duplicate = new FrontierV3FixtureCatalog.Profile("duplicate", "world", "production", "normal-world", "unit",
                "terminal", FrontierWorldRuntimeDefinition::configuration);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3FixtureCatalog.catalog(duplicate, duplicate));
    }

    @Test
    void defenderEquipmentFixtureStartsBeforeAnyIssueOrPhysicalSurfaceExists() {
        var configuration = FrontierV3FixtureCatalog.defenderEquipmentConfiguration(new WorldId("frontier:defender-equipment-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(new SubjectId("container:1-depot")).status());
        ExactItemStack sword = state.inventory().items().get(new SubjectId("item:development-defender-sword"));
        assertEquals("minecraft:iron_sword", sword.itemKind());
        assertTrue(state.physicalIntents().isEmpty(), "the fixture may declare canonical preconditions but may not pre-issue the hand-off");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.population.defender_equipment.review")));
    }

    @Test
    void medicalTreatmentFixtureStartsBeforeItsDepotAndCareOperationExist() {
        var configuration = FrontierV3FixtureCatalog.medicalTreatmentConfiguration(new WorldId("frontier:medical-treatment-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        SubjectId depot = new SubjectId("container:1-depot");
        ExactItemStack remedy = state.inventory().items().get(new SubjectId("item:development-medical-remedy"));
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(depot).status());
        assertEquals("minecraft:honey_bottle", remedy.itemKind());
        assertTrue(state.humanPopulation().medicalOperations().isEmpty(), "the fixture must not fabricate an active treatment before its actual depot exists");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.objective.review") && action.dueAt().ticks() == 1_000L));
    }

    @Test
    void steppedRouteFixtureCompilesOneBoundedTwoBlockRiseAndItsProviderOwnedFootings() {
        FrontierWorldState state = FrontierV3FixtureCatalog.steppedRouteConfiguration(new WorldId("frontier:stepped-route-fixture"), 41L).initialState();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        TraversalTopology topology = state.routeTopology().supplyTraversalTopology(state.bootstrap(), settlement);

        assertEquals(4L, topology.edges().stream().filter(edge -> edge.grade() == 1).count());
        assertEquals(1, topology.edges().stream().mapToInt(TraversalTopology.Edge::grade).max().orElseThrow());
        assertTrue(state.physicalDeltas().isEmpty(), "the fixture declares geometry but cannot pre-place a Minecraft support or surface");
        assertTrue(!FrontierRouteNetwork.foundationCells(state.bootstrap(), state.routeTopology()).isEmpty(),
                "the immutable provider compiles the real ramp footing rather than requiring pilot scaffolding");
        assertTrue(FrontierGrayboxPlan.compile(state).cells().values().stream().anyMatch(cell -> cell.semanticPart() == GrayboxSemanticPart.ROUTE_FOUNDATION));
    }

    @Test
    void hiveMobilizationFixtureDeclaresTheExactTaskGroupWithoutPreopeningACocoon() {
        FrontierWorldState state = FrontierV3FixtureCatalog.hiveMobilizationConfiguration(
                new WorldId("frontier:hive-mobilization-fixture"), 41L).initialState();
        HiveMobilization mobilization = state.hiveColony().mobilizations().values().stream().findFirst().orElseThrow();
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                "the exact waking group must survive the checkpoint boundary that precedes physical release");
        assertEquals(HiveMobilizationStatus.WAKING, mobilization.status());
        assertEquals(List.of(new SubjectId("bioform:east-11"), new SubjectId("bioform:east-14"), new SubjectId("bioform:east-2"),
                new SubjectId("bioform:east-23")), mobilization.memberIds());
        assertEquals(new SubjectId("bioform:east-23"), mobilization.overseerId());
        assertTrue(mobilization.memberIds().stream().allMatch(id ->
                state.hiveColony().bioformLifecycles().get(id).phase() == BioformLifecyclePhase.WAKING));
        assertTrue(mobilization.memberIds().stream().allMatch(id ->
                !HivePhysiologySupport.permitsAmbientLease(state, id)),
                "fixture task selection is not permission to create a body before a real cocoon release");
        SubjectId first = mobilization.memberIds().getFirst();
        SubjectId hibernaculum = state.hiveColony().bioformLifecycles().get(first).homeSlot().orElseThrow().hibernaculumId();
        assertEquals(new HiveCocoonSlot(new SubjectId("organ:east-hibernaculum-1"), 3),
                state.hiveColony().bioformLifecycles().get(first).homeSlot().orElseThrow());
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(hibernaculum);
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("WAKE SEQUENCE · 0/4"), "the physical tray must explain its own waking state without a HUD");
    }

    @Test
    void defenderEquipmentReturnFixtureStartsWithOneResolvedExactActorHeldSwordAndNoPhysicalShortcut() {
        var configuration = FrontierV3FixtureCatalog.defenderEquipmentReturnConfiguration(new WorldId("frontier:defender-equipment-return-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        ExactItemStack sword = state.inventory().items().get(new SubjectId("item:development-defender-return-sword"));

        assertEquals("minecraft:iron_sword", sword.itemKind());
        assertTrue(sword.custody() instanceof InventoryCustody.Actor);
        assertEquals(SettlementAssaultStatus.RESOLVED, state.strategicPlans().settlementAssaults().values().iterator().next().status());
        assertTrue(state.physicalIntents().isEmpty(), "the fixture may declare canonical custody but may not pre-return the physical stack");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.population.defender_equipment_return.review")));
    }

    @Test
    void engineeringEquipmentFixtureRetainsOnlyCanonicalCrewAndDepotToolsBeforeAVisit() {
        var configuration = FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(new WorldId("frontier:engineering-equipment-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        SubjectId projectId = new SubjectId("construction:route-reroute-settlement-1--366-64--304");
        RouteConstruction project = state.routeConstructions().get(projectId);
        ExactItemStack tool = state.inventory().items().get(new SubjectId("item:bootstrap-1-engineering-tool-1"));
        assertTrue(project != null && project.team().isPresent());
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(new SubjectId("container:1-depot")).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 20), tool.custody());
        assertTrue(state.physicalIntents().isEmpty(), "the fixture must not pre-issue or materialize an engineering tool");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.route_construction.scan")));
    }

    @Test
    void routeMaintenanceFairnessFixtureRetainsOneColdSourceAndOneIndependentLoadedWorksite() {
        FrontierWorldState state = FrontierV3FixtureCatalog.routeMaintenanceColdSourceFairnessConfiguration(
                new WorldId("frontier:route-maintenance-fairness-fixture"), 41L).initialState();
        RouteMaintenance cold = state.routeMaintenances().get(new SubjectId("maintenance:route--380-64--304"));
        RouteMaintenance loaded = state.routeMaintenances().get(new SubjectId("maintenance:route--140-64--304"));

        assertTrue(cold != null && cold.cargoId().isEmpty());
        assertTrue(loaded != null && loaded.cargoId().isPresent() && loaded.assembly().orElseThrow().complete());
        assertTrue(state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(cold.id())
                && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING));
        assertTrue(state.physicalIntents().values().stream().noneMatch(intent -> intent.subjectIds().contains(loaded.id())
                        && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE),
                "the fixture may retain COLD-ready cargo and crew, but a repair intent belongs exclusively to the later HOT worksite");
        assertEquals(ContainerSurfaceStatus.ACTIVE, state.inventory().surfaces().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER).status());
    }

    @Test
    void engineeringFixtureCompilesOneDeterministicallyCompletableExactCrewApproach() {
        FrontierWorldState state = FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(
                new WorldId("frontier:engineering-joint-approach-fixture"), 41L).initialState();
        SubjectId projectId = new SubjectId("construction:route-reroute-settlement-1--366-64--304");
        EngineeringWorkAssembly assembly = EngineeringWorksite.compile(state, state.routeConstructions().get(projectId));
        int boundedMoves = assembly.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < boundedMoves; move++) {
            int moveIndex = move;
            SubjectId advancing = assembly.nextSafeAdvance().orElseThrow(
                    () -> new AssertionError("compiled engineering crew must not deadlock at move " + moveIndex));
            assembly = assembly.advance(advancing);
            assertEquals(assembly.members().size(), assembly.positions().values().stream().distinct().count(),
                    "one COLD move may not overlap exact people");
        }
        assertTrue(assembly.complete(), "the retained COLD schedule must reach every distinct work-site slot");
    }

    @Test
    void engineeringWorksiteLeaseClosesThroughItsProjectOwnerRatherThanALogisticsBinding() {
        WorldId world = new WorldId("frontier:engineering-worksite-release");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.engineeringWorksiteConfiguration(world, 41L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        EngineeringWorkSceneCandidate candidate = FrontierEngineeringWorkSceneSupport.nextCandidate(initial).orElseThrow();
        assertTrue(FrontierSceneAdmission.available(initial, candidate.memberPositions().keySet()),
                "the COLD-ready crew is free for its own scene hand-off");
        assertTrue(candidate.memberPositions().keySet().stream().allMatch(actor -> FrontierSceneAdmission.reserved(initial, actor)),
                "the next engineering worksite owns its COLD-ready crew before ambient demand can reclaim it");
        SceneLeaseId leaseId = new SceneLeaseId("lease:engineering-worksite-release");
        SceneLease lease = SceneLease.forCause(leaseId, world, new EngineeringWorkSceneCause(candidate.projectId(), candidate.workCellIndex()),
                candidate.workCell(), engine.checkpoint().instant(), engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList(),
                SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());
        RouteConstruction project = initial.routeConstructions().get(candidate.projectId());
        var workIntent = io.farfrontier.palemirror.frontier.v3.process.RouteConstructionProcess.workIntent(project,
                project.cargoId().orElseThrow(), project.cargoId().map(initial.inventory().cargo()::get).orElseThrow().itemIds().getFirst());
        assertTrue(!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(initial, workIntent),
                "a retained construction item may not execute before its exact worksite is HOT");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-prepare", new EngineeringWorkSceneLeasePrepared(lease)));
        FrontierWorldState prepared = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(prepared, workIntent),
                "PREPARED is not physical-work authority");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT)));
        FrontierWorldState hot = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(hot, workIntent),
                "the exact current HOT lease is the only work authorization");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-restart", new SceneLeaseTransition(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)));
        FrontierWorldState recovering = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(recovering, workIntent),
                "a retained unstarted work intent must wait for the exact scene recovery after restart");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-reclaimed", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-draining", new SceneLeaseTransition(leaseId, SceneLeaseStatus.DRAINING)));
        List<SceneMemberPosition> captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                BodyPosition.above(new SurfaceAnchor(candidate.memberPositions().get(member.actorId()))), FixedScalar.whole(20))).toList();
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-release", new SceneLeaseReleased(leaseId, captured)));
        FrontierWorldState closed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(initial.routeConstructions().get(candidate.projectId()).settlementId(),
                FrontierSceneOwnerSupport.owner(closed, closed.sceneLeases().get(leaseId)));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                                                                    String command, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:" + command);
        return engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, world,
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload));
    }
}
