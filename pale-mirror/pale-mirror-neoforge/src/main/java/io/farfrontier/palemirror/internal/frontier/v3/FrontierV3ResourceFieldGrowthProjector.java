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

    /** Review/fairness is distinct from successful writes; retain each cell's outcome. */
    record BatchResult(java.util.Map<ResourceFieldLayout.CellId, Result> cells, boolean preparationPending) {
        BatchResult { cells = java.util.Map.copyOf(cells); }
        static BatchResult waiting() { return new BatchResult(java.util.Map.of(), true); }
        static BatchResult empty() { return new BatchResult(java.util.Map.of(), false); }
        boolean reviewed() { return !preparationPending && (cells.isEmpty() || cells.values().stream().anyMatch(value -> value != Result.RETRY)); }
        long advancedCells() { return cells.values().stream().filter(value -> value == Result.ADVANCED).count(); }
    }

    private enum Publication { SYNCHRONOUS, DEFERRED, BATCH }
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
        if (current.state().resourceSites().hasPendingCellMutation(siteId, cellId)) return Result.PENDING_WORK;
        return projectAccepted(level, site,
                new FrontierCanonicalState<>(current.worldId(), current.revision(), current.instant(), cycle), cellId, true);
    }

    /** Internal executor core; production admission must enter through the live-runtime boundary above. */
    static Result projectAccepted(ServerLevel level, ResourceSite site,
                             FrontierCanonicalState<ResourceFieldCycle> accepted,
                             ResourceFieldLayout.CellId cellId) {
        return projectAccepted(level, site, accepted, cellId, false);
    }

    static Result projectAccepted(ServerLevel level, ResourceSite site, FrontierCanonicalState<ResourceFieldCycle> accepted,
                                  ResourceFieldLayout.CellId cellId, boolean deferred) {
        return projectAccepted(level, site, accepted, cellId, deferred ? Publication.DEFERRED : Publication.SYNCHRONOUS, null);
    }

    /** One prepared publication, then individual effects, then one result publication. */
    static BatchResult projectCurrentBatch(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    SubjectId siteId, java.util.List<ResourceFieldLayout.CellId> cells) {
        if (!FrontierV3PhysicalWorld.isPhysical(level) || !FrontierV3ServerLifecycle.ownsRuntime(level, runtime))
            throw new IllegalArgumentException("growth batch has a foreign runtime or dimension");
        var current = runtime.canonicalState().orElseThrow();
        if (!FrontierV3PhysicalWorld.WORLD_ID.equals(current.worldId()))
            throw new IllegalArgumentException("growth batch owns another world");
        var site = current.state().resourceSite(siteId);
        var cycle = current.state().resourceSites().cycle(siteId);
        var accepted = new FrontierCanonicalState<>(current.worldId(), current.revision(), current.instant(), cycle);
        var available = cells.stream().filter(id -> !current.state().resourceSites().hasPendingCellMutation(siteId, id)).toList();
        return available.isEmpty() ? BatchResult.empty() : projectAcceptedBatch(level, site, accepted, available, true);
    }

    /** Component seam; production admission is the registered runtime boundary above. */
    static void projectAcceptedBatch(ServerLevel level, ResourceSite site,
            FrontierCanonicalState<ResourceFieldCycle> accepted, java.util.List<ResourceFieldLayout.CellId> cells) {
        projectAcceptedBatch(level, site, accepted, cells, false);
    }
    static BatchResult projectAcceptedBatch(ServerLevel level, ResourceSite site,
            FrontierCanonicalState<ResourceFieldCycle> accepted, java.util.List<ResourceFieldLayout.CellId> cells, boolean deferred) {
        if (cells.isEmpty() || cells.size() > FrontierV3ResourceSiteExecutor.projectionWriteBudget()
                || new java.util.HashSet<>(cells).size() != cells.size())
            throw new IllegalArgumentException("growth batch exceeds its disjoint write budget");
        var siteId = site.id();
        var cycle = accepted.state();
        if (!siteId.equals(cycle.siteId()) || !site.layout().fingerprint().equals(cycle.layout().fingerprint()))
            throw new IllegalArgumentException("growth batch has a foreign site or layout");
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        if (!(ledger.fieldClaim(siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !owner.witness().matchesCycle(cycle)) return BatchResult.empty();
        if (deferred && !ledger.prepareFieldDurable(level, siteId)) return BatchResult.waiting();
        var witness = owner.witness();
        var admitted = new java.util.ArrayList<ResourceFieldLayout.CellId>();
        for (var id : cells) {
            if (cycle.pendingPlayerBreaks().containsKey(id) || ledger.hasPendingFieldMutation(siteId, id)) continue;
            var canonical = cycle.cell(id);
            var cell = witness.cell(id);
            if (cell.foreign().isPresent() || !canonical.soil().cultivable()
                    || canonical.crop() != ResourceFieldCycle.Crop.ABSENT
                        && canonical.crop() != ResourceFieldCycle.Crop.GROWING && canonical.crop() != ResourceFieldCycle.Crop.MATURE) continue;
            var target = ResourceFieldPhysicalSurface.Condition.of(canonical);
            if (cell.pending().isPresent()) {
                var pending = cell.pending().orElseThrow();
                if (pending.canonicalSource().isPresent()
                        && pending.canonicalSource().orElseThrow().worldId().equals(accepted.worldId())
                        && pending.canonicalSource().orElseThrow().revision().compareTo(accepted.revision()) <= 0) admitted.add(id);
                continue;
            }
            if (cell.committed().equals(target)) continue;
            var observed = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, id, "projection:growth-batch-predecessor");
            if (observed.disposition() == FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT) {
                try { witness = witness.acknowledgeObservedGrowth(accepted, id, observed); }
                catch (IllegalArgumentException unrelatedDrift) { continue; }
                cell = witness.cell(id);
                observed = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, id, "projection:growth-batch-observed");
                if (cell.committed().equals(target)) continue;
            }
            if (observed.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT) continue;
            var transition = ResourceFieldCellTransition.between(siteId, cycle.epoch(), cycle.layout().revision(),
                    id, cell.committed(), target);
            witness = witness.beginCanonicalProjection(accepted, transition,
                    "projection:growth:" + siteId.value() + ':' + cycle.epoch() + ':' + id.value() + ':' + accepted.revision().value(), observed);
            admitted.add(id);
        }
        if (witness != owner.witness()) {
            ledger.replaceFieldClaim(owner, owner.withWitness(witness));
            if (deferred) ledger.persistAsync(level); else ledger.persist(level);
        }
        if (admitted.isEmpty()) return BatchResult.empty();
        // Exact field intent AND common operation permission must precede effects.
        if (deferred && !ledger.prepareFieldDurable(level, siteId)) return BatchResult.waiting();
        var outcomes = new java.util.LinkedHashMap<ResourceFieldLayout.CellId, Result>();
        try {
            var requests = new java.util.LinkedHashMap<ResourceFieldLayout.CellId, FrontierV3MinecraftOperation.Request>();
            for (var id : admitted) {
                var pending = witness.cell(id).pending().orElseThrow();
                var reading = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, id, "projection:growth-batch-apply");
                if (pending.completedSteps() < pending.transition().steps().size()
                        && (reading.disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT
                            || reading.disposition() == FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED))
                    requests.put(id, FrontierV3FieldProjectionOperation.request(level, siteId, cycle.layout().requireCell(id), pending,
                            () -> resumeCurrent(level, siteId, id)));
            }
            var results = new java.util.HashMap<ResourceFieldLayout.CellId, FrontierV3MinecraftOperation.Result>();
            if (!requests.isEmpty()) {
                var completed = deferred ? FrontierV3MinecraftOperation.applyBatchDeferred(level, java.util.List.copyOf(requests.values()))
                        : FrontierV3MinecraftOperation.applyBatch(level, java.util.List.copyOf(requests.values()));
                int index = 0;
                for (var id : requests.keySet()) results.put(id, completed.get(index++));
            }
            for (var id : admitted) {
                var result = projectAccepted(level, site, accepted, id, Publication.BATCH, results.get(id));
                outcomes.put(id, result);
            }
        } finally {
            // A failure may leave some cells applied and others pending. The original persisted
            // per-cell witnesses recover that split; no all-or-nothing application is invented.
            if (deferred) { ledger.persistAsync(level); ledger.completeOperationsDeferred(level); }
            else { ledger.persist(level); ledger.completeOperations(level); }
        }
        return new BatchResult(outcomes, false);
    }

    private static Result projectAccepted(ServerLevel level, ResourceSite site,
                             FrontierCanonicalState<ResourceFieldCycle> accepted,
                             ResourceFieldLayout.CellId cellId, Publication publication,
                             FrontierV3MinecraftOperation.Result batchResult) {
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
        if (publication == Publication.DEFERRED && !ledger.prepareFieldDurable(level, site.id())) return Result.RETRY;
        FrontierV3ResourceFieldWitness witness = owner.witness();
        if (!witness.matchesCycle(cycle)) return Result.STALE_CANONICAL;
        if (cycle.pendingPlayerBreaks().containsKey(cellId)) return Result.PENDING_PLAYER;
        if (ledger.hasPendingFieldMutation(site.id(), cellId)) return Result.PENDING_WORK;
        var canonicalCell = cycle.cell(cellId);
        if (!canonicalCell.soil().cultivable()
                || canonicalCell.crop() != ResourceFieldCycle.Crop.ABSENT && canonicalCell.crop() != ResourceFieldCycle.Crop.GROWING
                && canonicalCell.crop() != ResourceFieldCycle.Crop.MATURE) return Result.NEEDS_WORK;
        ResourceFieldPhysicalSurface.Condition target = ResourceFieldPhysicalSurface.Condition.of(canonicalCell);
        FrontierV3ResourceFieldWitness.Cell cell = witness.cell(cellId);
        if (cell.foreign().isPresent()) return Result.PHYSICAL_CONFLICT;
        if (cell.pending().isPresent()) {
            var pending = cell.pending().orElseThrow();
            if (pending.canonicalSource().isEmpty()) return Result.PENDING_WORK;
            if (!pending.canonicalSource().orElseThrow().worldId().equals(accepted.worldId())
                    || pending.canonicalSource().orElseThrow().revision().compareTo(accepted.revision()) > 0)
                return Result.STALE_CANONICAL;
        } else {
            var observed = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                    "projection:growth-predecessor");
            if (observed.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.UNLOADED;
            if (observed.disposition() == FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT) {
                try { witness = witness.acknowledgeObservedGrowth(accepted, cellId, observed); }
                catch (IllegalArgumentException unrelatedDrift) { return Result.PHYSICAL_CONFLICT; }
                ledger.replaceFieldClaim(owner, owner.withWitness(witness));
                if (!publish(level, ledger, site.id(), publication)) return Result.RETRY;
                owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
                cell = witness.cell(cellId);
                observed = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                        "projection:observed-growth-predecessor");
            }
            if (observed.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                return Result.PHYSICAL_CONFLICT;
            if (cell.committed().equals(target)) return Result.CURRENT;
            ResourceFieldCellTransition transition = ResourceFieldCellTransition.between(site.id(), cycle.epoch(),
                    cycle.layout().revision(), cellId, cell.committed(), target);
            witness = witness.beginCanonicalProjection(accepted, transition,
                    "projection:growth:" + site.id().value() + ':' + cycle.epoch() + ':' + cellId.value()
                            + ':' + accepted.revision().value(), observed);
            ledger.replaceFieldClaim(owner, owner.withWitness(witness));
            // Accepted canonical authority and predecessor precede the block write.
            if (!publish(level, ledger, site.id(), publication)) return Result.RETRY;
            owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
        }
        witness = owner.witness();
        var review = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                "projection:growth-current");
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.UNLOADED) return Result.UNLOADED;
        java.util.UUID operation = null;
        if (review.disposition() == FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED
                || review.disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT) {
            var pending = witness.cell(cellId).pending().orElseThrow();
            if (pending.completedSteps() < pending.transition().steps().size()) {
                if (publication == Publication.BATCH && batchResult == null) return Result.RETRY;
                var applied = switch (publication) {
                    case BATCH -> batchResult;
                    case SYNCHRONOUS -> FrontierV3FieldProjectionOperation.apply(level, site.id(), geometry, pending);
                    case DEFERRED -> FrontierV3MinecraftOperation.applyBatchDeferred(level,
                            java.util.List.of(FrontierV3FieldProjectionOperation.request(level, site.id(), geometry, pending,
                                    () -> resumeCurrent(level, site.id(), cellId)))).getFirst();
                };
                if (applied.pending()) return Result.RETRY;
                if (!applied.complete()) return Result.PHYSICAL_CONFLICT;
                operation = applied.ticket();
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
            if (operation != null) ledger.operationCompleted(level, operation);
            return finish(level, ledger, site.id(), publication);
        }
        var finalReview = FrontierV3ResourceFieldObservation.observe(level, cycle, witness, cellId,
                "projection:growth-final");
        witness = witness.acknowledgeCanonicalProjection(accepted, cellId, pending.causationId(), finalReview);
        ledger.replaceFieldClaim(owner, owner.withWitness(witness));
        if (operation != null) ledger.operationCompleted(level, operation);
        return finish(level, ledger, site.id(), publication);
    }

    /** Resume through the owner and current revision; never replay a captured request. */
    private static void resumeCurrent(ServerLevel level, SubjectId site, ResourceFieldLayout.CellId cell) {
        var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(level.getServer());
        if (runtime != null && FrontierV3PhysicalWorld.isPhysical(level))
            projectCurrentOne(level, runtime, site, cell);
    }

    private static boolean publish(ServerLevel level, FrontierV3ResourceSiteLedger ledger, SubjectId site, Publication publication) {
        if (publication == Publication.DEFERRED) return ledger.prepareFieldDurable(level, site);
        ledger.persist(level);
        return true;
    }
    private static Result finish(ServerLevel level, FrontierV3ResourceSiteLedger ledger, SubjectId site, Publication publication) {
        if (publication == Publication.SYNCHRONOUS) { ledger.persist(level); ledger.completeOperations(level); }
        else if (publication == Publication.DEFERRED) {
            ledger.persistAsync(level); ledger.completeOperationsDeferred(level);
            if (!ledger.prepareFieldDurable(level, site)) return Result.RETRY;
        }
        return Result.ADVANCED;
    }

}
