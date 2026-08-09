package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Executes at most one persisted operation per call. Every operation verifies
 * its physical postcondition before it is advanced in its job.
 */
public final class TestMineMaterializer {
    private static final String OVERLAY_BLOCK = "minecraft:netherrack";

    public boolean executeNext(ServerLevel level, TestMineRecord mine, MaterializationJob job) {
        if (!level.hasChunkAt(mine.anchor())) return false;
        if (job.state() == JobState.COMPLETED || job.state() == JobState.BLOCKED) return job.state() == JobState.COMPLETED;
        if (job.state() == JobState.PLANNED) job.start();
        MaterializationOperation operation = job.nextOperation();
        if (operation == null) {
            job.complete();
            return true;
        }
        if (operation.state() == OperationState.BLOCKED) {
            job.block(operation.lastError());
            return false;
        }
        if (operation.state() != OperationState.COMPLETED) {
            operation.start();
            String error = execute(level, mine, job, operation.type());
            if (error != null) {
                operation.block(error);
                job.block(error);
                return false;
            }
            operation.complete();
        }
        job.advanceOperation();
        if (job.nextOperation() == null) {
            job.complete();
            mine.object().setLifecycle(operation.type() == MaterializationOperationType.ENSURE_TEST_THREAT_CONTROLLER
                    ? WorldObjectLifecycle.ACTIVE : WorldObjectLifecycle.REPRESENTED);
        }
        return job.state() == JobState.COMPLETED;
    }

    private String execute(ServerLevel level, TestMineRecord mine, MaterializationJob job, MaterializationOperationType type) {
        return switch (type) {
            case ENSURE_OVERLAY -> ensureOverlay(level, mine);
            case ENSURE_TEST_THREAT_CONTROLLER -> ensureController(level, mine, job.jobId());
            case REMOVE_TEST_THREAT_CONTROLLER -> removeController(level, mine);
            case REMOVE_OVERLAY -> removeOverlay(level, mine);
        };
    }

    private String ensureOverlay(ServerLevel level, TestMineRecord mine) {
        for (MutableCell cell : mine.mutableCells()) {
            if (cell.conflicted()) return "Mutable cell " + cell.position() + " is conflicted";
            String current = blockId(level, cell.position());
            if (!current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                return "Mutable cell " + cell.position() + " was changed outside Pale Mirror";
            }
            if (!current.equals(OVERLAY_BLOCK)) level.setBlock(cell.position(), Blocks.NETHERRACK.defaultBlockState(), 3);
            cell.markApplied(OVERLAY_BLOCK);
        }
        return overlaysMatch(level, mine) ? null : "Overlay postcondition failed";
    }

    private String ensureController(ServerLevel level, TestMineRecord mine, String jobId) {
        if (!AdapterRegistry.testThreat().ensureController(level, mine, jobId)) return "Could not create TestThreat controller";
        return AdapterRegistry.testThreat().hasController(level, mine) ? null : "TestThreat controller postcondition failed";
    }

    private String removeController(ServerLevel level, TestMineRecord mine) {
        AdapterRegistry.testThreat().removeController(level, mine);
        return mine.controllerId() == null && !AdapterRegistry.testThreat().hasController(level, mine)
                ? null : "TestThreat controller removal postcondition failed";
    }

    private String removeOverlay(ServerLevel level, TestMineRecord mine) {
        for (MutableCell cell : mine.mutableCells()) {
            if (cell.conflicted()) return "Mutable cell " + cell.position() + " is conflicted";
            String current = blockId(level, cell.position());
            if (!current.equals(cell.lastAppliedBlock()) && !current.equals(cell.baselineBlock())) {
                cell.conflict();
                return "Mutable cell " + cell.position() + " was changed outside Pale Mirror";
            }
            if (!current.equals(cell.baselineBlock())) {
                Block baseline = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(cell.baselineBlock()));
                level.setBlock(cell.position(), baseline.defaultBlockState(), 3);
            }
            cell.markApplied(cell.baselineBlock());
        }
        return baselinesMatch(level, mine) ? null : "Overlay removal postcondition failed";
    }

    private static boolean overlaysMatch(ServerLevel level, TestMineRecord mine) {
        return mine.mutableCells().stream().allMatch(cell -> blockId(level, cell.position()).equals(OVERLAY_BLOCK));
    }

    private static boolean baselinesMatch(ServerLevel level, TestMineRecord mine) {
        return mine.mutableCells().stream().allMatch(cell -> blockId(level, cell.position()).equals(cell.baselineBlock()));
    }

    private static String blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
}
