package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded residency alone never authorizes a route build or structural-repair world mutation. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PhysicalDemandGameTests {
    private FrontierV3PhysicalDemandGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-route-construction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void anOrdinarilyLoadedChunkWithoutAPlayerDoesNotAuthorizePhysicalEffects(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(0, 8, 0));
        helper.assertTrue(helper.getLevel().hasChunkAt(position), "the GameTest fixture must provide an ordinarily loaded chunk");
        helper.assertFalse(FrontierV3PhysicalDemand.exists(helper.getLevel(), position),
                "chunk residency alone must not authorize a physical route or repair effect");
        helper.succeed();
    }
}
