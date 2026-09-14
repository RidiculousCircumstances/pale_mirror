package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Production-scene regression for a close two-person patrol column. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3RoutePatrolGameTests {
    private FrontierV3RoutePatrolGameTests() { }

    /**
     * The second exact member follows the leader into the cell the leader is leaving.  The
     * fixture creates only canonical COLD patrol state and ordinary scene demand; the registered
     * executor must materialize, move, observe and commit the one shared formation edge.
     */
    @GameTest(batch = "pm-frontier-v3-scene-route-patrol", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 140)
    public static void demandedCloseFormationAdvancesOnlyAfterBothExactBodiesLeaveAndArrive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); Fixture fixture = fixture(helper, "ordinary");
        RoutePatrolSceneObservation observation = new RoutePatrolSceneObservation();
        prepareRouteFloor(level, fixture);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(fixture.state());
        ServerPlayer observer = demand(helper, fixture);

        for (int turn = 1; turn <= 110; turn++) {
            helper.runAtTickTime(turn, () -> drive(level, runtime, fixture.taskId(), observation));
        }
        helper.runAtTickTime(111, () -> {
            FrontierWorldState current = runtime.decodedState().orElseThrow();
            RoutePatrol patrol = current.strategicPlans().routePatrols().get(fixture.taskId());
            helper.assertTrue(observation.hotExactRoster(),
                    "ordinary demand must materialize one HOT ROUTE_PATROL lease with its exact patrol roster, never a generic guard fallback");
            helper.assertTrue(observation.observedFormationAdvance(),
                    "the canonical patrol cursor must advance while that exact HOT formation is observed at its retained next bodies");
            helper.assertTrue(patrol.status() == RoutePatrolStatus.EN_ROUTE && patrol.travel().routeCursor() >= 1,
                    "the retained COLD patrol cursor must continue from the observed physical edge without a generic replacement: " + patrol.status()
                            + " cursor=" + patrol.travel().routeCursor());
            helper.assertTrue(patrol.blockReason().isEmpty(),
                    "the following exact scout must not be misclassified as an occupied foreign body: " + patrol.blockReason());
            runtime.shutdown(); releaseDemand(observer); helper.succeed();
        });
    }

    /** Ordinary observer loss releases the same exact HOT checkpoint; it is not a new patrol admission. */
    @GameTest(batch = "pm-frontier-v3-scene-z-route-patrol-return", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 285)
    public static void ordinaryDemandLossReturnsTheUnchangedFormationAndCursorToCold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); Fixture fixture = fixture(helper, "ordinary-return"); prepareRouteFloor(level, fixture);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(fixture.state()); ServerPlayer observer = demand(helper, fixture);
        RoutePatrolCheckpoint[] hotCheckpoint = new RoutePatrolCheckpoint[1];
        for (int turn = 1; turn <= 276; turn++) {
            int current = turn;
            helper.runAtTickTime(turn, () -> {
                drive(level, runtime, fixture.taskId(), new RoutePatrolSceneObservation());
                if (current == 30) {
                    FrontierWorldState state = runtime.decodedState().orElseThrow();
                    RoutePatrol patrol = state.strategicPlans().routePatrols().get(fixture.taskId());
                    hotCheckpoint[0] = new RoutePatrolCheckpoint(patrol);
                    // This is ordinary demand loss: the observer leaves through the normal
                    // connection path.  Directly changing a mock player's coordinates does
                    // not update the tracked player section, so it is not valid physical
                    // evidence that demand has actually departed.
                    // The full GameTest server reuses one level.  Earlier, completed tests
                    // can leave their mock observers connected there; those are real global
                    // demand under production policy, but are stale fixture residue here.
                    // Remove only those fixture players before asserting this test's own
                    // ordinary departure.
                    releasePriorMockDemand(level, observer);
                    releaseDemand(observer);
                }
            });
        }
        helper.runAtTickTime(277, () -> {
            FrontierWorldState returned = runtime.decodedState().orElseThrow();
            SceneLease lease = returned.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol).findFirst().orElseThrow();
            RoutePatrol patrol = returned.strategicPlans().routePatrols().get(fixture.taskId());
            helper.assertTrue(lease.status() == SceneLeaseStatus.CLOSED,
                    "ordinary no-demand hysteresis must release the existing route-patrol lease to COLD");
            helper.assertTrue(hotCheckpoint[0].patrol().equals(patrol),
                    "ordinary demand loss must preserve the same patrol formation and cursor, not admit a replacement");
            helper.assertTrue(FrontierRoutePatrolSceneSupport.bodies(patrol).equals(memberLocations(returned, patrol)),
                    "released patrol member locations must equal the retained exact formation");
            runtime.shutdown(); releaseDemand(observer); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-z-route-patrol-blocked", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void occupiedRetainedNextBodyBlocksTheSameDemandedPatrol(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); Fixture fixture = fixture(helper, "occupied"); prepareRouteFloor(level, fixture);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(fixture.state()); ServerPlayer observer = demand(helper, fixture);
        for (int turn = 1; turn <= 20; turn++) {
            int current = turn;
            helper.runAtTickTime(turn, () -> {
                if (current == 3) level.setBlock(new BlockPos(fixture.nextLeaderBody().x(), fixture.nextLeaderBody().y(), fixture.nextLeaderBody().z()),
                        Blocks.STONE.defaultBlockState(), 3);
                drive(level, runtime, fixture.taskId(), new RoutePatrolSceneObservation());
            });
        }
        helper.runAtTickTime(21, () -> {
            RoutePatrol patrol = runtime.decodedState().orElseThrow().strategicPlans().routePatrols().get(fixture.taskId());
            helper.assertTrue(patrol.status() == RoutePatrolStatus.BLOCKED
                            && patrol.blockReason().orElseThrow() == RoutePatrolBlockReason.OCCUPIED_NEXT_BODY,
                    "a normal physical obstruction must block the same retained patrol with typed ownership");
            runtime.shutdown(); releaseDemand(observer); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-z-route-patrol-body-loss", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void missingOwnedHotBodyBlocksTheSameDemandedPatrol(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); Fixture fixture = fixture(helper, "body-loss"); prepareRouteFloor(level, fixture);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(fixture.state()); ServerPlayer observer = demand(helper, fixture);
        for (int turn = 1; turn <= 20; turn++) {
            int current = turn;
            helper.runAtTickTime(turn, () -> {
                if (current == 3) runtime.decodedState().orElseThrow().sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol)
                        .findFirst().flatMap(lease -> lease.members().stream().map(member -> level.getEntity(member.entityId())).filter(Mob.class::isInstance).findFirst())
                        .ifPresent(Entity::discard);
                drive(level, runtime, fixture.taskId(), new RoutePatrolSceneObservation());
            });
        }
        helper.runAtTickTime(21, () -> {
            RoutePatrol patrol = runtime.decodedState().orElseThrow().strategicPlans().routePatrols().get(fixture.taskId());
            helper.assertTrue(patrol.status() == RoutePatrolStatus.BLOCKED
                            && patrol.blockReason().orElseThrow() == RoutePatrolBlockReason.MISSING_OWNED_BODY,
                    "loss of a HOT exact body must block the same retained patrol with typed ownership");
            runtime.shutdown(); releaseDemand(observer); helper.succeed();
        });
    }

    private static void prepareRouteFloor(ServerLevel level, Fixture fixture) {
        fixture.surfaces().forEach(surface -> {
            BlockPos support = new BlockPos(surface.x(), surface.y(), surface.z());
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        });
    }

    private static ServerPlayer demand(GameTestHelper helper, Fixture fixture) {
        var observer = helper.makeMockServerPlayerInLevel();
        // Demand must be ordinary and nearby, but a player standing in the retained column is
        // real obstruction evidence rather than a license to phase a patrol through a player.
        observer.setPos(fixture.handoff().x() + 6.5D, fixture.handoff().y() + 1.0D, fixture.handoff().z() + 6.5D);
        return observer;
    }

    private static void releaseDemand(ServerPlayer observer) { observer.connection.disconnect(Component.literal("route patrol fixture complete")); }

    private static void releasePriorMockDemand(ServerLevel level, ServerPlayer current) {
        level.players().stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                .filter(player -> !player.getUUID().equals(current.getUUID()))
                .filter(player -> player.getGameProfile().getName().equals("test-mock-player"))
                .toList().forEach(FrontierV3RoutePatrolGameTests::releaseDemand);
    }

    private static void drive(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime,
                              SubjectId taskId, RoutePatrolSceneObservation observation) {
        FrontierV3RoutePatrolSceneExecutor.tick(level, runtime);
        FrontierWorldState current = runtime.decodedState().orElseThrow(); observation.observe(level, current, taskId);
        current.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol)
                .flatMap(lease -> lease.members().stream()).map(member -> level.getEntity(member.entityId())).filter(Mob.class::isInstance)
                .map(Mob.class::cast).forEach(FrontierV3ControlledMobMotion::advance);
    }

    private static Fixture fixture(GameTestHelper helper, String scenario) {
        WorldId world = new WorldId("frontier:route-patrol-game-test-" + scenario);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 811L);
        FrontierWorldState source = base.initialState(); Settlement sourceSettlement = source.bootstrap().settlements().getFirst();
        BlockPosition sourceOrigin = source.routeTopology().supplyTraversalTopology(source.bootstrap(), sourceSettlement.id())
                .linearCorridorSurfaces().getFirst().support();
        BlockPos localOrigin = helper.absolutePos(new BlockPos(2, 8, 2));
        FrontierBootstrap bootstrap = translatedBootstrap(source.bootstrap(), localOrigin.getX() - sourceOrigin.x(),
                localOrigin.getY() - sourceOrigin.y(), localOrigin.getZ() - sourceOrigin.z());
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap); Settlement settlement = initial.bootstrap().settlements().getFirst();
        List<ResidentProfile> guards = FrontierWorldStateSupport.availableRouteResidents(initial, settlement.id(), ResidentProfession.SECURITY_WORKER);
        if (guards.size() < 2) throw new IllegalStateException("route-patrol fixture needs a two-person security roster");
        SubjectId leader = guards.getFirst().id(), scout = guards.get(1).id();
        SubjectId objectiveId = new SubjectId("objective:route-patrol-game-test-" + scenario), taskId = new SubjectId("task:route-patrol-game-test-" + scenario);
        StrategicObjective objective = new StrategicObjective(objectiveId, settlement.id(), StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(taskId, objectiveId, settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_GUARD), List.of(),
                StrategicTaskStatus.ACTIVE, Optional.empty());
        RouteUnitManifest unit = RouteUnitManifest.patrol(taskId, leader, List.of(scout));
        // The test's loaded template cell receives a translated complete fresh-world topology,
        // never a local replacement route. Admission and the COLD assembly remain production
        // transitions; only the first physical HOT edge is asserted by this GameTest.
        RoutePatrol patrol = RoutePatrol.planned(initial, task, settlement, unit);
        while (patrol.status() == RoutePatrolStatus.ASSEMBLING) patrol = patrol.advanceFormation();
        List<SurfaceAnchor> surfaces = patrol.inspectionRoute().linearCorridorSurfaces().stream().limit(4).toList();
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(initial.actorLocations());
        FrontierRoutePatrolSceneSupport.bodies(patrol).forEach((actor, body) -> actors.put(actor, new ActorLocation(body, ActorCondition.HEALTHY)));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .strategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task).startPatrol(patrol)));
        return new Fixture(state, taskId, surfaces, patrol.travel().leader().currentBody().supportingSurface().support(),
                patrol.advanceFormation().travel().leader().currentBody());
    }

    private static FrontierBootstrap translatedBootstrap(FrontierBootstrap source, int dx, int dy, int dz) {
        java.util.function.Function<BlockPosition, BlockPosition> translate = position -> new BlockPosition(position.x() + dx, position.y() + dy, position.z() + dz);
        List<Settlement> settlements = source.settlements().stream().map(settlement -> new Settlement(settlement.id(), settlement.displayName(),
                translate.apply(settlement.anchor()), settlement.residents().stream().map(resident -> new Resident(resident.id(), resident.settlementId(),
                resident.role(), translate.apply(resident.home()))).toList(), settlement.structures().stream().map(structure -> new SettlementStructure(
                structure.id(), structure.settlementId(), structure.kind(), translate.apply(structure.anchor()), structure.facing())).toList())).toList();
        List<HiveNest> nests = source.hive().seedNests().stream().map(nest -> new HiveNest(nest.id(), nest.hiveId(), translate.apply(nest.anchor()))).toList();
        List<HiveOrgan> organs = source.hive().organs().stream().map(organ -> new HiveOrgan(organ.id(), organ.hiveId(), organ.nestId(), organ.kind(),
                translate.apply(organ.anchor()), organ.containerId())).toList();
        List<Bioform> bioforms = source.hive().bioforms().stream().map(bioform -> new Bioform(bioform.id(), bioform.hiveId(), bioform.nestId(),
                bioform.chassis(), bioform.mutations(), bioform.assignment(), translate.apply(bioform.position()))).toList();
        Map<TerrainColumn, Integer> surveyed = new LinkedHashMap<>();
        source.terrain().surveyedSupportY().forEach((column, supportY) -> surveyed.put(new TerrainColumn(column.x() + dx, column.z() + dz), supportY + dy));
        return new FrontierBootstrap(source.worldId(), source.seed(), new WorldBounds(source.bounds().minX() + dx, source.bounds().minZ() + dz,
                source.bounds().width(), source.bounds().depth()), settlements, new Hive(source.hive().id(), nests, organs, bioforms), source.ruleset(),
                new TerrainSurfacePlan(source.terrain().baselineSupportY() + dy, surveyed));
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 811L);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(),
                base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private record Fixture(FrontierWorldState state, SubjectId taskId, List<SurfaceAnchor> surfaces, BlockPosition handoff,
                           BodyPosition nextLeaderBody) { }

    private static Map<SubjectId, BodyPosition> memberLocations(FrontierWorldState state, RoutePatrol patrol) {
        Map<SubjectId, BodyPosition> locations = new LinkedHashMap<>();
        patrol.memberIds().forEach(member -> locations.put(member, state.actorLocations().get(member).body()));
        return Map.copyOf(locations);
    }

    private record RoutePatrolCheckpoint(RoutePatrol patrol) { }

    /** Captures the transient physical proof before a finite patrol safely returns to COLD. */
    private static final class RoutePatrolSceneObservation {
        private boolean hotExactRoster;
        private boolean observedFormationAdvance;

        void observe(ServerLevel level, FrontierWorldState state, SubjectId taskId) {
            RoutePatrol patrol = state.strategicPlans().routePatrols().get(taskId);
            if (patrol == null) return;
            state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol)
                    .filter(lease -> lease.status() == SceneLeaseStatus.HOT).forEach(lease -> {
                        boolean exactRoster = lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet())
                                .equals(java.util.Set.copyOf(patrol.memberIds()));
                        boolean materialized = lease.members().stream().allMatch(member -> level.getEntity(member.entityId()) instanceof Mob);
                        hotExactRoster |= exactRoster && materialized;
                        observedFormationAdvance |= exactRoster && materialized && patrol.status() == RoutePatrolStatus.EN_ROUTE
                                && patrol.travel().routeCursor() >= 1;
                    });
        }

        boolean hotExactRoster() { return hotExactRoster; }
        boolean observedFormationAdvance() { return observedFormationAdvance; }
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, Revision coveredRevision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
