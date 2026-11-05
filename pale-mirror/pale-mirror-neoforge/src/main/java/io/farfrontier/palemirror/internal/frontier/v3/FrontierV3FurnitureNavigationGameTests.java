package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
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

/** Native regressions for shared furniture preference and partially supported departure. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3FurnitureNavigationGameTests {
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void overhangingStairSeatOccupantCanLeaveTheTableForThePassage(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 3; x <= 10; x++) for (int z = 3; z <= 10; z++) {
            var floor = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 5; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        var seat = helper.absolutePos(new BlockPos(5, 31, 5));
        level.setBlock(seat, Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST), 3);
        level.setBlock(seat.east(), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
        level.setBlock(seat.east().above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 3);
        level.setBlock(seat.east(2), Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        // Exact physical offset from the recorded Nora Bell stall, including partial support.
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(6.074999988079, 31.5, 5.570054570132));
        var destination = helper.absolutePos(new BlockPos(6, 30, 9));
        var target = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        var scope = new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(seat.getX()-3, seat.getZ()-3, 9, 9));
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, actor);
            helper.assertTrue(FrontierV3BodyObservation.capture(actor).support().isPresent(), "edge pose must retain actual support");
            helper.assertTrue(!level.getBlockCollisions(actor, actor.getBoundingBox()).iterator().hasNext(), "recorded pose is physically clear");
            var goal = FrontierV3GoalNavigation.Goal.station(target, scope);
            for (int turn = 0; turn < 150 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.BLOCKED,
                        "edge departure: " + result + "; position=" + actor.position());
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor); actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target), "the same body must reach the passage: " + actor.position());
            FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void nativePathPrefersAPassageOverAShortFurnitureShortcut(GameTestHelper helper) {
        var level = helper.getLevel(); var furniture = new java.util.HashSet<BlockPosition>();
        for (int x = 3; x <= 11; x++) for (int z = 4; z <= 8; z++) {
            var floor = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 4; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = 6; x <= 8; x++) {
            var block = helper.absolutePos(new BlockPos(x, 31, 6));
            level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
            furniture.add(new BlockPosition(block.getX(), block.getY(), block.getZ()));
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5, 31, 6.5));
        var target = helper.absolutePos(new BlockPos(10, 31, 6));
        var origin = helper.absolutePos(new BlockPos(3, 30, 4));
        var scope = new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(origin.getX(), origin.getZ(), 9, 5));
        var policy = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianTraversalPolicy(
                io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet.copyOf(furniture),
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianTraversalRules.initial());
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, actor);
            var query = FrontierV3PedestrianTraffic.query(level, actor, target, scope,
                    Optional.of(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER), policy);
            helper.assertTrue(query.path() != null && query.path().canReach(), "ordinary passage must be reachable");
            for (int i = 1; i < query.path().getNodeCount(); i++) {
                var node = query.path().getNode(i);
                helper.assertTrue(policy.penalty(new BlockPosition(node.x, node.y-1, node.z)) == 0,
                        "native expansion must apply the same soft preference as COLD");
            }
            var corridor = new java.util.HashSet<BlockPosition>();
            for (int x=3; x<=11; x++) for (int y=30; y<=33; y++) {
                var p=helper.absolutePos(new BlockPos(x,y,6)); corridor.add(new BlockPosition(p.getX(),p.getY(),p.getZ()));
            }
            var constrained=FrontierV3PedestrianTraffic.query(level,actor,target,
                    new FrontierV3NavigationScope.Restricted(new LocalNavigationEnvelope(corridor)),
                    Optional.of(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER),policy);
            helper.assertTrue(constrained.path()!=null && constrained.path().canReach(),
                    "a soft preference must still permit climbing when the ordinary detour is outside hard scope");
            actor.discard(); helper.succeed();
        });
    }

}
