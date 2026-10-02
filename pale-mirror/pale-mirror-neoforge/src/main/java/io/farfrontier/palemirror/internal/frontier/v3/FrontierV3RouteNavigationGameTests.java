package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.stream.IntStream;

/** Real Minecraft candidates discriminate a route hint from a hard task restriction. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3RouteNavigationGameTests {
    private FrontierV3RouteNavigationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void pedestrianDetoursAroundResidentWithoutChangingItsGoal(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 3));
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 3.5D));
        Villager occupant = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 1.0D, 3.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(start.getX() + 3, start.getY(), start.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(to,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                LocalNavigationEnvelope.around(from.standingBody(), to.standingBody()));
        helper.runAfterDelay(1, () -> {
            boolean detoured = false;
            for (int turn = 0; turn < 90 && !FrontierV3SemanticMovement.arrived(level, worker, to); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "a resident is a transient physical obstacle, not an impossible task: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
                if (Math.abs(worker.getZ() - (start.getZ() + .5D)) > .35D) detoured = true;
            }
            helper.assertTrue(detoured, "the native path must go around the resident's occupied body volume");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to), "the same goal must be reached");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); occupant.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void sharedGoalBypassesBlockedHintBeyondOldStripeWithoutFalseIntermediateArrival(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        for (int z = 0; z <= 3; z++) {
            BlockPos wall = helper.absolutePos(new BlockPos(3, 1, z));
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5D, 1.0D, 1.5D));
        List<SurfaceAnchor> hint = IntStream.rangeClosed(1, 6).mapToObj(x -> anchor(helper, x, 1)).toList();
        SurfaceAnchor target = hint.getLast();
        var order = order(target);
        var bounds = bounds(helper);
        var goal = FrontierV3GoalNavigation.Goal.routed(order, hint, bounds);
        helper.runAfterDelay(1, () -> {
            actor.setOnGround(true);
            var candidate = actor.getNavigation().createPath(new BlockPos(target.x(), target.y() + 1, target.z()), 0);
            helper.assertTrue(candidate != null && candidate.canReach(), "Minecraft must find the real building detour");
            var obsolete = new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.localLeg(hint, target));
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, candidate, obsolete).orElseThrow().reason()
                    == FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT, "the old stripe must reject this exact candidate");
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, candidate, goal.scope()).isEmpty(),
                    "shared physical policy must accept the loaded dry detour inside real world bounds");
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "a micro-waypoint must not complete the semantic order: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor);
                actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target),
                    "the same actor must reach the original final station: " + actor.position());
            helper.assertTrue(FrontierV3GoalNavigation.pursue(level, actor, goal).status()
                    == FrontierV3GoalNavigation.Status.ARRIVED, "only final physical arrival completes navigation");
            helper.assertTrue(actor.isNoAi(), "native task AI must not acquire activity ownership");
            FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void unchangedGoalResumesAfterPhysicalObstructionClearsWithoutReconnect(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            level.setBlock(start.offset(x, 1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(start.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor target = anchor(helper, 6, 2);
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), List.of(), bounds(helper));
        helper.runAtTickTime(1, () -> FrontierV3GoalNavigation.pursue(level, actor, goal));
        helper.runAtTickTime(25, () -> helper.assertTrue(FrontierV3GoalNavigation.pursue(level, actor, goal).status()
                == FrontierV3GoalNavigation.Status.BLOCKED, "a real obstruction has a finite local failure"));
        helper.runAtTickTime(50, () -> {
            level.setBlock(start.east().above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(start.east().above(2), Blocks.AIR.defaultBlockState(), 3);
            for (int turn = 0; turn < 120 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "a fresh physical candidate must resume the unchanged order: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor); actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target), "clearance must restore movement");
            FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
        });
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
            if (x == 0 || x == 7 || z == 0 || z == 7) {
                helper.getLevel().setBlock(support.above(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(support.above(2), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void repeatedRoutedGoalRefreshPreservesPathAndCompletesOneBlockAscent(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        for (int x = 3; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 3);
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5D, 1.0D, 2.5D));
        List<SurfaceAnchor> hint = IntStream.rangeClosed(1, 6).mapToObj(x -> {
            BlockPos support = helper.absolutePos(new BlockPos(x, x >= 3 ? 1 : 0, 2));
            return SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
        }).toList();
        SurfaceAnchor target = hint.getLast();
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), hint, bounds(helper));
        helper.runAfterDelay(1, () -> {
            FrontierV3GoalNavigation.pursue(level, actor, goal);
            var originalPath = actor.getNavigation().getPath();
            helper.assertTrue(originalPath != null, "the real +1 path must exist");
            FrontierV3GoalNavigation.pursue(level, actor, goal);
            helper.assertTrue(actor.getNavigation().getPath() == originalPath,
                    "acquiring the native actuator must not delete the route leg and restart the same path");
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "same-goal refresh must not block the supported ascent: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor);
                actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target),
                    "repeated production pursuit must complete the jump and land: " + actor.position());
            FrontierV3GoalNavigation.stop(actor);
            actor.discard(); helper.succeed();
        });
    }
    private static SurfaceAnchor anchor(GameTestHelper helper, int x, int z) {
        BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
        return SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
    }
    private static WorldBounds bounds(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new WorldBounds(origin.getX(), origin.getZ(), 8, 8);
    }
    private static MovementOrder order(SurfaceAnchor target) {
        return new MovementOrder(new SubjectId("test:route-owner"), new SubjectId("test:route-actor"), 0, 1,
                List.of(target), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
}
