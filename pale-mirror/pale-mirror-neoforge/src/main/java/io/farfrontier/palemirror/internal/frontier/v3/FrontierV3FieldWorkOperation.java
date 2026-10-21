package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtractionPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Field-owned adapter for one admitted cell action. Labour time and target choice
 * stay in the field process; common integration owns persistence and replay safety. */
final class FrontierV3FieldWorkOperation {
    private record Binding(FrontierV3OperationReceipt.Declaration declaration, List<FrontierV3MinecraftOperation.Participant> participants) { }
    private FrontierV3FieldWorkOperation() { }
    static FrontierV3MinecraftOperation.Result apply(ServerLevel level, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, Mob worker, ResourceFieldLayout.Cell cell,
            FrontierV3ResourceFieldWitness.Pending pending, BlockExtractionPort extraction) {
        var binding = binding(level, state, job, worker, cell, pending);
        return FrontierV3MinecraftOperation.apply(level, binding.declaration(), binding.participants(),
                () -> condition(level, cell, pending).equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().before()))
                        && pending.handEffect().map(effect -> FrontierV3ActorHandObservation.observe(level, state, lease, job).matchesBefore(effect)).orElse(true),
                () -> mutate(level, worker, cell, pending, extraction),
                () -> after(level, state, lease, job, cell, pending));
    }
    static Optional<FrontierV3MinecraftOperation.Result> observe(ServerLevel level, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, Mob worker, ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        var binding = binding(level, state, job, worker, cell, pending);
        return FrontierV3MinecraftOperation.observe(level, binding.declaration(), binding.participants(),
                () -> after(level, state, lease, job, cell, pending));
    }
    private static boolean after(ServerLevel level, FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        return condition(level, cell, pending).equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().after()))
                && pending.handEffect().map(effect -> FrontierV3ActorHandObservation.observe(level, state, lease, job).matches(effect)).orElse(true);
    }
    private static Binding binding(ServerLevel level, FrontierWorldState state, ResourceSiteHarvestJob job, Mob worker,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        var participants = new ArrayList<FrontierV3MinecraftOperation.Participant>();
        for (var step : pending.transition().steps()) participants.add(FrontierV3OperationChunks.current(level, position(cell, step.part())));
        if (pending.handEffect().isPresent()) participants.add(FrontierV3OperationEntityParticipant.current(worker,
                ActorBodyAuthority.current(state, job.workerId()).physicalEpoch()));
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_WORK,
                job.id(), pending.causationId(), FrontierV3OperationFingerprint.of(FrontierV3ResourceFieldWitness.workIntent(pending)),
                participants.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        return new Binding(declaration, List.copyOf(participants));
    }
    private static FrontierV3ResourceFieldObservation.Reading condition(ServerLevel level, ResourceFieldLayout.Cell cell,
            FrontierV3ResourceFieldWitness.Pending pending) {
        return FrontierV3ResourceFieldObservation.read(level, cell, pending.causationId());
    }
    private static boolean mutate(ServerLevel level, Mob worker, ResourceFieldLayout.Cell cell,
            FrontierV3ResourceFieldWitness.Pending pending, BlockExtractionPort extraction) {
        for (var step : pending.transition().steps()) {
            if (pending.handEffect().isPresent()) {
                var result = extraction.apply(pending.handEffect().orElseThrow().extraction());
                if (result != BlockExtractionPort.Result.APPLIED) return false;
            } else {
                var block = step.part() == ResourceFieldCellTransition.Part.SOIL ? FrontierV3FieldBlocks.soil(step.after().soil())
                        : step.after().crop() == ResourceFieldCycle.Crop.ABSENT ? Blocks.AIR.defaultBlockState()
                        : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, step.after().growthStage());
                if (!level.setBlock(position(cell, step.part()), block, 3)) return false;
            }
        }
        return pending.handEffect().isEmpty() || FrontierV3VillagerHandMutation.setOffhand(worker,
                new ItemStack(Items.WHEAT, pending.handEffect().orElseThrow().afterCount()));
    }
    private static BlockPos position(ResourceFieldLayout.Cell cell, ResourceFieldCellTransition.Part part) {
        var value = part == ResourceFieldCellTransition.Part.SOIL ? cell.soil().support() : cell.crop();
        return new BlockPos(value.x(), value.y(), value.z());
    }
}
