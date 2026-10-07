package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldWorldChangeHeld;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldWorldChangeAcknowledged;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded local observation of an already changed, naturally loaded field cell. */
final class FrontierV3ResourceFieldWorldChangeExecutor {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<Long, List<CellOwner>>> CELL_OWNERS = new IdentityHashMap<>();
    private record CellOwner(SubjectId siteId, ResourceFieldLayout.Cell cell) { }

    private FrontierV3ResourceFieldWorldChangeExecutor() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CELL_OWNERS.remove(runtime); }

    static void observeBlockWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  BlockPos position, BlockState replacement, boolean committed) {
        if (committed) afterBlockWrite(level, runtime, position, replacement);
        else beforeBlockWrite(level, runtime, position, replacement);
    }

    /** Durable-before-block fence for external loss of an already owned crop or soil cell. */
    static void beforeBlockWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 BlockPos position, BlockState replacement) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var owners = CELL_OWNERS.computeIfAbsent(runtime, ignored -> cellOwners(state));
        List<CellOwner> column = owners.get(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
        if (column == null || !level.hasChunkAt(position)) return;
        for (CellOwner owner : column) {
            ResourceFieldLayout.Cell cell = owner.cell();
            var cycle = state.resourceSites().cycle(owner.siteId());
            if (!cycle.layout().requireCell(cell.id()).equals(cell))
                throw new IllegalStateException("field mutation index has a stale canonical layout");
            if (FrontierV3ResourceFieldForeignChangeExecutor.beforeBlockWrite(
                    level, runtime, owner.siteId(), cell, position, replacement)) return;
            var canonical = cycle.cell(cell.id());
            if (canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                    || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED) continue;
            boolean cropLoss = minecraft(cell.crop()).equals(position) && replacement.is(Blocks.AIR);
            boolean cropReplant = minecraft(cell.crop()).equals(position) && replacement.is(Blocks.WHEAT)
                    && replacement.getValue(CropBlock.AGE) == 0 && canonical.growthStage() > 0;
            boolean soilLoss = minecraft(cell.soil().support()).equals(position) && replacement.is(Blocks.DIRT);
            boolean cropGrowth = minecraft(cell.crop()).equals(position) && replacement.is(Blocks.WHEAT)
                    && canonical.crop() == ResourceFieldCycle.Crop.GROWING
                    && replacement.getValue(CropBlock.AGE) > canonical.growthStage();
            if (!cropLoss && !soilLoss && !cropReplant && !cropGrowth) continue;
            var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell.id()));
            var after = cropGrowth
                    ? new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                        replacement.getValue(CropBlock.AGE) == 7 ? ResourceFieldCycle.Crop.MATURE : ResourceFieldCycle.Crop.GROWING,
                        replacement.getValue(CropBlock.AGE)) : soilLoss
                    ? new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT, ResourceFieldCycle.Crop.ABSENT, 0)
                    : new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                            cropReplant ? ResourceFieldCycle.Crop.GROWING : ResourceFieldCycle.Crop.ABSENT, 0);
            var change = classify(before, after);
            if (change == null || change != (cropGrowth ? ResourceFieldCellObserved.Change.CROP_GROWN
                    : soilLoss ? ResourceFieldCellObserved.Change.SOIL_BECAME_DIRT
                    : cropReplant ? ResourceFieldCellObserved.Change.CROP_REPLANTED
                    : ResourceFieldCellObserved.Change.CROP_REMOVED)) return;
            var ledger = FrontierV3ResourceSiteLedger.get(level);
            if (state.resourceSites().hasPendingWorldChange(owner.siteId())
                    || ledger.fieldWorldChange(owner.siteId()) != null
                    || ledger.fieldForeignChange(owner.siteId()) != null || ledger.fieldPlayerBreak(owner.siteId()) != null
                    || cycle.pendingPlayerBreaks().containsKey(cell.id())
                    || FrontierV3ResourceSiteExplosionLedger.get(level).hasPendingSite(owner.siteId())
                    || !(ledger.fieldClaim(owner.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership claim)
                    || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                    || !claim.witness().matchesCycle(cycle)) return;
            var retained = claim.witness().cell(cell.id());
            if (retained.pending().isPresent() || retained.foreign().isPresent() || !retained.committed().equals(before)) return;
            var reading = FrontierV3ResourceFieldObservation.read(level, cell, "world-field-prewrite");
            if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned observed
                    && observed.condition().equals(before))
                    && !(reading instanceof FrontierV3ResourceFieldObservation.Unloaded
                    && FrontierV3ResourceFieldForeignChangeExecutor.matchesWritablePredecessor(
                            level, cell, position, canonical, retained))) return;
            var checkpoint = runtime.canonicalState().orElseThrow();
            String cause = "world:field-cell-" + owner.siteId().value() + "-e" + cycle.epoch()
                    + "-c" + cell.id().value() + "-r" + checkpoint.revision().value() + "-prewrite";
            var witness = new FrontierV3ResourceFieldWorldChangeWitness(new ResourceFieldCellObserved(
                    owner.siteId(), cycle.epoch(), cycle.layout().revision(), cell.id(), before, after,
                    change, ResourceFieldCellObserved.Source.WORLD, cause));
            ledger.beginFieldWorldChange(witness);
            ledger.persist(level);
            var accepted = hold(runtime, witness);
            FrontierV3DiagnosticTrace.record(level.getServer(), "field-world-prewrite:" + owner.siteId().value(),
                    "resource_field_world_change_held:prewrite", owner.siteId(), accepted);
            return;
        }
    }

    private static Map<Long, List<CellOwner>> cellOwners(FrontierWorldState state) {
        Map<Long, List<CellOwner>> byColumn = new HashMap<>();
        state.resourceSites().cycles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            for (ResourceFieldLayout.Cell cell : entry.getValue().layout().cells()) {
                long cropColumn = ChunkPos.asLong(cell.crop().x() >> 4, cell.crop().z() >> 4);
                long soilColumn = ChunkPos.asLong(cell.soil().support().x() >> 4, cell.soil().support().z() >> 4);
                var owner = new CellOwner(entry.getKey(), cell);
                byColumn.computeIfAbsent(cropColumn, ignored -> new ArrayList<>()).add(owner);
                if (soilColumn != cropColumn)
                    byColumn.computeIfAbsent(soilColumn, ignored -> new ArrayList<>()).add(owner);
            }
        });
        Map<Long, List<CellOwner>> closed = new HashMap<>();
        byColumn.forEach((column, cells) -> closed.put(column, List.copyOf(cells)));
        return Map.copyOf(closed);
    }

    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (FrontierV3ResourceFieldForeignChangeExecutor.reconcileOne(level, runtime)) return true;
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        for (var change : ledger.pendingFieldWorldChanges()) {
            var cycle = state.resourceSites().cycle(change.siteId());
            // A stale local witness still owns its site, but cannot starve a second site's
            // independently reconcilable physical cause while its recovery is unresolved.
            if (!change.matches(cycle)) continue;
            var held = state.resourceSites().pendingWorldChange(change.siteId());
            if (held == null) {
                var canonical = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(change.cellId()));
                // In this schema a WORLD result cannot have entered the WAL without its
                // retained hold. Equal blocks alone do not identify the witness's cause.
                if (!canonical.equals(change.before())) continue;
                // The SavedData predecessor is durable already; publish its exact canonical
                // hold even when the cell chunk is unloaded, before this server tick advances COLD.
                hold(runtime, change);
                return true;
            }
            if (!held.equals(change.observation())) continue;
            var cell = cycle.layout().requireCell(change.cellId());
            if (!level.hasChunkAt(minecraft(cell.crop())) || !level.hasChunkAt(minecraft(cell.soil().support())))
                continue;
            if (reconcile(level, runtime, ledger, change)) return true;
        }
        // The SavedData witness can already be retired while its canonical release command
        // was interrupted. The retained hold itself identifies the one cell to inspect.
        for (var held : state.resourceSites().pendingWorldChanges().values().stream()
                .sorted(java.util.Comparator.comparing(ResourceFieldCellObserved::siteId)).toList()) {
            if (ledger.fieldWorldChange(held.siteId()) != null) continue;
            var cycle = state.resourceSites().cycle(held.siteId());
            if (cycle.epoch() != held.epoch() || cycle.layout().revision() != held.layoutRevision()) continue;
            var reading = FrontierV3ResourceFieldObservation.read(level, cycle.layout().requireCell(held.cellId()),
                    held.causationId());
            if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)) continue;
            var canonical = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(held.cellId()));
            if (!canonical.equals(owned.condition())) continue;
            var owner = ledger.fieldClaim(held.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership value
                    ? value : null;
            if (owner == null || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                    || !owner.witness().matchesCycle(cycle)) continue;
            var cell = owner.witness().cell(held.cellId());
            if (cell.pending().isPresent() || cell.foreign().isPresent()
                    || !cell.committed().equals(canonical)) continue;
            if (acknowledge(runtime, held, canonical)) return true;
        }
        return false;
    }

    /** True means this cell was consumed by a world observation, not a projection. */
    static boolean observeOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              SubjectId siteId, ResourceFieldLayout.CellId cellId) {
        Objects.requireNonNull(level, "world field observation level");
        Objects.requireNonNull(runtime, "world field observation runtime");
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var cycle = state.resourceSites().cycle(siteId);
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        if (FrontierV3ResourceFieldForeignChangeExecutor.observeOne(level, runtime, siteId, cellId)) return true;
        var canonical = cycle.cell(cellId);
        if (canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED) return false;
        if (state.resourceSites().hasPendingWorldChange(siteId)
                || ledger.fieldWorldChange(siteId) != null || ledger.fieldForeignChange(siteId) != null
                || ledger.fieldPlayerBreak(siteId) != null
                || !cycle.pendingPlayerBreaks().isEmpty()
                || !(ledger.fieldClaim(siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return false;
        var claim = owner.witness().cell(cellId);
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cellId));
        if (claim.pending().isPresent() || claim.foreign().isPresent()) return false;
        var reading = FrontierV3ResourceFieldObservation.read(level, cycle.layout().requireCell(cellId),
                "world-field-change-candidate");
        if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)) return false;
        var change = classify(before, owned.condition());
        if (change == null) return false; // Foreign/unknown or another owner's transition: fail closed.
        if (!owner.witness().admitsWorldObservation(cellId, before, owned.condition()))
            return false; // Projection lag is not damage; growth beyond its target is real biology.
        var checkpoint = runtime.canonicalState().orElseThrow();
        String cause = "world:field-cell-" + siteId.value() + "-e" + cycle.epoch() + "-c" + cellId.value()
                + "-r" + checkpoint.revision().value();
        var witness = new FrontierV3ResourceFieldWorldChangeWitness(new ResourceFieldCellObserved(siteId,
                cycle.epoch(), cycle.layout().revision(), cellId, before, owned.condition(), change,
                ResourceFieldCellObserved.Source.WORLD, cause));
        ledger.beginFieldWorldChange(witness);
        ledger.persist(level); // Exact physical predecessor/postcondition precede canonical WAL admission.
        hold(runtime, witness);
        return true;
    }

    private static CommandResult.Accepted hold(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               FrontierV3ResourceFieldWorldChangeWitness change) {
        var checkpoint = runtime.canonicalState().orElseThrow();
        CommandId id = new CommandId("executor:field-world-hold-r" + checkpoint.revision().value());
        var result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new ResourceFieldWorldChangeHeld(change.observation()))).orElseThrow(
                        () -> new IllegalStateException("v3 runtime is inactive"));
        if (result instanceof CommandResult.Rejected rejected)
            throw new IllegalStateException("persisted world field change cannot install its canonical hold for "
                    + change.siteId().value() + ": " + rejected.rejection());
        return (CommandResult.Accepted) result;
    }

    /** Confirm actual growth immediately after a successful write so consecutive bone-meal uses do not overlap holds. */
    static void afterBlockWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                BlockPos position, BlockState replacement) {
        if (!replacement.is(Blocks.WHEAT)) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var owners = CELL_OWNERS.computeIfAbsent(runtime, ignored -> cellOwners(state));
        var column = owners.get(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
        if (column == null) return;
        for (var owner : column) {
            if (!minecraft(owner.cell().crop()).equals(position)) continue;
            var held = state.resourceSites().pendingWorldChange(owner.siteId());
            if (held != null && held.cellId().equals(owner.cell().id())
                    && held.change() == ResourceFieldCellObserved.Change.CROP_GROWN)
                reconcileOne(level, runtime);
            return;
        }
    }

    static ResourceFieldCellObserved.Change classify(ResourceFieldPhysicalSurface.Condition before,
                                                     ResourceFieldPhysicalSurface.Condition after) {
        if (before.soil() != ResourceFieldCycle.Soil.FARMLAND) return null;
        if (!before.equals(after) && after.equalsOrGrowsFrom(before))
            return ResourceFieldCellObserved.Change.CROP_GROWN;
        if ((before.crop() == ResourceFieldCycle.Crop.GROWING || before.crop() == ResourceFieldCycle.Crop.MATURE)
                && before.growthStage() > 0 && after.equals(new ResourceFieldPhysicalSurface.Condition(
                        ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 0)))
            return ResourceFieldCellObserved.Change.CROP_REPLANTED;
        if (after.equals(new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT,
                ResourceFieldCycle.Crop.ABSENT, 0))) return ResourceFieldCellObserved.Change.SOIL_BECAME_DIRT;
        if ((before.crop() == ResourceFieldCycle.Crop.GROWING || before.crop() == ResourceFieldCycle.Crop.MATURE)
                && after.equals(new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0))) return ResourceFieldCellObserved.Change.CROP_REMOVED;
        return null;
    }

    private static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierV3ResourceSiteLedger ledger,
                                     FrontierV3ResourceFieldWorldChangeWitness change) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var cycle = state.resourceSites().cycle(change.siteId());
        if (!change.matches(cycle)) return false;
        var held = state.resourceSites().pendingWorldChange(change.siteId());
        var reading = FrontierV3ResourceFieldObservation.read(level, cycle.layout().requireCell(change.cellId()),
                change.observation().causationId());
        if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)) return false;
        var canonical = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(change.cellId()));
        if (canonical.equals(change.before())) {
            if (!change.observation().equals(held)) return false;
            if (owned.condition().equals(change.before())) {
                // The exact physical predecessor returned before WAL admission. Retire the
                // saved witness first; a crash before release leaves the canonical hold.
                ledger.retireFieldWorldChange(change); ledger.persist(level);
                acknowledge(runtime, change.observation(), change.before());
                return true;
            }
            if (!owned.condition().equals(change.after())) return false;
            var checkpoint = runtime.canonicalState().orElseThrow();
            CommandId id = new CommandId("executor:field-world-change-r" + checkpoint.revision().value());
            var accepted = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                    checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                    change.observation())).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(accepted instanceof CommandResult.Accepted)) return false;
            FrontierV3DiagnosticTrace.record(level.getServer(), "field-world-change:" + change.siteId().value(),
                    "resource_field_cell_observed", change.siteId(), accepted);
            state = runtime.decodedState().orElseThrow();
            cycle = state.resourceSites().cycle(change.siteId());
        }
        if (!ResourceFieldPhysicalSurface.Condition.of(cycle.cell(change.cellId())).equals(change.after())
                || !owned.condition().equals(change.after())) return false;
        var owner = ledger.fieldClaim(change.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership value
                ? value : null;
        if (owner == null || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return false;
        var next = owner.witness().acknowledgeWorldChange(change, cycle, owned);
        if (next != owner.witness()) {
            ledger.replaceFieldClaim(owner, owner.withWitness(next)); ledger.persist(level);
        }
        ledger.retireFieldWorldChange(change); ledger.persist(level);
        if (held != null) acknowledge(runtime, held, change.after());
        return true;
    }

    private static boolean acknowledge(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       ResourceFieldCellObserved held,
                                       ResourceFieldPhysicalSurface.Condition physical) {
        var checkpoint = runtime.canonicalState().orElseThrow();
        CommandId id = new CommandId("executor:field-world-ack-r" + checkpoint.revision().value());
        var result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new ResourceFieldWorldChangeAcknowledged(held, physical))).orElseThrow(
                        () -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private static BlockPos minecraft(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
