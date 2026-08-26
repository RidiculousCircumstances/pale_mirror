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

    @GameTest(batch = "pm-source-graybox-boundary", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void operatorEntryUsesAProtectedDeckOutsideTheSourceArena(GameTestHelper helper) {
        BlockPos footing = SourceGrayboxWorldBoundary.observationDeckFooting();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                helper.getLevel().setBlock(footing.offset(x, 0, z), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3);
            }
        }

        BlockPos entry = SourceGrayboxWorldBoundary.preparedEntry(helper.getLevel());
        helper.assertTrue(SourceGrayboxWorldBoundary.isNeutralObservationDeck(footing),
                "the operator deck must stay in the neutral north border rather than a source cell");
        helper.assertTrue(footing.getZ() < ReferenceGrayboxLayout.MIN_Z,
                "the operator deck must be outside the 64x44 source arena");
        helper.assertValueEqual(entry, footing.above(),
                "operator transport must enter above the dedicated observation deck");
        helper.assertTrue(helper.getLevel().getBlockState(footing).is(Blocks.SEA_LANTERN),
                "the observation deck centre must be lit to suppress neutral-border hostile spawns");
        helper.assertTrue(helper.getLevel().getBlockState(footing.offset(3, 0, 3)).is(Blocks.YELLOW_CONCRETE),
                "the observation deck perimeter must be visibly distinct from the neutral floor");

        BlockPos foreign = footing.offset(1, 0, 0);
        helper.getLevel().setBlock(foreign, Blocks.OBSIDIAN.defaultBlockState(), 3);
        boolean rejected = false;
        try {
            SourceGrayboxWorldBoundary.preparedEntry(helper.getLevel());
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().equals("source graybox operator deck conflicts with a non-boundary block");
        }
        helper.assertTrue(rejected,
                "an operator deck must fail closed on a foreign block instead of overwriting player state");
        helper.assertTrue(helper.getLevel().getBlockState(foreign).is(Blocks.OBSIDIAN),
                "a rejected operator deck preparation must preserve the conflicting block");
        helper.succeed();
    }
}
