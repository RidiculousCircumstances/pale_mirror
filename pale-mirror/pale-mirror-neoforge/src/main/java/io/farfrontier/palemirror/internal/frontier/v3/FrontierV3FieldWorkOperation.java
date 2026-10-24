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
        var pending = FrontierV3ResourceSiteHarvestReconciliation.ownedWorkPending(
                FrontierV3ResourceSiteLedger.get(level), lease, job, cycle).orElse(null);
        if (pending == null) return Optional.empty();
        // The work owner declares the causal dependency explicitly. Till/sow
        // touch terrain only; do not manufacture a body mutation participant.
        int beforeCount = pending.handEffect().map(FrontierV3ResourceFieldWitness.HandEffect::beforeCount)
                .orElse(ResourceSiteHarvestCargo.quantity(state, job));
        int afterCount = pending.handEffect().map(FrontierV3ResourceFieldWitness.HandEffect::afterCount).orElse(beforeCount);
        long epoch = pending.handEffect().map(FrontierV3ResourceFieldWitness.HandEffect::authorityEpoch)
                .orElseGet(() -> ResourceSiteHarvestCargo.handEpoch(state, job, lease));
        var cell = cycle.layout().cells().get(job.progress().pendingCropSlotIndex());
        var due = FrontierV3TraversalScheduleGate.dueBinding(runtime.executionView().orElseThrow(), job).orElse(null);
        if (due == null) return Optional.empty();
        var binding = binding(level, state, job, worker, cell, pending);
        java.util.function.Supplier<FrontierV3ActorHandObservation.Review> hand = () ->
                FrontierV3ActorHandObservation.observeRetained(level, state, lease, job, worker, identity);
        java.util.function.BooleanSupplier appliedImage = () -> condition(level, cell, pending)
                .equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().after()))
                && hand.get().matchesQuantity(afterCount)
                && toolMatches(worker, job, true)
                && pending.handEffect().map(hand.get()::matches).orElse(true);
        boolean applied = appliedImage.getAsBoolean();
        var effect = new ResourceSiteHarvestProgressed(job.siteId(), cycle.epoch(), job.id(),
                job.progress().completedCropSlots() + 1, cycle.layout().revision(), cell.id(), job.target().generation(),
                cycle.expectedWorkOutcome(cell.id()), due.id(), due.dueAt().ticks(), Optional.of(
                new ResourceSiteHarvestProgressed.HandObservation(new PhysicalStackAddress.ActorHand(job.workerId(), worker.getUUID()),
                        epoch, applied ? afterCount : beforeCount)));
        var retained = FrontierV3OperationAftermath.capture(level, binding.declaration(), binding.participants(),
                new HarvestRetiredEffectObserved(identity, effect, applied), applied,
                () -> applied ? appliedImage.getAsBoolean()
                        : condition(level, cell, pending).equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().before()))
                            && hand.get().matchesQuantity(beforeCount)
                            && toolMatches(worker, job, false)
                            && pending.handEffect().map(hand.get()::matches).orElse(true));
        // Native pre-loot observation cannot defer its exact effect into a later
        // tick: the entity is about to die. Preserve this irreversible barrier.
        if (applied && retained.isPresent()) confirmWitness(level, state, job, cell.id(), hand.get()).persist(level);
        return retained;
    }
    /** Called only after the common operation proves the complete native outcome. */
    static boolean confirmWitness(ServerLevel level, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, ResourceFieldLayout.CellId id) {
        return confirmWitness(level, state, job, id, FrontierV3ActorHandObservation.observe(level, state, lease, job))
                .prepareFieldDurable(level, job.siteId());
    }
    private static FrontierV3ResourceSiteLedger confirmWitness(ServerLevel level, FrontierWorldState state,
            ResourceSiteHarvestJob job, ResourceFieldLayout.CellId id, FrontierV3ActorHandObservation.Review hand) {
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(job.siteId());
        var witness = owner.witness(); var pending = witness.cell(id).pending().orElseThrow();
        if (pending.completedSteps() < pending.transition().steps().size())
            witness = witness.confirm(id, FrontierV3ResourceFieldObservation.observe(level,
                    state.resourceSites().cycle(job.siteId()), witness, id, pending.causationId()));
        if (pending.handEffect().isPresent() && !pending.handConfirmed())
            witness = witness.confirmHand(id, hand);
        if (!witness.equals(owner.witness())) ledger.replaceFieldClaim(owner, owner.withWitness(witness));
        // The caller owns its physical boundary: ordinary work waits without
        // parking the tick; native pre-loot capture must force the exact record.
        return ledger;
    }
    static FrontierV3MinecraftOperation.Result apply(ServerLevel level, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, Mob worker, ResourceFieldLayout.Cell cell,
            FrontierV3ResourceFieldWitness.Pending pending, BlockExtractionPort extraction, Runnable resume) {
        var binding = binding(level, state, job, worker, cell, pending);
        var writes = pending.transition().steps().stream().map(step -> new FrontierV3ManagedBlockWrites.PlannedWrite(
                position(cell, step.part()), block(step))).toList();
        return FrontierV3MinecraftOperation.applyDeferred(level, new FrontierV3MinecraftOperation.Request(binding.declaration(), binding.participants(),
                () -> condition(level, cell, pending).equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().before()))
                        && toolMatches(worker, job, false)
                        && pending.handEffect().map(effect -> FrontierV3ActorHandObservation.observe(level, state, lease, job).matchesBefore(effect)).orElse(true),
                FrontierV3ManagedBlockWrites.effect(level, writes, () -> mutate(level, worker, cell, pending, extraction)
                        && job.equipment().lastUse().map(use -> FrontierV3ToolUse.apply(worker.getMainHandItem(), use)).orElse(true), resume),
                () -> toolMatches(worker, job, true) && after(level, state, lease, job, cell, pending)));
    }
    static Optional<FrontierV3MinecraftOperation.Result> observe(ServerLevel level, FrontierWorldState state, SceneLease lease,
            ResourceSiteHarvestJob job, Mob worker, ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        var binding = binding(level, state, job, worker, cell, pending);
        return FrontierV3MinecraftOperation.observe(level, binding.declaration(), binding.participants(),
                () -> toolMatches(worker, job, true) && after(level, state, lease, job, cell, pending));
    }
    private static boolean after(ServerLevel level, FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        return condition(level, cell, pending).equals(new FrontierV3ResourceFieldObservation.Owned(pending.transition().after()))
                && pending.handEffect().map(effect -> {
                    var hand = FrontierV3ActorHandObservation.observe(level, state, lease, job);
                    return hand.matches(effect) && hand.matchesQuantity(effect.afterCount());
                }).orElse(true);
    }
    private static Binding binding(ServerLevel level, FrontierWorldState state, ResourceSiteHarvestJob job, Mob worker,
            ResourceFieldLayout.Cell cell, FrontierV3ResourceFieldWitness.Pending pending) {
        var participants = new ArrayList<FrontierV3MinecraftOperation.Participant>();
        for (var step : pending.transition().steps()) participants.add(FrontierV3OperationChunks.current(level, position(cell, step.part())));
        if (pending.handEffect().isPresent() || job.equipment().lastUse().isPresent()) participants.add(FrontierV3OperationEntityParticipant.current(worker,
                ActorBodyAuthority.current(state, job.workerId()).physicalEpoch()));
        var intent = FrontierV3ResourceFieldWitness.workIntent(pending);
        job.equipment().lastUse().ifPresent(use -> {
            intent.putString("toolId", use.before().id().value());
            intent.putString("toolKind", use.before().itemKind());
            intent.putInt("toolMaximumDamage", use.before().condition().maximumDamage());
            intent.putInt("toolDamageBefore", use.before().condition().damage());
            intent.putInt("toolWear", use.wear());
        });
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_WORK,
                job.id(), pending.causationId(), FrontierV3OperationFingerprint.of(intent),
                participants.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        return new Binding(declaration, List.copyOf(participants));
    }
    private static boolean toolMatches(Mob worker, ResourceSiteHarvestJob job, boolean after) {
        return job.equipment().lastUse().map(use -> after
                ? FrontierV3ToolUse.after(worker.getMainHandItem(), use)
                : FrontierV3ToolUse.before(worker.getMainHandItem(), use)).orElse(true);
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
                if (!level.setBlock(position(cell, step.part()), block(step), 3)) return false;
            }
        }
        return pending.handEffect().isEmpty() || FrontierV3VillagerHandMutation.setOffhand(worker,
                new ItemStack(Items.WHEAT, pending.handEffect().orElseThrow().afterCount()));
    }
    private static net.minecraft.world.level.block.state.BlockState block(ResourceFieldCellTransition.Step step) {
        return step.part() == ResourceFieldCellTransition.Part.SOIL ? FrontierV3FieldBlocks.soil(step.after().soil())
                : step.after().crop() == ResourceFieldCycle.Crop.ABSENT ? Blocks.AIR.defaultBlockState()
                : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, step.after().growthStage());
    }
    private static BlockPos position(ResourceFieldLayout.Cell cell, ResourceFieldCellTransition.Part part) {
        var value = part == ResourceFieldCellTransition.Part.SOIL ? cell.soil().support() : cell.crop();
        return new BlockPos(value.x(), value.y(), value.z());
    }
}
