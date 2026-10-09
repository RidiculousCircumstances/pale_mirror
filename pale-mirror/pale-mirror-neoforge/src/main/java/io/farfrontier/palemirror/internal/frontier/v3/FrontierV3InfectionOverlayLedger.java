package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Durable physical provenance for complete four-by-four infection surface patches.
 *
 * <p>One canonical {@link InfectionCell} owns one bounded set of sixteen independently measured
 * surface positions. The patch is admitted only as a whole against an air baseline. A later
 * foreign change conflicts the whole canonical cell and is permanent evidence; it is never a
 * permit to repaint the changed column.</p>
 */
final class FrontierV3InfectionOverlayLedger extends FrontierV3JournaledSavedData {
    /*
     * Format 5 deliberately distinguishes a physical adapter block from a canonical conflict.
     * This ledger has no command/cause identity, so it cannot author terminal diagnostic truth.
     */
    private static final int FORMAT = 6;
    static final int MAX_CELLS = 65_536;
    static final int PATCH_COLUMNS = InfectionCell.BLOCKS * InfectionCell.BLOCKS;
    private final Map<InfectionCell, Claim> claims;
    private final Map<Long, InfectionCell> cellsByPosition;

    private FrontierV3InfectionOverlayLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3InfectionOverlayLedger(Map<InfectionCell, Claim> claims) {
        super(FrontierV3PhysicalStoreKind.INFECTION);
        this.claims = table("claims", claims, cell -> cell.x() + "," + cell.z(), (cell, claim) -> {
            var value = new CompoundTag(); value.putInt("x", cell.x()); value.putInt("z", cell.z());
            value.putLongArray("positions", claim.positions()); value.putString("stage", claim.stage().name()); value.putString("phase", claim.phase().name()); value.putString("baseline",
            claim.predecessor().map(Enum::name).orElse("AIR")); return value;
        });
        this.cellsByPosition = new LinkedHashMap<>();
        claims.forEach((cell, claim) -> claim.positions().forEach(position -> {
            if (cellsByPosition.put(position, cell) != null) throw new IllegalStateException("duplicate v3 infection overlay position");
        }));
    }

    static FrontierV3InfectionOverlayLedger get(ServerLevel level) {
        return FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.INFECTION, FrontierV3InfectionOverlayLedger::new, FrontierV3InfectionOverlayLedger::load);
    }

    Claim claim(InfectionCell cell) { return claims.get(cell); }
    List<Map.Entry<InfectionCell, Claim>> orderedClaims() {
        return claims.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator
                .comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z))).toList();
    }
    void ensureCapacityFor(InfectionCell cell) {
        if (!claims.containsKey(cell) && claims.size() >= MAX_CELLS) throw new IllegalStateException("v3 infection overlay claim limit exceeded");
    }
    /**
     * Reserves exact naturally-observed columns before the first physical write.  A restart can
     * therefore distinguish a clean absent patch from an interrupted one without rediscovering a
     * different height above an already-written carpet.
     */
    void prepare(InfectionCell cell, List<BlockPos> positions, InfectionOverlayStage stage) { put(cell, positions, stage, Phase.PREPARED); }
    void activate(InfectionCell cell) {
        Claim prior = required(cell);
        if (prior.phase() != Phase.PREPARED) throw new IllegalStateException("infection patch is not prepared");
        claims.put(cell, new Claim(prior.positions(), prior.stage(), Phase.ACTIVE));
        setDirty();
    }
    void retargetPrepared(InfectionCell cell, InfectionOverlayStage stage) {
        Claim prior = required(cell);
        if (prior.phase() != Phase.PREPARED) throw new IllegalStateException("infection patch is not prepared");
        if (prior.stage() == stage) return;
        claims.put(cell, new Claim(prior.positions(), stage, Phase.PREPARED, prior.predecessor()));
        setDirty();
    }
    /** Records a failed whole-patch baseline without changing any observed world block. */
    void blocked(InfectionCell cell, List<BlockPos> positions, InfectionOverlayStage stage) { put(cell, positions, stage, Phase.DEFERRED); }

    void prepareReplacement(InfectionCell cell, InfectionOverlayStage target) {
        Claim prior = required(cell);
        if (!prior.active() && !prior.cleared()) throw new IllegalStateException("replacement has no exact patch predecessor");
        claims.put(cell,new Claim(prior.positions(),target,Phase.PREPARED,prior.active() ? Optional.of(prior.stage()) : Optional.empty()));
        setDirty();
    }
    void updateStage(InfectionCell cell, InfectionOverlayStage stage) {
        Claim prior = required(cell);
        if (prior.deferred() || prior.phase() != Phase.ACTIVE) throw new IllegalStateException("infection patch is not active");
        if (prior.stage() == stage) return;
        claims.put(cell, new Claim(prior.positions(), stage, Phase.ACTIVE));
        setDirty();
    }
    /**
     * A mirror can preserve that its own exact patch is blocked, but cannot label that fact a
     * terminal conflict: it lacks the producer-stamped canonical tuple required for one.
     */
    void defer(InfectionCell cell) {
        Claim prior = claims.get(cell);
        if (prior == null || prior.deferred()) return;
        claims.put(cell, new Claim(prior.positions(), prior.stage(), Phase.DEFERRED));
        setDirty();
    }
    /** Retains exact owned patch positions through durable canonical confirmation of a clear effect. */
    void clearedByEffect(InfectionCell cell) {
        Claim prior = required(cell);
        if (prior.phase() != Phase.ACTIVE) throw new IllegalStateException("cannot clear non-active infection overlay evidence");
        claims.put(cell, new Claim(prior.positions(), prior.stage(), Phase.CLEARED));
        setDirty();
    }
    void forgetRetracted(InfectionCell cell) {
        Claim prior = required(cell);
        if (prior.deferred()) throw new IllegalStateException("cannot forget deferred infection overlay evidence");
        claims.remove(cell);
        prior.positions().forEach(cellsByPosition::remove);
        setDirty();
    }
    Optional<InfectionCell> cellAt(BlockPos position) { return Optional.ofNullable(cellsByPosition.get(position.asLong())); }
    Optional<InfectionCell> activeCellAt(BlockPos position) {
        InfectionCell cell = cellsByPosition.get(position.asLong());
        Claim claim = cell == null ? null : claims.get(cell);
        return claim != null && claim.active() ? Optional.of(cell) : Optional.empty();
    }

    static FrontierV3InfectionOverlayLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        // Frontier v3 is fresh-world-only. Older formats lack either the complete sixteen-column
        // patch or its before-write PREPARED state, so accepting them would silently invent
        // physical state after an interrupted materialization.
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 infection overlay ledger");
        ListTag values = tag.getList("claims", Tag.TAG_COMPOUND);
        if (values.size() > MAX_CELLS) throw new IllegalStateException("v3 infection overlay claim limit exceeded");
        Map<InfectionCell, Claim> claims = new LinkedHashMap<>();
        for (Tag value : values) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("x", Tag.TAG_INT) || !entry.contains("z", Tag.TAG_INT) || !entry.contains("positions", Tag.TAG_LONG_ARRAY)
                    || !entry.contains("stage", Tag.TAG_STRING) || !entry.contains("phase", Tag.TAG_STRING) || !entry.contains("baseline",Tag.TAG_STRING)) {
                throw new IllegalStateException("incomplete v3 infection overlay claim");
            }
            InfectionCell cell = new InfectionCell(entry.getInt("x"), entry.getInt("z"));
            InfectionOverlayStage stage;
            try { stage = InfectionOverlayStage.valueOf(entry.getString("stage")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 infection overlay stage", invalid); }
            long[] rawPositions = entry.getLongArray("positions");
            if (rawPositions.length != PATCH_COLUMNS) throw new IllegalStateException("incomplete v3 infection surface patch");
            List<Long> positions = java.util.Arrays.stream(rawPositions).boxed().toList();
            Phase phase;
            try { phase = Phase.valueOf(entry.getString("phase")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 infection overlay phase", invalid); }
            Claim claim = new Claim(positions, stage, phase, entry.getString("baseline").equals("AIR") ? Optional.empty() : Optional.of(InfectionOverlayStage.valueOf(entry.getString("baseline"))));
            if (claims.put(cell, claim) != null) throw new IllegalStateException("duplicate v3 infection overlay claim");
        }
        return new FrontierV3InfectionOverlayLedger(claims);
    }

    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", FORMAT); return tag; }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag values = new ListTag();
        orderedClaims().forEach(entry -> {
            CompoundTag value = new CompoundTag();
            value.putInt("x", entry.getKey().x());
            value.putInt("z", entry.getKey().z());
            value.putLongArray("positions", entry.getValue().positions());
            value.putString("stage", entry.getValue().stage().name());
            value.putString("phase", entry.getValue().phase().name()); value.putString("baseline",entry.getValue().predecessor().map(Enum::name).orElse("AIR"));
            values.add(value);
        });
        tag.put("claims", values);
        return tag;
    }

    private void put(InfectionCell cell, List<BlockPos> rawPositions, InfectionOverlayStage stage, Phase phase) {
        ensureCapacityFor(cell);
        Claim next = new Claim(rawPositions.stream().map(BlockPos::asLong).toList(), stage, phase);
        Claim prior = claims.putIfAbsent(cell, next);
        if (prior != null && !prior.equals(next)) throw new IllegalStateException("v3 infection overlay claim changes its physical patch");
        if (prior == null) {
            next.positions().forEach(position -> {
                InfectionCell existing = cellsByPosition.put(position, cell);
                if (existing != null) throw new IllegalStateException("duplicate v3 infection overlay position");
            });
            setDirty();
        }
    }

    private Claim required(InfectionCell cell) {
        Claim claim = claims.get(cell);
        if (claim == null) throw new IllegalStateException("missing v3 infection overlay claim");
        return claim;
    }

    enum Phase { PREPARED, ACTIVE, DEFERRED, CLEARED }

    record Claim(List<Long> positions, InfectionOverlayStage stage, Phase phase, Optional<InfectionOverlayStage> predecessor) {
        Claim(List<Long> positions, InfectionOverlayStage stage, Phase phase) { this(positions,stage,phase,Optional.empty()); }
        Claim {
            positions = List.copyOf(positions);
            if (phase == null || stage == null || predecessor == null || phase != Phase.PREPARED && predecessor.isPresent()) throw new IllegalArgumentException("infection patch phase/predecessor");
            if (positions.size() != PATCH_COLUMNS || new java.util.HashSet<>(positions).size() != PATCH_COLUMNS) {
                throw new IllegalArgumentException("infection patch must retain every exact surface position once");
            }
        }
        boolean deferred() { return phase == Phase.DEFERRED; }
        /** Compatibility query for callers that only need a non-writable mirror claim. */
        @Deprecated(forRemoval = true)
        boolean conflicted() { return deferred(); }
        boolean cleared() { return phase == Phase.CLEARED; }
        boolean prepared() { return phase == Phase.PREPARED; }
        boolean active() { return phase == Phase.ACTIVE; }
        List<BlockPos> blockPositions() { return positions.stream().map(BlockPos::of).toList(); }
    }
}
