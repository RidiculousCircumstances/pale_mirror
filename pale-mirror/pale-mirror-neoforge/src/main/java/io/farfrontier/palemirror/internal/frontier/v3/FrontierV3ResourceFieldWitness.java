package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestWorkAcceptance;
import io.farfrontier.palemirror.frontier.v3.persistence.ResourceSiteHarvestAcceptanceCodec;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Versioned value for the replacement SavedData field claim; not a second runtime owner. */
final class FrontierV3ResourceFieldWitness {
    private static final int FORMAT = 8;

    /** Raw block-state NBT is retained verbatim, never guessed from an OBSTRUCTED enum. */
    static final class ForeignIncident {
        private final CompoundTag observedSoil;
        private final CompoundTag observedCrop;
        private final String causationId;

        ForeignIncident(CompoundTag observedSoil, CompoundTag observedCrop, String causationId) {
            this.observedSoil = Objects.requireNonNull(observedSoil, "foreign soil state").copy();
            this.observedCrop = Objects.requireNonNull(observedCrop, "foreign crop state").copy();
            this.causationId = Objects.requireNonNull(causationId, "foreign cell cause");
            if (causationId.isBlank() || !this.observedSoil.contains("Name", Tag.TAG_STRING)
                    || this.observedSoil.getString("Name").isBlank()
                    || !this.observedCrop.contains("Name", Tag.TAG_STRING)
                    || this.observedCrop.getString("Name").isBlank())
                throw new IllegalArgumentException("foreign field cell lacks exact block identities");
        }
        CompoundTag observedSoil() { return observedSoil.copy(); }
        CompoundTag observedCrop() { return observedCrop.copy(); }
        String causationId() { return causationId; }
        @Override public boolean equals(Object other) {
            return other instanceof ForeignIncident incident && observedSoil.equals(incident.observedSoil)
                    && observedCrop.equals(incident.observedCrop) && causationId.equals(incident.causationId);
        }
        @Override public int hashCode() { return Objects.hash(observedSoil, observedCrop, causationId); }
    }

    /** The output half of one harvest effect; it does not itself mint canonical wheat. */
    record HandEffect(SubjectId siteId, SubjectId jobId, SubjectId actorId, UUID entityId,
                      long authorityEpoch, int beforeCount) {
        HandEffect {
            Objects.requireNonNull(siteId, "harvest hand site");
            Objects.requireNonNull(jobId, "harvest hand job");
            Objects.requireNonNull(actorId, "harvest hand actor");
            Objects.requireNonNull(entityId, "harvest hand body");
            if (!siteId.value().startsWith("site:") || authorityEpoch < 1 || beforeCount < 0 || beforeCount >= 64)
                throw new IllegalArgumentException("harvest hand effect lacks a bounded predecessor or authority");
        }
        int afterCount() { return beforeCount + 1; }
    }

    record CanonicalSource(WorldId worldId, Revision revision) {
        CanonicalSource {
            Objects.requireNonNull(worldId, "field projection canonical world");
            Objects.requireNonNull(revision, "field projection canonical revision");
        }
    }

    record Pending(ResourceFieldCellTransition transition, int completedSteps, String causationId,
                   Optional<HandEffect> handEffect, boolean handConfirmed,
                   Optional<CanonicalSource> canonicalSource) {
        Pending {
            Objects.requireNonNull(transition, "pending field projection");
            Objects.requireNonNull(causationId, "pending field cause");
            handEffect = Objects.requireNonNull(handEffect, "pending harvest hand effect");
            canonicalSource = Objects.requireNonNull(canonicalSource, "pending field authority source");
            if (causationId.isBlank()) throw new IllegalArgumentException("pending field effect has no cause");
            if (completedSteps < 0 || completedSteps > transition.steps().size())
                throw new IllegalArgumentException("pending field projection has a terminal or invalid cursor");
            if (handEffect.isPresent() && !transition.isHarvestAndReplant())
                throw new IllegalArgumentException("only a yielding harvest may retain an actor-hand effect");
            if (handConfirmed && (handEffect.isEmpty() || completedSteps != transition.steps().size()))
                throw new IllegalArgumentException("confirmed harvest hand lacks its complete paired crop effect");
            if (canonicalSource.isPresent() && (handEffect.isPresent() || handConfirmed || transition.isHarvestAndReplant()))
                throw new IllegalArgumentException("preaccepted canonical projection cannot masquerade as farmer work");
        }
        Pending(ResourceFieldCellTransition transition, int completedSteps, String causationId) {
            this(transition, completedSteps, causationId, Optional.empty(), false, Optional.empty());
        }
        Pending(ResourceFieldCellTransition transition, int completedSteps, String causationId,
                Optional<HandEffect> handEffect) {
            this(transition, completedSteps, causationId, handEffect, false, Optional.empty());
        }
    }

    record Cell(ResourceFieldPhysicalSurface.Condition committed, Optional<Pending> pending,
                Optional<ForeignIncident> foreign, Optional<ResourceSiteHarvestWorkAcceptance> retiredWork) {
        Cell(ResourceFieldPhysicalSurface.Condition committed, Optional<Pending> pending, Optional<ForeignIncident> foreign) {
            this(committed, pending, foreign, Optional.empty());
        }
        Cell {
            Objects.requireNonNull(committed, "committed field cell");
            pending = Objects.requireNonNull(pending, "pending field effect");
            foreign = Objects.requireNonNull(foreign, "foreign field incident");
            retiredWork = Objects.requireNonNull(retiredWork, "field work retirement seal");
            pending.ifPresent(effect -> {
                if (!effect.transition().committedPrefix(effect.completedSteps()).equals(committed))
                    throw new IllegalArgumentException("field effect cursor disagrees with its committed prefix");
            });
        }
    }

    private final SubjectId siteId;
    private final long epoch;
    private final long layoutRevision;
    private final String layoutFingerprint;
    private final Map<Long, Map<ResourceFieldLayout.CellId, Cell>> byIdBucket;
    private final int cellCount;
    private final String cellIdsFingerprint;

    private FrontierV3ResourceFieldWitness(SubjectId siteId, long epoch, long layoutRevision, String layoutFingerprint,
                                           Map<Long, Map<ResourceFieldLayout.CellId, Cell>> byIdBucket,
                                           int cellCount, String cellIdsFingerprint, boolean validate) {
        this.siteId = Objects.requireNonNull(siteId, "physical field site owner");
        if (!siteId.value().startsWith("site:"))
            throw new IllegalArgumentException("physical field witness has no declared site owner");
        if (epoch < 1 || layoutRevision < 1 || cellCount < 0 || cellCount > ResourceFieldLayout.MAX_CELLS)
            throw new IllegalArgumentException("physical field witness has invalid layout or cell count");
        if (layoutFingerprint == null || !layoutFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("physical field witness has no exact layout fingerprint");
        this.epoch = epoch;
        this.layoutRevision = layoutRevision;
        this.layoutFingerprint = layoutFingerprint;
        this.byIdBucket = Map.copyOf(byIdBucket);
        this.cellCount = cellCount;
        this.cellIdsFingerprint = Objects.requireNonNull(cellIdsFingerprint, "physical field cell identities");
        if (validate) {
            int counted = 0;
            for (var group : this.byIdBucket.entrySet()) {
                for (var entry : group.getValue().entrySet()) {
                    var id = Objects.requireNonNull(entry.getKey(), "physical field cell id");
                    var cell = Objects.requireNonNull(entry.getValue(), "physical field cell claim");
                    cell.retiredWork().ifPresent(seal -> {
                        if (!seal.receipt().siteId().equals(siteId) || !seal.receipt().cellId().equals(id)
                                || seal.receipt().layoutRevision() != layoutRevision || seal.receipt().epoch() > epoch)
                            throw new IllegalArgumentException("field work seal has a foreign cell, layout or future epoch");
                    });
                    if (bucket(id) != group.getKey()) throw new IllegalArgumentException("field witness has a foreign ID bucket");
                    cell.pending().ifPresent(effect -> {
                        if (!effect.transition().siteId().equals(siteId) || effect.transition().epoch() != epoch
                                || effect.transition().layoutRevision() != layoutRevision
                                || !effect.transition().cellId().equals(id))
                            throw new IllegalArgumentException("physical field effect has foreign layout or cell identity");
                        effect.handEffect().ifPresent(hand -> {
                            if (!hand.siteId().equals(siteId))
                                throw new IllegalArgumentException("physical harvest hand has a foreign site owner");
                        });
                    });
                    counted++;
                }
            }
            if (counted != cellCount) throw new IllegalArgumentException("field witness cell count is inconsistent");
        }
    }

    private static FrontierV3ResourceFieldWitness fromFlat(SubjectId siteId, long epoch, long layoutRevision, String layoutFingerprint,
                                                            Map<ResourceFieldLayout.CellId, Cell> cells) {
        var groups = new HashMap<Long, Map<ResourceFieldLayout.CellId, Cell>>();
        for (var entry : cells.entrySet())
            groups.computeIfAbsent(bucket(entry.getKey()), ignored -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
        groups.replaceAll((ignored, group) -> Map.copyOf(group));
        return new FrontierV3ResourceFieldWitness(siteId, epoch, layoutRevision, layoutFingerprint, groups, cells.size(),
                ResourceFieldLayout.fingerprintCellIds(cells.keySet()), true);
    }

    private static long bucket(ResourceFieldLayout.CellId id) {
        return (id.value() - 1L) >>> 8;
    }

    static FrontierV3ResourceFieldWitness claimed(SubjectId siteId, long epoch, ResourceFieldPhysicalSurface surface) {
        var claims = new LinkedHashMap<ResourceFieldLayout.CellId, Cell>();
        for (ResourceFieldLayout.Cell cell : surface.layout().cells())
            claims.put(cell.id(), new Cell(surface.cell(cell.id()), Optional.empty(), Optional.empty()));
        return fromFlat(siteId, epoch, surface.layout().revision(), surface.layout().fingerprint(), claims);
    }

    SubjectId siteId() { return siteId; }
    boolean projectionImageOnly() {
        return byIdBucket.values().stream().flatMap(bucket -> bucket.values().stream())
                .allMatch(cell -> cell.pending().isEmpty() && cell.foreign().isEmpty() && cell.retiredWork().isEmpty());
    }
    @Override public boolean equals(Object other) {
        return other instanceof FrontierV3ResourceFieldWitness witness
                && siteId.equals(witness.siteId) && epoch == witness.epoch
                && layoutRevision == witness.layoutRevision && layoutFingerprint.equals(witness.layoutFingerprint)
                && cellCount == witness.cellCount && cellIdsFingerprint.equals(witness.cellIdsFingerprint)
                && byIdBucket.equals(witness.byIdBucket);
    }
    @Override public int hashCode() {
        return Objects.hash(siteId, epoch, layoutRevision, layoutFingerprint, byIdBucket, cellCount, cellIdsFingerprint);
    }
    long epoch() { return epoch; }
    long layoutRevision() { return layoutRevision; }
    String layoutFingerprint() { return layoutFingerprint; }
    Cell cell(ResourceFieldLayout.CellId id) {
        Cell cell = byIdBucket.getOrDefault(bucket(Objects.requireNonNull(id, "field witness cell")), Map.of()).get(id);
        if (cell == null) throw new IllegalArgumentException("field witness has no declared cell");
        return cell;
    }
    record LocatedCell(ResourceFieldLayout.Cell geometry, Cell claim) { }

    /** Layout owns the chunk partition; the recovered witness supplies only matching cell facts. */
    java.util.List<LocatedCell> cellsIn(ResourceFieldCycle cycle, ResourceFieldLayout.ChunkColumn chunk) {
        Objects.requireNonNull(chunk, "field witness chunk");
        if (!matchesCycle(Objects.requireNonNull(cycle, "field witness canonical cycle")))
            throw new IllegalArgumentException("field witness cannot use a foreign site, cycle or layout partition");
        ResourceFieldLayout layout = cycle.layout();
        return layout.cellsIn(chunk).stream().map(geometry -> new LocatedCell(geometry, cell(geometry.id()))).toList();
    }
    boolean matchesLayout(SubjectId siteId, ResourceFieldLayout layout) {
        return this.siteId.equals(Objects.requireNonNull(siteId, "field witness site owner"))
                && layout.revision() == layoutRevision && layout.cells().size() == cellCount
                && layout.fingerprint().equals(layoutFingerprint)
                && layout.cellIdsFingerprint().equals(cellIdsFingerprint);
    }

    boolean matchesCycle(ResourceFieldCycle cycle) {
        return matchesLayout(cycle.siteId(), cycle.layout()) && epoch == cycle.epoch();
    }

    /**
     * Adopt a later canonical COLD epoch without claiming that its blocks have been written.
     * The per-cell physical conditions remain exactly as observed; ordinary projection must
     * still reconcile them. A pending HOT effect retains the old epoch and its cause until its
     * own physical/canonical receipt is resolved, so it cannot be relabelled here.
     */
    FrontierV3ResourceFieldWitness rebaseColdEpoch(ResourceFieldCycle current) {
        Objects.requireNonNull(current, "later canonical field cycle");
        if (!matchesLayout(current.siteId(), current.layout()) || current.epoch() <= epoch
                || !current.pendingPlayerBreaks().isEmpty())
            throw new IllegalArgumentException("field witness cannot adopt a foreign, unresolved or non-successor cycle");
        for (var bucket : byIdBucket.values()) {
            if (bucket.values().stream().anyMatch(cell -> cell.pending().isPresent()))
                throw new IllegalStateException("field witness cannot skip an unresolved physical cell effect");
        }
        return new FrontierV3ResourceFieldWitness(siteId, current.epoch(), layoutRevision, layoutFingerprint,
                byIdBucket, cellCount, cellIdsFingerprint, false);
    }

    /** Adopt only a WAL-accepted player postcondition under its retained physical witness. */
    FrontierV3ResourceFieldWitness acknowledgePlayerBreak(
            FrontierV3ResourceFieldPlayerBreakWitness action, ResourceFieldCycle accepted,
            FrontierV3ResourceFieldObservation.Reading physical) {
        Objects.requireNonNull(action, "accepted player break");
        Objects.requireNonNull(accepted, "accepted player field");
        Objects.requireNonNull(physical, "player break physical postcondition");
        if (!action.matches(accepted) || action.observedChange().isEmpty()
                || !matchesCycle(accepted) || accepted.pendingPlayerBreaks().containsKey(action.cellId())
                || !(physical instanceof FrontierV3ResourceFieldObservation.Owned owned)
                || !owned.condition().equals(action.after())
                || !ResourceFieldPhysicalSurface.Condition.of(accepted.cell(action.cellId())).equals(action.after()))
            throw new IllegalArgumentException("player field break lacks its accepted and observed postcondition");
        Cell prior = cell(action.cellId());
        if (prior.pending().isPresent() || prior.foreign().isPresent()
                || !prior.committed().equals(action.before()) && !prior.committed().equals(action.after()))
            throw new IllegalArgumentException("player field break has a foreign physical predecessor");
        return prior.committed().equals(action.after()) ? this
                : replace(action.cellId(), new Cell(action.after(), Optional.empty(), Optional.empty()));
    }

    /** Adopt only a persisted world postcondition that the canonical WAL has accepted. */
    FrontierV3ResourceFieldWitness acknowledgeWorldChange(
            FrontierV3ResourceFieldWorldChangeWitness change, ResourceFieldCycle accepted,
            FrontierV3ResourceFieldObservation.Reading physical) {
        Objects.requireNonNull(change, "accepted world field change");
        Objects.requireNonNull(accepted, "accepted world field");
        Objects.requireNonNull(physical, "world field physical postcondition");
        if (!change.matches(accepted) || !matchesCycle(accepted)
                || accepted.pendingPlayerBreaks().containsKey(change.cellId())
                || !(physical instanceof FrontierV3ResourceFieldObservation.Owned owned)
                || !owned.condition().equals(change.after())
                || !ResourceFieldPhysicalSurface.Condition.of(accepted.cell(change.cellId())).equals(change.after()))
            throw new IllegalArgumentException("world field change lacks its accepted and observed postcondition");
        Cell prior = cell(change.cellId());
        if (prior.pending().isPresent() || prior.foreign().isPresent()
                || !prior.committed().equals(change.before()) && !prior.committed().equals(change.after()))
            throw new IllegalArgumentException("world field change has a foreign physical predecessor");
        return prior.committed().equals(change.after()) ? this
                : replace(change.cellId(), new Cell(change.after(), Optional.empty(), Optional.empty()));
    }

    /** Exact foreign NBT remains outside the owned-surface condition until observed clearance. */
    FrontierV3ResourceFieldWitness acknowledgeForeignChange(
            FrontierV3ResourceFieldForeignChangeWitness change, ResourceFieldCycle accepted,
            FrontierV3ResourceFieldForeignChangeWitness.Blocks actual,
            FrontierV3ResourceFieldObservation.Reading reading) {
        Objects.requireNonNull(change, "accepted foreign field cause");
        Objects.requireNonNull(actual, "foreign field physical blocks");
        Objects.requireNonNull(reading, "foreign field physical reading");
        if (!change.matches(accepted) || !matchesCycle(accepted)
                || accepted.pendingPlayerBreaks().containsKey(change.cellId())
                || change.observed().isEmpty() || !change.observed().orElseThrow().equals(actual))
            throw new IllegalArgumentException("foreign field claim lacks its exact accepted physical result");
        Cell prior = cell(change.cellId());
        if (prior.pending().isPresent()) throw new IllegalArgumentException("foreign field claim overlaps a pending effect");
        var canonical = accepted.cell(change.cellId());
        if (reading instanceof FrontierV3ResourceFieldObservation.Foreign foreign) {
            if (canonical.soil() != ResourceFieldCycle.Soil.OBSTRUCTED
                    && canonical.crop() != ResourceFieldCycle.Crop.OBSTRUCTED
                    || !foreign.incident().observedSoil().equals(actual.soil())
                    || !foreign.incident().observedCrop().equals(actual.crop()))
                throw new IllegalArgumentException("foreign field incident disagrees with its canonical obstruction");
            var incident = new ForeignIncident(actual.soil(), actual.crop(), change.hold().causationId());
            return prior.foreign().filter(incident::equals).isPresent() ? this
                    : replace(change.cellId(), new Cell(prior.committed(), Optional.empty(), Optional.of(incident)));
        }
        if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)
                || canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                || !ResourceFieldPhysicalSurface.Condition.of(canonical).equals(owned.condition()))
            throw new IllegalArgumentException("foreign field clearance lacks an owned observed postcondition");
        return prior.foreign().isEmpty() && prior.committed().equals(owned.condition()) ? this
                : replace(change.cellId(), new Cell(owned.condition(), Optional.empty(), Optional.empty()));
    }

    FrontierV3ResourceFieldWitness begin(ResourceFieldCellTransition transition, String causationId) {
        Objects.requireNonNull(transition, "field projection transition");
        Objects.requireNonNull(causationId, "field projection cause");
        if (causationId.isBlank()) throw new IllegalArgumentException("field projection needs an exact cause");
        Cell prior = cell(transition.cellId());
        if (!siteId.equals(transition.siteId()) || transition.epoch() != epoch
                || transition.layoutRevision() != layoutRevision
                || !prior.committed().equals(transition.before())
                || prior.pending().isPresent() || prior.foreign().isPresent())
            throw new IllegalArgumentException("field projection lacks its exact unblocked predecessor");
        return replace(transition.cellId(), new Cell(prior.committed(),
                Optional.of(new Pending(transition, 0, causationId)), prior.foreign()));
    }

    /** Physical projection of a canonical target that was already accepted at this exact revision. */
    FrontierV3ResourceFieldWitness beginCanonicalProjection(FrontierCanonicalState<ResourceFieldCycle> accepted,
                                                            ResourceFieldCellTransition transition,
                                                            String causationId,
                                                            FrontierV3ResourceFieldObservation.Review physicalBefore) {
        Objects.requireNonNull(accepted, "accepted canonical field");
        Objects.requireNonNull(transition, "canonical field projection");
        Objects.requireNonNull(physicalBefore, "canonical projection physical predecessor");
        ResourceFieldCycle cycle = accepted.state();
        if (!matchesCycle(cycle) || !siteId.equals(transition.siteId())
                || transition.epoch() != cycle.epoch() || transition.layoutRevision() != layoutRevision
                || cycle.pendingPlayerBreaks().containsKey(transition.cellId())
                || transition.isHarvestAndReplant()
                || !transition.after().equals(ResourceFieldPhysicalSurface.Condition.of(cycle.cell(transition.cellId())))
                || physicalBefore.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT
                || !physicalBefore.matches(this, transition.cellId())
                || !(physicalBefore.reading() instanceof FrontierV3ResourceFieldObservation.Owned owned)
                || !owned.condition().equals(transition.before()))
            throw new IllegalArgumentException("field projection lacks its already-accepted canonical cell target");
        FrontierV3ResourceFieldWitness begun = begin(transition, causationId);
        Cell prior = begun.cell(transition.cellId());
        return begun.replace(transition.cellId(), new Cell(prior.committed(), Optional.of(new Pending(
                transition, 0, causationId, Optional.empty(), false,
                Optional.of(new CanonicalSource(accepted.worldId(), accepted.revision())))), prior.foreign()));
    }

    /** Write-ahead paired intent: crop removal/replant and one exact offhand increment share one cell claim. */
    FrontierV3ResourceFieldWitness beginHarvest(ResourceFieldCellTransition transition, String causationId,
                                               HandEffect handEffect,
                                               FrontierV3ResourceFieldObservation.Review fieldBefore,
                                               FrontierV3ActorHandObservation.Review handBefore) {
        Objects.requireNonNull(handEffect, "harvest hand effect");
        if (!siteId.equals(handEffect.siteId()))
            throw new IllegalArgumentException("paired harvest declares another site owner");
        Objects.requireNonNull(fieldBefore, "harvest field predecessor observation");
        Objects.requireNonNull(handBefore, "harvest hand predecessor observation");
        if (!fieldBefore.matchesWorkPredecessor(this, transition) || !handBefore.matchesBefore(handEffect))
            throw new IllegalArgumentException("paired harvest lacks its exact observed physical predecessors: site="
                    + siteId.value() + ";job=" + handEffect.jobId().value() + ";actor=" + handEffect.actorId().value()
                    + ";cell=" + transition.cellId().value() + ";epoch=" + epoch
                    + ";expected=" + transition.before() + ";observed=" + fieldBefore.reading()
                    + ";fieldDisposition=" + fieldBefore.disposition() + ";handDisposition=" + handBefore.disposition());
        FrontierV3ResourceFieldWitness begun = begin(transition, causationId);
        Cell prior = begun.cell(transition.cellId());
        return begun.replace(transition.cellId(), new Cell(prior.committed(),
                Optional.of(new Pending(transition, 0, causationId, Optional.of(handEffect))), prior.foreign()));
    }

    /** Accept only a matching review of the next physical step, not a caller-supplied expected state. */
    FrontierV3ResourceFieldWitness confirm(ResourceFieldLayout.CellId id,
                                           FrontierV3ResourceFieldObservation.Review review) {
        Objects.requireNonNull(review, "field step observation review");
        Cell prior = cell(id);
        Pending pending = prior.pending().orElseThrow(() -> new IllegalArgumentException("field cell has no pending projection"));
        if (prior.foreign().isPresent()) throw new IllegalArgumentException("foreign field cell cannot confirm an owned projection");
        if (review.disposition() != FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED
                && review.disposition() != FrontierV3ResourceFieldObservation.Disposition.LATER_STEP_APPLIED
                || !review.matches(this, id) || !(review.reading() instanceof FrontierV3ResourceFieldObservation.Owned owned))
            throw new IllegalArgumentException("field projection lacks a matching next-step observation");
        ResourceFieldPhysicalSurface.Condition observedAfter = owned.condition();
        int next = review.completedSteps();
        if (next <= pending.completedSteps() || next > pending.transition().steps().size())
            throw new IllegalArgumentException("field projection review does not advance its retained prefix");
        if (!pending.transition().committedPrefix(next).equals(observedAfter))
            throw new IllegalArgumentException("field projection observed the wrong physical postcondition");
        // The physical prefix is not the canonical work receipt. Retain even a completed
        // planting/tillage effect until a later durable canonical acknowledgement can
        // retire its cause; otherwise a crash after the final block write loses the join.
        return replace(id, new Cell(observedAfter,
                Optional.of(new Pending(pending.transition(), next, pending.causationId(),
                        pending.handEffect(), pending.handConfirmed(), pending.canonicalSource())), prior.foreign()));
    }

    /** A real after-stack seals the physical pair, but only a later canonical receipt may retire its witness. */
    FrontierV3ResourceFieldWitness confirmHand(ResourceFieldLayout.CellId id,
                                                FrontierV3ActorHandObservation.Review review) {
        Objects.requireNonNull(review, "harvest hand observation review");
        Cell prior = cell(id);
        Pending pending = prior.pending().orElseThrow(() -> new IllegalArgumentException("field cell has no pending harvest"));
        HandEffect hand = pending.handEffect().orElseThrow(() -> new IllegalArgumentException("field effect has no hand half"));
        if (prior.foreign().isPresent() || pending.handConfirmed()
                || pending.completedSteps() != pending.transition().steps().size()
                || !review.matches(hand) || review.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT
                || review.stack().orElseThrow().quantity() != hand.afterCount())
            throw new IllegalArgumentException("paired harvest lacks its exact observed worker-hand postcondition");
        return replace(id, new Cell(prior.committed(), Optional.of(new Pending(pending.transition(),
                pending.completedSteps(), pending.causationId(), pending.handEffect(), true,
                pending.canonicalSource())), prior.foreign()));
    }

    /** Retire using the durable historical pair; no current body, scene or next-cell cursor is authority. */
    FrontierV3ResourceFieldWitness retireWork(ResourceSiteHarvestWorkAcceptance accepted) {
        var receipt = accepted.receipt();
        if (!receipt.siteId().equals(siteId) || receipt.epoch() != epoch || receipt.layoutRevision() != layoutRevision)
            throw new IllegalArgumentException("accepted field work has a foreign witness owner/epoch/layout");
        Cell prior = cell(receipt.cellId());
        if (prior.pending().isEmpty()) {
            if (!prior.retiredWork().equals(Optional.of(accepted)))
                throw new IllegalArgumentException("accepted field work has neither its pending effect nor exact retirement seal");
            return this;
        }
        Pending pending = prior.pending().orElseThrow();
        if (pending.canonicalSource().isPresent() || !pending.causationId().equals(accepted.causationId())
                || !pending.transition().equals(accepted.transition())
                || pending.completedSteps() != pending.transition().steps().size()
                || !prior.committed().equals(accepted.transition().after()))
            throw new IllegalArgumentException("field retirement disagrees with accepted effect: site=" + siteId.value()
                    + ";cell=" + receipt.cellId().value() + ";expectedCause=" + accepted.causationId()
                    + ";actualCause=" + pending.causationId() + ";steps=" + pending.completedSteps());
        if (pending.handEffect().isPresent()) {
            HandEffect effect = pending.handEffect().orElseThrow();
            var hand = receipt.observedHand().orElseThrow();
            if (!pending.handConfirmed() || !effect.jobId().equals(receipt.jobId())
                    || !effect.siteId().equals(receipt.siteId()) || !effect.actorId().equals(hand.address().actorId())
                    || !effect.entityId().equals(hand.address().entityId()) || effect.authorityEpoch() != hand.authorityEpoch()
                    || effect.afterCount() != hand.quantity())
                throw new IllegalArgumentException("field retirement has a foreign or unconfirmed historical actor hand");
        } else if (pending.handConfirmed() || accepted.transition().isHarvestAndReplant()) {
            throw new IllegalArgumentException("yielding field retirement has no paired physical hand");
        }
        return replace(receipt.cellId(), new Cell(prior.committed(), Optional.empty(), prior.foreign(), Optional.of(accepted)));
    }


    /** A preaccepted canonical target can retire its own complete physical projection, never a farmer work effect. */
    FrontierV3ResourceFieldWitness acknowledgeCanonicalProjection(FrontierCanonicalState<ResourceFieldCycle> current,
                                                                   ResourceFieldLayout.CellId id, String causationId,
                                                                   FrontierV3ResourceFieldObservation.Review observed) {
        Objects.requireNonNull(current, "current canonical field");
        Objects.requireNonNull(id, "projection cell");
        Objects.requireNonNull(causationId, "projection cause");
        Objects.requireNonNull(observed, "projection physical observation");
        Cell prior = cell(id);
        Pending pending = prior.pending().orElseThrow(() -> new IllegalArgumentException("field cell has no pending physical cause"));
        CanonicalSource source = pending.canonicalSource().orElseThrow(
                () -> new IllegalArgumentException("physical-first work cannot use canonical projection acknowledgement"));
        ResourceFieldCycle cycle = current.state();
        if (!source.worldId().equals(current.worldId()) || current.revision().compareTo(source.revision()) < 0
                || !matchesCycle(cycle) || cycle.pendingPlayerBreaks().containsKey(id)
                || !pending.causationId().equals(causationId)
                || pending.completedSteps() != pending.transition().steps().size()
                || prior.foreign().isPresent() || pending.handEffect().isPresent()
                || !prior.committed().equals(pending.transition().after())
                || !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(id)).equalsOrGrowsFrom(prior.committed())
                || observed.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT
                || !observed.matches(this, id))
            throw new IllegalArgumentException("field projection lacks the same accepted canonical and loaded physical result");
        return replace(id, new Cell(prior.committed(), Optional.empty(), Optional.empty()));
    }

    /** Inspect monotonic plant biology within an already accepted growth target; never infer work or yield. */
    FrontierV3ResourceFieldWitness acknowledgeObservedGrowth(FrontierCanonicalState<ResourceFieldCycle> accepted,
                                                            ResourceFieldLayout.CellId id,
                                                            FrontierV3ResourceFieldObservation.Review observed) {
        var cycle = accepted.state();
        var prior = cell(id);
        var target = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(id));
        if (!matchesCycle(cycle) || cycle.pendingPlayerBreaks().containsKey(id)
                || prior.committed().soil() != ResourceFieldCycle.Soil.FARMLAND
                || prior.pending().isPresent() || prior.foreign().isPresent()
                || observed.disposition() != FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT
                || !observed.matches(this, id)
                || !(observed.reading() instanceof FrontierV3ResourceFieldObservation.Owned actual)
                || actual.condition().equals(prior.committed())
                || !actual.condition().equalsOrGrowsFrom(prior.committed())
                || !target.equalsOrGrowsFrom(actual.condition()))
            throw new IllegalArgumentException("observed growth lacks its owned monotonic accepted biology boundary");
        return replace(id, new Cell(((FrontierV3ResourceFieldObservation.Owned) observed.reading()).condition(),
                Optional.empty(), Optional.empty()));
    }

    FrontierV3ResourceFieldWitness foreign(ResourceFieldLayout.CellId id, ForeignIncident incident) {
        Cell prior = cell(id);
        if (prior.foreign().isPresent()) throw new IllegalArgumentException("field cell already retains a foreign incident");
        return replace(id, new Cell(prior.committed(), prior.pending(), Optional.of(Objects.requireNonNull(incident))));
    }

    private FrontierV3ResourceFieldWitness replace(ResourceFieldLayout.CellId id, Cell replacement) {
        // Biology/world observations cannot erase the last exact retirement seal.
        // It is bounded to one per CellId and replaced only by the next retireWork receipt.
        if (replacement.retiredWork().isEmpty() && cell(id).retiredWork().isPresent())
            replacement = new Cell(replacement.committed(), replacement.pending(), replacement.foreign(), cell(id).retiredWork());
        long bucket = bucket(id);
        var next = new HashMap<>(byIdBucket);
        var group = new HashMap<>(next.get(bucket)); group.put(id, replacement);
        next.put(bucket, Map.copyOf(group));
        return new FrontierV3ResourceFieldWitness(siteId, epoch, layoutRevision, layoutFingerprint, next, cellCount,
                cellIdsFingerprint, false);
    }

    CompoundTag write() {
        var tag = new CompoundTag(); tag.putInt("format", FORMAT); tag.putString("site", siteId.value());
        tag.putLong("epoch", epoch);
        tag.putLong("layoutRevision", layoutRevision);
        tag.putString("layoutFingerprint", layoutFingerprint);
        var entries = new ListTag();
        byIdBucket.values().stream().flatMap(group -> group.entrySet().stream())
                .sorted(Map.Entry.comparingByKey(Comparator.comparingLong(ResourceFieldLayout.CellId::value)))
                .forEach(entry -> {
                    var value = new CompoundTag(); value.putLong("id", entry.getKey().value());
                    writeCondition(value, "committed", entry.getValue().committed());
                    entry.getValue().retiredWork().ifPresent(seal -> value.putByteArray("retiredWork",
                            ResourceSiteHarvestAcceptanceCodec.encodeAcceptance(seal)));
                    entry.getValue().pending().ifPresent(pending -> {
                        var effect = new CompoundTag();
                        writeCondition(effect, "before", pending.transition().before());
                        writeCondition(effect, "after", pending.transition().after());
                        effect.putString("mode", pending.transition().isHarvestAndReplant() ? "HARVEST_REPLANT" : "DIRECT");
                        effect.putInt("steps", pending.transition().steps().size());
                        effect.putString("cause", pending.causationId());
                        effect.putInt("completed", pending.completedSteps());
                        effect.putBoolean("handConfirmed", pending.handConfirmed());
                        effect.putString("origin", pending.canonicalSource().isPresent() ? "CANONICAL" : "WORK");
                        pending.canonicalSource().ifPresent(source -> {
                            effect.putString("canonicalWorld", source.worldId().value());
                            effect.putLong("canonicalRevision", source.revision().value());
                        });
                        pending.handEffect().ifPresent(hand -> {
                            var handTag = new CompoundTag();
                            handTag.putString("site", hand.siteId().value());
                            handTag.putString("job", hand.jobId().value());
                            handTag.putString("actor", hand.actorId().value());
                            handTag.putUUID("entity", hand.entityId());
                            handTag.putLong("authorityEpoch", hand.authorityEpoch());
                            handTag.putInt("before", hand.beforeCount());
                            handTag.putInt("after", hand.afterCount());
                            effect.put("hand", handTag);
                        });
                        value.put("pending", effect);
                    });
                    entry.getValue().foreign().ifPresent(incident -> {
                        var foreign = new CompoundTag(); foreign.put("soil", incident.observedSoil());
                        foreign.put("crop", incident.observedCrop()); foreign.putString("cause", incident.causationId());
                        value.put("foreign", foreign);
                    });
                    entries.add(value);
                });
        tag.put("cells", entries); return tag;
    }

    static FrontierV3ResourceFieldWitness read(CompoundTag tag) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT
                || !tag.contains("site", Tag.TAG_STRING) || !tag.contains("epoch", Tag.TAG_LONG)
                || !tag.contains("layoutRevision", Tag.TAG_LONG)
                || !tag.contains("layoutFingerprint", Tag.TAG_STRING) || !tag.contains("cells", Tag.TAG_LIST))
            throw new IllegalStateException("incompatible or incomplete physical field witness");
        long revision = tag.getLong("layoutRevision");
        ListTag entries = tag.getList("cells", Tag.TAG_COMPOUND);
        if (entries.size() > ResourceFieldLayout.MAX_CELLS)
            throw new IllegalStateException("physical field witness has an invalid cell count");
        var claims = new LinkedHashMap<ResourceFieldLayout.CellId, Cell>();
        for (Tag element : entries) {
            var value = (CompoundTag) element;
            if (!value.contains("id", Tag.TAG_LONG)) throw new IllegalStateException("physical field witness omits a cell ID");
            var id = new ResourceFieldLayout.CellId(value.getLong("id"));
            var committed = readCondition(value, "committed");
            Optional<Pending> pending = Optional.empty();
            if (value.contains("pending") && !value.contains("pending", Tag.TAG_COMPOUND))
                throw new IllegalStateException("pending field projection has an invalid tag type");
            if (value.contains("pending", Tag.TAG_COMPOUND)) {
                var effect = value.getCompound("pending");
                if (!effect.contains("completed", Tag.TAG_INT) || !effect.contains("mode", Tag.TAG_STRING)
                        || !effect.contains("steps", Tag.TAG_INT) || !effect.contains("cause", Tag.TAG_STRING)
                        || !effect.contains("handConfirmed", Tag.TAG_BYTE)
                        || !effect.contains("origin", Tag.TAG_STRING))
                    throw new IllegalStateException("pending field projection omits its exact cursor or mode");
                Optional<CanonicalSource> canonicalSource = switch (effect.getString("origin")) {
                    case "WORK" -> {
                        if (effect.contains("canonicalWorld") || effect.contains("canonicalRevision"))
                            throw new IllegalStateException("physical-first field work borrows a canonical projection origin");
                        yield Optional.empty();
                    }
                    case "CANONICAL" -> {
                        if (!effect.contains("canonicalWorld", Tag.TAG_STRING)
                                || !effect.contains("canonicalRevision", Tag.TAG_LONG))
                            throw new IllegalStateException("field projection has no exact accepted canonical source");
                        yield Optional.of(new CanonicalSource(new WorldId(effect.getString("canonicalWorld")),
                                new Revision(effect.getLong("canonicalRevision"))));
                    }
                    default -> throw new IllegalStateException("unknown field physical-cause origin");
                };
                var before = readCondition(effect, "before");
                var after = readCondition(effect, "after");
                ResourceFieldCellTransition transition = switch (effect.getString("mode")) {
                    case "DIRECT" -> ResourceFieldCellTransition.between(new SubjectId(tag.getString("site")),
                            tag.getLong("epoch"), revision, id, before, after);
                    case "HARVEST_REPLANT" -> {
                        var work = ResourceFieldCellTransition.harvestAndReplant(new SubjectId(tag.getString("site")),
                                tag.getLong("epoch"), revision, id, before);
                        if (!work.after().equals(after))
                            throw new IllegalStateException("harvest field projection has a foreign terminal condition");
                        yield work;
                    }
                    default -> throw new IllegalStateException("unknown physical field projection mode");
                };
                if (effect.getInt("steps") != transition.steps().size())
                    throw new IllegalStateException("physical field projection mode changes its retained step count");
                Optional<HandEffect> hand = Optional.empty();
                if (effect.contains("hand") && !effect.contains("hand", Tag.TAG_COMPOUND))
                    throw new IllegalStateException("pending harvest hand has an invalid tag type");
                if (effect.contains("hand", Tag.TAG_COMPOUND)) {
                    var handTag = effect.getCompound("hand");
                    if (!handTag.contains("site", Tag.TAG_STRING) || !handTag.contains("job", Tag.TAG_STRING)
                            || !handTag.contains("actor", Tag.TAG_STRING)
                            || !handTag.hasUUID("entity") || !handTag.contains("authorityEpoch", Tag.TAG_LONG)
                            || !handTag.contains("before", Tag.TAG_INT) || !handTag.contains("after", Tag.TAG_INT))
                        throw new IllegalStateException("pending harvest hand omits its exact predecessor or owner");
                    var restored = new HandEffect(new SubjectId(handTag.getString("site")),
                            new SubjectId(handTag.getString("job")),
                            new SubjectId(handTag.getString("actor")), handTag.getUUID("entity"),
                            handTag.getLong("authorityEpoch"), handTag.getInt("before"));
                    if (!restored.siteId().value().equals(tag.getString("site")))
                        throw new IllegalStateException("pending harvest hand has a foreign site owner");
                    if (handTag.getInt("after") != restored.afterCount())
                        throw new IllegalStateException("pending harvest hand has a foreign successor quantity");
                    hand = Optional.of(restored);
                }
                byte handConfirmed = effect.getByte("handConfirmed");
                if (handConfirmed != 0 && handConfirmed != 1)
                    throw new IllegalStateException("pending harvest hand has an invalid confirmation flag");
                pending = Optional.of(new Pending(transition, effect.getInt("completed"), effect.getString("cause"),
                        hand, handConfirmed == 1, canonicalSource));
            }
            Optional<ForeignIncident> foreign = Optional.empty();
            if (value.contains("foreign") && !value.contains("foreign", Tag.TAG_COMPOUND))
                throw new IllegalStateException("foreign field incident has an invalid tag type");
            if (value.contains("foreign", Tag.TAG_COMPOUND)) {
                var incident = value.getCompound("foreign");
                if (!incident.contains("soil", Tag.TAG_COMPOUND) || !incident.contains("crop", Tag.TAG_COMPOUND)
                        || !incident.contains("cause", Tag.TAG_STRING))
                    throw new IllegalStateException("incomplete foreign field incident");
                foreign = Optional.of(new ForeignIncident(incident.getCompound("soil"), incident.getCompound("crop"),
                        incident.getString("cause")));
            }
            if (value.contains("retiredWork") && !value.contains("retiredWork", Tag.TAG_BYTE_ARRAY))
                throw new IllegalStateException("field work retirement seal has an invalid tag type");
            var retired = value.contains("retiredWork", Tag.TAG_BYTE_ARRAY)
                    ? Optional.of(ResourceSiteHarvestAcceptanceCodec.decodeAcceptance(value.getByteArray("retiredWork")))
                    : Optional.<ResourceSiteHarvestWorkAcceptance>empty();
            if (claims.put(id, new Cell(committed, pending, foreign, retired)) != null)
                throw new IllegalStateException("duplicate physical field cell claim");
        }
        return fromFlat(new SubjectId(tag.getString("site")), tag.getLong("epoch"), revision,
                tag.getString("layoutFingerprint"), claims);
    }

    private static void writeCondition(CompoundTag parent, String key, ResourceFieldPhysicalSurface.Condition condition) {
        var tag = new CompoundTag(); tag.putInt("soil", soilTag(condition.soil()));
        tag.putInt("crop", cropTag(condition.crop())); tag.putInt("stage", condition.growthStage());
        parent.put(key, tag);
    }
    private static ResourceFieldPhysicalSurface.Condition readCondition(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) throw new IllegalStateException("missing physical field condition");
        var tag = parent.getCompound(key);
        if (!tag.contains("soil", Tag.TAG_INT) || !tag.contains("crop", Tag.TAG_INT) || !tag.contains("stage", Tag.TAG_INT))
            throw new IllegalStateException("incomplete physical field condition");
        return new ResourceFieldPhysicalSurface.Condition(soil(tag.getInt("soil")), crop(tag.getInt("crop")), tag.getInt("stage"));
    }
    private static int soilTag(ResourceFieldCycle.Soil soil) {
        return switch (soil) { case FARMLAND -> 1; case DIRT -> 2; default -> throw new IllegalArgumentException("unowned soil claim"); };
    }
    private static ResourceFieldCycle.Soil soil(int tag) {
        return switch (tag) { case 1 -> ResourceFieldCycle.Soil.FARMLAND; case 2 -> ResourceFieldCycle.Soil.DIRT;
            default -> throw new IllegalArgumentException("unknown physical soil tag " + tag); };
    }
    private static int cropTag(ResourceFieldCycle.Crop crop) {
        return switch (crop) { case ABSENT -> 1; case GROWING -> 2; case MATURE -> 3;
            default -> throw new IllegalArgumentException("unowned crop claim"); };
    }
    private static ResourceFieldCycle.Crop crop(int tag) {
        return switch (tag) { case 1 -> ResourceFieldCycle.Crop.ABSENT; case 2 -> ResourceFieldCycle.Crop.GROWING;
            case 3 -> ResourceFieldCycle.Crop.MATURE; default -> throw new IllegalArgumentException("unknown physical crop tag " + tag); };
    }
}
