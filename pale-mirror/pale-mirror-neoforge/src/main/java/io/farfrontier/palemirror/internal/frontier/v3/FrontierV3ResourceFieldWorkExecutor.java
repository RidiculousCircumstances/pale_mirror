package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCargo;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgressed;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** One durable physical farmer-cell step per turn; canonical yield follows observed blocks and offhand. */
final class FrontierV3ResourceFieldWorkExecutor {
    enum Disposition { PENDING, READY, CONFLICT }
    record Result(Disposition disposition, ResourceFieldCycle.WorkOutcome outcome,
                  Optional<ResourceSiteHarvestProgressed.HandObservation> hand, String failure, Optional<java.util.UUID> operation) {
        static Result pending() { return new Result(Disposition.PENDING, null, Optional.empty(), "", Optional.empty()); }
        static Result conflict(String failure) { return new Result(Disposition.CONFLICT, null, Optional.empty(), failure, Optional.empty()); }
        static Result ready(ResourceFieldCycle.WorkOutcome outcome, ResourceSiteHarvestProgressed.HandObservation hand) {
            return new Result(Disposition.READY, outcome, Optional.of(hand), "", Optional.empty());
        }
        Result withOperation(java.util.UUID ticket) { return new Result(disposition, outcome, hand, failure, Optional.of(ticket)); }
    }

    private FrontierV3ResourceFieldWorkExecutor() { }

    static String cause(ResourceSiteHarvestJob job, ResourceFieldCycle cycle, ResourceFieldLayout.CellId id) {
        return io.farfrontier.palemirror.frontier.v3.model.FieldCellOperation.id(job.id(), cycle.epoch(), id, job.target().generation());
    }

    static Result advance(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                          FrontierWorldState state, SceneLease lease,
                          ResourceSiteHarvestJob job, Mob worker) {
        if (!job.progress().hasPendingCrop() || lease.members().size() != 1
                || !lease.members().getFirst().actorId().equals(job.workerId())
                || !lease.members().getFirst().entityId().equals(worker.getUUID())
                || !FrontierV3ActorHandObservation.ownsCurrentHarvest(state, lease, job))
            return Result.conflict("harvest-owner-mismatch");
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        if (state.resourceSites().harvestMutationPending(job)) return Result.pending();
        ResourceFieldLayout.Cell cell = cycle.layout().cells().get(job.progress().pendingCropSlotIndex());
        ResourceFieldLayout.CellId id = cell.id();
        if (cycle.pendingPlayerBreaks().containsKey(id)) return Result.conflict("player-break-pending");
        ResourceFieldCycle.WorkOutcome outcome;
        try { outcome = cycle.expectedWorkOutcome(id); }
        catch (IllegalArgumentException invalid) { return Result.conflict("work-outcome-invalid"); }
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        if (!(ledger.fieldClaim(job.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return Result.conflict("field-owner-mismatch");
        FrontierV3ResourceFieldWitness witness = owner.witness();
        FrontierV3ResourceFieldWitness.Cell retained = witness.cell(id);
        if (retained.foreign().isPresent() && outcome != ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED)
            return Result.conflict("foreign-cell-with-owned-outcome");
        Optional<ResourceFieldCellTransition> transition = cycle.physicalWorkTransition(id);
        if (transition.isEmpty()) {
            var observed = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, id, cause(job, cycle, id));
            if (observed.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.pending();
            if (outcome == ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED) {
                if (!(observed.reading() instanceof FrontierV3ResourceFieldObservation.Foreign foreign)
                        || retained.foreign().isEmpty()
                        || !retained.foreign().orElseThrow().observedSoil().equals(foreign.incident().observedSoil())
                        || !retained.foreign().orElseThrow().observedCrop().equals(foreign.incident().observedCrop()))
                    return Result.conflict("blocked-cell-foreign-witness-mismatch");
            } else if (retained.pending().isPresent()
                    || observed.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                return observeInterruption(level, runtime, job, id, observed, "unprepared-cell-");
            return handResult(level, state, lease, job, outcome, ResourceSiteHarvestCargo.quantity(state, job));
        }
        // A durable growth projection belongs to the field owner, not to this worker.
        // Settle it before admitting a paired crop/hand effect; restart may retain it
        // between its prepare, write and acknowledgement boundaries.
        if (retained.pending().filter(pending -> pending.canonicalSource().isPresent()).isPresent())
            return projectPredecessor(level, runtime, job, id);
        if (retained.pending().isEmpty()) {
            var before = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, id, cause(job, cycle, id));
            if (before.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.pending();
            if (before.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                return observeInterruption(level, runtime, job, id, before, "cell-before-");
            // Physical CURRENT can lag canonical maturity. The existing field projector
            // owns the catch-up; harvesting an old block must not mint canonical yield.
            if (!before.matchesWorkPredecessor(witness, transition.orElseThrow()))
                return projectPredecessor(level, runtime, job, id);
            if (outcome == ResourceFieldCycle.WorkOutcome.HARVESTED) {
                var handBefore = FrontierV3ActorHandObservation.observe(level, state, lease, job);
                // Preparation reads standard Minecraft loot. Persist the complete result
                // with the existing crop/hand witness BEFORE either physical effect.
                var extraction = extractionPort(level, runtime, state, lease, worker, job).prepare(
                        cause(job, cycle, id), state.actorExecutions().current(
                            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId()), cell.crop(),
                        io.farfrontier.palemirror.frontier.v3.model.FieldHarvestExtraction.definition(state.bootstrap(), cell.crop()));
                var effect = new FrontierV3ResourceFieldWitness.HandEffect(job.siteId(), job.id(), job.workerId(),
                        lease.members().getFirst().entityId(), ResourceSiteHarvestCargo.handEpoch(state, job, lease),
                        ResourceSiteHarvestCargo.quantity(state, job), extraction);
                if (!handBefore.matchesBefore(effect))
                    return Result.conflict("hand-before-" + handBefore.disposition().name().toLowerCase(java.util.Locale.ROOT));
                witness = witness.beginHarvest(transition.orElseThrow(), cause(job, cycle, id), effect, before, handBefore);
            } else {
                witness = witness.begin(transition.orElseThrow(), cause(job, cycle, id));
            }
            ledger.replaceFieldClaim(owner, owner.withWitness(witness));
            ledger.persist(level);
            return Result.pending();
        }
        var pending = retained.pending().orElseThrow();
        if (pending.handEffect().isPresent()) {
            var extraction = pending.handEffect().orElseThrow().extraction();
            var execution = state.actorExecutions().current(
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId());
            if (!extraction.target().equals(cell.crop()) || !extraction.operationId().equals(pending.causationId())
                    || !extraction.execution().equals(execution))
                return Result.conflict("extraction-target-operation-or-execution-mismatch");
        }
        if (!pending.causationId().equals(cause(job, cycle, id))
                || !pending.transition().equals(transition.orElseThrow())
                || pending.canonicalSource().isPresent()
                || pending.handEffect().isPresent() != (outcome == ResourceFieldCycle.WorkOutcome.HARVESTED))
            return Result.conflict("pending-cell-cause-or-transition-mismatch;expectedCause=" + cause(job, cycle, id)
                    + ";actualCause=" + pending.causationId() + ";cell=" + id.value());
        var review = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, id, cause(job, cycle, id));
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.pending();
        var operation = FrontierV3FieldWorkOperation.apply(level, state, lease, job, worker, cell, pending,
                extractionPort(level, runtime, state, lease, worker, job));
        if (operation.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY) return Result.pending();
        if (!operation.complete()) return Result.conflict("cell-operation-" + operation.status().name().toLowerCase(java.util.Locale.ROOT));
        // The common receipt, not a matching block/count, authorizes advancement of
        // the family's bounded cell witness after a restart.
        FrontierV3FieldWorkOperation.confirmWitness(level, state, lease, job, id);
        var result = handResult(level, state, lease, job, outcome, pending.handEffect()
                .map(FrontierV3ResourceFieldWitness.HandEffect::afterCount).orElse(ResourceSiteHarvestCargo.quantity(state, job)));
        return result.disposition() == Disposition.READY ? result.withOperation(operation.ticket()) : result;
    }

    /** The preceding physical cause remains retained until its canonical cell is durably accepted. */

    private static Result handResult(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                     ResourceSiteHarvestJob job, ResourceFieldCycle.WorkOutcome outcome, int count) {
        var observed = FrontierV3ActorHandObservation.observe(level, state, lease, job);
        if (count == 0 && observed.disposition() != FrontierV3ActorHandObservation.Disposition.EMPTY
                || count > 0 && (observed.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT
                || observed.stack().orElseThrow().quantity() != count))
            return Result.conflict("hand-result-" + observed.disposition().name().toLowerCase(java.util.Locale.ROOT));
        return Result.ready(outcome, new ResourceSiteHarvestProgressed.HandObservation(
                new PhysicalStackAddress.ActorHand(job.workerId(), lease.members().getFirst().entityId()),
                ResourceSiteHarvestCargo.handEpoch(state, job, lease), count));
    }

    private static Result observeInterruption(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               ResourceSiteHarvestJob job, ResourceFieldLayout.CellId id,
                                               FrontierV3ResourceFieldObservation.Review review, String prefix) {
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT
                && FrontierV3ResourceFieldWorldChangeExecutor.observeOne(level, runtime, job.siteId(), id))
            return Result.pending();
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT) {
            var projection = FrontierV3ResourceFieldGrowthProjector.projectCurrentOne(level, runtime, job.siteId(), id);
            if (projection == FrontierV3ResourceFieldGrowthProjector.Result.CURRENT
                    || projection == FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED) return Result.pending();
        }
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.FOREIGN
                && FrontierV3ResourceFieldForeignChangeExecutor.observeOne(level, runtime, job.siteId(), id))
            return Result.pending();
        return Result.conflict(prefix + review.disposition().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Result projectPredecessor(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                              ResourceSiteHarvestJob job, ResourceFieldLayout.CellId id) {
        var projection = FrontierV3ResourceFieldGrowthProjector.projectCurrentOne(level, runtime, job.siteId(), id);
        return switch (projection) {
            case CURRENT, ADVANCED, UNLOADED, PENDING_WORK, PENDING_PLAYER, STALE_CANONICAL, RETRY -> Result.pending();
            case NEEDS_WORK, PHYSICAL_CONFLICT -> Result.conflict("work-predecessor-projection-"
                    + projection.name().toLowerCase(java.util.Locale.ROOT) + ":site=" + job.siteId().value()
                    + ":job=" + job.id().value() + ":cell=" + id.value());
        };
    }

    private static BlockPos minecraft(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private static io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtractionPort extractionPort(
            ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, SceneLease lease, Mob worker, ResourceSiteHarvestJob job) {
        var execution = state.actorExecutions().current(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        if (execution == null || !execution.activityOwnerId().equals(job.id()))
            throw new IllegalArgumentException("extraction lost its exact field execution");
        return new FrontierV3MinecraftBlockExtraction(level, worker,
                FrontierV3ResourceSiteHarvestSceneExecutor.workActuation(state, runtime, lease, job, worker));
    }

    private static BlockState block(ResourceFieldCellTransition.Step step) {
        return switch (step.part()) {
            case SOIL -> FrontierV3FieldBlocks.soil(step.after().soil());
            case CROP -> step.after().crop() == ResourceFieldCycle.Crop.ABSENT
                    ? Blocks.AIR.defaultBlockState()
                    : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, step.after().growthStage());
        };
    }
}
