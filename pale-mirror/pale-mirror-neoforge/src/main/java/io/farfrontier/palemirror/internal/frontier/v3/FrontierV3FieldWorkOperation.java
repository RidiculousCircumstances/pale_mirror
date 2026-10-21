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
    static Optional<FrontierV3OperationAftermath> deathEffect(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, Mob worker,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId identity) {
        var cycle = state.resourceSites().cycle(job.siteId());
        var pending = FrontierV3ResourceSiteHarvestReconciliation.ownedPending(
                FrontierV3ResourceSiteLedger.get(level), lease, job, cycle).orElse(null);
        if (pending == null) return Optional.empty();
        // Only a real hand participant can share the fatality cancellation gate.
        // Terrain-only sow/till effects retain their independent operation owner.
        var hand = pending.handEffect().orElseThrow();
        var cell = cycle.layout().cells().get(job.progress().pendingCropSlotIndex());
        var due = FrontierV3TraversalScheduleGate.dueBinding(runtime.executionView().orElseThrow(), job).orElse(null);
        if (due == null) return Optional.empty();
        var binding = binding(level, state, job, worker, cell, pending);
        boolean applied = after(level, state, lease, job, cell, pending);
        var effect = new ResourceSiteHarvestProgressed(job.siteId(), cycle.epoch(), job.id(),
                job.progress().completedCropSlots() + 1, cycle.layout().revision(), cell.id(), job.target().generation(),
                cycle.expectedWorkOutcome(cell.id()), due.id(), due.dueAt().ticks(), Optional.of(
                new ResourceSiteHarvestProgressed.HandObservation(new PhysicalStackAddress.ActorHand(job.workerId(), worker.getUUID()),
                        hand.authorityEpoch(), applied ? hand.afterCount() : hand.beforeCount())));
        var retained = FrontierV3OperationAftermath.capture(level, binding.declaration(), binding.participants(),
                new HarvestRetiredEffectObserved(identity, effect, applied), applied,
                () -> applied ? after(level, state, lease, job, cell, pending)
                        : condition(level, cell, pending).equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().before()))
                            && FrontierV3ActorHandObservation.observe(level, state, lease, job).matchesBefore(hand));
        if (applied && retained.isPresent()) confirmWitness(level, state, lease, job, cell.id());
        return retained;
    }
    /** Called only after the common operation proves the complete native outcome. */
    static void confirmWitness(ServerLevel level, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, ResourceFieldLayout.CellId id) {
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(job.siteId());
        var witness = owner.witness(); var pending = witness.cell(id).pending().orElseThrow();
        if (pending.completedSteps() < pending.transition().steps().size())
            witness = witness.confirm(id, FrontierV3ResourceFieldObservation.observe(level,
                    state.resourceSites().cycle(job.siteId()), witness, id, pending.causationId()));
        if (pending.handEffect().isPresent() && !pending.handConfirmed())
            witness = witness.confirmHand(id, FrontierV3ActorHandObservation.observe(level, state, lease, job));
        if (!witness.equals(owner.witness())) { ledger.replaceFieldClaim(owner, owner.withWitness(witness)); ledger.persist(level); }
    }
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
