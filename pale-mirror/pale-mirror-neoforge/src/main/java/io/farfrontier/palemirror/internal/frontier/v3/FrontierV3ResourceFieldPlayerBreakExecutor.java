package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPlayerBreakPrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Objects;

/** Exact crop-only player causality across canonical WAL, Vanilla blocks and field SavedData. */
final class FrontierV3ResourceFieldPlayerBreakExecutor {
    private FrontierV3ResourceFieldPlayerBreakExecutor() { }

    static FrontierV3ResourceSiteExecutor.BlockBreakObservation prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ResourceSite site,
            BlockPos position, ServerPlayer player) {
        Objects.requireNonNull(player, "field break player");
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        ResourceFieldCycle cycle = state.resourceSites().cycle(site.id());
        ResourceFieldLayout.Cell cell = cycle.layout().cropAt(block(position))
                .or(() -> cycle.layout().soilAt(block(position))).orElse(null);
        if (cell == null) return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        boolean support = cell.soil().support().equals(block(position));
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        if (!(ledger.fieldClaim(site.id()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership))
            return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
        if (owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)
                || state.resourceSites().hasPendingWorldChange(site.id())
                || ledger.fieldWorldChange(site.id()) != null || ledger.fieldForeignChange(site.id()) != null
                || ledger.fieldPlayerBreak(site.id()) != null
                || !cycle.pendingPlayerBreaks().isEmpty())
            return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        var retained = owner.witness().cell(cell.id());
        var canonical = cycle.cell(cell.id());
        if (retained.pending().isPresent()) return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        if (canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED) {
            var reading = FrontierV3ResourceFieldObservation.read(level, cell, "player-foreign-break");
            if (!(reading instanceof FrontierV3ResourceFieldObservation.Foreign foreign)
                    || retained.foreign().isEmpty()
                    || !retained.foreign().orElseThrow().observedSoil().equals(foreign.incident().observedSoil())
                    || !retained.foreign().orElseThrow().observedCrop().equals(foreign.incident().observedCrop()))
                return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
            // The actual Level.setBlock postcondition will acquire the foreign-cell hold.
            return FrontierV3ResourceSiteExecutor.BlockBreakObservation.ACCEPTED;
        }
        var before = ResourceFieldPhysicalSurface.Condition.of(canonical);
        if (support) {
            var reading = FrontierV3ResourceFieldObservation.read(level, cell, "player-support-break");
            return retained.foreign().isEmpty() && retained.committed().equals(before)
                    && reading instanceof FrontierV3ResourceFieldObservation.Owned owned
                    && owned.condition().equals(before)
                    ? FrontierV3ResourceSiteExecutor.BlockBreakObservation.ACCEPTED
                    : FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        }
        if (before.soil() != ResourceFieldCycle.Soil.FARMLAND
                || before.crop() != ResourceFieldCycle.Crop.GROWING && before.crop() != ResourceFieldCycle.Crop.MATURE)
            return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        // A first-arrival COLD crop may be visibly behind its canonical
        // cell. Finish only this exact cell's existing write-ahead projection before the
        // player action; never label the older physical crop as the canonical predecessor.
        if (!retained.committed().equals(before) || retained.pending().isPresent()) {
            var projection = FrontierV3ResourceFieldGrowthProjector.projectCurrentOne(level, runtime, site.id(), cell.id());
            if (projection != FrontierV3ResourceFieldGrowthProjector.Result.CURRENT
                    && projection != FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED)
                return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
            owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
            retained = owner.witness().cell(cell.id());
        }
        if (!retained.committed().equals(before) || retained.pending().isPresent() || retained.foreign().isPresent()
                || FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), cell.id(),
                        "player-break-precondition").disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
            return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        var checkpoint = runtime.canonicalState().orElseThrow();
        String actionId = "action:field-player-break-" + player.getUUID() + "-r" + checkpoint.revision().value()
                + "-c" + cell.id().value();
        var prepared = new ResourceFieldPlayerBreakPrepared(site.id(), cycle.epoch(), cycle.layout().revision(),
                cell.id(), before, player.getUUID(), actionId);
        if (!(submit(runtime, "prepare", prepared) instanceof CommandResult.Accepted))
            return FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED;
        ledger.beginFieldPlayerBreak(FrontierV3ResourceFieldPlayerBreakWitness.prepared(prepared));
        ledger.persist(level);
        return FrontierV3ResourceSiteExecutor.BlockBreakObservation.ACCEPTED;
    }

    /** Called after Vanilla's actual block action, not from the left-click admission event. */
    static boolean observePlayerAction(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       BlockPos position, ServerPlayer player) {
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        for (var witness : ledger.pendingFieldPlayerBreaks()) {
            if (!witness.playerId().equals(player.getUUID())) continue;
            if (FrontierV3PlayerBreakDisposition.active(level.getServer(), player.getUUID(), position)) return false;
            FrontierWorldState state = runtime.decodedState().orElse(null);
            if (state == null) return false;
            ResourceFieldCycle cycle = state.resourceSites().cycle(witness.siteId());
            if (!cell(cycle, witness).crop().equals(block(position))) continue;
            return reconcile(level, runtime, state, ledger, witness);
        }
        return false;
    }

    /** Bounded natural-load reconciliation after restart or a cancelled player action. */
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        for (var witness : ledger.pendingFieldPlayerBreaks()) {
            ResourceFieldCycle cycle = state.resourceSites().cycle(witness.siteId());
            ResourceFieldLayout.Cell cell = cell(cycle, witness);
            if (!level.hasChunkAt(minecraft(cell.crop()))) continue;
            if (FrontierV3PlayerBreakDisposition.active(level.getServer(), witness.playerId(), minecraft(cell.crop()))) continue;
            return reconcile(level, runtime, state, ledger, witness);
        }
        // Crash after the canonical permission but before the physical SavedData reservation:
        // the packet was never allowed to mutate. A still-exact predecessor can close as a
        // no-op; a changed cell without a physical witness is genuinely ambiguous.
        for (var lifecycle : state.resourceSites().sites().values().stream()
                .sorted(Comparator.comparing(value -> value.siteId().value())).toList()) {
            ResourceFieldCycle cycle = state.resourceSites().cycle(lifecycle.siteId());
            if (ledger.fieldPlayerBreak(lifecycle.siteId()) != null) continue;
            for (var entry : cycle.pendingPlayerBreaks().entrySet()) {
                ResourceFieldLayout.Cell cell = cycle.layout().requireCell(entry.getKey());
                if (!level.hasChunkAt(minecraft(cell.crop()))) continue;
                if (!(ledger.fieldClaim(lifecycle.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                        || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) continue;
                var reading = FrontierV3ResourceFieldObservation.read(level, cell, entry.getValue().actionId());
                if (reading instanceof FrontierV3ResourceFieldObservation.Owned owned
                        && owned.condition().equals(entry.getValue().before())
                        && owner.witness().cell(cell.id()).committed().equals(entry.getValue().before())) {
                    var observed = new ResourceFieldCellObserved(lifecycle.siteId(), cycle.epoch(), cycle.layout().revision(),
                            cell.id(), entry.getValue().before(), entry.getValue().before(),
                            ResourceFieldCellObserved.Change.UNCHANGED, ResourceFieldCellObserved.Source.PLAYER,
                            entry.getValue().actionId());
                    if (submit(runtime, "unwritten-unchanged", observed) instanceof CommandResult.Accepted)
                        return true;
                } else {
                    conflict(level, runtime, state.resourceSite(lifecycle.siteId()), cell.crop(),
                            "player-break-unwitnessed-physical-change");
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, FrontierV3ResourceSiteLedger ledger,
                                     FrontierV3ResourceFieldPlayerBreakWitness before) {
        ResourceFieldCycle cycle = state.resourceSites().cycle(before.siteId());
        ResourceFieldLayout.Cell cell = cell(cycle, before);
        if (!before.matches(cycle)) {
            conflict(level, runtime, state.resourceSite(before.siteId()), cell.crop(), "player-break-foreign-cycle");
            return true;
        }
        var reading = FrontierV3ResourceFieldObservation.read(level, cell, before.actionId());
        if (reading instanceof FrontierV3ResourceFieldObservation.Unloaded) return false;
        if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)) {
            conflict(level, runtime, state.resourceSite(before.siteId()), cell.crop(), "player-break-foreign-postcondition");
            return true;
        }
        var witness = before;
        if (witness.observedChange().isEmpty()) {
            var removed = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                    ResourceFieldCycle.Crop.ABSENT, 0);
            ResourceFieldCellObserved.Change change = owned.condition().equals(before.before())
                    ? ResourceFieldCellObserved.Change.UNCHANGED
                    : owned.condition().equals(removed) ? ResourceFieldCellObserved.Change.CROP_REMOVED : null;
            if (change == null) {
                conflict(level, runtime, state.resourceSite(before.siteId()), cell.crop(), "player-break-unclassified-postcondition");
                return true;
            }
            witness = before.observed(change);
            ledger.observeFieldPlayerBreak(before, witness); ledger.persist(level);
        }
        if (!owned.condition().equals(witness.after())) {
            conflict(level, runtime, state.resourceSite(before.siteId()), cell.crop(), "player-break-postcondition-diverged");
            return true;
        }
        if (cycle.pendingPlayerBreaks().containsKey(witness.cellId())) {
            var observed = new ResourceFieldCellObserved(witness.siteId(), witness.epoch(), witness.layoutRevision(),
                    witness.cellId(), witness.before(), witness.after(), witness.observedChange().orElseThrow(),
                    ResourceFieldCellObserved.Source.PLAYER, witness.actionId());
            var accepted = submit(runtime, "observed", observed);
            if (!(accepted instanceof CommandResult.Accepted)) return false;
            FrontierV3DiagnosticTrace.record(level.getServer(), "field-player-break:" + witness.siteId().value(),
                    "resource_field_cell_observed", witness.siteId(), accepted);
            state = runtime.decodedState().orElseThrow();
            cycle = state.resourceSites().cycle(witness.siteId());
        }
        var owner = ledger.fieldClaim(witness.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership value ? value : null;
        if (owner == null || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return false;
        var next = owner.witness().acknowledgePlayerBreak(witness, cycle, owned);
        if (next != owner.witness()) {
            ledger.replaceFieldClaim(owner, owner.withWitness(next)); ledger.persist(level);
        }
        ledger.retireFieldPlayerBreak(witness); ledger.persist(level);
        FrontierV3PlayerBreakDisposition.consume(level.getServer(), witness.playerId(), minecraft(cell.crop()));
        return true;
    }

    private static ResourceFieldLayout.Cell cell(ResourceFieldCycle cycle,
                                                 FrontierV3ResourceFieldPlayerBreakWitness witness) {
        return cycle.layout().requireCell(witness.cellId());
    }

    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        String phase,
                                        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = runtime.canonicalState().orElseThrow();
        CommandId id = new CommandId("executor:field-player-break-" + phase + "-r" + checkpoint.revision().value());
        return runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 ResourceSite site, BlockPosition position, String reason) {
        var checkpoint = runtime.canonicalState().orElseThrow();
        FrontierV3ResourceSiteConflictExecutor.recordConflict(level, runtime, FrontierV3ResourceSiteLedger.get(level),
                site, position, ResourceSiteDiagnosticProducer.PLAYER_REMOVED,
                new CommandId("executor:field-player-break-conflict-r" + checkpoint.revision().value()));
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn("PMV3_FIELD_PLAYER_BREAK conflict={} site={} cell={}",
                reason, site.id().value(), position);
    }

    private static BlockPosition block(BlockPos position) {
        return new BlockPosition(position.getX(), position.getY(), position.getZ());
    }
    private static BlockPos minecraft(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
