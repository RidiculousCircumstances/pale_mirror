package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical growth, retreat and player-conflict proof for source-cell tissue contours. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxInfectionTissueGameTests {
    private SourceGrayboxInfectionTissueGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void infectionTissueGrowsAndRetreatsFromTheExactSourceCell(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot active = infectionCellFixture(anchor, baseline, .30d, 0.0d, "a".repeat(64));
        SourceGrayboxPresentationPlan.Desired first = SourceGrayboxPresentationPlan.from(active).get("infection-tissue:cell:0:0:0");
        SourceGrayboxPresentationPlan.Desired second = SourceGrayboxPresentationPlan.from(active).get("infection-tissue:cell:0:0:1");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        materializer.apply(helper.getLevel(), active);
        BlockPos firstPos = new BlockPos(first.x(), first.y(), first.z());
        BlockPos secondPos = new BlockPos(second.x(), second.y(), second.z());
        SourceGrayboxPresentationLedger.Claim firstClaim = materializer.claimAt(helper.getLevel(), firstPos);
        helper.assertValueEqual(helper.getLevel().getBlockState(firstPos).getBlock(), Blocks.RED_WOOL,
                "an active source cell must make an obvious red tissue patch rather than a single diagnostic marker");
        helper.assertValueEqual(helper.getLevel().getBlockState(secondPos).getBlock(), Blocks.RED_WOOL,
                "the second active quarter must make source growth spatially legible");
        helper.assertTrue(firstClaim.installed() && firstClaim.kind().equals("INFECTION_TISSUE") && firstClaim.subjectId().equals("cell:0:0"),
                "each tissue clump must retain the exact source cell and durable physical provenance");

        ReferenceGrayboxSnapshot trace = infectionCellFixture(anchor, baseline, .10d, 0.0d, "b".repeat(64));
        materializer.apply(helper.getLevel(), trace);
        helper.assertValueEqual(helper.getLevel().getBlockState(firstPos).getBlock(), Blocks.BROWN_WOOL,
                "a weaker source value must visibly recede to a brown trace instead of retaining stale severe tissue");
        helper.assertValueEqual(helper.getLevel().getBlockState(secondPos).getBlock(), Blocks.AIR,
                "a source cell that recedes must remove only its still-owned excess clump");
        helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).claim("infection-tissue:cell:0:0:1") == null,
                "retired intact tissue provenance must be compacted instead of becoming a permanent phantom claim");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void infectionTissueRecordsAndRetainsAPlayerOwnedObstruction(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot snapshot = infectionCellFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(), .80d, .20d,
                "c".repeat(64));
        SourceGrayboxPresentationPlan.Desired blocked = SourceGrayboxPresentationPlan.from(snapshot).get("infection-tissue:cell:0:0:2");
        BlockPos obstruction = new BlockPos(blocked.x(), blocked.y(), blocked.z());
        helper.getLevel().setBlock(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        materializer.apply(helper.getLevel(), snapshot);
        SourceGrayboxPresentationLedger.Claim conflict = SourceGrayboxPresentationLedger.get(helper.getLevel()).claim(blocked.id());
        helper.assertValueEqual(helper.getLevel().getBlockState(obstruction).getBlock(), Blocks.DIAMOND_BLOCK,
                "a growing infection must not overwrite a pre-existing player block");
        helper.assertTrue(conflict != null && conflict.conflicted() && !conflict.installed(),
                "an unmaterialized foreign obstruction must become a durable, explicitly non-owning conflict");
        helper.assertTrue(SourceGrayboxConflictPresentation.labelText(conflict).contains("foreign-obstruction"),
                "the retained conflict must explain that PM never owned the blocking player geometry");
        SourceGrayboxPresentationLedger restored = SourceGrayboxPresentationLedger.load(
                SourceGrayboxPresentationLedger.get(helper.getLevel()).save(new net.minecraft.nbt.CompoundTag(), null), null);
        helper.assertTrue(restored.claim(blocked.id()).conflicted() && !restored.claim(blocked.id()).installed(),
                "a restart must preserve that the conflict was never PM-owned, rather than granting later erase authority");

        materializer.apply(helper.getLevel(), snapshot);
        helper.assertValueEqual(helper.getLevel().getBlockState(obstruction).getBlock(), Blocks.DIAMOND_BLOCK,
                "a later source publication must not retry its way into overwriting the same player block");
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot infectionCellFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, double infection,
                                                                  double signal, String revision) {
        List<ReferenceGrayboxSnapshot.Cell> cells = new ArrayList<>(baseline.cells());
        ReferenceGrayboxSnapshot.Cell original = cells.getFirst();
        cells.set(0, new ReferenceGrayboxSnapshot.Cell(original.x(), original.y(),
                new ReferenceGrayboxLayout.Rectangle(anchor.getX() - 8, anchor.getZ() - 8, 16, 16), infection,
                original.organicMass(), original.moisture(), signal, "cell.fixture"));
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), revision, baseline.bounds(), cells,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }
}
