package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Native animal behavior must respect the shared movement owner's lifetime. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3HorseNavigationGameTests {
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 60)
    public static void ownedHorseTravelSuppressesGrazingAndReleaseRestoresIt(GameTestHelper helper) {
        var level = helper.getLevel();
        List<SurfaceAnchor> supports = new ArrayList<>();
        for (int x = 4; x <= 9; x++) for (int z = 3; z <= 6; z++) {
            var support = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(support, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
            supports.add(SurfaceAnchor.at(support.getX(), support.getY(), support.getZ()));
        }
        var destination = helper.absolutePos(new BlockPos(8, 30, 4));
        var target = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(supports, List.of(target))), Optional.empty(), List.of());
        var donkey = helper.spawnWithNoFreeWill(EntityType.DONKEY, new Vec3(4.5, 31.0, 4.5));
        helper.runAfterDelay(1, () -> {
            donkey.setEating(true);
            helper.assertTrue(donkey.canEatGrass() && donkey.isImmobile(), "an unowned grazing donkey retains native behavior");
            var result = FrontierV3GoalNavigation.pursue(level, donkey, goal);
            helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS
                    && FrontierV3GoalNavigation.controls(donkey), "a real accepted path must own travel: " + result);
            donkey.aiStep();
            helper.assertTrue(!donkey.canEatGrass() && !donkey.isEating() && !donkey.isImmobile(),
                    "active travel releases existing grazing and prevents a competing fifty-tick stop");
            FrontierV3GoalNavigation.stop(donkey);
            donkey.setEating(true);
            donkey.aiStep();
            helper.assertTrue(donkey.canEatGrass() && donkey.isEating() && donkey.isImmobile(),
                    "release restores vanilla grazing; NoAI alone must not suppress it");
            donkey.discard(); helper.succeed();
        });
    }

    private FrontierV3HorseNavigationGameTests() { }
}
