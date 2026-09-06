package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Guards the production bridge from silently treating ordinary-world events as Frontier reality. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PhysicalWorldGameTests {
    private FrontierV3PhysicalWorldGameTests() { }

    @GameTest(batch = "pm-frontier-v3-physical-world", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void grayboxIsTheOnlyPhysicalFrontierDimension(GameTestHelper helper) {
        helper.assertTrue(FrontierV3PhysicalWorld.isPhysicalDimension(FrontierV3PhysicalWorld.DIMENSION),
                "the v3 executor must recognize its dedicated graybox dimension");
        helper.assertFalse(FrontierV3PhysicalWorld.isPhysicalDimension(Level.OVERWORLD),
                "ordinary Overworld coordinates must never mutate the graybox canonical world");
        helper.succeed();
    }
}
