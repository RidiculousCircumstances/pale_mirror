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
 * Durable physical provenance for dynamic infection markers.
 *
 * <p>Every marker is written only into a fresh air baseline. A later foreign change becomes a
 * terminal conflict; its claim is evidence, never authority to repaint that position.</p>
 */
final class FrontierV3InfectionOverlayLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_infection_overlay";
    private static final int FORMAT = 2;
    static final int MAX_CELLS = 65_536;
    private final Map<InfectionCell, Claim> claims;
    private final Map<Long, InfectionCell> cellsByPosition;

    private FrontierV3InfectionOverlayLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3InfectionOverlayLedger(Map<InfectionCell, Claim> claims) {
        this.claims = claims; this.cellsByPosition = new LinkedHashMap<>();
        claims.forEach((cell, claim) -> {
            if (cellsByPosition.put(claim.position(), cell) != null) throw new IllegalStateException("duplicate v3 infection overlay position");
        });
    }

    static FrontierV3InfectionOverlayLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3InfectionOverlayLedger::new,
                FrontierV3InfectionOverlayLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    Claim claim(InfectionCell cell) { return claims.get(cell); }
    List<Map.Entry<InfectionCell, Claim>> orderedClaims() {
        return claims.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator
                .comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z))).toList();
    }
    void ensureCapacityFor(InfectionCell cell) {
        if (!claims.containsKey(cell) && claims.size() >= MAX_CELLS) throw new IllegalStateException("v3 infection overlay claim limit exceeded");
    }
    void applied(InfectionCell cell, BlockPos position, InfectionOverlayStage stage) {
        ensureCapacityFor(cell);
        Claim next = new Claim(position.asLong(), stage, false, false);
        Claim prior = claims.putIfAbsent(cell, next);
        if (prior != null && !prior.equals(next)) throw new IllegalStateException("v3 infection overlay claim changes its physical position");
        if (prior == null) { cellsByPosition.put(position.asLong(), cell); setDirty(); }
    }
    /** Records an unavailable air-baseline position without changing the observed world. */
    void blocked(InfectionCell cell, BlockPos position, InfectionOverlayStage stage) {
        ensureCapacityFor(cell);
        Claim next = new Claim(position.asLong(), stage, true, false);
        Claim prior = claims.putIfAbsent(cell, next);
        if (prior != null && !prior.equals(next)) throw new IllegalStateException("v3 infection overlay claim changes its physical position");
        if (prior == null) { cellsByPosition.put(position.asLong(), cell); setDirty(); }
    }
    void updateStage(InfectionCell cell, InfectionOverlayStage stage) {
        Claim prior = required(cell);
        if (prior.conflicted() || prior.stage() == stage && !prior.cleared()) return;
        claims.put(cell, new Claim(prior.position(), stage, false, false)); setDirty();
    }
    void conflict(InfectionCell cell) {
        Claim prior = claims.get(cell);
        if (prior == null || prior.conflicted()) return;
        claims.put(cell, new Claim(prior.position(), prior.stage(), true, false)); setDirty();
    }
    /** Retains exact owned position through durable canonical confirmation of a cleared marker. */
    void clearedByEffect(InfectionCell cell) {
        Claim prior = required(cell);
        if (prior.conflicted()) throw new IllegalStateException("cannot clear conflicted infection overlay evidence");
        claims.put(cell, new Claim(prior.position(), prior.stage(), false, true)); setDirty();
    }
    void forgetRetracted(InfectionCell cell) {
        Claim prior = required(cell);
        if (prior.conflicted()) throw new IllegalStateException("cannot forget conflicted infection overlay evidence");
        claims.remove(cell); cellsByPosition.remove(prior.position()); setDirty();
    }
    Optional<InfectionCell> cellAt(BlockPos position) { return Optional.ofNullable(cellsByPosition.get(position.asLong())); }

    static FrontierV3InfectionOverlayLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.getInt("format"); if (format != 1 && format != FORMAT) throw new IllegalStateException("incompatible v3 infection overlay ledger");
        ListTag values = tag.getList("claims", Tag.TAG_COMPOUND);
        if (values.size() > MAX_CELLS) throw new IllegalStateException("v3 infection overlay claim limit exceeded");
        Map<InfectionCell, Claim> claims = new LinkedHashMap<>();
        for (Tag value : values) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("x", Tag.TAG_INT) || !entry.contains("z", Tag.TAG_INT) || !entry.contains("pos", Tag.TAG_LONG)
                    || !entry.contains("stage", Tag.TAG_STRING)) throw new IllegalStateException("incomplete v3 infection overlay claim");
            InfectionCell cell = new InfectionCell(entry.getInt("x"), entry.getInt("z"));
            InfectionOverlayStage stage;
            try { stage = InfectionOverlayStage.valueOf(entry.getString("stage")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 infection overlay stage", invalid); }
            if (claims.put(cell, new Claim(entry.getLong("pos"), stage, entry.getBoolean("conflict"), format == FORMAT && entry.getBoolean("cleared"))) != null) {
                throw new IllegalStateException("duplicate v3 infection overlay claim");
            }
        }
        return new FrontierV3InfectionOverlayLedger(claims);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        orderedClaims().forEach(entry -> {
            CompoundTag value = new CompoundTag();
            value.putInt("x", entry.getKey().x()); value.putInt("z", entry.getKey().z());
            value.putLong("pos", entry.getValue().position()); value.putString("stage", entry.getValue().stage().name());
            value.putBoolean("conflict", entry.getValue().conflicted()); value.putBoolean("cleared", entry.getValue().cleared()); values.add(value);
        });
        tag.put("claims", values); return tag;
    }

    private Claim required(InfectionCell cell) {
        Claim claim = claims.get(cell);
        if (claim == null) throw new IllegalStateException("missing v3 infection overlay claim");
        return claim;
    }
    record Claim(long position, InfectionOverlayStage stage, boolean conflicted, boolean cleared) { }
}
