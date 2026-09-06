package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves the explicit graybox activation can recover a drifted physical arena boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierGrayboxWorldBoundaryGameTests {
    private FrontierGrayboxWorldBoundaryGameTests() { }

    @GameTest(batch = "pm-frontier-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void explicitGrayboxBoundaryRecoversTheFiniteArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getWorldBorder().setCenter(71.0D, -43.0D);
        level.getWorldBorder().setSize(48.0D);

        FrontierGrayboxWorldBoundary.enforce(level);

        helper.assertValueEqual(level.getWorldBorder().getCenterX(), FrontierGrayboxWorldBoundary.CENTER,
                "explicit graybox activation must restore the fixed X center, not retain an operator-drifted border");
        helper.assertValueEqual(level.getWorldBorder().getCenterZ(), FrontierGrayboxWorldBoundary.CENTER,
                "explicit graybox activation must restore the fixed Z center, not retain an operator-drifted border");
        helper.assertValueEqual(level.getWorldBorder().getSize(), FrontierGrayboxWorldBoundary.SIZE,
                "the graybox world must be physically bounded to its declared 1024-block arena");
        helper.succeed();
    }
}
