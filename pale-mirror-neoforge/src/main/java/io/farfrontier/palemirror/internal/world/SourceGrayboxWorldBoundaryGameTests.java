package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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

    @GameTest(batch = "pm-source-graybox-boundary", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void entryPreloadsItsExactFootingAndFailsClosedWithoutIt(GameTestHelper helper) {
        BlockPos footing = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y - 1);
        helper.getLevel().setBlock(footing, Blocks.STONE.defaultBlockState(), 3);

        BlockPos entry = SourceGrayboxWorldBoundary.preparedEntry(helper.getLevel(), footing);
        helper.assertValueEqual(entry, footing.above(),
                "a prepared graybox entry must stand exactly above its declared neutral footing");

        helper.getLevel().setBlock(footing, Blocks.AIR.defaultBlockState(), 3);
        boolean rejected = false;
        try {
            SourceGrayboxWorldBoundary.preparedEntry(helper.getLevel(), footing);
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().equals("source graybox entry footing is unavailable");
        }
        helper.assertTrue(rejected,
                "a missing entry footing must reject transport instead of letting an operator fall through the graybox");
        helper.succeed();
    }
}
