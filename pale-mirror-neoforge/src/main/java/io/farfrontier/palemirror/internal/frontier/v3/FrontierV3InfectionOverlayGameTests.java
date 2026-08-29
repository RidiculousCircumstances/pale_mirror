package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Loaded-world correctness and recovery checks for one complete infection surface patch. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3InfectionOverlayGameTests {
    private FrontierV3InfectionOverlayGameTests() { }

    @GameTest(batch = "pm-frontier-v3-infection-overlay", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void infectionOverlayUsesFreshAirThenRetreatsOrPreservesForeignConflict(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos provisional = helper.absolutePos(new BlockPos(48, 8, 0));
        InfectionCell cell = cell(provisional); InfectionOverlayCell desired = new InfectionOverlayCell(cell, InfectionOverlayStage.BLOOM);
        List<BlockPos> expectedPatch = patch(provisional);
        expectedPatch.forEach(position -> { level.setBlock(position, Blocks.AIR.defaultBlockState(), 3); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); });
        FrontierWorldState state = state("frontier:game-test-infection", 91L);
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);

        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state), FrontierV3InfectionOverlayExecutor.ProjectionResult.APPLIED,
                "a sparse infection cell claims its complete fresh-air surface patch");
        List<BlockPos> materialized = ledger.claim(cell).blockPositions();
        helper.assertValueEqual(materialized.size(), FrontierV3InfectionOverlayLedger.PATCH_COLUMNS, "one canonical infection cell retains every physical surface column");
        helper.assertTrue(materialized.stream().allMatch(position -> level.getBlockState(position).is(Blocks.MAGENTA_CARPET)), "the bloom stage is an obvious foreign graybox surface patch");
        net.minecraft.nbt.CompoundTag serialized = ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        ledger = FrontierV3InfectionOverlayLedger.load(serialized, level.registryAccess());
        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.reconcileRetraction(level, ledger, java.util.Map.entry(cell, ledger.claim(cell)), state),
                FrontierV3InfectionOverlayExecutor.ProjectionResult.RETRACTED, "canonical retreat restores only the exact owned air-baseline marker after SavedData reload");
        helper.assertTrue(materialized.stream().allMatch(position -> level.getBlockState(position).isAir()), "retraction leaves the captured air baseline, not a terrain rewrite");

        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state), FrontierV3InfectionOverlayExecutor.ProjectionResult.APPLIED,
                "the same cell may return after a clean retreat");
        BlockPos marker = materialized.getFirst(); level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state), FrontierV3InfectionOverlayExecutor.ProjectionResult.CONFLICT,
                "a later player/world change is terminal provenance rather than repaint permission");
        helper.assertTrue(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK), "the materializer never overwrites the foreign replacement");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-infection-overlay", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void interruptedPreparedInfectionPatchNeverStacksOrCompletesAfterRestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(64, 8, 0));
        InfectionCell cell = cell(origin); InfectionOverlayCell desired = new InfectionOverlayCell(cell, InfectionOverlayStage.BLOOM);
        List<BlockPos> patch = patch(origin);
        patch.forEach(position -> { level.setBlock(position, Blocks.AIR.defaultBlockState(), 3); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); });
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        ledger.prepare(cell, patch, InfectionOverlayStage.BLOOM);
        BlockPos interrupted = patch.getFirst(); level.setBlock(interrupted, FrontierV3InfectionOverlayExecutor.material(InfectionOverlayStage.BLOOM), 3);
        ledger = FrontierV3InfectionOverlayLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());

        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state("frontier:game-test-interrupted-infection", 92L)),
                FrontierV3InfectionOverlayExecutor.ProjectionResult.CONFLICT, "a restart with an interrupted patch fails closed instead of completing unknown columns");
        helper.assertTrue(ledger.claim(cell).conflicted(), "the exact prepared provenance remains visible conflict evidence");
        helper.assertTrue(level.getBlockState(interrupted).is(Blocks.MAGENTA_CARPET) && level.getBlockState(interrupted.above()).isAir()
                        && patch.subList(1, patch.size()).stream().allMatch(position -> level.getBlockState(position).isAir()),
                "recovery neither removes the physical trace nor stacks or paints the remaining columns");
        helper.succeed();
    }

    private static InfectionCell cell(BlockPos origin) { return new InfectionCell(Math.floorDiv(origin.getX(), InfectionCell.BLOCKS), Math.floorDiv(origin.getZ(), InfectionCell.BLOCKS)); }
    private static List<BlockPos> patch(BlockPos origin) {
        int x = Math.multiplyExact(Math.floorDiv(origin.getX(), InfectionCell.BLOCKS), InfectionCell.BLOCKS);
        int z = Math.multiplyExact(Math.floorDiv(origin.getZ(), InfectionCell.BLOCKS), InfectionCell.BLOCKS);
        return java.util.stream.IntStream.range(0, FrontierV3InfectionOverlayLedger.PATCH_COLUMNS)
                .mapToObj(index -> new BlockPos(x + index % InfectionCell.BLOCKS, origin.getY(), z + index / InfectionCell.BLOCKS)).toList();
    }
    private static FrontierWorldState state(String world, long seed) { return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), seed)); }
}
