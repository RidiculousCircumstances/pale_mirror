package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** One naturally loaded, canonical-first field cell per call; never guesses physical work or local damage. */
final class FrontierV3ResourceFieldGrowthProjector {
    enum Result { CURRENT, ADVANCED, UNLOADED, PENDING_WORK, PENDING_PLAYER, NEEDS_WORK,
        STALE_CANONICAL, PHYSICAL_CONFLICT, RETRY }

    private FrontierV3ResourceFieldGrowthProjector() { }

    /** Production entry: authority is the live engine revision in the one physical Frontier world. */
    static Result projectCurrentOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    SubjectId siteId, ResourceFieldLayout.CellId cellId) {
        Objects.requireNonNull(level, "growth projection world");
        Objects.requireNonNull(runtime, "growth projection runtime");
        Objects.requireNonNull(siteId, "growth projection site identity");
        Objects.requireNonNull(cellId, "growth projection cell identity");
        if (!FrontierV3PhysicalWorld.isPhysical(level))
            throw new IllegalArgumentException("growth projection has a foreign physical dimension");
        if (!FrontierV3ServerLifecycle.ownsRuntime(level, runtime))
            throw new IllegalArgumentException("growth projection has a foreign server runtime");
        FrontierCanonicalState<FrontierWorldState> current = runtime.canonicalState().orElse(null);
        if (current == null) return Result.STALE_CANONICAL;
        if (!FrontierV3PhysicalWorld.WORLD_ID.equals(current.worldId()))
            throw new IllegalArgumentException("growth projection runtime owns another Frontier world");
        ResourceSite site = current.state().resourceSite(siteId);
        ResourceFieldCycle cycle = current.state().resourceSites().cycle(siteId);
        return projectAccepted(level, site,
                new FrontierCanonicalState<>(current.worldId(), current.revision(), current.instant(), cycle), cellId);
    }

    /** Internal executor core; production admission must enter through the live-runtime boundary above. */
    static Result projectAccepted(ServerLevel level, ResourceSite site,
                             FrontierCanonicalState<ResourceFieldCycle> accepted,
                             ResourceFieldLayout.CellId cellId) {
        Objects.requireNonNull(level, "growth projection world");
        Objects.requireNonNull(site, "growth projection site");
        Objects.requireNonNull(accepted, "growth projection canonical state");
        Objects.requireNonNull(cellId, "growth projection cell");
        ResourceFieldCycle cycle = accepted.state();
        if (!site.id().equals(cycle.siteId())
                || !site.layout().fingerprint().equals(cycle.layout().fingerprint()))
            throw new IllegalArgumentException("growth projection has a foreign site or layout");
        ResourceFieldLayout.Cell geometry = cycle.layout().requireCell(cellId);
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        if (!(ledger.fieldClaim(site.id()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner))
            throw new IllegalStateException("growth projection has no active cell-owned field");
        if (owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return Result.PHYSICAL_CONFLICT;
        FrontierV3ResourceFieldWitness witness = owner.witness();
        if (!witness.matchesCycle(cycle)) return Result.STALE_CANONICAL;
        if (cycle.pendingPlayerBreaks().containsKey(cellId)) return Result.PENDING_PLAYER;
        var canonicalCell = cycle.cell(cellId);
        if (canonicalCell.soil() != ResourceFieldCycle.Soil.FARMLAND
                || canonicalCell.crop() != ResourceFieldCycle.Crop.GROWING
                && canonicalCell.crop() != ResourceFieldCycle.Crop.MATURE) return Result.NEEDS_WORK;
        ResourceFieldPhysicalSurface.Condition target = ResourceFieldPhysicalSurface.Condition.of(canonicalCell);
        FrontierV3ResourceFieldWitness.Cell cell = witness.cell(cellId);
        if (cell.foreign().isPresent()) return Result.PHYSICAL_CONFLICT;
        if (cell.pending().isPresent()) {
            var pending = cell.pending().orElseThrow();
            if (pending.canonicalSource().isEmpty()) return Result.PENDING_WORK;
            if (!pending.transition().after().equals(target)
                    || !pending.canonicalSource().orElseThrow().worldId().equals(accepted.worldId())
                    || pending.canonicalSource().orElseThrow().revision().compareTo(accepted.revision()) > 0)
                return Result.STALE_CANONICAL;
        } else {
            var observed = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                    "projection:growth-predecessor");
            if (observed.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.UNLOADED;
            if (observed.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                return Result.PHYSICAL_CONFLICT;
            if (cell.committed().equals(target)) return Result.CURRENT;
            ResourceFieldCellTransition transition = ResourceFieldCellTransition.between(site.id(), cycle.epoch(),
                    cycle.layout().revision(), cellId, cell.committed(), target);
            witness = witness.beginCanonicalProjection(accepted, transition,
                    "projection:growth:" + site.id().value() + ':' + cycle.epoch() + ':' + cellId.value()
                            + ':' + accepted.revision().value(), observed);
            ledger.replaceFieldClaim(owner, owner.withWitness(witness));
            ledger.persist(level); // Accepted canonical authority and predecessor precede the block write.
            owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
        }
        witness = owner.witness();
        var review = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                "projection:growth-current");
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.UNLOADED;
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED
                || review.disposition() == FrontierV3ResourceFieldObservation.Disposition.LATER_STEP_APPLIED) {
            witness = witness.confirm(cellId, review);
        } else if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT) {
            var pending = witness.cell(cellId).pending().orElseThrow();
            if (pending.completedSteps() < pending.transition().steps().size()) {
                ResourceFieldCellTransition.Step step = pending.transition().steps().get(pending.completedSteps());
                BlockState next = switch (step.part()) {
                    case SOIL -> step.after().soil() == ResourceFieldCycle.Soil.FARMLAND
                            ? Blocks.FARMLAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    case CROP -> step.after().crop() == ResourceFieldCycle.Crop.ABSENT
                            ? Blocks.AIR.defaultBlockState()
                            : Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, step.after().growthStage());
                };
                BlockPos position = step.part() == ResourceFieldCellTransition.Part.SOIL
                        ? new BlockPos(geometry.soil().support().x(), geometry.soil().support().y(), geometry.soil().support().z())
                        : new BlockPos(geometry.crop().x(), geometry.crop().y(), geometry.crop().z());
                if (!level.setBlock(position, next, 3)) return Result.RETRY;
                var afterWrite = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                        "projection:growth-after-write");
                if (afterWrite.disposition() != FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED)
                    return Result.PHYSICAL_CONFLICT;
                witness = witness.confirm(cellId, afterWrite);
            }
        } else return Result.PHYSICAL_CONFLICT;
        var pending = witness.cell(cellId).pending().orElseThrow();
        if (pending.completedSteps() != pending.transition().steps().size()) {
            ledger.replaceFieldClaim(owner, owner.withWitness(witness));
            ledger.persist(level);
            return Result.ADVANCED;
        }
        var finalReview = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                "projection:growth-final");
        witness = witness.acknowledgeCanonicalProjection(accepted, cellId, pending.causationId(), finalReview);
        ledger.replaceFieldClaim(owner, owner.withWitness(witness));
        ledger.persist(level);
        return Result.ADVANCED;
    }

}
