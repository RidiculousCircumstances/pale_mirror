package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind;
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
    public static void worksiteOpeningDefersNativeStoneUntilSettledAndRejectsLaterObstruction(GameTestHelper helper) {
        var level = helper.getLevel(); var target = helper.absolutePos(new BlockPos(1, 2, 1));
        var owner = new SubjectId("extraction:socket-admission-test");
        var stone = new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:stone", java.util.Map.of());
        var air = new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:air", java.util.Map.of());
        var opening = new io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock(
                new io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock.Key(
                        io.farfrontier.palemirror.frontier.v3.model.CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, owner,
                        io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock.Role.CONTAINER_SOCKET, 1),
                new BlockPosition(target.getX(), target.getY(), target.getZ()), 1, air);
        var floor = new io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock(
                new io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock.Key(opening.key().family(), owner,
                        io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock.Role.INFRASTRUCTURE, 2),
                opening.position().offset(0, -1, 0), 1, stone);
        var support = new io.farfrontier.palemirror.frontier.v3.model.ContainerSocketSupport.Worksite(floor, opening);
        var ledger = FrontierV3GrayboxLedger.get(level);
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.DEFERRED, "native preimage is not an obstruction before opening projection");
        ledger.worksite(new FrontierV3WorksiteBlockWitness(opening, stone, FrontierV3WorksiteBlockWitness.Phase.PREPARED));
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.DEFERRED, "prepared opening still requires settled physical evidence");
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        ledger.worksite(new FrontierV3WorksiteBlockWitness(opening, stone, FrontierV3WorksiteBlockWitness.Phase.SETTLED));
        ledger.worksite(new FrontierV3WorksiteBlockWitness(floor, stone, FrontierV3WorksiteBlockWitness.Phase.SETTLED));
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.READY, "settled exact opening and foundation admit one chest");
        level.setBlock(target, Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT, "an obstruction after settlement remains a genuine conflict");
        helper.assertValueEqual(level.getBlockState(target), Blocks.BLUE_CONCRETE.defaultBlockState(), "validation never overwrites a foreign block");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-container-socket", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ownedSocketDefersBeforeProjectionThenAcceptsOnlyItsExactClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(8, 8, 0));
        GrayboxCell support = new GrayboxCell(new BlockPosition(target.getX(), target.getY() - 1, target.getZ()),
                new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE, new SubjectId("structure:socket-test")), GrayboxMaterial.DEPOT, GrayboxSemanticPart.FOUNDATION);
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
                new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE, new SubjectId("structure:socket-test-drift")), GrayboxMaterial.DEPOT, GrayboxSemanticPart.FOUNDATION);
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

    @GameTest(batch = "pm-frontier-v3-container-socket", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedRecoveryKeepsAnOwnedChestDistinctFromAForeignObstruction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(24, 8, 0));
        SubjectId container = new SubjectId("container:prepared-recovery");
        GrayboxCell support = new GrayboxCell(new BlockPosition(target.getX(), target.getY() - 1, target.getZ()),
                new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, new SubjectId("organ:prepared-recovery")), GrayboxMaterial.HIVE_STORE, GrayboxSemanticPart.HIVE_TISSUE);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        FrontierV3GrayboxExecutor.project(level, ledger, support);

        helper.assertTrue(FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, target, container) != null,
                "the recovery fixture needs one exact owned chest after durable PREPARED");
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.socketReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT,
                "fresh-socket admission must never adopt an already occupied target");
        helper.assertValueEqual(FrontierV3ContainerSurfaceExecutor.supportReadiness(level, ledger, target, support),
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.READY,
                "PREPARED recovery must retain the valid semantic foundation for its owned chest");
        helper.assertTrue(FrontierV3ContainerSurfaceExecutor.activeChest(level, target, container) != null,
                "only the matching durable container identity may resume this prepared lifecycle");
        helper.succeed();
    }
}
