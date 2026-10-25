package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import java.util.List;

/** Canonical-first field projection adapter, distinct from labour and external edits. */
final class FrontierV3FieldProjectionOperation {
    private FrontierV3FieldProjectionOperation() { }
    static FrontierV3MinecraftOperation.Result apply(ServerLevel level, SubjectId site,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        return FrontierV3MinecraftOperation.applyBatch(level, List.of(request(level, site, cell, pending, () -> { }))).getFirst();
    }
    static FrontierV3MinecraftOperation.Request request(ServerLevel level, SubjectId site,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending, Runnable resume) {
        if (pending.canonicalSource().isEmpty() || pending.handEffect().isPresent())
            throw new IllegalArgumentException("projection has no canonical source or owns labour cargo");
        int index = pending.completedSteps();
        var step = pending.transition().steps().get(index);
        var write = plannedWrite(cell, pending);
        var position = write.position();
        var port = FrontierV3OperationChunks.current(level, position);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                site, pending.causationId() + "/step/" + index,
                FrontierV3OperationFingerprint.of(FrontierV3ResourceFieldWitness.projectionIntent(pending)),
                java.util.Set.of(port.identity()));
        return new FrontierV3MinecraftOperation.Request(declaration, List.of(port),
                () -> FrontierV3ResourceFieldObservation.read(level, cell, pending.causationId())
                        .equals(new FrontierV3ResourceFieldObservation.Owned(step.before())),
                FrontierV3ProjectionWriteBudget.admit(level, FrontierV3ManagedBlockWrites.effect(level,
                        List.of(write), () -> level.setBlock(position, write.replacement(), 3), resume)),
                () -> FrontierV3ResourceFieldObservation.read(level, cell, pending.causationId())
                        .equals(new FrontierV3ResourceFieldObservation.Owned(step.after())));
    }
    /** Same declared fence owner as static/initial projection, amortized across this exact window. */
    static boolean prepareBatch(ServerLevel level, ResourceSite site, FrontierV3ResourceFieldWitness witness,
            java.util.Collection<ResourceFieldLayout.CellId> cells, Runnable resume) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(level.getServer());
        if (runtime == null) return false;
        var writes = cells.stream().map(id -> plannedWrite(site.layout().requireCell(id),
                witness.cell(id).pending().orElseThrow())).toList();
        return FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, writes, resume);
    }
    private static FrontierV3ManagedBlockWrites.PlannedWrite plannedWrite(ResourceFieldLayout.Cell cell,
            FrontierV3ResourceFieldWitness.Pending pending) {
        var step = pending.transition().steps().get(pending.completedSteps());
        var point = step.part() == ResourceFieldCellTransition.Part.SOIL ? cell.soil().support() : cell.crop();
        var next = step.part() == ResourceFieldCellTransition.Part.SOIL ? FrontierV3FieldBlocks.soil(step.after().soil())
                : step.after().crop() == ResourceFieldCycle.Crop.ABSENT ? Blocks.AIR.defaultBlockState()
                : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, step.after().growthStage());
        return new FrontierV3ManagedBlockWrites.PlannedWrite(new BlockPos(point.x(), point.y(), point.z()), next);
    }
}
