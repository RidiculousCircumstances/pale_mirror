package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Executor with defensive per-cell preconditions. It never overwrites unknown changes. */
public final class TestMineMaterializer {
    private static final String POLICY_ID = "pale_mirror:test_threat";
    private static final String POLICY_VERSION = "1";
    private static final String OVERLAY_BLOCK = "minecraft:netherrack";

    public void reconcile(ServerLevel level, FacilityState facility, TestMineRecord mine) {
        if (!level.hasChunkAt(mine.anchor())) return;
        if (mine.job() == null || !mine.job().isFor(facility.desiredRevision())) {
            mine.setJob(new MaterializationJob("pm:job:" + mine.id().value() + ":" + facility.desiredRevision(),
                    facility.desiredRevision(), POLICY_ID, POLICY_VERSION, JobState.PLANNED, 0, ""));
        }
        MaterializationJob job = mine.job();
        if (job.state() == JobState.COMPLETED) return;
        job.start();
        if (facility.status() == FacilityStatus.INFECTED) {
            applyOverlay(level, mine);
            if (!AdapterRegistry.testThreat().ensureController(level, mine, job.jobId())) {
                job.block("Could not create TestThreat controller");
                return;
            }
        } else {
            removeOverlay(level, mine);
            AdapterRegistry.testThreat().removeController(level, mine);
        }
        job.complete();
        facility.setObservedRevision(facility.desiredRevision());
    }

    private void applyOverlay(ServerLevel level, TestMineRecord mine) {
        for (MutableCell cell : mine.mutableCells()) {
            if (cell.conflicted()) continue;
            String current = blockId(level, cell.position());
            if (!current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                continue;
            }
            level.setBlock(cell.position(), Blocks.NETHERRACK.defaultBlockState(), 3);
            cell.markApplied(OVERLAY_BLOCK);
        }
    }

    private void removeOverlay(ServerLevel level, TestMineRecord mine) {
        for (MutableCell cell : mine.mutableCells()) {
            if (cell.conflicted() || !blockId(level, cell.position()).equals(cell.lastAppliedBlock())) continue;
            Block baseline = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(cell.baselineBlock()));
            level.setBlock(cell.position(), baseline.defaultBlockState(), 3);
            cell.markApplied(cell.baselineBlock());
        }
    }

    private static String blockId(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
}
