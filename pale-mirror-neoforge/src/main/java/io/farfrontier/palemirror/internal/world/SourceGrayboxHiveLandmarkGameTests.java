package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live-world proof for the illuminated, non-canonical hive recognition landmark. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxHiveLandmarkGameTests {
    private SourceGrayboxHiveLandmarkGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coLocatedHiveRetainsItsVisibleNonCanonicalSignal(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxLayout.Rectangle site = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 12, 12);
        ReferenceGrayboxLayout.Rectangle organ = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 3, anchor.getZ() + 3, 10, 10);
        ReferenceGrayboxSnapshot snapshot = new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(),
                baseline.bounds(), baseline.cells(), List.of(), List.of(), List.of(new ReferenceGrayboxSnapshot.ResourceSite(1, "mine", -1,
                site, 1.0d, 1.0d, 0.0d, "site.mine")), List.of(), List.of(new ReferenceGrayboxSnapshot.HiveOrgan(1, "core", organ,
                1.0d, 1.0d, false, "organ.core")), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        materializer.apply(helper.getLevel(), snapshot);

        SourceGrayboxPresentationPlan.Desired signal = SourceGrayboxPresentationPlan.from(snapshot).get("hive-organ:1:signal");
        BlockPos signalProbe = new BlockPos(signal.x(), signal.y(), signal.z());
        helper.assertValueEqual(helper.getLevel().getBlockState(signalProbe).getBlock(), Blocks.SEA_LANTERN,
                "a hive organ must retain its night-visible signal after co-located facts are vertically packed");
        helper.assertValueEqual(materializer.claimAt(helper.getLevel(), signalProbe).kind(), "HIVE_ORGAN_SIGNAL",
                "the neutral light remains a visual landmark, not a new canonical hive fact");
        helper.succeed();
    }
}
