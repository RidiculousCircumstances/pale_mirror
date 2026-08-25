package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Recovery proof for the explicit physical boundary of the source graybox. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxWorldBoundaryGameTests {
    private SourceGrayboxWorldBoundaryGameTests() { }

    @GameTest(batch = "pm-source-graybox-boundary", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sourceActivationRecoversTheDeclaredFiniteArena(GameTestHelper helper) {
        helper.getLevel().getWorldBorder().setCenter(71.0d, -43.0d);
        helper.getLevel().getWorldBorder().setSize(48.0d);

        SourceGrayboxWorldBoundary.enforce(helper.getLevel());

        helper.assertValueEqual(helper.getLevel().getWorldBorder().getCenterX(), 0.0d,
                "source graybox activation must restore the canonical X centre");
        helper.assertValueEqual(helper.getLevel().getWorldBorder().getCenterZ(), 0.0d,
                "source graybox activation must restore the canonical Z centre");
        helper.assertValueEqual(helper.getLevel().getWorldBorder().getSize(), (double) ReferenceGrayboxLayout.WORLD_BLOCKS,
                "source graybox activation must restore the 1024-block arena");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-boundary", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void missingDedicatedDimensionFailsClosedRatherThanUsingTheTestOverworld(GameTestHelper helper) {
        boolean rejected = false;
        try {
            SourceGrayboxWorldBoundary.level(helper.getLevel().getServer());
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().equals("source graybox dimension is unavailable");
        }
        helper.assertTrue(rejected,
                "a missing dedicated graybox level must reject activation rather than claiming the ordinary test overworld");
        helper.succeed();
    }
}
