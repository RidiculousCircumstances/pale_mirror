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
        return FrontierV3MinecraftOperation.applyBatch(level, List.of(request(level, site, cell, pending))).getFirst();
    }
    static FrontierV3MinecraftOperation.Request request(ServerLevel level, SubjectId site,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        if (pending.canonicalSource().isEmpty() || pending.handEffect().isPresent())
            throw new IllegalArgumentException("projection has no canonical source or owns labour cargo");
        int index = pending.completedSteps();
        var step = pending.transition().steps().get(index);
        var point = step.part() == ResourceFieldCellTransition.Part.SOIL ? cell.soil().support() : cell.crop();
        var position = new BlockPos(point.x(), point.y(), point.z());
        var port = FrontierV3OperationChunks.current(level, position);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                site, pending.causationId() + "/step/" + index,
                FrontierV3OperationFingerprint.of(FrontierV3ResourceFieldWitness.projectionIntent(pending)),
                java.util.Set.of(port.identity()));
        var next = step.part() == ResourceFieldCellTransition.Part.SOIL ? FrontierV3FieldBlocks.soil(step.after().soil())
                : step.after().crop() == ResourceFieldCycle.Crop.ABSENT ? Blocks.AIR.defaultBlockState()
                : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, step.after().growthStage());
        return new FrontierV3MinecraftOperation.Request(declaration, List.of(port),
                () -> FrontierV3ResourceFieldObservation.read(level, cell, pending.causationId())
                        .equals(new FrontierV3ResourceFieldObservation.Owned(step.before())),
                () -> level.setBlock(position, next, 3),
                () -> FrontierV3ResourceFieldObservation.read(level, cell, pending.causationId())
                        .equals(new FrontierV3ResourceFieldObservation.Owned(step.after())));
    }
}
