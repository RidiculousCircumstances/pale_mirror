package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

/** Bounded, restart-reconstructible first-field writes; no per-tick whole-layout plan allocation. */
final class FrontierV3ResourceFieldInitialPlan {
    enum Disposition { UNLOADED, BEFORE, APPLIED, FOREIGN }
    record Step(SubjectId siteId, long layoutRevision, String layoutFingerprint, int index,
                BlockPosition position, BlockState target) {
        Step {
            Objects.requireNonNull(siteId, "initial field site");
            Objects.requireNonNull(layoutFingerprint, "initial field layout fingerprint");
            Objects.requireNonNull(position, "initial field block");
            Objects.requireNonNull(target, "initial field target block");
            if (index < 0 || layoutRevision < 1 || !layoutFingerprint.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("initial field step has invalid layout identity");
        }
    }
    static final class Review {
        private final Step step;
        private final Disposition disposition;
        private final Optional<BlockState> observed;
        private final boolean writtenThisCall;

        private Review(Step step, Disposition disposition, Optional<BlockState> observed, boolean writtenThisCall) {
            this.step = step; this.disposition = disposition; this.observed = observed;
            this.writtenThisCall = writtenThisCall;
        }
        Step step() { return step; }
        Disposition disposition() { return disposition; }
        Optional<BlockState> observed() { return observed; }
        boolean writtenThisCall() { return writtenThisCall; }
        boolean matches(ResourceSite site, int nextWrite, Disposition expected) {
            return disposition == expected && step.equals(stepAt(site, nextWrite));
        }
    }

    private FrontierV3ResourceFieldInitialPlan() { }

    static int writeCount(ResourceSite site) {
        ResourceFieldLayout layout = Objects.requireNonNull(site, "initial field site").layout();
        return Math.addExact(layout.irrigationSlots().size(), Math.multiplyExact(layout.cells().size(), 2));
    }

    static Step stepAt(ResourceSite site, int index) {
        Objects.requireNonNull(site, "initial field site");
        ResourceFieldLayout layout = site.layout();
        if (index < 0 || index >= writeCount(site)) throw new IllegalArgumentException("initial field write is outside its declared layout");
        int water = layout.irrigationSlots().size();
        BlockPosition position;
        BlockState target;
        if (index < water) {
            position = layout.irrigationSlots().get(index);
            target = Blocks.WATER.defaultBlockState();
        } else {
            int cellIndex = (index - water) >>> 1;
            ResourceFieldLayout.Cell cell = layout.cells().get(cellIndex);
            if (((index - water) & 1) == 0) {
                position = cell.soil().support();
                target = Blocks.FARMLAND.defaultBlockState();
            } else {
                position = cell.crop();
                target = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0);
            }
        }
        return new Step(site.id(), layout.revision(), layout.fingerprint(), index, position, target);
    }

    /** Read only the declared step; an unloaded position is never queried or forced into memory. */
    static Review observe(ServerLevel level, ResourceSite site, int index) {
        Objects.requireNonNull(level, "initial field world");
        Step step = stepAt(site, index);
        BlockPos position = new BlockPos(step.position().x(), step.position().y(), step.position().z());
        if (!level.hasChunkAt(position)) return new Review(step, Disposition.UNLOADED, Optional.empty(), false);
        BlockState current = level.getBlockState(position);
        int water = site.layout().irrigationSlots().size();
        boolean cropStep = index >= water && ((index - water) & 1) == 1;
        boolean baseline = cropStep ? current.isAir()
                : (current.is(Blocks.DIRT) || current.is(Blocks.GRASS_BLOCK)
                        || current.is(Blocks.LIGHT_GRAY_CONCRETE))
                        && !level.getBlockState(position.below()).isAir();
        Disposition disposition = current.equals(step.target()) ? Disposition.APPLIED
                : baseline ? Disposition.BEFORE : Disposition.FOREIGN;
        return new Review(step, disposition, Optional.of(current), false);
    }

    /** A prepared cursor permits one block write; only a write performed by this call is proof for advancement. */
    static Review applyPrepared(ServerLevel level, ResourceSite site,
                                FrontierV3ResourceSiteLedger ledger) {
        if (!(ledger.fieldClaim(site.id()) instanceof FrontierV3ResourceSiteLedger.FieldInitialization claim))
            throw new IllegalStateException("initial field write has no current SavedData claim");
        FrontierV3ResourceSiteLedger.InitialCursor cursor = claim.cursor();
        if (!claim.siteId().equals(site.id()) || claim.status() != FrontierV3ResourceSiteLedger.Status.PENDING
                || !cursor.matches(site) || !cursor.prepared() || cursor.complete())
            throw new IllegalArgumentException("initial field write has no prepared SavedData cursor");
        int index = cursor.nextWrite();
        Review before = observe(level, site, index);
        if (before.disposition() != Disposition.BEFORE) return before;
        Step step = before.step();
        BlockPos position = new BlockPos(step.position().x(), step.position().y(), step.position().z());
        if (!level.setBlock(position, step.target(), 3)) return observe(level, site, index);
        Review after = observe(level, site, index);
        return after.disposition() == Disposition.APPLIED
                ? new Review(after.step(), after.disposition(), after.observed(), true) : after;
    }

    /** A completed cursor is not proof that the complete field still exists at activation. */
    static void requireCompletePhysicalField(ServerLevel level, ResourceSite site,
                                             ResourceFieldCycle target,
                                             FrontierV3ResourceFieldWitness witness) {
        Objects.requireNonNull(level, "initial field world");
        Objects.requireNonNull(site, "initial field site");
        Objects.requireNonNull(target, "initial field target");
        Objects.requireNonNull(witness, "initial field witness");
        if (!site.id().equals(target.siteId()) || !witness.matchesCycle(target)
                || !site.layout().equals(target.layout()))
            throw new IllegalArgumentException("initial field physical completion has a foreign owner or layout");
        for (BlockPosition position : site.layout().irrigationSlots()) {
            BlockPos water = new BlockPos(position.x(), position.y(), position.z());
            if (!level.hasChunkAt(water) || !level.getBlockState(water).is(Blocks.WATER))
                throw new IllegalStateException("initial field irrigation is unloaded or no longer present");
        }
        for (ResourceFieldLayout.Cell cell : site.layout().cells()) {
            var observed = FrontierV3ResourceFieldObservation.read(level, cell, "field-initial-activation:" + site.id().value());
            if (!(observed instanceof FrontierV3ResourceFieldObservation.Owned owned)
                    || !owned.condition().equals(ResourceFieldPhysicalSurface.Condition.of(target.cell(cell.id()))))
                throw new IllegalStateException("initial field cell is unloaded or differs from the seeded target: " + cell.id().value());
        }
    }
}
