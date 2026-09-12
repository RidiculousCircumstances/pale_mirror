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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        ServerLevel level = helper.getLevel(); Fixture fixture = fixture();
        fixture.surfaces().forEach(surface -> {
            BlockPos support = new BlockPos(surface.x(), surface.y(), surface.z());
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        });
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(fixture.state());
        var observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(fixture.handoff().x() + .5D, fixture.handoff().y() + 1.0D, fixture.handoff().z() + .5D);

        for (int turn = 1; turn <= 110; turn++) {
            helper.runAtTickTime(turn, () -> drive(level, runtime));
        }
        helper.runAtTickTime(111, () -> {
            FrontierWorldState current = runtime.decodedState().orElseThrow();
            RoutePatrol patrol = current.strategicPlans().routePatrols().get(fixture.taskId());
            SceneLease lease = current.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol).findFirst().orElseThrow();
            helper.assertTrue(lease.status() == SceneLeaseStatus.HOT,
                    "ordinary demanded scene must retain one HOT ROUTE_PATROL lease, not a generic guard fallback: " + lease.status());
            helper.assertTrue(patrol.status() == RoutePatrolStatus.EN_ROUTE && patrol.travel().routeCursor() >= 1,
                    "the canonical patrol cursor advances only after the complete observed formation arrives: " + patrol);
            helper.assertTrue(patrol.blockReason().isEmpty(),
                    "the following exact scout must not be misclassified as an occupied foreign body: " + patrol.blockReason());
            helper.assertTrue(lease.members().stream().allMatch(member -> level.getEntity(member.entityId()) instanceof Mob),
                    "the HOT lease must retain one materialized physical body for every exact roster member");
            runtime.shutdown(); helper.succeed();
        });
    }

    private static void drive(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        FrontierV3RoutePatrolSceneExecutor.tick(level, runtime);
        runtime.decodedState().orElseThrow().sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol)
                .flatMap(lease -> lease.members().stream()).map(member -> level.getEntity(member.entityId())).filter(Mob.class::isInstance)
                .map(Mob.class::cast).forEach(FrontierV3ServerLifecycle::advanceControlledMob);
    }

    private static Fixture fixture() {
        WorldId world = new WorldId("frontier:route-patrol-game-test");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 811L);
        FrontierWorldState initial = base.initialState(); Settlement settlement = initial.bootstrap().settlements().getFirst();
        List<ResidentProfile> guards = FrontierWorldStateSupport.availableRouteResidents(initial, settlement.id(), ResidentProfession.SECURITY_WORKER);
        if (guards.size() < 2) throw new IllegalStateException("route-patrol fixture needs a two-person security roster");
        SubjectId leader = guards.getFirst().id(), scout = guards.get(1).id();
        SubjectId objectiveId = new SubjectId("objective:route-patrol-game-test"), taskId = new SubjectId("task:route-patrol-game-test");
        StrategicObjective objective = new StrategicObjective(objectiveId, settlement.id(), StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        List<SurfaceAnchor> surfaces = initial.routeTopology().supplyTraversalTopology(initial.bootstrap(), settlement.id())
                .linearCorridorSurfaces().stream().limit(4).toList();
        StrategicTask task = new StrategicTask(taskId, objectiveId, settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_GUARD), List.of(),
                StrategicTaskStatus.ACTIVE, Optional.empty());
        RouteUnitManifest unit = RouteUnitManifest.patrol(taskId, leader, List.of(scout));
        TraversalTopology inspection = RoutePatrol.inspectionTopology(initial.withChanges(FrontierWorldStateUpdate.begin()
                .physicalDeltas(Map.of(surfaces.get(1).support(), new PhysicalDelta(surfaces.get(1).support(),
                        PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "test")))), task, settlement);
        TraversalTopology leaderRoute = inspection.linearSegment(new TraversalTopologyId("topology:route-patrol-game-test:leader"), 1, 3);
        TraversalTopology scoutRoute = inspection.linearSegment(new TraversalTopologyId("topology:route-patrol-game-test:scout"), 0, 2);
        PatrolTravel travel = new PatrolTravel(leader, leaderRoute, Map.of(leader, new PatrolTravel.Member(leaderRoute, 0),
                scout, new PatrolTravel.Member(scoutRoute, 0)));
        TraversalTopology leaderIngress = topology("leader-ingress", taskId, List.of(surfaces.get(0), surfaces.get(1)));
        TraversalTopology scoutIngress = topology("scout-ingress", taskId, List.of(surfaces.get(0).offset(-1, 0, 0), surfaces.get(0)));
        PatrolAssembly assembly = new PatrolAssembly(Map.of(leader, new PatrolAssembly.Member(leaderIngress, 1),
                scout, new PatrolAssembly.Member(scoutIngress, 1)));
        RoutePatrol patrol = new RoutePatrol(taskId, settlement.id(), unit, inspection, assembly, travel,
                TacticalPlan.routePatrol(task, unit, surfaces.stream().map(SurfaceAnchor::support).toList()), RoutePatrolStatus.EN_ROUTE,
                Optional.empty(), Optional.empty());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(leader, ActorLocation.standingOn(surfaces.get(1))); actors.put(scout, ActorLocation.standingOn(surfaces.get(0)));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .physicalDeltas(Map.of(surfaces.get(1).support(), new PhysicalDelta(surfaces.get(1).support(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "test")))
                .strategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task).startPatrol(patrol)));
        return new Fixture(state, taskId, surfaces, surfaces.get(1).support());
    }

    private static TraversalTopology topology(String suffix, SubjectId taskId, List<SurfaceAnchor> surfaces) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:route-patrol-game-test:" + suffix), 1L, taskId,
                TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), surfaces);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 811L);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private record Fixture(FrontierWorldState state, SubjectId taskId, List<SurfaceAnchor> surfaces, BlockPosition handoff) { }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, Revision coveredRevision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
