package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Physical proof for the bounded HOT local-navigation actuator. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3LocalNavigationGameTests {
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void lowRoofPocketUsesTheExitBeforeClimbingTheAdjacentRiser(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 3; x <= 6; x++) for (int z = 3; z <= 6; z++) {
            var floor = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 4; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        var low = helper.absolutePos(new BlockPos(4, 30, 4));
        var high = helper.absolutePos(new BlockPos(5, 31, 4));
        level.setBlock(high, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(low.above(3), Blocks.STONE.defaultBlockState(), 3);
        var allowed = new java.util.HashSet<BlockPosition>();
        // The hard volume includes native jump height as well as standing supports.
        for (int x = 3; x <= 6; x++) for (int z = 3; z <= 6; z++) for (int y = 30; y <= 33; y++) {
            var p = helper.absolutePos(new BlockPos(x, y, z)); allowed.add(new BlockPosition(p.getX(), p.getY(), p.getZ()));
        }
        var scope = new FrontierV3NavigationScope.Restricted(new LocalNavigationEnvelope(allowed));
        var target = SurfaceAnchor.at(high.getX(), high.getY(), high.getZ());
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.65, 31, 4.5));
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, worker);
            helper.assertTrue(!FrontierV3PathClearance.edge(level, worker, low.above(), high.above()),
                    "standing room must not authorize climbing through the pocket ceiling");
            level.setBlock(low.above(3), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3PathClearance.edge(level, worker, low.above(), high.above()),
                    "a later query observes a removed ceiling, never an earlier cached refusal");
            level.setBlock(low.above(3), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3GoalNavigation.canReach(level, worker, FrontierV3GoalNavigation.Goal.station(target, scope)),
                    "a legal route through the open side remains available");
            for (int turn = 0; turn < 140 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, FrontierV3GoalNavigation.Goal.station(target, scope));
                helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.BLOCKED, "pocket escape: " + result
                        + "; position=" + worker.position() + "; support=" + worker.getOnPos() + "; turn=" + turn);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker); worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, target), "exit then climb must physically arrive: " + worker.position());
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void pushedParkedPedestrianRemainsAnObstacleButSlowTravellerKeepsItsHeadway(GameTestHelper helper) {
        var level = helper.getLevel(); var supports = new LinkedHashSet<BlockPosition>();
        for (int x = 4; x <= 12; x++) for (int z = 3; z <= 7; z++) {
            var floor = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
            supports.add(new BlockPosition(floor.getX(), floor.getY(), floor.getZ()));
        }
        var target = helper.absolutePos(new BlockPos(12, 31, 5));
        var scope = new FrontierV3NavigationScope.Restricted(new LocalNavigationEnvelope(supports));
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31, 5.5));
        Villager peer = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(6.5, 31, 5.5));
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, actor);
            FrontierV3BodyObservation.refreshGroundContact(level, peer);
            peer.getNavigation().stop(); peer.setDeltaMovement(.001, 0, 0);
            var parked = FrontierV3PedestrianTraffic.query(level, actor, target, scope);
            helper.assertTrue(parked.blockers().stream().anyMatch(body -> body.id().equals(peer.getUUID())),
                    "a pushed parked body cannot disappear from detour/courtesy evidence");
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, parked.path(), scope).isEmpty(),
                    "the ordinary native graph finds a legal detour around the parked pedestrian");
            var peerPath = peer.getNavigation().createPath(target, 0);
            helper.assertTrue(peerPath != null && peer.getNavigation().moveTo(peerPath, .01), "peer has an actual unfinished travel leg");
            var travelling = FrontierV3PedestrianTraffic.query(level, actor, target, scope);
            helper.assertTrue(travelling.blockers().stream().noneMatch(body -> body.id().equals(peer.getUUID())),
                    "genuinely slow travel keeps following semantics without a velocity threshold");
            FrontierV3GoalNavigation.stop(actor); FrontierV3GoalNavigation.stop(peer);
            actor.discard(); peer.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void declaredStepProfileFindsNativeDetourInsteadOfTheForbiddenRise(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++)
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int z = 2; z <= 9; z++) helper.setBlock(new BlockPos(5, 1, z), Blocks.STONE);
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5, 1, 5.5));
        var corner = helper.absolutePos(BlockPos.ZERO);
        var scope = new FrontierV3NavigationScope.ObservedWorld(new io.farfrontier.palemirror.frontier.v3.model.WorldBounds(
                corner.getX(), corner.getZ(), 13, 13));
        var profile = new io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile(600_000, 1_950_000, 0, 1,
                Set.of(TraversalCapability.PEDESTRIAN));
        var path = FrontierV3PedestrianTraffic.query(level, actor, helper.absolutePos(new BlockPos(8, 1, 5)), scope, Optional.of(profile)).path();
        helper.assertTrue(path != null && path.canReach(), "a forbidden rise must trigger native detour search, not false no-path");
        helper.assertTrue(FrontierV3PedestrianTraffic.permitsProfile(path, Optional.of(profile)), "every native edge respects declared step/drop");
        boolean around = false;
        for (int index = 0; index < path.getNodeCount(); index++) {
            var node = path.getNode(index);
            if (node.z < corner.getZ() + 2 || node.z > corner.getZ() + 9) around = true;
        }
        helper.assertTrue(around, "the actual Minecraft path goes around the wall");
        actor.discard(); helper.succeed();
    }
    private FrontierV3LocalNavigationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void waitingVisitorsReachSeparateBuffersAcrossARaisedRoadWithoutEnteringService(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 4; x <= 15; x++) for (int z = 4; z <= 12; z++) {
            int y = x < 8 ? 30 : 31;
            var floor = helper.absolutePos(new BlockPos(x, y, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        var anchor = helper.absolutePos(new BlockPos(12, 31, 8));
        var station = SurfaceAnchor.at(anchor.getX(), anchor.getY(), anchor.getZ());
        var left = SurfaceAnchor.at(anchor.getX() - 1, anchor.getY(), anchor.getZ() - 2);
        var right = SurfaceAnchor.at(anchor.getX() - 1, anchor.getY(), anchor.getZ() + 2);
        var boundary = new io.farfrontier.palemirror.frontier.v3.model.ServiceAccessBoundary(Set.of(station));
        var point = new io.farfrontier.palemirror.frontier.v3.model.ServiceAccessPoint(
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("test:facility"),
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("test:settlement"), station, boundary,
                List.of(left, right), Set.of(SurfaceAnchor.at(anchor.getX() - 1, anchor.getY(), anchor.getZ())));
        var minimum = helper.absolutePos(new BlockPos(4, 30, 4));
        var maximum = helper.absolutePos(new BlockPos(16, 31, 13));
        var bounds = new io.farfrontier.palemirror.frontier.v3.model.WorldBounds(
                minimum.getX(), minimum.getZ(), maximum.getX() - minimum.getX(), maximum.getZ() - minimum.getZ());
        var first = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31, 6.5));
        var second = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31, 10.5));
        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 150 && (!FrontierV3SemanticMovement.arrived(level, first, left)
                    || !FrontierV3SemanticMovement.arrived(level, second, right)); turn++) {
                for (var entry : List.of(java.util.Map.entry(first, left), java.util.Map.entry(second, right))) {
                    var actor = entry.getKey();
                    var result = FrontierV3GoalNavigation.pursue(level, actor,
                            FrontierV3ServiceApproachNavigation.waitingGoal(point, entry.getValue(), bounds,
                                new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                                    new io.farfrontier.palemirror.frontier.v3.api.SubjectId("test:waiting-owner"),
                                    new io.farfrontier.palemirror.frontier.v3.api.SubjectId("test:" + actor.getUUID()),
                                    0, 1L, List.of(entry.getValue()), TraversalCapability.PEDESTRIAN,
                                    io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION)));
                    helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.BLOCKED,
                            "declared outside buffer remains reachable over the real raised road: " + result);
                    FrontierV3GoalNavigation.advanceAtEntityBoundary(actor); actor.aiStep();
                    helper.assertTrue(boundary.cleared(FrontierV3SurfaceObservation.observedBody(actor)),
                            "waiting cannot enter the exclusive service boundary");
                }
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, first, left)
                    && FrontierV3SemanticMovement.arrived(level, second, right), "both physical visitors reach distinct waiting pockets");
            FrontierV3GoalNavigation.stop(first); FrontierV3GoalNavigation.stop(second);
            first.discard(); second.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 160)
    public static void quarryDescentNeedsSweptHeadroomNotJustTwoEmptyStandingCells(GameTestHelper helper) {
        var level = helper.getLevel();
        var high = helper.absolutePos(new BlockPos(4, 31, 4));
        var low = helper.absolutePos(new BlockPos(5, 30, 4));
        for (var floor : List.of(high, low)) {
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(low.above(3), Blocks.STONE.defaultBlockState(), 3);
        var start = SurfaceAnchor.at(high.getX(), high.getY(), high.getZ());
        var target = SurfaceAnchor.at(low.getX(), low.getY(), low.getZ());
        // Hard scope covers the actual 3-D step volume, including intermediate
        // feet height while descending; it is not merely two arrival supports.
        var scope = new FrontierV3NavigationScope.Restricted(new LocalNavigationEnvelope(Set.of(start.support(), target.support(),
                new BlockPosition(low.getX(), high.getY(), low.getZ()), new BlockPosition(high.getX(), low.getY(), high.getZ()))));
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 32, 4.5));
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, worker);
            helper.assertTrue(!FrontierV3GoalNavigation.canReach(level, worker, FrontierV3GoalNavigation.Goal.station(target, scope)),
                    "the actual reported two-cell niche cannot be entered from the higher neighbour");
            // This fixture exposes the exact extra clearance in the new excavation
            // declaration; it does not pretend that the block was mined here.
            level.setBlock(low.above(3), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3GoalNavigation.canReach(level, worker, FrontierV3GoalNavigation.Goal.station(target, scope)),
                    "swept headroom restores the same native route without enlarging scope");
            for (int turn = 0; turn < 100 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, FrontierV3GoalNavigation.Goal.station(target, scope));
                helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.BLOCKED, "the descent remains executable: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker); worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, target), "the same physical body arrives at the lower station: position="
                    + worker.position() + "; support=" + FrontierV3BodyObservation.capture(worker).support()
                    + "; target=" + target + "; path=" + worker.getNavigation().getPath());
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 160)
    public static void nativeSearchFindsALegalDetourInsteadOfRejectingTheUnconstrainedShortestPath(GameTestHelper helper) {
        var level = helper.getLevel(); var supports = new LinkedHashSet<BlockPosition>();
        for (int x = 4; x <= 12; x++) for (int z = 3; z <= 8; z++) {
            var floor = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
            if (!(x >= 7 && x <= 9 && z <= 5)) supports.add(new BlockPosition(floor.getX(), floor.getY(), floor.getZ()));
        }
        var end = helper.absolutePos(new BlockPos(12, 30, 4));
        var target = new SurfaceAnchor(new BlockPosition(end.getX(), end.getY(), end.getZ()));
        var scope = new FrontierV3NavigationScope.Restricted(new LocalNavigationEnvelope(supports));
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31, 4.5));
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, worker);
            var ordinary = worker.getNavigation().createPath(end.above(), 0);
            helper.assertTrue(ordinary != null && ordinary.canReach()
                    && FrontierV3PhysicalPathPolicy.reject(level, ordinary, scope).orElseThrow().reason()
                        == FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT,
                    "the actual native shortest path must leave the task's hard scope: " + ordinary + ";ground=" + worker.onGround());
            var query = FrontierV3PedestrianTraffic.query(level, worker, end.above(), scope);
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, query.path(), scope).isEmpty(),
                    "a real longer native path exists within the unchanged hard scope");
            var goal = FrontierV3GoalNavigation.Goal.station(target, scope);
            for (int turn = 0; turn < 130 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.BLOCKED, "legal detour remains executable: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker); worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, target), "the same body reaches its semantic goal");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty")
    public static void retainedPathProofRejectsNativeEditsAndReobservesTheImmediateFluid(GameTestHelper helper) {
        var level = helper.getLevel(); var supports = new LinkedHashSet<BlockPosition>();
        var nodes = new ArrayList<net.minecraft.world.level.pathfinder.Node>();
        for (int x = 2; x <= 5; x++) {
            var floor = helper.absolutePos(new BlockPos(x, 0, 2)); level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            supports.add(new BlockPosition(floor.getX(), floor.getY(), floor.getZ()));
            nodes.add(new net.minecraft.world.level.pathfinder.Node(floor.getX(), floor.getY() + 1, floor.getZ()));
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5, 1, 2.5));
        var scope = new FrontierV3NavigationScope.Restricted(new LocalNavigationEnvelope(supports));
        var path = new net.minecraft.world.level.pathfinder.Path(nodes, nodes.getLast().asBlockPos(), true);
        helper.assertTrue(FrontierV3PhysicalPathInspection.inspect(level, worker, path, scope).isEmpty(), "current path is accepted");
        double retained = FrontierV3PhysicalPathInspection.remainingDistance(worker, path, worker.position());
        helper.assertTrue(Math.abs(retained - FrontierV3MinecraftGoalNavigation.remainingDistance(path, worker.position())) < 1e-8,
                "derived suffix cost equals the original path metric");
        var changed = path.getNode(2);
        path.replaceNode(2, new net.minecraft.world.level.pathfinder.Node(changed.x, changed.y, changed.z + 2));
        helper.assertTrue(FrontierV3PhysicalPathInspection.inspect(level, worker, path, scope).orElseThrow().reason()
                == FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT, "in-place native edits cannot reuse an old scope proof");
        path.replaceNode(2, changed);
        helper.assertTrue(FrontierV3PhysicalPathInspection.inspect(level, worker, path, scope).isEmpty(), "restored coordinates are checked again");
        level.setBlock(path.getNode(1).asBlockPos(), Blocks.WATER.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3PhysicalPathInspection.inspect(level, worker, path, scope).orElseThrow().reason()
                == FrontierV3GoalNavigation.BlockReason.UNSUPPORTED_MEDIUM, "cached scope does not conceal new physical fluid");
        FrontierV3PhysicalPathInspection.forget(worker); worker.discard(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void goalNavigatorWalksTwoExcavatedBenchesWithoutChangingTheBody(GameTestHelper helper) {
        var level = helper.getLevel(); var hints = new ArrayList<SurfaceAnchor>();
        for (int x = 4; x <= 15; x++) for (int z = 3; z <= 5; z++) {
            int y = 30 - (x - 4) / 4;
            var support = helper.absolutePos(new BlockPos(x, y, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
            if (z == 4) hints.add(SurfaceAnchor.at(support.getX(), support.getY(), support.getZ()));
        }
        var target = hints.getLast();
        var goal = new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(hints, List.of(target))), Optional.empty(), hints);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31.0, 4.5));
        var identity = worker.getUUID();
        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 150 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.BLOCKED, "an open excavated descent has a native path: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker); worker.aiStep();
            }
            helper.assertTrue(worker.isAlive() && worker.getUUID().equals(identity)
                    && FrontierV3SemanticMovement.arrived(level, worker, target),
                    "the same body must reach the real lower supported landing through shared goal navigation");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 110)
    public static void intermediateRouteHintPreservesMomentumAndStartsItsSuccessor(GameTestHelper helper) {
        var level = helper.getLevel();
        List<SurfaceAnchor> hints = new ArrayList<>();
        // This template is 38x48x38; the entire physical fixture stays inside
        // its own bounds rather than overwriting adjacent concurrently run tests.
        for (int x = 4; x <= 9; x++) for (int z = 3; z <= 5; z++) {
            var support = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
            if (z == 4) hints.add(SurfaceAnchor.at(support.getX(), support.getY(), support.getZ()));
        }
        var target = hints.getLast(); var intermediate = hints.get(4);
        var envelope = LocalNavigationEnvelope.along(hints, List.of(target));
        var goal = new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.Restricted(envelope), Optional.empty(), hints);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31.0, 4.5));
        helper.runAfterDelay(1, () -> {
            boolean crossed = false;
            for (int turn = 0; turn < 75 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                boolean atHint = FrontierV3SemanticMovement.arrived(level, worker, intermediate);
                double before = worker.getDeltaMovement().horizontalDistance();
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                if (atHint && !crossed) {
                    helper.assertTrue(before > 0, "the real body must arrive at the hint with momentum");
                    helper.assertTrue(worker.getDeltaMovement().horizontalDistance() > 0,
                            "local route handoff cannot erase motion before the final goal");
                    var observed = FrontierV3MinecraftGoalNavigation.observation(worker);
                    helper.assertTrue(FrontierV3GoalNavigation.controls(worker) && observed.targets().contains(target),
                            "the successor must own motion in the same refresh, including native final centering: " + observed);
                    crossed = true;
                }
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "an intermediate route hint cannot report final arrival: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(crossed && FrontierV3SemanticMovement.arrived(level, worker, target),
                    "the physical navigator must cross its local hint and reach the retained final goal");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 65)
    public static void minecraftGoalNavigatorReportsAnUnreachableGoalWithinFinitePhysicalTurns(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(3, 0, 3));
        BlockPos destination = start.east(2);
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            BlockPos wall = start.offset(x, 1, z);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 3.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(to,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                LocalNavigationEnvelope.around(from.standingBody(), to.standingBody()));
        var status = new java.util.concurrent.atomic.AtomicReference<>(FrontierV3GoalNavigation.Status.IN_PROGRESS);
        for (int turn = 1; turn <= 26; turn++) helper.runAtTickTime(turn, () -> {
            status.set(FrontierV3GoalNavigation.pursue(level, worker, goal).status());
            helper.assertTrue(worker.isNoAi(), "the blocked path cannot enlist vanilla goal or Brain AI");
        });
        helper.runAtTickTime(27, () -> {
            helper.assertValueEqual(status.get(), FrontierV3GoalNavigation.Status.BLOCKED,
                    "an unreachable physical goal must have a finite, explicit local disposition");
            helper.assertTrue(worker.getOnPos().equals(start), "failure may not move the worker through the obstruction");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void minecraftGoalNavigatorDetoursWithoutVanillaTaskAi(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos destination = start.east(3);
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 3; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(start.east(2).above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(start.east(2).above(2), Blocks.STONE.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        LocalNavigationEnvelope envelope = LocalNavigationEnvelope.around(from.standingBody(), to.standingBody());
        var goal = new FrontierV3GoalNavigation.Goal(to, io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN, envelope);
        AtomicBoolean detoured = new AtomicBoolean();
        helper.runAfterDelay(1, () -> {
            List<String> samples = new ArrayList<>();
            // GameTest catch-up collapses many runAtTickTime callbacks into a single
            // physical entity turn. Drive the same NoAI navigation/control/travel
            // methods as separate bounded physical turns, as the local-motion proof
            // below does, without supplying any synthetic position or path result.
            for (int tick = 1; tick <= 75 && !FrontierV3SemanticMovement.arrived(level, worker, to); tick++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "Minecraft path must remain available within the retained goal: " + result
                                + " position=" + worker.position() + " onGround=" + worker.onGround()
                                + " support=" + worker.getOnPos() + " target=" + destination
                                + " path=" + worker.getNavigation().getPath());
                helper.assertTrue(worker.isNoAi(), "the farmer must not activate vanilla task/Brain AI");
                var observed = worker.getOnPos();
                helper.assertTrue(envelope.contains(new BlockPosition(observed.getX(), observed.getY(), observed.getZ())),
                        "the physical body must remain inside the retained navigation envelope");
                if (Math.abs(worker.getZ() - (start.getZ() + .5D)) > .35D) detoured.set(true);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
                if (tick % 5 == 0) samples.add(tick + ":" + worker.position() + "/" + worker.getOnPos()
                        + "/" + worker.getNavigation().getPath());
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to),
                    "the exact retained goal must be physically reached: " + worker.position()
                            + " ground=" + worker.onGround() + " path=" + worker.getNavigation().getPath()
                            + " speed=" + worker.getSpeed() + " zza=" + worker.zza
                            + " velocity=" + worker.getDeltaMovement() + " samples=" + samples);
            helper.assertTrue(detoured.get(), "the Minecraft path must avoid the real blocked column");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void observedGoalCanDetourBeyondTheOldKnownPathStripe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos destination = start.east(4);
        for (int x = 1; x <= 7; x++) for (int z = 0; z <= 5; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int z = 0; z <= 3; z++) {
            BlockPos wall = helper.absolutePos(new BlockPos(4, 1, z));
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        List<SurfaceAnchor> oldKnownPath = java.util.stream.IntStream.rangeClosed(0, 4)
                .mapToObj(index -> SurfaceAnchor.at(start.getX() + index, start.getY(), start.getZ()))
                .toList();
        LocalNavigationEnvelope oldStripe = LocalNavigationEnvelope.along(oldKnownPath, List.of(to));
        LocalNavigationEnvelope latitude = LocalNavigationEnvelope.between(from.standingBody(), List.of(to));
        helper.assertTrue(!oldStripe.contains(new BlockPosition(start.getX() + 2, start.getY(), start.getZ() + 2)),
                "the old stripe must exclude the only legal two-cell detour");
        helper.assertTrue(latitude.contains(new BlockPosition(start.getX() + 2, start.getY(), start.getZ() + 2)),
                "bounded HOT latitude must admit the physical detour without choosing another task");
        var goal = new FrontierV3GoalNavigation.Goal(to,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN, latitude);
        AtomicBoolean detoured = new AtomicBoolean();
        helper.runAfterDelay(1, () -> {
            for (int tick = 1; tick <= 90 && !FrontierV3SemanticMovement.arrived(level, worker, to); tick++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "real Minecraft path must remain within the bounded replan latitude: " + result);
                if (worker.getZ() >= start.getZ() + 1.9D) detoured.set(true);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(detoured.get(), "the worker must physically leave the obsolete one-cell stripe");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to),
                    "the same semantic goal must be reached without a synthetic body move: " + worker.position());
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 90)
    public static void minecraftGoalNavigatorUsesReachableStationInDeclaredServiceRegion(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos obstructed = helper.absolutePos(new BlockPos(4, 0, 2));
        BlockPos service = helper.absolutePos(new BlockPos(4, 0, 3));
        Set<BlockPosition> supports = new LinkedHashSet<>();
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 5; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            supports.add(new BlockPosition(support.getX(), support.getY(), support.getZ()));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(obstructed.above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(obstructed.above(2), Blocks.STONE.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor blockedStation = SurfaceAnchor.at(obstructed.getX(), obstructed.getY(), obstructed.getZ());
        SurfaceAnchor serviceStation = SurfaceAnchor.at(service.getX(), service.getY(), service.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(List.of(blockedStation, serviceStation),
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                new LocalNavigationEnvelope(supports));
        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 70 && !FrontierV3SemanticMovement.arrived(level, worker, serviceStation); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "declared service region should use its reachable station: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, serviceStation),
                    "physical actor must reach the legal, unobstructed service support");
            var arrived = FrontierV3GoalNavigation.pursue(level, worker, goal);
            helper.assertValueEqual(arrived.status(), FrontierV3GoalNavigation.Status.ARRIVED,
                    "only an observed legal station closes the goal");
            helper.assertValueEqual(arrived.arrivedStation().orElseThrow(), serviceStation,
                    "arrival must carry the exact service station rather than an arbitrary representative");
            helper.assertTrue(worker.isNoAi(), "service navigation may not activate vanilla task AI");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void minecraftGoalNavigatorReachesHydratedCropSupportWithoutTrampling(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos middle = start.east();
        BlockPos soil = middle.east();
        for (BlockPos support : List.of(start, middle, soil)) {
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
        level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
        level.setBlock(soil.south(), Blocks.WATER.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor target = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(target,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                LocalNavigationEnvelope.around(from.standingBody(), target.standingBody()));
        helper.runAfterDelay(1, () -> {
            String lastReason = "not-started";
            for (int turn = 0; turn < 65 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                lastReason = result.reason();
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "hydrated farmland remains a reachable physical support: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
                helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND),
                        "ordinary Minecraft travel may not trample this hydrated field");
            }
            var diagnosticPath = worker.getNavigation().createPath(soil.above(), 0);
            var pathNodes = diagnosticPath == null ? List.of() : java.util.stream.IntStream.range(0, diagnosticPath.getNodeCount())
                    .mapToObj(index -> diagnosticPath.getNode(index).asBlockPos().toString()).toList();
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, target),
                    "exact farmland support must be observed after vanilla travel: " + worker.position()
                            + " reason=" + lastReason + " ground=" + worker.onGround()
                            + " observed=" + worker.getOnPos() + " path=" + worker.getNavigation().getPath()
                            + " soil=" + level.getBlockState(soil) + " crop=" + level.getBlockState(soil.above())
                            + " diagnosticPath=" + diagnosticPath + " nodes=" + pathNodes);
            helper.assertTrue(FrontierV3GoalNavigation.pursue(level, worker, goal).status()
                            == FrontierV3GoalNavigation.Status.ARRIVED,
                    "the shared boundary reports only physically observed arrival");
            helper.assertTrue(worker.isNoAi(), "the navigation bridge must retain the persisted NoAI flag");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void minecraftGoalNavigatorKeepsFarmerAboveEveryCropOnRepeatedFieldEdges(GameTestHelper helper) {
        var level = helper.getLevel();
        List<BlockPos> route = new ArrayList<>();
        for (int z = 4; z <= 5; z++) {
            for (int step = 0; step < 8; step++) {
                int x = z == 4 ? 4 + step : 11 - step;
                BlockPos soil = helper.absolutePos(new BlockPos(x, 30, z));
                level.setBlock(soil.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
                level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
                level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), 3);
                route.add(soil);
            }
        }
        Villager farmer = helper.spawnWithNoFreeWill(EntityType.VILLAGER,
                new Vec3(4.5D, 30.9375D, 4.5D));
        helper.runAfterDelay(1, () -> {
            for (int index = 1; index < route.size(); index++) {
                BlockPos previous = route.get(index - 1), next = route.get(index);
                SurfaceAnchor from = SurfaceAnchor.at(previous.getX(), previous.getY(), previous.getZ());
                SurfaceAnchor target = SurfaceAnchor.at(next.getX(), next.getY(), next.getZ());
                var goal = new FrontierV3GoalNavigation.Goal(target,
                        io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                        LocalNavigationEnvelope.around(from.standingBody(), target.standingBody()));
                for (int turn = 0; turn < 80 && !FrontierV3SemanticMovement.arrived(level, farmer, target); turn++) {
                    var motion = FrontierV3GoalNavigation.pursue(level, farmer, goal);
                    helper.assertTrue(motion.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                            "field edge " + index + " blocked: " + motion + " body=" + farmer.position());
                    FrontierV3GoalNavigation.advanceAtEntityBoundary(farmer);
                    farmer.aiStep();
                }
                helper.assertTrue(FrontierV3SemanticMovement.arrived(level, farmer, target),
                        "field edge " + index + " missed exact crop support: " + farmer.position()
                                + " on=" + farmer.getOnPos());
                helper.assertValueEqual(FrontierV3SupportedBodyCapture.observe(level, farmer),
                        java.util.Optional.of(new BodyPosition(next.getX(), next.getY() + 1, next.getZ())),
                        "field edge " + index + " may not retain a body inside farmland");
                FrontierV3GoalNavigation.stop(farmer);
                level.setBlock(next.above(), Blocks.AIR.defaultBlockState(), 3);
                for (int workTurn = 0; workTurn < 20; workTurn++) {
                    FrontierV3ControlledMobMotion.showStationWorkGesture(level, farmer);
                    FrontierV3ControlledMobMotion.advanceAtEntityBoundary(farmer);
                    farmer.aiStep();
                }
                helper.assertValueEqual(FrontierV3SupportedBodyCapture.observe(level, farmer),
                        java.util.Optional.of(new BodyPosition(next.getX(), next.getY() + 1, next.getZ())),
                        "field work " + index + " may not lower the retained body into its soil");
                FrontierV3ControlledMobMotion.stop(farmer);
            }
            FrontierV3GoalNavigation.stop(farmer); farmer.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-harvest-support", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 140)
    public static void tendingThenLeavingHydratedFarmlandDoesNotInventTrampling(GameTestHelper helper) {
        var level = helper.getLevel();
        var soil = helper.absolutePos(new BlockPos(4, 30, 4));
        var road = soil.east();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
        level.setBlock(soil.west(), Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
        level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(road, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(road.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(road.above(2), Blocks.AIR.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 30.9375D, 4.5D));
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);
        var from = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        var to = SurfaceAnchor.at(road.getX(), road.getY(), road.getZ());
        for (int tick = 1; tick <= 100; tick++) {
            final int turn = tick;
            helper.runAtTickTime(tick, () -> {
                helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND),
                        "ordinary tending/exit must retain hydrated soil; turn=" + turn
                                + " pos=" + worker.position() + " fall=" + worker.fallDistance
                                + " motion=" + FrontierV3ControlledMobMotion.motionObservation(worker));
                if (turn < 50) {
                    FrontierV3ControlledMobMotion.showStationWorkGesture(level, worker);
                } else {
                    if (turn == 50) level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
                    if (turn == 50) FrontierV3ControlledMobMotion.clearStationWorkGesture(level, worker);
                    pursueFixtureRetainedEdge(level, worker, from, to);
                }
                // GameTest callbacks can run after EntityTick.Pre; drive that same
                // idempotent boundary explicitly so a missed tick is not an arrival proof.
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
            });
        }
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to),
                    "worker must actually leave the fractional support for its retained road checkpoint: pos="
                            + worker.position() + " support=" + worker.getOnPos() + " target=" + road
                            + " arrival=" + FrontierV3SemanticMovement.at(level, worker, to)
                            + " motion=" + FrontierV3ControlledMobMotion.motionObservation(worker));
            helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND), "soil remains after exit");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
        });
    }

    /**
     * A production hand-off may begin immediately below its first retained grade-one edge.  It
     * must use the same ordinary collision path as every other retained pedestrian edge: the
     * physical actuator cannot strand the exact worker at cursor zero merely because the next
     * named support is one block higher.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void retainedGradeOneEdgeLeavesTheObservedHandoffSurface(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 0, 4));
        // Keep this complete two-column edge in the template interior.  The first support is
        // deliberately one block below the next named support, matching the production route
        // shape without supplying a synthetic position or alternate path.
        helper.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(2), Blocks.AIR.defaultBlockState(), 3);
        BlockPos nextSupport = origin.west().above();
        helper.getLevel().setBlock(nextSupport, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(nextSupport.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(nextSupport.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        // A production hand-off retains the ordinary ambient-physics registration.  The same
        // exact body must still clear this one-grade retained edge instead of having gravity
        // erase each bounded ascent increment.
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);
        SurfaceAnchor retainedCurrent = SurfaceAnchor.at(origin.getX(), origin.getY(), origin.getZ());
        SurfaceAnchor retainedNext = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());
        helper.runAfterDelay(1, () -> {
            // GameTest can coalesce registered tick callbacks after a catch-up, so model the
            // ordinary scene-post submission and following entity-pre turn directly here.  It
            // still invokes the production actuator's real gravity/collision path and supplies
            // no body position, target, route, or collision result from the fixture.
            pursueFixtureRetainedEdge(helper.getLevel(), worker, retainedCurrent, retainedNext);
            for (int turn = 0; turn < 100 && !FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, retainedNext); turn++) {
                FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                worker.aiStep();
                pursueFixtureRetainedEdge(helper.getLevel(), worker, retainedCurrent, retainedNext);
            }
            FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
            worker.aiStep();
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, retainedNext),
                    "a retained grade-one hand-off edge must physically advance without changing its cursor or route: actual="
                            + worker.position() + " trace=" + FrontierV3ControlledMobMotion.trace(worker));
            FrontierV3ControlledMobMotion.stop(worker);
            // The same body must actually settle on the raised support without treating the
            // actuator's gravity/lift halves as an accumulated fall from a fictitious height.
            for (int turn = 0; turn < 4; turn++) FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
            helper.assertTrue(worker.isAlive() && worker.getHealth() == worker.getMaxHealth()
                            && worker.fallDistance == 0.0F,
                    "a completed one-block ascent must land without synthetic fall damage: health="
                            + worker.getHealth() + " fallDistance=" + worker.fallDistance);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void retainedProductionEdgeRequiresItsNamedPhysicalSupport(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        SurfaceAnchor target = SurfaceAnchor.at(origin.getX() - 1, origin.getY() + 1, origin.getZ());

        helper.assertFalse(FrontierV3ProductionWorkSceneExecutor.clearNextBody(helper.getLevel(), worker, target),
                "an air cell below a retained target body is an owned blocked edge, not a valid upward movement target");
        BlockPos support = new BlockPos(target.x(), target.y(), target.z());
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3ProductionWorkSceneExecutor.clearNextBody(helper.getLevel(), worker, target),
                "the same exact target becomes eligible only when its named support is physically current");
        helper.succeed();
    }

    /**
     * The shared arrival provider reads named collision supports, not a hard-coded body Y or a
     * near-enough point.  Thin legitimate supports remain admissible; water and a foreign body
     * cell are explicit local blocks for an ordinary pedestrian edge.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void semanticTraversalDistinguishesThinSupportFromUndeclaredMediumAndForeignObstruction(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 4.5D));
        SurfaceAnchor target = SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
        helper.getLevel().setBlock(support, Blocks.OAK_SLAB.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a declared slab support must remain a valid semantic pedestrian target");
        helper.getLevel().setBlock(support, Blocks.WHITE_CARPET.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a declared carpet support must remain a valid semantic pedestrian target");
        helper.getLevel().setBlock(support, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.WHEAT.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a normal crop workstation must use its farmland support and collision-clear crop body cell");
        helper.getLevel().setBlock(support.above(), Blocks.WATER.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "water in an ordinary pedestrian body cell is an explicit undeclared-medium block");
        helper.getLevel().setBlock(support.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a foreign occupied body cell is an explicit clearance block, never a proximity arrival");
        worker.discard(); helper.succeed();
    }

    /**
     * A second naturally loaded body can occupy a retained production column after COLD
     * admission.  The exact worker may use only the fixed current/next neighborhood to pass
     * it; no alternate route or cursor is supplied to the physical actuator.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 160)
    public static void productionTraversalYieldsAroundLiveBodyWithoutChangingRetainedCheckpoint(GameTestHelper helper) {
        BlockPos currentSupport = helper.absolutePos(new BlockPos(3, 0, 3));
        BlockPos nextSupport = currentSupport.east();
        for (BlockPos support : List.of(currentSupport, currentSupport.north(), currentSupport.south(), nextSupport,
                nextSupport.north(), nextSupport.south())) {
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        // GameTestHelper converts these body positions from template-relative coordinates;
        // the retained supports above are deliberately absolute world observations.
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        Zombie blocker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 3.5D));
        SurfaceAnchor current = SurfaceAnchor.at(currentSupport.getX(), currentSupport.getY(), currentSupport.getZ());
        SurfaceAnchor next = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());
        LocalNavigationEnvelope envelope = LocalNavigationEnvelope.around(current.standingBody(), next.standingBody());

        helper.runAfterDelay(1, () -> {
            try {
                for (int turn = 0; turn < 32; turn++) {
                    pursueFixtureRetainedEdge(helper.getLevel(), worker, current, next);
                    FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                    worker.aiStep();
                    BlockPosition support = support(worker.position());
                    helper.assertTrue(envelope.contains(support),
                            "a production yield must remain inside the two-support HOT envelope: " + worker.position());
                    helper.assertTrue(!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next),
                            "an occupied exact station cannot award production arrival");
                }
                blocker.discard();
                for (int turn = 0; turn < 32; turn++) {
                    pursueFixtureRetainedEdge(helper.getLevel(), worker, current, next);
                    FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                    worker.aiStep();
                }
                helper.assertTrue(FrontierV3SurfaceObservation.at(worker, next),
                        "only the original retained production checkpoint may complete the yielded edge: " + worker.position());
                FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
            } catch (RuntimeException failure) {
                FrontierV3ControlledMobMotion.stop(worker); worker.discard(); blocker.discard(); throw failure;
            }
        });
    }

    /**
     * The retained workshop corridor descends diagonally immediately after its first level
     * hand-off edge.  A no-AI body must physically walk off that lip and settle on the named
     * lower support; retaining an envelope must not leave it hovering at the old datum.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void productionTraversalSettlesADescendingDiagonalRetainedEdge(GameTestHelper helper) {
        BlockPos currentSupport = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos nextSupport = currentSupport.south().below();
        for (BlockPos support : List.of(currentSupport, nextSupport)) {
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        SurfaceAnchor current = SurfaceAnchor.at(currentSupport.getX(), currentSupport.getY(), currentSupport.getZ());
        SurfaceAnchor next = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());

        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 45; turn++) {
                if (!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next))
                    pursueFixtureRetainedEdge(helper.getLevel(), worker, current, next);
                FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next),
                    "a descending retained production edge must reach its named lower support without changing cursor authority: "
                            + worker.position() + " trace=" + FrontierV3ControlledMobMotion.trace(worker));
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    /** A settled accepted hand-off retires only historical fall evidence from the exact body. */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void acceptedProductionHandoffClearsOnlyGroundedHistoricalFallDistance(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        BodyPosition observed = new BodyPosition(support.getX(), support.getY() + 1, support.getZ());

        worker.fallDistance = 64.0F;
        helper.assertTrue(FrontierV3ProductionWorkSceneExecutor.clearHistoricalFallDistanceAtAcceptedHandoff(helper.getLevel(), worker, observed),
                "an exact grounded production hand-off must retire its old vanilla fall counter");
        helper.assertTrue(worker.fallDistance == 0.0F,
                "clearing an accepted hand-off must preserve the same body and only retire historical fall evidence");
        worker.setPos(worker.getX(), worker.getY() + .90D, worker.getZ());
        worker.fallDistance = 6.0F;
        helper.assertFalse(FrontierV3ProductionWorkSceneExecutor.clearHistoricalFallDistanceAtAcceptedHandoff(helper.getLevel(), worker, observed),
                "an in-flight body must retain ordinary fall evidence instead of borrowing the old grounded hand-off");
        helper.assertTrue(worker.fallDistance == 6.0F,
                "an unsupported body must keep its own physical fall counter");
        helper.succeed();
    }

    /** A retained scene body on its named support must not manufacture fall velocity between hand-off turns. */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void groundedHandoffBodyDoesNotAccumulateSyntheticFallVelocity(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);

        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 16; turn++) FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
            helper.assertTrue(Math.abs(worker.getY() - support.getY() - 1.0D) <= 0.01D
                            && Math.abs(worker.getDeltaMovement().y) <= 1.0E-8D,
                    "a grounded retained body must keep its exact hand-off floor without synthetic fall momentum: position="
                            + worker.position() + " velocity=" + worker.getDeltaMovement());
            FrontierV3ControlledMobMotion.stop(worker);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void harvestTargetWithForeignLivingBodyIsAnExactClearanceBlocker(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.getLevel().setBlock(origin, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.east(), Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(), Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
        helper.getLevel().setBlock(origin.east().above(), Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
        helper.getLevel().setBlock(origin.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.east().above(2), Blocks.AIR.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        Villager blocker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 2.5D));
        SurfaceAnchor target = SurfaceAnchor.at(origin.getX() + 1, origin.getY(), origin.getZ());
        helper.runAtTickTime(2, () -> {
            helper.assertValueEqual(FrontierV3SemanticMovement.target(helper.getLevel(), worker, target),
                    io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition.BLOCKED_CLEARANCE,
                    "a retained harvest target occupied by a foreign living body must not be reported navigable");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); blocker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void cropTendingIsAStationaryWorkPoseNotAnOrbit(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        Vec3 station = worker.position();
        for (int turn = 1; turn <= 16; turn++) helper.runAtTickTime(turn, () -> {
            FrontierV3ControlledMobMotion.showStationWorkGesture(helper.getLevel(), worker);
            helper.assertTrue(worker.position().distanceToSqr(station) < .0001D,
                    "the production work gesture must not move the worker away from its station: " + worker.position());
        });
        helper.runAtTickTime(17, () -> {
            helper.assertTrue(worker.position().distanceToSqr(station) < .0001D,
                    "a crop work gesture must stay at its station rather than circle as filler: " + worker.position());
            FrontierV3ControlledMobMotion.clearStationWorkGesture(helper.getLevel(), worker);
            helper.succeed();
        });
    }

    /** Disposable path-provider fixture; no canonical actor or activity is admitted here. */
    private static void pursueFixtureRetainedEdge(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        FrontierV3GoalNavigation.pursue(level, worker, FrontierV3GoalNavigation.Goal.station(next,
                new FrontierV3NavigationScope.Restricted(io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope.around(
                        current.standingBody(), next.standingBody()))));
    }

    private static BlockPosition support(Vec3 feet) {
        return new BlockPosition((int) Math.floor(feet.x), (int) Math.floor(feet.y) - 1, (int) Math.floor(feet.z));
    }
}
