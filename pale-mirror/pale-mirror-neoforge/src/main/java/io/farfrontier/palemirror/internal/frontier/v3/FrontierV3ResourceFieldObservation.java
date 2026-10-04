package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** Read-only, one-cell loaded-world evidence. It never creates ownership or loads a chunk. */
final class FrontierV3ResourceFieldObservation {
    sealed interface Reading permits Unloaded, Owned, Foreign { }
    enum Unloaded implements Reading { INSTANCE }
    record Owned(ResourceFieldPhysicalSurface.Condition condition) implements Reading {
        Owned { Objects.requireNonNull(condition, "observed owned field condition"); }
    }
    record Foreign(FrontierV3ResourceFieldWitness.ForeignIncident incident) implements Reading {
        Foreign { Objects.requireNonNull(incident, "observed foreign field blocks"); }
    }

    enum Disposition { UNLOADED, CURRENT, NEXT_STEP_APPLIED, LATER_STEP_APPLIED, OWNED_DRIFT, FOREIGN }
    static final class Review {
        private final Disposition disposition;
        private final Reading reading;
        private final SubjectId siteId;
        private final long epoch;
        private final long layoutRevision;
        private final String layoutFingerprint;
        private final ResourceFieldLayout.CellId cellId;
        private final FrontierV3ResourceFieldWitness.Cell predecessor;
        private final boolean worldRead;
        private final int completedSteps;

        private Review(Disposition disposition, Reading reading,
                       SubjectId siteId, long epoch, ResourceFieldLayout layout,
                       ResourceFieldLayout.CellId cellId, FrontierV3ResourceFieldWitness.Cell predecessor,
                       boolean worldRead, int completedSteps) {
            this.disposition = Objects.requireNonNull(disposition, "field observation disposition");
            this.reading = Objects.requireNonNull(reading, "field observation evidence");
            this.siteId = Objects.requireNonNull(siteId, "field observation site owner");
            this.epoch = epoch;
            this.layoutRevision = layout.revision();
            this.layoutFingerprint = layout.fingerprint();
            this.cellId = cellId;
            this.predecessor = predecessor;
            this.worldRead = worldRead;
            this.completedSteps = completedSteps;
            if (completedSteps < 0 || (disposition == Disposition.NEXT_STEP_APPLIED
                    || disposition == Disposition.LATER_STEP_APPLIED) != (completedSteps > 0))
                throw new IllegalArgumentException("field review has an invalid observed projection prefix");
        }

        Disposition disposition() { return disposition; }
        Reading reading() { return reading; }
        int completedSteps() { return completedSteps; }

        boolean matches(FrontierV3ResourceFieldWitness witness, ResourceFieldLayout.CellId id) {
            return worldRead && siteId.equals(witness.siteId()) && epoch == witness.epoch()
                    && layoutRevision == witness.layoutRevision()
                    && layoutFingerprint.equals(witness.layoutFingerprint())
                    && cellId.equals(id) && predecessor.equals(witness.cell(id));
        }

        /** CURRENT is relative to the physical claim, not proof that biology has been projected. */
        boolean matchesWorkPredecessor(FrontierV3ResourceFieldWitness witness,
                                       ResourceFieldCellTransition transition) {
            return disposition == Disposition.CURRENT && matches(witness, transition.cellId())
                    && reading instanceof Owned owned && owned.condition().equals(transition.before());
        }
    }

    private FrontierV3ResourceFieldObservation() { }

    static Reading read(ServerLevel level, ResourceFieldLayout.Cell cell, String causationId) {
        Objects.requireNonNull(level, "observed field world");
        Objects.requireNonNull(cell, "observed field cell");
        BlockPos soil = minecraft(cell.soil().support());
        BlockPos crop = minecraft(cell.crop());
        if (!level.hasChunkAt(soil) || !level.hasChunkAt(crop)) return Unloaded.INSTANCE;
        return classify(level.getBlockState(soil), level.getBlockState(crop), causationId);
    }

    static Reading classify(BlockState soil, BlockState crop, String causationId) {
        Objects.requireNonNull(soil, "observed field soil");
        Objects.requireNonNull(crop, "observed field crop");
        Objects.requireNonNull(causationId, "field observation cause");
        if (causationId.isBlank()) throw new IllegalArgumentException("field observation has no exact cause");
        ResourceFieldCycle.Soil ownedSoil = soil.is(Blocks.FARMLAND) ? ResourceFieldCycle.Soil.FARMLAND
                : soil.is(Blocks.DIRT) ? ResourceFieldCycle.Soil.DIRT : null;
        if (ownedSoil != null && crop.is(Blocks.AIR))
            return new Owned(new ResourceFieldPhysicalSurface.Condition(ownedSoil, ResourceFieldCycle.Crop.ABSENT, 0));
        if (ownedSoil == ResourceFieldCycle.Soil.FARMLAND && crop.is(Blocks.WHEAT)) {
            int age = crop.getValue(CropBlock.AGE);
            return new Owned(new ResourceFieldPhysicalSurface.Condition(ownedSoil,
                    age == 7 ? ResourceFieldCycle.Crop.MATURE : ResourceFieldCycle.Crop.GROWING, age));
        }
        return new Foreign(new FrontierV3ResourceFieldWitness.ForeignIncident(
                NbtUtils.writeBlockState(soil), NbtUtils.writeBlockState(crop), causationId));
    }

    static Review compare(FrontierV3ResourceFieldWitness witness, ResourceFieldLayout layout,
                          ResourceFieldLayout.CellId id, Reading reading) {
        return compare(witness, layout, id, reading, false);
    }

    /** The only confirmation-capable review reads one naturally loaded cell itself. */
    static Review observe(ServerLevel level, ResourceFieldCycle cycle, FrontierV3ResourceFieldWitness witness,
                          ResourceFieldLayout.CellId id, String causationId) {
        Objects.requireNonNull(witness, "physical field claim");
        Objects.requireNonNull(cycle, "observed canonical field cycle");
        if (!witness.matchesCycle(cycle))
            throw new IllegalArgumentException("physical field claim has a foreign site, cycle or layout");
        ResourceFieldLayout layout = cycle.layout();
        ResourceFieldLayout.Cell cell = layout.requireCell(id);
        return compare(witness, layout, id, read(level, cell, causationId), true);
    }

    /**
     * Read an already-retained physical effect from a predecessor COLD epoch. This grants no
     * permission to start work in that epoch: only the exact persisted pending cell may be
     * inspected and confirmed, and the current canonical layout still owns its geometry.
     */
    static Review observePendingPredecessor(ServerLevel level, ResourceFieldCycle current,
                                            FrontierV3ResourceFieldWitness predecessor,
                                            ResourceFieldLayout.CellId id, String causationId) {
        Objects.requireNonNull(current, "current canonical field cycle");
        Objects.requireNonNull(predecessor, "predecessor physical field claim");
        if (!predecessor.matchesLayout(current.siteId(), current.layout())
                || predecessor.epoch() >= current.epoch()
                || predecessor.cell(id).pending().isEmpty()
                || current.pendingPlayerBreaks().containsKey(id))
            throw new IllegalArgumentException("field predecessor recovery lacks its exact pending owner or current layout");
        ResourceFieldLayout layout = current.layout();
        return compare(predecessor, layout, id, read(level, layout.requireCell(id), causationId), true);
    }

    private static Review compare(FrontierV3ResourceFieldWitness witness, ResourceFieldLayout layout,
                                  ResourceFieldLayout.CellId id, Reading reading, boolean worldRead) {
        Objects.requireNonNull(witness, "physical field claim");
        Objects.requireNonNull(layout, "observed field layout");
        Objects.requireNonNull(id, "observed field cell ID");
        Objects.requireNonNull(reading, "observed field blocks");
        if (!witness.matchesLayout(witness.siteId(), layout))
            throw new IllegalArgumentException("physical field claim has a foreign layout");
        FrontierV3ResourceFieldWitness.Cell claim = witness.cell(id);
        if (reading instanceof Unloaded) return new Review(Disposition.UNLOADED, reading, witness.siteId(), witness.epoch(), layout, id, claim, worldRead, 0);
        if (reading instanceof Foreign || claim.foreign().isPresent())
            return new Review(Disposition.FOREIGN, reading, witness.siteId(), witness.epoch(), layout, id, claim, worldRead, 0);
        ResourceFieldPhysicalSurface.Condition observed = ((Owned) reading).condition();
        if (claim.committed().equals(observed)) return new Review(Disposition.CURRENT, reading, witness.siteId(), witness.epoch(), layout, id, claim, worldRead, 0);
        if (claim.pending().isPresent()) {
            FrontierV3ResourceFieldWitness.Pending pending = claim.pending().orElseThrow();
            int matchingPrefix = 0;
            for (int next = pending.completedSteps() + 1; next <= pending.transition().steps().size(); next++) {
                if (!pending.transition().committedPrefix(next).equals(observed)) continue;
                if (matchingPrefix != 0) return new Review(Disposition.OWNED_DRIFT, reading, witness.siteId(), witness.epoch(), layout, id, claim, worldRead, 0);
                matchingPrefix = next;
            }
            if (matchingPrefix != 0) return new Review(
                    matchingPrefix == pending.completedSteps() + 1 ? Disposition.NEXT_STEP_APPLIED : Disposition.LATER_STEP_APPLIED,
                    reading, witness.siteId(), witness.epoch(), layout, id, claim, worldRead, matchingPrefix);
        }
        return new Review(Disposition.OWNED_DRIFT, reading, witness.siteId(), witness.epoch(), layout, id, claim, worldRead, 0);
    }

    private static BlockPos minecraft(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
