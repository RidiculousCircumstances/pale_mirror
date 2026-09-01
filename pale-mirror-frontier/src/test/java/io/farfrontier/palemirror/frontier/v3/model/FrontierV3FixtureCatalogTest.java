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
        assertEquals(20, profiles.size());
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
        SceneLeaseId leaseId = new SceneLeaseId("lease:engineering-worksite-release");
        SceneLease lease = SceneLease.forCause(leaseId, world, new EngineeringWorkSceneCause(candidate.projectId(), candidate.workCellIndex()),
                candidate.workCell(), engine.checkpoint().instant(), engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList(),
                candidate.memberPositions(), java.util.Set.of(), Optional.empty());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-prepare", new EngineeringWorkSceneLeasePrepared(lease)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-draining", new SceneLeaseTransition(leaseId, SceneLeaseStatus.DRAINING)));
        List<SceneMemberPosition> captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                candidate.memberPositions().get(member.actorId()), FixedScalar.whole(20))).toList();
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
