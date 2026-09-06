package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical proof that a source trade route reads as a corridor without duplicating its damage authority. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxRoutePresentationGameTests {
    private SourceGrayboxRoutePresentationGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void tradeRouteUsesNeutralShouldersWithoutDuplicatingTypedDamage(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 56);
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.routeFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Route route = snapshot.routes().getFirst();
        ReferenceGrayboxLayout.Point segment = ReferenceGrayboxLayout.routeSlots(route.start(), route.end()).getFirst();
        BlockPos centre = new BlockPos(segment.x(), ReferenceGrayboxLayout.GROUND_Y, segment.z());
        for (int offset : java.util.List.of(-1, 1)) {
            BlockPos shoulder = centre.offset(0, 0, offset);
            SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(helper.getLevel(), shoulder);
            helper.assertValueEqual(helper.getLevel().getBlockState(shoulder).getBlock(), Blocks.GRAY_WOOL,
                    "a trade route must expose a neutral shoulder instead of reading as a one-block map stroke");
            helper.assertValueEqual(claim.kind(), "ROUTE_SHOULDER",
                    "the neutral shoulder must remain presentation-only infrastructure, not a duplicate damage slot");
            helper.assertValueEqual(claim.subjectId(), route.id(),
                    "each shoulder must retain the exact route identity it makes legible");
            helper.assertValueEqual(claim.interactionKind(), "",
                    "a readability shoulder must never create a second source route-damage fact");
        }
        helper.assertValueEqual(SourceGrayboxPalette.block("route.shoulder").getBlock(), Blocks.GRAY_WOOL,
                "the neutral shoulder palette must preserve status colour for the source route centre line");
        helper.succeed();
    }
}
