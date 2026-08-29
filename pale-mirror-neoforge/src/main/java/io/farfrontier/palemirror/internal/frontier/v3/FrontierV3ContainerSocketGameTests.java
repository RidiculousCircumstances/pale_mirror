package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for the graybox-support to exact-container hand-off. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ContainerSocketGameTests {
    private FrontierV3ContainerSocketGameTests() { }

    @GameTest(batch = "pm-frontier-v3-container-socket", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ownedSocketDefersBeforeProjectionThenAcceptsOnlyItsExactClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(8, 8, 0));
        GrayboxCell support = new GrayboxCell(new BlockPosition(target.getX(), target.getY() - 1, target.getZ()),
                new SubjectId("structure:socket-test"), GrayboxMaterial.DEPOT, GrayboxSemanticPart.FOUNDATION);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);

        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.DEFERRED,
                "an unprojected owned foundation must defer container creation, not become a terminal conflict");
        level.setBlock(target.below().below(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, support), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the fixture must materialize the exact claimed foundation");
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.READY,
                "only the matching materialized semantic claim may admit a container");

        level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT,
                "a world chest at the port stays conflict evidence and is never adopted");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-container-socket", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void changedOwnedSupportIsConflictRatherThanAValidSocket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(16, 8, 0));
        GrayboxCell support = new GrayboxCell(new BlockPosition(target.getX(), target.getY() - 1, target.getZ()),
                new SubjectId("structure:socket-test-drift"), GrayboxMaterial.DEPOT, GrayboxSemanticPart.FOUNDATION);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        level.setBlock(target.below().below(), Blocks.STONE.defaultBlockState(), 3);
        FrontierV3GrayboxExecutor.project(level, ledger, support);
        level.setBlock(target.below(), Blocks.BLUE_CONCRETE.defaultBlockState(), 3);

        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT,
                "a changed owned support cannot be mistaken for a legitimate container foundation");
        helper.assertValueEqual(level.getBlockState(target.below()), Blocks.BLUE_CONCRETE.defaultBlockState(),
                "socket validation must leave the changed world block untouched");
        helper.succeed();
    }
}
