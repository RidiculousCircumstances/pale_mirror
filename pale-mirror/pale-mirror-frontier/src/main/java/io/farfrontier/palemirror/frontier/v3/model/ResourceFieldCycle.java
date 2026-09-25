package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Exact, immutable per-cell facts for one field epoch. A missing crop is not a
 * harvested crop, and beginning another epoch never plants or tills a block.
 * Individual cell work copies only its chunk; a scheduled growth-stage turn
 * visits the declared field once, not once per simulation tick.
 */
public final class ResourceFieldCycle {
    public enum Soil { UNKNOWN, FARMLAND, DIRT, OBSTRUCTED }
    public enum Crop { UNKNOWN, ABSENT, GROWING, MATURE, OBSTRUCTED }
    public enum WorkOutcome { HARVESTED, PLANTED, TILLED_AND_PLANTED, SKIPPED_IMMATURE, SKIPPED_BLOCKED }

    /** WAL-backed permission for exactly one imminent Vanilla player crop removal. */
    public record PendingPlayerBreak(UUID playerId, String actionId, ResourceFieldPhysicalSurface.Condition before) {
        public PendingPlayerBreak {
            Objects.requireNonNull(playerId, "field break player");
            Objects.requireNonNull(actionId, "field break action");
            Objects.requireNonNull(before, "field break predecessor");
            if (actionId.isBlank() || before.soil() != Soil.FARMLAND
                    || before.crop() != Crop.GROWING && before.crop() != Crop.MATURE)
                throw new IllegalArgumentException("field break has no exact owned crop predecessor");
        }
    }

    public record CellState(Soil soil, Crop crop, int growthStage, boolean accounted, boolean yielded) {
        public CellState {
            Objects.requireNonNull(soil, "field soil condition");
            Objects.requireNonNull(crop, "field crop condition");
            if (growthStage < 0 || growthStage > ResourceSiteLifecycle.MATURE_STAGE
                    || crop == Crop.MATURE && growthStage != ResourceSiteLifecycle.MATURE_STAGE
                    || crop == Crop.GROWING && growthStage == ResourceSiteLifecycle.MATURE_STAGE
                    || crop != Crop.GROWING && crop != Crop.MATURE && growthStage != 0
                    || yielded && !accounted
                    || (crop == Crop.GROWING || crop == Crop.MATURE) && soil != Soil.FARMLAND
                    || soil == Soil.UNKNOWN && crop != Crop.UNKNOWN
                    || soil == Soil.DIRT && crop != Crop.ABSENT && crop != Crop.OBSTRUCTED
                    || soil == Soil.OBSTRUCTED && crop != Crop.OBSTRUCTED) {
                throw new IllegalArgumentException("field cell condition is inconsistent");
            }
        }
        static CellState unsurveyed() { return new CellState(Soil.UNKNOWN, Crop.UNKNOWN, 0, false, false); }
    }

    private final SubjectId siteId;
    private final ResourceFieldLayout layout;
    private final long epoch;
    private final Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>> byChunk;
    private final Map<ResourceFieldLayout.CellId, PendingPlayerBreak> pendingPlayerBreaks;
    private final int accountedCount;
    private final int harvestedCount;
    private final int accountedPrefixCount;

    private ResourceFieldCycle(SubjectId siteId, ResourceFieldLayout layout, long epoch,
                               Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>> byChunk,
                               boolean validateCells) {
        this(siteId, layout, epoch, byChunk, Map.of(), validateCells);
    }

    private ResourceFieldCycle(SubjectId siteId, ResourceFieldLayout layout, long epoch,
                               Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>> byChunk,
                               Map<ResourceFieldLayout.CellId, PendingPlayerBreak> pendingPlayerBreaks,
                               boolean validateCells) {
        this(siteId, layout, epoch, byChunk, pendingPlayerBreaks, validateCells,
                (int) byChunk.values().stream().flatMap(values -> values.values().stream()).filter(CellState::accounted).count(),
                (int) byChunk.values().stream().flatMap(values -> values.values().stream()).filter(CellState::yielded).count(),
                accountedPrefix(layout, byChunk));
    }

    private ResourceFieldCycle(SubjectId siteId, ResourceFieldLayout layout, long epoch,
                               Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>> byChunk,
                               Map<ResourceFieldLayout.CellId, PendingPlayerBreak> pendingPlayerBreaks,
                               boolean validateCells, int accountedCount, int harvestedCount, int accountedPrefixCount) {
        this.siteId = Objects.requireNonNull(siteId, "field cycle site owner");
        if (!siteId.value().startsWith("site:"))
            throw new IllegalArgumentException("field cycle needs a declared site owner");
        this.layout = Objects.requireNonNull(layout, "field layout");
        if (epoch < 0) throw new IllegalArgumentException("field epoch must not be negative");
        this.epoch = epoch;
        var snapshot = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        byChunk.forEach((chunk, states) -> snapshot.put(Objects.requireNonNull(chunk),
                validateCells ? Map.copyOf(states) : Objects.requireNonNull(states)));
        this.byChunk = Map.copyOf(snapshot);
        this.pendingPlayerBreaks = Map.copyOf(Objects.requireNonNull(pendingPlayerBreaks, "pending field breaks"));
        if (accountedCount < 0 || accountedCount > layout.cells().size()
                || harvestedCount < 0 || harvestedCount > accountedCount
                || accountedPrefixCount < 0 || accountedPrefixCount > accountedCount)
            throw new IllegalArgumentException("field work/yield counters are invalid");
        this.accountedCount = accountedCount;
        this.harvestedCount = harvestedCount;
        this.accountedPrefixCount = accountedPrefixCount;
        if (validateCells) {
            for (ResourceFieldLayout.Cell cell : layout.cells()) {
                if (!this.byChunk.getOrDefault(chunkOf(cell), Map.of()).containsKey(cell.id()))
                    throw new IllegalArgumentException("field cycle omits an exact layout cell");
            }
            if (this.byChunk.values().stream().mapToInt(Map::size).sum() != layout.cells().size())
                throw new IllegalArgumentException("field cycle retains a foreign or duplicate cell");
            var actionIds = new java.util.HashSet<String>();
            for (PendingPlayerBreak pending : this.pendingPlayerBreaks.values()) {
                if (!actionIds.add(pending.actionId()))
                    throw new IllegalArgumentException("field snapshot reuses one physical break action across cells");
            }
        }
        this.pendingPlayerBreaks.forEach((id, pending) -> {
            if (!ResourceFieldPhysicalSurface.Condition.of(cell(id)).equals(pending.before()))
                throw new IllegalArgumentException("pending field break has a stale or foreign cell predecessor");
        });
    }

    public static ResourceFieldCycle unsurveyed(SubjectId siteId, ResourceFieldLayout layout, long epoch) {
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        for (ResourceFieldLayout.Cell cell : layout.cells())
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>()).put(cell.id(), CellState.unsurveyed());
        return new ResourceFieldCycle(siteId, layout, epoch, groups, true);
    }

    /** The initial preparation's exact field receipt seeds every declared cell. */
    public static ResourceFieldCycle seeded(SubjectId siteId, ResourceFieldLayout layout, long epoch) {
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        for (ResourceFieldLayout.Cell cell : layout.cells())
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>())
                    .put(cell.id(), new CellState(Soil.FARMLAND, Crop.GROWING, 0, false, false));
        return new ResourceFieldCycle(siteId, layout, epoch, groups, true);
    }

    /** Strict decoder boundary: no omitted, duplicate or foreign cell may be recovered. */
    public static ResourceFieldCycle restore(SubjectId siteId, ResourceFieldLayout layout, long epoch,
                                             Map<ResourceFieldLayout.CellId, CellState> cells) {
        return restore(siteId, layout, epoch, cells, Map.of());
    }

    public static ResourceFieldCycle restore(SubjectId siteId, ResourceFieldLayout layout, long epoch,
                                             Map<ResourceFieldLayout.CellId, CellState> cells,
                                             Map<ResourceFieldLayout.CellId, PendingPlayerBreak> pendingPlayerBreaks) {
        Objects.requireNonNull(layout, "field layout");
        Objects.requireNonNull(cells, "field cells");
        if (cells.size() != layout.cells().size()) throw new IllegalArgumentException("field cycle cell count differs from layout");
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            CellState state = cells.get(cell.id());
            if (state == null) throw new IllegalArgumentException("missing field cell state " + cell.id().value());
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>()).put(cell.id(), state);
        }
        return new ResourceFieldCycle(siteId, layout, epoch, groups, pendingPlayerBreaks, true);
    }

    public SubjectId siteId() { return siteId; }
    public ResourceFieldLayout layout() { return layout; }
    public long epoch() { return epoch; }
    public Map<ResourceFieldLayout.CellId, CellState> cellStates() {
        var cells = new LinkedHashMap<ResourceFieldLayout.CellId, CellState>();
        for (ResourceFieldLayout.Cell cell : layout.cells()) cells.put(cell.id(), cell(cell.id()));
        return Map.copyOf(cells);
    }
    public CellState cell(ResourceFieldLayout.CellId id) {
        ResourceFieldLayout.Cell geometry = layout.requireCell(id);
        return byChunk.get(chunkOf(geometry)).get(id);
    }
    public Map<ResourceFieldLayout.CellId, PendingPlayerBreak> pendingPlayerBreaks() { return pendingPlayerBreaks; }

    public ResourceFieldCycle preparePlayerBreak(ResourceFieldLayout.CellId id, PendingPlayerBreak pending) {
        Objects.requireNonNull(pending, "prepared field break");
        if (!ResourceFieldPhysicalSurface.Condition.of(cell(id)).equals(pending.before())
                || pendingPlayerBreaks.containsKey(id) || pendingPlayerBreaks.values().stream()
                        .anyMatch(existing -> existing.actionId().equals(pending.actionId())))
            throw new IllegalArgumentException("field break has a stale or duplicate predecessor");
        var next = new LinkedHashMap<>(pendingPlayerBreaks);
        next.put(id, pending);
        return new ResourceFieldCycle(siteId, layout, epoch, byChunk, next, false, accountedCount, harvestedCount, accountedPrefixCount);
    }

    public ResourceFieldCycle closePlayerBreak(ResourceFieldLayout.CellId id, String actionId) {
        PendingPlayerBreak pending = pendingPlayerBreaks.get(id);
        if (pending == null || !pending.actionId().equals(Objects.requireNonNull(actionId, "closed field break action")))
            throw new IllegalArgumentException("field break has no exact pending action");
        var next = new LinkedHashMap<>(pendingPlayerBreaks);
        next.remove(id);
        return new ResourceFieldCycle(siteId, layout, epoch, byChunk, next, false, accountedCount, harvestedCount, accountedPrefixCount);
    }
    public List<ResourceFieldLayout.Cell> pendingCellsIn(ResourceFieldLayout.ChunkColumn chunk) {
        return layout.cellsIn(chunk).stream().filter(cell -> !cell(cell.id()).accounted()).toList();
    }
    public int harvestedCount() {
        return harvestedCount;
    }
    public int accountedCount() {
        return accountedCount;
    }
    /** Exact contiguous work-order prefix, maintained incrementally and rebuilt on recovery. */
    public int accountedPrefixCount() { return accountedPrefixCount; }
    public boolean cycleAccounted() { return accountedCount() == layout.cells().size(); }

    /** Only a preparation receipt may declare both soil and planted crop current. */
    public ResourceFieldCycle prepared(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.UNKNOWN || prior.crop() != Crop.UNKNOWN || prior.accounted())
            throw new IllegalArgumentException("field preparation is stale or overwrites a known cell");
        return replace(id, new CellState(Soil.FARMLAND, Crop.GROWING, 0, false, false));
    }

    /** An actual observed player/world crop loss produces no settlement yield. */
    public ResourceFieldCycle cropRemoved(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND
                || prior.crop() != Crop.GROWING && prior.crop() != Crop.MATURE)
            throw new IllegalArgumentException("field crop loss is stale or not an owned live crop");
        return replace(id, new CellState(prior.soil(), Crop.ABSENT, 0, prior.accounted(), prior.yielded()));
    }

    /** Direct soil damage remains local; the observed crop cannot be credited twice. */
    public ResourceFieldCycle soilBecameDirt(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND) throw new IllegalArgumentException("field soil damage is stale or foreign");
        return replace(id, new CellState(Soil.DIRT,
                prior.crop() == Crop.OBSTRUCTED ? Crop.OBSTRUCTED : Crop.ABSENT,
                0, prior.accounted(), prior.yielded()));
    }

    /** A foreign crop-space block is recorded locally and never overwritten by projection. */
    public ResourceFieldCycle cropObstructed(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND && prior.soil() != Soil.DIRT
                || prior.crop() != Crop.GROWING && prior.crop() != Crop.MATURE && prior.crop() != Crop.ABSENT)
            throw new IllegalArgumentException("field crop obstruction is stale or not an owned crop space");
        return replace(id, new CellState(prior.soil(), Crop.OBSTRUCTED, 0, prior.accounted(), prior.yielded()));
    }

    /** A replaced support has unknown recoverable ground until another observation proves it. */
    public ResourceFieldCycle soilObstructed(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND && prior.soil() != Soil.DIRT)
            throw new IllegalArgumentException("field support obstruction is stale or unknown");
        return replace(id, new CellState(Soil.OBSTRUCTED, Crop.OBSTRUCTED, 0, prior.accounted(), prior.yielded()));
    }

    public ResourceFieldCycle obstructionCleared(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.crop() != Crop.OBSTRUCTED)
            throw new IllegalArgumentException("field cell has no observed obstruction to clear");
        return prior.soil() == Soil.FARMLAND || prior.soil() == Soil.DIRT
                ? replace(id, new CellState(prior.soil(), Crop.ABSENT, 0, prior.accounted(), prior.yielded()))
                : replace(id, new CellState(Soil.UNKNOWN, Crop.UNKNOWN, 0, prior.accounted(), prior.yielded()));
    }

    /** A later exact physical observation resolves the exposed support; it is not farmer work. */
    public ResourceFieldCycle observedBareSoil(ResourceFieldLayout.CellId id, Soil observed) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.UNKNOWN || prior.crop() != Crop.UNKNOWN
                || observed != Soil.FARMLAND && observed != Soil.DIRT)
            throw new IllegalArgumentException("bare-soil observation requires one unresolved exposed cell");
        return replace(id, new CellState(observed, Crop.ABSENT, 0, prior.accounted(), prior.yielded()));
    }

    /** A held world observation may change one obstructed cell without granting yield or work. */
    public ResourceFieldCycle observedForeignTransition(ResourceFieldLayout.CellId id, CellState after) {
        CellState prior = cell(id);
        Objects.requireNonNull(after, "observed foreign field postcondition");
        boolean wasForeign = prior.soil() == Soil.OBSTRUCTED || prior.crop() == Crop.OBSTRUCTED;
        boolean isForeign = after.soil() == Soil.OBSTRUCTED || after.crop() == Crop.OBSTRUCTED;
        if ((!wasForeign && !isForeign) || prior.soil() == Soil.UNKNOWN || prior.crop() == Crop.UNKNOWN
                || after.soil() == Soil.UNKNOWN || after.crop() == Crop.UNKNOWN
                || after.accounted() != prior.accounted() || after.yielded() != prior.yielded())
            throw new IllegalArgumentException("foreign field observation cannot invent owned work or an unknown cell");
        return replace(id, after);
    }

    /** A foreign-intended write can physically settle as an ordinary owned loss. */
    public ResourceFieldCycle observedInterference(ResourceFieldLayout.CellId id, CellState after) {
        CellState prior = cell(id);
        if (prior.soil() == Soil.OBSTRUCTED || prior.crop() == Crop.OBSTRUCTED
                || after.soil() == Soil.OBSTRUCTED || after.crop() == Crop.OBSTRUCTED)
            return observedForeignTransition(id, after);
        ResourceFieldCycle next;
        if (after.soil() == Soil.DIRT && after.crop() == Crop.ABSENT && prior.soil() == Soil.FARMLAND)
            next = soilBecameDirt(id);
        else if (after.soil() == Soil.FARMLAND && after.crop() == Crop.ABSENT
                && prior.soil() == Soil.FARMLAND)
            next = cropRemoved(id);
        else throw new IllegalArgumentException("foreign-intended field write produced no admitted owned change");
        if (!next.cell(id).equals(after))
            throw new IllegalArgumentException("foreign-intended field write changed work or yield without evidence");
        return next;
    }

    /** The current work plan accounts for an inaccessible cell without changing its block. */
    public ResourceFieldCycle skipBlocked(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.crop() != Crop.OBSTRUCTED || prior.accounted())
            throw new IllegalArgumentException("field cell has no pending obstruction to skip");
        return replace(id, new CellState(prior.soil(), prior.crop(), 0, true, false));
    }

    public ResourceFieldCycle advanceGrowth(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND || prior.crop() != Crop.GROWING || prior.accounted())
            throw new IllegalArgumentException("field cell cannot grow from its current condition");
        int next = Math.addExact(prior.growthStage(), 1);
        return replace(id, new CellState(prior.soil(), next == ResourceSiteLifecycle.MATURE_STAGE ? Crop.MATURE : Crop.GROWING,
                next, false, false));
    }

    /** The existing stage clock advances only live planted cells. */
    public ResourceFieldCycle advanceGrowthStage() {
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            CellState current = cell(cell.id());
            if (current.crop() == Crop.GROWING && !current.accounted() && !pendingPlayerBreaks.containsKey(cell.id())) {
                int next = Math.addExact(current.growthStage(), 1);
                current = new CellState(current.soil(), next == ResourceSiteLifecycle.MATURE_STAGE ? Crop.MATURE : Crop.GROWING,
                        next, false, false);
            }
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>()).put(cell.id(), current);
        }
        return new ResourceFieldCycle(siteId, layout, epoch, groups, pendingPlayerBreaks, true);
    }

    /** The farmer harvests one ripe crop and plants its successor at the same station. */
    public ResourceFieldCycle harvested(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND || prior.crop() != Crop.MATURE || prior.accounted())
            throw new IllegalArgumentException("field harvest cannot claim an absent or already processed crop");
        return replace(id, new CellState(prior.soil(), Crop.GROWING, 0, true, true));
    }

    /** A work receipt, not projection, repairs dirt without creating a crop. */
    public ResourceFieldCycle tilled(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.DIRT || prior.crop() != Crop.ABSENT)
            throw new IllegalArgumentException("field tillage requires observed plain dirt");
        return replace(id, new CellState(Soil.FARMLAND, Crop.ABSENT, 0, prior.accounted(), prior.yielded()));
    }

    /** The farmer plants a missing crop without claiming current-cycle yield. */
    public ResourceFieldCycle planted(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND || prior.crop() != Crop.ABSENT || prior.accounted())
            throw new IllegalArgumentException("field planting requires unaccounted empty farmland");
        return replace(id, new CellState(Soil.FARMLAND, Crop.GROWING, 0, true, false));
    }

    /** An immature crop is left intact while this visit accounts for no wheat. */
    public ResourceFieldCycle skipImmature(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.soil() != Soil.FARMLAND || prior.crop() != Crop.GROWING || prior.accounted())
            throw new IllegalArgumentException("field cell has no pending immature crop to skip");
        return replace(id, new CellState(prior.soil(), prior.crop(), prior.growthStage(), true, false));
    }

    /** One retained farmer visit: harvest/replant, repair/plant, or leave a local skip. */
    public ResourceFieldCycle worked(ResourceFieldLayout.CellId id) {
        return worked(id, expectedWorkOutcome(id));
    }

    public WorkOutcome expectedWorkOutcome(ResourceFieldLayout.CellId id) {
        CellState prior = cell(id);
        if (prior.accounted()) throw new IllegalArgumentException("field cell work was already accounted");
        return switch (prior.crop()) {
            case MATURE -> WorkOutcome.HARVESTED;
            case GROWING -> WorkOutcome.SKIPPED_IMMATURE;
            case ABSENT -> prior.soil() == Soil.DIRT ? WorkOutcome.TILLED_AND_PLANTED : WorkOutcome.PLANTED;
            case OBSTRUCTED -> WorkOutcome.SKIPPED_BLOCKED;
            case UNKNOWN -> throw new IllegalArgumentException("unsurveyed field cell cannot be worked");
        };
    }

    /** Exact physical delta of this cell's admitted work; a local skip writes no block. */
    public java.util.Optional<ResourceFieldCellTransition> physicalWorkTransition(ResourceFieldLayout.CellId id) {
        WorkOutcome outcome = expectedWorkOutcome(id);
        if (outcome == WorkOutcome.SKIPPED_IMMATURE || outcome == WorkOutcome.SKIPPED_BLOCKED)
            return java.util.Optional.empty();
        ResourceFieldPhysicalSurface.Condition before = ResourceFieldPhysicalSurface.Condition.of(cell(id));
        if (outcome == WorkOutcome.HARVESTED)
            return java.util.Optional.of(ResourceFieldCellTransition.harvestAndReplant(siteId, epoch, layout.revision(), id, before));
        ResourceFieldPhysicalSurface.Condition after = ResourceFieldPhysicalSurface.Condition.of(worked(id, outcome).cell(id));
        return java.util.Optional.of(ResourceFieldCellTransition.between(siteId, epoch, layout.revision(), id, before, after));
    }

    /** A durable cell receipt must declare the exact outcome, not just advance a count. */
    public ResourceFieldCycle worked(ResourceFieldLayout.CellId id, WorkOutcome outcome) {
        if (Objects.requireNonNull(outcome, "field work outcome") != expectedWorkOutcome(id))
            throw new IllegalArgumentException("field work outcome disagrees with the retained cell condition");
        return switch (outcome) {
            case HARVESTED -> harvested(id);
            case PLANTED -> planted(id);
            case TILLED_AND_PLANTED -> tilled(id).planted(id);
            case SKIPPED_IMMATURE -> skipImmature(id);
            case SKIPPED_BLOCKED -> skipBlocked(id);
        };
    }

    public ResourceFieldCycle nextEpoch() {
        if (!cycleAccounted()) throw new IllegalArgumentException("field cannot retire unaccounted cell work");
        if (!pendingPlayerBreaks.isEmpty()) throw new IllegalArgumentException("field cannot retire an unresolved player action");
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            CellState prior = cell(cell.id());
            CellState next = new CellState(prior.soil(), prior.crop(), prior.growthStage(),
                    false, false);
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>()).put(cell.id(), next);
        }
        return new ResourceFieldCycle(siteId, layout, Math.addExact(epoch, 1), groups, true);
    }

    /** Layout revisions must explicitly name every retired cell obligation. */
    public ResourceFieldCycle revised(ResourceFieldLayout next, Set<ResourceFieldLayout.CellId> retired) {
        if (!pendingPlayerBreaks.isEmpty()) throw new IllegalArgumentException("field cannot revise an unresolved player action");
        // A counted prefix belongs to the current work order. Rebuilding that prefix
        // against a reordered/shortened layout would silently reinterpret a retained
        // farmer cursor. The future field-revision owner must either finish the cycle
        // or explicitly migrate the job and every accounted outcome in one transition.
        if (accountedCount != 0)
            throw new IllegalArgumentException("field cannot revise accounted work without an explicit job migration");
        Objects.requireNonNull(next, "next field layout");
        if (next.revision() != Math.addExact(layout.revision(), 1))
            throw new IllegalArgumentException("field cycle requires the immediate next layout revision");
        layout.revise(next.revision(), next.nextCellId(), next.cells(), next.irrigationSlots());
        Set<ResourceFieldLayout.CellId> expectedRetired = new HashSet<>();
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            if (next.cell(cell.id()).isEmpty()) expectedRetired.add(cell.id());
        }
        if (!expectedRetired.equals(Set.copyOf(Objects.requireNonNull(retired, "retired field cells"))))
            throw new IllegalArgumentException("field revision must account for exactly its retired cell identities");
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>>();
        for (ResourceFieldLayout.Cell cell : next.cells()) {
            CellState state = layout.cell(cell.id()).isPresent() ? cell(cell.id()) : CellState.unsurveyed();
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>()).put(cell.id(), state);
        }
        return new ResourceFieldCycle(siteId, next, epoch, groups, true);
    }

    private ResourceFieldCycle replace(ResourceFieldLayout.CellId id, CellState next) {
        if (pendingPlayerBreaks.containsKey(id)) throw new IllegalArgumentException("field cell has an unresolved player action");
        ResourceFieldLayout.ChunkColumn chunk = chunkOf(layout.requireCell(id));
        var groups = new LinkedHashMap<>(byChunk);
        var cells = new LinkedHashMap<>(groups.get(chunk));
        CellState prior = cells.get(id);
        cells.put(id, Objects.requireNonNull(next));
        groups.put(chunk, Map.copyOf(cells));
        int prefix = accountedPrefixCount;
        int workIndex = layout.workIndex(id);
        if (prior.accounted() && !next.accounted() && workIndex < prefix) {
            prefix = workIndex;
        } else if (!prior.accounted() && next.accounted() && workIndex == prefix) {
            while (prefix < layout.cells().size()) {
                ResourceFieldLayout.Cell nextCell = layout.cells().get(prefix);
                if (!groups.get(chunkOf(nextCell)).get(nextCell.id()).accounted()) break;
                prefix++;
            }
        }
        return new ResourceFieldCycle(siteId, layout, epoch, groups, pendingPlayerBreaks, false,
                accountedCount + Boolean.compare(next.accounted(), prior.accounted()),
                harvestedCount + Boolean.compare(next.yielded(), prior.yielded()), prefix);
    }

    private static int accountedPrefix(ResourceFieldLayout layout,
                                       Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, CellState>> groups) {
        int prefix = 0;
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            CellState state = groups.getOrDefault(chunkOf(cell), Map.of()).get(cell.id());
            if (state == null || !state.accounted()) break;
            prefix++;
        }
        return prefix;
    }

    private static ResourceFieldLayout.ChunkColumn chunkOf(ResourceFieldLayout.Cell cell) {
        return new ResourceFieldLayout.ChunkColumn(Math.floorDiv(cell.crop().x(), 16), Math.floorDiv(cell.crop().z(), 16));
    }

    @Override public boolean equals(Object other) {
        return other instanceof ResourceFieldCycle cycle && siteId.equals(cycle.siteId) && epoch == cycle.epoch
                && layout.equals(cycle.layout) && byChunk.equals(cycle.byChunk)
                && pendingPlayerBreaks.equals(cycle.pendingPlayerBreaks);
    }
    @Override public int hashCode() { return Objects.hash(siteId, layout, epoch, byChunk, pendingPlayerBreaks); }
}
