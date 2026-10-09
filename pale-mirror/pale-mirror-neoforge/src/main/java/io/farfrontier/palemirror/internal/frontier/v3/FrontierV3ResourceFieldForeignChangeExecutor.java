package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.CellMutationProtocol;
import net.minecraft.core.registries.Registries;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignCellObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignChangeAcknowledged;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignChangeHeld;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

/** Exact, per-site foreign aftermath; never treats an obstruction as owned write surface. */
final class FrontierV3ResourceFieldForeignChangeExecutor {
    private FrontierV3ResourceFieldForeignChangeExecutor() { }

    static boolean beforeBlockWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    SubjectId siteId, ResourceFieldLayout.Cell cell,
                                    BlockPos position, BlockState replacement) {
        boolean cropSlot = minecraft(cell.crop()).equals(position);
        boolean soilSlot = minecraft(cell.soil().support()).equals(position);
        if (!cropSlot && !soilSlot || level.getBlockState(position).equals(replacement)) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var cycle = state.resourceSites().cycle(siteId);
        var before = cycle.cell(cell.id());
        var inFlight = FrontierV3ResourceSiteLedger.get(level).fieldForeignChange(siteId, cell.id());
        if (inFlight != null) return true; // Keep the exact predecessor; after-write versions its observation.
        boolean alreadyForeign = foreign(before);
        boolean willBeForeign = soilSlot && !replacement.is(Blocks.FARMLAND) && !replacement.is(Blocks.DIRT)
                || cropSlot && !replacement.is(Blocks.AIR) && !replacement.is(Blocks.WHEAT);
        boolean replacementGeneration = soilSlot && before.soil() == ResourceFieldCycle.Soil.DIRT && replacement.is(Blocks.FARMLAND)
                || cropSlot && replacement.is(Blocks.WHEAT) && (before.crop() == ResourceFieldCycle.Crop.ABSENT
                    || replacement.getValue(CropBlock.AGE) > 0 && replacement.getValue(CropBlock.AGE) < before.growthStage());
        if (!alreadyForeign && !willBeForeign && !replacementGeneration) return false;
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        if (!available(level, state, cycle, ledger, siteId, cell.id())) return false;
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(siteId);
        var retained = owner.witness().cell(cell.id());
        var reading = FrontierV3ResourceFieldObservation.read(level, cell, "world-foreign-prewrite");
        if (!matchesPredecessor(before, retained, reading)
                && !(reading instanceof FrontierV3ResourceFieldObservation.Unloaded
                    && matchesWritablePredecessor(level, cell, position, before, retained))) return false;
        var checkpoint = runtime.canonicalState().orElseThrow();
        String cause = "world:foreign-cell-" + siteId.value() + "-e" + cycle.epoch()
                + "-c" + cell.id().value() + "-r" + checkpoint.revision().value() + "-prewrite";
        var held = new ResourceFieldForeignChangeHeld(siteId, cycle.epoch(), cycle.layout().revision(),
                cell.id(), before, cause);
        var witness = new FrontierV3ResourceFieldForeignChangeWitness(held, Optional.empty());
        ledger.beginFieldForeignChange(witness);
        ledger.persist(level);
        var accepted = submit(runtime, held, "field-foreign-hold");
        FrontierV3DiagnosticTrace.record(level.getServer(), "field-foreign-prewrite:" + siteId.value(),
                "resource_field_foreign_change_held:prewrite", siteId, accepted);
        return true;
    }

    /** Capture facts only; never apply canonical policy or issue another physical write inside Level.setBlock. */
    static void captureAfterWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  SubjectId siteId, ResourceFieldLayout.Cell cell) {
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var witness = ledger.fieldForeignChange(siteId, cell.id());
        var state = runtime.decodedState().orElse(null);
        if (witness == null || state == null) return;
        var actual = readBlocks(level, cell);
        if (actual == null) return;
        boolean committed = !state.resourceSites().cycle(siteId).cell(cell.id()).equals(witness.hold().before());
        var decision = CellMutationProtocol.review(witness.observed(), actual, committed,
                CellMutationProtocol.Semantics.EXTERNAL_OBSERVATION);
        if (decision != CellMutationProtocol.Resolution.CAPTURE
                && decision != CellMutationProtocol.Resolution.SUPERSEDE_CAPTURE) return;
        var next = witness.observe(actual);
        ledger.observeFieldForeignChange(witness, next); ledger.persist(level);
        if (decision == CellMutationProtocol.Resolution.SUPERSEDE_CAPTURE)
            com.mojang.logging.LogUtils.getLogger().info("PMV3 cell_mutation_superseded key={} cause={} version={} previous={} current={}",
                    witness.mutationKey(), witness.hold().causationId(), next.observationVersion(),
                    witness.observed().orElseThrow(), actual);
    }

    static boolean observeOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              SubjectId siteId, ResourceFieldLayout.CellId cellId) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var cycle = state.resourceSites().cycle(siteId);
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        if (!available(level, state, cycle, ledger, siteId, cellId)) return false;
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(siteId);
        var retained = owner.witness().cell(cellId);
        var cell = cycle.layout().requireCell(cellId);
        var reading = FrontierV3ResourceFieldObservation.read(level, cell, "world-foreign-scan");
        if (reading instanceof FrontierV3ResourceFieldObservation.Unloaded || retained.pending().isPresent()) return false;
        var before = cycle.cell(cellId);
        boolean drift;
        if (foreign(before)) {
            if (retained.foreign().isEmpty()) return false;
            drift = !(reading instanceof FrontierV3ResourceFieldObservation.Foreign observed)
                    || !sameBlocks(observed.incident(), retained.foreign().orElseThrow());
        } else {
            if (retained.foreign().isPresent()
                    || !retained.committed().equals(ResourceFieldPhysicalSurface.Condition.of(before))) return false;
            drift = reading instanceof FrontierV3ResourceFieldObservation.Foreign
                    || reading instanceof FrontierV3ResourceFieldObservation.Owned owned
                        && !owned.condition().equals(ResourceFieldPhysicalSurface.Condition.of(before))
                        && FrontierV3ResourceFieldWorldChangeExecutor.classify(
                            ResourceFieldPhysicalSurface.Condition.of(before), owned.condition()) == null;
        }
        if (!drift) return false;
        var checkpoint = runtime.canonicalState().orElseThrow();
        String cause = "world:foreign-cell-" + siteId.value() + "-e" + cycle.epoch()
                + "-c" + cellId.value() + "-r" + checkpoint.revision().value() + "-scan";
        var held = new ResourceFieldForeignChangeHeld(siteId, cycle.epoch(), cycle.layout().revision(),
                cellId, before, cause);
        var blocks = readBlocks(level, cell);
        if (blocks == null) return false;
        var witness = new FrontierV3ResourceFieldForeignChangeWitness(held, Optional.of(blocks));
        ledger.beginFieldForeignChange(witness);
        ledger.persist(level);
        submit(runtime, held, "field-foreign-hold");
        return true;
    }

    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        for (var witness : ledger.pendingFieldForeignChanges()) {
            var cycle = state.resourceSites().cycle(witness.siteId());
            if (!witness.matches(cycle)) continue;
            var held = state.resourceSites().pendingForeignChange(witness.siteId(), witness.cellId());
            boolean receiptAlreadyReleased = held == null && !cycle.cell(witness.cellId()).equals(witness.hold().before());
            if (held == null && !receiptAlreadyReleased) {
                submit(runtime, witness.hold(), "field-foreign-hold");
                return true;
            }
            if (!receiptAlreadyReleased && !held.equals(witness.hold()))
                throw new IllegalStateException("cell mutation has competing canonical cause: " + witness.mutationKey());
            held = witness.hold();
            var cell = cycle.layout().requireCell(witness.cellId());
            var currentBlocks = readBlocks(level, cell);
            if (currentBlocks == null) continue; // Named UNLOADED, not an age-based unlock.
            boolean committed = !cycle.cell(held.cellId()).equals(held.before());
            var resolution = CellMutationProtocol.review(witness.observed(), currentBlocks, committed,
                    CellMutationProtocol.Semantics.EXTERNAL_OBSERVATION);
            if (resolution == CellMutationProtocol.Resolution.CAPTURE
                    || resolution == CellMutationProtocol.Resolution.SUPERSEDE_CAPTURE) {
                var captured = witness.observe(currentBlocks);
                ledger.observeFieldForeignChange(witness, captured); ledger.persist(level);
                if (resolution == CellMutationProtocol.Resolution.SUPERSEDE_CAPTURE)
                    com.mojang.logging.LogUtils.getLogger().info(
                            "PMV3 cell_mutation_superseded key={} cause={} version={} previous={} current={}",
                            witness.mutationKey(), held.causationId(), captured.observationVersion(),
                            witness.observed().orElseThrow(), currentBlocks);
                return true;
            }
            var captured = witness.observed().orElseThrow();
            // External observation receipts describe history. Finish an accepted version before
            // retaining a later fact; unlike work/custody effects, they never replay a world write.
            var reading = classifyCaptured(level, captured, held.causationId());
            var target = observedState(held.before(), reading, captured);
            if (target == null) throw new IllegalStateException("unclassified cell observation: " + witness.mutationKey());
            if (!committed && !target.equals(held.before())) {
                var accepted = submit(runtime, new ResourceFieldForeignCellObserved(held, target,
                        captured.soil().getString("Name"), captured.crop().getString("Name")), "field-foreign-observed");
                FrontierV3DiagnosticTrace.record(level.getServer(), "field-foreign-observed:" + held.siteId().value(),
                        "resource_field_foreign_cell_observed", held.siteId(), accepted);
                state = runtime.decodedState().orElseThrow();
                cycle = state.resourceSites().cycle(held.siteId());
            }
            if (!sameObservedCell(cycle.cell(held.cellId()), target))
                throw new IllegalStateException("accepted cell observation disagrees with its retained version: " + witness.mutationKey());
            var owner = ledger.fieldClaim(held.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership value
                    ? value : null;
            if (owner == null || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE)
                throw new IllegalStateException("cell observation lost its physical owner: " + witness.mutationKey());
            var next = owner.witness().acknowledgeForeignChange(witness, cycle, captured, reading);
            if (next != owner.witness()) {
                ledger.replaceFieldClaim(owner, owner.withWitness(next)); ledger.persist(level);
            }
            if (!receiptAlreadyReleased)
                submit(runtime, new ResourceFieldForeignChangeAcknowledged(held, cycle.cell(held.cellId())), "field-foreign-ack");
            if (!captured.equals(currentBlocks)) {
                var checkpoint = runtime.canonicalState().orElseThrow();
                var successor = new ResourceFieldForeignChangeHeld(held.siteId(), cycle.epoch(), cycle.layout().revision(),
                        held.cellId(), cycle.cell(held.cellId()), "world:foreign-cell-" + held.siteId().value() + "-e" + cycle.epoch()
                        + "-c" + held.cellId().value() + "-r" + checkpoint.revision().value() + "-successor");
                var successorWitness = new FrontierV3ResourceFieldForeignChangeWitness(successor, Optional.of(currentBlocks));
                ledger.replaceFieldForeignChange(witness, successorWitness); ledger.persist(level);
                submit(runtime, successor, "field-foreign-hold");
            } else {
                ledger.retireFieldForeignChange(witness); ledger.persist(level);
            }
            com.mojang.logging.LogUtils.getLogger().info("PMV3 cell_mutation_closed key={} cause={} version={} successor={}",
                    witness.mutationKey(), held.causationId(), witness.observationVersion(), !captured.equals(currentBlocks));
            return true;
        }
        for (var held : state.resourceSites().pendingForeignChanges().values().stream()
                .sorted(java.util.Comparator.comparing(ResourceFieldForeignChangeHeld::siteId)).toList()) {
            if (ledger.fieldForeignChange(held.siteId(), held.cellId()) != null) continue;
            var cycle = state.resourceSites().cycle(held.siteId());
            if (cycle.epoch() != held.epoch() || cycle.layout().revision() != held.layoutRevision()) continue;
            var owner = ledger.fieldClaim(held.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership value
                    ? value : null;
            if (owner == null || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                    || !owner.witness().matchesCycle(cycle)) continue;
            var reading = FrontierV3ResourceFieldObservation.read(level, cycle.layout().requireCell(held.cellId()),
                    held.causationId());
            if (!matchesCurrent(cycle.cell(held.cellId()), owner.witness().cell(held.cellId()), reading)) continue;
            submit(runtime, new ResourceFieldForeignChangeAcknowledged(held, cycle.cell(held.cellId())),
                    "field-foreign-ack");
            return true;
        }
        return false;
    }

    private static FrontierV3ResourceFieldObservation.Reading classifyCaptured(ServerLevel level,
            FrontierV3ResourceFieldForeignChangeWitness.Blocks captured, String cause) {
        return FrontierV3ResourceFieldObservation.classify(
                NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), captured.soil()),
                NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), captured.crop()), cause);
    }

    private static boolean available(ServerLevel level, FrontierWorldState state, ResourceFieldCycle cycle,
                                     FrontierV3ResourceSiteLedger ledger, SubjectId siteId,
                                     ResourceFieldLayout.CellId cellId) {
        if (state.resourceSites().hasPendingCellMutation(siteId, cellId)
                || ledger.hasPendingFieldMutation(siteId, cellId) || cycle.pendingPlayerBreaks().containsKey(cellId)
                || !(ledger.fieldClaim(siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return false;
        // A prepared crop is interruptible until its physical work witness begins.
        // Both the prewrite and scanner paths still require the retained cell to
        // have no pending effect before admitting a foreign predecessor.
        return true;
    }

    private static boolean matchesPredecessor(ResourceFieldCycle.CellState canonical,
                                              FrontierV3ResourceFieldWitness.Cell claim,
                                              FrontierV3ResourceFieldObservation.Reading reading) {
        if (claim.pending().isPresent()) return false;
        if (foreign(canonical))
            return claim.foreign().isPresent()
                    && reading instanceof FrontierV3ResourceFieldObservation.Foreign observed
                    && sameBlocks(observed.incident(), claim.foreign().orElseThrow());
        return claim.foreign().isEmpty()
                && claim.committed().equals(ResourceFieldPhysicalSurface.Condition.of(canonical))
                && reading instanceof FrontierV3ResourceFieldObservation.Owned observed
                && observed.condition().equals(claim.committed());
    }

    /** A write to one loaded half still needs its hold when the other cell half is unloaded. */
    static boolean matchesWritablePredecessor(ServerLevel level, ResourceFieldLayout.Cell cell,
                                              BlockPos position, ResourceFieldCycle.CellState canonical,
                                              FrontierV3ResourceFieldWitness.Cell claim) {
        if (!level.hasChunkAt(position) || claim.pending().isPresent()) return false;
        boolean soilSlot = minecraft(cell.soil().support()).equals(position);
        boolean cropSlot = minecraft(cell.crop()).equals(position);
        if (!soilSlot && !cropSlot) return false;
        BlockState actual = level.getBlockState(position);
        if (foreign(canonical)) {
            if (claim.foreign().isEmpty()) return false;
            var incident = claim.foreign().orElseThrow();
            return NbtUtils.writeBlockState(actual).equals(soilSlot
                    ? incident.observedSoil() : incident.observedCrop());
        }
        if (claim.foreign().isPresent()
                || !claim.committed().equals(ResourceFieldPhysicalSurface.Condition.of(canonical))) return false;
        if (soilSlot) return canonical.soil() == ResourceFieldCycle.Soil.FARMLAND
                ? actual.is(Blocks.FARMLAND) : canonical.soil() == ResourceFieldCycle.Soil.DIRT
                && actual.is(Blocks.DIRT);
        return switch (canonical.crop()) {
            case ABSENT -> actual.is(Blocks.AIR);
            case GROWING, MATURE -> actual.is(Blocks.WHEAT)
                    && actual.getValue(CropBlock.AGE) == canonical.growthStage();
            default -> false;
        };
    }

    private static boolean matchesCurrent(ResourceFieldCycle.CellState canonical,
                                          FrontierV3ResourceFieldWitness.Cell claim,
                                          FrontierV3ResourceFieldObservation.Reading reading) {
        return matchesPredecessor(canonical, claim, reading);
    }

    /** Work accounting may advance atomically with the exact observed physical cell. */
    private static boolean sameObservedCell(ResourceFieldCycle.CellState canonical,
                                            ResourceFieldCycle.CellState observed) {
        return canonical.soil() == observed.soil() && canonical.crop() == observed.crop()
                && canonical.growthStage() == observed.growthStage()
                && canonical.workAccessBlocked() == observed.workAccessBlocked()
                && canonical.yielded() == observed.yielded();
    }

    private static boolean sameBlocks(FrontierV3ResourceFieldWitness.ForeignIncident a,
                                      FrontierV3ResourceFieldWitness.ForeignIncident b) {
        return a.observedSoil().equals(b.observedSoil()) && a.observedCrop().equals(b.observedCrop());
    }

    private static ResourceFieldCycle.CellState observedState(ResourceFieldCycle.CellState before,
                                                               FrontierV3ResourceFieldObservation.Reading reading,
                                                               FrontierV3ResourceFieldForeignChangeWitness.Blocks blocks) {
        if (reading instanceof FrontierV3ResourceFieldObservation.Owned owned) {
            var condition = owned.condition();
            return new ResourceFieldCycle.CellState(condition.soil(), condition.crop(), condition.growthStage(),
                    before.accounted(), before.yielded(), before.workAccessBlocked());
        }
        if (!(reading instanceof FrontierV3ResourceFieldObservation.Foreign)) return null;
        String soil = blocks.soil().getString("Name");
        ResourceFieldCycle.Soil kind = Objects.equals(soil, "minecraft:farmland")
                ? ResourceFieldCycle.Soil.FARMLAND : Objects.equals(soil, "minecraft:dirt")
                ? ResourceFieldCycle.Soil.DIRT : ResourceFieldCycle.Soil.OBSTRUCTED;
        return new ResourceFieldCycle.CellState(kind, ResourceFieldCycle.Crop.OBSTRUCTED, 0,
                before.accounted(), before.yielded(), before.workAccessBlocked());
    }

    private static FrontierV3ResourceFieldForeignChangeWitness.Blocks readBlocks(ServerLevel level,
                                                                                   ResourceFieldLayout.Cell cell) {
        BlockPos soil = minecraft(cell.soil().support()), crop = minecraft(cell.crop());
        if (!level.hasChunkAt(soil) || !level.hasChunkAt(crop)) return null;
        return new FrontierV3ResourceFieldForeignChangeWitness.Blocks(
                NbtUtils.writeBlockState(level.getBlockState(soil)),
                NbtUtils.writeBlockState(level.getBlockState(crop)));
    }

    private static CommandResult.Accepted submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  FrontierPayload payload, String commandKind) {
        var checkpoint = runtime.canonicalState().orElseThrow();
        CommandId id = new CommandId("executor:" + commandKind + "-r" + checkpoint.revision().value());
        var result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                payload)).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (result instanceof CommandResult.Rejected rejected)
            throw new IllegalStateException("persisted foreign field cause cannot advance: " + rejected.rejection());
        return (CommandResult.Accepted) result;
    }

    private static boolean foreign(ResourceFieldCycle.CellState state) {
        return state.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || state.crop() == ResourceFieldCycle.Crop.OBSTRUCTED;
    }

    private static BlockPos minecraft(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
